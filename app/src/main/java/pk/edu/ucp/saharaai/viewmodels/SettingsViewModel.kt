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
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import pk.edu.ucp.saharaai.data.repository.BiometricSessionRepository
import pk.edu.ucp.saharaai.utils.BiometricSessionVault

class SettingsViewModel : ViewModel() {
    private val biometricRepository = BiometricSessionRepository()

    var pushNotifications by mutableStateOf(false)
    var sounds by mutableStateOf(true)
    var vibration by mutableStateOf(true)
    var biometricEnabled by mutableStateOf(false)
        private set
    var privacyModeEnabled by mutableStateOf(true)
        private set

    sealed class BiometricEnrollmentState {
        object Idle : BiometricEnrollmentState()
        object Working : BiometricEnrollmentState()
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

        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("sahara_prefs", Context.MODE_PRIVATE)
        val vaultArmed = BiometricSessionVault.isArmed(appContext)
        biometricEnabled = prefs.getBoolean("biometric_enabled", false) && vaultArmed
        if (!vaultArmed && prefs.getBoolean("biometric_enabled", false)) {
            prefs.edit().putBoolean("biometric_enabled", false).apply()
        }
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

    fun togglePrivacyMode(context: Context, enabled: Boolean) {
        privacyModeEnabled = enabled
        context.applicationContext.getSharedPreferences("sahara_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("privacy_mode", enabled).apply()
    }

    private fun biometricMessage(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() }
            ?: "Could not update biometric login. Check your connection and try again."
}
