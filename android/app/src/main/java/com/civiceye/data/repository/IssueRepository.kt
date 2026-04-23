package com.civiceye.data.repository

import android.net.Uri

import com.civiceye.data.model.Issue
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IssueRepository @Inject constructor(
    private val firestore: FirebaseFirestore  
) {
    
    private val issuesCollection = firestore.collection("issues")
    // commentsCollection removed
    
    // Get issues for a location as Flow (real-time updates)
    fun getIssuesByLocation(locationId: String): Flow<List<Issue>> = callbackFlow {
        val listener = issuesCollection
            .whereEqualTo("locationId", locationId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val issues = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Issue::class.java)?.copy(id = doc.id)
                }?.sortedByDescending { it.createdAt } ?: emptyList()
                
                trySend(issues)
            }
        
        awaitClose { listener.remove() }
    }

    // Get ALL issues regardless of location
    fun getAllIssues(): Flow<List<Issue>> = callbackFlow {
        val listener = issuesCollection
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50) // Limit to 50 for performance
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val issues = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Issue::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                
                trySend(issues)
            }
        
        awaitClose { listener.remove() }
    }
    
    // Get issues by category/department
    fun getIssuesByCategory(locationId: String, category: String): Flow<List<Issue>> = callbackFlow {
        val listener = issuesCollection
            .whereEqualTo("locationId", locationId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val issues = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Issue::class.java)?.copy(id = doc.id)
                }?.filter { it.category == category }
                    ?.sortedByDescending { it.createdAt } ?: emptyList()
                
                trySend(issues)
            }
        
        awaitClose { listener.remove() }
    }
    
    // Get issues submitted by a specific user (for Track screen)
    fun getUserIssues(userId: String): Flow<List<Issue>> = callbackFlow {
        val listener = issuesCollection
            .whereEqualTo("reporterId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val issues = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Issue::class.java)?.copy(id = doc.id)
                }?.sortedByDescending { it.createdAt } ?: emptyList()
                
                trySend(issues)
            }
        
        awaitClose { listener.remove() }
    }
    
    // Get single issue
    suspend fun getIssue(issueId: String): Result<Issue> {
        return try {
            val doc = issuesCollection.document(issueId).get().await()
            val issue = doc.toObject(Issue::class.java)?.copy(id = doc.id)
            if (issue != null) Result.success(issue)
            else Result.failure(Exception("Issue not found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Create new issue
    suspend fun createIssue(
        title: String,
        description: String,
        category: String,
        locationId: String,
        address: String,
        imageUris: List<Uri>,
        reporterId: String,
        reporterName: String,
        latitude: Double? = null,
        longitude: Double? = null,
        locationType: String = "MANUAL",
        gpsTimestamp: Timestamp? = null,
        aiGenerated: Boolean = false,
        aiConfidence: Float = 0f,
        aiDetectedIssues: List<String> = emptyList(),
        severity: String = "",
        assignedDepartment: String = "",
        userComments: String = ""
    ): Result<Issue> {
        return try {
            // Upload images to Cloudinary
            val imageUrls = mutableListOf<String>()
            val uploadErrors = mutableListOf<String>()
            
            for ((index, uri) in imageUris.withIndex()) {
                try {
                    val url = uploadToCloudinary(uri)
                    imageUrls.add(url)
                    android.util.Log.d("IssueRepository", "Uploaded image $index to Cloudinary: $url")
                } catch (e: Exception) {
                    android.util.Log.e("IssueRepository", "Failed to upload image $index: ${e.message}")
                    uploadErrors.add(e.message ?: "Unknown error")
                }
            }
            
            // If user selected images but none were uploaded, fail the process
            if (imageUris.isNotEmpty() && imageUrls.isEmpty()) {
                val errorMsg = uploadErrors.firstOrNull() ?: "Image upload failed"
                throw Exception("Failed to upload images: $errorMsg. Check internet connection.")
            }
            
            // Create issue document
            val issue = Issue(
                title = title,
                description = description,
                category = category,
                locationId = locationId,
                address = address,
                images = imageUrls,
                reporterId = reporterId,
                reporterName = reporterName,
                status = Issue.STATUS_PENDING,
                assignedDepartment = assignedDepartment.ifEmpty { category }, // Fallback to category if empty
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now(),
                latitude = latitude,
                longitude = longitude,
                locationType = locationType,
                gpsTimestamp = gpsTimestamp,
                aiGenerated = aiGenerated,
                aiConfidence = aiConfidence,
                aiDetectedIssues = aiDetectedIssues,
                severity = severity,
                userComments = userComments
            )
            
            val docRef = issuesCollection.add(issue).await()
            
            // Try to increment user's issues reported count (don't fail if this fails)
            try {
                firestore.collection("users").document(reporterId)
                    .update("issuesReported", FieldValue.increment(1))
                    .await()
            } catch (e: Exception) {
                android.util.Log.w("IssueRepository", "Could not update issuesReported: ${e.message}")
            }
            
            Result.success(issue.copy(id = docRef.id))
        } catch (e: Exception) {
            android.util.Log.e("IssueRepository", "Failed to create issue: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun uploadToCloudinary(uri: Uri): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val context = com.civiceye.CivicEyeApplication.instance.applicationContext
        val inputStream = context.contentResolver.openInputStream(uri) 
            ?: throw Exception("Could not open input stream for URI")
        
        val bytes = inputStream.readBytes()
        inputStream.close()
        
        // Cloudinary config - User's credentials
        val cloudName = "dpp2wlbhh"
        val apiKey = "875443631971545"
        val apiSecret = "Y6fQx8OnV-qatUUxbaFgyElZUfA"
        val timestamp = System.currentTimeMillis() / 1000
        
        // Prepare signature for signed upload
        // Signature is SHA-1 of "timestamp=xxxxx<api_secret>"
        val signatureStr = "timestamp=$timestamp$apiSecret"
        val signature = java.security.MessageDigest.getInstance("SHA-1")
            .digest(signatureStr.toByteArray())
            .joinToString("") { "%02x".format(it) }
            
        val client = okhttp3.OkHttpClient()
        
        val requestBody = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("file", "image.jpg", 
                bytes.toRequestBody("image/*".toMediaTypeOrNull()))
            .addFormDataPart("api_key", apiKey)
            .addFormDataPart("timestamp", timestamp.toString())
            .addFormDataPart("signature", signature)
            .build()

        val request = okhttp3.Request.Builder()
            .url("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        
        if (!response.isSuccessful) {
            throw Exception("Cloudinary upload failed: $responseBody")
        }
        
        // Parse JSON response to get the secure URL
        val json = org.json.JSONObject(responseBody)
        return@withContext json.getString("secure_url")
    }
    
    // Toggle like
    suspend fun toggleLike(issueId: String, userId: String): Result<Boolean> {
        return try {
            val issueRef = issuesCollection.document(issueId)
            val doc = issueRef.get().await()
            val issue = doc.toObject(Issue::class.java) ?: return Result.failure(Exception("Issue not found"))
            
            val isLiked = issue.likedBy.contains(userId)
            
            if (isLiked) {
                issueRef.update(
                    "likedBy", FieldValue.arrayRemove(userId),
                    "likeCount", FieldValue.increment(-1)
                ).await()
            } else {
                issueRef.update(
                    "likedBy", FieldValue.arrayUnion(userId),
                    "likeCount", FieldValue.increment(1)
                ).await()
            }
            
            Result.success(!isLiked)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Toggle support ("This affects me too")
    suspend fun toggleSupport(issueId: String, userId: String): Result<Boolean> {
        return try {
            val issueRef = issuesCollection.document(issueId)
            val doc = issueRef.get().await()
            val issue = doc.toObject(Issue::class.java) ?: return Result.failure(Exception("Issue not found"))
            
            val isSupported = issue.supportedBy.contains(userId)
            
            if (isSupported) {
                issueRef.update(
                    "supportedBy", FieldValue.arrayRemove(userId),
                    "supportCount", FieldValue.increment(-1)
                ).await()
                
                firestore.collection("users").document(userId)
                    .update("issuesSupported", FieldValue.increment(-1))
            } else {
                issueRef.update(
                    "supportedBy", FieldValue.arrayUnion(userId),
                    "supportCount", FieldValue.increment(1)
                ).await()
                
                firestore.collection("users").document(userId)
                    .update("issuesSupported", FieldValue.increment(1))
            }
            
            Result.success(!isSupported)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Increment share count
    suspend fun incrementShare(issueId: String): Result<Unit> {
        return try {
            issuesCollection.document(issueId)
                .update("shareCount", FieldValue.increment(1))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // getComments removed
    // addComment removed
    // Get issues assigned to a staff member
    fun getAssignedIssues(staffId: String): Flow<List<Issue>> = callbackFlow {
        val listener = issuesCollection
            .whereEqualTo("assignedTo", staffId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val issues = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Issue::class.java)?.copy(id = doc.id)
                }?.sortedByDescending { it.createdAt } ?: emptyList()
                
                trySend(issues)
            }
        
        awaitClose { listener.remove() }
    }
    
    // Resolve issue (Staff Action)
    suspend fun resolveIssue(
        issueId: String,
        resolutionNotes: String,
        proofImageUris: List<Uri>
    ): Result<Unit> {
        return try {
            // Upload proof images
            val imageUrls = mutableListOf<String>()
            for (uri in proofImageUris) {
                val url = uploadToCloudinary(uri)
                imageUrls.add(url)
            }
            
            val updates = mapOf(
                "status" to Issue.STATUS_RESOLVED,
                "resolutionNotes" to resolutionNotes,
                "proofImages" to imageUrls,
                "resolvedAt" to Timestamp.now(),
                "userModifiedAI" to false // Re-using this field or ignore? Let's just update standard fields.
            )
            
            issuesCollection.document(issueId).update(updates).await()
            
            // Increment issues resolved count for the user (Staff)
            val issue = issuesCollection.document(issueId).get().await().toObject(Issue::class.java)
            if (issue != null && issue.assignedTo.isNotEmpty()) {
                 try {
                    firestore.collection("users").document(issue.assignedTo)
                        .update("issuesResolved", FieldValue.increment(1)) // Assuming field exists or we add it
                        .await()
                } catch (e: Exception) {
                    // Ignore if field doesn't exist yet
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Update status (Admin/Staff generic)
    suspend fun updateStatus(issueId: String, status: String): Result<Unit> {
         return try {
            issuesCollection.document(issueId).update("status", status).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
