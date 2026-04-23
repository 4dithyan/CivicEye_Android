package com.civiceye.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civiceye.data.model.User
import com.civiceye.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val user: User? = null,
    val error: String? = null,
    val passwordResetSent: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    
    val isLoggedIn: Boolean
        get() = authRepository.isLoggedIn
        
    fun checkSession() {
        if (isLoggedIn) {
             viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                authRepository.getCurrentUserData()
                    .onSuccess { user ->
                        if (user?.isCivilian() == true || user?.isStaff() == true) {
                            _uiState.update { 
                                it.copy(isLoading = false, isLoggedIn = true, user = user) 
                            }
                        } else {
                            authRepository.logout()
                             _uiState.update { 
                                it.copy(isLoading = false, error = "Invalid role for mobile app.")
                            }
                        }
                    }
                    .onFailure {
                        // Session invalid or network error, maybe stay logged in but show error? 
                        // Or just let them be, we can't route without role.
                        _uiState.update { it.copy(isLoading = false) }
                    }
             }
        }
    }
    
    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            authRepository.login(email, password)
                .onSuccess { _ ->
                    // Get user data from Firestore
                    authRepository.getCurrentUserData()
                        .onSuccess { user ->
                            if (user?.isCivilian() == true || user?.isStaff() == true) {
                                _uiState.update { 
                                    it.copy(isLoading = false, isLoggedIn = true, user = user) 
                                }
                            } else {
                                // Admin users or unknown roles
                                authRepository.logout()
                                _uiState.update { 
                                    it.copy(
                                        isLoading = false, 
                                        error = "Admin users must use the web dashboard."
                                    ) 
                                }
                            }
                        }
                        .onFailure { e ->
                            _uiState.update { 
                                it.copy(isLoading = false, error = e.message ?: "Failed to get user data") 
                            }
                        }
                }
                .onFailure { e ->
                    _uiState.update { 
                        it.copy(isLoading = false, error = e.message ?: "Login failed") 
                    }
                }
        }
    }
    
    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            authRepository.sendPasswordReset(email)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, passwordResetSent = true) }
                }
                .onFailure { e ->
                    _uiState.update { 
                        it.copy(isLoading = false, error = e.message ?: "Failed to send reset email") 
                    }
                }
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null, passwordResetSent = false) }
    }
}
