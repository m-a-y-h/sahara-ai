package pk.edu.ucp.saharaai.service

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import pk.edu.ucp.saharaai.utils.NotificationHelper

class SaharaFirebaseMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        serviceScope.launch {
            RealtimeDBService.saveDeviceToken(uid, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        NotificationHelper.init(this)
        val data = message.data
        val title = message.notification?.title
            ?: data["title"]
            ?: data["titleEn"]
            ?: "Sahara AI"
        val body = message.notification?.body
            ?: data["body"]
            ?: data["bodyEn"]
            ?: ""
        if (body.isBlank()) return

        NotificationHelper.showPushNotification(
            context = this,
            title = title,
            body = body,
            type = data["type"].orEmpty(),
            actionRoute = data["actionRoute"].orEmpty(),
        )
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
