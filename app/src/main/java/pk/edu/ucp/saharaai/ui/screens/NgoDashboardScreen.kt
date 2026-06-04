package pk.edu.ucp.saharaai.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.data.model.CounselorProfile
import pk.edu.ucp.saharaai.data.model.RegionalRiskSummary
import pk.edu.ucp.saharaai.ui.components.CardVariant
import pk.edu.ucp.saharaai.ui.components.HazeBackButton
import pk.edu.ucp.saharaai.ui.components.SaharaCard
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.utils.NgoStatsExporter
import pk.edu.ucp.saharaai.utils.showLocalizedToast
import pk.edu.ucp.saharaai.viewmodels.NgoDashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NgoDashboardScreen(
    navController: NavController,
    isEnglish: Boolean = false,
    ngoKey: String = "",
    ngoViewModel: NgoDashboardViewModel = viewModel()
) {
    val isDark        = isSystemInDarkTheme()
    val bgHazeState   = remember { HazeState() }
    val softTextColor = if (isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f)
    val titleColor    = SaharaStrongGreen
    val context       = LocalContext.current

    
    LaunchedEffect(ngoKey) { ngoViewModel.loadDashboard(ngoKey) }

    val counselors  by ngoViewModel.counselors.collectAsState()
    val totalUsers  by ngoViewModel.totalUsers.collectAsState()
    val totalChats  by ngoViewModel.totalChats.collectAsState()
    val isLoading   by ngoViewModel.isLoading.collectAsState()
    val ngoRegion   by ngoViewModel.ngoRegion.collectAsState()
    val ngoName     by ngoViewModel.ngoName.collectAsState()
    val isRefreshing by ngoViewModel.isRefreshing.collectAsState()
    val regionalRisk by ngoViewModel.regionalRisk.collectAsState()

    
    val totalCounselors  = counselors.size
    val onlineCounselors = ngoViewModel.onlineCounselorCount()

    
    val onlinePct  = ngoViewModel.onlinePercent()
    val offlinePct = ngoViewModel.offlinePercent()
    val pieData = listOf(
        onlinePct  to SaharaStrongGreen,
        offlinePct to SaharaCoral
    )

    
    val blobMotion = rememberBackdropBlobMotion()

    // SAF-backed CSV export. The Uri is whatever the user picks in the system
    // file-picker (Downloads, Drive, etc.); we write the snapshot of the
    // dashboard's current totals + regional risk + counselor table to it.
    var pendingCsv by remember { mutableStateOf<String?>(null) }
    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val csv = pendingCsv
        pendingCsv = null
        if (uri == null || csv == null) return@rememberLauncherForActivityResult
        val ok = NgoStatsExporter.writeCsvToUri(context, uri, csv)
        context.showLocalizedToast(
            isEnglish,
            if (ok) "Saved regional statistics." else "Could not save the file.",
            if (ok) "Statistics save ho gayi." else "File save nahi ho saki.",
            if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
        )
    }
    val launchDownload: () -> Unit = {
        pendingCsv = NgoStatsExporter.buildCsv(
            region = ngoRegion,
            totalUsers = totalUsers,
            totalChats = totalChats,
            counselors = counselors,
            regionalRisk = regionalRisk,
        )
        saveCsvLauncher.launch(NgoStatsExporter.buildDefaultFilename(ngoRegion))
    }


    // Match the rest of the app: gradient + animated blobs as the hazeSource,
    // a HazeBackButton + title row instead of an image + dim + dark bar header.
    val bgGradient = if (isDark) {
        listOf(
            SaharaStrongGreen.copy(alpha = 0.20f),
            MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.background,
        )
    } else {
        listOf(
            SaharaStrongGreen.copy(alpha = 0.25f),
            SaharaSky.copy(alpha = 0.10f),
            MaterialTheme.colorScheme.background.copy(alpha = 0.2f),
        )
    }
    val blob1Color = SaharaStrongGreen.copy(alpha = if (isDark) 0.25f else 0.15f)
    val blob2Color = SaharaSky.copy(alpha = if (isDark) 0.20f else 0.18f)

    Box(modifier = Modifier.fillMaxSize()) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(bgHazeState)
                .background(Brush.verticalGradient(bgGradient))
        ) {
            Box(
                modifier = Modifier
                    .size(350.dp)
                    .offset(x = (-80).dp, y = (-50).dp)
                    .primaryBlobMotion(blobMotion)
                    .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(blob1Color, Color.Transparent)))
            )
            Box(
                modifier = Modifier
                    .size(400.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 100.dp, y = 50.dp)
                    .secondaryBlobMotion(blobMotion)
                    .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(blob2Color, Color.Transparent)))
            )
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { ngoViewModel.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Header — HazeBackButton + title Column. Title is the NGO's
                // own name when known (fallback to "NGO Dashboard"); subtext is
                // the operating region + live indicator.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HazeBackButton(onClick = { navController.popBackStack() }, hazeState = bgHazeState)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ngoName.ifBlank { if (isEnglish) "NGO Dashboard" else "NGO Dashboard" },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = titleColor,
                            maxLines = 2,
                        )
                        Text(
                            text = when {
                                ngoRegion.isBlank() && isEnglish -> "Regional overview · live"
                                ngoRegion.isBlank()              -> "Ilaqai jaiza · live"
                                isEnglish                        -> "$ngoRegion region · live"
                                else                             -> "$ngoRegion · live"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = softTextColor.copy(.65f),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {

                
                if (isLoading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SaharaStrongGreen)
                    }
                }

                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    NgoStatCard(
                        title    = if (isEnglish) "Total Users" else "Kul Sareen",
                        value    = "%,d".format(totalUsers),
                        icon     = Icons.Default.Group,
                        textColor = softTextColor,
                        hazeState = bgHazeState,
                        modifier = Modifier.weight(1f)
                    )
                    NgoStatCard(
                        title    = if (isEnglish) "Counselors" else "Counselors",
                        value    = "%,d".format(totalCounselors),
                        icon     = Icons.Default.People,
                        textColor = softTextColor,
                        hazeState = bgHazeState,
                        modifier = Modifier.weight(1f)
                    )
                }

                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    NgoStatCard(
                        title    = if (isEnglish) "Online Now" else "Abhi Online",
                        value    = "%,d".format(onlineCounselors),
                        icon     = Icons.Default.Wifi,
                        textColor = softTextColor,
                        hazeState = bgHazeState,
                        modifier = Modifier.weight(1f)
                    )
                    NgoStatCard(
                        title    = if (isEnglish) "Chat Sessions" else "Chat Sessions",
                        value    = "%,d".format(totalChats),
                        icon     = Icons.Default.Forum,
                        textColor = softTextColor,
                        hazeState = bgHazeState,
                        modifier = Modifier.weight(1f),
                        isAlert  = false
                    )
                }

                Spacer(Modifier.height(8.dp))

                
                SaharaCard(
                    variant   = CardVariant.GLASS,
                    hazeState = bgHazeState,
                    modifier  = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text  = if (isEnglish) "Counselor Status" else "Counselor Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = softTextColor
                    )
                    Spacer(Modifier.height(24.dp))

                    // The old donut chart broke down when totalCounselors was 0
                    // (renderers split 0/0 as 0% green + 100% red, which is a
                    // misleading view of "we have no counselors"). A horizontal
                    // split bar shows the real ratio AND degenerates gracefully
                    // to a flat empty pip when the list is empty.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "%,d".format(totalCounselors),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = softTextColor,
                            )
                            Text(
                                text = if (isEnglish) "Counselors total" else "Counselors total",
                                style = MaterialTheme.typography.labelSmall,
                                color = softTextColor.copy(.65f),
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "%,d".format(onlineCounselors),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = SaharaStrongGreen,
                            )
                            Text(
                                text = if (isEnglish) "online now" else "abhi online",
                                style = MaterialTheme.typography.labelSmall,
                                color = softTextColor.copy(.65f),
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    val safeTotal = totalCounselors.coerceAtLeast(1)
                    val onlineFrac = (onlineCounselors.toFloat() / safeTotal).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(SaharaCoral.copy(alpha = 0.18f)),
                    ) {
                        if (totalCounselors > 0 && onlineFrac > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(onlineFrac)
                                    .background(SaharaStrongGreen),
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        LegendItem(
                            color     = SaharaStrongGreen,
                            label     = if (isEnglish) "Online ${onlinePct.toInt()}%"
                                        else "Online ${onlinePct.toInt()}%",
                            textColor = softTextColor
                        )
                        LegendItem(
                            color     = SaharaCoral,
                            label     = if (isEnglish) "Offline ${offlinePct.toInt()}%"
                                        else "Offline ${offlinePct.toInt()}%",
                            textColor = softTextColor
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Analytics, null, tint = SaharaSky, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isEnglish) "Regional Risk Averages" else "Ilaqai Risk Average",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = softTextColor,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = launchDownload,
                        enabled = !isLoading,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = if (isEnglish)
                                "Download regional statistics (CSV)"
                            else
                                "Regional statistics download karein (CSV)",
                            tint = if (isLoading) SaharaStrongGreen.copy(.4f) else SaharaStrongGreen,
                        )
                    }
                }
                Text(
                    text = if (isEnglish)
                        "Latest assessment per user only. Users without a saved region appear as Unspecified."
                    else
                        "Har user ka sirf latest assessment. Region na ho to Unspecified dikhaya jata hai.",
                    style = MaterialTheme.typography.labelSmall,
                    color = softTextColor.copy(.65f)
                )
                if (regionalRisk.isEmpty() && !isLoading) {
                    SaharaCard(variant = CardVariant.GLASS, hazeState = bgHazeState, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (isEnglish) "No assessment data is available for this region." else "Is ilaqe ke assessment data mojood nahi.",
                            color = softTextColor.copy(.6f)
                        )
                    }
                } else {
                    regionalRisk.forEach { summary ->
                        RegionalRiskCard(summary, isEnglish, bgHazeState, softTextColor)
                    }
                }

                Spacer(Modifier.height(8.dp))

                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.People,
                        contentDescription = null,
                        tint = SaharaStrongGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text  = if (isEnglish) "Registered Counselors" else "Registered Counselors",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = softTextColor
                    )
                    if (totalCounselors > 0) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(SaharaStrongGreen.copy(.15f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "$totalCounselors",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SaharaStrongGreen
                            )
                        }
                    }
                }

                if (counselors.isEmpty() && !isLoading) {
                    SaharaCard(
                        variant   = CardVariant.GLASS,
                        hazeState = bgHazeState,
                        modifier  = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.PersonAdd, null,
                                    tint = softTextColor.copy(.4f),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text  = if (isEnglish) "No counselors registered yet"
                                            else "Abhi koi counselor registered nahi",
                                    color = softTextColor.copy(.5f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                } else {
                    counselors.forEach { counselor ->
                        NgoCounselorCard(
                            counselor     = counselor,
                            isEnglish     = isEnglish,
                            hazeState     = bgHazeState,
                            softTextColor = softTextColor,
                            isDark        = isDark,
                            onCallClick   = {
                                val cId = counselor.counselorId.ifBlank { counselor.userId }
                                val cName = counselor.name.replace(" ", "_").ifBlank { "Counselor" }
                                if (cId.isNotBlank()) navController.navigate("counselor-call/$cId/$cName/voice/self")
                            },
                            onVideoClick  = {
                                val cId = counselor.counselorId.ifBlank { counselor.userId }
                                val cName = counselor.name.replace(" ", "_").ifBlank { "Counselor" }
                                if (cId.isNotBlank()) navController.navigate("counselor-call/$cId/$cName/video/self")
                            },
                            onChatClick   = {
                                val cId   = counselor.counselorId.ifBlank { counselor.userId }
                                val cName = counselor.name.replace(" ", "_")
                                if (cId.isNotBlank()) {
                                    navController.navigate("counselor-chat/$cId/$cName")
                                }
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun RegionalRiskCard(
    summary: RegionalRiskSummary,
    isEnglish: Boolean,
    hazeState: HazeState,
    softTextColor: Color
) {
    val scoreColor = when {
        summary.averageLatestScore >= 6.0 -> SaharaCoral
        summary.averageLatestScore >= 3.0 -> SaharaWarning
        else -> SaharaStrongGreen
    }
    SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(summary.region, fontWeight = FontWeight.Bold, color = softTextColor)
                Text(
                    "%.1f / 10".format(summary.averageLatestScore),
                    fontWeight = FontWeight.ExtraBold,
                    color = scoreColor
                )
            }
            LinearProgressIndicator(
                progress = { (summary.averageLatestScore / 10.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(8.dp)),
                color = scoreColor,
                trackColor = scoreColor.copy(.15f)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (isEnglish) "${summary.assessedUsers}/${summary.registeredUsers} assessed" else "${summary.assessedUsers}/${summary.registeredUsers} assessed",
                    style = MaterialTheme.typography.labelSmall,
                    color = softTextColor.copy(.7f)
                )
                Text(
                    if (isEnglish) "${summary.totalAssessments} total tests" else "${summary.totalAssessments} tests",
                    style = MaterialTheme.typography.labelSmall,
                    color = softTextColor.copy(.7f)
                )
            }
            Text(
                if (isEnglish)
                    "${summary.highRiskUsers} substantial/severe, ${summary.moderateRiskUsers} moderate (latest scores)"
                else
                    "${summary.highRiskUsers} zyada/shadeed, ${summary.moderateRiskUsers} moderate (latest scores)",
                style = MaterialTheme.typography.labelSmall,
                color = softTextColor.copy(.7f)
            )
        }
    }
}



@Composable
fun NgoCounselorCard(
    counselor: CounselorProfile,
    isEnglish: Boolean,
    hazeState: HazeState,
    softTextColor: Color,
    isDark: Boolean,
    onCallClick: () -> Unit,
    onVideoClick: () -> Unit,
    onChatClick: () -> Unit
) {
    val isOnline    = counselor.isAvailable
    val statusColor = if (isOnline) SaharaStrongGreen else SaharaCoral
    val initials    = counselor.name.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2).joinToString("")
        .ifBlank { "?" }

    SaharaCard(
        variant   = CardVariant.GLASS,
        hazeState = hazeState,
        modifier  = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {

            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp)
                        .background(SaharaStrongGreen.copy(.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initials, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SaharaStrongGreen)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text  = counselor.name.ifBlank { if (isEnglish) "Counselor" else "Counselor" },
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                        color      = if (isDark) Color.White else Color.Black
                    )
                    Text(
                        text  = counselor.specialization.ifBlank { "Mental Health" },
                        style = MaterialTheme.typography.labelMedium,
                        color = softTextColor.copy(.7f)
                    )
                    if (counselor.ngoName.isNotBlank()) {
                        Text(
                            text  = counselor.ngoName,
                            style = MaterialTheme.typography.labelSmall,
                            color = SaharaStrongGreen.copy(.8f)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .background(statusColor.copy(.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text  = if (isOnline) (if (isEnglish) "Online" else "Online")
                                else (if (isEnglish) "Offline" else "Offline"),
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color      = statusColor
                    )
                }
            }

            
            if (counselor.region.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = softTextColor.copy(.6f), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(counselor.region, style = MaterialTheme.typography.labelSmall, color = softTextColor.copy(.6f))
                }
            }

            
            if (counselor.totalRatings > 0 || counselor.sessionCount > 0) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (counselor.totalRatings > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = SaharaWarning, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "%.1f".format(counselor.rating),
                                style = MaterialTheme.typography.labelSmall,
                                color = softTextColor.copy(.8f)
                            )
                        }
                    }
                    if (counselor.sessionCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Forum, null, tint = SaharaSky, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "${counselor.sessionCount} sessions",
                                style = MaterialTheme.typography.labelSmall,
                                color = softTextColor.copy(.8f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick  = onCallClick,
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = SaharaStrongGreen.copy(if (isDark) .3f else .15f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Call, null, tint = SaharaStrongGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Call", color = SaharaStrongGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Button(
                    onClick  = onVideoClick,
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = SaharaSky.copy(if (isDark) .3f else .15f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Videocam, null, tint = SaharaSky, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Video", color = SaharaSky, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Button(
                    onClick  = onChatClick,
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = SaharaWarning.copy(if (isDark) .3f else .15f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, null, tint = SaharaWarning, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Chat", color = SaharaWarning, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}



@Composable
fun NgoStatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    textColor: Color,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    isAlert: Boolean = false
) {
    val primaryColor = if (isAlert) SaharaCoral else SaharaGreen
    val iconBgColor  = if (isAlert) SaharaCoral.copy(.15f) else SaharaGreen.copy(.15f)

    SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(iconBgColor, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = primaryColor, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = textColor,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            // Pin to two lines so the four cards in the dashboard row end at
            // the same height regardless of title length (Total Users / Online
            // Now / Chat Sessions / Counselors are different widths and were
            // misaligning the row).
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(.7f),
                minLines = 2,
                maxLines = 2,
            )
        }
    }
}



@Composable
fun LegendItem(color: Color, label: String, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, color = textColor, fontWeight = FontWeight.SemiBold)
    }
}
