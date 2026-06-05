package pk.edu.ucp.saharaai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.ui.components.BottomNav
import pk.edu.ucp.saharaai.ui.components.CardVariant
import pk.edu.ucp.saharaai.ui.components.HazeBackButton
import pk.edu.ucp.saharaai.ui.components.SaharaCard
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.viewmodels.ProgressViewModel



data class ProgressAchievement(
    val id: String,
    val nameEn: String, val nameUr: String,
    val descEn: String, val descUr: String,
    val unlocked: Boolean,
    val icon: ImageVector
)



data class RiskHistoryEntry(
    val date: String,
    val score: Int,
    val riskLevel: String,
    val quizType: String
)



data class ProgressState(
    val streak: Int         = 0,
    val longestStreak: Int  = 0,
    val totalDays: Int      = 0,
    val lastCheckIn: String = "",
    val unlockedAchievementIds: Set<String> = emptySet(),
    val riskHistory: List<RiskHistoryEntry> = emptyList(),
    val isLoading: Boolean  = true
)



@Composable
fun ProgressScreen(
    navController: NavController,
    onNavigateBack: () -> Unit = { navController.popBackStack() },
    isEnglish: Boolean = false,
    progressViewModel: ProgressViewModel = viewModel()
) {
    val progress = progressViewModel.progress
    val hasAssessment = GlobalAppState.hasCompletedInitialAssessment
    val riskScore = GlobalAppState.dast10Score

    LaunchedEffect(hasAssessment) { progressViewModel.loadProgress(hasAssessment) }

    ProgressScreenContent(
        navController = navController,
        onNavigateBack = onNavigateBack,
        isEnglish = isEnglish,
        progress = progress,
        hasAssessment = hasAssessment,
        riskScore = riskScore
    )
}

