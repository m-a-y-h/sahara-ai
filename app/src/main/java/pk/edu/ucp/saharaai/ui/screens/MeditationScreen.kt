package pk.edu.ucp.saharaai.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
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
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.HazeColorEffect
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.service.rememberMeditationMusicController
import pk.edu.ucp.saharaai.ui.components.BottomNav
import pk.edu.ucp.saharaai.ui.components.HazeBackButton
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.utils.MeditationAudioEngine
import pk.edu.ucp.saharaai.viewmodels.MeditationViewModel



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
    val secondHold: Long = 0L,
    val accentColor: Color = SaharaSky
)

data class GuidedSession(
    val titleEn: String,
    val titleUr: String,
    val descEn: String,
    val descUr: String,
    val duration: String,
    val icon: ImageVector = Icons.Default.MusicNote,
    val accentColor: Color = SaharaLavender,
    val audioFile: String? = null      // remote/cached file name, e.g. "meditation_relaxing.mp3"
)

private fun triggerBeatingVibration(context: Context, patternName: String) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val pulse = when {
            patternName.contains("4-7-8") -> 70L
            patternName.contains("Box") -> 40L
            else -> 90L
        }
        val timings = LongArray(14) { pulse }.also { it[0] = 0L }
        val amplitudes = IntArray(14) { if (it % 2 == 0) 0 else VibrationEffect.DEFAULT_AMPLITUDE }
        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    } else {
        @Suppress("DEPRECATION") vibrator.vibrate(800)
    }
}



