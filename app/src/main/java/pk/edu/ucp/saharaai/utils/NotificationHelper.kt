package pk.edu.ucp.saharaai.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.R as AndroidR
import pk.edu.ucp.saharaai.MainActivity


object NotificationHelper {

    const val CHANNEL_COMMUNITY = "sahara_community"
    const val CHANNEL_COUNSELOR = "sahara_counselor"
    const val CHANNEL_SYSTEM = "sahara_system"

    private var nextId = 1000

    
    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_COMMUNITY,
                    "Community",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Community replies and interactions" }
            )

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_COUNSELOR,
                    "Counselor Messages",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Messages from your assigned counselor" }
            )

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SYSTEM,
                    "Sahara Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Account, avatar, and app updates" }
            )
        }
    }

    
    fun showReplyNotification(context: Context, title: String, body: String) {
        show(context, CHANNEL_COMMUNITY, title, body)
    }

    
    fun showCounselorNotification(context: Context, title: String, body: String) {
        show(context, CHANNEL_COUNSELOR, title, body)
    }

    fun showPushNotification(
        context: Context,
        title: String,
        body: String,
        type: String,
        actionRoute: String = "",
    ) {
        val channelId = when (type.uppercase()) {
            "COUNSELOR", "CHAT", "MESSAGE" -> CHANNEL_COUNSELOR
            "COMMUNITY" -> CHANNEL_COMMUNITY
            else -> CHANNEL_SYSTEM
        }
        show(context, channelId, title, body, actionRoute)
    }
    

    private fun show(
        context: Context,
        channelId: String,
        title: String,
        body: String,
        actionRoute: String = "",
    ) {
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("actionRoute", actionRoute)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, nextId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(AndroidR.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(nextId++, notification)
    }
}
