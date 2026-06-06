package pk.edu.ucp.saharaai.ui.screens

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import pk.edu.ucp.saharaai.data.repository.AppReputationRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import pk.edu.ucp.saharaai.ui.components.BottomNav
import pk.edu.ucp.saharaai.ui.components.HazeBackButton
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.viewmodels.ScreenTimeViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit





private const val EV_ACTIVITY_RESUMED       = 1   
private const val EV_ACTIVITY_PAUSED        = 2   
private const val EV_SCREEN_INTERACTIVE     = 15  
private const val EV_SCREEN_NON_INTERACTIVE = 16  
private const val EV_KEYGUARD_SHOWN         = 17  
private const val EV_KEYGUARD_HIDDEN        = 18  



// TikTok + YouTube are always surfaced (explicitly wanted). Every OTHER app
// shows up only when the global reputation service has reverse-searched its
// name and scored it BAD/BRAINROT — see AppReputationRepository. This is what
// keeps the screen to a focused list instead of every app the user opened.
private val ALWAYS_FLAGGED_PACKAGES = setOf(
    "com.zhiliaoapp.musically",         // TikTok
    "com.ss.android.ugc.trill",         // TikTok (older international)
    "com.ss.android.ugc.aweme",         // TikTok / Douyin
    "com.google.android.youtube",       // YouTube
    "com.google.android.youtube.lite",  // YouTube Go
)



private data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val totalTimeMs: Long,
    val iconPainter: Painter?
)

private data class WeekDayData(
    val shortLabel: String,
    val totalMs: Long,
    val isToday: Boolean,
    val isFuture: Boolean
)














private fun midnightOf(dayOffset: Int = 0): Long {
    val c = Calendar.getInstance()
    c.add(Calendar.DAY_OF_YEAR, dayOffset)
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0);      c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}


private fun floorMidnight(ts: Long): Long {
    val c = Calendar.getInstance(); c.timeInMillis = ts
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0);      c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}


private fun distributeSession(
    byDay: MutableMap<Long, MutableMap<String, Long>>,
    pkg: String,
    rawStart: Long,
    rawEnd: Long,
    globalStart: Long,
    globalEnd: Long
) {
    val start = rawStart.coerceAtLeast(globalStart)
    val end   = rawEnd.coerceAtMost(globalEnd)
    if (end <= start) return

    var cursor = start
    while (cursor < end) {
        val dayKey  = floorMidnight(cursor)
        val nextDay = dayKey + TimeUnit.DAYS.toMillis(1)
        val segEnd  = minOf(end, nextDay)
        val ms      = segEnd - cursor
        if (ms > 0) byDay.getOrPut(dayKey) { mutableMapOf() }.merge(pkg, ms, Long::plus)
        cursor = nextDay
    }
}


private fun queryEventUsage(
    usm: UsageStatsManager,
    startMs: Long,
    endMs: Long
): Pair<Map<Long, Map<String, Long>>, Boolean> {

    val byDay    = mutableMapOf<Long, MutableMap<String, Long>>()
    var gotAny   = false

    try {
        val uev = usm.queryEvents(startMs - TimeUnit.HOURS.toMillis(3), endMs)
            ?: return byDay to false

        val ev          = UsageEvents.Event()
        val lastResumed = mutableMapOf<String, Long>() 
        var screenOn    = true  

        
        fun closeAll(atTime: Long) {
            for ((pkg, resumedAt) in lastResumed)
                distributeSession(byDay, pkg, resumedAt, atTime, startMs, endMs)
            lastResumed.clear()
        }

        while (uev.hasNextEvent()) {
            uev.getNextEvent(ev)
            gotAny = true
            val pkg = ev.packageName
            val ts  = ev.timeStamp

            val evType = ev.eventType

            if (evType == EV_ACTIVITY_RESUMED) {
                if (screenOn) {
                    
                    lastResumed[pkg]?.let { distributeSession(byDay, pkg, it, ts, startMs, endMs) }
                    lastResumed[pkg] = ts
                }
            } else if (evType == EV_ACTIVITY_PAUSED) {
                val r = lastResumed.remove(pkg)
                if (r != null) distributeSession(byDay, pkg, r, ts, startMs, endMs)
            } else if (evType == EV_SCREEN_NON_INTERACTIVE) {
                
                
                
                
                screenOn = false
                closeAll(ts)
            } else if (evType == EV_SCREEN_INTERACTIVE) {
                screenOn = true
                
            } else if (evType == EV_KEYGUARD_SHOWN) {
                
                closeAll(ts)
            }
            
        }

        
        
        if (screenOn) {
            val cap = minOf(System.currentTimeMillis(), endMs)
            for ((pkg, resumedAt) in lastResumed)
                distributeSession(byDay, pkg, resumedAt, cap, startMs, endMs)
        }
        

    } catch (_: Exception) {  }

    return byDay to gotAny
}


