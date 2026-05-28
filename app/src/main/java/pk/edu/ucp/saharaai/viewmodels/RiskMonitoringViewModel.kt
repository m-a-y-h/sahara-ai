package pk.edu.ucp.saharaai.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.model.AssessmentCycleStatus
import pk.edu.ucp.saharaai.data.model.CumulativeRiskReport
import pk.edu.ucp.saharaai.data.model.MonitoringPeriod
import pk.edu.ucp.saharaai.data.model.MonitoringStartNotice
import pk.edu.ucp.saharaai.data.model.RiskProfile
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import pk.edu.ucp.saharaai.data.repository.RiskProfileRepository
import pk.edu.ucp.saharaai.ui.screens.GlobalAppState

/**
 * Drives both the dashboard ring + the "monitoring started" popup.
 *
 * The popup is a one-shot: when the dashboard opens, we look for a notice
 * doc with ``shown == false``. If present, [pendingStartNotice] becomes
 * non-null and the dashboard shows the modal; tapping "Got it" calls
 * [acknowledgeStartNotice], which marks the doc shown and clears the flow.
 */
class RiskMonitoringViewModel : ViewModel() {

    private val _profile = MutableStateFlow<RiskProfile?>(null)
    val profile: StateFlow<RiskProfile?> = _profile.asStateFlow()

    private val _period = MutableStateFlow<MonitoringPeriod?>(null)
    val period: StateFlow<MonitoringPeriod?> = _period.asStateFlow()

    private val _cycleStatus = MutableStateFlow<AssessmentCycleStatus?>(null)
    val cycleStatus: StateFlow<AssessmentCycleStatus?> = _cycleStatus.asStateFlow()

    private val _pendingStartNotice = MutableStateFlow<MonitoringStartNotice?>(null)
    val pendingStartNotice: StateFlow<MonitoringStartNotice?> = _pendingStartNotice.asStateFlow()

    private val _pendingCumulativeReport = MutableStateFlow<CumulativeRiskReport?>(null)
    val pendingCumulativeReport: StateFlow<CumulativeRiskReport?> = _pendingCumulativeReport.asStateFlow()

    /** Call from the dashboard's LaunchedEffect(Unit). */
    fun refresh() {
        viewModelScope.launch {
            RiskProfileRepository.loadProfile().onSuccess { _profile.value = it }
            RiskProfileRepository.loadMonitoringPeriod().onSuccess { _period.value = it }
            RiskProfileRepository.loadAssessmentCycleStatus().onSuccess { status ->
                _cycleStatus.value = status
                if (status?.assessmentRequired == true &&
                    GlobalAppState.lastAssessmentTimestamp <= status.completedAtEpochMs
                ) {
                    GlobalAppState.hasCompletedInitialAssessment = false
                    GlobalAppState.hasEverCompletedAssessment = true
                    Firebase.auth.currentUser?.uid?.let { uid ->
                        val cycleId = status.latestReportId.ifBlank { status.currentCycleId }
                        RealtimeDBService.resetCompletedCycleData(uid, cycleId)
                    }
                }
            }
            RiskProfileRepository.loadPendingStartNotice().onSuccess { _pendingStartNotice.value = it }
            RiskProfileRepository.loadPendingCumulativeReport().onSuccess { _pendingCumulativeReport.value = it }
        }
    }

    fun acknowledgeStartNotice() {
        _pendingStartNotice.value = null
        viewModelScope.launch { RiskProfileRepository.markStartNoticeShown() }
    }

    fun acknowledgeCumulativeReport(cycleId: String) {
        _pendingCumulativeReport.value = null
        viewModelScope.launch { RiskProfileRepository.markCumulativeReportAcknowledged(cycleId) }
    }
}
