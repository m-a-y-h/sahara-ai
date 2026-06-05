package pk.edu.ucp.saharaai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import pk.edu.ucp.saharaai.data.model.VoiceAnalyzeResponse
import pk.edu.ucp.saharaai.data.model.VoiceLevel
import pk.edu.ucp.saharaai.ui.components.BottomNav
import pk.edu.ucp.saharaai.ui.components.ButtonVariant
import pk.edu.ucp.saharaai.ui.components.CardVariant
import pk.edu.ucp.saharaai.ui.components.HazeBackButton
import pk.edu.ucp.saharaai.ui.components.SaharaButton
import pk.edu.ucp.saharaai.ui.components.SaharaCard
import pk.edu.ucp.saharaai.ui.theme.SaharaCoral
import pk.edu.ucp.saharaai.ui.theme.SaharaPeach
import pk.edu.ucp.saharaai.ui.theme.SaharaSky
import pk.edu.ucp.saharaai.ui.theme.SaharaStrongGreen
import pk.edu.ucp.saharaai.ui.theme.SaharaWarning
import pk.edu.ucp.saharaai.utils.ObservePermissionState
import pk.edu.ucp.saharaai.utils.PermissionCopy
import pk.edu.ucp.saharaai.utils.VoiceRecorder
import pk.edu.ucp.saharaai.utils.rememberAppPermissionRequester
import pk.edu.ucp.saharaai.viewmodels.VoiceAnalysisViewModel
import pk.edu.ucp.saharaai.viewmodels.VoiceUiState
import androidx.compose.animation.core.animateFloat
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource


@Composable
fun VoiceAnalysisScreen(
    navController: NavController,
    onNavigateBack: () -> Unit,
    onNavigateToChat: () -> Unit,
    isEnglish: Boolean = false,
    viewModel: VoiceAnalysisViewModel = viewModel(),
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val state by viewModel.state.collectAsState()
    val hazeState = remember { HazeState() }
    val blobMotion = rememberBackdropBlobMotion()
    val bgGradient = if (isDark) {
        listOf(
            SaharaStrongGreen.copy(alpha = 0.20f),
            MaterialTheme.colorScheme.background.copy(alpha = 0.70f),
            MaterialTheme.colorScheme.background,
        )
    } else {
        listOf(
            SaharaStrongGreen.copy(alpha = 0.25f),
            SaharaPeach.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
        )
    }
    val blob1Color = SaharaStrongGreen.copy(alpha = if (isDark) 0.25f else 0.16f)
    val blob2Color = SaharaSky.copy(alpha = if (isDark) 0.20f else 0.18f)

    val audioGranted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val audioPermissionRequester = rememberAppPermissionRequester(
        permission = Manifest.permission.RECORD_AUDIO,
        isEnglish = isEnglish,
        copy = PermissionCopy(
            deniedEn = "Microphone permission was denied.",
            deniedUr = "Microphone ki ijazat nahi di gayi.",
            settingsEn = "Enable microphone permission in App settings to use Voice AI.",
            settingsUr = "Voice AI ke liye App settings mein microphone ki ijazat dein.",
        ),
        onGranted = { audioGranted.value = true },
        onDenied = { audioGranted.value = false },
    )
    ObservePermissionState(audioPermissionRequester) {
        audioGranted.value = it
    }

    LaunchedEffect(Unit) {
        if (!audioGranted.value) audioPermissionRequester.request()
    }

    val recorder = remember { VoiceRecorder(context) }
    DisposableEffect(Unit) { onDispose { recorder.release() } }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .background(Brush.verticalGradient(bgGradient))
        ) {
            Box(
                modifier = Modifier
                    .size(350.dp)
                    .offset(x = (-80).dp, y = (-50).dp)
                    .primaryBlobMotion(blobMotion)
                    .background(Brush.radialGradient(listOf(blob1Color, Color.Transparent)))
            )
            Box(
                modifier = Modifier
                    .size(400.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 100.dp, y = 50.dp)
                    .secondaryBlobMotion(blobMotion)
                    .background(Brush.radialGradient(listOf(blob2Color, Color.Transparent)))
            )
        }

        Scaffold(
            topBar = { VoiceTopBar(isEnglish, hazeState, onNavigateBack) },
            bottomBar = { BottomNav(navController = navController, hazeState = hazeState) },
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { inner ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(inner)
            ) {
                if (!audioGranted.value) {
                    MicPermissionGate(
                        isEnglish = isEnglish,
                        hazeState = hazeState,
                        onRequest = { audioPermissionRequester.request() },
                    )
                    return@Box
                }

                when (val s = state) {
                    VoiceUiState.Idle -> RecorderPane(
                        isEnglish = isEnglish,
                        hazeState = hazeState,
                        isRecording = viewModel.isRecording,
                        elapsedSeconds = viewModel.recordingSeconds,
                        onStart = {
                            recorder.start().fold(
                                onSuccess = { viewModel.onRecordingStarted() },
                                onFailure = { viewModel.onRecordingCancelled(it.message) },
                            )
                        },
                        onStop = {
                            recorder.stop().fold(
                                onSuccess = { clip ->
                                    viewModel.onRecordingStopped(clip.bytes, clip.mimeType)
                                },
                                onFailure = { viewModel.onRecordingCancelled(it.message) },
                            )
                        },
                    )
                    is VoiceUiState.Captured -> ReviewPane(
                        isEnglish = isEnglish,
                        hazeState = hazeState,
                        durationSeconds = s.durationSeconds,
                        onRetake = viewModel::reset,
                        onSend = viewModel::analyze,
                    )
                    VoiceUiState.Analyzing -> AnalyzingPane(isEnglish, hazeState)
                    is VoiceUiState.Result -> ResultPane(
                        isEnglish = isEnglish,
                        hazeState = hazeState,
                        level = s.level,
                        response = s.response,
                        onRetake = viewModel::reset,
                        onMeditation = {  },
                        onCounselor = onNavigateToChat,
                        onEmergency = {  },
                        onDone = onNavigateBack,
                    )
                    is VoiceUiState.Error -> ErrorPane(
                        isEnglish = isEnglish,
                        hazeState = hazeState,
                        message = s.message,
                        reasons = s.reasons,
                        onRetake = viewModel::reset,
                    )
                }
            }
        }
    }
}





