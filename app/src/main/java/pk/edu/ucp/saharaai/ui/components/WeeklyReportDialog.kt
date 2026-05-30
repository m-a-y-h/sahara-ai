package pk.edu.ucp.saharaai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pk.edu.ucp.saharaai.data.model.ListeningSeverity
import pk.edu.ucp.saharaai.data.model.WeeklyListeningReport
import pk.edu.ucp.saharaai.ui.theme.SaharaCoral
import pk.edu.ucp.saharaai.ui.theme.SaharaPeach
import pk.edu.ucp.saharaai.ui.theme.SaharaSky
import pk.edu.ucp.saharaai.ui.theme.SaharaStrongGreen
import pk.edu.ucp.saharaai.ui.theme.SaharaWarning

/**
 * Modal that auto-opens on the dashboard whenever a new
 * `weekly_reports/{week}` document arrives that the user hasn't yet seen.
 * Designed to be unobtrusive: a single sentence, a chip showing how many
 * tracks were flagged, and three actions — open the full report, snooze
 * (dismiss only), or delete the report entirely.
 */
@Composable
fun WeeklyReportPopupDialog(
    hazeState: HazeState,
    report: WeeklyListeningReport,
    isEnglish: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val (accent, headline) = when (report.severity) {
        ListeningSeverity.HIGH ->
            SaharaCoral to (if (isEnglish) "High-risk listening week" else "High-risk week")
        ListeningSeverity.MEDIUM ->
            SaharaPeach to (if (isEnglish) "Elevated listening pattern" else "Sun-ne mein elevated pattern")
        ListeningSeverity.LOW ->
            SaharaSky to (if (isEnglish) "Some flagged tracks this week" else "Kuch tracks flag hue is hafte")
        ListeningSeverity.NONE ->
            SaharaStrongGreen to (if (isEnglish) "Clean week — no flags" else "Saaf hafta — koi flag nahi")
        ListeningSeverity.UNKNOWN ->
            SaharaWarning to (if (isEnglish) "Weekly listening summary" else "Hafte ka summary")
    }

    GlassAlertDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = accent.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Icon(
                        Icons.Filled.Headphones,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier
                            .size(28.dp)
                            .padding(top = 10.dp),
                    )
                }
            }
        },
        title = {
            Text(
                headline,
                color = accent,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        },
        text = {
            Column {
                Text(
                    if (isEnglish) report.summaryLabel else report.summaryLabel,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                if (report.topGenres.isNotEmpty()) {
                    Text(
                        text = if (isEnglish) {
                            "Top genres: " + report.topGenres.take(3).joinToString { it.first }
                        } else {
                            "Top genres: " + report.topGenres.take(3).joinToString { it.first }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (report.severity == ListeningSeverity.HIGH ||
                    report.severity == ListeningSeverity.MEDIUM
                ) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = accent.copy(alpha = 0.10f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (isEnglish) {
                                "This is a screening signal, not a diagnosis. Open the report to see what was flagged and why."
                            } else {
                                "Ye sirf screening signal hai, diagnosis nahi. Report kholein ke kya flag hua aur kyun."
                            },
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpen) {
                Text(if (isEnglish) "Open report" else "Report kholein")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text(
                        if (isEnglish) "Delete" else "Delete",
                        color = SaharaCoral,
                    )
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) {
                    Text(if (isEnglish) "Later" else "Baad mein")
                }
            }
        },
    )
}
