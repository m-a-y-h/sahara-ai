package pk.edu.ucp.saharaai.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt
import pk.edu.ucp.saharaai.data.model.FlaggedTrack
import pk.edu.ucp.saharaai.data.model.ListeningSeverity
import pk.edu.ucp.saharaai.data.model.WeeklyListeningReport
import pk.edu.ucp.saharaai.ui.components.BottomNav
import pk.edu.ucp.saharaai.ui.components.CardVariant
import pk.edu.ucp.saharaai.ui.components.GlassAlertDialog
import pk.edu.ucp.saharaai.ui.components.HazeBackButton
import pk.edu.ucp.saharaai.ui.components.SaharaCard
import pk.edu.ucp.saharaai.ui.theme.SaharaCoral
import pk.edu.ucp.saharaai.ui.theme.SaharaPeach
import pk.edu.ucp.saharaai.ui.theme.SaharaSky
import pk.edu.ucp.saharaai.ui.theme.SaharaStrongGreen
import pk.edu.ucp.saharaai.ui.theme.SaharaWarning
import pk.edu.ucp.saharaai.viewmodels.WeeklyReportViewModel

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

    // The user's join date — used to clamp a report's shown start so it never
    // begins before they registered.
    var registrationIso by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isNotBlank()) {
            val createdAt = pk.edu.ucp.saharaai.data.remote.RealtimeDBService.getUserCreatedAt(uid)
            if (createdAt > 0L) {
                registrationIso = java.time.Instant.ofEpochMilli(createdAt)
                    .atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
            }
        }
    }
    var pendingDeletion by remember { mutableStateOf<WeeklyListeningReport?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }

    val hazeState = remember { HazeState() }
    val isDark = isSystemInDarkTheme()
    val bgGradient = if (isDark) {
        listOf(
            SaharaStrongGreen.copy(alpha = 0.20f),
            MaterialTheme.colorScheme.background.copy(alpha = 0.64f),
            MaterialTheme.colorScheme.background,
        )
    } else {
        listOf(
            SaharaStrongGreen.copy(alpha = 0.24f),
            SaharaPeach.copy(alpha = 0.10f),
            MaterialTheme.colorScheme.background.copy(alpha = 0.20f),
        )
    }
    val blob1Color = SaharaStrongGreen.copy(alpha = if (isDark) 0.24f else 0.15f)
    val blob2Color = SaharaSky.copy(alpha = if (isDark) 0.20f else 0.18f)

    Box(Modifier.fillMaxSize()) {
        ScreenBackdrop(
            hazeState = hazeState,
            bgGradient = bgGradient,
            blob1Color = blob1Color,
            secondaryBlob = BackdropBlobSpec(
                size = 420.dp,
                offsetX = 110.dp,
                offsetY = 60.dp,
                color = blob2Color,
                alignment = Alignment.BottomEnd,
            ),
        )

        Scaffold(
            bottomBar = { BottomNav(navController = navController, hazeState = hazeState) },
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    WeeklyReportHeader(
                        hazeState = hazeState,
                        isEnglish = isEnglish,
                        onBack = { navController.popBackStack() },
                    )
                }

                when {
                    isLoading && reports.isEmpty() -> {
                        item {
                            WeeklyStateCard(
                                hazeState = hazeState,
                                icon = null,
                                title = if (isEnglish) "Loading reports" else "Reports aa rahi hain",
                                body = if (isEnglish) "Fetching your latest listening summaries." else "Aapke latest summaries laaye ja rahe hain.",
                                accent = SaharaStrongGreen,
                                loading = true,
                            )
                        }
                    }
                    error != null && reports.isEmpty() -> {
                        item {
                            WeeklyStateCard(
                                hazeState = hazeState,
                                icon = Icons.Filled.Warning,
                                title = if (isEnglish) "Reports unavailable" else "Reports nahi mil rahi",
                                body = error.orEmpty(),
                                accent = SaharaCoral,
                            )
                        }
                    }
                    reports.isEmpty() -> {
                        item {
                            WeeklyStateCard(
                                hazeState = hazeState,
                                icon = Icons.Filled.Headphones,
                                title = if (isEnglish) "No weekly reports yet" else "Abhi koi report nahi",
                                body = if (isEnglish) {
                                    "Weekly summaries will appear here after the next listening check."
                                } else {
                                    "Agli jaanch ke baad hafta-wari reports yahan ayengi."
                                },
                                accent = SaharaStrongGreen,
                            )
                        }
                    }
                    else -> {
                        val latest = reports.first()
                        item {
                            LatestReportHero(
                                report = latest,
                                registrationIso = registrationIso,
                                reportCount = reports.size,
                                totalFlagged = reports.sumOf { it.flaggedCount },
                                totalListened = reports.sumOf { it.totalTracks },
                                hazeState = hazeState,
                                isEnglish = isEnglish,
                            )
                        }
                        item {
                            ListeningBreakdownCard(
                                report = latest,
                                hazeState = hazeState,
                                isEnglish = isEnglish,
                            )
                        }
                        item {
                            SectionTitle(
                                title = if (isEnglish) "Saved reports" else "Purani reports",
                                subtitle = if (isEnglish) {
                                    "${reports.size} week${if (reports.size == 1) "" else "s"} on record"
                                } else {
                                    "${reports.size} haftay record mein"
                                },
                            )
                        }
                        items(reports, key = { it.weekStartIso }) { report ->
                            WeeklyReportCard(
                                report = report,
                                registrationIso = registrationIso,
                                hazeState = hazeState,
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

    pendingDeletion?.let { report ->
        GlassAlertDialog(
            hazeState = hazeState,
            onDismissRequest = { pendingDeletion = null },
            title = {
                Text(
                    if (isEnglish) "Delete this report?" else "Ye report delete karein?",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    if (isEnglish) {
                        "${formatWeekRange(report, registrationIso)} has ${report.flaggedCount} flagged track(s). This can't be undone."
                    } else {
                        "${formatWeekRange(report, registrationIso)} mein ${report.flaggedCount} nishan-zada gaanay hain. Ye wapas nahi aa sakta."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteReport(report.weekStartIso)
                    pendingDeletion = null
                }) {
                    Text(if (isEnglish) "Delete" else "Delete", color = SaharaCoral)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text(if (isEnglish) "Cancel" else "Cancel")
                }
            },
        )
    }
}

@Composable
private fun WeeklyReportHeader(
    hazeState: HazeState,
    isEnglish: Boolean,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HazeBackButton(onClick = onBack, hazeState = hazeState)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (isEnglish) "Weekly Reports" else "Hafta-wari Reports",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = SaharaStrongGreen,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (isEnglish) "Listening pattern digests" else "Sunne ke pattern ka khulasa",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LatestReportHero(
    report: WeeklyListeningReport,
    registrationIso: String = "",
    reportCount: Int,
    totalFlagged: Int,
    totalListened: Int,
    hazeState: HazeState,
    isEnglish: Boolean,
) {
    val accent = colorForSeverity(report.severity)
    SaharaCard(
        variant = CardVariant.GLASS,
        hazeState = hazeState,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(accent.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = severityIcon(report.severity),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (isEnglish) "Latest week" else "Sab se naya hafta",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = severityTitle(report.severity, isEnglish),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SeverityChip(report.severity, isEnglish)
            }

            Text(
                text = formatWeekRange(report, registrationIso),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isEnglish) "Aggregate score" else "Majmuei score",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${scorePercent(report.aggregateScore)}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                }
                LinearProgressIndicator(
                    progress = { scorePercent(report.aggregateScore) / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = accent,
                    trackColor = accent.copy(alpha = 0.14f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HeroMetric(
                    label = if (isEnglish) "Flagged" else "Nishan-zada",
                    value = report.flaggedCount.toString(),
                    accent = SaharaCoral,
                    modifier = Modifier.weight(1f),
                )
                HeroMetric(
                    label = if (isEnglish) "Listened" else "Sune gaye",
                    value = report.totalTracks.toString(),
                    accent = SaharaSky,
                    modifier = Modifier.weight(1f),
                )
                HeroMetric(
                    label = if (isEnglish) "Weeks" else "Haftay",
                    value = reportCount.toString(),
                    accent = SaharaStrongGreen,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = if (isEnglish) {
                    "$totalFlagged flagged across $totalListened listened tracks in saved reports."
                } else {
                    "Saved reports mein $totalFlagged nishan-zada aur $totalListened sune gaye gaanay."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeroMetric(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(76.dp)
            .background(accent.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .border(1.dp, accent.copy(alpha = 0.20f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = accent,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ListeningBreakdownCard(
    report: WeeklyListeningReport,
    hazeState: HazeState,
    isEnglish: Boolean,
) {
    SaharaCard(
        variant = CardVariant.GLASS,
        hazeState = hazeState,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTitle(
                title = if (isEnglish) "Latest breakdown" else "Naya hisaab",
                subtitle = if (isEnglish) "Severity and genre mix" else "Shiddat aur qism ka hisaab",
            )
            SeverityBreakdown(report, isEnglish)
            if (report.topGenres.isNotEmpty()) {
                TopGenres(report.topGenres, isEnglish)
            }
        }
    }
}

@Composable
private fun SeverityBreakdown(report: WeeklyListeningReport, isEnglish: Boolean) {
    val total = report.severityBreakdown.values.sum().coerceAtLeast(1)
    val severities = listOf(
        ListeningSeverity.HIGH,
        ListeningSeverity.MEDIUM,
        ListeningSeverity.LOW,
        ListeningSeverity.NONE,
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        severities.forEach { severity ->
            val count = report.severityBreakdown[severity.wire] ?: 0
            BreakdownBar(
                label = severityLabel(severity, isEnglish),
                count = count,
                fraction = count.toFloat() / total.toFloat(),
                accent = colorForSeverity(severity),
            )
        }
    }
}

@Composable
private fun BreakdownBar(
    label: String,
    count: Int,
    fraction: Float,
    accent: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(6.dp)),
            color = accent,
            trackColor = accent.copy(alpha = 0.12f),
        )
    }
}

@Composable
private fun TopGenres(topGenres: List<Pair<String, Int>>, isEnglish: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (isEnglish) "Top genres" else "Zyada suni qisamain",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        topGenres.take(4).chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { (genre, count) ->
                    GenrePill(
                        genre = genre,
                        count = count,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GenrePill(
    genre: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(42.dp)
            .background(SaharaSky.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .border(1.dp, SaharaSky.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = SaharaSky, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = genre,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeeklyReportCard(
    report: WeeklyListeningReport,
    registrationIso: String = "",
    hazeState: HazeState,
    isEnglish: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = colorForSeverity(report.severity)
    SaharaCard(
        variant = CardVariant.GLASS,
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(accent.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = severityIcon(report.severity),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = formatWeekRange(report, registrationIso),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = summaryText(report, isEnglish),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = SaharaCoral)
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SeverityChip(report.severity, isEnglish)
                Text(
                    text = "${scorePercent(report.aggregateScore)}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                LinearProgressIndicator(
                    progress = { scorePercent(report.aggregateScore) / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = accent,
                    trackColor = accent.copy(alpha = 0.14f),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (report.flaggedTracks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SaharaStrongGreen.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                                .border(1.dp, SaharaStrongGreen.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                        ) {
                            Text(
                                text = if (isEnglish) "No flagged tracks this week." else "Is hafte koi gaana nishan-zada nahi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        report.flaggedTracks.take(12).forEach { track ->
                            FlaggedTrackRow(track = track)
                        }
                        if (report.flaggedTracks.size > 12) {
                            Text(
                                text = if (isEnglish) {
                                    "+${report.flaggedTracks.size - 12} more flagged track(s)"
                                } else {
                                    "+${report.flaggedTracks.size - 12} aur nishan-zada gaanay"
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
}

@Composable
private fun FlaggedTrackRow(track: FlaggedTrack) {
    val accent = colorForSeverity(track.severity)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .border(1.dp, accent.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(accent, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = track.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${scorePercent(track.score)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (track.genres.isNotEmpty()) {
                Text(
                    text = track.genres.take(3).joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            track.flagReasons.take(2).forEach { reason ->
                Text(
                    text = "- $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WeeklyStateCard(
    hazeState: HazeState,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    title: String,
    body: String,
    accent: Color,
    loading: Boolean = false,
) {
    SaharaCard(
        variant = CardVariant.GLASS,
        hazeState = hazeState,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(color = accent, strokeWidth = 3.dp, modifier = Modifier.size(42.dp))
            } else if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(accent.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(34.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SeverityChip(severity: ListeningSeverity, isEnglish: Boolean) {
    val accent = colorForSeverity(severity)
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.36f)),
    ) {
        Text(
            text = severityLabel(severity, isEnglish),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
    }
}

private fun formatWeekRange(report: WeeklyListeningReport, registrationIso: String = ""): String {
    val rawStart = datePart(report.weekStartIso)
    // A fresh user's calendar week (e.g. Sun 2026-05-31) starts before they
    // registered, which looks wrong. Clamp the shown start to the later of the
    // week start and the join date (ISO dates compare lexically).
    val start = if (registrationIso.isNotBlank() && registrationIso > rawStart) registrationIso else rawStart
    val end = datePart(report.weekEndIso)
    return if (start == end || end.isBlank()) start else "$start - $end"
}

private fun datePart(value: String): String = value.take(10).ifBlank { "Unknown" }

private fun scorePercent(score: Double): Int = (score.coerceIn(0.0, 1.0) * 100.0).roundToInt()

private fun summaryText(report: WeeklyListeningReport, isEnglish: Boolean): String {
    return if (isEnglish) {
        "${report.flaggedCount} flagged / ${report.totalTracks} listened"
    } else {
        "${report.flaggedCount} nishan-zada / ${report.totalTracks} sune gaye"
    }
}

private fun severityLabel(severity: ListeningSeverity, isEnglish: Boolean): String = when (severity) {
    ListeningSeverity.HIGH -> if (isEnglish) "High" else "Zyada"
    ListeningSeverity.MEDIUM -> if (isEnglish) "Medium" else "Darmiyani"
    ListeningSeverity.LOW -> if (isEnglish) "Low" else "Kam"
    ListeningSeverity.NONE -> if (isEnglish) "Clear" else "Saaf"
    ListeningSeverity.UNKNOWN -> if (isEnglish) "Unknown" else "Na-maloom"
}

private fun severityTitle(severity: ListeningSeverity, isEnglish: Boolean): String = when (severity) {
    ListeningSeverity.HIGH -> if (isEnglish) "High-risk listening week" else "Zyada risk wala hafta"
    ListeningSeverity.MEDIUM -> if (isEnglish) "Elevated pattern" else "Pattern zyada hai"
    ListeningSeverity.LOW -> if (isEnglish) "Some flagged tracks" else "Kuch gaanay nishan-zada"
    ListeningSeverity.NONE -> if (isEnglish) "Clean week" else "Saaf hafta"
    ListeningSeverity.UNKNOWN -> if (isEnglish) "Weekly summary" else "Haftay ka khulasa"
}

private fun severityIcon(severity: ListeningSeverity) = when (severity) {
    ListeningSeverity.HIGH -> Icons.Filled.Warning
    ListeningSeverity.MEDIUM -> Icons.Filled.Insights
    ListeningSeverity.LOW -> Icons.Filled.GraphicEq
    ListeningSeverity.NONE -> Icons.Filled.CheckCircle
    ListeningSeverity.UNKNOWN -> Icons.Filled.MusicNote
}

@Composable
private fun colorForSeverity(severity: ListeningSeverity) = when (severity) {
    ListeningSeverity.HIGH -> SaharaCoral
    ListeningSeverity.MEDIUM -> SaharaPeach
    ListeningSeverity.LOW -> SaharaSky
    ListeningSeverity.NONE -> SaharaStrongGreen
    ListeningSeverity.UNKNOWN -> SaharaWarning
}
