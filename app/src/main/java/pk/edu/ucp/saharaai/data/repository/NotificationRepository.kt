package pk.edu.ucp.saharaai.data.repository

import kotlinx.coroutines.flow.Flow
import pk.edu.ucp.saharaai.data.model.AppNotification
import pk.edu.ucp.saharaai.data.model.NotificationType
import pk.edu.ucp.saharaai.data.remote.FirestoreService
import com.google.firebase.Timestamp

object NotificationRepository {

    
    suspend fun send(notif: AppNotification): Result<String> =
        FirestoreService.saveNotification(notif)

    
    suspend fun sendCrisisAlert(userId: String): Result<String> = send(
        AppNotification(
            userId    = userId,
            titleEn   = "Crisis Alert",
            titleUr   = "Hanggami Alert",
            bodyEn    = "A high-risk signal was detected. Please reach out to your counselor or call 1166.",
            bodyUr    = "Zyada khatre ka signal mila. Apne counselor se rabta karein ya 1166 call karein.",
            type      = NotificationType.CRISIS.name,
            actionRoute = "emergency",
            timestamp = Timestamp.now()
        )
    )

    suspend fun sendRelapseAlert(userId: String): Result<String> = send(
        AppNotification(
            userId    = userId,
            titleEn   = "Relapse Risk Detected",
            titleUr   = "Dobara Girne Ka Khatra",
            bodyEn    = "Your behavioral patterns suggest increased risk. We're here for you.",
            bodyUr    = "Aapke patterns mein khatray ki nishanian hain. Hum aapke sath hain.",
            type      = NotificationType.RELAPSE.name,
            actionRoute = "assessment",
            timestamp = Timestamp.now()
        )
    )

    suspend fun sendCounselorMessage(userId: String, counselorName: String): Result<String> = send(
        AppNotification(
            userId    = userId,
            titleEn   = "Message from $counselorName",
            titleUr   = "$counselorName ka Paigham",
            bodyEn    = "Your counselor has sent you a new message.",
            bodyUr    = "Aapke counselor ne aapko naya paigham bheja hai.",
            type      = NotificationType.COUNSELOR.name,
            actionRoute = "chat",
            timestamp = Timestamp.now()
        )
    )

    suspend fun sendAchievement(userId: String, achievement: String): Result<String> = send(
        AppNotification(
            userId    = userId,
            titleEn   = "Achievement Unlocked!",
            titleUr   = "Achievement Mil Gayi!",
            bodyEn    = "You earned: $achievement. Keep going!",
            bodyUr    = "Aapko mili: $achievement. Aise hi aage barhein!",
            type      = NotificationType.ACHIEVEMENT.name,
            actionRoute = "recovery",
            timestamp = Timestamp.now()
        )
    )

    
    suspend fun getNotifications(userId: String): Result<List<AppNotification>> =
        FirestoreService.getNotifications(userId)

    
    fun getNotificationsFlow(userId: String): Flow<List<AppNotification>> =
        FirestoreService.getNotificationsFlow(userId)

    
    suspend fun markRead(notifId: String): Result<Unit> =
        FirestoreService.markNotificationRead(notifId)

    
    suspend fun markAllRead(userId: String): Result<Unit> =
        FirestoreService.markAllNotificationsRead(userId)
}
