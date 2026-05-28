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

class SettingsViewModel : ViewModel() {
    var pushNotifications by mutableStateOf(false)
    var sounds by mutableStateOf(true)
    var vibration by mutableStateOf(true)
    var biometricEnabled by mutableStateOf(false)
        private set
    var privacyModeEnabled by mutableStateOf(true)
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
            if (remote && !biometricEnabled) {
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

    fun toggleBiometric(context: Context, enabled: Boolean) {
        biometricEnabled = enabled
        context.applicationContext.getSharedPreferences("sahara_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("biometric_enabled", enabled).apply()
        val uid = Firebase.auth.currentUser?.uid.orEmpty()
        if (uid.isNotBlank()) {
            viewModelScope.launch { RealtimeDBService.setBiometricEnabled(uid, enabled) }
        }
    }

    fun togglePrivacyMode(context: Context, enabled: Boolean) {
        privacyModeEnabled = enabled
        context.applicationContext.getSharedPreferences("sahara_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("privacy_mode", enabled).apply()
    }
}
