package pk.edu.ucp.saharaai.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import pk.edu.ucp.saharaai.BuildConfig
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService

class WelcomeSettingsViewModel : ViewModel() {
    fun verifyKey(
        type: String,
        key: String,
        isEnglish: Boolean,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (type == "ORGANIZATION" &&
            ((BuildConfig.ADMIN_KEY.isNotBlank() && key == BuildConfig.ADMIN_KEY) ||
                (BuildConfig.NGO_KEY.isNotBlank() && key == BuildConfig.NGO_KEY))
        ) {
            onSuccess(key)
            return
        }
        viewModelScope.launch {
            // ngo_keys / counselor_keys require an authenticated read, but the
            // Welcome screen is pre-login — so an admin-issued key (stored in
            // RTDB, unlike the built-in BuildConfig keys above) gets a permission
            // denied that surfaces as "Connection error". Sign in anonymously
            // first so the lookup AND the dashboard the user then enters work.
            if (Firebase.auth.currentUser == null &&
                !runCatching { Firebase.auth.signInAnonymously().await() }.isSuccess
            ) {
                onFailure(if (isEnglish) "Connection error. Try again." else "Connection error. Dobara koshish karein.")
                return@launch
            }
            val data = if (type == "ORGANIZATION") {
                RealtimeDBService.getNgoKey(key)
            } else {
                RealtimeDBService.getCounselorKey(key)
            }.getOrElse {
                onFailure(if (isEnglish) "Connection error. Try again." else "Connection error. Dobara koshish karein.")
                return@launch
            }
            if (data?.get("isActive") == true) {
                onSuccess(key)
            } else if (type == "ORGANIZATION") {
                onFailure(if (isEnglish) "Invalid NGO/Admin key." else "NGO/Admin key galat hai.")
            } else if (data == null) {
                onFailure(if (isEnglish) "Key not found. Contact your admin." else "Key nahi mili. Admin se rabta karein.")
            } else {
                onFailure(if (isEnglish) "This key has been deactivated." else "Ye key band kar di gayi hai.")
            }
        }
    }
}
