package com.civiceye.ui.track

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civiceye.data.model.Issue
import com.civiceye.data.repository.AuthRepository
import com.civiceye.data.repository.IssueRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackViewModel @Inject constructor(
    private val issueRepository: IssueRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _issues = MutableStateFlow<List<Issue>>(emptyList())
    val issues: StateFlow<List<Issue>> = _issues.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val currentUserId: String? = authRepository.getCurrentUserId()

    init {
        loadUserIssues()
    }

    fun loadUserIssues() {
        val userId = authRepository.getCurrentUserId()
        if (userId != null) {
            viewModelScope.launch {
                android.util.Log.d("TrackViewModel", "Loading issues for UserID: $userId")
                _isLoading.value = true
                issueRepository.getUserIssues(userId).collect { userIssues ->
                    android.util.Log.d("TrackViewModel", "Found ${userIssues.size} issues")
                    _issues.value = userIssues
                    _isLoading.value = false
                }
            }
        } else {
             android.util.Log.e("TrackViewModel", "User ID is null")
        }
    }
}
