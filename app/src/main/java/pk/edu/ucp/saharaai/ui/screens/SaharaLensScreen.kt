package pk.edu.ucp.saharaai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import pk.edu.ucp.saharaai.data.model.LensLevel
import pk.edu.ucp.saharaai.lens.FaceAntiSpoofingClassifier
import pk.edu.ucp.saharaai.lens.FaceScannerAnalyzer
import pk.edu.ucp.saharaai.lens.LensValidation
import pk.edu.ucp.saharaai.lens.RectFractional
import pk.edu.ucp.saharaai.data.model.LensScanResponse
import pk.edu.ucp.saharaai.ui.components.ButtonVariant
import pk.edu.ucp.saharaai.ui.components.SaharaButton
import pk.edu.ucp.saharaai.ui.components.HazeBackButton
import pk.edu.ucp.saharaai.ui.theme.SaharaCoral
import pk.edu.ucp.saharaai.ui.theme.SaharaPeach
import pk.edu.ucp.saharaai.ui.theme.SaharaSky
import pk.edu.ucp.saharaai.ui.theme.SaharaStrongGreen
import pk.edu.ucp.saharaai.ui.theme.SaharaWarning
import pk.edu.ucp.saharaai.utils.ObservePermissionState
import pk.edu.ucp.saharaai.utils.PermissionCopy
import pk.edu.ucp.saharaai.utils.rememberAppPermissionRequester
import pk.edu.ucp.saharaai.viewmodels.LensUiState
import pk.edu.ucp.saharaai.viewmodels.LensViewModel
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource


@Composable
fun SaharaLensScreen(
    navController: NavController,
    isEnglish: Boolean = true,
    viewModel: LensViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val hazeState = remember { HazeState() }

    val cameraGranted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermissionRequester = rememberAppPermissionRequester(
        permission = Manifest.permission.CAMERA,
        isEnglish = isEnglish,
        copy = PermissionCopy(
            deniedEn = "Camera permission was denied.",
            deniedUr = "Camera ki ijazat nahi di gayi.",
            settingsEn = "Enable camera permission in App settings to use Sahara Lens.",
            settingsUr = "Sahara Lens ke liye App settings mein camera ki ijazat dein.",
        ),
        onGranted = { cameraGranted.value = true },
        onDenied = { cameraGranted.value = false },
    )
    ObservePermissionState(cameraPermissionRequester) {
        cameraGranted.value = it
    }

    LaunchedEffect(Unit) {
        if (!cameraGranted.value) cameraPermissionRequester.request()
    }

    Scaffold(
        modifier = Modifier.hazeSource(hazeState),
        topBar = { LensTopBar(isEnglish, hazeState, onBack = { navController.popBackStack() }) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            SaharaStrongGreen.copy(alpha = 0.10f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                            MaterialTheme.colorScheme.background,
                        )
                    )
                )
        ) {
            if (!cameraGranted.value) {
                PermissionGate(
                    isEnglish = isEnglish,
                    onRequest = { cameraPermissionRequester.request() },
                )
                return@Box
            }

            when (val s = state) {
                LensUiState.Capturing -> CapturePane(
                    isEnglish = isEnglish,
                    viewModel = viewModel,
                )
                is LensUiState.Reviewing -> ReviewPane(
                    isEnglish = isEnglish,
                    imageBytes = s.imageBytes,
                    onRetake = viewModel::onRetake,
                    onConfirm = viewModel::onConfirm,
                )
                LensUiState.Analyzing -> AnalyzingPane(isEnglish)
                is LensUiState.Result -> ResultPane(
                    isEnglish = isEnglish,
                    level = s.level,
                    response = s.response,
                    onRetake = viewModel::reset,
                    onMeditation = { navController.navigate("meditation") },
                    onCounselor = { navController.navigate("counselors") },
                    onEmergency = { navController.navigate("emergency") },
                    onDone = { navController.popBackStack() },
                )
                is LensUiState.Error -> ErrorPane(
                    isEnglish = isEnglish,
                    message = s.message,
                    reasons = s.reasons,
                    onRetake = viewModel::reset,
                )
            }
        }
    }
}





