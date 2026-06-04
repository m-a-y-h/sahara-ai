package pk.edu.ucp.saharaai.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.model.SocialPlatformConnection
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import pk.edu.ucp.saharaai.data.remote.YouTubeChannelClient
import pk.edu.ucp.saharaai.data.remote.YouTubeSubscription


class SocialConnectionsViewModel : ViewModel() {

    val signedInUserId: String get() = Firebase.auth.currentUser?.uid.orEmpty()

    
    private val _connected = MutableStateFlow<Map<String, SocialPlatformConnection>>(emptyMap())
    val connected: StateFlow<Map<String, SocialPlatformConnection>> = _connected.asStateFlow()

    
    private val _saving = MutableStateFlow<String?>(null)
    val saving: StateFlow<String?> = _saving.asStateFlow()

    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var uid = ""

    
    fun init(userId: String) {
        if (userId.isBlank() || uid == userId) return
        uid = userId
        viewModelScope.launch {
            RealtimeDBService.listenToSocialConnections(uid).collect { map ->
                _connected.value = map
            }
        }
    }

    
    fun connect(platform: String, username: String) {
        val trimmed = username.trim()
        if (uid.isBlank() || trimmed.isBlank()) return
        viewModelScope.launch {
            _saving.value = platform
            RealtimeDBService.saveSocialConnection(uid, platform, trimmed)
                .onFailure { _error.value = it.message ?: "Failed to connect $platform." }
            _saving.value = null
        }
    }

    fun completeBlueskyConnection(handle: String, did: String, consentVersion: String) {
        if (uid.isBlank()) {
            _error.value = "Please sign in before connecting Bluesky."
            return
        }
        if (!did.startsWith("did:") || handle.isBlank()) {
            _error.value = "Bluesky did not return a valid account identity."
            return
        }
        viewModelScope.launch {
            _saving.value = "bluesky"
            RealtimeDBService.saveVerifiedBlueskyConnection(uid, handle, did, consentVersion)
                .onFailure { _error.value = it.message ?: "Failed to store Bluesky connection." }
            _saving.value = null
        }
    }

    fun completeSteamConnection(steamId: String, displayName: String, consentVersion: String) {
        if (uid.isBlank()) {
            _error.value = "Please sign in before connecting Steam."
            return
        }
        if (!steamId.matches(Regex("""\d{17,25}"""))) {
            _error.value = "Steam did not return a valid account identity."
            return
        }
        viewModelScope.launch {
            _saving.value = "steam"
            RealtimeDBService.saveVerifiedSteamConnection(uid, steamId, displayName, consentVersion)
                .onFailure { _error.value = it.message ?: "Failed to store Steam connection." }
            _saving.value = null
        }
    }

    fun completeSpotifyConnection(spotifyId: String, displayName: String, consentVersion: String) {
        if (uid.isBlank()) {
            _error.value = "Please sign in before connecting Spotify."
            return
        }
        if (spotifyId.isBlank()) {
            _error.value = "Spotify did not return a valid account identity."
            return
        }
        viewModelScope.launch {
            _saving.value = "spotify"
            RealtimeDBService.saveVerifiedSpotifyConnection(uid, spotifyId, displayName, consentVersion)
                .onFailure { _error.value = it.message ?: "Failed to store Spotify connection." }
            _saving.value = null
        }
    }

    fun completeYouTubeConnection(
        channelId: String,
        channelTitle: String,
        subscriptions: List<YouTubeSubscription>,
        subscriptionsTruncated: Boolean,
        consentVersion: String
    ) {
        if (uid.isBlank()) {
            _error.value = "Please sign in before connecting YouTube."
            return
        }
        if (channelId.isBlank()) {
            _error.value = "YouTube did not return a valid channel identity."
            return
        }
        viewModelScope.launch {
            _saving.value = "youtube"
            RealtimeDBService.saveVerifiedYouTubeConnection(
                uid,
                channelId,
                channelTitle,
                subscriptions,
                subscriptionsTruncated,
                consentVersion
            )
                .onFailure { _error.value = it.message ?: "Failed to store YouTube connection." }
            _saving.value = null
        }
    }

    fun completeYouTubeAuthorization(accessToken: String, consentVersion: String) {
        if (accessToken.isBlank()) {
            _error.value = "Google did not return YouTube authorization."
            return
        }
        viewModelScope.launch {
            _saving.value = "youtube"
            YouTubeChannelClient.loadAuthorizedData(accessToken)
                .onSuccess { data ->
                    RealtimeDBService.saveVerifiedYouTubeConnection(
                        uid = uid,
                        channelId = data.channelId,
                        channelTitle = data.channelTitle,
                        subscriptions = data.subscriptions,
                        subscriptionsTruncated = data.subscriptionsTruncated,
                        consentVersion = consentVersion
                    ).onFailure {
                        _error.value = it.message ?: "Failed to store YouTube connection."
                    }

                    // Reverse search: scan the freshly-fetched subscriptions
                    // against the rule-based substance / recovery deny-lists and
                    // persist the summary alongside the raw list. Failure here
                    // is non-fatal — the raw connection still goes in.
                    val report = pk.edu.ucp.saharaai.utils.YouTubeSubscriptionClassifier
                        .classify(data.subscriptions)
                    val recoveryCount = pk.edu.ucp.saharaai.utils.YouTubeSubscriptionClassifier
                        .recoveryChannelCount(data.subscriptions)
                    val flaggedPayload = report.flagged.map {
                        mapOf(
                            "channelId"    to it.channelId,
                            "channelTitle" to it.channelTitle,
                            "severity"     to it.severity.name,
                            "matches"      to it.matches,
                        )
                    }
                    RealtimeDBService.saveYouTubeFlaggedSubscriptions(
                        uid                  = uid,
                        totalSubscriptions   = report.totalSubscriptions,
                        flagged              = flaggedPayload,
                        overallSeverity      = report.overallSeverity.name,
                        recoveryChannelCount = recoveryCount,
                    )
                    _youtubeFlaggedCount.value = report.flaggedCount
                    _youtubeRecoveryCount.value = recoveryCount
                    _youtubeOverallSeverity.value = report.overallSeverity.name
                }
                .onFailure {
                    _error.value = it.message ?: "Failed to read YouTube subscriptions."
                }
            _saving.value = null
        }
    }

    // Lightweight UI state for the Connections panel to show a "found N
    // concerning channels in your N subscriptions" banner immediately after
    // the YouTube connect completes, without re-reading from RTDB.
    private val _youtubeFlaggedCount = kotlinx.coroutines.flow.MutableStateFlow(0)
    val youtubeFlaggedCount: kotlinx.coroutines.flow.StateFlow<Int> = _youtubeFlaggedCount
    private val _youtubeRecoveryCount = kotlinx.coroutines.flow.MutableStateFlow(0)
    val youtubeRecoveryCount: kotlinx.coroutines.flow.StateFlow<Int> = _youtubeRecoveryCount
    private val _youtubeOverallSeverity = kotlinx.coroutines.flow.MutableStateFlow("NONE")
    val youtubeOverallSeverity: kotlinx.coroutines.flow.StateFlow<String> = _youtubeOverallSeverity

    fun reportError(message: String) {
        _error.value = message
    }

    
    fun disconnect(platform: String) {
        if (uid.isBlank()) return
        viewModelScope.launch {
            _saving.value = platform
            RealtimeDBService.removeSocialConnection(uid, platform)
                .onFailure { _error.value = it.message ?: "Failed to disconnect $platform." }
            _saving.value = null
        }
    }

    fun clearError() { _error.value = null }
}
