package pk.edu.ucp.saharaai.viewmodels

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.AuthCredential
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.remote.BiometricAuthHttpException
import pk.edu.ucp.saharaai.data.repository.AuthRepository
import pk.edu.ucp.saharaai.data.repository.BiometricSessionRepository
import pk.edu.ucp.saharaai.data.repository.FirebaseAuthFailure
import pk.edu.ucp.saharaai.data.repository.GoogleCredentialAuth
import pk.edu.ucp.saharaai.data.repository.GoogleSignInOutcome

class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val biometricRepository: BiometricSessionRepository = BiometricSessionRepository(),
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

    /** UI state for the rare Firebase collision fallback where automatic
     *  Google/email linking is refused and the user must prove the password
     *  account once. Empty/blank when not active. */
    var googleLinkPromptEmail by mutableStateOf("")
        private set
    private var pendingGoogleCredential: AuthCredential? = null

    fun signInWithGoogle(context: Context, isEnglish: Boolean, onSuccess: (String) -> Unit) {
        if (isLoading) return
        isLoading = true
        errorMessage = ""
        viewModelScope.launch {
            GoogleCredentialAuth.signIn(context)
                .onSuccess { outcome ->
                    when (outcome) {
                        is GoogleSignInOutcome.Signed -> {
                            isLoading = false
                            onSuccess(outcome.authResult.user?.email.orEmpty())
                        }
                        is GoogleSignInOutcome.NeedsPasswordLink -> {
                            // Firebase refused automatic provider linking.
                            // The screen renders the one-time password fallback
                            // and calls completeGoogleLinkWithPassword.
                            isLoading = false
                            pendingGoogleCredential = outcome.pendingGoogleCredential
                            googleLinkPromptEmail = outcome.email
                        }
                    }
                }
                .onFailure {
                    isLoading = false
                    errorMessage = GoogleCredentialAuth.userMessage(it, isEnglish)
                }
        }
    }

    fun completeGoogleLinkWithPassword(
        password: String,
        isEnglish: Boolean,
        onSuccess: (String) -> Unit,
    ) {
        val email = googleLinkPromptEmail
        val cred = pendingGoogleCredential
        if (email.isBlank() || cred == null) return
        if (password.isBlank()) {
            errorMessage = if (isEnglish) "Enter your password to continue."
                           else "Jari rakhne ke liye apna password darj karein."
            return
        }
        if (isLoading) return
        isLoading = true
        errorMessage = ""
        viewModelScope.launch {
            GoogleCredentialAuth.completeLinkWithPassword(email, password, cred)
                .onSuccess {
                    isLoading = false
                    pendingGoogleCredential = null
                    googleLinkPromptEmail = ""
                    onSuccess(email)
                }
                .onFailure {
                    isLoading = false
                    errorMessage = if (isEnglish)
                        "Incorrect password — couldn't link Google to your account."
                    else
                        "Galat password — Google ko aapke account se link nahi kar saka."
                }
        }
    }

    fun cancelGoogleLinkPrompt() {
        pendingGoogleCredential = null
        googleLinkPromptEmail = ""
        errorMessage = ""
    }

    fun signInWithBiometricSession(
        context: Context,
        isEnglish: Boolean,
        onSuccess: (String) -> Unit,
    ) {
        if (isLoading) return
        isLoading = true
        errorMessage = ""
        viewModelScope.launch {
            biometricRepository.signInWithStoredSession(context)
                .onSuccess { restored ->
                    isLoading = false
                    onSuccess(restored.email)
                }
                .onFailure {
                    isLoading = false
                    errorMessage = biometricMessage(it, isEnglish)
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

    private fun biometricMessage(error: Throwable, isEnglish: Boolean): String {
        val terminal = (error as? BiometricAuthHttpException)?.isTerminalAuthFailure == true
        return when {
            terminal -> {
                if (isEnglish) {
                    "Fingerprint login was removed for this device. Sign in normally and enable it again."
                } else {
                    "Is device se fingerprint login hata diya gaya hai. Normal sign in karke dobara enable karein."
                }
            }
            error.message?.isNotBlank() == true -> {
                if (isEnglish) error.message.orEmpty()
                else "Fingerprint login nahi ho saka. Internet check karke dobara koshish karein."
            }
            else -> {
                if (isEnglish) {
                    "Fingerprint login failed. Check your internet connection and try again."
                } else {
                    "Fingerprint login nahi ho saka. Internet check karke dobara koshish karein."
                }
            }
        }
    }
}
