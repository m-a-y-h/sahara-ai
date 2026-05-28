package pk.edu.ucp.saharaai.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
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
                (BuildConfig.NGO_KEY.isNotBlank() && key == BuildConfig.NGO_KEY) ||
                key == BuildConfig.BYPASS_CODE)
        ) {
            onSuccess(key)
            return
        }
        if (type == "COUNSELOR" && key == BuildConfig.BYPASS_CODE) {
            onSuccess(key)
            return
        }
        viewModelScope.launch {
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
