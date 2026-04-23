package com.civiceye.data.repository

import com.civiceye.data.model.Department
import com.civiceye.data.model.Location
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MasterDataRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    
    private val locationsCollection = firestore.collection("locations")
    private val departmentsCollection = firestore.collection("departments")
    
    // Get all locations (removed isActive filter to avoid needing composite index)
    fun getLocations(): Flow<List<Location>> = callbackFlow {
        val listener = locationsCollection
            .orderBy("name")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Don't close, just send empty list and log error
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val locations = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Location::class.java)?.copy(id = doc.id)
                }?.filter { it.isActive } ?: emptyList()
                
                trySend(locations)
            }
        
        awaitClose { listener.remove() }
    }
    
    // Get all departments (removed isActive filter to avoid needing composite index)
    fun getDepartments(): Flow<List<Department>> = callbackFlow {
        val listener = departmentsCollection
            .orderBy("name")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Don't close, just send empty list and log error
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val departments = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Department::class.java)?.copy(id = doc.id)
                }?.filter { it.isActive } ?: emptyList()
                
                trySend(departments)
            }
        
        awaitClose { listener.remove() }
    }
    
    // Get single location
    suspend fun getLocation(locationId: String): Result<Location> {
        return try {
            val doc = locationsCollection.document(locationId).get().await()
            val location = doc.toObject(Location::class.java)?.copy(id = doc.id)
            if (location != null) Result.success(location)
            else Result.failure(Exception("Location not found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Get single department
    suspend fun getDepartment(departmentId: String): Result<Department> {
        return try {
            val doc = departmentsCollection.document(departmentId).get().await()
            val department = doc.toObject(Department::class.java)?.copy(id = doc.id)
            if (department != null) Result.success(department)
            else Result.failure(Exception("Department not found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Get departments as list (one-time fetch) - fixed to avoid composite index
    suspend fun getDepartmentsList(): Result<List<Department>> {
        return try {
            val snapshot = departmentsCollection
                .get()
                .await()
            
            val departments = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Department::class.java)?.copy(id = doc.id)
            }.filter { it.isActive }.sortedBy { it.name }
            
            Result.success(departments)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Get locations as list (one-time fetch) - fixed to avoid composite index
    suspend fun getLocationsList(): Result<List<Location>> {
        return try {
            val snapshot = locationsCollection
                .get()
                .await()
            
            val locations = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Location::class.java)?.copy(id = doc.id)
            }.filter { it.isActive }.sortedBy { it.name }
            
            Result.success(locations)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Add new location dynamically (De-duplicate by name)
    suspend fun addLocation(name: String, latitude: Double, longitude: Double): Result<Location> {
        return try {
            // Check if location with same name already exists
            val existingDocs = locationsCollection
                .whereEqualTo("name", name)
                .limit(1)
                .get()
                .await()

            if (!existingDocs.isEmpty) {
                // Return existing location
                val doc = existingDocs.documents[0]
                val existingLocation = doc.toObject(Location::class.java)?.copy(id = doc.id)
                if (existingLocation != null) {
                   return Result.success(existingLocation)
                }
            }

            // Create new location if not exists
            val locationId = locationsCollection.document().id
            val newLocation = Location(
                id = locationId,
                name = name,
                state = "Kerala", // Default state
                latitude = latitude,
                longitude = longitude,
                isActive = true,
                createdAt = com.google.firebase.Timestamp.now()
            )
            
            locationsCollection.document(locationId).set(newLocation).await()
            Result.success(newLocation)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    // Seed default departments if missing
    suspend fun seedDepartments() {
        try {
            val snapshot = departmentsCollection.get().await()
            if (snapshot.isEmpty) {
                val batch = firestore.batch()
                
                val defaultDepartments = listOf(
                    "PWD (Roads)", "KSEB (Electricity)", "KWA (Water)", 
                    "Health Department", "Waste Management", "Police", "Fire & Rescue",
                    "Revenue Department", "Panchayat", "Municipality", "Corporation"
                )
                
                defaultDepartments.forEach { deptName ->
                    val docRef = departmentsCollection.document()
                    val dept = Department(
                        id = docRef.id,
                        name = deptName,
                        isActive = true
                    )
                    batch.set(docRef, dept)
                }
                
                batch.commit().await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Get all Staff members for Directory
    suspend fun getStaffMembers(): Result<List<com.civiceye.data.model.User>> {
        return try {
            val snapshot = firestore.collection("users")
                .whereEqualTo("role", "staff")
                .get()
                .await()
            
            val staff = snapshot.documents.mapNotNull { doc ->
                doc.toObject(com.civiceye.data.model.User::class.java)?.copy(uid = doc.id)
            }.sortedBy { it.name }
            
            Result.success(staff)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