@Suppress("DEPRECATION")
private fun queryIntervalDailyUsage(
    usm: UsageStatsManager,
    dayStart: Long,
    dayEnd: Long
): Map<String, Long> {

    val windowMs = (dayEnd - dayStart).coerceAtLeast(1L)
    val result   = mutableMapOf<String, Long>()
    val stats    = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dayStart, dayEnd)
        ?: return result

    for (stat in stats) {
        
        if (stat.lastTimeUsed < dayStart || stat.lastTimeUsed >= dayEnd) continue

        val rawMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            stat.totalTimeVisible
        else
            stat.totalTimeInForeground

        if (rawMs <= 0L) continue

        
        val bStart   = stat.firstTimeStamp
        val bEnd     = stat.lastTimeStamp.coerceAtLeast(bStart + 1L)
        val bDur     = (bEnd - bStart).coerceAtLeast(1L)
        val oStart   = maxOf(dayStart, bStart)
        val oEnd     = minOf(dayEnd,   bEnd)
        val overlapMs = (oEnd - oStart).coerceAtLeast(0L)

        
        val scaledMs = (rawMs.toDouble() * overlapMs / bDur).toLong()

        
        val cappedMs = scaledMs
            .coerceAtMost(overlapMs)
            .coerceAtMost(windowMs)

        if (cappedMs > 0L)
            result[stat.packageName] = (result[stat.packageName] ?: 0L) + cappedMs
    }
    return result
}



private suspend fun loadTodayHarmfulApps(context: Context): List<AppUsageInfo> {
    val usm      = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val dayStart = midnightOf(0)
    val now      = System.currentTimeMillis()

    val (eventByDay, eventsWorked) = queryEventUsage(usm, dayStart, now)

    val todayUsage: Map<String, Long> =
        if (eventsWorked) eventByDay[floorMidnight(dayStart)] ?: emptyMap()
        else              queryIntervalDailyUsage(usm, dayStart, now)

    // Keep only TikTok / YouTube and apps the reputation service has
    // reverse-searched + scored BAD/BRAINROT — not every app with usage.
    // lookup() hits the local cache (the tracker service warms it as the user
    // switches apps) before any Firestore read, and enqueues unknown packages
    // for the backend reverse-search worker.
    val out = mutableListOf<AppUsageInfo>()
    for ((pkg, ms) in todayUsage) {
        if (ms <= 0L || isSystemApp(context, pkg)) continue
        val appName = getAppName(context, pkg)
        val flagged = pkg in ALWAYS_FLAGGED_PACKAGES ||
            // Defensive: a Firestore error (denied read / offline) must not crash
            // the screen — just treat the app as not-flagged for this load.
            runCatching { AppReputationRepository.lookup(pkg, appName)?.isBad() == true }
                .getOrDefault(false)
        if (flagged) {
            out += AppUsageInfo(pkg, appName, ms, drawableToBitmapPainter(context, pkg))
        }
    }
    return out.sortedByDescending { it.totalTimeMs }
}




