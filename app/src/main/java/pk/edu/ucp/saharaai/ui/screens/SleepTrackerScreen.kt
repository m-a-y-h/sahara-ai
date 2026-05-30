package pk.edu.ucp.saharaai.ui.screens

import androidx.compose.animation.core.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.health.connect.client.PermissionController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.ui.components.GlassAlertDialog
import pk.edu.ucp.saharaai.ui.components.BottomNav
import pk.edu.ucp.saharaai.ui.components.ButtonVariant
import pk.edu.ucp.saharaai.ui.components.SaharaButton
import pk.edu.ucp.saharaai.ui.components.HazeBackButton
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.data.repository.SleepActigraphyRepository
import pk.edu.ucp.saharaai.data.repository.SLEEP_SOURCE_ACTIGRAPHY
import pk.edu.ucp.saharaai.data.repository.SLEEP_SOURCE_DEFAULT
import pk.edu.ucp.saharaai.data.repository.SLEEP_SOURCE_SIX_MONTH_AVERAGE
import pk.edu.ucp.saharaai.viewmodels.SleepLog
import pk.edu.ucp.saharaai.viewmodels.SleepTrackerViewModel
import pk.edu.ucp.saharaai.viewmodels.HealthSleepImportState
import pk.edu.ucp.saharaai.viewmodels.SLEEP_SOURCE_HEALTH_CONNECT
import pk.edu.ucp.saharaai.util.PermissionCopy
import pk.edu.ucp.saharaai.util.rememberAppPermissionsRequester



private fun hoursToType(h: Float)    = when { h >= 8f -> "excellent"; h >= 7f -> "good"; h >= 6f -> "okay"; else -> "poor" }
private fun hoursToLabelEn(h: Float) = when { h >= 8f -> "Excellent"; h >= 7f -> "Good"; h >= 6f -> "Okay"; else -> "Poor" }
private fun hoursToLabelUr(h: Float) = when { h >= 8f -> "Behtareen"; h >= 7f -> "Acha"; h >= 6f -> "Theek"; else -> "Kharab" }

