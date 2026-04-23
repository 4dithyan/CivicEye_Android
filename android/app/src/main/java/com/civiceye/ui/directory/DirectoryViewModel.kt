package com.civiceye.ui.directory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civiceye.data.model.User
import com.civiceye.data.repository.MasterDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DirectoryUiState(
    val isLoading: Boolean = false,
    val staffMembers: List<User> = emptyList(),
    val filteredStaff: List<User> = emptyList(),
    val departmentNames: Map<String, String> = emptyMap(),
    val selectedDepartmentId: String? = null,
    val error: String? = null
)

@HiltViewModel
class DirectoryViewModel @Inject constructor(
    private val masterDataRepository: MasterDataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DirectoryUiState())
    val uiState: StateFlow<DirectoryUiState> = _uiState.asStateFlow()

    init {
        loadDirectory()
    }

    fun loadDirectory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Fetch Staff and Departments in parallel
            try {
                // 1. Fetch Departments (for mapping IDs to Names)
                val deptResult = masterDataRepository.getDepartmentsList()
                val departmentsMap = deptResult.getOrElse { emptyList() }
                    .associate { it.id to it.name }

                // 2. Fetch Staff
                val staffResult = masterDataRepository.getStaffMembers()
                
                if (staffResult.isSuccess) {
                    val allStaff = staffResult.getOrThrow()
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            staffMembers = allStaff,
                            filteredStaff = allStaff, // Initially all
                            departmentNames = departmentsMap
                        ) 
                    }
                } else {
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            error = "Failed to load directory: ${staffResult.exceptionOrNull()?.message}"
                        ) 
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun filterByDepartment(departmentId: String?) {
        _uiState.update { currentState ->
            val filtered = if (departmentId == null) {
                currentState.staffMembers
            } else {
                currentState.staffMembers.filter { it.departmentId == departmentId }
            }
            currentState.copy(
                selectedDepartmentId = departmentId,
                filteredStaff = filtered
            )
        }
    }
}
