package com.civiceye.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civiceye.data.model.Department
import com.civiceye.data.model.Issue
import com.civiceye.data.model.Location
import com.civiceye.data.model.User
import com.civiceye.data.repository.AuthRepository
import com.civiceye.data.repository.IssueRepository
import com.civiceye.data.repository.MasterDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val issues: List<Issue> = emptyList(),
    val locations: List<Location> = emptyList(),
    val departments: List<Department> = emptyList(),
    val selectedLocation: Location? = null,
    val selectedCategory: String? = null,
    val currentUser: User? = null,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val issueRepository: IssueRepository,
    private val masterDataRepository: MasterDataRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private var issuesJob: Job? = null
    
    val currentUserId: String
        get() = authRepository.currentUser?.uid ?: ""
    
    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // Load user data
            authRepository.getCurrentUserData()
                .onSuccess { user ->
                    _uiState.update { it.copy(currentUser = user) }
                    
                    // Load locations
                    loadLocations()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            
            // Load departments
            loadDepartments()
        }
    }
    
    private fun loadLocations() {
        viewModelScope.launch {
            // Use Flow for real-time updates when admin adds new locations
            masterDataRepository.getLocations().collect { locations ->
                // Default to "All Locations" (null) instead of forcing a selection
                // This ensures newly created issues in different locations are visible by default
                val selectedLocation = if (_uiState.value.selectedLocation != null) {
                    locations.find { it.id == _uiState.value.selectedLocation!!.id }
                } else {
                    null // Explicitly null for "All Locations"
                }

                _uiState.update { 
                    it.copy(locations = locations, selectedLocation = selectedLocation) 
                }
                
                // Load issues for selected location (or ALL if null)
                if (_uiState.value.issues.isEmpty()) {
                    loadIssues(selectedLocation?.id)
                }
            }
        }
    }
    
    private fun loadDepartments() {
        viewModelScope.launch {
            // Use Flow for real-time updates when admin adds new departments
            masterDataRepository.getDepartments().collect { departments ->
                _uiState.update { it.copy(departments = departments) }
            }
        }
    }
    
    private fun loadIssues(locationId: String?) {
        issuesJob?.cancel()
        issuesJob = viewModelScope.launch {
            val categoryId = _uiState.value.selectedCategory
            // Resolve Category Name from ID (because Issue stores Name, but Filter uses ID)
            val categoryName = _uiState.value.departments.find { it.id == categoryId }?.name
            
            val issueFlow = when {
                // Case 1: All Locations (locationId is null)
                locationId == null -> issueRepository.getAllIssues()
                
                // Case 2: Specific Location + Category
                categoryName != null -> issueRepository.getIssuesByCategory(locationId, categoryName)
                
                // Case 3: Specific Location (No Category)
                else -> issueRepository.getIssuesByLocation(locationId)
            }
            
            try {
                issueFlow.collect { issues ->
                    // Apply category filter manually if viewing All Locations + Category selected
                    // (Since getAllIssues() doesn't filter by category)
                    val filteredIssues = if (locationId == null && categoryName != null) {
                        issues.filter { it.category == categoryName }
                    } else {
                        issues
                    }
                    
                    _uiState.update { it.copy(isLoading = false, issues = filteredIssues) }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update { it.copy(isLoading = false, error = "Failed to load issues: ${e.message}") }
            }
        }
    }
    
    fun selectLocation(location: Location?) {
        _uiState.update { it.copy(selectedLocation = location, isLoading = true) }
        loadIssues(location?.id)
    }
    
    fun filterByCategory(categoryId: String?) {
        _uiState.update { it.copy(selectedCategory = categoryId, isLoading = true) }
        // Reload issues to apply filter
        val locId = _uiState.value.selectedLocation?.id
        loadIssues(locId)
    }
    
    fun refresh() {
        val selectedLoc = _uiState.value.selectedLocation
        _uiState.update { it.copy(isLoading = true) }
        loadIssues(selectedLoc?.id)
    }
    
    fun toggleLike(issueId: String) {
        viewModelScope.launch {
            issueRepository.toggleLike(issueId, currentUserId)
        }
    }
    
    fun toggleSupport(issueId: String) {
        viewModelScope.launch {
            issueRepository.toggleSupport(issueId, currentUserId)
        }
    }
    
    fun incrementShare(issueId: String) {
        viewModelScope.launch {
            issueRepository.incrementShare(issueId)
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
