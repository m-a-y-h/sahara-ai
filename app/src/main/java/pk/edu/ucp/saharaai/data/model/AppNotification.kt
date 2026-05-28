package pk.edu.ucp.saharaai.data.model

import com.google.firebase.Timestamp

enum class NotificationType { CRISIS, RELAPSE, COUNSELOR, ACHIEVEMENT, GENERAL, SYSTEM }

data class AppNotification(
    val notificationId: String = "",
    val userId: String = "",
    val titleEn: String = "",
    val titleUr: String = "",
    val bodyEn: String = "",
    val bodyUr: String = "",
    val type: String = NotificationType.GENERAL.name,
    val isRead: Boolean = false,
    val actionRoute: String = "",
    val timestamp: Timestamp = Timestamp.now()
) {
    constructor() : this("", "", "", "", "", "", NotificationType.GENERAL.name, false, "", Timestamp.now())
}
