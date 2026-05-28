package pk.edu.ucp.saharaai.viewmodels

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.model.BugReport
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService

class HelpCenterViewModel : ViewModel() {
    var reports by mutableStateOf<List<BugReport>>(emptyList())
        private set
    var isSubmitting by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    var submittedReportId by mutableStateOf("")
        private set

    fun initialize() {
        val uid = Firebase.auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) return
        viewModelScope.launch {
            RealtimeDBService.listenToUserBugReports(uid).collect { reports = it }
        }
    }

    fun submit(deviceModel: String, description: String, screenshotUri: Uri) {
        val user = Firebase.auth.currentUser
        if (user == null) {
            error = "Please sign in before reporting a bug."
            return
        }
        error = ""
        isSubmitting = true
        viewModelScope.launch {
            RealtimeDBService.submitBugReport(
                userId = user.uid,
                email = user.email.orEmpty(),
                deviceModel = deviceModel,
                description = description,
                screenshotUri = screenshotUri,
            ).onSuccess {
                submittedReportId = it
            }.onFailure {
                error = it.message.orEmpty()
            }
            isSubmitting = false
        }
    }

    fun clearSubmittedEvent() {
        submittedReportId = ""
    }
}
