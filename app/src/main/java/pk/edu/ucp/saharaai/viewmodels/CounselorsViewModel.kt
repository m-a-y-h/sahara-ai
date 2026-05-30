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
import pk.edu.ucp.saharaai.data.model.PaymentRequest
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService

class CounselorsViewModel : ViewModel() {
    var uid by mutableStateOf("")
        private set

    // Active counselors with profileComplete = true, ONLINE AND OFFLINE. Each
    // map carries `effectiveOnline = isOnline && !isInvisible` so the screen
    // can sort online-first and badge offline counselors as such.
    var allCounselors by mutableStateOf<List<Map<String, Any>>>(emptyList())
        private set

    /** Back-compat alias for callers that still ask for "onlineCounselors". */
    val onlineCounselors: List<Map<String, Any>>
        get() = allCounselors.filter { it["effectiveOnline"] as? Boolean == true }

    var paymentStatuses by mutableStateOf<List<PaymentRequest>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var submissionMessage by mutableStateOf("")
        private set

    fun initialize() {
        uid = Firebase.auth.currentUser?.uid.orEmpty()
        viewModelScope.launch {
            // Listen for ALL active counselors (online + offline) so offline
            // counselors still appear, rendered with a grey status pip.
            RealtimeDBService.listenToAllActiveCounselors().collect {
                allCounselors = it
                isLoading = false
            }
        }
        if (uid.isNotBlank()) {
            viewModelScope.launch {
                RealtimeDBService.listenToUserPaymentRequests(uid).collect {
                    paymentStatuses = it
                }
            }
        } else {
            isLoading = false
        }
    }

    fun submitPayment(
        counselorKey: String,
        counselorName: String,
        amount: String,
        accountTitle: String,
        reference: String,
        proofUri: Uri?,
        isEnglish: Boolean,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            RealtimeDBService.submitPaymentRequest(
                userId = uid,
                counselorKey = counselorKey,
                counselorName = counselorName,
                amountPkr = amount,
                accountTitle = accountTitle,
                transactionReference = reference,
                proofUri = proofUri
            ).onSuccess {
                submissionMessage = if (isEnglish) {
                    "Payment details submitted. Please wait while we confirm your amount; this usually takes half an hour to 1 day."
                } else {
                    "Payment details submit ho gayi. Raqam confirm hone ka intizar karein; aam tor par aadha ghanta se 1 din lagta hai."
                }
                onSuccess()
            }.onFailure {
                submissionMessage = it.message ?: "Upload failed."
            }
        }
    }
}
