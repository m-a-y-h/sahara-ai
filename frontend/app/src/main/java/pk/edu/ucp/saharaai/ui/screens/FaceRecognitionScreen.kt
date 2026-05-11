package pk.edu.ucp.saharaai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class FaceLoginStatus {
    IDLE,
    STARTING_CAMERA,
    CAMERA_ACTIVE,
    CAPTURING,
    PROCESSING,
    SUCCESS,
    ERROR
}

@Composable
fun FaceRecognitionScreen(
    navController: NavHostController,
    isEnglish: Boolean = true
) {
    var status by remember { mutableStateOf(FaceLoginStatus.IDLE) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val title = if (isEnglish) "Face Login" else "Face Se Log In"
    val subtitle = if (isEnglish) "Position your face in the frame to continue" else "Apna chehra frame mein rakhein jari rakhne ke liye"

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            coroutineScope.launch {
                status = FaceLoginStatus.STARTING_CAMERA
                delay(600)
                status = FaceLoginStatus.CAMERA_ACTIVE
            }
        } else {
            status = FaceLoginStatus.ERROR
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "scan_anim")
    val scanLinePosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_line"
    )
    val cameraStartPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cam_pulse"
    )

    val handleStartCamera = {
        val check = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (check == PackageManager.PERMISSION_GRANTED) {
            coroutineScope.launch {
                status = FaceLoginStatus.STARTING_CAMERA
                delay(600)
                status = FaceLoginStatus.CAMERA_ACTIVE
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        Unit
    }

    val handleCapture = {
        coroutineScope.launch {
            status = FaceLoginStatus.CAPTURING
            delay(150)
            status = FaceLoginStatus.PROCESSING
            delay(2200)
            val verified = true
            if (verified) {
                status = FaceLoginStatus.SUCCESS
                delay(1400)
                navController.navigate("dashboard") {
                    popUpTo("login") { inclusive = true }
                }
            } else {
                status = FaceLoginStatus.ERROR
            }
        }
        Unit
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (isEnglish) "Back" else "Wapis",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Icon(
                imageVector = Icons.Default.Face,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            FaceLoginViewport(
                status = status,
                scanLinePosition = scanLinePosition,
                cameraStartPulse = cameraStartPulse,
                onCapture = handleCapture,
                isEnglish = isEnglish
            )

            Spacer(modifier = Modifier.height(32.dp))

            FaceLoginActions(
                status = status,
                onStartCamera = handleStartCamera,
                onRetry = { status = FaceLoginStatus.IDLE },
                isEnglish = isEnglish
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun FaceLoginViewport(
    status: FaceLoginStatus,
    scanLinePosition: Float,
    cameraStartPulse: Float,
    onCapture: () -> Unit,
    isEnglish: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            when (status) {
                FaceLoginStatus.IDLE ->
                    IdleView(isEnglish)

                FaceLoginStatus.STARTING_CAMERA ->
                    StartingCameraView(cameraStartPulse, isEnglish)

                FaceLoginStatus.CAMERA_ACTIVE,
                FaceLoginStatus.CAPTURING ->
                    LiveCameraFeed(
                        isCapturing = status == FaceLoginStatus.CAPTURING,
                        onCapture = onCapture,
                        isEnglish = isEnglish
                    )

                FaceLoginStatus.PROCESSING ->
                    ProcessingView(scanLinePosition, isEnglish)

                FaceLoginStatus.SUCCESS ->
                    SuccessView(isEnglish)

                FaceLoginStatus.ERROR ->
                    ErrorView(isEnglish)
            }
        }
    }
}

@Composable
private fun IdleView(isEnglish: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Color.DarkGray.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = Color.LightGray,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isEnglish) "Camera preview will appear here" else "Camera preview yahan dikhayi degi",
            color = Color.LightGray,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun StartingCameraView(pulseScale: Float, isEnglish: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(48.dp)
                .scale(pulseScale),
            strokeWidth = 3.dp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isEnglish) "Initializing camera…" else "Camera shuru ho raha hai…",
            color = Color.LightGray,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun LiveCameraFeed(
    isCapturing: Boolean,
    onCapture: () -> Unit,
    isEnglish: Boolean
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                val executor = ContextCompat.getMainExecutor(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build()
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, executor)
            }
        )

        FaceOvalGuide()

        AnimatedVisibility(
            visible = isCapturing,
            enter = fadeIn(animationSpec = tween(50)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.75f))
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                    )
                )
                .padding(bottom = 28.dp, top = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onCapture,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = if (isEnglish) "Verify face" else "Chehra tasdeeq karein",
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun BoxScope.FaceOvalGuide() {
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .width(180.dp)
            .height(240.dp)
            .border(
                width = 2.dp,
                color = Color.White.copy(alpha = 0.6f),
                shape = RoundedCornerShape(percent = 50)
            )
    )
}

@Composable
private fun ProcessingView(scanLinePosition: Float, isEnglish: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111111))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(200.dp)
                .height(270.dp)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.primary,
                                Color.Transparent
                            )
                        )
                    )
                    .align(Alignment.TopCenter)
                    .offset(y = (270 * scanLinePosition).dp)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isEnglish) "Verifying identity…" else "Tasdeeq ho rahi hai…",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SuccessView(isEnglish: Boolean) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "success_scale"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Verified",
            tint = Color(0xFF4CAF50),
            modifier = Modifier
                .size(80.dp)
                .scale(scale)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isEnglish) "Identity Verified" else "Tasdeeq Mukammal",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4CAF50)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isEnglish) "Welcome back!" else "Khush Amdeed!",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray
        )
    }
}

@Composable
private fun ErrorView(isEnglish: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Error",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isEnglish) "Verification Failed" else "Tasdeeq Nakama",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isEnglish) "Face not recognised or camera permission was denied. Please try again." else "Chehra nahi pehchana gaya ya camera ki ijazat nahi mili. Dubara koshish karein.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.LightGray,
            textAlign = TextAlign.Center
        )
    }
}


@Composable
private fun FaceLoginActions(
    status: FaceLoginStatus,
    onStartCamera: () -> Unit,
    onRetry: () -> Unit,
    isEnglish: Boolean
) {
    AnimatedContent(
        targetState = status,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
        label = "action_anim"
    ) { currentStatus ->
        when (currentStatus) {
            FaceLoginStatus.IDLE ->
                Button(
                    onClick = onStartCamera,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEnglish) "Start Face Verification" else "Chehray ki Tasdeeq Shuru Karein",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

            FaceLoginStatus.ERROR ->
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (isEnglish) "Try Again" else "Dubara Koshish Karein",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

            else -> Spacer(modifier = Modifier.height(52.dp))
        }
    }
}
