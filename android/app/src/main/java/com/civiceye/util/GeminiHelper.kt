package com.civiceye.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.civiceye.data.model.Department
import com.civiceye.data.model.IssueAnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Helper class for Gemini AI image analysis (Direct REST API Version)
 * Bypasses Google AI SDK to prevent "404 Not Found" and Version Aliasing issues.
 */
object GeminiHelper {
    
    private const val TAG = "GeminiHelper"
    
    // Gemini API key (User provided)
    private const val GEMINI_API_KEY = "AIzaSyAtHkG2Ltq-HKfnzqvWeCB7Wr_SiHd9Gwc"
    
    // Base URL - Model will be appended dynamically
    // Switched to v1 (Stable) as per user suggestion
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1/models/"
    
    // Models to try in order (Confirmed Available via HTML Test)
    private val MODELS = listOf(
        "gemini-2.0-flash",
        "gemini-2.5-flash",
        "gemini-2.0-flash-lite",
        "gemini-1.5-flash" // Fallback
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeIssueImage(
        context: Context, 
        imageUri: Uri, 
        departments: List<Department>,
        onFeedback: (String) -> Unit = {}
    ): Result<IssueAnalysisResult> {
        return withContext(Dispatchers.IO) {
            val errorLog = StringBuilder()
            
            try {
                // 1. Prepare Image (Once)
                val bitmap = loadAndCompressBitmap(context, imageUri)
                val base64Image = bitmapToBase64(bitmap)
                val promptText = createCivicIssuePrompt(departments)
                
                // 2. Iterate through models
                for (model in MODELS) {
                    try {
                        Log.d(TAG, "Attempting Model: $model")
                        val result = callModel(model, promptText, base64Image, onFeedback)
                        if (result != null) {
                            return@withContext Result.success(result)
                        } else {
                            errorLog.append("[$model: Empty Response] ")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Model $model failed: ${e.message}")
                        errorLog.append("[$model: ${e.message}] ")
                    }
                }
            } catch (e: Exception) {
                // Return error if image loading fails
                return@withContext Result.success(createErrorResult("Image Error: ${e.message}"))
            }
            
            // 3. Fallback: If all failed
            Result.success(createErrorResult("All AI Models Failed: $errorLog"))
        }
    }

    suspend fun validateIssueDescription(
        description: String,
        onFeedback: (String) -> Unit = {}
    ): Result<IssueAnalysisResult> {
        return withContext(Dispatchers.IO) {
            val prompt = """
                Analyze the following issue description for a Civic Issue Reporting App.
                Description: "$description"
                
                Rules:
                - STRICTLY REJECT if it mentions private/personal issues, non-civic complaints, or violates the policy.
                - It MUST comprise a valid civic issue (Roads, Waste, Water, etc.).
                
                Return JSON:
                {
                  "is_civic_issue": true/false,
                  "reason": "Why it is accepted or rejected",
                  "category": "Suggested category",
                  "confidence": 0-100
                }
            """.trimIndent()
            
            // Use flash model for text
            val result = callModel("gemini-2.0-flash", prompt, null, onFeedback)
            if (result != null) Result.success(result)
            else Result.failure(Exception("Validation failed"))
        }
    }

    private suspend fun callModel(
        modelName: String, 
        prompt: String, 
        base64Image: String?, 
        onFeedback: (String) -> Unit
    ): IssueAnalysisResult? {
        val contentsPart = JSONArray()
        
        // Add Text Prompt
        contentsPart.put(JSONObject().apply { put("text", prompt) })
        
        // Add Image if present
        if (base64Image != null) {
            contentsPart.put(JSONObject().apply {
                put("inline_data", JSONObject().apply {
                    put("mime_type", "image/jpeg")
                    put("data", base64Image)
                })
            })
        }

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", contentsPart)
            }))
        }

        val request = Request.Builder()
            .url("$BASE_URL$modelName:generateContent?key=$GEMINI_API_KEY")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        // Exponential Backoff Logic
        var attempt = 0
        val maxRetries = 3
        var currentDelay = 500L // Start with 500ms

        while (attempt <= maxRetries) {
            try {
                if (attempt > 0) {
                    val msg = "System busy, retrying in ${currentDelay/1000.0}s..."
                    Log.d(TAG, msg)
                    withContext(Dispatchers.Main) { onFeedback(msg) }
                    kotlinx.coroutines.delay(currentDelay)
                }

                val response = client.newCall(request).execute()
                val responseBodyStr = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    // Check for 429 (Too Many Requests) or 503 (Service Unavailable)
                    if (response.code == 429 || response.code == 503) {
                        response.close()
                        attempt++
                        currentDelay *= 2 // Exponential backoff (500 -> 1000 -> 2000)
                        continue // Retry
                    }
                    
                    val jsonObj = try { JSONObject(responseBodyStr) } catch(e: Exception) { null }
                    val errorMsg = jsonObj?.optJSONObject("error")?.optString("message") 
                        ?: "HTTP ${response.code}"
                    throw Exception(errorMsg)
                }

                // Success
                val responseJson = JSONObject(responseBodyStr)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val text = parts?.getJSONObject(0)?.optString("text")
                    if (!text.isNullOrEmpty()) {
                        return parseAIResponse(text)
                    }
                }
                return null // Empty valid response

            } catch (e: Exception) {
                if (attempt >= maxRetries) throw e // Rethrow if max retries reached
                attempt++
                currentDelay *= 2
            }
        }
        return null
    }
    
    // ... existing helpers ...

    private fun createErrorResult(message: String): IssueAnalysisResult {
        return IssueAnalysisResult(
            title = "AI Failed",
            description = message,
            category = "Other",
            severity = "Medium",
            confidence = 0.0f,
            detectedIssues = emptyList()
        )
    }

    private fun loadAndCompressBitmap(context: Context, uri: Uri): Bitmap {
        val inputStream = context.contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        
        val maxDimension = 800
        return if (originalBitmap.width > maxDimension || originalBitmap.height > maxDimension) {
            val scale = minOf(
                maxDimension.toFloat() / originalBitmap.width,
                maxDimension.toFloat() / originalBitmap.height
            )
            val newWidth = (originalBitmap.width * scale).toInt()
            val newHeight = (originalBitmap.height * scale).toInt()
            Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
        } else {
            originalBitmap
        }
    }
    
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun createCivicIssuePrompt(departments: List<Department>): String {
         val categoryList = if (departments.isNotEmpty()) {
            departments.joinToString("\n") { dept -> "- ${dept.name}" } + "\n- Other"
        } else {
             // Fallback
            """
            - Roads
            - Water Authority
            - Waste Management
            - Streetlights
            - Other
            """.trimIndent()
        }
        
        return """
            You are an AI moderator for a Civic Issue Reporting App.
            
            OBJECTIVE: Identify if the image contains a valid civic issue.
            
            SCREENSHOT POLICY:
            - Reject strictly if the image is a screenshot of a Mobile/Desktop UI, Social Media Post, Text Message, or Meme.
            - ACCEPT the image if it is a photo of a real-world scene, even if it is high quality, low light, or taken from a screen (if the content is clearly a civic issue).
            
            ONLY allow issues related to:
            - Roads (potholes, cracks, damaged surfaces)
            - Water leakage, pipeline bursts, drainage overflow
            - Sewage problems or water stagnation
            - Garbage, waste dumping in public places
            - Streetlights, traffic signals, public lighting issues
            - Flooding or blocked drains
            - Unsafe public construction zones
            - Government or municipal infrastructure damage
            - Public safety hazards in common areas

            STRICTLY REJECT images containing or related to:
            - Selfies or photos focused on people
            - Private homes or personal property
            - Pets, animals, food, objects
            - Vehicles, personal damage, or private disputes
            - Screenshots, memes, social media images
            - Random nature photos without civic relevance
            - Any image not affecting the general public

            Rules:
            - Be strict. If the image is unclear or doubtful, REJECT it.
            - Do NOT assume context beyond the image.
            - Do NOT be lenient.
            - Do NOT generate creative descriptions.
            - SELECT THE CATEGORY EXACTLY FROM THIS LIST (Do not invent new ones):
            $categoryList

            Return ONLY valid JSON in the following format:

            {
              "is_civic_issue": true or false,
              "issue_category": "<Excatly one name from the list above>",
              "confidence": <number from 0 to 100>,
              "reason": "<one short sentence explaining the decision>"
            }

            If the image does not clearly show a public or civic issue, return:
            {
              "is_civic_issue": false,
              "reason": "Reason for rejection"
            }
        """.trimIndent()
    }
    
    private fun parseAIResponse(responseText: String): IssueAnalysisResult? {
        return try {
            val jsonText = responseText.replace("```json", "").replace("```", "").trim()
            val json = JSONObject(jsonText)
            
            val isCivic = json.optBoolean("is_civic_issue", false)
            val reason = json.optString("reason", "")
            val category = json.optString("issue_category", "Other")
            val confidence = json.optDouble("confidence", 0.0).toFloat()
            
            if (!isCivic) {
                // Return a special result indicating rejection
                val rejectionReason = if (reason.isNotEmpty()) reason else "Not a civic issue"
                return IssueAnalysisResult(
                    title = "Not a Civic Issue",
                    description = "Image rejected: $rejectionReason. Please upload a clear photo of a public infrastructure issue.",
                    category = "Rejected", // Magic string to handle in UI
                    severity = "Low",
                    confidence = 0.0f,
                    detectedIssues = emptyList()
                )
            }
            
            // If Valid Civic Issue, we map the simple JSON to our internal model
            // Title = "Issue: [Category]"
            // Description = Reason (Since user asked for explanation)
            IssueAnalysisResult(
                title = "Reported Issue: $category",
                description = reason,
                category = category,
                severity = "Medium", // Default as severity logic wasn't requested
                confidence = confidence / 100f, // Normalize 0-100 to 0-1
                detectedIssues = emptyList()
            )
        } catch (e: Exception) { null }
    }
}
