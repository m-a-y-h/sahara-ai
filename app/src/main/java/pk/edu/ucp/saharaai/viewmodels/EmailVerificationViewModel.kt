package pk.edu.ucp.saharaai.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.BuildConfig
import pk.edu.ucp.saharaai.util.EmailOtpService

class EmailVerificationViewModel : ViewModel() {
    var isLoading by mutableStateOf(false)
        private set
    var isSending by mutableStateOf(false)
        private set
    var errorMsg by mutableStateOf("")
        private set
    var isVerified by mutableStateOf(false)
        private set
    var isNotConfigured by mutableStateOf(false)
        private set

    fun clearError() {
        errorMsg = ""
    }

    fun reportError(message: String) {
        errorMsg = message
    }

    fun sendOtp(email: String, name: String, uid: String, isEnglish: Boolean) {
        if (uid.isBlank() || isSending) return
        viewModelScope.launch {
            isSending = true
            errorMsg = ""
            val otp = EmailOtpService.generateOtp()
            EmailOtpService.storeOtp(uid, otp).onFailure { error ->
                errorMsg = error.message ?: "Storage error."
                isSending = false
                return@launch
            }
            when (
                val result = EmailOtpService.sendOtpEmail(
                    toEmail = email,
                    toName = name.ifBlank { email.substringBefore("@") },
                    otp = otp,
                    serviceId = BuildConfig.EMAILJS_SERVICE_ID,
                    templateId = BuildConfig.EMAILJS_TEMPLATE_ID,
                    publicKey = BuildConfig.EMAILJS_PUBLIC_KEY
                )
            ) {
                is EmailOtpService.SendResult.Success -> Unit
                is EmailOtpService.SendResult.NotConfigured -> isNotConfigured = true
                is EmailOtpService.SendResult.RateLimited -> {
                    errorMsg = if (isEnglish) "Please wait before resending." else "Dobara bhejna ke liye intzaar karein."
                }
                is EmailOtpService.SendResult.Failure -> {
                    errorMsg = if (isEnglish) {
                        "Failed to send email: ${result.error}"
                    } else {
                        "Email nahi bheji ja saki: ${result.error}"
                    }
                }
            }
            isSending = false
        }
    }

    fun verifyOtp(uid: String, enteredCode: String, isEnglish: Boolean) {
        if (isLoading) return
        isLoading = true
        errorMsg = ""
        viewModelScope.launch {
            when (val result = EmailOtpService.verifyOtp(uid, enteredCode)) {
                is EmailOtpService.VerifyResult.Success -> {
                    EmailOtpService.markEmailVerified(uid)
                    isVerified = true
                }
                is EmailOtpService.VerifyResult.WrongCode -> {
                    errorMsg = if (isEnglish) "Incorrect code. Please try again." else "Galat code. Dobara koshish karein."
                }
                is EmailOtpService.VerifyResult.Expired -> {
                    errorMsg = if (isEnglish) "Code has expired. Please resend." else "Code expire ho gaya. Dobara bhejein."
                }
                is EmailOtpService.VerifyResult.AlreadyUsed -> {
                    errorMsg = if (isEnglish) "Code already used. Please resend." else "Code already use ho chuka hai."
                }
                is EmailOtpService.VerifyResult.NotFound -> {
                    errorMsg = if (isEnglish) "No code found. Please resend." else "Koi code nahi mila. Dobara bhejein."
                }
                is EmailOtpService.VerifyResult.Error -> errorMsg = result.message
            }
            isLoading = false
        }
    }
}