@Composable
fun ProgressScreenContent(
    navController: NavController,
    onNavigateBack: () -> Unit,
    isEnglish: Boolean,
    progress: ProgressState,
    hasAssessment: Boolean,
    riskScore: Int
) {
    val isDark     = isSystemInDarkTheme()
    val hazeState  = remember { HazeState() }

    
    val achievements = remember(progress.streak, progress.unlockedAchievementIds, hasAssessment) {
        buildAchievements(
            streak     = progress.streak,
            unlockedIds= progress.unlockedAchievementIds,
            hasAssessment = hasAssessment
        )
    }

    
    val bgGradient = if (isDark) listOf(
        SaharaStrongGreen.copy(alpha = 0.15f),
        MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
        MaterialTheme.colorScheme.background
    ) else listOf(
        SaharaStrongGreen.copy(alpha = 0.20f),
        SaharaPeach.copy(alpha = 0.08f),
        MaterialTheme.colorScheme.background.copy(alpha = 0.4f)
    )

    val blobMotion = rememberBackdropBlobMotion()

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .background(Brush.verticalGradient(bgGradient))
        ) {
            Box(Modifier.size(450.dp).offset((-120).dp, (-80).dp).primaryBlobMotion(blobMotion)
                .background(Brush.radialGradient(listOf(SaharaWarning.copy(alpha = if (isDark) 0.15f else 0.12f), Color.Transparent))))
            Box(Modifier.size(500.dp).align(Alignment.BottomEnd).offset(150.dp, 100.dp).secondaryBlobMotion(blobMotion)
                .background(Brush.radialGradient(listOf(SaharaStrongGreen.copy(alpha = if (isDark) 0.15f else 0.15f), Color.Transparent))))
        }

        Scaffold(
        bottomBar        = { BottomNav(navController = navController, hazeState = hazeState) },
        containerColor   = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            if (progress.isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = SaharaStrongGreen, strokeWidth = 3.dp)
                }
                return@Box
            }

            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(64.dp))

                
                Column {
                    Text(
                        text = if (isEnglish) "Your Progress" else "Aap Ki Progress",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = SaharaStrongGreen,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = if (isEnglish) "Celebrating every step of your journey" else "Aap ke safar ke har qadam ka jashan",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(28.dp))

                
                RiskScoreSummaryCard(
                    score     = riskScore,
                    hasAssessment = hasAssessment,
                    isEnglish = isEnglish,
                    isDark    = isDark
                )

                Spacer(Modifier.height(20.dp))

                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        modifier    = Modifier.weight(1f),
                        value       = "${progress.streak}",
                        labelEn     = "Streak",
                        labelUr     = "Streak",
                        unit        = if (isEnglish) "days" else "din",
                        icon        = Icons.Default.LocalFireDepartment,
                        tint        = SaharaWarning,
                        isEnglish   = isEnglish,
                        isDark      = isDark
                    )
                    StatCard(
                        modifier    = Modifier.weight(1f),
                        value       = "${progress.longestStreak}",
                        labelEn     = "Best",
                        labelUr     = "Bhetar",
                        unit        = if (isEnglish) "days" else "din",
                        icon        = Icons.Default.EmojiEvents,
                        tint        = Color(0xFFFFD700),
                        isEnglish   = isEnglish,
                        isDark      = isDark
                    )
                    StatCard(
                        modifier    = Modifier.weight(1f),
                        value       = "${progress.totalDays}",
                        labelEn     = "Total",
                        labelUr     = "Kul",
                        unit        = if (isEnglish) "days" else "din",
                        icon        = Icons.Default.CalendarMonth,
                        tint        = SaharaSky,
                        isEnglish   = isEnglish,
                        isDark      = isDark
                    )
                }

                Spacer(Modifier.height(32.dp))

                
                SectionHeader(
                    textEn = "Journey Milestones",
                    textUr = "Safar Ki Manzilain",
                    isEnglish = isEnglish
                )
                Spacer(Modifier.height(14.dp))
                MilestonesCard(streak = progress.streak, isEnglish = isEnglish)

                Spacer(Modifier.height(32.dp))

                
                if (progress.riskHistory.isNotEmpty()) {
                    SectionHeader(
                        textEn = "Risk Score History",
                        textUr = "Risk Score Ka Record",
                        isEnglish = isEnglish
                    )
                    Spacer(Modifier.height(14.dp))
                    RiskHistoryCard(
                        history   = progress.riskHistory,
                        isEnglish = isEnglish,
                        isDark    = isDark
                    )
                    Spacer(Modifier.height(32.dp))
                }

                
                SectionHeader(
                    textEn = "Achievements",
                    textUr = "Kamiyabiyan",
                    isEnglish = isEnglish
                )
                Spacer(Modifier.height(14.dp))

                achievements.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { ach ->
                            AchievementChip(
                                modifier  = Modifier.weight(1f),
                                ach       = ach,
                                isEnglish = isEnglish,
                                isDark    = isDark
                            )
                        }
                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                Spacer(Modifier.height(innerPadding.calculateBottomPadding() + 40.dp))
            }
        }
        }
        HazeBackButton(
            onClick = onNavigateBack,
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 20.dp, top = 16.dp),
        )
    }
}