@Composable
private fun qualityColor(type: String): Color = when (type) {
    "excellent" -> SaharaStrongGreen
    "good"      -> SaharaSky
    "okay"      -> SaharaWarning
    "poor"      -> SaharaCoral
    else        -> MaterialTheme.colorScheme.onSurfaceVariant
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTrackerScreen(
    navController: NavController,
    onNavigateBack: () -> Unit = { navController.popBackStack() },
    isEnglish: Boolean = false,
    viewModel: SleepTrackerViewModel = viewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.initCurrentUser()
        viewModel.checkHealthConnect(context)
        viewModel.checkAutomaticTracking(context)
    }

    val isDark    = isSystemInDarkTheme()
    val hazeState = remember { HazeState() }

    
    val logs      by viewModel.logs.collectAsState()
    val sleepGoal by viewModel.sleepGoal.collectAsState()
    val isSaving  by viewModel.isSaving.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val healthImportState by viewModel.healthImportState.collectAsState()
    val automaticTrackingEnabled by viewModel.automaticTrackingEnabled.collectAsState()
    val error     by viewModel.error.collectAsState()
    val message   by viewModel.message.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        if (!error.isNullOrBlank()) { snackbarHostState.showSnackbar(error!!); viewModel.clearError() }
    }
    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) { snackbarHostState.showSnackbar(message!!); viewModel.clearMessage() }
    }
    val healthPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        viewModel.onHealthPermissionsResult(context, granted)
    }
    val automaticPermissionRequester = rememberAppPermissionsRequester(
        permissions = SleepActigraphyRepository.requiredPermissions(),
        isEnglish = isEnglish,
        copy = PermissionCopy(
            deniedEn = "Automatic sleep estimates need activity recognition and notification permission.",
            deniedUr = "Automatic neend estimate ke liye activity recognition aur notification ki ijazat chahiye.",
            settingsEn = "Enable activity recognition and notification permissions in App settings to use automatic sleep estimates.",
            settingsUr = "Automatic neend estimate ke liye App settings mein activity recognition aur notification permissions dein.",
        ),
        onGranted = {
            viewModel.onAutomaticPermissionsResult(
                context,
                SleepActigraphyRepository.requiredPermissions().associateWith { true },
            )
        },
        onDenied = { viewModel.onAutomaticPermissionsResult(context, emptyMap()) },
    )

    
    
    val weekData: List<SleepLog?> = remember(logs) {
        viewModel.weekDates.map { date -> logs[date] }
    }
    val recorded      = weekData.filterNotNull()
    val avgHours      = if (recorded.isEmpty()) 0f else recorded.sumOf { it.hours.toDouble() }.toFloat() / recorded.size
    val bestLog       = recorded.maxByOrNull { it.hours }
    val worstLog      = recorded.minByOrNull { it.hours }
    val healthyNights = recorded.count { it.hours in 7f..9f }
    val importedNights = recorded.count { it.source == SLEEP_SOURCE_HEALTH_CONNECT }
    val automaticNights = recorded.count { it.automatic }
    val goalProgress  = (avgHours / sleepGoal).coerceIn(0f, 1f)

    
    var selectedDay by remember { mutableStateOf(viewModel.todayIndex) }
    val selLog       = weekData.getOrNull(selectedDay)
    val selectedDate  = viewModel.weekDates[selectedDay]
    val canLogSelectedDate = selectedDate <= viewModel.todayDate
    var editingDate by remember { mutableStateOf<String?>(null) }

    val bedtimeState  = rememberTimePickerState(22, 30, true)
    val waketimeState = rememberTimePickerState(6,  0,  true)

    var bedtimeHour    by remember { mutableStateOf(-1) }
    var bedtimeMinute  by remember { mutableStateOf(0)  }
    var waketimeHour   by remember { mutableStateOf(-1) }
    var waketimeMinute by remember { mutableStateOf(0)  }

    var showBedtimePicker  by remember { mutableStateOf(false) }
    var showWaketimePicker by remember { mutableStateOf(false) }
    var showGoalDialog     by remember { mutableStateOf(false) }

    val calculatedHours: Float? = if (bedtimeHour >= 0 && waketimeHour >= 0) {
        val bedMin  = bedtimeHour  * 60 + bedtimeMinute
        val wakeMin = waketimeHour * 60 + waketimeMinute
        val diff    = if (wakeMin > bedMin) wakeMin - bedMin else (1440 - bedMin) + wakeMin
        diff / 60f
    } else null

    
    val qualityType = selLog?.qualityType ?: "none"
    val insightEn = when (qualityType) {
        "excellent" -> "Great sleep! Consistent early bedtimes are working well."
        "good"      -> "Solid rest. Maintain this schedule for best results."
        "okay"      -> "You can do better. Try sleeping 30 min earlier."
        "poor"      -> "Short sleep detected. Avoid screens 1 hr before bed."
        else        -> "Log tonight's sleep below to see your insight."
    }
    val insightUr = when (qualityType) {
        "excellent" -> "Bohat achhi neend! Waqt par sonay ka silsila jari rakkhein."
        "good"      -> "Theek neend. Is waqt ko barqarar rakhnay ki koshish karein."
        "okay"      -> "Behtar ho sakta hai. Aaj 30 min pehle sonay ki koshish karein."
        "poor"      -> "Neend kam thi. Sonay se 1 ghanta pehle screen band karein."
        else        -> "Aaj raat ka record neeche darj karein."
    }

    
    val bgGradient = if (isDark)
        listOf(SaharaLavender.copy(.22f), MaterialTheme.colorScheme.background.copy(.6f), MaterialTheme.colorScheme.background)
    else
        listOf(SaharaLavender.copy(.28f), SaharaSkyLight.copy(.12f), MaterialTheme.colorScheme.background.copy(.2f))

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().hazeSource(hazeState).background(Brush.verticalGradient(bgGradient)))
        Scaffold(
            bottomBar    = { BottomNav(navController, hazeState) },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent
        ) { pad ->
            Column(
                Modifier.fillMaxSize().padding(pad)
                    .padding(horizontal = 24.dp).verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(24.dp))

                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HazeBackButton(onClick = onNavigateBack, hazeState = hazeState, tint = SaharaLavender)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            if (isEnglish) "Sleep Tracker" else "Neend ka Record",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold, color = SaharaLavender
                        )
                        Text(
                            if (isEnglish) "This week · ${viewModel.weekDates.first()} to ${viewModel.weekDates.last()}"
                            else           "Is hafta · ${viewModel.weekDates.first()} se ${viewModel.weekDates.last()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        if (isEnglish) "Average" else "Average",
                        if (recorded.isEmpty()) "—" else "%.1f".format(avgHours) + "h",
                        SaharaSky, hazeState, Modifier.weight(1f)
                    )
                    StatTile(
                        if (isEnglish) "Best" else "Behtareen",
                        bestLog?.let { "%.1f".format(it.hours) + "h" } ?: "—",
                        SaharaStrongGreen, hazeState, Modifier.weight(1f)
                    )
                    StatTile(
                        if (isEnglish) "Worst" else "Kamtareen",
                        worstLog?.let { "%.1f".format(it.hours) + "h" } ?: "—",
                        SaharaCoral, hazeState, Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(14.dp))

                
                GlassCard(hazeState, SaharaLavender.copy(.25f)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column {
                            Text(if (isEnglish) "Sleep Goal" else "Neend ka Hadaf", style = MaterialTheme.typography.bodySmall)
                            Text("${sleepGoal.toInt()}h", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = SaharaLavender)
                        }
                        TextButton({ showGoalDialog = true }) { Text("Edit", color = SaharaLavender) }
                    }
                    LinearProgressIndicator(
                        progress = { goalProgress },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                        color = SaharaLavender
                    )
                    if (recorded.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (isEnglish)
                                "${"%.0f".format(goalProgress * 100)}% of goal · ${recorded.size}/7 days logged this week"
                            else
                                "${"%.0f".format(goalProgress * 100)}% goal · ${recorded.size}/7 din logged is hafta",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                GlassCard(hazeState, SaharaLavender.copy(.25f)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isEnglish) "Automatic Sleep Estimate" else "Automatic Neend Estimate",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (isEnglish)
                                    "Uses phone motion while a persistent notification is visible."
                                else
                                    "Persistent notification ke sath phone motion use hoti hai.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = {
                                if (automaticTrackingEnabled) {
                                    viewModel.disableAutomaticTracking(context)
                                } else {
                                    viewModel.enableAutomaticTracking(context) { permissions ->
                                        automaticPermissionRequester.request()
                                    }
                                }
                            }
                        ) {
                            Text(
                                if (automaticTrackingEnabled) {
                                    if (isEnglish) "Stop" else "Band"
                                } else {
                                    if (isEnglish) "Enable" else "On"
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (automaticTrackingEnabled) {
                            if (isEnglish)
                                "Active. Low-confidence nights use your measured average, or 6h when no history exists."
                            else
                                "Active. Kam confidence par pichla average, ya history na ho to 6h use hotay hain."
                        } else {
                            if (isEnglish)
                                "Off. Enable only if you agree to continuous motion summaries while notified."
                            else
                                "Band. Sirf razamandi par motion summaries enable karein."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(14.dp))

                GlassCard(hazeState, SaharaSky.copy(.25f)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isEnglish) "Recorded Sleep Import" else "Recorded Neend Import",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (isEnglish)
                                    "Import durations recorded in Health Connect or your wearable app."
                                else
                                    "Health Connect ya wearable mein recorded duration import karein.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = {
                                viewModel.importFromHealthConnect(context) { permissions ->
                                    healthPermissionLauncher.launch(permissions)
                                }
                            },
                            enabled = !isImporting
                        ) {
                            Text(if (isImporting) "..." else if (isEnglish) "Import" else "Import")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val healthStatus = when (healthImportState) {
                        HealthSleepImportState.READY ->
                            if (isEnglish) "Sleep permission granted. Import runs only when you tap Import."
                            else "Sleep ijazat mil gayi hai. Import sirf button dabane par hota hai."
                        HealthSleepImportState.PERMISSION_REQUIRED ->
                            if (isEnglish) "Sleep permission is required for import; manual logging remains available."
                            else "Import ke liye sleep ijazat chahiye; manual record mojood hai."
                        HealthSleepImportState.UPDATE_REQUIRED ->
                            if (isEnglish) "Health Connect must be installed or updated on this phone."
                            else "Is phone par Health Connect install ya update karein."
                        HealthSleepImportState.UNAVAILABLE ->
                            if (isEnglish) "Health Connect is not available; use manual sleep logging."
                            else "Health Connect mojood nahi; manual sleep record istemal karein."
                        HealthSleepImportState.CHECKING ->
                            if (isEnglish) "Checking Health Connect availability..."
                            else "Health Connect check ho raha hai..."
                    }
                    Text(
                        healthStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(14.dp))

                
                GlassCard(hazeState, SaharaLavender.copy(.2f)) {
                    Text(
                        if (isEnglish) "Weekly Overview" else "Haftawar Jaiza",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(30.dp))
                    Row(
                        Modifier.fillMaxWidth().height(150.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        weekData.forEachIndexed { index, log ->
                            val hasRec   = log != null
                            val isSel    = selectedDay == index
                            val isToday  = index == viewModel.todayIndex
                            val barColor = qualityColor(log?.qualityType ?: "none")
                            val barHeight by animateDpAsState(
                                targetValue = if (hasRec) (log.hours * 12).dp.coerceAtMost(140.dp) else 4.dp,
                                label = "bar$index"
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f).clickable { selectedDay = index }
                            ) {
                                
                                if (isSel && hasRec) {
                                    Surface(color = barColor, shape = RoundedCornerShape(4.dp)) {
                                        Text("%.1f".format(log.hours), fontSize = 8.sp,
                                            color = Color.White, modifier = Modifier.padding(2.dp))
                                    }
                                } else {
                                    Spacer(Modifier.height(14.dp))
                                }

                                Box(
                                    Modifier.width(15.dp).height(barHeight)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            when {
                                                !hasRec  -> MaterialTheme.colorScheme.onSurface.copy(.1f)
                                                isSel    -> barColor
                                                else     -> barColor.copy(.35f)
                                            }
                                        )
                                )

                                
                                Text(
                                    if (isEnglish) viewModel.dayLabelsEn[index]
                                    else           viewModel.dayLabelsUr[index],
                                    fontSize  = 9.sp,
                                    fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal,
                                    color     = if (isToday) SaharaLavender
                                               else MaterialTheme.colorScheme.onSurface
                                )

                                
                                if (isToday) {
                                    Box(Modifier.size(4.dp).background(SaharaLavender, CircleShape))
                                }
                            }
                        }
                    }
                    if (recorded.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (isEnglish) "No sleep logged this week yet."
                            else           "Is hafta abhi koi neend record nahi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                GlassCard(hazeState, SaharaSky.copy(.25f)) {
                    Text(
                        if (isEnglish) "Seven-night Analysis" else "Saat Raaton ka Jaiza",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            recorded.size < 7 -> if (isEnglish)
                                "${recorded.size}/7 nights recorded. Record all seven nights before a weekly interpretation."
                            else
                                "${recorded.size}/7 raatein recorded. Haftawar jaizay ke liye saat raatein mukammal karein."
                            avgHours < 7f -> if (isEnglish)
                                "Average sleep is below the 7-hour review threshold this week."
                            else
                                "Is haftay average neend 7 ghanton se kam hai."
                            avgHours > 9f -> if (isEnglish)
                                "Average sleep is above the 9-hour review threshold this week."
                            else
                                "Is haftay average neend 9 ghanton se zyada hai."
                            else -> if (isEnglish)
                                "Average sleep is within the 7 to 9 hour review range this week."
                            else
                                "Is haftay average neend 7 se 9 ghanton ke range mein hai."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (isEnglish)
                            "$healthyNights healthy-range · $importedNights imported · $automaticNights automatic"
                        else
                            "$healthyNights munasib · $importedNights imported · $automaticNights automatic",
                        style = MaterialTheme.typography.labelSmall,
                        color = SaharaSky
                    )
                    Text(
                        if (isEnglish) "This is a wellness summary, not a diagnosis."
                        else "Yeh wellness summary hai, diagnosis nahi.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(14.dp))

                
                val detailColor = qualityColor(selLog?.qualityType ?: "none")
                GlassCard(hazeState, detailColor.copy(.4f)) {
                    val dayLabel = if (selectedDay == viewModel.todayIndex)
                        (if (isEnglish) "Today" else "Aaj")
                    else
                        viewModel.dateLabel(selectedDay)

                    Text(
                        if (isEnglish) "$dayLabel's Sleep" else "$dayLabel ki Neend",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                    )

                    if (selLog != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "%.1f hours".format(selLog.hours),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (selLog.bedtime.isNotBlank() && selLog.waketime.isNotBlank()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MiniTimeBadge(Icons.Default.Bedtime,  selLog.bedtime,  SaharaLavender)
                                MiniTimeBadge(Icons.Default.WbSunny, selLog.waketime, SaharaSky)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = detailColor.copy(.15f)
                        ) {
                            Text(
                                if (isEnglish) hoursToLabelEn(selLog.hours) else hoursToLabelUr(selLog.hours),
                                style = MaterialTheme.typography.labelSmall,
                                color = detailColor, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            when (selLog.source) {
                                SLEEP_SOURCE_HEALTH_CONNECT -> "Source: Health Connect import (${selLog.timeZoneId})"
                                SLEEP_SOURCE_ACTIGRAPHY -> "Source: phone-motion estimate (${selLog.timeZoneId})"
                                SLEEP_SOURCE_SIX_MONTH_AVERAGE -> "Source: measured sleep average fallback (${selLog.timeZoneId})"
                                SLEEP_SOURCE_DEFAULT -> "Source: first-week 6h fallback (${selLog.timeZoneId})"
                                else -> "Source: manually logged (${selLog.timeZoneId})"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (selLog.automatic && selLog.sourceReason.isNotBlank()) {
                            Text(
                                selLog.sourceReason,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (isEnglish) "No sleep recorded for this day."
                            else           "Is din ka koi record nahi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(if (isEnglish) insightEn else insightUr, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(24.dp))

                
                if (canLogSelectedDate && (selLog == null || editingDate == selectedDate)) {
                    Text(
                        if (isEnglish) "Log Sleep for ${viewModel.dateLabel(selectedDay)}"
                        else "${viewModel.dateLabel(selectedDay)} ki Neend Record Karein",
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    GlassCard(hazeState, SaharaLavender.copy(.2f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TimePickerButton(
                                label   = if (isEnglish) "Bedtime" else "Sonay ka Waqt",
                                time    = if (bedtimeHour >= 0) "%02d:%02d".format(bedtimeHour, bedtimeMinute) else null,
                                icon    = Icons.Default.Bedtime,
                                color   = SaharaLavender,
                                isDark  = isDark,
                                onClick = { showBedtimePicker = true },
                                modifier = Modifier.weight(1f)
                            )
                            TimePickerButton(
                                label   = if (isEnglish) "Wake Time" else "Uthne ka Waqt",
                                time    = if (waketimeHour >= 0) "%02d:%02d".format(waketimeHour, waketimeMinute) else null,
                                icon    = Icons.Default.WbSunny,
                                color   = SaharaSky,
                                isDark  = isDark,
                                onClick = { showWaketimePicker = true },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        
                        calculatedHours?.let { h ->
                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(qualityColor(hoursToType(h)).copy(.12f))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Bedtime, null,
                                    tint = qualityColor(hoursToType(h)), modifier = Modifier.size(18.dp))
                                Text(
                                    if (isEnglish)
                                        "%.1fh · ${if (isEnglish) hoursToLabelEn(h) else hoursToLabelUr(h)}"
                                            .format(h)
                                    else
                                        "%.1f ghante · ${hoursToLabelUr(h)}".format(h),
                                    fontWeight = FontWeight.Bold,
                                    color = qualityColor(hoursToType(h))
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        SaharaButton(
                            text = when {
                                isSaving        -> if (isEnglish) "Saving…" else "Save ho raha hai…"
                                calculatedHours != null -> if (isEnglish) "Save Sleep Log" else "Record Save Karein"
                                else            -> if (isEnglish) "Set Times First" else "Pehle Waqt Chunein"
                            },
                            onClick = {
                                calculatedHours?.let { h ->
                                    viewModel.logSleep(
                                        selectedDate,
                                        "%02d:%02d".format(bedtimeHour, bedtimeMinute),
                                        "%02d:%02d".format(waketimeHour, waketimeMinute),
                                        h
                                    )
                                    editingDate = null
                                }
                            },
                            variant   = if (calculatedHours != null && !isSaving) ButtonVariant.GRADIENT else ButtonVariant.OUTLINE,
                            isFullWidth = true,
                            isEnglish = isEnglish
                        )
                        if (selLog != null) {
                            TextButton(
                                onClick = { editingDate = null },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text(if (isEnglish) "Cancel edit" else "Edit khatam karein")
                            }
                        }
                    }
                } else if (selLog != null) {
                    GlassCard(hazeState, SaharaStrongGreen.copy(.3f)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = SaharaStrongGreen)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (isEnglish) "Sleep record saved"
                                    else           "Neend ka record mehfooz hai",
                                    fontWeight = FontWeight.SemiBold
                                )
                                selLog.let { log ->
                                    Text(
                                        if (log.bedtime.isNotBlank())
                                            "%.1fh  ·  ${log.bedtime} to ${log.waketime}".format(log.hours)
                                        else
                                            "%.1fh  ·  ${when (log.source) {
                                                SLEEP_SOURCE_ACTIGRAPHY -> "phone-motion estimate"
                                                SLEEP_SOURCE_SIX_MONTH_AVERAGE -> "past-average fallback"
                                                SLEEP_SOURCE_DEFAULT -> "6h fallback"
                                                else -> "Health Connect import"
                                            }}".format(log.hours),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SaharaStrongGreen
                                    )
                                }
                            }
                            if (canLogSelectedDate) {
                                TextButton(onClick = { editingDate = selectedDate }) {
                                    Text(if (isEnglish) "Edit" else "Edit")
                                }
                            }
                        }
                    }
                } else {
                    GlassCard(hazeState, MaterialTheme.colorScheme.onSurface.copy(.1f)) {
                        Text(
                            if (isEnglish) "Sleep can be logged after this date."
                            else "Is din ke baad neend record ki ja sakti hai.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }

    if (showBedtimePicker) {
        GlassAlertDialog(
            hazeState = hazeState,
            onDismissRequest = { showBedtimePicker = false },
            title = { Text(if (isEnglish) "Select Bedtime" else "Sonay ka Waqt Chunein") },
            text  = { Box(Modifier.fillMaxWidth(), Alignment.Center) { TimePicker(bedtimeState) } },
            confirmButton = {
                TextButton({ bedtimeHour = bedtimeState.hour; bedtimeMinute = bedtimeState.minute; showBedtimePicker = false }) {
                    Text("OK", color = SaharaLavender)
                }
            },
            dismissButton = { TextButton({ showBedtimePicker = false }) { Text(if (isEnglish) "Cancel" else "Wapas") } }
        )
    }
    if (showWaketimePicker) {
        GlassAlertDialog(
            hazeState = hazeState,
            onDismissRequest = { showWaketimePicker = false },
            title = { Text(if (isEnglish) "Select Wake Time" else "Uthnay ka Waqt Chunein") },
            text  = { Box(Modifier.fillMaxWidth(), Alignment.Center) { TimePicker(waketimeState) } },
            confirmButton = {
                TextButton({ waketimeHour = waketimeState.hour; waketimeMinute = waketimeState.minute; showWaketimePicker = false }) {
                    Text("OK", color = SaharaSky)
                }
            },
            dismissButton = { TextButton({ showWaketimePicker = false }) { Text(if (isEnglish) "Cancel" else "Wapas") } }
        )
    }
    if (showGoalDialog) {
        var tempGoal by remember { mutableStateOf(sleepGoal) }
        GlassAlertDialog(
            hazeState = hazeState,
            onDismissRequest = { showGoalDialog = false },
            title = { Text(if (isEnglish) "Set Sleep Goal" else "Neend ka Hadaf") },
            text = {
                Column {
                    Text("${tempGoal.toInt()}h", style = MaterialTheme.typography.headlineMedium,
                        color = SaharaLavender, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Slider(value = tempGoal, onValueChange = { tempGoal = it }, valueRange = 6f..10f, steps = 3)
                }
            },
            confirmButton = {
                TextButton({ viewModel.updateGoal(tempGoal); showGoalDialog = false }) { Text("Save", color = SaharaLavender) }
            }
        )
    }
}



@Composable
private fun GlassCard(hazeState: HazeState, borderColor: Color, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .hazeEffect(hazeState) {
                blurEffect { blurRadius = 25.dp; colorEffects = listOf(HazeColorEffect.tint(Color.White.copy(.08f))) }
            }
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) { Column(content = content) }
}

@Composable
private fun StatTile(label: String, value: String, color: Color, hazeState: HazeState, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(14.dp))
            .hazeEffect(hazeState) {
                blurEffect { blurRadius = 25.dp; colorEffects = listOf(HazeColorEffect.tint(Color.White.copy(.08f))) }
            }
            .border(1.dp, color.copy(.25f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, fontSize = 10.sp)
        }
    }
}

@Composable
private fun TimePickerButton(
    label: String, time: String?, icon: ImageVector,
    color: Color, isDark: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(if (time != null) color.copy(.1f) else Color.Gray.copy(.1f))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(label, fontSize = 10.sp)
                Text(time ?: "Set", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MiniTimeBadge(icon: ImageVector, time: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(.12f)) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(10.dp))
            Spacer(Modifier.width(3.dp))
            Text(time, fontSize = 10.sp, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}
