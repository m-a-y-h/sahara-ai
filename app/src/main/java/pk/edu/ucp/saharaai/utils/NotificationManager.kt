package pk.edu.ucp.saharaai.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import pk.edu.ucp.saharaai.ui.theme.SaharaPeach
import pk.edu.ucp.saharaai.ui.theme.SaharaSky
import pk.edu.ucp.saharaai.ui.theme.SaharaStrongGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NotificationItem(
    val titleEn: String,
    val titleUr: String,
    val messageEn: String,
    val messageUr: String,
    val time: String,
    val icon: ImageVector,
    val iconTint: Color,
    var isUnread: Boolean = true
)

object NotificationManager {
    val notifications = mutableStateListOf<NotificationItem>()

    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date())
    }

    fun logWelcome() {
        notifications.add(0, NotificationItem(
            titleEn = "Welcome to Sahara AI",
            titleUr = "Sahara AI mein Khush Amdeed",
            messageEn = "Your recovery journey starts today. We are here to support you every step of the way.",
            messageUr = "Aapka safar aaj shuru hota hai. Hum har qadam par aapke sath hain.",
            time = getCurrentTime(),
            icon = Icons.Default.WavingHand,
            iconTint = SaharaPeach
        ))
    }

    fun logAssessment(oldScore: Int, newScore: Int) {
        val trendEn = if (newScore < oldScore) "decreased" else if (newScore > oldScore) "increased" else "remained the same"
        val trendUr = if (newScore < oldScore) "kam ho gaya" else if (newScore > oldScore) "barh gaya" else "waisa hi"

        notifications.add(0, NotificationItem(
            titleEn = "Assessment Completed",
            titleUr = "Jaeza Mukammal",
            messageEn = "Your risk score has $trendEn from $oldScore to $newScore. Keep tracking your progress!",
            messageUr = "Aapka risk score $oldScore se $newScore tak $trendUr hai. Apni progress par nazar rakhein!",
            time = getCurrentTime(),
            icon = Icons.Default.AssignmentTurnedIn,
            iconTint = SaharaStrongGreen
        ))
    }

    fun logUsername(alias: String) {
        notifications.add(0, NotificationItem(
            titleEn = "Alias Assigned",
            titleUr = "Naya Naam",
            messageEn = "You are now playing as '$alias' in the local arenas. Stay anonymous, stay strong!",
            messageUr = "Local arenas mein ab aapka naam '$alias' hai. Mehfooz rahein, mazboot rahein!",
            time = getCurrentTime(),
            icon = Icons.Default.Badge,
            iconTint = SaharaSky
        ))
    }

    fun logAchievement(achievementEn: String, achievementUr: String) {
        notifications.add(0, NotificationItem(
            titleEn = "Achievement Unlocked!",
            titleUr = "Kamiyabi Hasil Ki!",
            messageEn = "Congratulations! You just unlocked: $achievementEn.",
            messageUr = "Mubarak ho! Aapne hasil kiya: $achievementUr.",
            time = getCurrentTime(),
            icon = Icons.Default.EmojiEvents,
            iconTint = Color(0xFFFFD700)
        ))
    }

    fun hasUnread(): Boolean {
        return notifications.any { it.isUnread }
    }

    fun markAsRead(notification: NotificationItem) {
        val index = notifications.indexOf(notification)
        if (index != -1 && notifications[index].isUnread) {
            notifications[index] = notifications[index].copy(isUnread = false)
        }
    }
}
