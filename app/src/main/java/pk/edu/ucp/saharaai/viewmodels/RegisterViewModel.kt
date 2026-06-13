package pk.edu.ucp.saharaai.viewmodels

import androidx.compose.runtime.*
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import pk.edu.ucp.saharaai.data.repository.AuthRepository
import pk.edu.ucp.saharaai.data.repository.FirebaseAuthFailure

class RegisterViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {
    var name by mutableStateOf("")
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var isAccepted by mutableStateOf(false)
    var showToS by mutableStateOf(false)
    var showPassword by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
        private set
    var authError by mutableStateOf<FirebaseAuthFailure?>(null)
        private set
    var errorMessage by mutableStateOf("")
        private set

    private val nameRegex = Regex("^[a-zA-Z\\s]*$")
    private val emailRegex = Regex("""^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[a-zA-Z]{2,}$""")
    private val commonTitles = listOf(
        "syed", "sayyed", "sayyid", "muhammad", "mohammad", "mohd", "muhammed", "mohamed", "mohammed",
        "peer", "doctor", "pir", "qazi", "qaazi", "khan", "bin", "shaikh", "sheikh", "mian", "shah",
        "makhdoom", "hafiz", "raja", "malik", "sardar", "wadera", "mir", "haju", "haaji", "haji",
        "mirza", "ustad", "jan", "khawaja", "dewan", "rana", "chaudhari", "choudhury", "chowdhury",
        "chaudhry", "chohadry", "choudhry", "choudhary", "chohadri", "chouudry", "choudari", "chaudree",
        "chowdhrie", "chawdry", "chowdhary", "chowdhry", "chowdhri", "chowdhari", "chawdhury", "chaudery",
        "rai", "rao", "kanwar", "jam", "arbab", "zardar", "nawab", "qari", "mufti", "allama", "maulana",
        "pirzada", "ghulam", "zaildar", "pasha", "syeda", "bibi", "begum", "beghum", "moulvi", "faqir",
        "faqeer", "hashmi", "hazrat", "bano", "baano", "pirzadi", "sahibzadi", "khanum", "khatoon",
        "sultan", "malak"
    )

    fun isValidName(name: String): Boolean {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return words.size >= 2 && words.all { it.length >= 3 } && nameRegex.matches(name)
    }

    fun isValidEmail(email: String): Boolean = emailRegex.matches(email)

    fun getCallingName(fullName: String): String {
        val parts = fullName.trim().split(" ")
        if (parts.isEmpty()) return ""
        val callingName = parts.firstOrNull { part ->
            !commonTitles.contains(part.lowercase().replace(".", ""))
        } ?: parts.first()
        return callingName.replaceFirstChar { it.uppercase() }
    }

    val isNameValid: Boolean get() = isValidName(name) || name.isEmpty()
    val isEmailValid: Boolean get() = isValidEmail(email) || email.isEmpty()

    val hasMinLength: Boolean get() = password.length >= 8
    val hasUppercase: Boolean get() = password.any { it.isUpperCase() }
    val hasNumber: Boolean get() = password.any { it.isDigit() }
    val hasSpecial: Boolean get() = password.any { !it.isLetterOrDigit() }

    val isPasswordValid: Boolean get() = hasMinLength && hasUppercase && hasNumber && hasSpecial
    val passwordsMatch: Boolean get() = password == confirmPassword || confirmPassword.isEmpty()

    val isFormValid: Boolean
        get() = isValidName(name) && isValidEmail(email) && isPasswordValid && (password == confirmPassword) && isAccepted

    fun register(onSuccess: (String, String, String) -> Unit) {
        if (!isFormValid || isLoading) return
        authError = null
        isLoading = true
        val cleanEmail = email.trim().lowercase()
        authRepository.register(cleanEmail, password) { failure ->
            isLoading = false
            if (failure == null) {
                val callingName = runCatching { getCallingName(name).ifBlank { "User" } }
                    .getOrDefault("User")
                onSuccess(name.trim(), cleanEmail, callingName)
            } else {
                authError = failure
            }
        }
    }

    fun clearAuthError() {
        authError = null
        errorMessage = ""
    }

    fun registerWithEmail(
        name: String,
        email: String,
        password: String,
        isEnglish: Boolean,
        onSuccess: (String, String, String) -> Unit
    ) {
        if (isLoading) return
        isLoading = true
        errorMessage = ""
        val cleanEmail = email.trim().lowercase()
        authRepository.register(cleanEmail, password) { failure ->
            if (failure != null) {
                isLoading = false
                errorMessage = if (isEnglish) "Registration failed. Try again."
                else "Registration nakam ho gayi hai. Dobara koshish karein."
                return@register
            }
            viewModelScope.launch {
                val user = Firebase.auth.currentUser
                runCatching {
                    user?.updateProfile(
                        UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()
                    )?.await()
                }
                RealtimeDBService.saveUser(user?.uid.orEmpty(), name.trim(), cleanEmail)
                isLoading = false
                val callingName = runCatching { getCallingName(name).ifBlank { "User" } }.getOrDefault("User")
                onSuccess(name.trim(), cleanEmail, callingName)
            }
        }
    }

    fun registerWithGoogle(
        context: Context,
        isEnglish: Boolean,
        onNewUser: (String, String, String) -> Unit,
        onExistingUser: (String) -> Unit
    ) {
        if (isLoading) return
        isLoading = true
        errorMessage = ""
        viewModelScope.launch {
            pk.edu.ucp.saharaai.data.repository.GoogleCredentialAuth.signIn(context)
                .onSuccess { outcome ->
                    when (outcome) {
                        is pk.edu.ucp.saharaai.data.repository.GoogleSignInOutcome.Signed -> {
                            val authResult = outcome.authResult
                            val user = authResult.user
                            val email = user?.email.orEmpty()
                            val displayName = user?.displayName.orEmpty()
                            if (authResult.additionalUserInfo?.isNewUser ?: true) {
                                val callingName = runCatching { getCallingName(displayName).ifBlank { "User" } }
                                    .getOrDefault("User")
                                onNewUser(displayName, email, callingName)
                            } else {
                                onExistingUser(email)
                            }
                            isLoading = false
                        }
                        is pk.edu.ucp.saharaai.data.repository.GoogleSignInOutcome.NeedsPasswordLink -> {
                            // Register flow shouldn't normally hit this — but if it
                            // does, the user has an existing account, so point
                            // them at the Sign-in screen.
                            isLoading = false
                            errorMessage = if (isEnglish)
                                "This email already has a Sahara account — sign in with your password."
                            else
                                "Is email se Sahara account pehle se hai — apne password se sign in karein."
                        }
                    }
                }
                .onFailure {
                    isLoading = false
                    errorMessage = pk.edu.ucp.saharaai.data.repository.GoogleCredentialAuth.userMessage(it, isEnglish)
                }
        }
    }
}
