package pk.edu.ucp.saharaai.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.model.VoiceAnalyzeResponse
import pk.edu.ucp.saharaai.data.model.VoiceLevel
import pk.edu.ucp.saharaai.data.repository.SaharaVoiceRepository


sealed class VoiceUiState {
    
    object Idle : VoiceUiState()

    
    data class Captured(
        val audioBytes: ByteArray,
        val durationSeconds: Int,
        val mimeType: String,
    ) : VoiceUiState() {
        override fun equals(other: Any?): Boolean =
            other is Captured && audioBytes === other.audioBytes
        override fun hashCode(): Int = audioBytes.hashCode()
    }

    
    object Analyzing : VoiceUiState()

    
    data class Result(val response: VoiceAnalyzeResponse, val level: VoiceLevel) : VoiceUiState()

    
    data class Error(val message: String, val reasons: List<String> = emptyList()) : VoiceUiState()
}

class VoiceAnalysisViewModel : ViewModel() {

    
    private val _state = MutableStateFlow<VoiceUiState>(VoiceUiState.Idle)
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    
    var isRecording by mutableStateOf(false)
        private set

    
    var recordingSeconds by mutableStateOf(0)
        private set

    private val maxDurationSeconds: Int = 60
    private val minDurationSeconds: Int = 3
    private var timerJob: Job? = null

    

    
    fun onRecordingStarted() {
        isRecording = true
        recordingSeconds = 0
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isRecording && recordingSeconds < maxDurationSeconds) {
                kotlinx.coroutines.delay(1000)
                recordingSeconds += 1
            }
        }
    }

    
    fun onRecordingStopped(
        audioBytes: ByteArray,
        mimeType: String,
    ) {
        timerJob?.cancel()
        val seconds = recordingSeconds
        isRecording = false
        if (audioBytes.isEmpty() || seconds < minDurationSeconds) {
            reset()
            _state.value = VoiceUiState.Error(
                message = "Recording was too short — please try again with a longer clip.",
            )
            return
        }
        _state.value = VoiceUiState.Captured(
            audioBytes = audioBytes,
            durationSeconds = seconds,
            mimeType = mimeType,
        )
    }

    
    fun onRecordingCancelled(reason: String? = null) {
        timerJob?.cancel()
        isRecording = false
        recordingSeconds = 0
        if (reason != null) {
            _state.value = VoiceUiState.Error(message = reason)
        } else {
            _state.value = VoiceUiState.Idle
        }
    }

    

    
    fun analyze() {
        val current = _state.value
        if (current !is VoiceUiState.Captured) return

        _state.value = VoiceUiState.Analyzing
        viewModelScope.launch {
            val result = SaharaVoiceRepository.analyze(current.audioBytes, current.mimeType)
            result.fold(
                onSuccess = { response ->
                    if (response.passed != true || response.screening == null) {
                        val reasons = (response.reasons.orEmpty() +
                            response.screening?.reasons.orEmpty()).distinct()
                        _state.value = VoiceUiState.Error(
                            message = "Couldn't read the clip — please retake.",
                            reasons = reasons,
                        )
                        return@fold
                    }
                    val level = VoiceLevel.fromWire(response.screening.level)
                    _state.value = VoiceUiState.Result(response, level)
                    
                    launch { SaharaVoiceRepository.recordCheckIn(response) }
                },
                onFailure = { err ->
                    _state.value = VoiceUiState.Error(
                        message = err.message ?: "Sahara Voice couldn't reach the server.",
                    )
                },
            )
        }
    }

    fun reset() {
        timerJob?.cancel()
        isRecording = false
        recordingSeconds = 0
        _state.value = VoiceUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
