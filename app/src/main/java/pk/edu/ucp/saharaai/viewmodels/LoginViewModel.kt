package pk.edu.ucp.saharaai.viewmodels

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.repository.AuthRepository
import pk.edu.ucp.saharaai.data.repository.FirebaseAuthFailure
import pk.edu.ucp.saharaai.data.repository.GoogleCredentialAuth

class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var showPassword by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
        private set
    var authError by mutableStateOf<FirebaseAuthFailure?>(null)
        private set
    var biometricSessionRequired by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf("")
        private set

    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[a-zA-Z]{2,}\$")
    fun isValidEmail(email: String): Boolean = emailRegex.matches(email)

    val isEmailValid: Boolean
        get() = isValidEmail(email) || email.isEmpty()

    val isFormValid: Boolean
        get() = isValidEmail(email) && password.isNotEmpty()

    fun signIn(onSuccess: (String) -> Unit) {
        if (!isFormValid || isLoading) return
        authError = null
        biometricSessionRequired = false
        isLoading = true
        val cleanEmail = email.trim().lowercase()
        authRepository.signIn(cleanEmail, password) { failure ->
            isLoading = false
            if (failure == null) {
                onSuccess(cleanEmail)
            } else {
                authError = failure
            }
        }
    }

    fun clearAuthError() {
        authError = null
        biometricSessionRequired = false
        errorMessage = ""
    }

    fun reportError(message: String) {
        errorMessage = message
    }

    fun signIn(email: String, password: String, isEnglish: Boolean, onSuccess: (String) -> Unit) {
        if (isLoading) return
        isLoading = true
        errorMessage = ""
        val cleanEmail = email.trim().lowercase()
        authRepository.signIn(cleanEmail, password) { failure ->
            isLoading = false
            if (failure == null) {
                onSuccess(cleanEmail)
            } else {
                errorMessage = if (isEnglish) "Login failed. Check your credentials."
                else "Login fail hua. Email ya password ghalat hai."
            }
        }
    }

    fun signInWithGoogle(context: Context, isEnglish: Boolean, onSuccess: (String) -> Unit) {
        if (isLoading) return
        isLoading = true
        errorMessage = ""
        viewModelScope.launch {
            GoogleCredentialAuth.signIn(context)
                .onSuccess {
                    isLoading = false
                    onSuccess(it.user?.email.orEmpty())
                }
                .onFailure {
                    isLoading = false
                    errorMessage = GoogleCredentialAuth.userMessage(it, isEnglish)
                }
        }
    }

    fun authenticateWithBiometrics(context: Context, isEnglish: Boolean, onSuccess: (String) -> Unit) {
        val fragmentActivity = context as? FragmentActivity ?: return
        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(fragmentActivity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    if (authRepository.hasAuthenticatedSession()) {
                        authError = null
                        biometricSessionRequired = false
                        onSuccess(authRepository.currentUserEmail().orEmpty())
                    } else {
                        biometricSessionRequired = true
                    }
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(if (isEnglish) "Biometric Login" else "Biometric Tasdeeq")
            .setSubtitle(if (isEnglish) "Log in using your secure credential" else "Apni secure tasdeeq se log in karein")
            .setNegativeButtonText(if (isEnglish) "Cancel" else "Cancel Karein")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
