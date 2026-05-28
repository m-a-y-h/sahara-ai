package pk.edu.ucp.saharaai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import pk.edu.ucp.saharaai.data.model.ListeningSeverity
import pk.edu.ucp.saharaai.data.model.WeeklyListeningReport
import pk.edu.ucp.saharaai.ui.theme.SaharaCoral
import pk.edu.ucp.saharaai.ui.theme.SaharaPeach
import pk.edu.ucp.saharaai.ui.theme.SaharaSky
import pk.edu.ucp.saharaai.ui.theme.SaharaStrongGreen
import pk.edu.ucp.saharaai.ui.theme.SaharaWarning
import pk.edu.ucp.saharaai.viewmodels.WeeklyReportViewModel
import pk.edu.ucp.saharaai.ui.components.HazeBackButton
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * Past weekly listening reports — list view + tap-to-expand detail.
 *
 * Each row shows the week range, the severity bucket as a coloured tag, the
 * flagged/total count, and a delete icon. Tapping a row expands a detail
 * pane with the per-track breakdown.
 *
 * Privacy posture: the screen never displays the raw listening history.
 * The detail view only renders the tracks that were already classified as
 * flagged by the cron — anything below the threshold is dropped at write
 * time on the server.
 */
@Composable
fun WeeklyReportScreen(
    navController: NavController,
    isEnglish: Boolean = true,
    viewModel: WeeklyReportViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val reports by viewModel.reports.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    var pendingDeletion by remember { mutableStateOf<WeeklyListeningReport?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    val hazeState = remember { HazeState() }

    Box(
        Modifier
            .fillMaxSize()
            .hazeSource(hazeState)
            .background(
                Brush.verticalGradient(
                    listOf(
                        SaharaStrongGreen.copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.background,
                    )
                )
            )
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Top bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HazeBackButton(onClick = { navController.popBackStack() }, hazeState = hazeState)
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (isEnglish) "Weekly listening reports" else "Hafta-wari reports",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = if (isEnglish) {
                            "Saved digests — open any week to see flagged tracks."
                        } else {
                            "Save kiye gaye digests — kisi bhi hafte ki flag hui tracks dekhein."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when {
                isLoading && reports.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SaharaStrongGreen, strokeWidth = 4.dp)
                    }
                }
                error != null && reports.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = error ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SaharaCoral,
                        )
                    }
                }
                reports.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        EmptyState(isEnglish)
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(reports, key = { it.weekStartIso }) { report ->
                            ReportRow(
                                report = report,
                                isEnglish = isEnglish,
                                expanded = expanded == report.weekStartIso,
                                onToggle = {
                                    expanded = if (expanded == report.weekStartIso) null else report.weekStartIso
                                },
                                onDelete = { pendingDeletion = report },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDeletion?.let { rep ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = {
                Text(if (isEnglish) "Delete this report?" else "Ye report delete karein?")
            },
            text = {
                Text(
                    if (isEnglish) {
                        "${rep.weekStartIso.take(10)} — ${rep.flaggedCount} flagged track(s). This can't be undone."
                    } else {
                        "${rep.weekStartIso.take(10)} — ${rep.flaggedCount} flagged track(s). Wapas nahi aa sakta."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteReport(rep.weekStartIso)
                    pendingDeletion = null
                }) { Text(if (isEnglish) "Delete" else "Delete", color = SaharaCoral) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text(if (isEnglish) "Cancel" else "Cancel")
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Sub-composables
// ---------------------------------------------------------------------------

@Composable
private fun EmptyState(isEnglish: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Filled.Headphones,
            contentDescription = null,
            tint = SaharaStrongGreen,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (isEnglish) "No weekly reports yet" else "Abhi koi report nahi",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (isEnglish) {
                "Reports show up every Monday after Sahara's weekly run."
            } else {
                "Reports har peer ko Sahara ke weekly run ke baad show hoti hain."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReportRow(
    report: WeeklyListeningReport,
    isEnglish: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = colorForSeverity(report.severity)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SeverityChip(report.severity, isEnglish)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = report.weekStartIso.take(10) + " → " + report.weekEndIso.take(10),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = report.summaryLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = SaharaCoral)
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                if (report.flaggedTracks.isEmpty()) {
                    Text(
                        text = if (isEnglish) "No flagged tracks this week." else "Is hafte koi track flag nahi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    report.flaggedTracks.take(20).forEach { track ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colorForSeverity(track.severity).copy(alpha = 0.08f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(
                                    text = "${track.name} — ${track.artist}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                )
                                track.flagReasons.take(2).forEach { reason ->
                                    Text(
                                        text = "• $reason",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    if (report.flaggedTracks.size > 20) {
                        Text(
                            text = if (isEnglish) {
                                "+${report.flaggedTracks.size - 20} more flagged track(s)"
                            } else {
                                "+${report.flaggedTracks.size - 20} aur flag hui tracks"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeverityChip(severity: ListeningSeverity, isEnglish: Boolean) {
    val accent = colorForSeverity(severity)
    val label = when (severity) {
        ListeningSeverity.HIGH    -> if (isEnglish) "HIGH" else "ZYADA"
        ListeningSeverity.MEDIUM  -> if (isEnglish) "MED"  else "MED"
        ListeningSeverity.LOW     -> if (isEnglish) "LOW"  else "KAM"
        ListeningSeverity.NONE    -> if (isEnglish) "OK"   else "OK"
        ListeningSeverity.UNKNOWN -> "?"
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = accent.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun colorForSeverity(severity: ListeningSeverity) = when (severity) {
    ListeningSeverity.HIGH    -> SaharaCoral
    ListeningSeverity.MEDIUM  -> SaharaPeach
    ListeningSeverity.LOW     -> SaharaSky
    ListeningSeverity.NONE    -> SaharaStrongGreen
    ListeningSeverity.UNKNOWN -> SaharaWarning
}
