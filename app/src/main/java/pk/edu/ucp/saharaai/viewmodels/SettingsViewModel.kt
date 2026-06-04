package pk.edu.ucp.saharaai.viewmodels

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import pk.edu.ucp.saharaai.data.repository.AuthRepository
import pk.edu.ucp.saharaai.data.repository.FirebaseAuthFailure
import pk.edu.ucp.saharaai.utils.BiometricCredentialVault

class SettingsViewModel : ViewModel() {
    private val authRepository = AuthRepository()

    var pushNotifications by mutableStateOf(false)
    var sounds by mutableStateOf(true)
    var vibration by mutableStateOf(true)
    var biometricEnabled by mutableStateOf(false)
        private set
    var privacyModeEnabled by mutableStateOf(true)
        private set

    /** UI state for the biometric-enrollment dialog. When [Requested], the
     *  Settings screen shows a password prompt; on submit the screen calls
     *  [completeBiometricEnrollment]. Google-only users have [needsLink]=true
     *  (we'll link a fresh email/password credential); manual users have
     *  needsLink=false (we'll just re-verify their existing password). */
    sealed class BiometricEnrollmentState {
        object Idle : BiometricEnrollmentState()
        data class Requested(
            val email: String,
            val needsLink: Boolean,
        ) : BiometricEnrollmentState()
        data class Submitting(val email: String) : BiometricEnrollmentState()
        data class Error(val message: String) : BiometricEnrollmentState()
    }

    var biometricEnrollmentState by mutableStateOf<BiometricEnrollmentState>(BiometricEnrollmentState.Idle)
        private set

    fun initialize(context: Context) {
        pushNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val prefs = context.applicationContext.getSharedPreferences("sahara_prefs", Context.MODE_PRIVATE)
        biometricEnabled = prefs.getBoolean("biometric_enabled", false)
        privacyModeEnabled = prefs.getBoolean("privacy_mode", true)
        val uid = Firebase.auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) return
        viewModelScope.launch {
            val remote = RealtimeDBService.getBiometricEnabled(uid)
            // Only honour a remote "true" if the vault is actually armed on
            // this device — otherwise the toggle is misleading and the
            // biometric button on Login won't show.
            val vaultArmed = BiometricCredentialVault.isArmed(context)
            if (remote && !biometricEnabled && vaultArmed) {
                biometricEnabled = true
                prefs.edit().putBoolean("biometric_enabled", true).apply()
            } else if (!remote && biometricEnabled) {
                RealtimeDBService.setBiometricEnabled(uid, true)
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

    /** Entry point from the Settings toggle. Enabling routes into the
     *  enrollment flow (which may ask for a password); disabling immediately
     *  clears the on-device vault and the cloud flag. */
    fun toggleBiometric(context: Context, enabled: Boolean) {
        if (!enabled) {
            BiometricCredentialVault.clear(context)
            biometricEnabled = false
            context.applicationContext.getSharedPreferences("sahara_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("biometric_enabled", false).apply()
            val uid = Firebase.auth.currentUser?.uid.orEmpty()
            if (uid.isNotBlank()) {
                viewModelScope.launch { RealtimeDBService.setBiometricEnabled(uid, false) }
            }
            return
        }
        requestEnableBiometric(context)
    }

    private fun requestEnableBiometric(context: Context) {
        val user = Firebase.auth.currentUser
        if (user == null) {
            biometricEnrollmentState = BiometricEnrollmentState.Error(
                "Sign in first to enable biometric login."
            )
            return
        }
        val email = user.email.orEmpty()
        if (email.isBlank()) {
            biometricEnrollmentState = BiometricEnrollmentState.Error(
                "Your account has no email; biometric login needs an email account."
            )
            return
        }
        val hasPwd = authRepository.hasPasswordProvider()
        val vaultArmed = BiometricCredentialVault.isArmed(context)
        if (hasPwd && vaultArmed) {
            // Already fully set up locally — just flip the cloud + UI flag.
            persistBiometricEnabled(context, user.uid, true)
            return
        }
        biometricEnrollmentState = BiometricEnrollmentState.Requested(
            email = email,
            needsLink = !hasPwd,
        )
    }

    fun cancelBiometricEnrollment() {
        biometricEnrollmentState = BiometricEnrollmentState.Idle
    }

    /** Called from the Settings dialog when the user submits a password.
     *  Links the credential (for Google-only users) or re-verifies the
     *  existing one, then seals it in the keystore vault and flips the flag.
     */
    fun completeBiometricEnrollment(context: Context, password: String) {
        val request = biometricEnrollmentState as? BiometricEnrollmentState.Requested ?: return
        val user = Firebase.auth.currentUser ?: run {
            biometricEnrollmentState = BiometricEnrollmentState.Error(
                "Sign in first to enable biometric login."
            )
            return
        }
        if (password.length < 6) {
            biometricEnrollmentState = BiometricEnrollmentState.Error(
                "Password must be at least 6 characters."
            )
            return
        }
        biometricEnrollmentState = BiometricEnrollmentState.Submitting(request.email)
        viewModelScope.launch {
            val failure = if (request.needsLink) {
                authRepository.linkEmailPassword(request.email, password)
            } else {
                authRepository.reauthenticateWithPassword(request.email, password)
            }
            if (failure != null) {
                biometricEnrollmentState = BiometricEnrollmentState.Error(failureMessage(failure))
                return@launch
            }
            BiometricCredentialVault.save(context, request.email, password)
            persistBiometricEnabled(context, user.uid, true)
            biometricEnrollmentState = BiometricEnrollmentState.Idle
        }
    }

    private fun persistBiometricEnabled(context: Context, uid: String, enabled: Boolean) {
        biometricEnabled = enabled
        context.applicationContext.getSharedPreferences("sahara_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("biometric_enabled", enabled).apply()
        if (uid.isNotBlank()) {
            viewModelScope.launch { RealtimeDBService.setBiometricEnabled(uid, enabled) }
        }
    }

    private fun failureMessage(failure: FirebaseAuthFailure): String = when (failure) {
        FirebaseAuthFailure.EMAIL_ALREADY_IN_USE -> "That password is already linked to another account."
        FirebaseAuthFailure.INVALID_CREDENTIALS -> "Incorrect password."
        FirebaseAuthFailure.EMAIL_PASSWORD_DISABLED -> "Email/password sign-in isn't enabled for this project."
        FirebaseAuthFailure.CONFIGURATION_MISSING -> "Firebase email/password sign-in is misconfigured."
        FirebaseAuthFailure.WEAK_PASSWORD -> "Password is too weak — try at least 8 characters with letters and numbers."
        FirebaseAuthFailure.NETWORK -> "Network error. Check your connection and try again."
        FirebaseAuthFailure.TOO_MANY_REQUESTS -> "Too many attempts. Try again in a few minutes."
        FirebaseAuthFailure.USER_DISABLED -> "This account has been disabled."
        FirebaseAuthFailure.UNKNOWN -> "Could not enable biometric login."
    }

    fun togglePrivacyMode(context: Context, enabled: Boolean) {
        privacyModeEnabled = enabled
        context.applicationContext.getSharedPreferences("sahara_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("privacy_mode", enabled).apply()
    }
}
