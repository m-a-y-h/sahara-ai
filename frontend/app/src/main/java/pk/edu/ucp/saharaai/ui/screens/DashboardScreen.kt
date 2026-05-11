package pk.edu.ucp.saharaai.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.ui.components.BottomNav
import pk.edu.ucp.saharaai.ui.components.SaharaCard
import pk.edu.ucp.saharaai.ui.components.CardVariant
import pk.edu.ucp.saharaai.ui.theme.*
import java.util.Calendar
import java.util.TimeZone

object GlobalAppState {
    var userName by mutableStateOf("")
    var userEmail by mutableStateOf("")
    var hasCheckedIn by mutableStateOf(false)
    var currentStreak by mutableStateOf(12)
    var hasCompletedInitialAssessment by mutableStateOf(false)
    var dast10Score by mutableStateOf(0)
    var isMinor by mutableStateOf(false)
    var anonymousUsername by mutableStateOf("")
    var userLocation by mutableStateOf("")
    var hasGrantedLocation by mutableStateOf(false)
}

data class DailyTip(
    val textEn: String,
    val textUr: String,
    val isExternalLink: Boolean,
    val actionDestination: String
)

data class RiskStatus(
    val percentage: Float,
    val title: String,
    val action: String,
    val color: Color
)

@Composable
fun DashboardScreen(
    navController: NavController,
    isEnglish: Boolean = false,
    userName: String = "User"
) {
    val isDark = isSystemInDarkTheme()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val hazeState = remember { HazeState() }

    val accentGreen = SaharaStrongGreen
    val bgGradient = if (isDark) {
        listOf(
            SaharaStrongGreen.copy(alpha = 0.2f),
            MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.background
        )
    } else {
        listOf(
            SaharaStrongGreen.copy(alpha = 0.25f),
            SaharaPeach.copy(alpha = 0.1f),
            MaterialTheme.colorScheme.background.copy(alpha = 0.2f)
        )
    }

    val blob1Color = SaharaStrongGreen.copy(alpha = if (isDark) 0.25f else 0.15f)
    val blob2Color = SaharaStrongGreen.copy(alpha = if (isDark) 0.2f else 0.18f)

    val (riskPercentage, riskTitle, riskAction, riskColor) = if (!GlobalAppState.hasCompletedInitialAssessment) {
        RiskStatus(0f, "Pending Assessment", "Complete evaluation", Color.Gray)
    } else {
        when (GlobalAppState.dast10Score) {
            0 -> RiskStatus(5f, "No Problems", "None at this time", SaharaStrongGreen)
            in 1..2 -> RiskStatus(25f, "Low Level", "Monitor, re-assess later", SaharaSky)
            in 3..5 -> RiskStatus(50f, "Moderate Level", "Further Investigation", SaharaWarning)
            in 6..8 -> RiskStatus(75f, "Substantial Level", "Intensive Assessment", SaharaCoral)
            else -> RiskStatus(100f, "Severe Level", "Intensive Assessment", Color.Red)
        }
    }

    val greeting = remember {
        val pktTimeZone = TimeZone.getTimeZone("Asia/Karachi")
        val calendar = Calendar.getInstance(pktTimeZone)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        when (hour) {
            in 5..11 -> if (isEnglish) "Good morning," else "Subah Bakhair,"
            in 12..16 -> if (isEnglish) "Good afternoon," else "Dopeher Bakhair,"
            in 17..20 -> if (isEnglish) "Good evening," else "Shaam Bakhair,"
            else -> if (isEnglish) "Good night," else "Shab Bakhair,"
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val blobRotation by infiniteTransition.animateFloat(
        initialValue = -10f, targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(6000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "rotation"
    )
    val blobScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(5000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "scale"
    )

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val assessmentPulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseScale"
    )

    val quickActionsScrollState = rememberScrollState()
    LaunchedEffect(Unit) {
        delay(600)
        quickActionsScrollState.animateScrollTo(180, tween(durationMillis = 350, easing = FastOutLinearInEasing))
        quickActionsScrollState.animateScrollTo(0, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }

    val tipsList = listOf(
        DailyTip("Drinking a glass of water first thing in the morning boosts brain function.", "Subah uthte hi ek glass pani peena dimagh ke liye best hai.", true, "https://www.ncbi.nlm.nih.gov/pmc/articles/PMC2908954/"),
        DailyTip("Take a 5-minute stretch break. Exercise aids in addiction recovery.", "5 min ki break lein. Exercise recovery mein madad karti hai.", true, "https://www.ncbi.nlm.nih.gov/pmc/articles/PMC3224086/"),
        DailyTip("Write down three things you are grateful for today.", "Aaj ki 3 achi baatein apne journal mein note karein.", false, "journal")
    )

    var currentTipIndex by remember { mutableStateOf(0) }
    val tipScaleAnim = remember { Animatable(1f) }
    val tipAlphaAnim = remember { Animatable(1f) }

    Scaffold(
        bottomBar = { BottomNav(navController = navController, hazeState = hazeState) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .background(Brush.verticalGradient(bgGradient))
        ) {
            Box(modifier = Modifier.size(350.dp).offset(x = (-80).dp, y = (-50).dp).rotate(blobRotation).scale(blobScale).background(Brush.radialGradient(listOf(blob1Color, Color.Transparent))))
            Box(modifier = Modifier.size(400.dp).align(Alignment.BottomEnd).offset(x = 100.dp, y = 50.dp).rotate(-blobRotation).scale(blobScale).background(Brush.radialGradient(listOf(blob2Color, Color.Transparent))))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.align(Alignment.TopStart)) {
                        Text(greeting, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$userName 👋", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = accentGreen)
                    }
                    IconButton(
                        onClick = { navController.navigate("notifications") },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(y = (-2).dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), CircleShape)
                    ) {
                        BadgedBox(
                            badge = {
                                if (pk.edu.ucp.saharaai.ui.screens.NotificationManager.hasUnread()) {
                                    Badge(containerColor = SaharaCoral, modifier = Modifier.offset(x = (-4).dp, y = 4.dp))
                                }
                            }
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = accentGreen)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = 0.85f }) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier.size(48.dp).background(riskColor.copy(alpha = if (isDark) 0.25f else 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = riskColor)
                                }
                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text("Current Status", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = riskTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = riskColor
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${riskPercentage.toInt()}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("Score", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        LinearProgressIndicator(
                            progress = { riskPercentage / 100f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = riskColor,
                            trackColor = riskColor.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        val actionRowModifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .let { base ->
                                if (!GlobalAppState.hasCompletedInitialAssessment) {
                                    base.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { navController.navigate("assessment") }
                                } else base
                            }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = actionRowModifier
                        ) {
                            Icon(Icons.AutoMirrored.Filled.TrendingDown, contentDescription = null, tint = accentGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(riskAction, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = accentGreen)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(if (isEnglish) "Quick Actions" else "Fauri Actions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val itemModifier = Modifier.weight(1f)

                    if (!GlobalAppState.hasCompletedInitialAssessment) {
                        QuickActionItem(
                            "Assessment",
                            Icons.AutoMirrored.Filled.Assignment,
                            SaharaStrongGreen,
                            itemModifier.graphicsLayer {
                                scaleX = assessmentPulseScale
                                scaleY = assessmentPulseScale
                            },
                            isDark
                        ) {
                            navController.navigate("assessment")
                        }

                        QuickActionItem("Counselors", Icons.Default.Psychology, Color(0xFFEC6A45), itemModifier, isDark) {
                            navController.navigate("counselors")
                        }

                        QuickActionItem("Emergency", Icons.Default.Warning, SaharaCoral, itemModifier, isDark) {
                            navController.navigate("emergency")
                        }
                    } else {
                        QuickActionItem("Counselors", Icons.Default.Psychology, Color(0xFFEC6A45), itemModifier, isDark) {
                            navController.navigate("counselors")
                        }

                        QuickActionItem("Emergency", Icons.Default.Warning, SaharaCoral, itemModifier, isDark) {
                            navController.navigate("emergency")
                        }

                        QuickActionItem("Assessment", Icons.AutoMirrored.Filled.Assignment, Color.Gray, itemModifier, isDark) {
                            Toast.makeText(
                                context,
                                if (isEnglish) "You have already completed the test for your current evaluation period, please try later."
                                else "Aap apna assessment pehle hi mukammal kar chuke hain.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                Text(if (isEnglish) "Risk Score Calculators" else "Risk Calculators", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(16.dp))

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val itemWidth = (maxWidth - 32.dp) / 3
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(quickActionsScrollState), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        val itemModifier = Modifier.width(itemWidth)
                        QuickActionItem("Voice AI", Icons.Default.Mic, Color(0xFF9B7EBD), itemModifier, isDark) { navController.navigate("voice-analysis") }
                        QuickActionItem("Journal", Icons.AutoMirrored.Filled.Assignment, Color(0xFF8D6E63), itemModifier, isDark) { navController.navigate("journal") }
                        QuickActionItem("Community", Icons.Default.Groups, accentGreen, itemModifier, isDark) { navController.navigate("community") }
                        QuickActionItem("Meditation", Icons.Default.SelfImprovement, SaharaPeach, itemModifier, isDark) { navController.navigate("meditation") }
                        QuickActionItem("Sleep", Icons.Default.Bedtime, Color(0xFF5C6BC0), itemModifier, isDark) { navController.navigate("sleep-tracker") }
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                Text(if (isEnglish) "Today's Insights" else "Aaj ke Insights", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AnimatedVisibility(visible = !GlobalAppState.hasCheckedIn, exit = scaleOut(tween(500)) + shrinkVertically(tween(500))) {
                        SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = 0.85f }.clickable { navController.navigate("chat") }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(44.dp).background(SaharaSky.copy(alpha = if (isDark) 0.3f else 0.2f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = SaharaSky)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Rozana ka Check-in", fontWeight = FontWeight.Bold, color = SaharaSky)
                                    Text(if (isEnglish) "Let's talk about how you're feeling today." else "Dekhein aaj aap kaisa feel kar rahe hain.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    val activeTip = tipsList[currentTipIndex]

                    SaharaCard(
                        variant = CardVariant.DASHBOARD_GLASS,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = tipScaleAnim.value
                                scaleY = tipScaleAnim.value
                                alpha = tipAlphaAnim.value * 0.85f
                            }
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                if (tipScaleAnim.value == 1f) {
                                    if (activeTip.isExternalLink) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(activeTip.actionDestination)))
                                    else navController.navigate(activeTip.actionDestination)

                                    coroutineScope.launch {
                                        tipScaleAnim.animateTo(1.08f, tween(100))
                                        launch { tipAlphaAnim.animateTo(0f, tween(150)) }
                                        tipScaleAnim.animateTo(0.85f, tween(150))
                                        delay(20000)
                                        currentTipIndex = (currentTipIndex + 1) % tipsList.size
                                        tipScaleAnim.snapTo(0.85f)
                                        launch { tipAlphaAnim.animateTo(1f, tween(200)) }
                                        tipScaleAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                    }
                                }
                            }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(44.dp).background(SaharaPeach.copy(alpha = if (isDark) 0.3f else 0.2f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = SaharaPeach)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Tip", fontWeight = FontWeight.Bold, color = SaharaPeach)
                                Text(if (isEnglish) activeTip.textEn else activeTip.textUr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding() + 40.dp))
            }
        }
    }
}

@Composable
fun QuickActionItem(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.clickable { onClick() }) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color.copy(alpha = if (isDark) 0.25f else 0.15f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1
        )
    }
}