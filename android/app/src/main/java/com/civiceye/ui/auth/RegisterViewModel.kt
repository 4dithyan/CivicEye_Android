
package com.civiceye.ui.auth

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civiceye.data.model.Location
import com.civiceye.data.repository.AuthRepository
import com.civiceye.data.repository.MasterDataRepository
import com.civiceye.util.LocationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegisterUiState(
    val isLoading: Boolean = false,
    val isRegistered: Boolean = false,
    val locations: List<Location> = emptyList(),
    val autoSelectedLocation: Location? = null,
    val error: String? = null
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val masterDataRepository: MasterDataRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()
    
    fun loadLocations() {
        viewModelScope.launch {
            // Use Flow for real-time updates when admin adds new locations
            masterDataRepository.getLocations().collect { locations ->
                val updatedLocations = locations.toMutableList().apply {
                    add(0, Location(id = "ADD_NEW_GPS", name = "📍 Add Current Location (Other)", isActive = true))
                }
                _uiState.update { it.copy(locations = updatedLocations) }
            }
        }
    }

    /**
     * Create a new location from current GPS
     */
    fun createLocationFromGPS(context: Context) {
        // Prevent duplicate fetches if we already found a location
        if (_uiState.value.autoSelectedLocation != null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                // 1. Get Location (High Accuracy for finding the city)
                val gpsLocation = LocationHelper.getCurrentLocation(context)
                
                if (gpsLocation == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Could not fetch GPS. Try outdoors.") }
                    return@launch
                }
                
                // 2. Get Address Object (City Centroid)
                val addressObj = LocationHelper.reverseGeocode(context, gpsLocation.latitude, gpsLocation.longitude)
                
                if (addressObj == null) {
                     _uiState.update { it.copy(isLoading = false, error = "Could not detect area name.") }
                     return@launch
                }

                // 3. Format Name (e.g. "Purakkad, Kerala")
                val areaName = LocationHelper.formatAddress(addressObj)
                
                // 4. Save to Backend using Area Centroid (Privacy: Ignore user's exact GPS)
                // We use addressObj.latitude which is the center of the found locality, not the user's house.
                masterDataRepository.addLocation(areaName, addressObj.latitude, addressObj.longitude)
                    .onSuccess { newLocation ->
                         // 5. Update UI - Set this as auto-selected
                         _uiState.update { 
                            it.copy(
                                isLoading = false, 
                                autoSelectedLocation = newLocation
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

    fun consumeAutoSelection() {
        _uiState.update { it.copy(autoSelectedLocation = null) }
    }
    
    fun register(
        email: String,
        password: String,
        name: String,
        phone: String,
        locationId: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            // Validation
            if (phone.length != 10 || !phone.all { it.isDigit() }) {
                _uiState.update { it.copy(isLoading = false, error = "Phone number must be exactly 10 digits") }
                return@launch
            }
            
            authRepository.register(
                email = email,
                password = password,
                name = name,
                phone = phone,
                locationId = locationId
            ).onSuccess {
                _uiState.update { it.copy(isLoading = false, isRegistered = true) }
            }.onFailure { e ->
                _uiState.update { 
                    it.copy(isLoading = false, error = e.message ?: "Registration failed") 
                }
            }
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
