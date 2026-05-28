package pk.edu.ucp.saharaai.viewmodels

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
            RealtimeDBService.listenToRegistrationRequests().collect { applications = it }
        }
        viewModelScope.launch {
            RealtimeDBService.listenToPaymentRequests().collect { payments = it }
        }
        viewModelScope.launch {
            RealtimeDBService.listenToBugReports().collect { bugReports = it }
        }
        viewModelScope.launch {
            RealtimeDBService.listenToAvatarRequests().collect { avatarRequests = it }
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
