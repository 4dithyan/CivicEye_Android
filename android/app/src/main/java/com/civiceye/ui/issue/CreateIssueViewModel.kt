package com.civiceye.ui.issue

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civiceye.data.model.Department
import com.civiceye.data.model.IssueAnalysisResult
import com.civiceye.data.model.Location
import com.civiceye.data.repository.AuthRepository
import com.civiceye.data.repository.IssueRepository
import com.civiceye.data.repository.MasterDataRepository
import com.civiceye.util.GeminiHelper
import com.civiceye.util.LocationHelper
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import javax.inject.Inject

data class CreateIssueUiState(
    val isLoading: Boolean = false,
    val isAnalyzing: Boolean = false,
    val isSubmitted: Boolean = false,
    val locations: List<Location> = emptyList(),
    val departments: List<Department> = emptyList(),
    val aiResult: IssueAnalysisResult? = null,
    val gpsAddress: String? = null,
    val gpsLatitude: Double? = null,
    
    val gpsLongitude: Double? = null,
    val areaLatitude: Double? = null, // Centroid of the area
    val areaLongitude: Double? = null, // Centroid of the area
    val error: String? = null,
    val aiStatusMessage: String? = null // For "System busy, retrying..." feedback
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class CreateIssueViewModel @Inject constructor(
    private val issueRepository: IssueRepository,
    private val masterDataRepository: MasterDataRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(CreateIssueUiState())
    val uiState: StateFlow<CreateIssueUiState> = _uiState.asStateFlow()

    // Debounce Flow for Description
    private val _descriptionFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            _descriptionFlow
                .debounce(500L) // Wait 500ms after typing stops
                .distinctUntilChanged()
                .filter { it.length > 10 } // Only validate if decent length
                .collectLatest { text ->
                    validateDescription(text)
                }
        }
    }
    
    fun onDescriptionChanged(text: String) {
        _descriptionFlow.value = text
    }

    private suspend fun validateDescription(text: String) {
        _uiState.update { it.copy(isAnalyzing = true, aiStatusMessage = "Validating content...") }
        
        GeminiHelper.validateIssueDescription(text) { feedback ->
             _uiState.update { it.copy(aiStatusMessage = feedback) }
        }.onSuccess { result ->
             _uiState.update { 
                 it.copy(
                     isAnalyzing = false, 
                     aiStatusMessage = if (result.category == "Rejected") result.description else "Content looks good",
                     // Optionally update aiResult if we want to reflect the validation
                 ) 
             }
        }.onFailure {
             _uiState.update { it.copy(isAnalyzing = false, aiStatusMessage = null) }
        }
    }

    fun loadMasterData() {
        viewModelScope.launch {
            masterDataRepository.getLocationsList()
                .onSuccess { locations ->
                    // Add "Add New" option at the top
                    val updatedLocations = locations.toMutableList().apply {
                        add(0, Location(id = "ADD_NEW_GPS", name = "📍 Add Current Location", isActive = true))
                    }
                    _uiState.update { it.copy(locations = updatedLocations) }
                }
            
            masterDataRepository.getDepartmentsList()
                .onSuccess { departments ->
                    _uiState.update { it.copy(departments = departments) }
                }
        }
    }

    /**
     * Create a new location from current GPS
     */
    fun createLocationFromGPS(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                // 1. Get Location
                val location = LocationHelper.getCurrentLocation(context)
                
                if (location == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Could not fetch GPS. Try outdoors.") }
                    return@launch
                }
                
                // 2. Get Address Name
                val addressObj = LocationHelper.reverseGeocode(context, location.latitude, location.longitude)
                
                val addressName = if (addressObj != null) {
                    LocationHelper.formatAddress(addressObj)
                } else {
                    "New Location (${location.latitude.toString().take(7)}, ${location.longitude.toString().take(7)})"
                }
                
                // 3. Check if location with same name already exists
                val existingLocation = _uiState.value.locations.find { it.name.equals(addressName, ignoreCase = true) }
                
                if (existingLocation != null) {
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            gpsAddress = "Located: ${existingLocation.name}" // Feedback
                        ) 
                    }
                    return@launch
                }
                
                // 4. Save New Location to Backend (Only if unique)
                masterDataRepository.addLocation(addressName, location.latitude, location.longitude)
                    .onSuccess { newLocation ->
                        // 5. Refresh List
                        val currentList = _uiState.value.locations.toMutableList()
                        currentList.add(1, newLocation)
                        
                        _uiState.update { 
                            it.copy(
                                isLoading = false, 
                                locations = currentList,
                                gpsAddress = "Created: $addressName",
                                areaLatitude = addressObj?.latitude,
                                areaLongitude = addressObj?.longitude
                            ) 
                        }
                    }
                    .onFailure { e ->
                         _uiState.update { it.copy(isLoading = false, error = "Failed to save location: ${e.message}") }
                    }
                    
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /**
     * Analyze image with AI and capture GPS location automatically
     */
    fun analyzeImageAndCaptureLocation(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, error = null) }
            
            // Run AI and Location in parallel
            // Run AI and Location in parallel with strict TIMEOUTS
            val locationDeferred = async {
                // LocationHelper.getCurrentLocation now handles timeout (5s) + fallback to LastKnown internally
                // We give it a total of 7s here just in case
                kotlinx.coroutines.withTimeoutOrNull(7000L) { 
                     try {
                        LocationHelper.getCurrentLocation(context)
                     } catch (e: Exception) {
                        null
                     }
                }
            }

            val aiDeferred = async {
                kotlinx.coroutines.withTimeoutOrNull(60000L) { // 60s timeout - let GeminiHelper handle inner timeouts
                    GeminiHelper.analyzeIssueImage(context, imageUri, _uiState.value.departments) { feedback ->
                        _uiState.update { it.copy(aiStatusMessage = feedback) }
                    }
                }
            }

            // Wait for both
            val location = locationDeferred.await()
            val aiResultCombined = aiDeferred.await()
            
             // Process Location
             var address: String? = null
             var areaLat: Double? = null
             var areaLng: Double? = null
             
             if (location != null) {
                  try {
                      val addrObj = LocationHelper.reverseGeocode(
                          context,
                          location.latitude,
                          location.longitude
                      )
                      if (addrObj != null) {
                          address = LocationHelper.formatAddress(addrObj)
                          areaLat = addrObj.latitude
                          areaLng = addrObj.longitude
                      }
                  } catch (e: Exception) { 
                      e.printStackTrace()
                      address = null 
                  }
            } else {
                address = null
            }
            
            // FALLBACK: If GPS fails (common on emulators/indoors), try to use User's Home Location
            if (location == null) {
                // Try to get user's location from Master Data
                val user = authRepository.getCurrentUserData().getOrNull()
                val userLocationId = user?.locationId
                val userLocation = _uiState.value.locations.find { it.id == userLocationId }
                
                if (userLocation != null) {
                    address = "Near ${userLocation.name} (GPS Weak)"
                    _uiState.update { 
                        it.copy(
                            gpsLatitude = if (userLocation.latitude != 0.0) userLocation.latitude else 12.9716, 
                            gpsLongitude = if (userLocation.longitude != 0.0) userLocation.longitude else 77.5946,
                            gpsAddress = address
                        ) 
                    }
                } else {
                    // Total fallback
                    address = "Location Signal Weak"
                    _uiState.update { 
                        it.copy(
                            gpsLatitude = 12.9716, // Default Bangalore
                            gpsLongitude = 77.5946,
                            gpsAddress = address
                        ) 
                    }
                }
            } else {
                 _uiState.update { 
                    it.copy(
                        gpsLatitude = location.latitude, 
                        gpsLongitude = location.longitude,
                        gpsAddress = address,
                        areaLatitude = areaLat,
                        areaLongitude = areaLng
                    ) 
                }
            }

            // Process AI Result
            val aiAnalysis = aiResultCombined?.getOrNull()
            val aiError = aiResultCombined?.exceptionOrNull()?.message ?: "AI Analysis Timed Out"
            
            var validatedAnalysis = aiAnalysis
            
            // Validate Department Name Match
            if (validatedAnalysis != null && validatedAnalysis.category != "Rejected") {
                val exists = _uiState.value.departments.any { 
                    it.name.equals(validatedAnalysis!!.category, ignoreCase = true) 
                }
                
                if (!exists) {
                    // Normalize to "Other" or find best match? For now, forced "Other" is safest
                    validatedAnalysis = validatedAnalysis.copy(category = "Other")
                }
            }

            // Fallback Result: STRICT REJECTION if AI fails
            val finalAiResult = validatedAnalysis ?: IssueAnalysisResult(
                title = "Verification Failed",
                description = "AI Verification Failed: $aiError. Please retake photo.",
                category = "Rejected",
                severity = "Low",
                confidence = 0f,
                detectedIssues = emptyList()
            )
            
            _uiState.update {
                it.copy(
                    isAnalyzing = false,
                    aiResult = finalAiResult,
                    // Show error in UI directly if rejected
                    error = if(finalAiResult.category == "Rejected") finalAiResult.description else null
                )
            }
        }
    }

    /**
     * Submit issue with AI-generated data
     */
    fun submitIssueWithAI(
        images: List<Uri>,
        userComments: String = ""
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val user = authRepository.getCurrentUserData().getOrNull()
            if (user == null) {
                _uiState.update { it.copy(isLoading = false, error = "Please login again") }
                return@launch
            }
            
            val aiResult = _uiState.value.aiResult
            if (aiResult == null) {
                _uiState.update { it.copy(isLoading = false, error = "Please analyze image first") }
                return@launch
            }
            
            // Use AI-generated data
            val title = aiResult.title
            val description = aiResult.description
            val category = aiResult.category
            
            // STRICT CHECK: Block Rejected Issues
            if (category == "Rejected") {
                _uiState.update { it.copy(isLoading = false, error = "Cannot submit: ${aiResult.description}") }
                return@launch
            }
            
            // Use captured address or fallback
            val address = _uiState.value.gpsAddress ?: "Unknown Location"
            val latitude = _uiState.value.gpsLatitude
            val longitude = _uiState.value.gpsLongitude
            
            // Strict Department Matching
            // Ensure the AI's category strictly matches one of our DB departments
            val department = _uiState.value.departments.find { 
                it.name.equals(category, ignoreCase = true) 
            }
            
            // If match found, use it. If NOT found (AI hallucinations or mismatches), FORCE "Other"
            val finalCategory = department?.name ?: "Other"
            val finalDepartmentId = department?.id ?: "Other" // Admin will see "Other" and can re-assign
            
            // 1. Try Name Match (Fuzzy: Check if GPS address contains the Location Name)
            // e.g. Address "123 Main St, Purakkad" contains "Purakkad" -> Match!
            var matchedLocation = _uiState.value.locations.find { loc ->
                address.contains(loc.name, ignoreCase = true)
            }
            
            // 2. If no name match, try Distance Proximity (GPS) - WITH THRESHOLD
            if (matchedLocation == null && latitude != null && longitude != null) {
                val nearestLocation = _uiState.value.locations.minByOrNull { loc ->
                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(
                        latitude, longitude,
                        loc.latitude, loc.longitude,
                        results
                    )
                    results[0]
                }
                
                // Only accept if within 3km (3000m) to avoid merging distinct towns
                if (nearestLocation != null) {
                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(
                        latitude, longitude,
                        nearestLocation.latitude, nearestLocation.longitude,
                        results
                    )
                    if (results[0] <= 3000) {
                        matchedLocation = nearestLocation
                    }
                }
            }
            
            // 3. If no match found, CREATE NEW LOCATION
            if (matchedLocation == null && latitude != null && longitude != null) {
                // Formatting name for new location (City/Area name prefers)
                val newLocationName = address.split(",").firstOrNull()?.trim() ?: address
                
                // Use Area Centroid if available, else fallback to user GPS (Privacy: area preferred)
                val latToSave = _uiState.value.areaLatitude ?: latitude
                val lngToSave = _uiState.value.areaLongitude ?: longitude
                
                // Try to save (Repo handles de-duplication)
                val result = masterDataRepository.addLocation(newLocationName, latToSave, lngToSave)
                matchedLocation = result.getOrNull()
            }
            
            // 4. Fallback: User's Profile Location (Only if creation failed)
            if (matchedLocation == null) {
                 val userLocationId = user.locationId
                 matchedLocation = _uiState.value.locations.find { it.id == userLocationId }
            }
            
            issueRepository.createIssue(
                title = title,
                description = description,
                category = finalCategory,
                assignedDepartment = finalDepartmentId,
                locationId = matchedLocation?.id ?: "",
                address = address,
                imageUris = images,
                reporterId = authRepository.currentUser?.uid ?: "",
                reporterName = user.name,
                // reporterProfileImage removed
                latitude = latitude,
                longitude = longitude,
                locationType = if (latitude != null) "GPS" else "MANUAL",
                gpsTimestamp = if (latitude != null) Timestamp.now() else null,
                aiGenerated = true,
                aiConfidence = aiResult.confidence,
                aiDetectedIssues = aiResult.detectedIssues,
                severity = aiResult.severity,
                userComments = userComments
            ).onSuccess {
                _uiState.update { it.copy(isLoading = false, isSubmitted = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to submit") }
            }
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

