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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    notice: MonitoringStartNotice,
    isEnglish: Boolean,
    onAcknowledge: () -> Unit,
) {
    AlertDialog(
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
    )
}

/**
 * The risk service deliberately serialises monitoring timestamps without
 * seconds; on the off chance the ISO string contains them (e.g. a manual
 * back-fill), trim them so the popup still reads cleanly.
 */
private fun formatNoSeconds(iso: String): String {
    if (iso.isBlank()) return "—"
    // ISO-8601 minute precision is "YYYY-MM-DDTHH:MM(+ZZ:ZZ)?". If the input
    // has seconds we slice them out without parsing — easier than involving
    // a date-time formatter for an already-formatted server string.
    val tIndex = iso.indexOf('T')
    if (tIndex < 0) return iso
    val datePart = iso.substring(0, tIndex)
    val rest = iso.substring(tIndex + 1)
    val tzIndex = rest.indexOfAny(charArrayOf('+', 'Z', '-'), startIndex = 0)
    val time = if (tzIndex > 0) rest.substring(0, tzIndex) else rest
    val tz = if (tzIndex > 0) rest.substring(tzIndex) else ""
    val timeNoSeconds = time.split(":").take(2).joinToString(":")
    return "$datePart  $timeNoSeconds${if (tz.isNotEmpty()) " $tz" else ""}"
}
