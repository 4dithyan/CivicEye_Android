package com.civiceye.ui.issue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Comment import removed
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

data class IssueDetailUiState(
    val isLoading: Boolean = false,
    val issue: Issue? = null,
    // comments removed
    val error: String? = null
)

@HiltViewModel
class IssueDetailViewModel @Inject constructor(
    private val issueRepository: IssueRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(IssueDetailUiState())
    val uiState: StateFlow<IssueDetailUiState> = _uiState.asStateFlow()
    
    val currentUserId: String
        get() = authRepository.currentUser?.uid ?: ""
    
    private var issueId: String = ""
    
    fun loadIssue(id: String) {
        issueId = id
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            issueRepository.getIssue(id)
                .onSuccess { issue ->
                    _uiState.update { it.copy(isLoading = false, issue = issue) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            
            // Comments loading removed
        }
    }
    
    fun toggleLike() {
        viewModelScope.launch {
            issueRepository.toggleLike(issueId, currentUserId)
                .onSuccess { _ ->
                    // Refresh issue
                    loadIssue(issueId)
                }
        }
    }
    
    fun toggleSupport() {
        viewModelScope.launch {
            issueRepository.toggleSupport(issueId, currentUserId)
                .onSuccess {
                    loadIssue(issueId)
                }
        }
    }
    
    fun incrementShare() {
        viewModelScope.launch {
            issueRepository.incrementShare(issueId)
        }
    }
    
    // addComment removed
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
