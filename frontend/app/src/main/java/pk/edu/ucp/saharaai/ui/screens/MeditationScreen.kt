package pk.edu.ucp.saharaai.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.HazeColorEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.ui.components.BottomNav
import pk.edu.ucp.saharaai.ui.theme.*

data class BreathingExercise(
    val titleEn: String,
    val titleUr: String,
    val descEn: String,
    val descUr: String,
    val duration: String,
    val icon: ImageVector,
    val inhale: Long,
    val hold: Long,
    val exhale: Long,
    val secondHold: Long = 0L
)

data class GuidedSession(val titleEn: String, val titleUr: String, val descEn: String, val descUr: String, val duration: String, val plays: String)

fun triggerBeatingVibration(context: Context, patternName: String) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val pulseInterval = when (patternName) {
            "4-7-8 Breathing" -> 70L
            "Box Breathing" -> 40L
            else -> 100L
        }

        val timings = LongArray(14) { pulseInterval }
        timings[0] = 0L
        val amplitudes = IntArray(14) { if (it % 2 == 0) 0 else VibrationEffect.DEFAULT_AMPLITUDE }

        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(1000)
    }
}

@Composable
fun MeditationScreen(
    navController: NavController,
    onNavigateBack: () -> Unit = { navController.popBackStack() },
    isEnglish: Boolean = false
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val hazeState = remember { HazeState() }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val exercises = remember {
        listOf(
            BreathingExercise(
                "4-7-8 Breathing", "4-7-8 Saans ki Mashq",
                "For deep relaxation", "Gehray sukoon ke liye",
                "5 min", Icons.Default.Air,
                4000L, 7000L, 8000L
            ),
            BreathingExercise(
                "Box Breathing", "Box Breathing",
                "For focus and stress", "Tawajo aur stress ke liye",
                "3 min", Icons.Default.Air,
                4000L, 4000L, 4000L, 4000L
            ),
            BreathingExercise(
                "4-4-8 Breathing", "4-4-8 Saans ki Mashq",
                "To lower heart rate", "Dil ki dharkan ke liye",
                "5 min", Icons.Default.Air,
                4000L, 4000L, 8000L
            )
        )
    }

    val sessions = remember {
        listOf(
            GuidedSession("Morning Calm", "Subah ka Sukoon", "Start your day right", "Din ki achi shuruwat ke liye", "10 min", "1.2k"),
            GuidedSession("Sleep Deeply", "Gehri Neend", "Drift into restful sleep", "Pursukoon neend ke liye", "20 min", "3.4k"),
            GuidedSession("Anxiety Relief", "Fikr se Azaadi", "Let go of your worries", "Pareshaniyon ko bhula dein", "15 min", "2.8k")
        )
    }

    var selectedMeditation by remember { mutableStateOf("4-7-8 Breathing") }
    var playingMeditation by remember { mutableStateOf<String?>(null) }
    var breathTextEn by remember { mutableStateOf("Tap to Start") }
    var breathTextUr by remember { mutableStateOf("Shuru Karein") }

    val bgGradient = if (isDark) {
        listOf(SaharaSky.copy(alpha = 0.2f), MaterialTheme.colorScheme.background.copy(alpha = 0.6f), MaterialTheme.colorScheme.background)
    } else {
        listOf(SaharaSky.copy(alpha = 0.25f), SaharaLavender.copy(alpha = 0.15f), MaterialTheme.colorScheme.background.copy(alpha = 0.2f))
    }

    val infiniteTransition = rememberInfiniteTransition(label = "animations")
    val blobRotation by infiniteTransition.animateFloat(initialValue = -10f, targetValue = 10f, animationSpec = infiniteRepeatable(tween(8000, easing = EaseInOutSine), RepeatMode.Reverse))
    val blobScale by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 1.15f, animationSpec = infiniteRepeatable(tween(6000, easing = EaseInOutSine), RepeatMode.Reverse))

    val idlePulse by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 1.08f, animationSpec = infiniteRepeatable(tween(2500, easing = EaseInOutSine), RepeatMode.Reverse))
    val activePulse by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 1.5f, animationSpec = infiniteRepeatable(tween(4000, easing = EaseInOutSine), RepeatMode.Reverse))
    val auraScale = if (playingMeditation != null) activePulse else idlePulse

    LaunchedEffect(playingMeditation) {
        if (playingMeditation != null) {
            val currentExercise = exercises.find { it.titleEn == playingMeditation }

            while (playingMeditation != null) {
                breathTextEn = "Breathe In..."; breathTextUr = "Saans Andar..."
                delay(currentExercise?.inhale ?: 4000L)

                if (playingMeditation == null) break
                breathTextEn = "Hold..."; breathTextUr = "Rokiye..."
                delay(currentExercise?.hold ?: 2000L)

                if (playingMeditation == null) break
                breathTextEn = "Breathe Out..."; breathTextUr = "Saans Bahar..."
                delay(currentExercise?.exhale ?: 4000L)

                if (currentExercise != null && currentExercise.secondHold > 0L) {
                    if (playingMeditation == null) break
                    breathTextEn = "Hold..."; breathTextUr = "Rokiye..."
                    delay(currentExercise.secondHold)
                }
            }
        } else {
            breathTextEn = "Tap to Start"
            breathTextUr = "Shuru Karein"
        }
    }

    Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState).background(Brush.verticalGradient(bgGradient))) {
        Box(
            modifier = Modifier.size(350.dp).offset(x = (-80).dp, y = (-50).dp).rotate(blobRotation)
                .scale(blobScale).background(
                Brush.radialGradient(
                    listOf(
                        SaharaSky.copy(alpha = 0.2f),
                        Color.Transparent
                    )
                )
            )
        )
        Box(
            modifier = Modifier.size(400.dp).align(Alignment.BottomEnd)
                .offset(x = 100.dp, y = 50.dp).rotate(-blobRotation).scale(blobScale).background(
                Brush.radialGradient(
                    listOf(
                        SaharaLavender.copy(alpha = 0.15f),
                        Color.Transparent
                    )
                )
            )
        )

        Scaffold(
            bottomBar = { BottomNav(navController = navController, hazeState = hazeState) },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(scrollState)
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            CircleShape
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = SaharaSky
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            if (isEnglish) "Meditation" else "Muraqba",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = SaharaSky
                        )
                        Text(
                            if (isEnglish) "Find your inner peace" else "Sukoon paayen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier.fillMaxWidth().height(250.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.size(120.dp).scale(auraScale).background(
                            Brush.radialGradient(
                                listOf(
                                    SaharaSky.copy(alpha = 0.35f),
                                    Color.Transparent
                                )
                            )
                        )
                    )
                    Box(
                        modifier = Modifier.size(140.dp).clip(CircleShape).background(
                            Brush.radialGradient(
                                listOf(
                                    SaharaSky.copy(alpha = 0.6f),
                                    SaharaSky
                                )
                            )
                        )
                            .clickable {
                                if (playingMeditation != null) playingMeditation = null
                                else {
                                    playingMeditation = selectedMeditation
                                    triggerBeatingVibration(context, selectedMeditation)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val currentText = if (isEnglish) breathTextEn else breathTextUr
                        AnimatedContent(targetState = currentText, label = "") { text ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                if (playingMeditation == null) {
                                    Text(
                                        selectedMeditation,
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    if (isEnglish) "Breathing Exercises" else "Saans ki Mashqein",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(exercises) { exercise ->
                        val isSelected = selectedMeditation == exercise.titleEn
                        val isPlaying = playingMeditation == exercise.titleEn

                        Box(
                            modifier = Modifier
                                .width(240.dp).height(160.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .hazeEffect(state = hazeState) {
                                    blurEffect {
                                        blurRadius = 25.dp
                                        colorEffects =
                                            listOf(HazeColorEffect.tint(Color.White.copy(alpha = 0.08f)))
                                    }
                                }
                                .border(
                                    if (isSelected) 2.dp else 0.dp,
                                    if (isSelected) SaharaSky else Color.Transparent,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { selectedMeditation = exercise.titleEn }
                                .padding(16.dp)
                        ) {
                            Column {
                                Icon(
                                    exercise.icon,
                                    null,
                                    tint = SaharaSky,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    if (isEnglish) exercise.titleEn else exercise.titleUr,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (isEnglish) exercise.descEn else exercise.descUr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    Arrangement.SpaceBetween,
                                    Alignment.CenterVertically
                                ) {
                                    Text(
                                        exercise.duration,
                                        style = MaterialTheme.typography.labelSmall
                                    )

                                    IconButton(onClick = {
                                        selectedMeditation = exercise.titleEn
                                        if (isPlaying) {
                                            playingMeditation = null
                                        } else {
                                            playingMeditation = exercise.titleEn
                                            triggerBeatingVibration(
                                                context,
                                                exercise.titleEn
                                            )
                                            coroutineScope.launch { scrollState.animateScrollTo(0) }
                                        }
                                    }) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = SaharaSky
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    if (isEnglish) "Guided Sessions" else "Guided Sessions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    sessions.forEach { session ->
                        val isSelected = selectedMeditation == session.titleEn
                        val isPlaying = playingMeditation == session.titleEn

                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .hazeEffect(state = hazeState) {
                                    blurEffect {
                                        blurRadius = 25.dp
                                        colorEffects =
                                            listOf(HazeColorEffect.tint(Color.White.copy(alpha = 0.08f)))
                                    }
                                }
                                .border(
                                    if (isSelected) 2.dp else 0.dp,
                                    if (isSelected) SaharaLavender else Color.Transparent,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { selectedMeditation = session.titleEn }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Headphones,
                                null,
                                tint = SaharaLavender,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (isEnglish) session.titleEn else session.titleUr,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${session.duration} • ${session.plays} plays",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            IconButton(onClick = {
                                selectedMeditation = session.titleEn
                                if (isPlaying) {
                                    playingMeditation = null
                                } else {
                                    playingMeditation = session.titleEn
                                    triggerBeatingVibration(context, session.titleEn)
                                    coroutineScope.launch { scrollState.animateScrollTo(0) }
                                }
                            }) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    null,
                                    tint = SaharaLavender
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(100.dp))
                }
            }
        }
    }
}