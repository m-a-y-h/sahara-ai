package pk.edu.ucp.saharaai.util

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object NotificationRouteStore {
    private val _pending = MutableStateFlow<String?>(null)
    val pending = _pending.asStateFlow()

    fun capture(intent: Intent?): Boolean {
        val route = intent?.getStringExtra("actionRoute")?.trim().orEmpty()
        if (route.isBlank()) return false
        _pending.value = route
        return true
    }

    fun consume(route: String) {
        if (_pending.value == route) _pending.value = null
    }
}
