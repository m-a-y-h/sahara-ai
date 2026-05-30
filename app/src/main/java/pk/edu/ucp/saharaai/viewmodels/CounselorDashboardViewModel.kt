package pk.edu.ucp.saharaai.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.model.CounselorProfile
import pk.edu.ucp.saharaai.data.model.EmergencyAlert
import pk.edu.ucp.saharaai.data.model.ModerationReport
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import pk.edu.ucp.saharaai.data.repository.CounselorRepository
import pk.edu.ucp.saharaai.data.repository.EmergencyRepository
import pk.edu.ucp.saharaai.data.repository.ReportRepository

class CounselorDashboardViewModel : ViewModel() {

    val signedInCounselorId: String get() = Firebase.auth.currentUser?.uid.orEmpty()

    private val _profile        = MutableStateFlow<CounselorProfile?>(null)
    val profile: StateFlow<CounselorProfile?> = _profile.asStateFlow()

    private val _openAlerts     = MutableStateFlow<List<EmergencyAlert>>(emptyList())
    val openAlerts: StateFlow<List<EmergencyAlert>> = _openAlerts.asStateFlow()

    private val _openReports    = MutableStateFlow<List<ModerationReport>>(emptyList())
    val openReports: StateFlow<List<ModerationReport>> = _openReports.asStateFlow()

    private val _activeSessions = MutableStateFlow<List<String>>(emptyList())
    val activeSessions: StateFlow<List<String>> = _activeSessions.asStateFlow()

    
    private val _chatSessions   = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val chatSessions: StateFlow<List<Map<String, Any>>> = _chatSessions.asStateFlow()

    private val _isChatSessionsLoading = MutableStateFlow(true)
    val isChatSessionsLoading: StateFlow<Boolean> = _isChatSessionsLoading.asStateFlow()

    private val _isOnline       = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    // Counselor's manual "appear offline" override. Auto-online is driven by
    // Firebase presence (see attachPresence below); this flag lets the counselor
    // appear offline to users even while their app is connected. Effective
    // visibility is `isOnline && !isInvisible`.
    private val _isInvisible = MutableStateFlow(false)
    val isInvisible: StateFlow<Boolean> = _isInvisible.asStateFlow()

    private var presenceCloser: (() -> Unit)? = null

    private val _callEnabled    = MutableStateFlow(false)
    val callEnabled: StateFlow<Boolean> = _callEnabled.asStateFlow()

    
    private val _realtimeData   = MutableStateFlow<Map<String, Any>?>(null)
    val realtimeData: StateFlow<Map<String, Any>?> = _realtimeData.asStateFlow()

