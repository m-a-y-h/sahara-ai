package pk.edu.ucp.saharaai.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.ui.components.HazeBackButton
import pk.edu.ucp.saharaai.viewmodels.FaceRecognitionViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource


enum class BiometricStatus { IDLE, AUTHENTICATING, SUCCESS, ERROR, NOT_AVAILABLE }

@Composable
fun FaceRecognitionScreen(
    navController: NavHostController,
    isEnglish: Boolean = true,
    faceViewModel: FaceRecognitionViewModel = viewModel()
) {
    val context       = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDark        = isSystemInDarkTheme()
    val hazeState     = remember { HazeState() }

    
    val biometricManager = remember { BiometricManager.from(context) }
    val canAuthenticate  = remember {
        biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    LaunchedEffect(Unit) { faceViewModel.initialize(context) }
    val biometricEnabled = faceViewModel.biometricEnabled
    val hasStoredSession = faceViewModel.hasStoredSession

    var status    by remember { mutableStateOf(BiometricStatus.IDLE) }
    var errorText by remember { mutableStateOf("") }

    
    fun launchBiometric() {
        val activity = context.findFragmentActivity()
        if (activity == null) {
            errorText = if (isEnglish) "Unable to open biometric authentication." else "Biometric tasdeeq khul nahi saki."
            status = BiometricStatus.ERROR
            return
        }
        status = BiometricStatus.AUTHENTICATING

        val executor = ContextCompat.getMainExecutor(context)
        val prompt   = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    status = BiometricStatus.SUCCESS
                    faceViewModel.onAuthenticationSucceeded()
                    coroutineScope.launch {
                        delay(1200)
                        navController.navigate("auth-gate") { popUpTo(0) { inclusive = true } }
                    }
                }
                override fun onAuthenticationError(code: Int, msg: CharSequence) {
                    errorText = msg.toString()
                    status = BiometricStatus.ERROR
                }
                override fun onAuthenticationFailed() {
                    
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(if (isEnglish) "Biometric Login" else "Biometric Tasdeeq")
            .setSubtitle(
                if (isEnglish) "Use your face ID or fingerprint to continue"
                else "Jari rakhne ke liye apna face ID ya fingerprint use karein"
            )
            .setNegativeButtonText(if (isEnglish) "Cancel" else "Cancel Karein")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()

        prompt.authenticate(promptInfo)
    }

    
    LaunchedEffect(canAuthenticate, biometricEnabled, hasStoredSession) {
        if (canAuthenticate && biometricEnabled && hasStoredSession) {
            delay(400) 
            launchBiometric()
        }
    }

    
    val pulseScale = rememberFrameOscillation(0.92f, 1.08f, 1000)

    val bgGradient = if (isDark) {
        listOf(SaharaStrongGreen.copy(alpha = 0.15f), MaterialTheme.colorScheme.background)
    } else {
        listOf(SaharaStrongGreen.copy(alpha = 0.10f), MaterialTheme.colorScheme.background)
    }

    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState)
            .background(Brush.verticalGradient(bgGradient))
    ) {
        
        HazeBackButton(
            onClick = { navController.popBackStack() },
            hazeState = hazeState,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            
            Box(contentAlignment = Alignment.Center) {
                
                if (status == BiometricStatus.IDLE || status == BiometricStatus.AUTHENTICATING) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .scale(pulseScale)
                            .background(SaharaStrongGreen.copy(alpha = 0.12f), CircleShape)
                    )
                }
                
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            when (status) {
                                BiometricStatus.SUCCESS       -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                                BiometricStatus.ERROR,
                                BiometricStatus.NOT_AVAILABLE -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                else                          -> SaharaStrongGreen.copy(alpha = 0.15f)
                            },
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (status) {
                            BiometricStatus.SUCCESS       -> Icons.Default.CheckCircle
                            BiometricStatus.ERROR         -> Icons.Default.ErrorOutline
                            BiometricStatus.NOT_AVAILABLE -> Icons.Default.Block
                            else                          -> Icons.Default.Fingerprint
                        },
                        contentDescription = null,
                        tint = when (status) {
                            BiometricStatus.SUCCESS       -> Color(0xFF4CAF50)
                            BiometricStatus.ERROR,
                            BiometricStatus.NOT_AVAILABLE -> MaterialTheme.colorScheme.error
                            else                          -> SaharaStrongGreen
                        },
                        modifier = Modifier.size(52.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            
            Text(
                text = when (status) {
                    BiometricStatus.SUCCESS       ->
                        if (isEnglish) "Verified!" else "Tasdeeq Mukammal!"
                    BiometricStatus.AUTHENTICATING ->
                        if (isEnglish) "Verifying…" else "Tasdeeq Ho Rahi Hai…"
                    BiometricStatus.ERROR         ->
                        if (isEnglish) "Authentication Failed" else "Tasdeeq Nakam"
                    BiometricStatus.NOT_AVAILABLE ->
                        if (isEnglish) "Not Available" else "Dastiyab Nahi"
                    else ->
                        if (isEnglish) "Biometric Login" else "Biometric Login"
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = when (status) {
                    BiometricStatus.SUCCESS -> Color(0xFF4CAF50)
                    BiometricStatus.ERROR, BiometricStatus.NOT_AVAILABLE ->
                        MaterialTheme.colorScheme.error
                    else -> SaharaStrongGreen
                },
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            
            Text(
                text = when {
                    !canAuthenticate ->
                        if (isEnglish) "Your device does not support biometric authentication."
                        else "Aapka device biometric authentication support nahi karta."
                    !biometricEnabled ->
                        if (isEnglish) "Biometric login is disabled. Enable it in Settings → Security."
                        else "Biometric login band hai. Settings → Hifazat mein chalu karein."
                    !hasStoredSession ->
                        if (isEnglish) "Please log in with your email and password first, then you can use biometric login."
                        else "Pehle email aur password se login karein, phir biometric use kar saktay hain."
                    status == BiometricStatus.ERROR ->
                        errorText.ifBlank {
                            if (isEnglish) "Please try again."
                            else "Dubara koshish karein."
                        }
                    status == BiometricStatus.SUCCESS ->
                        if (isEnglish) "Welcome back! Redirecting…"
                        else "Khush Amdeed! Redirect ho raha hai…"
                    status == BiometricStatus.AUTHENTICATING ->
                        if (isEnglish) "Please complete the biometric prompt."
                        else "Biometric prompt mein tasdeeq karein."
                    else ->
                        if (isEnglish) "Use your device's Face ID or fingerprint sensor."
                        else "Apna Face ID ya fingerprint sensor use karein."
                },
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(40.dp))

            
            when {
                
                !hasStoredSession -> {
                    Button(
                        onClick = { navController.popBackStack() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SaharaStrongGreen)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isEnglish) "Go Back to Login" else "Login Par Wapis Jayein",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                
                !canAuthenticate || !biometricEnabled -> {
                    if (!biometricEnabled && canAuthenticate) {
                        OutlinedButton(
                            onClick = { navController.navigate("settings") },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isEnglish) "Go to Settings" else "Settings Mein Jayein",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                
                status == BiometricStatus.IDLE || status == BiometricStatus.ERROR -> {
                    Button(
                        onClick = { launchBiometric() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SaharaStrongGreen)
                    ) {
                        Icon(Icons.Default.Fingerprint, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (status == BiometricStatus.ERROR) {
                                if (isEnglish) "Try Again" else "Dubara Koshish Karein"
                            } else {
                                if (isEnglish) "Verify with Biometrics" else "Biometric se Tasdeeq Karein"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                
                status == BiometricStatus.AUTHENTICATING -> {
                    CircularProgressIndicator(color = SaharaStrongGreen, strokeWidth = 3.dp)
                }
                
                status == BiometricStatus.SUCCESS -> {
                    CircularProgressIndicator(
                        color = Color(0xFF4CAF50),
                        strokeWidth = 3.dp
                    )
                }
                else -> {}
            }
        }
    }
}
