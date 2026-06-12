package pk.edu.ucp.saharaai.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.ui.components.*
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.viewmodels.ActivityLogViewModel
import pk.edu.ucp.saharaai.viewmodels.WeeklyReport
import java.text.SimpleDateFormat
import java.util.*



@Composable
fun ActivityLogScreen(
    navController: NavController,
    onNavigateBack: () -> Unit,
    isEnglish     : Boolean = false,
    activityLogViewModel: ActivityLogViewModel = viewModel()
) {
    val isDark       = isSystemInDarkTheme()
    val primaryGreen = if (isDark) SaharaStrongGreen else SaharaGreen
    val hazeState    = remember { HazeState() }
    val context      = LocalContext.current

    val bgGradient = if (isDark)
        listOf(SaharaStrongGreen.copy(.2f), MaterialTheme.colorScheme.background.copy(.6f), MaterialTheme.colorScheme.background)
    else
        listOf(SaharaStrongGreen.copy(.25f), SaharaPeach.copy(.1f), MaterialTheme.colorScheme.background.copy(.2f))

    val blobMotion = rememberBackdropBlobMotion()

    
    val weekStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -6)
        }.timeInMillis
    }
    val weekEnd   = remember { System.currentTimeMillis() }

    val weekLabel = remember {
        val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
        val sdfY = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        "${sdf.format(Date(weekStart))} – ${sdfY.format(Date(weekEnd))}"
    }

    val isLoading = activityLogViewModel.isLoading
    val report = activityLogViewModel.report
    LaunchedEffect(weekStart, weekEnd, isEnglish) {
        activityLogViewModel.loadReport(context, weekStart, weekEnd, isEnglish)
    }

    
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().hazeSource(hazeState).background(Brush.verticalGradient(bgGradient))) {
            Box(Modifier.size(350.dp).offset((-80).dp, (-50).dp).primaryBlobMotion(blobMotion)
                .background(Brush.radialGradient(listOf(SaharaStrongGreen.copy(if (isDark) .25f else .15f), Color.Transparent))))
            Box(Modifier.size(400.dp).align(Alignment.BottomEnd).offset(100.dp, 50.dp).secondaryBlobMotion(blobMotion)
                .background(Brush.radialGradient(listOf(SaharaSky.copy(if (isDark) .2f else .18f), Color.Transparent))))
        }

        Scaffold(
            bottomBar = { BottomNav(navController = navController, hazeState = hazeState) },
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { innerPadding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(16.dp))

            
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HazeBackButton(onClick = onNavigateBack, hazeState = hazeState, tint = primaryGreen)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (isEnglish) "Weekly Report" else "Hafta Wari Report",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold, color = primaryGreen)
                        Text(weekLabel, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (report.memberSinceDays > 0) {
                        Surface(shape = RoundedCornerShape(20.dp),
                            color = SaharaStrongGreen.copy(.15f)) {
                            Text("Day ${report.memberSinceDays}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = SaharaStrongGreen,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                if (isLoading) {
                    Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = primaryGreen)
                    }
                } else {

                
                if (report.flags.isNotEmpty()) {
                    SectionTitle(if (isEnglish) "Flags for Review" else "Nazar Sani ke Liye", Icons.Default.Flag, SaharaCoral)
                    SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(4.dp)) {
                            report.flags.forEachIndexed { idx, flag ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(8.dp).clip(CircleShape)
                                        .background(if (flag.startsWith("⚠")) SaharaCoral else SaharaWarning))
                                    Spacer(Modifier.width(12.dp))
                                    Text(flag, style = MaterialTheme.typography.bodySmall,
                                        color = if (flag.startsWith("⚠")) SaharaCoral else SaharaWarning,
                                        modifier = Modifier.weight(1f))
                                }
                                if (idx < report.flags.lastIndex)
                                    HorizontalDivider(Modifier.padding(horizontal = 12.dp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(.06f))
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                
                SectionTitle(if (isEnglish) "Risk Assessment" else "Khatray ka Andaza", Icons.Default.Shield, SaharaLavender)
                RiskAssessmentCard(report = report, hazeState = hazeState, isEnglish = isEnglish)
                Spacer(Modifier.height(20.dp))

                
                SectionTitle(if (isEnglish) "This Week" else "Is Hafte", Icons.Default.CalendarMonth, SaharaStrongGreen)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniStatCard(Icons.Default.TouchApp, SaharaStrongGreen,
                        report.totalSessions.toString(),
                        if (isEnglish) "Sessions" else "Sessions",
                        hazeState, Modifier.weight(1f))
                    MiniStatCard(Icons.Default.Mood, SaharaPeach,
                        report.moodLogCount.toString(),
                        if (isEnglish) "Moods" else "Mood",
                        hazeState, Modifier.weight(1f))
                    MiniStatCard(Icons.Default.Book, SaharaSky,
                        report.journalCount.toString(),
                        if (isEnglish) "Journals" else "Journal",
                        hazeState, Modifier.weight(1f))
                    MiniStatCard(Icons.Default.

                    Chat, SaharaLavender,
                        report.chatMsgCount.toString(),
                        if (isEnglish) "Messages" else "Msgs",
                        hazeState, Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))

                
                ActiveDaysStrip(activeDays = report.activeDays, primaryGreen = primaryGreen,
                    hazeState = hazeState, isEnglish = isEnglish)
                Spacer(Modifier.height(20.dp))

                
                if (report.totalSessions > 0) {
                    SectionTitle(if (isEnglish) "Screen Time" else "Screen Time", Icons.Default.PhoneAndroid, SaharaSky)
                    ScreenTimeCard(report = report, hazeState = hazeState, isEnglish = isEnglish)
                    Spacer(Modifier.height(20.dp))
                }

                
                if (report.moodDistribution.isNotEmpty()) {
                    SectionTitle(if (isEnglish) "Mood Breakdown" else "Mood Ka Khulasa", Icons.Default.Mood, SaharaPeach)
                    MoodBreakdownCard(moodMap = report.moodDistribution, moodLogCount = report.moodLogCount,
                        hazeState = hazeState, isEnglish = isEnglish)
                    Spacer(Modifier.height(20.dp))
                }

                
                SectionTitle(if (isEnglish) "Account Health" else "Account Ki Haalat", Icons.Default.VerifiedUser, SaharaStrongGreen)
                AccountHealthCard(report = report, hazeState = hazeState, isEnglish = isEnglish)
                Spacer(Modifier.height(20.dp))

                
                if (report.totalSessions == 0 && report.moodLogCount == 0 && report.journalCount == 0 && report.chatMsgCount == 0) {
                    SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.HourglassEmpty, null, tint = primaryGreen.copy(.4f), modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(if (isEnglish) "Not enough data yet — check back after a week of use."
                                 else "Abhi data kam hai — ek hafte baad check karein.",
                                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                
                SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = SaharaSky.copy(.7f), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isEnglish)
                                "This report is generated from your last 7 days of activity and is used for wellness monitoring only."
                            else
                                "Ye report aakhri 7 din ki activity se banti hai aur sirf wellness monitoring ke liye hai.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}



@Composable
private fun SectionTitle(title: String, icon: ImageVector, color: Color) {
    val isDark = isSystemInDarkTheme()
    Row(Modifier.padding(start = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            color = if (isDark) Color.White.copy(.9f) else Color.Black.copy(.85f))
    }
}

@Composable
private fun RiskAssessmentCard(report: WeeklyReport, hazeState: HazeState, isEnglish: Boolean) {
    val (riskLabel, riskColor, riskDesc) = when {
        report.dast10Score < 0  -> Triple(
            if (isEnglish) "Not Taken" else "Test Nahi Diya",
            Color.Gray,
            if (isEnglish) "No assessment on record." else "Koi assessment record nahi."
        )
        report.dast10Score <= 5  -> Triple(
            if (isEnglish) "Low Risk" else "Kam Khatra",
            SaharaStrongGreen,
            if (isEnglish) "Score ${report.dast10Score}/28 — No significant risk detected."
            else "Score ${report.dast10Score}/28 — Koi bada khatra nahi."
        )
        report.dast10Score <= 10 -> Triple(
            if (isEnglish) "Moderate Risk" else "Darmiyani Khatra",
            SaharaWarning,
            if (isEnglish) "Score ${report.dast10Score}/28 — Some problematic substance use patterns."
            else "Score ${report.dast10Score}/28 — Kuch maddah use patterns hazelous hain."
        )
        else -> Triple(
            if (isEnglish) "High Risk" else "Zyada Khatra",
            SaharaCoral,
            if (isEnglish) "Score ${report.dast10Score}/28 — Significant risk. Professional support recommended."
            else "Score ${report.dast10Score}/28 — Zyada khatra. Professional madad ki zaroorat hai."
        )
    }

    SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(riskColor.copy(.15f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Shield, null, tint = riskColor, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(riskLabel, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = riskColor)
                    Text(riskDesc, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (report.dast10Score >= 0) {
                Spacer(Modifier.height(14.dp))
                
                val progress = (report.dast10Score / 28f).coerceIn(0f, 1f)
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("DAST-10", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${report.dast10Score}/28", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = riskColor)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = riskColor, trackColor = riskColor.copy(.15f)
                    )
                }
                if (report.assessmentTs > 0L) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        (if (isEnglish) "Last taken: " else "Aakhri baar: ") +
                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(report.assessmentTs)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveDaysStrip(activeDays: Set<Int>, primaryGreen: Color, hazeState: HazeState, isEnglish: Boolean) {
    val dayLabels = if (isEnglish) listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")
                   else            listOf("Ahad","Peer","Mangal","Budh","Jumarat","Juma","Hafta")
    
    SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (i in 1..7) {
                val active = i in activeDays
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(32.dp)
                        .background(if (active) primaryGreen else primaryGreen.copy(.1f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        if (active) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(dayLabels[i - 1].take(if (isEnglish) 3 else 4),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) primaryGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(.5f))
                }
            }
        }
    }
}

@Composable
private fun ScreenTimeCard(report: WeeklyReport, hazeState: HazeState, isEnglish: Boolean) {
    val totalH  = report.totalScreenMs / 3_600_000L
    val totalM  = (report.totalScreenMs % 3_600_000L) / 60_000L
    val maxMs   = report.topApps.firstOrNull()?.second ?: 1L

    SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${totalH}h ${totalM}m", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = SaharaSky)
                Spacer(Modifier.width(8.dp))
                Text(if (isEnglish) "total this week" else "is hafte kul",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp))
            }
            if (report.topApps.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(.06f))
                Spacer(Modifier.height(10.dp))
                Text(if (isEnglish) "Top Apps" else "Zyada Istemal",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                report.topApps.forEach { (name, ms) ->
                    val appH = ms / 3_600_000L
                    val appM = (ms % 3_600_000L) / 60_000L
                    val appS = (ms % 60_000L) / 1_000L
                    val timeStr = when {
                        appH > 0  -> "${appH}h ${appM}m"
                        appM > 0  -> "${appM}m"
                        else      -> "${appS}s"
                    }
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name.substringAfterLast(".").ifBlank { name },
                                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text(timeStr, style = MaterialTheme.typography.labelSmall, color = SaharaSky)
                        }
                        Spacer(Modifier.height(3.dp))
                        LinearProgressIndicator(
                            progress = { (ms.toFloat() / maxMs).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                            color = SaharaSky, trackColor = SaharaSky.copy(.12f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodBreakdownCard(moodMap: Map<String, Int>, moodLogCount: Int, hazeState: HazeState, isEnglish: Boolean) {
    val total   = moodMap.values.sum().coerceAtLeast(1)
    val moodColors = mapOf(
        "Happy" to SaharaStrongGreen, "Calm" to SaharaStrongGreen,
        "Hopeful" to SaharaSky, "Sad" to SaharaSky,
        "Anxious" to SaharaWarning, "Angry" to SaharaCoral,
        "Neutral" to Color.Gray
    )

    SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(if (isEnglish) "$moodLogCount mood logs this week" else "Is hafte $moodLogCount mood logs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            moodMap.entries.sortedByDescending { it.value }.forEach { (mood, count) ->
                val color = moodColors.entries.firstOrNull { mood.contains(it.key, true) }?.value ?: SaharaSky
                val pct   = count.toFloat() / total
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(mood, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(80.dp))
                    LinearProgressIndicator(
                        progress = { pct },
                        modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = color, trackColor = color.copy(.12f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("$count", style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, color = color)
                }
            }
        }
    }
}

@Composable
private fun AccountHealthCard(report: WeeklyReport, hazeState: HazeState, isEnglish: Boolean) {
    SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            HealthRow(Icons.Default.VerifiedUser, SaharaStrongGreen,
                if (isEnglish) "Email Verified" else "Email Verify",
                if (report.emailVerified) (if (isEnglish) "Confirmed" else "Hua hai")
                else (if (isEnglish) "Not verified" else "Nahi hua"),
                if (report.emailVerified) SaharaStrongGreen else SaharaCoral)
            HorizontalDivider(Modifier.padding(horizontal = 0.dp), color = MaterialTheme.colorScheme.onSurface.copy(.06f))
            HealthRow(Icons.Default.PersonAdd, SaharaSky,
                if (isEnglish) "Member For" else "Member Hai",
                if (isEnglish) "${report.memberSinceDays} days" else "${report.memberSinceDays} din se",
                SaharaSky)
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(.06f))
            HealthRow(Icons.Default.Assessment, SaharaLavender,
                if (isEnglish) "Assessment Status" else "Assessment",
                if (report.dast10Score >= 0) (if (isEnglish) "Completed" else "Mukammal")
                else (if (isEnglish) "Not taken" else "Nahi di"),
                if (report.dast10Score >= 0) SaharaStrongGreen else SaharaWarning)
        }
    }
}

@Composable
private fun HealthRow(icon: ImageVector, iconColor: Color, label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).background(iconColor.copy(.12f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
private fun MiniStatCard(icon: ImageVector, color: Color, value: String, label: String,
                          hazeState: HazeState, modifier: Modifier) {
    SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
