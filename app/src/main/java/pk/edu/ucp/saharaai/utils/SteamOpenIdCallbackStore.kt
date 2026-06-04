package pk.edu.ucp.saharaai.utils

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SteamOpenIdResult(
    val steamId: String = "",
    val displayName: String = "",
    val error: String = ""
)


object SteamOpenIdCallbackStore {
    private val _pending = MutableStateFlow<SteamOpenIdResult?>(null)
    val pending = _pending.asStateFlow()

    fun capture(intent: Intent?): Boolean {
        val data = intent?.data ?: return false
        if (
            intent.action != Intent.ACTION_VIEW ||
            data.scheme != "saharaai" ||
            data.host != "openid" ||
            data.path != "/steam/callback"
        ) {
            return false
        }

        _pending.value = SteamOpenIdResult(
            steamId = data.getQueryParameter("steamId").orEmpty(),
            displayName = data.getQueryParameter("displayName").orEmpty(),
            error = data.getQueryParameter("error").orEmpty()
        )
        return true
    }

    fun consume(result: SteamOpenIdResult) {
        if (_pending.value == result) _pending.value = null
    }
}
