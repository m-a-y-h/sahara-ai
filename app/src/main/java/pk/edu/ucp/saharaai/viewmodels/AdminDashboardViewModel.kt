package pk.edu.ucp.saharaai.viewmodels

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.model.AvatarRequest
import pk.edu.ucp.saharaai.data.model.BugReport
import pk.edu.ucp.saharaai.data.model.PaymentRequest
import pk.edu.ucp.saharaai.data.model.RegistrationRequest
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService

class AdminDashboardViewModel : ViewModel() {
    var applications by mutableStateOf<List<RegistrationRequest>>(emptyList())
        private set
    var payments by mutableStateOf<List<PaymentRequest>>(emptyList())
        private set
    var bugReports by mutableStateOf<List<BugReport>>(emptyList())
        private set
    var avatarRequests by mutableStateOf<List<AvatarRequest>>(emptyList())
        private set
    var error by mutableStateOf("")
        private set

    init {
        viewModelScope.launch {
            RealtimeDBService.listenToRegistrationRequests()
                .catch { reportListenerError("application forms", it) }
                .collect { applications = it }
        }
        viewModelScope.launch {
            RealtimeDBService.listenToPaymentRequests()
                .catch { reportListenerError("payment requests", it) }
                .collect { payments = it }
        }
        viewModelScope.launch {
            RealtimeDBService.listenToBugReports()
                .catch { reportListenerError("bug reports", it) }
                .collect { bugReports = it }
        }
        viewModelScope.launch {
            RealtimeDBService.listenToAvatarRequests()
                .catch { reportListenerError("avatar requests", it) }
                .collect { avatarRequests = it }
        }
    }

    private fun reportListenerError(section: String, throwable: Throwable) {
        val raw = throwable.message.orEmpty()
        error = when {
            raw.contains("permission denied", ignoreCase = true) ->
                "Admin dashboard cannot read $section yet. Re-enter the admin key after Firebase Auth is ready, and make sure the latest database rules are deployed."
            raw.isBlank() ->
                "Admin dashboard could not load $section."
            else ->
                "Admin dashboard could not load $section: $raw"
        }
    }

    fun approveRegistration(
        request: RegistrationRequest,
        issuedKey: String,
        notes: String,
        approvedAttributeIds: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            error = ""
            RealtimeDBService.approveRegistrationRequest(request, issuedKey, "ADMIN", notes, approvedAttributeIds)
                .onFailure { error = it.message.orEmpty() }
        }
    }

    fun rejectRegistration(requestId: String, notes: String) {
        viewModelScope.launch {
            error = ""
            RealtimeDBService.rejectRegistrationRequest(requestId, "ADMIN", notes)
                .onFailure { error = it.message.orEmpty() }
        }
    }

    fun approvePayment(payment: PaymentRequest, notes: String) {
        viewModelScope.launch {
            error = ""
            RealtimeDBService.approvePaymentRequest(payment, "ADMIN", notes)
                .onFailure { error = it.message.orEmpty() }
        }
    }

    fun rejectPayment(payment: PaymentRequest, notes: String, attachmentUri: Uri? = null) {
        viewModelScope.launch {
            error = ""
            RealtimeDBService.rejectPaymentRequest(payment, "ADMIN", notes, attachmentUri)
                .onFailure { error = it.message.orEmpty() }
        }
    }

    fun resolveBugReport(reportId: String) {
        viewModelScope.launch {
            error = ""
            RealtimeDBService.resolveBugReport(reportId, "ADMIN")
                .onFailure { error = it.message.orEmpty() }
        }
    }

    fun approveAvatar(request: AvatarRequest, comment: String) {
        viewModelScope.launch {
            error = ""
            RealtimeDBService.approveAvatarRequest(request, "ADMIN", comment)
                .onFailure { error = it.message.orEmpty() }
        }
    }

    fun rejectAvatar(request: AvatarRequest, comment: String) {
        viewModelScope.launch {
            error = ""
            RealtimeDBService.rejectAvatarRequest(request, "ADMIN", comment)
                .onFailure { error = it.message.orEmpty() }
        }
    }

    fun blockAvatarUser(request: AvatarRequest, comment: String) {
        viewModelScope.launch {
            error = ""
            RealtimeDBService.blockFromAvatarRequest(request, "ADMIN", comment)
                .onFailure { error = it.message.orEmpty() }
        }
    }
}
