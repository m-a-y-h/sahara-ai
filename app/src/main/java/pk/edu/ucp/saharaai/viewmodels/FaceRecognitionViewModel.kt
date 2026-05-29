package pk.edu.ucp.saharaai.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import pk.edu.ucp.saharaai.ui.screens.GlobalAppState
import pk.edu.ucp.saharaai.util.callingName

class FaceRecognitionViewModel : ViewModel() {
    var biometricEnabled by mutableStateOf(false)
        private set
    var storedEmail by mutableStateOf("")
        private set
    var storedName by mutableStateOf("")
        private set
    var hasStoredSession by mutableStateOf(false)
        private set

    fun initialize(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("sahara_prefs", Context.MODE_PRIVATE)
        biometricEnabled = prefs.getBoolean("biometric_enabled", false)
        storedEmail = prefs.getString("user_email", "").orEmpty().ifBlank {
            prefs.getString("biometric_last_email", "").orEmpty()
        }
        storedName = prefs.getString("user_full_name", "").orEmpty().ifBlank {
            prefs.getString("biometric_last_name", "").orEmpty()
        }
        hasStoredSession = Firebase.auth.currentUser != null || storedEmail.isNotBlank()
    }

    fun onAuthenticationSucceeded() {
        val user = Firebase.auth.currentUser
        val email = user?.email?.ifBlank { null } ?: storedEmail
        val name = user?.displayName?.ifBlank { null } ?: storedName.ifBlank { null } ?: "User"
        GlobalAppState.userName = callingName(name).ifBlank { name }
        GlobalAppState.userEmail = email
        user?.uid?.let { uid ->
            viewModelScope.launch { RealtimeDBService.logFaceLogin(uid) }
        }
    }
}
