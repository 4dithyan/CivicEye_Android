package com.civiceye.ui.staff

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civiceye.data.model.Issue
import com.civiceye.data.repository.AuthRepository
import com.civiceye.data.repository.IssueRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.civiceye.data.model.User

data class StaffUiState(
    val isLoading: Boolean = false,
    val assignedIssues: List<Issue> = emptyList(),
    val userProfile: User? = null,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class StaffViewModel @Inject constructor(
    private val issueRepository: IssueRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(StaffUiState())
    val uiState: StateFlow<StaffUiState> = _uiState.asStateFlow()
    
    init {
        loadData()
    }
    
    fun loadData() {
        val currentUser = authRepository.currentUser
        if (currentUser == null) {
            _uiState.update { it.copy(error = "User not logged in") }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // 1. Fetch User Profile (Real-time)
            launch {
                authRepository.getUserDataFlow(currentUser.uid).collect { user ->
                     _uiState.update { it.copy(
                        userProfile = user
                    ) }
                }
            }
            
            // 2. Fetch Issues (Real-time)
            launch {
                issueRepository.getAssignedIssues(currentUser.uid).collect { issues ->
                    _uiState.update { it.copy(
                        isLoading = false,
                        assignedIssues = issues
                    ) }
                }
            }
        }
    }
    
    fun updateAvailability(status: String) {
        val user = _uiState.value.userProfile ?: return
        
        viewModelScope.launch {
            // Optimistic update
            _uiState.update { it.copy(
                userProfile = it.userProfile?.copy(availabilityStatus = status)
            ) }
            
            // Update in Firestore
            // We need a repository method for this, specifically for availability or generic update
            // Using generic updateProfile for now, but ideally a specific method
            // Since updateProfile takes specific args, we might need to add a specific method to AuthRepository
            // For now, let's assume we add `updateAvailability` to AuthRepository or do a direct Firestore patch if needed
            // But strict architecture says use Repository. 
            // Let's rely on AuthRepository having a generic or specific update.
             authRepository.updateUserStatus(status)
                .onFailure { e ->
                    // Revert on failure
                    _uiState.update { it.copy(
                        error = "Failed to update status: ${e.message}",
                        userProfile = it.userProfile?.copy(availabilityStatus = user.availabilityStatus)
                    ) }
                }
        }
    }
    
    fun startIssue(issueId: String) {
        viewModelScope.launch {
            issueRepository.updateStatus(issueId, Issue.STATUS_IN_PROGRESS)
                .onSuccess {
                    _uiState.update { it.copy(successMessage = "Issue marked In Progress") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }
    
    fun resolveIssue(issueId: String, notes: String, proofImages: List<Uri>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            issueRepository.resolveIssue(issueId, notes, proofImages)
                 .onSuccess {
                    _uiState.update { it.copy(
                        isLoading = false, 
                        successMessage = "Issue Resolved! Admin will verify."
                    ) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }
    
    fun logout() {
        authRepository.logout()
    }
    
    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
    
    fun refresh() {
        loadData()
    }
}