private fun loadAppWeekData(context: Context, packageName: String): List<WeekDayData> {
    val usm      = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val now      = System.currentTimeMillis()
    val dow      = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    val fromMon  = if (dow == Calendar.SUNDAY) -6 else -(dow - Calendar.MONDAY)
    val labels   = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    val weekStart = midnightOf(fromMon)
    val weekEnd   = minOf(midnightOf(fromMon + 7), now)

    
    val (weekByDay, eventsWorked) = queryEventUsage(usm, weekStart, weekEnd)

    return (0..6).map { i ->
        val offset   = fromMon + i
        val dayStart = midnightOf(offset)
        val dayEnd   = dayStart + TimeUnit.DAYS.toMillis(1)
        val isFuture = dayStart > now
        val isToday  = offset == 0

        val ms: Long = when {
            isFuture    -> 0L

            eventsWorked ->
                weekByDay[floorMidnight(dayStart)]?.get(packageName) ?: 0L

            
            isToday     -> queryIntervalDailyUsage(usm, dayStart, now)[packageName] ?: 0L
            else        -> queryIntervalDailyUsage(usm, dayStart, dayEnd)[packageName] ?: 0L
        }

        WeekDayData(labels[i], ms, isToday, isFuture)
    }
}



private fun hasUsagePermission(context: Context): Boolean {
    val ops  = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    else
        @Suppress("DEPRECATION")
        ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun isSystemApp(context: Context, pkg: String): Boolean =
    try { (context.packageManager.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
    catch (_: Exception) { true }

private fun getAppName(context: Context, pkg: String): String =
    try {
        context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(pkg, PackageManager.GET_META_DATA)
        ).toString()
    } catch (_: Exception) { pkg }

private fun drawableToBitmapPainter(context: Context, pkg: String): Painter? =
    try {
        val d = context.packageManager.getApplicationIcon(pkg)
        val bmp = if (d is BitmapDrawable && d.bitmap != null) d.bitmap
        else {
            val b = Bitmap.createBitmap(
                d.intrinsicWidth.coerceAtLeast(1),
                d.intrinsicHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            d.setBounds(0, 0, b.width, b.height); d.draw(Canvas(b)); b
        }
        BitmapPainter(bmp.asImageBitmap())
    } catch (_: Exception) { null }

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val enabled = try {
        Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
    } catch (_: Settings.SettingNotFoundException) {
        0
    }
    if (enabled != 1) return false
    val services = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return services.split(":").any { it.contains("AppTrackerService", ignoreCase = true) }
}

private fun formatDuration(ms: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else  -> "< 1m"
    }
}

private fun usageColor(ms: Long): Color = when {
    ms >= TimeUnit.HOURS.toMillis(4)   -> SaharaCoral
    ms >= TimeUnit.HOURS.toMillis(2)   -> SaharaWarning
    ms >= TimeUnit.HOURS.toMillis(1)   -> SaharaSky
    else                               -> SaharaStrongGreen
}



@Composable
fun ScreenTimeScreen(
    navController: NavController,
    onNavigateBack: () -> Unit = { navController.popBackStack() },
    isEnglish: Boolean = false,
    screenTimeViewModel: ScreenTimeViewModel = viewModel()
) {
    val context   = LocalContext.current
    val isDark    = isSystemInDarkTheme()
    val hazeState = remember { HazeState() }
    val teal      = Color(0xFF00897B)
    val softText  = if (isDark) Color.White.copy(.9f) else Color.Black.copy(.85f)

    var hasPermission  by remember { mutableStateOf(hasUsagePermission(context)) }
    var todayApps      by remember { mutableStateOf<List<AppUsageInfo>>(emptyList()) }
    var isLoading      by remember { mutableStateOf(false) }
    var selectedApp    by remember { mutableStateOf<AppUsageInfo?>(null) }
    var appWeekData    by remember { mutableStateOf<List<WeekDayData>>(emptyList()) }
    var isLoadingWeek  by remember { mutableStateOf(false) }

    var isAccessEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    val liveEvents = screenTimeViewModel.liveEvents
    val lifecycleOwner = LocalLifecycleOwner.current

    fun refreshPermissionState() {
        hasPermission = hasUsagePermission(context)
        isAccessEnabled = isAccessibilityServiceEnabled(context)
    }

    DisposableEffect(lifecycleOwner) {
        refreshPermissionState()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isAccessEnabled) {
        screenTimeViewModel.listenToTodayEvents()
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        isLoading = true
        val loaded = withContext(Dispatchers.Default) { loadTodayHarmfulApps(context) }
        todayApps = loaded
        isLoading = false

        screenTimeViewModel.saveTodaySnapshot(
            loaded.map { Triple(it.packageName, it.appName, it.totalTimeMs) }
        )
    }

    LaunchedEffect(selectedApp) {
        val app = selectedApp ?: return@LaunchedEffect
        isLoadingWeek = true
        appWeekData = withContext(Dispatchers.Default) { loadAppWeekData(context, app.packageName) }
        isLoadingWeek = false
    }

    val bgGradient = if (isDark)
        listOf(teal.copy(.2f), MaterialTheme.colorScheme.background.copy(.6f), MaterialTheme.colorScheme.background)
    else
        listOf(teal.copy(.25f), SaharaSky.copy(.1f), MaterialTheme.colorScheme.background.copy(.2f))

    val blobMotion = rememberBackdropBlobMotion()

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .hazeSource(hazeState)
                .background(Brush.verticalGradient(bgGradient))
        ) {
            Box(Modifier.size(350.dp).offset((-80).dp, (-50).dp).primaryBlobMotion(blobMotion)
                .background(Brush.radialGradient(listOf(teal.copy(if (isDark) .25f else .15f), Color.Transparent))))
            Box(Modifier.size(400.dp).align(Alignment.BottomEnd).offset(100.dp, 50.dp).secondaryBlobMotion(blobMotion)
                .background(Brush.radialGradient(listOf(SaharaSky.copy(if (isDark) .2f else .18f), Color.Transparent))))
        }

        Scaffold(bottomBar = { BottomNav(navController, hazeState) }, containerColor = Color.Transparent) { pad ->
            AnimatedContent(
                targetState  = selectedApp,
                transitionSpec = { fadeIn(tween(220)).togetherWith(fadeOut(tween(160))) },
                label        = "sv"
            ) { app ->
                if (app == null) {
                    HarmfulAppsListView(pad, hasPermission, isLoading, todayApps,
                        isAccessEnabled, liveEvents,
                        teal, softText, isDark, isEnglish, hazeState, context,
                        onBack            = onNavigateBack,
                        onGrantPermission = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                        onRefreshPermission  = { hasPermission   = hasUsagePermission(context) },
                        onAccessibilitySettings = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        onRefreshAccessibility = { isAccessEnabled = isAccessibilityServiceEnabled(context) },
                        onAppTap          = { selectedApp = it }
                    )
                } else {
                    AppDetailView(app, appWeekData, isLoadingWeek,
                        pad, teal, softText, isDark, isEnglish, hazeState,
                        onBack = { selectedApp = null }
                    )
                }
            }
        }
    }
}



