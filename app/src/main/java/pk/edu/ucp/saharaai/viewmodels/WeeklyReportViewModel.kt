package pk.edu.ucp.saharaai.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.model.WeeklyListeningReport
import pk.edu.ucp.saharaai.data.repository.ListeningActivityRepository

/**
 * Drives both the WeeklyReportScreen list view and the dashboard popup.
 *
 * State flows:
 *   * `reports`           — full history (newest first)
 *   * `pendingPopup`      — most recent report the user hasn't dismissed yet,
 *                           null when nothing fresh is waiting
 *   * `isLoading` / `error` — surface conditions for the UI
 */
class WeeklyReportViewModel : ViewModel() {

    private val _reports = MutableStateFlow<List<WeeklyListeningReport>>(emptyList())
    val reports: StateFlow<List<WeeklyListeningReport>> = _reports.asStateFlow()

    private val _pendingPopup = MutableStateFlow<WeeklyListeningReport?>(null)
    val pendingPopup: StateFlow<WeeklyListeningReport?> = _pendingPopup.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            ListeningActivityRepository.listWeeklyReports().fold(
                onSuccess = { _reports.value = it },
                onFailure = { _error.value = it.message ?: "Couldn't load weekly reports." },
            )
            _isLoading.value = false
        }
    }

    /** Called from the dashboard composable's LaunchedEffect on entry. */
    fun checkForPopup() {
        viewModelScope.launch {
            ListeningActivityRepository.unseenLatestReport().onSuccess { report ->
                _pendingPopup.value = report
            }
        }
    }

    fun dismissPopup(weekStartIso: String) {
        _pendingPopup.value = null
        viewModelScope.launch {
            ListeningActivityRepository.dismissReport(weekStartIso)
        }
    }

    fun deleteReport(weekStartIso: String) {
        viewModelScope.launch {
            ListeningActivityRepository.deleteReport(weekStartIso).onSuccess {
                _reports.value = _reports.value.filterNot { it.weekStartIso == weekStartIso }
                if (_pendingPopup.value?.weekStartIso == weekStartIso) {
                    _pendingPopup.value = null
                }
            }
        }
    }

    fun clearError() { _error.value = null }
}
