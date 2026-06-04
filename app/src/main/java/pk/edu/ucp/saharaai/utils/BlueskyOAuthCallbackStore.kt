package pk.edu.ucp.saharaai.util

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BlueskyOAuthResult(
    val did: String = "",
    val handle: String = "",
    val error: String = ""
)


object BlueskyOAuthCallbackStore {
    private val _pending = MutableStateFlow<BlueskyOAuthResult?>(null)
    val pending = _pending.asStateFlow()

    fun capture(intent: Intent?): Boolean {
        val data = intent?.data ?: return false
        if (
            intent.action != Intent.ACTION_VIEW ||
            data.scheme != "saharaai" ||
            data.host != "oauth" ||
            data.path != "/bluesky/callback"
        ) {
            return false
        }

        _pending.value = BlueskyOAuthResult(
            did = data.getQueryParameter("did").orEmpty(),
            handle = data.getQueryParameter("handle").orEmpty(),
            error = data.getQueryParameter("error").orEmpty()
        )
        return true
    }

    fun consume(result: BlueskyOAuthResult) {
        if (_pending.value == result) _pending.value = null
    }
}