@Composable
private fun VoiceTopBar(isEnglish: Boolean, hazeState: HazeState, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HazeBackButton(onClick = onBack, hazeState = hazeState)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (isEnglish) "Voice Analysis" else "Voice Analysis",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = SaharaStrongGreen,
            )
            Text(
                text = if (isEnglish) {
                    "Speak naturally for 5–15 seconds"
                } else {
                    "Normal andaaz mein 5–15 second baat karein"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MicPermissionGate(
    isEnglish: Boolean,
    hazeState: HazeState,
    onRequest: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Mic, null, tint = SaharaStrongGreen, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (isEnglish) "Microphone permission needed" else "Microphone ki ijazat chahiye",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isEnglish) {
                        "Voice analysis stays on your phone until you send it. Only the screening result is saved — never the recording itself."
                    } else {
                        "Recording aapke phone par hi rehti hai jab tak aap send nahi karte. Sirf screening result save hota hai — recording nahi."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                SaharaButton(
                    text = if (isEnglish) "Allow microphone" else "Microphone ijazat dein",
                    onClick = onRequest,
                    variant = ButtonVariant.GRADIENT,
                    isFullWidth = true,
                )
            }
        }
    }
}

@Composable
private fun RecorderPane(
    isEnglish: Boolean,
    hazeState: HazeState,
    isRecording: Boolean,
    elapsedSeconds: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val pulseScale = rememberFrameOscillation(
        startValue = 1f,
        endValue = if (isRecording) 1.12f else 1f,
        halfCycleMillis = 900
    )

    Box(
        Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isRecording) formatSeconds(elapsedSeconds)
                           else if (isEnglish) "Tap to record" else "Record karne ke liye tap karein",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light),
                    color = if (isRecording) SaharaCoral else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(24.dp))

                Surface(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .clickable { if (isRecording) onStop() else onStart() },
                    shape = CircleShape,
                    color = if (isRecording) SaharaCoral else SaharaStrongGreen,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    text = if (isRecording) {
                        if (isEnglish) "Tap again to stop. Max 60 seconds." else "Rokne ke liye dobara tap karein. Max 60 second."
                    } else {
                        if (isEnglish) {
                            "Find a quiet place and speak about how you're feeling right now."
                        } else {
                            "Aaram se baith ke batayein aap is waqt kaisa feel kar rahe hain."
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReviewPane(
    isEnglish: Boolean,
    hazeState: HazeState,
    durationSeconds: Int,
    onRetake: () -> Unit,
    onSend: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = CircleShape,
                    color = SaharaSky.copy(alpha = 0.18f),
                    modifier = Modifier.size(120.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Mic, null, tint = SaharaSky, modifier = Modifier.size(56.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (isEnglish) "Recording ready" else "Recording tayyar",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = formatSeconds(durationSeconds),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
                    color = SaharaSky,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isEnglish) {
                        "Sending will analyse the audio for a screening signal. The clip is not stored."
                    } else {
                        "Send karne par audio ka analysis hoga. Recording save nahi hoti."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SaharaButton(
                        text = if (isEnglish) "Retake" else "Dobara",
                        onClick = onRetake,
                        variant = ButtonVariant.OUTLINE,
                        isFullWidth = true,
                        modifier = Modifier.weight(1f),
                    )
                    SaharaButton(
                        text = if (isEnglish) "Send" else "Bhejein",
                        onClick = onSend,
                        variant = ButtonVariant.GRADIENT,
                        isFullWidth = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalyzingPane(
    isEnglish: Boolean,
    hazeState: HazeState,
) {
    Box(
        Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = SaharaStrongGreen, strokeWidth = 4.dp)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (isEnglish) "Listening…" else "Sun raha hoon…",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isEnglish) {
                        "Screening signal only — never a diagnosis. The clip stays on your phone."
                    } else {
                        "Sirf screening signal hai, diagnosis nahi. Recording phone par hi rehti hai."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = SaharaStrongGreen)
            }
        }
    }
}

@Composable
private fun ResultPane(
    isEnglish: Boolean,
    hazeState: HazeState,
    level: VoiceLevel,
    response: VoiceAnalyzeResponse,
    onRetake: () -> Unit,
    onMeditation: () -> Unit,
    onCounselor: () -> Unit,
    onEmergency: () -> Unit,
    onDone: () -> Unit,
) {
    val accent = when (level) {
        VoiceLevel.NEUTRAL   -> SaharaStrongGreen
        VoiceLevel.ELEVATED  -> SaharaPeach
        VoiceLevel.HIGH      -> SaharaCoral
        VoiceLevel.UNCERTAIN -> SaharaSky
        VoiceLevel.UNKNOWN   -> SaharaWarning
    }
    val icon: ImageVector = when (level) {
        VoiceLevel.NEUTRAL   -> Icons.Filled.SentimentVerySatisfied
        VoiceLevel.ELEVATED  -> Icons.Filled.SelfImprovement
        VoiceLevel.HIGH      -> Icons.Filled.LocalHospital
        VoiceLevel.UNCERTAIN -> Icons.Filled.Refresh
        VoiceLevel.UNKNOWN   -> Icons.Filled.Warning
    }
    val title = when (level) {
        VoiceLevel.NEUTRAL   -> if (isEnglish) "Your voice sounds steady" else "Awaz steady lag rahi hai"
        VoiceLevel.ELEVATED  -> if (isEnglish) "We hear some tension" else "Thori tension sun rahay hain"
        VoiceLevel.HIGH      -> if (isEnglish) "Strong distress in your voice" else "Awaz mein distress tez hai"
        VoiceLevel.UNCERTAIN -> if (isEnglish) "Couldn't read the clip clearly" else "Clip saaf nahi samjhi"
        VoiceLevel.UNKNOWN   -> if (isEnglish) "Result unavailable" else "Result mojood nahi"
    }
    val subtitle = when (level) {
        VoiceLevel.NEUTRAL   -> if (isEnglish) "Keep checking in with yourself today." else "Apne aap se aaj baat karte rahein."
        VoiceLevel.ELEVATED  -> if (isEnglish) "A short breathing exercise can settle things." else "Saans ki choti mashq asar karegi."
        VoiceLevel.HIGH      -> if (isEnglish) "Talking to someone helps. Sahara counselors are available, or call 1122/115 in an emergency."
                                else "Baat karna madad karta hai. Sahara counselor available hain, ya emergency mein 1122/115 call karein."
        VoiceLevel.UNCERTAIN -> if (isEnglish) "Try a quieter spot and a slightly longer clip." else "Khamosh jagah aur thora lamba clip try karein."
        VoiceLevel.UNKNOWN   -> if (isEnglish) "Please try again." else "Dobara try karein."
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = accent.copy(alpha = 0.15f)) {
                    Icon(icon, null, tint = accent, modifier = Modifier.padding(10.dp).size(28.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = accent,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(16.dp))
        ScreeningBreakdown(response, isEnglish, hazeState)

        Spacer(Modifier.height(20.dp))
        when (level) {
            VoiceLevel.HIGH -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SaharaButton(
                    text = if (isEnglish) "Counselor" else "Counselor",
                    onClick = onCounselor,
                    variant = ButtonVariant.OUTLINE,
                    isFullWidth = true,
                    modifier = Modifier.weight(1f),
                )
                SaharaButton(
                    text = if (isEnglish) "Emergency" else "Emergency",
                    onClick = onEmergency,
                    variant = ButtonVariant.CORAL,
                    isFullWidth = true,
                    modifier = Modifier.weight(1f),
                )
            }
            VoiceLevel.ELEVATED -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SaharaButton(
                    text = if (isEnglish) "Try breathing" else "Saans ki mashq",
                    onClick = onMeditation,
                    variant = ButtonVariant.GRADIENT,
                    isFullWidth = true,
                    modifier = Modifier.weight(1f),
                )
                SaharaButton(
                    text = if (isEnglish) "Talk to a counselor" else "Counselor se baat",
                    onClick = onCounselor,
                    variant = ButtonVariant.OUTLINE,
                    isFullWidth = true,
                    modifier = Modifier.weight(1f),
                )
            }
            VoiceLevel.UNCERTAIN -> SaharaButton(
                text = if (isEnglish) "Retake" else "Dobara",
                onClick = onRetake,
                variant = ButtonVariant.GRADIENT,
                isFullWidth = true,
            )
            else -> SaharaButton(
                text = if (isEnglish) "Done" else "Theek hai",
                onClick = onDone,
                variant = ButtonVariant.GRADIENT,
                isFullWidth = true,
            )
        }

        response.modelVersion?.let {
            Text(
                text = "Model: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ScreeningBreakdown(
    response: VoiceAnalyzeResponse,
    isEnglish: Boolean,
    hazeState: HazeState,
) {
    val probs = response.screening?.screeningProbs ?: return
    if (probs.isEmpty()) return
    val ordered = listOf("neutral", "stress", "sadness", "fear").mapNotNull { key ->
        probs[key]?.let { key to it }
    }
    SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (isEnglish) "Screening breakdown" else "Screening breakdown",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(8.dp))
        ordered.forEach { (label, value) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                LinearProgressIndicator(
                    progress = { value.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(2f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = colorForScreeningLabel(label),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${(value * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ErrorPane(
    isEnglish: Boolean,
    hazeState: HazeState,
    message: String,
    reasons: List<String>,
    onRetake: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        val displayMessage = friendlyVoiceErrorMessage(message, isEnglish)
        SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.MicOff, null, tint = SaharaCoral, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = displayMessage,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            if (reasons.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    LazyColumn(contentPadding = PaddingValues(12.dp)) {
                        items(reasons) { reason ->
                            Text(
                                text = "• $reason",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            SaharaButton(
                text = if (isEnglish) "Retake" else "Dobara try karein",
                onClick = onRetake,
                variant = ButtonVariant.GRADIENT,
                isFullWidth = true,
            )
        }
    }
}





private fun formatSeconds(s: Int): String {
    val mm = s / 60
    val ss = s % 60
    return "%02d:%02d".format(mm, ss)
}

private fun friendlyVoiceErrorMessage(raw: String, isEnglish: Boolean): String {
    val lower = raw.lowercase()
    return when {
        lower.contains("too short") -> if (isEnglish) {
            "Recording was too short. Please try again with a longer clip."
        } else {
            "Recording bohat choti thi. Thora lamba clip dobara record karein."
        }
        lower.contains("timeout") || lower.contains("timed out") -> if (isEnglish) {
            "Voice service took too long. Please try again."
        } else {
            "Voice analysis mein waqt zyada lag gaya. Dobara try karein."
        }
        lower.contains("not configured") -> if (isEnglish) {
            "Voice analysis URL is not configured."
        } else {
            "Voice analysis URL set nahi hai."
        }
        lower.contains("http 5") ||
            lower.contains("inference failed") ||
            lower.contains("remoteerror") ||
            lower.contains("parent input") -> if (isEnglish) {
                "Voice service could not analyse the clip right now. Please try again shortly."
            } else {
                "Voice analysis abhi available nahi hai. Thori der baad dobara try karein."
            }
        raw.isBlank() -> if (isEnglish) {
            "Sahara Voice couldn't reach the server."
        } else {
            "Sahara Voice server tak nahi pohanch saka."
        }
        else -> raw
    }
}

private fun colorForScreeningLabel(label: String): Color = when (label.lowercase()) {
    "neutral" -> SaharaStrongGreen
    "stress"  -> SaharaPeach
    "sadness" -> SaharaSky
    "fear"    -> SaharaCoral
    else      -> SaharaWarning
}
