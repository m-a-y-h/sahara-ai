package pk.edu.ucp.saharaai.viewmodels

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.messaging.messaging
import kotlinx.coroutines.tasks.await
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import java.util.Locale

data class PreciseApplicationLocation(
    val city: String,
    val district: String,
    val accuracyMeters: Float,
)

class RegistrationRequestViewModel : ViewModel() {
    var isSubmitting by mutableStateOf(false)
        private set
    var submitted by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    var isLocating by mutableStateOf(false)
        private set
    var locationError by mutableStateOf("")
        private set

    fun reportError(message: String) {
        error = message
    }

    fun fetchPreciseLocation(
        context: Context,
        isEnglish: Boolean,
        onLocated: (PreciseApplicationLocation) -> Unit,
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationError = if (isEnglish) "Precise location permission is required." else "Precise location ki ijazat zaroori hai."
            return
        }
        isLocating = true
        locationError = ""
        viewModelScope.launch {
            runCatching {
                val location = LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                    .await() ?: error("Turn on location and try again.")
                val address = withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    Geocoder(context, Locale.getDefault())
                        .getFromLocation(location.latitude, location.longitude, 1)
                        ?.firstOrNull()
                } ?: error("No address was found for this location.")
                // Geocoder returns layered admin levels; the previous version
                // fell back to subAdminArea for BOTH city and district which
                // collapses to "Lahore, Lahore" for a Bahria Town address
                // (Pakistan's geocoder typically reports
                // locality=Lahore / subAdminArea=Lahore / subLocality=Bahria
                // Town). New mapping prefers the most-specific layer for
                // district and de-duplicates against city.
                val locality = address.locality.orEmpty()
                val subLocality = address.subLocality.orEmpty()
                val subAdmin = address.subAdminArea.orEmpty()
                val admin = address.adminArea.orEmpty()
                val feature = address.featureName.orEmpty()
                val thoroughfare = address.thoroughfare.orEmpty()

                val city = locality
                    .ifBlank { subAdmin }
                    .ifBlank { admin }
                val districtCandidates = listOf(subLocality, feature, thoroughfare, subAdmin)
                val district = districtCandidates.firstOrNull {
                    it.isNotBlank() && !it.equals(city, ignoreCase = true)
                }.orEmpty()
                require(city.isNotBlank()) { "Could not determine city." }
                // district may legitimately be blank (rural address, unmapped
                // neighbourhood). The form lets the user type it in; we
                // don't fail the geocode for that.
                PreciseApplicationLocation(city, district, location.accuracy)
            }.onSuccess(onLocated).onFailure {
                locationError = if (isEnglish) {
                    it.message ?: "Could not retrieve precise location."
                } else {
                    "Precise location nahi mil saki. Dobara koshish karein."
                }
            }
            isLocating = false
        }
    }

    fun submit(
        applicantType: String,
        name: String,
        organization: String,
        email: String,
        phone: String,
        region: String,
        city: String,
        district: String,
        locationAccuracyMeters: Float,
        verificationBody: String,
        registrationNumber: String,
        qualificationSummary: String,
        details: String,
        documentUris: Map<String, Uri>,
        requiredDocumentKeys: List<String>
    ) {
        error = ""
        isSubmitting = true
        viewModelScope.launch {
            // Cheap collision check against the public email_password_index
            // populated by AuthRepository on every email/password signup,
            // signin, and linkEmailPassword. If the applicant's email is
            // already attached to a Sahara account we bail before the
            // upload kicks in — saves the user a long compress/base64
            // path that would only fail later when the admin tries to
            // mint the counselor's Firebase user. Google-only accounts
            // (no password provider attached) won't be in the index;
            // those collisions get caught at the admin-approval step.
            val cleanEmail = email.trim().lowercase()
            val existingUid = runCatching {
                RealtimeDBService.lookupEmailHasPassword(cleanEmail)
            }.getOrNull()
            if (!existingUid.isNullOrBlank()) {
                error = "This email is already linked to a Sahara account. " +
                    "If you are the owner, please contact support — counselors must apply with a different email."
                isSubmitting = false
                return@launch
            }

            // Grab this device's FCM token (best-effort) so the admin's later
            // approval can push the issued key straight back to this device.
            // Failure is non-fatal — the email channel still works.
            val fcmToken = runCatching {
                com.google.firebase.Firebase.messaging.token
                    .await()
            }.getOrNull().orEmpty()
            RealtimeDBService.submitRegistrationRequest(
                applicantType = applicantType,
                applicantName = name.trim(),
                organizationName = organization.trim(),
                email = cleanEmail,
                phone = phone.trim(),
                region = region.trim(),
                city = city.trim(),
                district = district.trim(),
                locationAccuracyMeters = locationAccuracyMeters,
                verificationBody = verificationBody.trim(),
                registrationNumber = registrationNumber.trim(),
                qualificationSummary = qualificationSummary.trim(),
                details = details.trim(),
                documentUris = documentUris,
                requiredDocumentKeys = requiredDocumentKeys,
                applicantFcmToken = fcmToken,
            ).onSuccess {
                submitted = true
            }.onFailure {
                error = it.message.orEmpty()
            }
            isSubmitting = false
        }
    }
}
