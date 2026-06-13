package pk.edu.ucp.saharaai.viewmodels

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.BuildConfig
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import pk.edu.ucp.saharaai.data.repository.AuthRepository
import pk.edu.ucp.saharaai.data.repository.BiometricSessionRepository
import pk.edu.ucp.saharaai.data.repository.FirebaseAuthFailure
import pk.edu.ucp.saharaai.utils.BiometricSessionVault
import pk.edu.ucp.saharaai.utils.EmailOtpService

class SettingsViewModel : ViewModel() {
    private val authRepository = AuthRepository()
    private val biometricRepository = BiometricSessionRepository()

    var pushNotifications by mutableStateOf(false)
    var sounds by mutableStateOf(true)
    var vibration by mutableStateOf(true)
    var biometricEnabled by mutableStateOf(false)
        private set
    var passwordBackupEnabled by mutableStateOf(false)
        private set
    var privacyModeEnabled by mutableStateOf(true)
        private set

    sealed class BiometricEnrollmentState {
        object Idle : BiometricEnrollmentState()
        object Working : BiometricEnrollmentState()
        data class Error(val message: String) : BiometricEnrollmentState()
    }

    sealed class PasswordBackupState {
        object Idle : PasswordBackupState()
        data class Requested(
            val email: String,
            val errorMessage: String = "",
        ) : PasswordBackupState()
        data class Submitting(val email: String) : PasswordBackupState()
        data class Error(val message: String) : PasswordBackupState()
    }

    var biometricEnrollmentState by mutableStateOf<BiometricEnrollmentState>(BiometricEnrollmentState.Idle)
        private set
    var passwordBackupState by mutableStateOf<PasswordBackupState>(PasswordBackupState.Idle)
        private set

    fun initialize(context: Context) {
        pushNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("sahara_prefs", Context.MODE_PRIVATE)
        val vaultArmed = BiometricSessionVault.isArmed(appContext)
        biometricEnabled = prefs.getBoolean("biometric_enabled", false) && vaultArmed
        if (!vaultArmed && prefs.getBoolean("biometric_enabled", false)) {
            prefs.edit().putBoolean("biometric_enabled", false).apply()
        }
        passwordBackupEnabled = authRepository.hasPasswordProvider()
        privacyModeEnabled = prefs.getBoolean("privacy_mode", true)

        val uid = Firebase.auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) return
        viewModelScope.launch {
            val remote = RealtimeDBService.getBiometricEnabled(uid)
            when {
                remote && vaultArmed && !biometricEnabled -> {
                    biometricEnabled = true
                    prefs.edit().putBoolean("biometric_enabled", true).apply()
                }
                !remote && biometricEnabled -> {
                    RealtimeDBService.setBiometricEnabled(uid, true)
                }
            }
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        pushNotifications = enabled
    }

    fun toggleSounds(enabled: Boolean) {
        sounds = enabled
    }

    fun toggleVibration(enabled: Boolean) {
        vibration = enabled
    }

    fun toggleBiometric(context: Context, enabled: Boolean) {
        if (biometricEnrollmentState is BiometricEnrollmentState.Working) return
        if (enabled) enableBiometric(context) else disableBiometric(context)
    }

    private fun enableBiometric(context: Context) {
        biometricEnrollmentState = BiometricEnrollmentState.Working
        viewModelScope.launch {
            biometricRepository.enrollCurrentUser(context)
                .onSuccess {
                    biometricEnabled = true
                    biometricEnrollmentState = BiometricEnrollmentState.Idle
                }
                .onFailure {
                    biometricEnabled = false
                    context.applicationContext.getSharedPreferences("sahara_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("biometric_enabled", false).apply()
                    biometricEnrollmentState = BiometricEnrollmentState.Error(biometricMessage(it))
                }
        }
    }

    private fun disableBiometric(context: Context) {
        biometricEnrollmentState = BiometricEnrollmentState.Working
        viewModelScope.launch {
            biometricRepository.disableCurrentDevice(context)
                .onSuccess {
                    biometricEnabled = false
                    biometricEnrollmentState = BiometricEnrollmentState.Idle
                }
                .onFailure {
                    biometricEnabled = false
                    biometricEnrollmentState = BiometricEnrollmentState.Error(biometricMessage(it))
                }
        }
    }

    fun dismissBiometricEnrollmentState() {
        biometricEnrollmentState = BiometricEnrollmentState.Idle
    }

    fun requestPasswordBackup(context: Context) {
        if (passwordBackupEnabled || authRepository.hasPasswordProvider()) {
            passwordBackupEnabled = true
            return
        }
        val user = Firebase.auth.currentUser
        if (user == null || user.isAnonymous) {
            passwordBackupState = PasswordBackupState.Error("Sign in first to add email/password login.")
            return
        }
        val email = user.email.orEmpty()
        if (email.isBlank()) {
            passwordBackupState = PasswordBackupState.Error("Your account has no email address.")
            return
        }
        passwordBackupState = PasswordBackupState.Requested(email)
    }

    fun cancelPasswordBackup() {
        passwordBackupState = PasswordBackupState.Idle
    }

    fun completePasswordBackup(context: Context, password: String) {
        val request = passwordBackupState as? PasswordBackupState.Requested ?: return
        if (password.length < 8) {
            passwordBackupState = request.copy(errorMessage = "Password must be at least 8 characters.")
            return
        }
        passwordBackupState = PasswordBackupState.Submitting(request.email)
        viewModelScope.launch {
            val failure = authRepository.linkEmailPassword(request.email, password)
            if (failure != null) {
                passwordBackupState = PasswordBackupState.Requested(
                    email = request.email,
                    errorMessage = failureMessage(failure),
                )
                return@launch
            }

            passwordBackupEnabled = true
            runCatching {
                EmailOtpService.sendPasswordEmail(
                    toEmail = request.email,
                    toName = Firebase.auth.currentUser?.displayName.orEmpty(),
                    password = password,
                    mailerUrl = BuildConfig.SAHARA_MAILER_URL,
                )
            }
            passwordBackupState = PasswordBackupState.Idle
        }
    }

    fun togglePrivacyMode(context: Context, enabled: Boolean) {
        privacyModeEnabled = enabled
        context.applicationContext.getSharedPreferences("sahara_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("privacy_mode", enabled).apply()
    }

    private fun biometricMessage(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() }
            ?: "Could not update biometric login. Check your connection and try again."

    private fun failureMessage(failure: FirebaseAuthFailure): String = when (failure) {
        FirebaseAuthFailure.EMAIL_ALREADY_IN_USE -> "That password is already linked to another account."
        FirebaseAuthFailure.INVALID_CREDENTIALS -> "Incorrect password."
        FirebaseAuthFailure.EMAIL_PASSWORD_DISABLED -> "Email/password sign-in isn't enabled for this project."
        FirebaseAuthFailure.CONFIGURATION_MISSING -> "Firebase email/password sign-in is misconfigured."
        FirebaseAuthFailure.WEAK_PASSWORD -> "Password is too weak - try at least 8 characters with letters and numbers."
        FirebaseAuthFailure.NETWORK -> "Network error. Check your connection and try again."
        FirebaseAuthFailure.TOO_MANY_REQUESTS -> "Too many attempts. Try again in a few minutes."
        FirebaseAuthFailure.USER_DISABLED -> "This account has been disabled."
        FirebaseAuthFailure.UNKNOWN -> "Could not add email/password login."
    }
}