@Composable
private fun ProgressLockedScreen(
    isEnglish    : Boolean,
    innerPadding : PaddingValues,
    navController: NavController,
    isDark       : Boolean
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(SaharaStrongGreen.copy(alpha = if (isDark) 0.15f else 0.08f), CircleShape)
                    .border(2.dp, SaharaStrongGreen.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = SaharaStrongGreen
                )
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = if (isEnglish) "Unlock Your Journey" else "Apna Safar Unlock Karein",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (isEnglish)
                    "Complete your first assessment to begin tracking your personal progress, " +
                    "streaks, and achievements."
                else
                    "Apni progress tracker, streak, aur achievements shuru karne ke " +
                    "liye pehle assessment mukammal karein.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(Modifier.height(40.dp))

            
            SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isEnglish) "What's Waiting For You:" else "Aap Ke Liye Kya Hai:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = SaharaStrongGreen
                )
                Spacer(Modifier.height(16.dp))
                listOf(
                    "🔥" to if (isEnglish) "Daily Streak Counter" else "Rozana Streak Counter",
                    "📊" to if (isEnglish) "Health Risk Analytics" else "Risk Score Ka Tajzia",
                    "🏆" to if (isEnglish) "18 Unique Achievements" else "18 Munfarid Achievements",
                    "🎯" to if (isEnglish) "Personal Milestones" else "Zati Milestones"
                ).forEach { (emoji, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(emoji, fontSize = 16.sp)
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text  = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(36.dp))

            Button(
                onClick  = { navController.navigate("assessment") },
                colors   = ButtonDefaults.buttonColors(containerColor = SaharaStrongGreen),
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape    = RoundedCornerShape(20.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null,
                    modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (isEnglish) "Start Assessment" else "Assessment Shuru Karein",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}



@Composable
private fun RiskScoreSummaryCard(score: Int, hasAssessment: Boolean, isEnglish: Boolean, isDark: Boolean) {
    if (!hasAssessment) {
        SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(SaharaWarning.copy(alpha = if (isDark) 0.15f else 0.1f), CircleShape)
                        .border(2.dp, SaharaWarning.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Assignment, null, tint = SaharaWarning, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEnglish) "Assessment Due" else "Assessment Baqi Hai",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = SaharaWarning
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isEnglish)
                            "Your milestones stay saved. Complete the assessment to restart this cycle."
                        else
                            "Aapki milestones saved hain. Naya cycle shuru karne ke liye assessment complete karein.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    val (riskLabel, riskColor, emoji) = when (score) {
        0        -> Triple(if (isEnglish) "No Problems"      else "Koi Masla Nahi",   SaharaStrongGreen, "🌿")
        in 1..2  -> Triple(if (isEnglish) "Low Risk"         else "Nicha Khatara",    SaharaSky,         "🔵")
        in 3..5  -> Triple(if (isEnglish) "Moderate Risk"    else "Mutawasit Khatara",SaharaWarning,     "⚠️")
        in 6..8  -> Triple(if (isEnglish) "Substantial Risk" else "Zyada Khatara",    SaharaCoral,       "🔴")
        else     -> Triple(if (isEnglish) "Severe Risk"      else "Bohat Zyada",      Color.Red,         "🚨")
    }

    SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(riskColor.copy(alpha = if (isDark) 0.15f else 0.1f), CircleShape)
                    .border(2.dp, riskColor.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(emoji, fontSize = 22.sp)
                    Text(
                        text       = "$score",
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color      = riskColor
                    )
                }
            }

            Spacer(Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = if (isEnglish) "Current Risk Level" else "Mojooda Risk Level",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text       = riskLabel,
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color      = riskColor
                )
                Spacer(Modifier.height(12.dp))
                
                Box(modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(riskColor.copy(alpha = 0.15f))) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(score.toFloat() / 10f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(riskColor.copy(alpha = 0.7f), riskColor)
                                )
                            )
                    )
                }
            }
        }
    }
}



@Composable
private fun StatCard(
    modifier  : Modifier,
    value     : String,
    labelEn   : String,
    labelUr   : String,
    unit      : String,
    icon      : ImageVector,
    tint      : Color,
    isEnglish : Boolean,
    isDark    : Boolean
) {
    SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(12.dp),
                color = tint.copy(alpha = if (isDark) 0.15f else 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text       = value,
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color      = tint
            )
            Text(
                text      = if (isEnglish) labelEn else labelUr,
                style     = MaterialTheme.typography.labelSmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (unit.isNotBlank()) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
        }
    }
}



@Composable
private fun MilestonesCard(streak: Int, isEnglish: Boolean) {
    val milestones = listOf(1, 7, 14, 30, 90, 365)
    val next       = milestones.firstOrNull { it > streak } ?: milestones.last()
    val prev       = milestones.lastOrNull  { it <= streak } ?: 0
    val progress   = if (next > prev) (streak - prev).toFloat() / (next - prev) else 1f

    SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth()) {
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            milestones.forEach { day ->
                val reached = streak >= day
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (reached) SaharaStrongGreen
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                            )
                            .border(
                                1.dp,
                                if (reached) SaharaStrongGreen.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (reached) {
                            Icon(Icons.Default.Check, null,
                                tint = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Text(
                                text = "$day",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (day == 1) "1d" else if (day == 365) "1y" else "${day}d",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (reached) SaharaStrongGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (reached) FontWeight.ExtraBold else FontWeight.Medium,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(SaharaStrongGreen.copy(alpha = 0.1f))) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(SaharaStrongGreen)
            )
        }

        Spacer(Modifier.height(14.dp))

        val remaining = next - streak
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (streak >= 365) Icons.Default.WorkspacePremium else Icons.Default.Flag,
                contentDescription = null,
                tint = SaharaStrongGreen,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (streak >= 365)
                    if (isEnglish) "Ultimate Milestone Achieved!" else "Aakhri Manzil Hasil!"
                else
                    if (isEnglish) "$remaining days to $next-day goal" else "$next din ke liye $remaining din baqi",
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color      = SaharaStrongGreen
            )
        }
    }
}



