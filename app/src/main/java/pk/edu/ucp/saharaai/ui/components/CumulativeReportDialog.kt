package pk.edu.ucp.saharaai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pk.edu.ucp.saharaai.data.model.CumulativeRiskReport
import pk.edu.ucp.saharaai.ui.theme.SaharaStrongGreen

@Composable
fun CumulativeReportDialog(
    hazeState: HazeState,
    report: CumulativeRiskReport,
    isEnglish: Boolean,
    onViewProgress: () -> Unit,
    onAcknowledge: () -> Unit,
) {
    val driverText = report.featureSourceSummary.entries
        .sortedByDescending { it.value["mean_contribution"] ?: 0.0 }
        .take(3)
        .joinToString { it.key.replace("_", " ") }
        .ifBlank { if (isEnglish) "No dominant driver" else "Koi dominant driver nahi" }

    GlassAlertDialog(
        hazeState = hazeState,
        onDismissRequest = onAcknowledge,
        icon = {
            Icon(Icons.Default.Assessment, contentDescription = null, tint = SaharaStrongGreen)
        },
        title = {
            Text(
                if (isEnglish) "Your 6-month report is ready" else "Aapki 6-month report tayyar hai",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (isEnglish)
                        "This cycle is complete. Please take your assessment again to unlock calculators and chat."
                    else
                        "Yeh cycle mukammal ho gaya hai. Calculators aur chat unlock karne ke liye assessment dobara complete karein.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ReportMetric(
                        label = if (isEnglish) "Final score" else "Final score",
                        value = "${report.finalRiskPercent}%",
                    )
                    ReportMetric(
                        label = if (isEnglish) "Weeks" else "Weeks",
                        value = "${report.completedWeeks}/26",
                    )
                }
                Text(
                    text = if (isEnglish) "Top signals: $driverText" else "Top signals: $driverText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onViewProgress,
                colors = ButtonDefaults.buttonColors(containerColor = SaharaStrongGreen),
            ) {
                Text(if (isEnglish) "View progress" else "Progress dekhein")
            }
        },
        dismissButton = {
            TextButton(onClick = onAcknowledge) {
                Text(if (isEnglish) "Got it" else "Theek hai")
            }
        },
    )
}

@Composable
private fun ReportMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.padding(end = 16.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}