    private val _isLoading      = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error          = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    
    fun loadProfile(counselorId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = CounselorRepository.getCounselor(counselorId)
            result.onSuccess { profile ->
                _profile.value = profile
                _isOnline.value = profile?.isAvailable ?: false
            }.onFailure {
                
                val byUser = CounselorRepository.getCounselorByUserId(counselorId)
                byUser.onSuccess { profile ->
                    _profile.value = profile
                    _isOnline.value = profile?.isAvailable ?: false
                }.onFailure {
                    _error.value = it.message
                }
            }
            _isLoading.value = false
        }
    }

    
    fun loadOpenAlerts(ngoId: String) {
        viewModelScope.launch {
            val result = EmergencyRepository.getOpenAlerts(ngoId)
            result.onSuccess { _openAlerts.value = it }
            result.onFailure { _error.value = it.message }
        }
    }

    
    fun loadReports() {
        viewModelScope.launch {
            val result = ReportRepository.getOpenReports()
            result.onSuccess { _openReports.value = it }
            result.onFailure { _error.value = it.message }
        }
    }

    
    fun listenToChatSessions(counselorId: String) {
        viewModelScope.launch {
            _isChatSessionsLoading.value = true
            CounselorRepository.getCounselorChatsFlow(counselorId).collect { sessions ->
                _chatSessions.value = sessions
                _isChatSessionsLoading.value = false
            }
        }
    }

    
    fun listenToRealtimeProfile(key: String) {
        viewModelScope.launch {
            RealtimeDBService.listenToCounselorData(key).collect { data ->
                _realtimeData.value = data
                _isOnline.value = data?.get("isOnline") as? Boolean ?: false
                _isInvisible.value = data?.get("isInvisible") as? Boolean ?: false
                _callEnabled.value = data?.get("callEnabled") as? Boolean ?: false
            }
        }
    }

    /** Wire Firebase presence so this counselor is auto-online whenever the
     *  dashboard is foregrounded and connected. Idempotent — calling it again
     *  for the same key is harmless; calling it for a NEW key tears down the
     *  previous listener first. Pair with [detachPresence] when the dashboard
     *  is destroyed (or with `onCleared`, which we already call). */
    fun attachPresence(key: String) {
        if (key.isBlank()) return
        presenceCloser?.invoke()
        presenceCloser = RealtimeDBService.setupCounselorPresence(key)
    }

    fun detachPresence() {
        presenceCloser?.invoke()
        presenceCloser = null
    }

    override fun onCleared() {
        super.onCleared()
        detachPresence()
    }

    /** Counselor's manual "appear offline" toggle. Online is still driven by
     *  presence; this flag only filters how users see the counselor. */
    fun toggleInvisible(key: String) {
        if (key.isBlank()) return
        val next = !_isInvisible.value
        _isInvisible.value = next                                // optimistic
        viewModelScope.launch {
            RealtimeDBService.setCounselorInvisible(key, next)
                .onFailure {
                    _error.value = it.message
                    _isInvisible.value = !next                   // rollback
                }
        }
    }

    /** Legacy direct online toggle. Kept for back-compat with older callers
     *  (e.g. fallback admin flows). New screens should use [toggleInvisible]
     *  and rely on [attachPresence] for online state. */
    fun toggleOnlineStatus(key: String) {
        val newStatus = !_isOnline.value
        _isOnline.value = newStatus
        viewModelScope.launch {
            RealtimeDBService.setOnlineStatus(key, newStatus)
        }
    }

    fun setCallAvailability(key: String, enabled: Boolean) {
        _callEnabled.value = enabled
        viewModelScope.launch {
            RealtimeDBService.setCounselorCallAvailability(key, enabled)
                .onFailure { _error.value = it.message }
        }
    }

    
    fun listenToChatSessionsRealtime(key: String) {
        viewModelScope.launch {
            _isChatSessionsLoading.value = true
            RealtimeDBService.listenToCounselorChats(key).collect { sessions ->
                _chatSessions.value = sessions
                _isChatSessionsLoading.value = false
            }
        }
    }

    
    fun toggleAvailability(counselorId: String) {
        val newStatus = !_isOnline.value
        _isOnline.value = newStatus
        viewModelScope.launch {
            val effectiveId = _profile.value?.counselorId?.ifBlank { counselorId } ?: counselorId
            CounselorRepository.setAvailability(effectiveId, newStatus)
            _profile.value = _profile.value?.copy(isAvailable = newStatus)
        }
    }

    
    fun acknowledgeAlert(alertId: String, counselorId: String) {
        viewModelScope.launch {
            EmergencyRepository.acknowledge(alertId, counselorId)
            _openAlerts.value = _openAlerts.value.filter { it.alertId != alertId }
        }
    }

    
    fun resolveAlert(alertId: String, counselorId: String) {
        viewModelScope.launch {
            EmergencyRepository.resolve(alertId, counselorId)
            _openAlerts.value = _openAlerts.value.filter { it.alertId != alertId }
            
            val effectiveId = _profile.value?.counselorId?.ifBlank { counselorId } ?: counselorId
            CounselorRepository.completeSession(effectiveId)
        }
    }

    
    fun resolveReport(reportId: String, counselorId: String, note: String) {
        viewModelScope.launch {
            ReportRepository.resolveReport(reportId, counselorId, note)
            _openReports.value = _openReports.value.filter { it.reportId != reportId }
        }
    }

    
    fun dismissReport(reportId: String, counselorId: String) {
        viewModelScope.launch {
            ReportRepository.dismissReport(reportId, counselorId, "No action needed.")
            _openReports.value = _openReports.value.filter { it.reportId != reportId }
        }
    }

    fun clearError() { _error.value = null }
}
