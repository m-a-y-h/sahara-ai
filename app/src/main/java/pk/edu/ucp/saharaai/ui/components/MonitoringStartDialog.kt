package pk.edu.ucp.saharaai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pk.edu.ucp.saharaai.R
import pk.edu.ucp.saharaai.data.model.MonitoringStartNotice
import pk.edu.ucp.saharaai.ui.theme.SaharaStrongGreen

/**
 * One-shot dashboard popup that fires when the 6-month monitoring window
 * opens (one week after the DAST assessment). Reads the timestamps and
 * localised text directly from the [MonitoringStartNotice] doc the
 * `services/sahara_risk` cron wrote, so the message stays in lock-step
 * with the backend's notion of when the window starts/ends.
 *
 * The popup is one-shot — `acknowledgeStartNotice` marks the doc as shown
 * once the user taps "Got it", so the dashboard never re-fires it.
 */
@Composable
fun MonitoringStartDialog(
    hazeState: HazeState,
    notice: MonitoringStartNotice,
    isEnglish: Boolean,
    onAcknowledge: () -> Unit,
) {
    GlassAlertDialog(
        hazeState = hazeState,
        onDismissRequest = onAcknowledge,
        icon = {
            Icon(Icons.Filled.Timeline, contentDescription = null, tint = SaharaStrongGreen)
        },
        title = {
            Text(
                text = if (isEnglish) "Behaviour-cycle monitoring has begun"
                       else "Behaviour-cycle monitoring shuru ho gaya hai",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = SaharaStrongGreen,
            )
        },
        text = {
            Column {
                Text(
                    text = if (isEnglish) notice.noticeTextEn else notice.noticeTextUr,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SaharaStrongGreen.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = if (isEnglish) "Started" else "Shuru",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            // The backend writes the ISO timestamps with minute-level precision (no seconds).
                            text = formatNoSeconds(notice.monitoringStartsAtIso),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (isEnglish) "Ends" else "Khatam",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatNoSeconds(notice.monitoringEndsAtIso),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (isEnglish) "${notice.durationWeeks} weeks (6 months)"
                                   else "${notice.durationWeeks} hafte (6 mahine)",
                            style = MaterialTheme.typography.labelSmall,
                            color = SaharaStrongGreen,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAcknowledge) {
                Text(if (isEnglish) "Got it" else "Theek hai")
            }
        },
        panelBackdropResId = R.drawable.sahara_bg5,
    )
}

/**
 * The risk service serialises monitoring timestamps as a UTC instant ("...Z").
 * Render it in the device's local timezone (minute precision) so the time
 * matches the user's clock — and there's no stray "Z" on screen.
 */
private fun formatNoSeconds(iso: String): String {
    if (iso.isBlank()) return "—"
    val out = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm")
    runCatching {
        // Handles "Z" and explicit offsets like "+05:00", with or without seconds.
        return java.time.OffsetDateTime.parse(iso)
            .atZoneSameInstant(java.time.ZoneId.systemDefault())
            .format(out)
    }
    runCatching {
        // No timezone in the string — treat as a local date-time, trim to minutes.
        return java.time.LocalDateTime.parse(iso).format(out)
    }
    return iso
}
