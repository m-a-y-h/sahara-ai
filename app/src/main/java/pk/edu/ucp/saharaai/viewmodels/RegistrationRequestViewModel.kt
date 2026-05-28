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
                val city = address.locality.orEmpty().ifBlank { address.subAdminArea.orEmpty() }
                    .ifBlank { address.adminArea.orEmpty() }
                val district = address.subAdminArea.orEmpty().ifBlank { address.locality.orEmpty() }
                    .ifBlank { address.adminArea.orEmpty() }
                require(city.isNotBlank() && district.isNotBlank()) { "Could not determine city or district." }
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
            RealtimeDBService.submitRegistrationRequest(
                applicantType,
                name.trim(),
                organization.trim(),
                email.trim(),
                phone.trim(),
                region.trim(),
                city.trim(),
                district.trim(),
                locationAccuracyMeters,
                verificationBody.trim(),
                registrationNumber.trim(),
                qualificationSummary.trim(),
                details.trim(),
                documentUris,
                requiredDocumentKeys
            ).onSuccess {
                submitted = true
            }.onFailure {
                error = it.message.orEmpty()
            }
            isSubmitting = false
        }
    }
}
