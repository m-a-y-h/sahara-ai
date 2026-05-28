package pk.edu.ucp.saharaai.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
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

    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[a-zA-Z]{2,}\$")

    fun isValidEmail(input: String): Boolean = emailRegex.matches(input)

    val isEmailValid: Boolean
        get() = isValidEmail(email) || email.isEmpty()

    fun updateEmail(value: String) {
        email = value.trim().lowercase()
        resetError = null
        errorMessage = ""
    }

    fun clearAuthError() {
        resetError = null
        errorMessage = ""
    }

    fun sendResetLink() {
        if (!isValidEmail(email) || isLoading) return
        isLoading = true
        resetError = null
        authRepository.sendPasswordResetLink(email) { failure ->
            isLoading = false
            if (failure == null) {
                currentStep = ResetStep.SUCCESS
            } else {
                resetError = failure
            }
        }
    }

    fun requestResetLink(
        targetEmail: String,
        isEnglish: Boolean,
        onSuccess: () -> Unit,
        onFailure: () -> Unit = {}
    ) {
        if (isLoading) return
        isLoading = true
        errorMessage = ""
        authRepository.sendPasswordResetLink(targetEmail.trim()) { failure ->
            isLoading = false
            if (failure == null) {
                onSuccess()
            } else {
                errorMessage = when (failure) {
                    FirebaseAuthFailure.INVALID_CREDENTIALS ->
                        if (isEnglish) "No account found with this email address. Please check the email or sign up first."
                        else "Is email par koi account nahi mila. Email check karein ya pehle register karein."
                    else -> if (isEnglish) "Failed to send reset email. Please try again."
                    else "Reset email nahi bheji ja saki. Dobara koshish karein."
                }
                onFailure()
            }
        }
    }
}
