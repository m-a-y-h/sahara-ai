package pk.edu.ucp.saharaai.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.ui.components.BottomNav
import pk.edu.ucp.saharaai.ui.components.CardVariant
import pk.edu.ucp.saharaai.ui.components.SaharaCard
import pk.edu.ucp.saharaai.ui.theme.*

data class ProgressAchievement(
    val nameEn: String, val nameUr: String,
    val descEn: String, val descUr: String,
    val unlocked: Boolean,
    val icon: ImageVector
)

@Composable
fun ProgressScreen(
    navController: NavController,
    isEnglish: Boolean = false
) {
    val isDark = isSystemInDarkTheme()
    val hazeState = remember { HazeState() }

    val currentStreak = GlobalAppState.currentStreak
    val hasCompletedAssessment = GlobalAppState.hasCompletedInitialAssessment
    val milestones = listOf(1, 7, 14, 30, 90, 365)

    val achievements = remember(currentStreak, hasCompletedAssessment) {
        listOf(
            ProgressAchievement("Initial Assessment", "Pehla Jaeza", "Started your journey", "Safar ka aghaz", hasCompletedAssessment, Icons.Default.AssignmentTurnedIn),
            ProgressAchievement("First Step", "Pehla Qadam", "1 day streak", "1 din streak", currentStreak >= 1, Icons.Default.Star),
            ProgressAchievement("Consistency", "Mustaqil Mizaji", "3 day streak", "3 din streak", currentStreak >= 3, Icons.Default.Timeline),
            ProgressAchievement("Week Strong", "Mazboot Hafta", "7 day streak", "7 din streak", currentStreak >= 7, Icons.Default.LocalFireDepartment),
            ProgressAchievement("Clean Fortnight", "Do Hafte Saaf", "14 day streak", "14 din streak", currentStreak >= 14, Icons.Default.Bolt),
            ProgressAchievement("Month Milestone", "Mahine Ki Manzil", "30 day streak", "30 din streak", currentStreak >= 30, Icons.Default.CalendarMonth),
            ProgressAchievement("Community Member", "Community Rukn", "Join a group", "Group join karein", false, Icons.Default.Groups),
            ProgressAchievement("Habit Breaker", "Adat Chhorne Wala", "45 day streak", "45 din streak", currentStreak >= 45, Icons.Default.Psychology),
            ProgressAchievement("Quarter Century", "Quarter Century", "60 day streak", "60 din streak", currentStreak >= 60, Icons.AutoMirrored.Filled.DirectionsRun),
            ProgressAchievement("Resilience", "Sabr-o-Istiqlal", "90 day streak", "90 din streak", currentStreak >= 90, Icons.Default.Shield),
            ProgressAchievement("Half Year Hero", "Adha Saal Hero", "180 day streak", "180 din streak", currentStreak >= 180, Icons.Default.WorkspacePremium),
            ProgressAchievement("Iron Will", "Mazboot Iradah", "200 day streak", "200 din streak", currentStreak >= 200, Icons.Default.SelfImprovement),
            ProgressAchievement("Life Saver", "Jan Bachane Wala", "Support 5 peers", "5 doston ki madad", false, Icons.Default.VolunteerActivism),
            ProgressAchievement("Mentor", "Rehnuma", "Lead a session", "Session lead karein", false, Icons.Default.School),
            ProgressAchievement("Unstoppable", "Na-qabil-e-Taskhir", "250 day streak", "250 din streak", currentStreak >= 250, Icons.Default.RocketLaunch),
            ProgressAchievement("Golden Heart", "Sunehra Dil", "Donate to cause", "Donate karein", false, Icons.Default.Favorite),
            ProgressAchievement("Decade of Days", "Dinon ki Dehai", "300 day streak", "300 din streak", currentStreak >= 300, Icons.Default.AutoAwesome),
            ProgressAchievement("Sovereign", "Khud-mukhtar", "1 year sober", "1 saal sober", currentStreak >= 365, Icons.Default.MilitaryTech)
        )
    }

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

    val blob1Color = SaharaWarning.copy(alpha = if (isDark) 0.2f else 0.15f)
    val blob2Color = SaharaStrongGreen.copy(alpha = if (isDark) 0.2f else 0.18f)

    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val blobRotation by infiniteTransition.animateFloat(
        initialValue = -10f, targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(7000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "rotation"
    )
    val blobScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(5000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "scale"
    )

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

                Column {
                    Text(
                        text = if (isEnglish) "My Progress" else "Meri Progress",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = SaharaStrongGreen
                    )
                    Text(
                        text = if (isEnglish) "Track your recovery journey" else "Apni recovery journey dekhen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = 0.9f }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isEnglish) "Current Streak" else "Mojooda Streak",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "$currentStreak",
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SaharaStrongGreen
                                )
                                Text(
                                    text = if (isEnglish) " days" else " din",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = SaharaStrongGreen,
                                    modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier.size(64.dp).background(SaharaWarning.copy(alpha = if (isDark) 0.2f else 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.LocalFireDepartment, contentDescription = "Fire", modifier = Modifier.size(36.dp), tint = SaharaWarning)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = if (isEnglish) "Milestones" else "Manzilain (Milestones)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))

                SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        milestones.take(4).forEach { day ->
                            val reached = currentStreak >= day
                            val dayLabel = if (isEnglish) "Days" else "Din"

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (reached) SaharaStrongGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (reached) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(20.dp))
                                    } else {
                                        Text("$day", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (day == 1) (if (isEnglish) "1 Day" else "1 Din") else "$day $dayLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (reached) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    LinearProgressIndicator(
                        progress = { (currentStreak.toFloat() / 30f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = SaharaStrongGreen,
                        trackColor = SaharaStrongGreen.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (isEnglish) "Next milestone: 30 days" else "Agla milestone: 30 din",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = SaharaStrongGreen,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))

                Text(
                    text = if (isEnglish) "Achievements" else "Kamiyabiyan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))

                achievements.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { ach ->
                            val title = if (isEnglish) ach.nameEn else ach.nameUr
                            val desc = if (isEnglish) ach.descEn else ach.descUr

                            SaharaCard(
                                variant = CardVariant.DASHBOARD_GLASS,
                                modifier = Modifier.weight(1f).aspectRatio(0.85f)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .offset(y = (-6).dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (ach.unlocked) SaharaWarning.copy(alpha = if (isDark) 0.25f else 0.15f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = ach.icon,
                                            contentDescription = null,
                                            tint = if (ach.unlocked) SaharaWarning else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (ach.unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        modifier = Modifier.basicMarquee()
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        fontSize = 9.sp,
                                        modifier = Modifier.basicMarquee()
                                    )
                                }
                            }
                        }
                        repeat(3 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
                Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding() + 40.dp))
            }
        }
    }
}