@Composable
private fun HarmfulAppsListView(
    pad: PaddingValues,
    hasPermission: Boolean,
    isLoading: Boolean,
    todayApps: List<AppUsageInfo>,
    isAccessEnabled: Boolean,
    liveEvents: List<Map<String, Any>>,
    teal: Color, softText: Color, isDark: Boolean, isEnglish: Boolean,
    hazeState: HazeState, context: Context,
    onBack: () -> Unit,
    onGrantPermission: () -> Unit,
    onRefreshPermission: () -> Unit,
    onAccessibilitySettings: () -> Unit,
    onRefreshAccessibility: () -> Unit,
    onAppTap: (AppUsageInfo) -> Unit
) {
    val maxMs   = todayApps.maxOfOrNull { it.totalTimeMs } ?: 1L
    val totalMs = todayApps.sumOf { it.totalTimeMs }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp,
            top = pad.calculateTopPadding() + 20.dp, bottom = pad.calculateBottomPadding() + 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HazeBackButton(onClick = onBack, hazeState = hazeState, tint = teal)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(if (isEnglish) "Screen Time" else "Screen Time",
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = teal)
                    Text(if (isEnglish) "Social & gaming apps — today" else "Aaj ke social aur game apps",
                        style = MaterialTheme.typography.bodySmall, color = softText.copy(.6f))
                }
            }
        }

        if (!hasPermission) {
            item {
                StCard(hazeState, teal.copy(.3f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PhoneAndroid, null, tint = teal, modifier = Modifier.size(48.dp))
                        Text(if (isEnglish) "Usage Access Required" else "Usage Access Chahiye",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text(
                            if (isEnglish) "Grant Usage Access in Settings to track harmful app screen time."
                            else "Settings mein Usage Access dein taake screen time dikh sake.",
                            style = MaterialTheme.typography.bodySmall, color = softText.copy(.7f), textAlign = TextAlign.Center)
                        Button(onClick = onGrantPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = teal),
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (isEnglish) "Open Settings" else "Settings Kholein", color = Color.White)
                        }
                        TextButton(onClick = onRefreshPermission) {
                            Text(if (isEnglish) "I've granted it — refresh" else "De di — refresh karein", color = teal)
                        }
                    }
                }
            }
            return@LazyColumn
        }

        if (isLoading) {
            item { Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { CircularProgressIndicator(color = teal) } }
            return@LazyColumn
        }

        if (todayApps.isNotEmpty()) {
            item {
                StCard(hazeState, teal.copy(.25f)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column {
                            Text(if (isEnglish) "Total Today" else "Aaj Kul",
                                style = MaterialTheme.typography.bodySmall, color = softText.copy(.7f))
                            Text(formatDuration(totalMs), style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold, color = usageColor(totalMs))
                            Text(if (isEnglish) "Tap any app for its weekly chart"
                            else "Kisi app ko tap karein hafta dekhne ke liye",
                                style = MaterialTheme.typography.labelSmall, color = softText.copy(.5f))
                        }
                        Box(Modifier.size(56.dp).background(teal.copy(if (isDark) .2f else .1f), CircleShape), Alignment.Center) {
                            Icon(Icons.Default.Shield, null, tint = teal, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
            items(todayApps, key = { it.packageName }) { info ->
                HarmfulAppRow(info, maxMs, isDark, softText, hazeState, isEnglish) { onAppTap(info) }
            }
        } else {
            item {
                StCard(hazeState, SaharaStrongGreen.copy(.25f)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🌿", fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(if (isEnglish) "No harmful app usage today! Great job."
                        else "Aaj koi nuqsandeh app nahi chali! Bohat acha.",
                            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
                            color = SaharaStrongGreen, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(
                            if (isAccessEnabled) SaharaStrongGreen else softText.copy(.3f),
                            CircleShape
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isEnglish) "Live App Tracking" else "Live App Tracking",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = softText
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (!isAccessEnabled) {
            item {
                StCard(hazeState, teal.copy(.3f)) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Visibility, null, tint = teal, modifier = Modifier.size(40.dp))
                        Text(
                            if (isEnglish) "Enable Live Session Tracking"
                            else "Live Session Tracking Enable Karein",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            if (isEnglish) {
                                "Grant Accessibility Service permission so Sahara AI can securely log every app session in real-time."
                            } else {
                                "Accessibility Service permission dein taake Sahara AI har app session ko real-time mein mehfooz tareeqe se save kare."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = softText.copy(.7f),
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onAccessibilitySettings,
                            colors = ButtonDefaults.buttonColors(containerColor = teal),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isEnglish) "Open Accessibility Settings"
                                else "Accessibility Settings Kholein",
                                color = Color.White
                            )
                        }
                        TextButton(onClick = onRefreshAccessibility) {
                            Text(
                                if (isEnglish) "I've enabled it — refresh"
                                else "Enable kar di — refresh karein",
                                color = teal,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        } else {
            item {
                val liveSummary = remember(liveEvents) {
                    liveEvents
                        .groupBy { it["packageName"] as? String ?: "" }
                        .map { (_, events) ->
                            val name = events.firstOrNull()?.get("appName") as? String ?: ""
                            val totalTimeMs = events.sumOf {
                                (it["durationMillis"] as? Long) ?: 0L
                            }
                            Triple(name, totalTimeMs, events.size)
                        }
                        .sortedByDescending { it.second }
                }

                StCard(hazeState, SaharaStrongGreen.copy(.2f)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                if (isEnglish) "Sessions Tracked Today" else "Aaj Ke Sessions",
                                style = MaterialTheme.typography.bodySmall,
                                color = softText.copy(.7f)
                            )
                            Text(
                                "${liveEvents.size}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = SaharaStrongGreen
                            )
                            Text(
                                if (isEnglish) "Securely synced ✓" else "Mehfooz sync ✓",
                                style = MaterialTheme.typography.labelSmall,
                                color = SaharaStrongGreen.copy(.7f)
                            )
                        }
                        Box(
                            Modifier
                                .size(52.dp)
                                .background(
                                    SaharaStrongGreen.copy(if (isDark) .2f else .12f),
                                    CircleShape
                                ),
                            Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Visibility,
                                null,
                                tint = SaharaStrongGreen,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    if (liveSummary.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = softText.copy(.08f))
                        Spacer(Modifier.height(10.dp))
                        liveSummary.take(5).forEach { (name, totalTimeMs, count) ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                Arrangement.SpaceBetween,
                                Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        if (isEnglish) "$count open${if (count != 1) "s" else ""}"
                                        else "$count baar khola",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = softText.copy(.5f)
                                    )
                                }
                                Text(
                                    if (totalTimeMs > 0L) formatDuration(totalTimeMs) else "< 1m",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SaharaStrongGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}



@Composable
private fun HarmfulAppRow(
    info: AppUsageInfo, maxMs: Long, isDark: Boolean, softText: Color,
    hazeState: HazeState, isEnglish: Boolean, onClick: () -> Unit
) {
    val appColor   = usageColor(info.totalTimeMs)
    val barFrac by animateFloatAsState(
        (info.totalTimeMs.toFloat() / maxMs.toFloat()).coerceIn(0.02f, 1f),
        tween(600, easing = FastOutSlowInEasing), label = "bar")

    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .hazeEffect(hazeState) { blurEffect { blurRadius = 25.dp; colorEffects = listOf(HazeColorEffect.tint(Color.White.copy(.08f))) } }
            .border(1.dp, appColor.copy(.25f), RoundedCornerShape(16.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).background(appColor.copy(if (isDark) .2f else .1f), RoundedCornerShape(12.dp)), Alignment.Center) {
                if (info.iconPainter != null)
                    Image(info.iconPainter, info.appName, Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)))
                else
                    Text(info.appName.take(1).uppercase(Locale.US), fontWeight = FontWeight.Bold, color = appColor, fontSize = 18.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(info.appName, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(formatDuration(info.totalTimeMs), fontWeight = FontWeight.Bold,
                        color = appColor, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(appColor.copy(.15f))) {
                    Box(Modifier.fillMaxWidth(barFrac).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(appColor))
                }
            }
            Spacer(Modifier.width(10.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = softText.copy(.3f),
                modifier = Modifier.size(16.dp).rotate(180f))
        }
    }
}



@Composable
private fun AppDetailView(
    app: AppUsageInfo, weekData: List<WeekDayData>, isLoadingWeek: Boolean,
    pad: PaddingValues, teal: Color, softText: Color, isDark: Boolean,
    isEnglish: Boolean, hazeState: HazeState, onBack: () -> Unit
) {
    val appColor = usageColor(app.totalTimeMs)

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp,
            top = pad.calculateTopPadding() + 20.dp, bottom = pad.calculateBottomPadding() + 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HazeBackButton(onClick = onBack, hazeState = hazeState, tint = teal)
                Spacer(Modifier.width(10.dp))
                if (app.iconPainter != null)
                    Image(app.iconPainter, app.appName, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
                else
                    Box(Modifier.size(36.dp).background(appColor.copy(.2f), RoundedCornerShape(10.dp)), Alignment.Center) {
                        Text(app.appName.take(1).uppercase(Locale.US), color = appColor, fontWeight = FontWeight.Bold)
                    }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(app.appName, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold, color = softText)
                    Text(if (isEnglish) "Weekly Usage" else "Hafta Wari Istemaal",
                        style = MaterialTheme.typography.bodySmall, color = softText.copy(.6f))
                }
            }
        }

        
        item {
            StCard(hazeState, appColor.copy(.3f)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text(if (isEnglish) "Today" else "Aaj",
                            style = MaterialTheme.typography.bodySmall, color = softText.copy(.7f))
                        Text(formatDuration(app.totalTimeMs), style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold, color = appColor)
                    }
                    Box(Modifier.size(52.dp).background(appColor.copy(if (isDark) .2f else .12f), CircleShape), Alignment.Center) {
                        if (app.iconPainter != null)
                            Image(app.iconPainter, app.appName, Modifier.size(30.dp).clip(CircleShape))
                        else
                            Icon(Icons.Default.PhoneAndroid, null, tint = appColor, modifier = Modifier.size(26.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                val (ic, en, ur) = when {
                    app.totalTimeMs >= TimeUnit.HOURS.toMillis(4)   ->
                        Triple(SaharaCoral,       "⚠ Very high usage today!",        "⚠ Aaj bahut zyada istemaal!")
                    app.totalTimeMs >= TimeUnit.HOURS.toMillis(2)   ->
                        Triple(SaharaWarning,     "High usage — consider limiting.",  "Zyada istemaal — limit karein.")
                    app.totalTimeMs >= TimeUnit.MINUTES.toMillis(30) ->
                        Triple(SaharaSky,         "Moderate usage today.",            "Mutawasit istemaal aaj.")
                    else ->
                        Triple(SaharaStrongGreen, "Low usage today. Keep it up! 🌿", "Kam istemaal. Aise hi rakhein! 🌿")
                }
                Surface(shape = RoundedCornerShape(10.dp), color = ic.copy(.12f)) {
                    Text(if (isEnglish) en else ur, style = MaterialTheme.typography.bodySmall, color = ic,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }

        
        item {
            StCard(hazeState, appColor.copy(.2f)) {
                Text(if (isEnglish) "This Week" else "Is Hafte",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    color = softText, modifier = Modifier.padding(bottom = 16.dp))

                when {
                    isLoadingWeek -> Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
                        CircularProgressIndicator(color = appColor, modifier = Modifier.size(28.dp))
                    }
                    weekData.isEmpty() -> Box(Modifier.fillMaxWidth().height(60.dp), Alignment.Center) {
                        Text(if (isEnglish) "No data" else "Data nahi hai",
                            style = MaterialTheme.typography.bodySmall, color = softText.copy(.5f))
                    }
                    else -> {
                        val maxMs  = (weekData.maxOfOrNull { it.totalMs } ?: 0L)
                            .coerceAtLeast(TimeUnit.MINUTES.toMillis(1))
                        val maxBarH = 110.dp

                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround, Alignment.Bottom) {
                            weekData.forEach { day ->
                                val frac by animateFloatAsState(
                                    if (day.isFuture) 0f else (day.totalMs.toFloat() / maxMs).coerceIn(0f, 1f),
                                    tween(500, easing = FastOutSlowInEasing), label = "wb")
                                val barColor = when {
                                    day.isFuture      -> softText.copy(.1f)
                                    day.isToday       -> appColor
                                    day.totalMs == 0L -> softText.copy(.12f)
                                    else              -> appColor.copy(.5f)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    if (!day.isFuture && day.totalMs > 0L) {
                                        Text(formatDuration(day.totalMs), fontSize = 8.sp,
                                            color = if (day.isToday) appColor else softText.copy(.5f),
                                            textAlign = TextAlign.Center,
                                            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal)
                                        Spacer(Modifier.height(3.dp))
                                    } else Spacer(Modifier.height(16.dp))

                                    Box(Modifier.width(18.dp).height(maxBarH)
                                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                        .background(softText.copy(.07f)), Alignment.BottomCenter) {
                                        Box(Modifier.width(18.dp)
                                            .height((maxBarH * frac).coerceAtLeast(if (!day.isFuture) 2.dp else 0.dp))
                                            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                            .background(barColor)
                                            .then(if (day.isToday) Modifier.border(1.dp, appColor, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)) else Modifier))
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(day.shortLabel, fontSize = 10.sp,
                                        fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = if (day.isToday) appColor else softText.copy(.5f))
                                    if (day.isToday) {
                                        Spacer(Modifier.height(2.dp))
                                        Box(Modifier.size(4.dp).background(appColor, CircleShape))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        val weekTotal = weekData.sumOf { it.totalMs }
                        if (weekTotal > 0) {
                            Text((if (isEnglish) "This week: " else "Is hafte: ") + formatDuration(weekTotal),
                                style = MaterialTheme.typography.labelSmall, color = softText.copy(.5f),
                                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}



@Composable
private fun StCard(hazeState: HazeState, borderColor: Color, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .hazeEffect(hazeState) { blurEffect { blurRadius = 25.dp; colorEffects = listOf(HazeColorEffect.tint(Color.White.copy(.08f))) } }
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) { Column(content = content) }
}
