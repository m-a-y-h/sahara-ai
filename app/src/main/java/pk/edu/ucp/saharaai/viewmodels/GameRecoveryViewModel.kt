package pk.edu.ucp.saharaai.viewmodels

import android.content.Context
import android.location.Geocoder
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import pk.edu.ucp.saharaai.data.repository.ChatRepository
import pk.edu.ucp.saharaai.ui.screens.GlobalAppState
import pk.edu.ucp.saharaai.utils.DesiAliasGenerator
import pk.edu.ucp.saharaai.utils.NotificationManager
import java.util.Locale

data class LeaderboardUser(val username: String, val location: String, val xp: Int, val rank: Int, val isCurrentUser: Boolean = false)

class GameRecoveryViewModel : ViewModel() {
    var uid by mutableStateOf("")
        private set
    var tempAlias by mutableStateOf(GlobalAppState.anonymousUsername.ifEmpty { generateDesiAlias() })

    fun initialize() {
        uid = Firebase.auth.currentUser?.uid.orEmpty()
    }

    fun loadGameData(
        today: String,
        thisWeek: String,
        onProfileLoaded: (Map<String, Any>?) -> Unit,
        onLeaderboardsLoaded: (List<Map<String, Any>>, List<Map<String, Any>>, List<Map<String, Any>>) -> Unit
    ) {
        if (uid.isBlank()) {
            onProfileLoaded(null)
            onLeaderboardsLoaded(emptyList(), emptyList(), emptyList())
            return
        }
        viewModelScope.launch {
            GlobalAppState.hasCheckedIn = ChatRepository.hasUserAiMessageToday(uid)
            onProfileLoaded(RealtimeDBService.loadGameProfile(uid, today, thisWeek))
            onLeaderboardsLoaded(
                RealtimeDBService.loadGameLeaderboard("daily", 20, today, thisWeek),
                RealtimeDBService.loadGameLeaderboard("weekly", 20, today, thisWeek),
                RealtimeDBService.loadGameLeaderboard("allTime", 20)
            )
        }
    }

    fun saveAlias(alias: String, location: String) {
        if (uid.isBlank()) return
        viewModelScope.launch { RealtimeDBService.saveGameAlias(uid, alias, location) }
    }

    fun startRecovery(
        alias: String,
        location: String,
        onComplete: (Result<Map<String, Any>>) -> Unit,
    ) {
        if (uid.isBlank()) {
            onComplete(Result.failure(IllegalStateException("Please sign in again before starting Game Recovery.")))
            return
        }
        viewModelScope.launch {
            onComplete(RealtimeDBService.startGameRecovery(uid, alias, location))
        }
    }

    fun completeTask(
        taskId: String,
        xp: Int,
        today: String,
        thisWeek: String,
        onComplete: (Map<String, Any>?) -> Unit
    ) {
        if (uid.isBlank()) {
            onComplete(null)
            return
        }
        viewModelScope.launch {
            onComplete(RealtimeDBService.completeGameTask(uid, taskId, xp, today, thisWeek).getOrNull())
        }
    }

    val currentUser: LeaderboardUser
        get() = LeaderboardUser(
            username = GlobalAppState.anonymousUsername.ifEmpty { tempAlias },
            location = GlobalAppState.userLocation.ifEmpty { "Locating..." },
            xp = 0,
            rank = 999,
            isCurrentUser = true
        )

    val dailyLeaderboard = listOf(
        LeaderboardUser("ZaalimBroast", "Clifton, Karachi", 120, 1),
        LeaderboardUser("NaughtyKhashkash", "Bahria Town, Lahore", 95, 2),
        LeaderboardUser("EpicParatha", "DHA, Multan", 60, 3)
    )

    val weeklyLeaderboard = listOf(
        LeaderboardUser("ShahiKinnu", "F-8, Islamabad", 1500, 1),
        LeaderboardUser("ZaalimBroast", "Clifton, Karachi", 1240, 2),
        LeaderboardUser("MasoomFalsa", "PECHS, Karachi", 980, 3)
    )

    val allTimeLeaderboard = listOf(
        LeaderboardUser("MasoomFalsa", "PECHS, Karachi", 85000, 1),
        LeaderboardUser("ChatpataSonf", "Cavalry Ground, Lahore", 72000, 2),
        LeaderboardUser("ThandaSamosa", "Saddar, Rawalpindi", 68500, 3)
    )

    fun randomizeAlias() {
        tempAlias = generateDesiAlias()
    }

    fun onLocationGranted(context: Context) {
        GlobalAppState.hasGrantedLocation = true
        GlobalAppState.anonymousUsername = tempAlias
        NotificationManager.logUsername(tempAlias)
        fetchRealLocation(context) { realLoc ->
            GlobalAppState.userLocation = realLoc
        }
    }

    private fun generateDesiAlias(): String = DesiAliasGenerator.generate()

    private fun fetchRealLocation(context: Context, onLocationFetched: (String) -> Unit) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        val subLocality = address.subLocality ?: address.featureName ?: "Local Arena"
                        val locality = address.locality ?: address.subAdminArea ?: "Unknown City"
                        onLocationFetched("$subLocality, $locality")
                    } else {
                        onLocationFetched("Unknown Sector")
                    }
                } else {
                    onLocationFetched("Location Unavailable")
                }
            }
        } catch (e: SecurityException) {
            onLocationFetched("Permission Denied")
        } catch (e: Exception) {
            onLocationFetched("Location Error")
        }
    }
}
