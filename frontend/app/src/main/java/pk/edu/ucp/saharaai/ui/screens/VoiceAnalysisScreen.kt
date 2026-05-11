package pk.edu.ucp.saharaai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.ui.components.ButtonVariant
import pk.edu.ucp.saharaai.ui.components.CardVariant
import pk.edu.ucp.saharaai.ui.components.SaharaButton
import pk.edu.ucp.saharaai.ui.components.SaharaCard
import pk.edu.ucp.saharaai.ui.theme.*

@Composable
fun VoiceAnalysisScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChat: () -> Unit,
    isEnglish: Boolean = false
) {
    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableIntStateOf(0) }
    var hasRecording by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisComplete by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val hazeState = remember { HazeState() }
    val isDark = isSystemInDarkTheme()
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val maxDuration = 60

    val bgGradient = if (isDark) {
        listOf(SaharaStrongGreen.copy(alpha = 0.2f), MaterialTheme.colorScheme.background.copy(alpha = 0.6f), MaterialTheme.colorScheme.background)
    } else {
        listOf(SaharaStrongGreen.copy(alpha = 0.25f), SaharaPeach.copy(alpha = 0.1f), MaterialTheme.colorScheme.background.copy(alpha = 0.2f))
    }
    val blob1Color = SaharaStrongGreen.copy(alpha = if (isDark) 0.25f else 0.15f)
    val blob2Color = SaharaSky.copy(alpha = if (isDark) 0.2f else 0.18f)

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isRecording = true
        }
    }

    LaunchedEffect(isRecording) {
        while (isRecording && (recordingTime < maxDuration)) {
            delay(1000)
            recordingTime++
            if (recordingTime >= maxDuration) {
                isRecording = false
                hasRecording = true
            }
        }
    }

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

    val recordPulseScale by animateFloatAsState(
        targetValue = if (isRecording) 1.4f else 1f,
        animationSpec = infiniteRepeatable(animation = tween(800, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "recordPulse"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .background(Brush.verticalGradient(bgGradient))
        ) {
            Box(
                modifier = Modifier
                    .size(350.dp)
                    .offset(x = (-80).dp, y = (-50).dp)
                    .rotate(blobRotation)
                    .scale(blobScale)
                    .background(Brush.radialGradient(listOf(blob1Color, Color.Transparent)))
            )
            Box(
                modifier = Modifier
                    .size(400.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 100.dp, y = 50.dp)
                    .rotate(-blobRotation)
                    .scale(blobScale)
                    .background(Brush.radialGradient(listOf(blob2Color, Color.Transparent)))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SaharaSky)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = if (isEnglish) "Voice Analysis" else "Awaaz ka Tajziya",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = SaharaSky
                    )
                    Text(
                        text = if (isEnglish) "Discover hidden emotions" else "Apne jazbaat ko samajhein",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            if (!analysisComplete) {
                SaharaCard(variant = CardVariant.GLASS, modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {

                        Text(
                            text = String.format(locale, "%02d:%02d", recordingTime / 60, recordingTime % 60),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isRecording) SaharaCoral else MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = if (isRecording) (if (isEnglish) "Listening..." else "Sun raha hai...") else (if (isEnglish) "Ready" else "Tayyar"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isRecording) SaharaCoral else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(40.dp))

                        if (!hasRecording) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(140.dp)
                            ) {
                                if (isRecording) {
                                    Box(
                                        modifier = Modifier
                                            .size(100.dp)
                                            .scale(recordPulseScale)
                                            .background(SaharaCoral.copy(alpha = 0.3f), CircleShape)
                                            .blur(10.dp)
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (isRecording) {
                                            isRecording = false
                                            if (recordingTime >= 3) hasRecording = true
                                        } else {
                                            val permissionCheckResult = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                                            if (permissionCheckResult == PackageManager.PERMISSION_GRANTED) {
                                                isRecording = true
                                            } else {
                                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(88.dp).clip(CircleShape),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isRecording) SaharaCoral else SaharaSky
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { recordingTime = 0; hasRecording = false },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), CircleShape).size(56.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                                }

                                IconButton(
                                    onClick = { /* Play Audio */ },
                                    modifier = Modifier.background(SaharaSky.copy(alpha = 0.15f), CircleShape).size(72.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = SaharaSky, modifier = Modifier.size(40.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (hasRecording) {
                    SaharaButton(
                        text = if (isAnalyzing) (if (isEnglish) "Analyzing..." else "Tajziya ho raha hai...") else (if (isEnglish) "Analyze Emotions" else "Jazbaat ka Tajziya Karein"),
                        onClick = {
                            if (!isAnalyzing) {
                                coroutineScope.launch {
                                    isAnalyzing = true
                                    delay(2500)
                                    isAnalyzing = false
                                    analysisComplete = true
                                }
                            }
                        },
                        variant = if (isAnalyzing) ButtonVariant.OUTLINE else ButtonVariant.GRADIENT,
                        isFullWidth = true,
                        isEnglish = isEnglish
                    )
                }
            } else {
                SaharaCard(variant = CardVariant.GLASS, modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {

                        Box(
                            modifier = Modifier.size(64.dp).background(SaharaSky.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = SaharaSky, modifier = Modifier.size(32.dp))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (isEnglish) "Analysis Complete" else "Tajziya Mukammal",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SaharaSky.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Text(
                                    text = if (isEnglish) "Primary Emotion Detected" else "Aham Jazba",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (isEnglish) "Hopeful" else "Pur-Umeed",
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SaharaSky
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(shape = RoundedCornerShape(12.dp), color = SaharaGreen.copy(alpha = 0.15f)) {
                                    Text(
                                        text = if (isEnglish) "78% confidence" else "78% yaqeen",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = SaharaGreen,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            SaharaButton(
                                text = if (isEnglish) "Retry" else "Dobara Karein",
                                onClick = { recordingTime = 0; hasRecording = false; analysisComplete = false },
                                variant = ButtonVariant.OUTLINE,
                                modifier = Modifier.weight(1f).height(48.dp),
                                isEnglish = isEnglish
                            )
                            SaharaButton(
                                text = if (isEnglish) "Chat with AI" else "AI Se Baat Karein",
                                onClick = onNavigateToChat,
                                variant = ButtonVariant.GRADIENT,
                                modifier = Modifier.weight(1.2f).height(48.dp),
                                isEnglish = isEnglish
                            )
                        }
                    }
                }
            }
        }
    }
}