@Composable
fun MeditationScreen(
    navController: NavController,
    onNavigateBack: () -> Unit = { navController.popBackStack() },
    isEnglish: Boolean = false,
    meditationViewModel: MeditationViewModel = viewModel()
) {
    val context      = LocalContext.current
    val isDark       = isSystemInDarkTheme()
    val hazeState    = remember { HazeState() }
    val scrollState  = rememberScrollState()
    val scope        = rememberCoroutineScope()

    
    val audioEngine = remember { MeditationAudioEngine() }
    DisposableEffect(Unit) { onDispose { audioEngine.stop() } }

    
    val musicCtrl = rememberMeditationMusicController()

    
    val exercises = remember {
        listOf(
            BreathingExercise(
                "4-7-8 Breathing", "4-7-8 Saans ki Mashq",
                "For deep relaxation", "Gehray sukoon ke liye",
                "5 min", Icons.Default.Air, 4_000L, 7_000L, 8_000L,
                accentColor = SaharaSky
            ),
            BreathingExercise(
                "Box Breathing", "Box Breathing",
                "For focus & stress relief", "Tawajo aur stress ke liye",
                "3 min", Icons.Default.Air, 4_000L, 4_000L, 4_000L, 4_000L,
                accentColor = SaharaLavender
            ),
            BreathingExercise(
                "4-4-8 Breathing", "4-4-8 Saans ki Mashq",
                "Lowers heart rate fast", "Dil ki dharkan tezi se kam kare",
                "5 min", Icons.Default.Air, 4_000L, 4_000L, 8_000L,
                accentColor = SaharaStrongGreen
            ),
            BreathingExercise(
                "Diaphragmatic Breathing", "Diaphragm Saans",
                "Reduces stress & anxiety", "Stress aur anxiety kam kare",
                "6 min", Icons.Default.SelfImprovement, 5_000L, 0L, 5_000L,
                accentColor = SaharaPeach
            )
        )
    }

    
    val sessions = remember {
        listOf(
            GuidedSession(
                "Relaxing Timer", "Sukoon Bakhsh Timer",
                "Anxiety release · sleep · soft ambient",
                "Sukoon, neend aur soft ambient",
                "10 min", Icons.Default.MusicNote, SaharaSky,
                "meditation_relaxing.mp3"
            ),
            GuidedSession(
                "Pure Waves", "Saf Lahrein",
                "Pure meditation sound waves",
                "Saf meditation ki awazein",
                "10 min", Icons.Default.MusicNote, SaharaLavender,
                "meditation_pure_waves.mp3"
            ),
            GuidedSession(
                "Positive Energy", "مثبت توانائی",
                "Find inner peace within 10 minutes",
                "10 minute mein sukoon paayein",
                "10 min", Icons.Default.MusicNote, SaharaPeach,
                "meditation_positive_energy.mp3"
            ),
            GuidedSession(
                "Deep Focus", "Gehri Tawajo",
                "Brain power · focus · concentration",
                "Concentration aur dimagh ki taqat",
                "10 min", Icons.Default.MusicNote, SaharaStrongGreen,
                "meditation_deep_focus.mp3"
            )
        )
    }

    
    val songPlaylist = remember(sessions) {
        sessions.mapNotNull { s -> s.audioFile?.let { it to s.titleEn } }
    }

    
    val selectedTitle = meditationViewModel.selectedMeditation
    val playingTitle = meditationViewModel.playingMeditation
    val breathTextEn = meditationViewModel.breathTextEn
    val breathTextUr = meditationViewModel.breathTextUr

    val selectedColor = remember(selectedTitle) {
        exercises.find { it.titleEn == selectedTitle }?.accentColor ?: SaharaSky
    }

    
    val bgGradient = if (isDark)
        listOf(selectedColor.copy(.18f), MaterialTheme.colorScheme.background.copy(.7f), MaterialTheme.colorScheme.background)
    else
        listOf(selectedColor.copy(.22f), SaharaLavender.copy(.12f), MaterialTheme.colorScheme.background.copy(.3f))

    val blobMotion = rememberBackdropBlobMotion()
    val idlePulse = rememberFrameOscillation(1f, 1.08f, 2_500)
    val activePuls = rememberFrameOscillation(1f, 1.55f, 4_000)
    val auraScale  = if (playingTitle != null) activePuls else idlePulse

    
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(Brush.verticalGradient(bgGradient))
        ) {
            Box(Modifier.size(360.dp).offset((-80).dp, (-50).dp).primaryBlobMotion(blobMotion)
                .background(Brush.radialGradient(listOf(selectedColor.copy(.18f), Color.Transparent))))
            Box(Modifier.size(420.dp).align(Alignment.BottomEnd).offset(100.dp, 50.dp).secondaryBlobMotion(blobMotion)
                .background(Brush.radialGradient(listOf(SaharaLavender.copy(.14f), Color.Transparent))))
        }

        Scaffold(
            bottomBar = { BottomNav(navController, hazeState) },
            containerColor = Color.Transparent
        ) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).verticalScroll(scrollState)) {

                Spacer(Modifier.height(24.dp))

                
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    HazeBackButton(onClick = onNavigateBack, hazeState = hazeState, tint = selectedColor)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            if (isEnglish) "Meditation" else "Muraqba",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = selectedColor
                        )
                        Text(
                            if (isEnglish) "Find your inner peace" else "Apna sukoon paayen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                
                Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(130.dp).scale(auraScale)
                        .background(Brush.radialGradient(listOf(selectedColor.copy(.30f), Color.Transparent))))
                    Box(
                        modifier = Modifier.size(150.dp).clip(CircleShape)
                            .background(Brush.radialGradient(listOf(selectedColor.copy(.7f), selectedColor)))
                            .clickable {
                                if (playingTitle != null) {
                                    meditationViewModel.stopMeditation()
                                    audioEngine.stop()
                                } else {
                                    exercises.find { it.titleEn == selectedTitle }?.let { ex ->
                                        triggerBeatingVibration(context, ex.titleEn)
                                        meditationViewModel.toggleMeditation(
                                            ex.titleEn, ex.inhale, ex.hold, ex.exhale, ex.secondHold
                                        )
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(if (isEnglish) breathTextEn else breathTextUr, label = "bt") { text ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text, color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                                if (playingTitle == null) {
                                    Text(
                                        selectedTitle, color = Color.White.copy(.65f),
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (playingTitle != null) {
                        Row(
                            Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(Modifier.size(8.dp).background(selectedColor, CircleShape))
                            Text(
                                playingTitle ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                
                
                
                Text(
                    if (isEnglish) "Breathing Exercises" else "Saans ki Mashqein",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Text(
                    if (isEnglish) "Tap ▶ to start guided breathing with animation"
                    else           "Animation ke sath guided breathing ke liye ▶ dabayein",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(exercises) { ex ->
                        val isSel  = selectedTitle == ex.titleEn
                        val isPlay = playingTitle  == ex.titleEn

                        Box(
                            modifier = Modifier.width(220.dp).height(162.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .hazeEffect(hazeState) {
                                    blurEffect { blurRadius = 25.dp; colorEffects = listOf(HazeColorEffect.tint(Color.White.copy(.07f))) }
                                }
                                .border(
                                    if (isSel) 2.dp else 0.5.dp,
                                    if (isSel) ex.accentColor else MaterialTheme.colorScheme.outlineVariant.copy(.3f),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { meditationViewModel.selectMeditation(ex.titleEn) }
                                .padding(14.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(ex.icon, null, tint = ex.accentColor, modifier = Modifier.size(28.dp))
                                    if (isPlay) {
                                        Spacer(Modifier.width(8.dp))
                                        Box(Modifier.size(8.dp).background(ex.accentColor, CircleShape))
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (isEnglish) ex.titleEn else ex.titleUr,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    if (isEnglish) ex.descEn else ex.descUr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.weight(1f))
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                    Text(ex.duration, style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        buildString {
                                            append("${ex.inhale / 1000}s")
                                            if (ex.hold > 0) append("-${ex.hold / 1000}s")
                                            append("-${ex.exhale / 1000}s")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ex.accentColor
                                    )
                                    IconButton(
                                        onClick = {
                                            meditationViewModel.selectMeditation(ex.titleEn)
                                            if (isPlay) {
                                                meditationViewModel.stopMeditation()
                                                audioEngine.stop()
                                            } else {
                                                triggerBeatingVibration(context, ex.titleEn)
                                                meditationViewModel.toggleMeditation(
                                                    ex.titleEn, ex.inhale, ex.hold, ex.exhale, ex.secondHold
                                                )
                                                scope.launch { scrollState.animateScrollTo(0) }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            if (isPlay) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            null, tint = ex.accentColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                
                
                
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.MusicNote, null, tint = SaharaLavender, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isEnglish) "Guided Sessions" else "Guided Sessions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    if (isEnglish) "Continues playing after you close the app"
                    else           "App band karne ke baad bhi bajta rahega",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
                )

                Column(
                    Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    sessions.forEach { session ->
                        val file         = session.audioFile!!
                        val isThisPlaying  = musicCtrl.isPlaying && musicCtrl.currentTitle == session.titleEn
                        val isThisSelected = musicCtrl.currentTitle == session.titleEn

                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .hazeEffect(hazeState) {
                                    blurEffect { blurRadius = 25.dp; colorEffects = listOf(HazeColorEffect.tint(Color.White.copy(.07f))) }
                                }
                                .border(
                                    if (isThisSelected) 2.dp else 0.5.dp,
                                    if (isThisSelected) session.accentColor
                                    else MaterialTheme.colorScheme.outlineVariant.copy(.3f),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { musicCtrl.play(file, session.titleEn, songPlaylist) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(44.dp)
                                    .background(session.accentColor.copy(if (isDark) .2f else .12f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(session.icon, null, tint = session.accentColor, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (isEnglish) session.titleEn else session.titleUr,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (isThisPlaying) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(Modifier.size(7.dp).background(session.accentColor, CircleShape))
                                    }
                                }
                                Text(
                                    if (isEnglish) session.descEn else session.descUr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    session.duration,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = session.accentColor.copy(.8f)
                                )
                            }
                            IconButton(
                                onClick = { musicCtrl.play(file, session.titleEn, songPlaylist) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    null, tint = session.accentColor
                                )
                            }
                        }
                    }

                    
                    if (musicCtrl.currentTitle.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        val activeSession = sessions.find { it.titleEn == musicCtrl.currentTitle }
                        val barColor      = activeSession?.accentColor ?: SaharaLavender

                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(barColor.copy(if (isDark) .18f else .10f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (isEnglish) "Now Playing: ${musicCtrl.currentTitle}"
                                else           "Ab Chal Raha Hai: ${musicCtrl.currentTitle}",
                                style = MaterialTheme.typography.labelMedium,
                                color = barColor,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f).padding(start = 8.dp)
                            )
                            Row {
                                IconButton(onClick = musicCtrl::prev, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.SkipPrevious, null, tint = barColor)
                                }
                                IconButton(
                                    onClick = { if (musicCtrl.isPlaying) musicCtrl.pause() else musicCtrl.resume() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        if (musicCtrl.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        null, tint = barColor
                                    )
                                }
                                IconButton(onClick = musicCtrl::next, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.SkipNext, null, tint = barColor)
                                }
                                IconButton(onClick = musicCtrl::stop, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Stop, null, tint = SaharaCoral)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(100.dp))
                }
            }
        }
    }
}
