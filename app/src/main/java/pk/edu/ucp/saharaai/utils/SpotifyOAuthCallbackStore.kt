package pk.edu.ucp.saharaai.util

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SpotifyOAuthResult(
    val spotifyId: String = "",
    val displayName: String = "",
    val error: String = ""
)


object SpotifyOAuthCallbackStore {
    private val _pending = MutableStateFlow<SpotifyOAuthResult?>(null)
    val pending = _pending.asStateFlow()

    fun capture(intent: Intent?): Boolean {
        val data = intent?.data ?: return false
        if (
            intent.action != Intent.ACTION_VIEW ||
            data.scheme != "saharaai" ||
            data.host != "oauth" ||
            data.path != "/spotify/callback"
        ) {
            return false
        }

        _pending.value = SpotifyOAuthResult(
            spotifyId = data.getQueryParameter("spotifyId").orEmpty(),
            displayName = data.getQueryParameter("displayName").orEmpty(),
            error = data.getQueryParameter("error").orEmpty()
        )
        return true
    }

    fun consume(result: SpotifyOAuthResult) {
        if (_pending.value == result) _pending.value = null
    }
}