@Composable
private fun LensTopBar(isEnglish: Boolean, hazeState: HazeState, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HazeBackButton(onClick = onBack, hazeState = hazeState)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (isEnglish) "Sahara Lens" else "Sahara Lens",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = if (isEnglish) {
                    "A private 5-second face check-in"
                } else {
                    "5 second ka private face check-in"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionGate(isEnglish: Boolean, onRequest: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Visibility,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = SaharaStrongGreen,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (isEnglish) "Camera permission needed" else "Camera ki ijazat chahiye",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isEnglish) {
                "Sahara Lens uses your front camera for a brief check-in. The photo never leaves your phone — only the screening result is saved."
            } else {
                "Sahara Lens aapke front camera se chhota check-in karta hai. Photo phone mein hi rehti hai — sirf screening result save hota hai."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        SaharaButton(
            text = if (isEnglish) "Allow camera" else "Camera ijazat dein",
            onClick = onRequest,
            variant = ButtonVariant.GRADIENT,
            isFullWidth = true,
        )
    }
}


@Composable
private fun CapturePane(
    isEnglish: Boolean,
    viewModel: LensViewModel,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(android.view.Surface.ROTATION_0)
            .build()
    }

    
    
    
    
    val antiSpoof = remember {
        runCatching { FaceAntiSpoofingClassifier.create(context) }.getOrNull()
    }
    DisposableEffect(antiSpoof) {
        onDispose {
            antiSpoof?.close()
            cameraExecutor.shutdown()
            analysisExecutor.shutdown()
        }
    }

    val analyzer = remember(antiSpoof) {
        FaceScannerAnalyzer(
            ovalBoundsFractional = RectFractional.CENTERED_OVAL,
            antiSpoofClassifier = antiSpoof,
            onValidation = viewModel::onAnalyzerValidation,
        )
    }

    val validation by viewModel.validation.collectAsState()
    val holdSeconds by viewModel.holdSeconds.collectAsState()
    val readyToCapture by viewModel.readyToCapture.collectAsState()
    var isShooting by remember { mutableStateOf(false) }

    
    
    
    LaunchedEffect(readyToCapture) {
        if (readyToCapture && !isShooting) {
            isShooting = true
            try {
                val bytes = captureJpeg(imageCapture, cameraExecutor)
                viewModel.onCaptured(bytes)
            } catch (_: Throwable) {
                
                
                viewModel.reset()
            } finally {
                isShooting = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    runCatching {
                        val cameraProvider = cameraProviderFuture.get()
                        cameraProvider.unbindAll()
                        val preview = Preview.Builder().build().apply {
                            surfaceProvider = previewView.surfaceProvider
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setImageQueueDepth(3)
                            .build()
                            .also { it.setAnalyzer(analysisExecutor, analyzer) }
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            preview,
                            analysis,
                            imageCapture,
                        )
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize(),
        )

        
        
        
        
        val ovalAccent = when {
            validation is LensValidation.Valid && holdSeconds >= LensViewModel.REQUIRED_HOLD_SECONDS -> SaharaStrongGreen
            validation is LensValidation.Valid -> SaharaPeach
            else -> SaharaCoral
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            
            
            
            val ovalW = size.width * 0.70f
            val ovalH = size.height * 0.60f
            val topLeft = Offset(
                x = (size.width  - ovalW) / 2f,
                y = (size.height - ovalH) / 2f,
            )
            drawOval(
                color = ovalAccent,
                topLeft = topLeft,
                size = Size(ovalW, ovalH),
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                    )
                )
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val state = validation
            val title = if (state is LensValidation.Valid) {
                if (holdSeconds >= LensViewModel.REQUIRED_HOLD_SECONDS) {
                    if (isEnglish) "Capturing…" else "Capture ho raha hai…"
                } else {
                    val remaining = (LensViewModel.REQUIRED_HOLD_SECONDS - holdSeconds).coerceAtLeast(0)
                    if (isEnglish) "Hold still… ${remaining}s" else "Hilna mat… ${remaining}s"
                }
            } else {
                state.reason
            }
            val color = when {
                state is LensValidation.Valid -> SaharaStrongGreen
                else -> Color.White
            }
            Text(
                text = title,
                color = color,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (isEnglish) {
                    "Anti-spoof: real face + neutral expression + good light required"
                } else {
                    "Anti-spoof: asli chehra, neutral expression, achi roshni zaroori"
                },
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ReviewPane(
    isEnglish: Boolean,
    imageBytes: ByteArray,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
) {
    val bmp = remember(imageBytes) {
        android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Text(
            text = if (isEnglish) "Send this for screening?" else "Ye screening ke liye bhejein?",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        
                        .graphicsLayer(scaleX = -1f),
                )
            }
        }
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
                onClick = onConfirm,
                variant = ButtonVariant.GRADIENT,
                isFullWidth = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AnalyzingPane(isEnglish: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = SaharaStrongGreen, strokeWidth = 4.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (isEnglish) "Reading the moment…" else "Lamha samjha ja raha hai…",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isEnglish) {
                "This is a screening signal, never a diagnosis. The photo stays on your phone — only the result is saved."
            } else {
                "Ye sirf screening signal hai, diagnosis nahi. Photo phone par hi rehti hai — sirf result save hota hai."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = SaharaStrongGreen,
        )
    }
}

@Composable
private fun ResultPane(
    isEnglish: Boolean,
    level: LensLevel,
    response: LensScanResponse,
    onRetake: () -> Unit,
    onMeditation: () -> Unit,
    onCounselor: () -> Unit,
    onEmergency: () -> Unit,
    onDone: () -> Unit,
) {
    val accent = when (level) {
        LensLevel.NEUTRAL   -> SaharaStrongGreen
        LensLevel.ELEVATED  -> SaharaPeach
        LensLevel.HIGH      -> SaharaCoral
        LensLevel.UNCERTAIN -> SaharaSky
        LensLevel.UNKNOWN   -> SaharaWarning
    }
    val icon: ImageVector = when (level) {
        LensLevel.NEUTRAL   -> Icons.Filled.SentimentVerySatisfied
        LensLevel.ELEVATED  -> Icons.Filled.SelfImprovement
        LensLevel.HIGH      -> Icons.Filled.LocalHospital
        LensLevel.UNCERTAIN -> Icons.Filled.Refresh
        LensLevel.UNKNOWN   -> Icons.Filled.Warning
    }
    val title = when (level) {
        LensLevel.NEUTRAL   -> if (isEnglish) "You seem steady right now" else "Aap is waqt theek lag rahe hain"
        LensLevel.ELEVATED  -> if (isEnglish) "We're picking up some stress" else "Thori stress dikh rahi hai"
        LensLevel.HIGH      -> if (isEnglish) "Strong distress signal" else "Distress ka signal tez hai"
        LensLevel.UNCERTAIN -> if (isEnglish) "Couldn't read clearly" else "Saaf nahi pata laga"
        LensLevel.UNKNOWN   -> if (isEnglish) "Result unavailable" else "Result mojood nahi"
    }
    val subtitle = when (level) {
        LensLevel.NEUTRAL   -> if (isEnglish) {
            "Keep an eye on yourself. Journal or breathe if anything shifts."
        } else {
            "Apne aap par nazar rakhein. Kuch change ho to journal ya saans ki mashq karein."
        }
        LensLevel.ELEVATED  -> if (isEnglish) {
            "A short breathing exercise or a journal entry can settle things."
        } else {
            "Saans ki choti mashq ya ek journal entry asar karegi."
        }
        LensLevel.HIGH      -> if (isEnglish) {
            "Reaching out helps. Sahara counselors are available, or call 1122/115 in an emergency."
        } else {
            "Baat karna madad karta hai. Sahara counselor available hain, ya emergency mein 1122/115 call karein."
        }
        LensLevel.UNCERTAIN -> if (isEnglish) {
            "Try again with better light and a head-on face."
        } else {
            "Achi roshni mein, seedha dekh kar dobara try karein."
        }
        LensLevel.UNKNOWN   -> if (isEnglish) "Please try again." else "Dobara try karein."
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Surface(
            color = accent.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = accent,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        ScreeningBreakdown(response, isEnglish)

        Spacer(Modifier.height(20.dp))
        when (level) {
            LensLevel.HIGH -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
            LensLevel.ELEVATED -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
            LensLevel.UNCERTAIN -> SaharaButton(
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

        Spacer(Modifier.height(8.dp))
        response.modelVersion?.let {
            Text(
                text = if (isEnglish) "Model: $it" else "Model: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ScreeningBreakdown(response: LensScanResponse, isEnglish: Boolean) {
    val probs = response.screening?.screeningProbs ?: return
    if (probs.isEmpty()) return
    val ordered = listOf("neutral", "stress", "sadness", "fear").mapNotNull { key ->
        probs[key]?.let { key to it }
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = if (isEnglish) "Screening breakdown" else "Screening breakdown",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(8.dp))
            ordered.forEach { (label, value) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
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
}

@Composable
private fun ErrorPane(
    isEnglish: Boolean,
    message: String,
    reasons: List<String>,
    onRetake: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Warning, null, tint = SaharaCoral, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        if (reasons.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            ) {
                LazyColumn(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                ) {
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





private fun colorForScreeningLabel(label: String): Color = when (label.lowercase()) {
    "neutral" -> SaharaStrongGreen
    "stress"  -> SaharaPeach
    "sadness" -> SaharaSky
    "fear"    -> SaharaCoral
    else      -> SaharaWarning
}


private suspend fun captureJpeg(
    imageCapture: ImageCapture,
    executor: java.util.concurrent.Executor,
): ByteArray = suspendCancellableCoroutine { cont ->
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                    cont.resume(bytes)
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                } finally {
                    image.close()
                }
            }
            override fun onError(exception: ImageCaptureException) {
                cont.resumeWithException(exception)
            }
        },
    )
}
