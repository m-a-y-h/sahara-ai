package pk.edu.ucp.saharaai.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.model.LensLevel
import pk.edu.ucp.saharaai.data.model.LensScanResponse
import pk.edu.ucp.saharaai.data.repository.SaharaLensRepository
import pk.edu.ucp.saharaai.lens.LensValidation


sealed class LensUiState {
    
    object Capturing : LensUiState()

    
    data class Reviewing(val imageBytes: ByteArray) : LensUiState() {
        override fun equals(other: Any?): Boolean = other is Reviewing && imageBytes === other.imageBytes
        override fun hashCode(): Int = imageBytes.hashCode()
    }

    
    object Analyzing : LensUiState()

    
    data class Result(val response: LensScanResponse, val level: LensLevel) : LensUiState()

    
    data class Error(val message: String, val reasons: List<String> = emptyList()) : LensUiState()
}

class LensViewModel : ViewModel() {

    private val _state = MutableStateFlow<LensUiState>(LensUiState.Capturing)
    val state: StateFlow<LensUiState> = _state.asStateFlow()

    
    
    

    private val _validation = MutableStateFlow<LensValidation>(
        LensValidation.Invalid(
            reason = "Starting camera…",
            kind = LensValidation.InvalidKind.NO_FACE,
        )
    )
    val validation: StateFlow<LensValidation> = _validation.asStateFlow()

    
    private val _holdSeconds = MutableStateFlow(0)
    val holdSeconds: StateFlow<Int> = _holdSeconds.asStateFlow()

    
    private val _readyToCapture = MutableStateFlow(false)
    val readyToCapture: StateFlow<Boolean> = _readyToCapture.asStateFlow()

    private var holdJob: Job? = null

    
    fun onAnalyzerValidation(validation: LensValidation) {
        _validation.value = validation
        if (_state.value !is LensUiState.Capturing) return  
        if (validation is LensValidation.Valid) {
            if (holdJob?.isActive == true) return  
            holdJob = viewModelScope.launch {
                while (_holdSeconds.value < REQUIRED_HOLD_SECONDS) {
                    delay(1_000)
                    
                    if (_validation.value !is LensValidation.Valid) return@launch
                    _holdSeconds.value = _holdSeconds.value + 1
                }
                _readyToCapture.value = true
            }
        } else {
            holdJob?.cancel()
            holdJob = null
            _holdSeconds.value = 0
            _readyToCapture.value = false
        }
    }

    
    fun onCaptured(jpegBytes: ByteArray) {
        _readyToCapture.value = false
        _holdSeconds.value = 0
        holdJob?.cancel()
        holdJob = null
        if (jpegBytes.isEmpty()) {
            _state.value = LensUiState.Error("Empty capture — try again.")
            return
        }
        _state.value = LensUiState.Reviewing(jpegBytes)
    }

    
    fun onRetake() {
        _state.value = LensUiState.Capturing
    }

    
    fun onConfirm() {
        val current = _state.value
        if (current !is LensUiState.Reviewing) return

        _state.value = LensUiState.Analyzing
        viewModelScope.launch {
            val scanResult = SaharaLensRepository.scan(current.imageBytes)
            scanResult.fold(
                onSuccess = { response ->
                    val qualityPassed = response.qualityGate?.passed ?: false
                    if (!qualityPassed) {
                        val reasons = response.qualityGate?.reasons.orEmpty()
                        _state.value = LensUiState.Error(
                            message = "Photo couldn't be analysed — please retake.",
                            reasons = reasons,
                        )
                        return@fold
                    }
                    val level = LensLevel.fromWire(response.screening?.level)
                    _state.value = LensUiState.Result(response, level)
                    
                    
                    
                    launch { SaharaLensRepository.recordCheckIn(response) }
                },
                onFailure = { err ->
                    _state.value = LensUiState.Error(
                        message = err.message ?: "Sahara Lens couldn't reach the server.",
                    )
                },
            )
        }
    }

    fun reset() {
        _readyToCapture.value = false
        _holdSeconds.value = 0
        holdJob?.cancel()
        holdJob = null
        _validation.value = LensValidation.Invalid(
            reason = "Starting camera…",
            kind = LensValidation.InvalidKind.NO_FACE,
        )
        _state.value = LensUiState.Capturing
    }

    override fun onCleared() {
        super.onCleared()
        holdJob?.cancel()
    }

    companion object {
        const val REQUIRED_HOLD_SECONDS: Int = 5
    }
}
