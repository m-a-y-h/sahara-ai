package pk.edu.ucp.saharaai.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.LocationManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.ui.components.BottomNav
import pk.edu.ucp.saharaai.ui.components.ButtonVariant
import pk.edu.ucp.saharaai.ui.components.CardVariant
import pk.edu.ucp.saharaai.ui.components.SaharaButton
import pk.edu.ucp.saharaai.ui.components.SaharaCard
import pk.edu.ucp.saharaai.ui.theme.*
import java.util.Locale

fun fetchRealLocation(context: Context, onLocationFetched: (String) -> Unit) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    try {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val subLocality = address.subLocality ?: address.featureName ?: "Local Arena"
                    val locality = address.locality ?: address.subAdminArea ?: "Unknown City"
                    onLocationFetched("$subLocality, $locality")
                } else {
                    onLocationFetched("Unknown Sector")
                }
            } else {
                onLocationFetched("Location Unavailable")
            }
        }
    } catch (e: SecurityException) {
        onLocationFetched("Permission Denied")
    } catch (e: Exception) {
        onLocationFetched("Location Error")
    }
}

fun generateDesiAlias(): String {
    val isMuzakar = listOf(true, false).random()

    val (adjs, nouns) = if (isMuzakar) {
        listOf(
            "Bindaas", "Toofani", "Jalali", "Ninja", "Aflatoon", "Tez", "Masoom",
            "Thanda", "Khatta", "Meetha", "Karara", "Chatpata", "Zaalim", "Shahi",
            "Nawabi", "Khufiya", "Mast", "Chalaak", "Rangeela", "Classic", "Desi",
            "Epic", "Kadak", "Awesome", "Shandar", "Zabardast", "Zordaar", "Asli",
            "Anokha", "Sakht", "Kurkura", "Lazeez", "Ajeeb", "Pyaara", "Seedha",
            "Aakhri", "Bhunawa", "Taza", "VVIP", "Royal"
        ) to listOf(
            "Samosa", "BunKabab", "Roll", "Paratha", "Tikka", "Kabab", "Pulao",
            "Zarda", "Pakora", "Chargha", "GolGappa", "Paparh", "Broast", "Shawarma",
            "DahiBhalla", "Naan", "Kulcha", "Taftan", "Katakat", "GolaGanda",
            "AndaShami", "Pathoora", "Sandal", "RoohAfza", "Naurus", "JamEShireen",
            "Talbeena", "Sattu", "LimoPani", "Kaju", "Badam", "Akhrot", "Pista",
            "Chilghoza", "Makhaana", "Gurh", "Falooda", "Halwa", "Patisa",
            "SohanHalwa", "Pera", "DoodhSoda", "Qorma", "Keema", "Saag", "SiriPaaye",
            "Bong", "Anda", "GannayKaRas", "Gajrela", "Amrood", "Aam", "Falsa",
            "Jamun", "Kharbooza", "Tarbooz", "Singhara", "ChapliKabab"
        )
    } else {
        listOf(
            "Bindaas", "Toofani", "Jalali", "Ninja", "Aflatoon", "Tez", "Masoom",
            "Thandi", "Khatti", "Meethi", "Karari", "Chatpati", "Zaalim", "Shahi",
            "Nawabi", "Khufiya", "Mast", "Chalaak", "Rangeeli", "Classic", "Desi",
            "Epic", "Kadak", "Awesome", "Shandar", "Zabardast", "Zordaar", "Asli",
            "Anokhi", "Sakht", "Kurkuri", "Lazeez", "Ajeeb", "Pyaari", "Seedhi",
            "Aakhri", "Bhuni", "Taza", "VVIP", "Royal"
        ) to listOf(
            "Chutni", "Biryani", "Lassi", "Khashkhash", "Sewiyaan", "Nihari",
            "Karahi", "Haleem", "Sajji", "Chaat", "FruitChaat", "DahiPhulki",
            "Papri", "Boondi", "Golgappi", "Challi", "Makai", "ShamiTikki",
            "DaalMash", "Kachori", "Nimko", "Mungphali", "Rewari", "Khajoor",
            "Supari", "Saunf", "Panjeeri", "DoodhPati", "KashmiriChai", "PeshawariChai",
            "Jalebi", "Kulfi", "Kheer", "Firni", "Barfi", "Gajak", "ChanaChaat",
            "AlooTikki", "Boti", "Chapati", "Methi", "Lobia", "Ilaichi",
            "Kishmish", "Khubani", "Anjeer", "Sikanjabeen", "Pheni", "Qulfi",
            "Baqarkhani", "Chai"
        )
    }
    return "${adjs.random()}${nouns.random()}"
}

data class LeaderboardUser(val username: String, val location: String, val xp: Int, val rank: Int, val isCurrentUser: Boolean = false)
data class MilestoneTask(val titleEn: String, val titleUr: String, val descEn: String, val descUr: String, val xp: Int, val icon: ImageVector, val isComplete: Boolean)