@Composable
private fun RiskHistoryCard(
    history   : List<RiskHistoryEntry>,
    isEnglish : Boolean,
    isDark    : Boolean
) {
    SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text       = if (isEnglish) "Recent Assessments" else "Haliya Jaizey",
                style      = MaterialTheme.typography.labelMedium,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            Surface(
                color = SaharaStrongGreen.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isEnglish) "Last 6" else "Aakhri 6",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = SaharaStrongGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(Modifier.height(20.dp))

        
        Row(
            modifier = Modifier.fillMaxWidth().height(100.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            history.forEach { entry ->
                val barFrac  = (entry.score / 10f).coerceIn(0.08f, 1f)
                val barColor = when (entry.riskLevel.lowercase()) {
                    "none"        -> SaharaStrongGreen
                    "low"         -> SaharaSky
                    "moderate"    -> SaharaWarning
                    "substantial" -> SaharaCoral
                    else          -> Color.Red
                }
                Column(
                    modifier            = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(barFrac)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(barColor, barColor.copy(alpha = 0.6f))
                                )
                            )
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text  = "${entry.score}",
                        style = MaterialTheme.typography.labelSmall,
                        color = barColor,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            history.forEach { entry ->
                val shortDate = entry.date.takeLast(5).replace("-", "/") 
                Text(
                    text      = shortDate,
                    modifier  = Modifier.weight(1f),
                    style     = MaterialTheme.typography.labelSmall,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    fontSize  = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        
        if (history.size >= 2) {
            Spacer(Modifier.height(20.dp))
            val first = history.first().score
            val last  = history.last().score
            val (trendIcon, trendText, trendColor) = when {
                last < first -> Triple(Icons.AutoMirrored.Filled.TrendingDown, if (isEnglish) "Improvement detected"  else "Behtari nazar aa rahi hai", SaharaStrongGreen)
                last > first -> Triple(Icons.AutoMirrored.Filled.TrendingUp, if (isEnglish) "Increased risk detected" else "Risk barh raha hai", SaharaCoral)
                else         -> Triple(Icons.AutoMirrored.Filled.TrendingFlat, if (isEnglish) "Risk level is stable"    else "Risk level mustahkam hai", SaharaSky)
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = trendColor.copy(alpha = if (isDark) 0.1f else 0.05f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(trendIcon, null, tint = trendColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = trendText,
                        style = MaterialTheme.typography.labelMedium,
                        color = trendColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}



@Composable
private fun AchievementChip(
    modifier  : Modifier,
    ach       : ProgressAchievement,
    isEnglish : Boolean,
    isDark    : Boolean
) {
    SaharaCard(
        variant  = CardVariant.DASHBOARD_GLASS,
        modifier = modifier.aspectRatio(0.82f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().offset(y = (-4).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (ach.unlocked) SaharaWarning.copy(alpha = if (isDark) 0.2f else 0.12f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                    )
                    .then(
                        if (ach.unlocked) Modifier.border(1.dp, SaharaWarning.copy(alpha = 0.3f), CircleShape)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ach.icon,
                    contentDescription = null,
                    tint   = if (ach.unlocked) SaharaWarning else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text       = if (isEnglish) ach.nameEn else ach.nameUr,
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color      = if (ach.unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                textAlign  = TextAlign.Center,
                maxLines   = 1,
                modifier   = Modifier.basicMarquee()
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text     = if (isEnglish) ach.descEn else ach.descUr,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (ach.unlocked) 0.7f else 0.3f),
                textAlign= TextAlign.Center,
                maxLines = 1,
                fontSize = 8.sp,
                modifier = Modifier.basicMarquee(),
                fontWeight = if (ach.unlocked) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}



@Composable
private fun SectionHeader(textEn: String, textUr: String, isEnglish: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(4.dp, 18.dp).clip(CircleShape).background(SaharaStrongGreen))
        Spacer(Modifier.width(10.dp))
        Text(
            text       = if (isEnglish) textEn else textUr,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color      = MaterialTheme.colorScheme.onSurface,
            letterSpacing = 0.5.sp
        )
    }
}


private fun buildAchievements(
    streak        : Int,
    unlockedIds   : Set<String>,
    hasAssessment : Boolean
): List<ProgressAchievement> = listOf(
    ProgressAchievement("initial_assessment",
        "Initial Assessment", "Pehla Jaeza",
        "Started your journey", "Safar ka aghaz",
        hasAssessment || "initial_assessment" in unlockedIds,
        Icons.Default.AssignmentTurnedIn),
    ProgressAchievement("first_step",
        "First Step", "Pehla Qadam",
        "1 day streak", "1 din streak",
        streak >= 1 || "first_step" in unlockedIds,
        Icons.Default.Star),
    ProgressAchievement("consistency",
        "Consistency", "Mustaqil Mizaji",
        "3 day streak", "3 din streak",
        streak >= 3 || "consistency" in unlockedIds,
        Icons.Default.Timeline),
    ProgressAchievement("week_strong",
        "Week Strong", "Mazboot Hafta",
        "7 day streak", "7 din streak",
        streak >= 7 || "week_strong" in unlockedIds,
        Icons.Default.LocalFireDepartment),
    ProgressAchievement("clean_fortnight",
        "Clean Fortnight", "Do Hafte Saaf",
        "14 day streak", "14 din streak",
        streak >= 14 || "clean_fortnight" in unlockedIds,
        Icons.Default.Bolt),
    ProgressAchievement("month_milestone",
        "Month Milestone", "Mahine Ki Manzil",
        "30 day streak", "30 din streak",
        streak >= 30 || "month_milestone" in unlockedIds,
        Icons.Default.CalendarMonth),
    ProgressAchievement("community_member",
        "Community Member", "Community Rukn",
        "Join a group", "Group join karein",
        "community_member" in unlockedIds,
        Icons.Default.Groups),
    ProgressAchievement("habit_breaker",
        "Habit Breaker", "Adat Chhorne Wala",
        "45 day streak", "45 din streak",
        streak >= 45 || "habit_breaker" in unlockedIds,
        Icons.Default.Psychology),
    ProgressAchievement("quarter_century",
        "Quarter Century", "Quarter Century",
        "60 day streak", "60 din streak",
        streak >= 60 || "quarter_century" in unlockedIds,
        Icons.AutoMirrored.Filled.DirectionsRun),
    ProgressAchievement("resilience",
        "Resilience", "Sabr-o-Istiqlal",
        "90 day streak", "90 din streak",
        streak >= 90 || "resilience" in unlockedIds,
        Icons.Default.Shield),
    ProgressAchievement("half_year",
        "Half Year Hero", "Adha Saal Hero",
        "180 day streak", "180 din streak",
        streak >= 180 || "half_year" in unlockedIds,
        Icons.Default.WorkspacePremium),
    ProgressAchievement("iron_will",
        "Iron Will", "Mazboot Iradah",
        "200 day streak", "200 din streak",
        streak >= 200 || "iron_will" in unlockedIds,
        Icons.Default.SelfImprovement),
    ProgressAchievement("life_saver",
        "Life Saver", "Jan Bachane Wala",
        "Support 5 peers", "5 doston ki madad",
        "life_saver" in unlockedIds,
        Icons.Default.VolunteerActivism),
    ProgressAchievement("mentor",
        "Mentor", "Rehnuma",
        "Lead a session", "Session lead karein",
        "mentor" in unlockedIds,
        Icons.Default.School),
    ProgressAchievement("unstoppable",
        "Unstoppable", "Na-qabil-e-Taskhir",
        "250 day streak", "250 din streak",
        streak >= 250 || "unstoppable" in unlockedIds,
        Icons.Default.RocketLaunch),
    ProgressAchievement("golden_heart",
        "Golden Heart", "Sunehra Dil",
        "Donate to cause", "Donate karein",
        "golden_heart" in unlockedIds,
        Icons.Default.Favorite),
    ProgressAchievement("decade_days",
        "Decade of Days", "Dinon ki Dehai",
        "300 day streak", "300 din streak",
        streak >= 300 || "decade_days" in unlockedIds,
        Icons.Default.AutoAwesome),
    ProgressAchievement("sovereign",
        "Sovereign", "Khud-mukhtar",
        "1 year sober", "1 saal sober",
        streak >= 365 || "sovereign" in unlockedIds,
        Icons.Default.MilitaryTech)
)
