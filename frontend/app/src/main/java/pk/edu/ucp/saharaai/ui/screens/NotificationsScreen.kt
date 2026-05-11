package pk.edu.ucp.saharaai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pk.edu.ucp.saharaai.ui.components.CardVariant
import pk.edu.ucp.saharaai.ui.components.SaharaCard
import pk.edu.ucp.saharaai.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable

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

@Composable
fun NotificationsScreen(
    onNavigateBack: () -> Unit,
    isEnglish: Boolean = false
) {
    val isDark = isSystemInDarkTheme()
    val primaryGreen = SaharaStrongGreen

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isDark) Color.DarkGray.copy(alpha = 0.5f) else Color.LightGray.copy(alpha = 0.3f))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = primaryGreen)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = if (isEnglish) "Notifications" else "Itla'aat (Notifications)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = primaryGreen
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->

        if (NotificationManager.notifications.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.NotificationsOff,
                        contentDescription = "Empty",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = if (isEnglish) "All Caught Up!" else "Koi Nayi Ittila Nahi!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isEnglish) "Your activity logs and updates will appear here." else "Aapki sargarmiyon ki tafseelat yahan nazar aayengi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(NotificationManager.notifications) { notification ->
                    val readAlpha = if (notification.isUnread) 1f else 0.5f

                    SaharaCard(
                        variant = CardVariant.DEFAULT,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = readAlpha }
                            .clickable { NotificationManager.markAsRead(notification) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(notification.iconTint.copy(alpha = if (isDark) 0.25f else 0.15f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(notification.icon, contentDescription = null, tint = notification.iconTint)
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isEnglish) notification.titleEn else notification.titleUr,
                                        fontWeight = if (notification.isUnread) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (notification.isUnread) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(SaharaCoral, CircleShape)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = if (isEnglish) notification.messageEn else notification.messageUr,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = notification.time,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}