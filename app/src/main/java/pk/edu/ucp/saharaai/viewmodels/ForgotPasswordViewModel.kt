package pk.edu.ucp.saharaai.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import pk.edu.ucp.saharaai.data.repository.AuthRepository
import pk.edu.ucp.saharaai.data.repository.FirebaseAuthFailure

enum class ResetStep {
    ENTER_EMAIL,
    SUCCESS
}

class ForgotPasswordViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {
    var currentStep by mutableStateOf(ResetStep.ENTER_EMAIL)
        private set
    var email by mutableStateOf("")
    var isLoading by mutableStateOf(false)
        private set
    var resetError by mutableStateOf<FirebaseAuthFailure?>(null)
        private set
    var errorMessage by mutableStateOf("")
        private set
    var statusMessage by mutableStateOf("")
        private set

    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[a-zA-Z]{2,}\$")

    fun isValidEmail(input: String): Boolean = emailRegex.matches(input)

    val isEmailValid: Boolean
        get() = isValidEmail(email) || email.isEmpty()

    fun updateEmail(value: String) {
        email = value.trim().lowercase()
        resetError = null
        errorMessage = ""
        statusMessage = ""
    }

    fun clearAuthError() {
        resetError = null
        errorMessage = ""
        statusMessage = ""
    }

    fun sendResetLink() {
        if (!isValidEmail(email) || isLoading) return
        requestResetLink(
            targetEmail = email,
            isEnglish = true,
            onSuccess = { currentStep = ResetStep.SUCCESS },
            onFailure = {},
        )
    }

    private fun noPasswordAccountMessage(isEnglish: Boolean): String =
        if (isEnglish) {
            "No email/password account exists for this email. If you signed up with Google, use Google sign-in."
        } else {
            "Is email ke liye email/password account nahi mila. Agar Google se signup kiya tha to Google sign-in use karein."
        }

    private fun resetFailureMessage(failure: FirebaseAuthFailure, isEnglish: Boolean): String = when (failure) {
        FirebaseAuthFailure.INVALID_CREDENTIALS ->
            noPasswordAccountMessage(isEnglish)
        FirebaseAuthFailure.NETWORK ->
            if (isEnglish) "Network error. Check your connection and try again."
            else "Network masla hai. Connection check kar ke dobara koshish karein."
        FirebaseAuthFailure.TOO_MANY_REQUESTS ->
            if (isEnglish) "Too many reset attempts. Try again in a few minutes."
            else "Reset attempts zyada ho gayi hain. Kuch dair baad koshish karein."
        FirebaseAuthFailure.USER_DISABLED ->
            if (isEnglish) "This account has been disabled."
            else "Ye account disabled hai."
        FirebaseAuthFailure.EMAIL_PASSWORD_DISABLED ->
            if (isEnglish) "Email/password sign-in is not enabled for this project."
            else "Is project mein email/password sign-in enabled nahi hai."
        else ->
            if (isEnglish) "Failed to send reset email. Please try again."
            else "Reset email nahi bheji ja saki. Dobara koshish karein."
    }

    fun requestResetLink(
        targetEmail: String,
        isEnglish: Boolean,
        onSuccess: () -> Unit,
        onFailure: () -> Unit = {},
        isResend: Boolean = false,
    ) {
        if (isLoading) return
        val cleanEmail = targetEmail.trim().lowercase()
        if (!isValidEmail(cleanEmail)) {
            errorMessage = if (isEnglish) "Enter a valid email address." else "Sahih email address darj karein."
            statusMessage = ""
            onFailure()
            return
        }
        isLoading = true
        errorMessage = ""
        statusMessage = ""
        viewModelScope.launch {
            val passwordUid = RealtimeDBService.lookupEmailHasPasswordResult(cleanEmail)
                .onFailure {
                    isLoading = false
                    errorMessage = if (isEnglish) {
                        "Could not check this account. Check your connection and try again."
                    } else {
                        "Account check nahi ho saka. Connection check kar ke dobara koshish karein."
                    }
                    onFailure()
                }
                .getOrNull()
            if (!isLoading) return@launch
            if (passwordUid.isNullOrBlank()) {
                isLoading = false
                errorMessage = noPasswordAccountMessage(isEnglish)
                onFailure()
                return@launch
            }

            val failure = authRepository.sendPasswordResetLink(cleanEmail)
            isLoading = false
            if (failure == null) {
                statusMessage = if (isResend) {
                    if (isEnglish) "Reset link sent again." else "Reset link dobara bhej diya gaya."
                } else {
                    if (isEnglish) "Reset link sent." else "Reset link bhej diya gaya."
                }
                onSuccess()
            } else {
                errorMessage = resetFailureMessage(failure, isEnglish)
                onFailure()
            }
        }
    }
}
