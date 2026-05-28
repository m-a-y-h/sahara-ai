package pk.edu.ucp.saharaai.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService

class CounselorSetupViewModel : ViewModel() {
    var isLoading by mutableStateOf(false)
        private set
    var errorMsg by mutableStateOf("")
        private set

    fun reportError(message: String) {
        errorMsg = message
    }

    fun saveProfile(
        counselorKey: String,
        fullName: String,
        feePkr: Int,
        ngoName: String,
        region: String,
        bio: String,
        fallbackError: String,
        onSuccess: () -> Unit
    ) {
        val uid = Firebase.auth.currentUser?.uid.orEmpty()
        isLoading = true
        errorMsg = ""
        viewModelScope.launch {
            RealtimeDBService.saveCounselorSetup(
                key = counselorKey,
                uid = uid,
                name = fullName.trim(),
                feePkr = feePkr,
                ngoName = ngoName.trim(),
                region = region.trim().ifBlank { "Pakistan" },
                bio = bio.trim()
            ).onSuccess {
                onSuccess()
            }.onFailure {
                errorMsg = it.message ?: fallbackError
            }
            isLoading = false
        }
    }
}
