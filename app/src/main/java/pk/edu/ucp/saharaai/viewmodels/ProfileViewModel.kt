package pk.edu.ucp.saharaai.viewmodels

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.firebase.Firebase
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import pk.edu.ucp.saharaai.ui.screens.GlobalAppState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileViewModel : ViewModel() {
    var resolvedName by mutableStateOf("Sahara User")
        private set
    var resolvedRegion by mutableStateOf("")
        private set
    var memberSince by mutableStateOf("")
        private set
    var isPrivacyModeEnabled by mutableStateOf(true)
        private set
    var isUpdatingName by mutableStateOf(false)
        private set
    var isUpdatingRegion by mutableStateOf(false)
        private set
    var avatarId by mutableStateOf("avatar_01")
        private set
    var customAvatarUrl by mutableStateOf("")
        private set
    var customAvatarStatus by mutableStateOf("")
        private set
    var isUploadingAvatar by mutableStateOf(false)
        private set
    var avatarMessage by mutableStateOf("")
        private set
    var isProfileLoading by mutableStateOf(true)
        private set

    private val uid: String
        get() = Firebase.auth.currentUser?.uid.orEmpty()

    fun load(context: Context, fallbackName: String) {
        val prefs = context.applicationContext.getSharedPreferences("sahara_prefs", Context.MODE_PRIVATE)
        resolvedName = fallbackName.ifBlank { GlobalAppState.userName }.ifBlank { "Sahara User" }
        isPrivacyModeEnabled = prefs.getBoolean("privacy_mode", true)
        val currentUid = uid
        if (currentUid.isBlank()) {
            isProfileLoading = false
            return
        }
        isProfileLoading = true
        viewModelScope.launch {
            runCatching {
                RealtimeDBService.getUser(currentUid).getOrNull()?.let { user ->
                    user["name"]?.toString()?.takeIf(String::isNotBlank)?.let {
                        resolvedName = it
                    }
                    avatarId = user["avatarId"]?.toString().orEmpty().ifBlank { "avatar_01" }
                    customAvatarUrl = user["customAvatarUrl"]?.toString().orEmpty()
                    customAvatarStatus = user["customAvatarStatus"]?.toString().orEmpty()
                }
                resolvedRegion = RealtimeDBService.getUserRegion(currentUid)
                if (resolvedRegion.isBlank()) {
                    detectDeviceRegion(context)?.let { detected ->
                        RealtimeDBService.updateUserRegion(currentUid, detected).onSuccess {
                            resolvedRegion = detected
                        }
                    }
                }
                val createdAt = RealtimeDBService.getUserCreatedAt(currentUid).takeIf { it > 0L }
                    ?: (Firebase.auth.currentUser?.metadata?.creationTimestamp ?: 0L)
                memberSince = if (createdAt > 0L) {
                    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(createdAt))
                } else {
                    ""
                }
            }.also {
                isProfileLoading = false
            }
        }
    }

    fun updateName(context: Context, newName: String, onComplete: () -> Unit) {
        val currentUid = uid
        if (currentUid.isBlank() || isUpdatingName) return
        viewModelScope.launch {
            isUpdatingName = true
            RealtimeDBService.updateUserName(currentUid, newName).onSuccess {
                Firebase.auth.currentUser?.updateProfile(
                    UserProfileChangeRequest.Builder().setDisplayName(newName).build()
                )
                context.applicationContext.getSharedPreferences("sahara_prefs", Context.MODE_PRIVATE)
                    .edit().putString("user_full_name", newName).apply()
                resolvedName = newName
                onComplete()
            }
            isUpdatingName = false
        }
    }

    fun updateRegion(newRegion: String, onComplete: () -> Unit) {
        val currentUid = uid
        if (currentUid.isBlank() || isUpdatingRegion) return
        viewModelScope.launch {
            isUpdatingRegion = true
            RealtimeDBService.updateUserRegion(currentUid, newRegion).onSuccess {
                resolvedRegion = newRegion
                onComplete()
            }
            isUpdatingRegion = false
        }
    }

    fun refreshRegionFromDevice(context: Context, onComplete: (Boolean) -> Unit = {}) {
        val currentUid = uid
        if (currentUid.isBlank() || isUpdatingRegion) {
            onComplete(false)
            return
        }
        viewModelScope.launch {
            isUpdatingRegion = true
            val detected = detectDeviceRegion(context)
            if (detected.isNullOrBlank()) {
                isUpdatingRegion = false
                onComplete(false)
                return@launch
            }
            RealtimeDBService.updateUserRegion(currentUid, detected).onSuccess {
                resolvedRegion = detected
                onComplete(true)
            }.onFailure {
                onComplete(false)
            }
            isUpdatingRegion = false
        }
    }

    fun togglePrivacyMode(context: Context, enabled: Boolean) {
        isPrivacyModeEnabled = enabled
        context.applicationContext.getSharedPreferences("sahara_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("privacy_mode", enabled).apply()
    }

    fun updateAvatarId(newAvatarId: String, isEnglish: Boolean) {
        val currentUid = uid
        if (currentUid.isBlank()) return
        viewModelScope.launch {
            avatarMessage = ""
            RealtimeDBService.updateUserAvatarId(currentUid, newAvatarId).onSuccess {
                avatarId = newAvatarId
                customAvatarUrl = ""
                customAvatarStatus = ""
                avatarMessage = if (isEnglish) "Avatar updated." else "Avatar update ho gaya."
            }.onFailure {
                avatarMessage = it.message ?: if (isEnglish) "Could not update avatar." else "Avatar update nahi ho saka."
            }
        }
    }

    fun submitCustomAvatar(
        uri: Uri,
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
        email: String,
        isEnglish: Boolean,
    ) {
        val currentUid = uid
        if (currentUid.isBlank() || isUploadingAvatar) return
        viewModelScope.launch {
            isUploadingAvatar = true
            avatarMessage = ""
            RealtimeDBService.submitAvatarRequest(
                userId = currentUid,
                userEmail = email,
                userName = resolvedName,
                fileUri = uri,
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
            ).onSuccess {
                customAvatarStatus = "PENDING_REVIEW"
                avatarMessage = if (isEnglish) {
                    "Avatar sent for admin review."
                } else {
                    "Avatar admin review ke liye bhej diya gaya."
                }
            }.onFailure {
                avatarMessage = it.message ?: if (isEnglish) "Could not upload avatar." else "Avatar upload nahi ho saka."
            }
            isUploadingAvatar = false
        }
    }

    private suspend fun detectDeviceRegion(context: Context): String? {
        normalizedLocation(GlobalAppState.userLocation)?.let { return it }
        if (!hasLocationPermission(context)) return null
        return runCatching {
            val appContext = context.applicationContext
            val location = LocationServices.getFusedLocationProviderClient(appContext).lastLocation.await()
                ?: return@runCatching null
            val address = Geocoder(appContext, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
                ?: return@runCatching null
            val area = address.subLocality ?: address.featureName
            val city = address.locality ?: address.subAdminArea ?: address.adminArea
            val detected = listOfNotNull(area, city)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(", ")
            normalizedLocation(detected)?.also {
                GlobalAppState.userLocation = it
                GlobalAppState.hasGrantedLocation = true
            }
        }.getOrNull()
    }

    private fun hasLocationPermission(context: Context): Boolean {
        val appContext = context.applicationContext
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun normalizedLocation(value: String): String? {
        val clean = value.trim()
        if (clean.isBlank()) return null
        if (clean.equals("Locating...", ignoreCase = true)) return null
        if (clean.equals("Locating…", ignoreCase = true)) return null
        if (clean.equals("Location Unavailable", ignoreCase = true)) return null
        if (clean.equals("Permission Denied", ignoreCase = true)) return null
        if (clean.equals("Location Error", ignoreCase = true)) return null
        return clean
    }
}
