package pk.edu.ucp.saharaai.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.model.CounselorProfile
import pk.edu.ucp.saharaai.data.repository.CounselorRepository

sealed class CounselorUiState {
    object Idle        : CounselorUiState()
    object Loading     : CounselorUiState()
    object Success     : CounselorUiState()
    data class Error(val message: String) : CounselorUiState()
}

class CounselorViewModel : ViewModel() {

    private val _counselors   = MutableStateFlow<List<CounselorProfile>>(emptyList())
    val counselors: StateFlow<List<CounselorProfile>> = _counselors.asStateFlow()

    private val _selected     = MutableStateFlow<CounselorProfile?>(null)
    val selectedCounselor: StateFlow<CounselorProfile?> = _selected.asStateFlow()

    private val _uiState      = MutableStateFlow<CounselorUiState>(CounselorUiState.Idle)
    val uiState: StateFlow<CounselorUiState> = _uiState.asStateFlow()

    private val _isLoading    = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _sessionId    = MutableStateFlow("")
    val sessionId: StateFlow<String> = _sessionId.asStateFlow()

    
    fun loadCounselors() {
        viewModelScope.launch {
            _isLoading.value = true
            _uiState.value = CounselorUiState.Loading
            val result = CounselorRepository.getCounselors()
            if (result.isSuccess) {
                _counselors.value = result.getOrDefault(emptyList())
                _uiState.value = CounselorUiState.Success
            } else {
                _uiState.value = CounselorUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to load counselors."
                )
            }
            _isLoading.value = false
        }
    }

    
    fun selectCounselor(counselor: CounselorProfile, userId: String) {
        _selected.value = counselor
        _sessionId.value = CounselorRepository.buildSessionId(userId, counselor.counselorId)
    }

    
    fun rateCounselor(counselorId: String, rating: Float) {
        viewModelScope.launch {
            CounselorRepository.rateCounselor(counselorId, rating)
            
            loadCounselors()
        }
    }

    
    fun setAvailability(counselorId: String, available: Boolean) {
        viewModelScope.launch {
            CounselorRepository.setAvailability(counselorId, available)
        }
    }

    fun clearError() { _uiState.value = CounselorUiState.Idle }
}