@Composable
fun GameRecoveryScreen(
    navController: NavController,
    onNavigateBack: () -> Unit = { navController.popBackStack() },
    isEnglish: Boolean = false
) {
    val isDark = isSystemInDarkTheme()
    val hazeState = remember { HazeState() }
    val context = LocalContext.current

    var tempAlias by remember { mutableStateOf(GlobalAppState.anonymousUsername.ifEmpty { generateDesiAlias() }) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            GlobalAppState.hasGrantedLocation = true
            GlobalAppState.anonymousUsername = tempAlias
            NotificationManager.logUsername(tempAlias)

            fetchRealLocation(context) { realLoc ->
                GlobalAppState.userLocation = realLoc
            }
        } else {
            Toast.makeText(context, if(isEnglish) "Location Permission required for Game Based Recovery" else "Game Based Recovery ke liye location ki ijazat chahiye", Toast.LENGTH_SHORT).show()
        }
    }

    val finalAlias = GlobalAppState.anonymousUsername.ifEmpty { tempAlias }
    val finalLocation = GlobalAppState.userLocation.ifEmpty { "Locating..." }
    val currentUser = LeaderboardUser(finalAlias, finalLocation, 0, 999, true)

    val dailyLeaderboard = listOf(
        LeaderboardUser("ZaalimBroast", "Clifton, Karachi", 120, 1),
        LeaderboardUser("NaughtyKhashkash", "Bahria Town, Lahore", 95, 2),
        LeaderboardUser("EpicParatha", "DHA, Multan", 60, 3)
    )

    val weeklyLeaderboard = listOf(
        LeaderboardUser("ShahiKinnu", "F-8, Islamabad", 1500, 1),
        LeaderboardUser("ZaalimBroast", "Clifton, Karachi", 1240, 2),
        LeaderboardUser("MasoomFalsa", "PECHS, Karachi", 980, 3)
    )

    val allTimeLeaderboard = listOf(
        LeaderboardUser("MasoomFalsa", "PECHS, Karachi", 85000, 1),
        LeaderboardUser("ChatpataSonf", "Cavalry Ground, Lahore", 72000, 2),
        LeaderboardUser("ThandaSamosa", "Saddar, Rawalpindi", 68500, 3)
    )

    val dailyTasks = remember(GlobalAppState.hasCheckedIn) {
        listOf(
            MilestoneTask("Log Daily Triggers", "Triggers Log Karein", "Identify and log what triggered you today.", "Aaj ke triggers pehchan kar log karein.", 50, Icons.Default.Psychology, GlobalAppState.hasCheckedIn),
            MilestoneTask("CBT Reflection", "CBT Soch-Vichar", "Challenge one negative thought.", "Ek manfi (negative) soch ko challenge karein.", 75, Icons.Default.TrendingUp, false),
            MilestoneTask("Urge Surfing", "Khwahish Par Qaboo", "Practice 5 mins of urge surfing meditation.", "5 min urge surfing ki mashq karein.", 40, Icons.Default.Spa, false)
        )
    }

    val bgGradient = if (isDark) {
        listOf(SaharaStrongGreen.copy(alpha = 0.2f), MaterialTheme.colorScheme.background.copy(alpha = 0.6f), MaterialTheme.colorScheme.background)
    } else {
        listOf(SaharaStrongGreen.copy(alpha = 0.25f), SaharaSky.copy(alpha = 0.15f), MaterialTheme.colorScheme.background.copy(alpha = 0.2f))
    }
    val blob1Color = SaharaStrongGreen.copy(alpha = if (isDark) 0.25f else 0.15f)
    val blob2Color = SaharaSky.copy(alpha = if (isDark) 0.2f else 0.18f)

    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val blobRotation by infiniteTransition.animateFloat(initialValue = -10f, targetValue = 10f, animationSpec = infiniteRepeatable(tween(6000, easing = EaseInOutSine), RepeatMode.Reverse), label = "rotation")
    val blobScale by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 1.15f, animationSpec = infiniteRepeatable(tween(5000, easing = EaseInOutSine), RepeatMode.Reverse), label = "scale")

    Scaffold(
        bottomBar = { BottomNav(navController = navController, hazeState = hazeState) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState).background(Brush.verticalGradient(bgGradient))) {
            Box(modifier = Modifier.size(350.dp).offset(x = (-80).dp, y = (-50).dp).rotate(blobRotation).scale(blobScale).background(Brush.radialGradient(listOf(blob1Color, Color.Transparent))))
            Box(modifier = Modifier.size(400.dp).align(Alignment.BottomEnd).offset(x = 100.dp, y = 50.dp).rotate(-blobRotation).scale(blobScale).background(Brush.radialGradient(listOf(blob2Color, Color.Transparent))))

            AnimatedVisibility(
                visible = !GlobalAppState.hasGrantedLocation,
                enter = fadeIn(),
                exit = fadeOut(tween(500)) + scaleOut(targetScale = 0.9f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                            Box(modifier = Modifier.size(80.dp).background(SaharaSky.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = SaharaSky, modifier = Modifier.size(40.dp))
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = if (isEnglish) "Local Leaderboards" else "Muqami Leaderboards",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (isEnglish) "To assign you to your regional arena while keeping you anonymous, we need real-time location access." else "Aapko aapke ilaqay ke mutabiq anonymous leaderboard dikhane ke liye location ki ijazat darkar hai.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(SaharaStrongGreen.copy(alpha = 0.05f))
                                    .border(1.dp, SaharaStrongGreen.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = if (isEnglish) "Your Anonymous Alias:" else "Aapka Anonymous Naam:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = tempAlias,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = SaharaStrongGreen,
                                            maxLines = 1,
                                            modifier = Modifier.basicMarquee()
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    IconButton(
                                        onClick = { tempAlias = generateDesiAlias() },
                                        modifier = Modifier
                                            .background(SaharaStrongGreen.copy(alpha = 0.1f), CircleShape)
                                            .size(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Randomize",
                                            tint = SaharaStrongGreen,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            SaharaButton(
                                text = if (isEnglish) "Grant Access" else "Ijazat Dein",
                                onClick = {
                                    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                                    val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                                            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

                                    if (!isGpsEnabled) {
                                        Toast.makeText(context, "Please turn on your GPS Location in settings.", Toast.LENGTH_LONG).show()
                                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                    } else {
                                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    }
                                },
                                variant = ButtonVariant.DEFAULT,
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                isEnglish = isEnglish
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(onClick = onNavigateBack) {
                                Text(if (isEnglish) "Not Now" else "Abhi Nahi", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = GlobalAppState.hasGrantedLocation,
                enter = fadeIn(tween(500, delayMillis = 300)) + slideInVertically(initialOffsetY = { 50 }),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp).verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onNavigateBack, modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SaharaStrongGreen)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Game Based Recovery",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = SaharaStrongGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = 0.9f }) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier.size(56.dp).background(SaharaSky.copy(alpha = if (isDark) 0.3f else 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "#${currentUser.rank}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = SaharaSky
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentUser.username,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                    )
                                    Text(
                                        text = if (isEnglish) "from ${currentUser.location}" else "${currentUser.location} se",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${currentUser.xp} XP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = SaharaStrongGreen)
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Level 0", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("Level 1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { 0.05f },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = SaharaSky,
                                trackColor = SaharaSky.copy(alpha = 0.2f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    val pagerState = rememberPagerState(initialPage = 2, pageCount = { 3 })

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (isEnglish) "Top Warriors" else "Top Warriors",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp)
                        )

                        val tabTitle = when (pagerState.currentPage) {
                            0 -> if (isEnglish) "All Time" else "Hamesha"
                            1 -> if (isEnglish) "Weekly" else "Hafta War"
                            else -> if (isEnglish) "Daily" else "Rozana"
                        }
                        Text(text = tabTitle, style = MaterialTheme.typography.labelSmall, color = SaharaStrongGreen, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                        val activeList = when(page) {
                            0 -> allTimeLeaderboard
                            1 -> weeklyLeaderboard
                            else -> dailyLeaderboard
                        }

                        SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                activeList.forEachIndexed { index, user ->
                                    val isTop3 = user.rank <= 3
                                    val rankColor = when (user.rank) {
                                        1 -> Color(0xFFFFD700)
                                        2 -> Color(0xFFC0C0C0)
                                        3 -> Color(0xFFCD7F32)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "#${user.rank}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = rankColor,
                                            modifier = Modifier.width(32.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = user.username,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                            )
                                            Text(
                                                text = user.location,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isTop3) {
                                            Icon(Icons.Default.EmojiEvents, contentDescription = "Trophy", tint = rankColor, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(
                                            text = "${user.xp} XP",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (index < activeList.size - 1) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        repeat(3) { index ->
                            val isSelected = pagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(if (isSelected) 14.dp else 10.dp)
                                    .background(
                                        color = if (isSelected) SaharaStrongGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(topStartPercent = 100, bottomEndPercent = 100)
                                    )
                                    .rotate(45f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    Text(
                        text = if (isEnglish) "Daily Milestones" else "Rozana ke Ahdaaf",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    dailyTasks.forEach { task ->
                        val title = if (isEnglish) task.titleEn else task.titleUr
                        val desc = if (isEnglish) task.descEn else task.descUr
                        val iconColor = if (task.isComplete) SaharaStrongGreen else MaterialTheme.colorScheme.onSurfaceVariant

                        SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier.size(40.dp).background(iconColor.copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = task.icon,
                                            contentDescription = null,
                                            tint = iconColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (task.isComplete) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                        )
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                if (task.isComplete) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Done", tint = SaharaStrongGreen, modifier = Modifier.size(24.dp))
                                } else {
                                    Surface(shape = RoundedCornerShape(8.dp), color = SaharaSky.copy(alpha = 0.15f)) {
                                        Text(
                                            text = "+${task.xp} XP",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = SaharaSky,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding() + 40.dp))
                }
            }
        }
    }
}