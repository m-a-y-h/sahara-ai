package pk.edu.ucp.saharaai.ui.screens

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.R
import pk.edu.ucp.saharaai.ui.components.*
import pk.edu.ucp.saharaai.ui.theme.*

private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[a-zA-Z]{2,}\$")
private fun isValidEmail(email: String): Boolean = emailRegex.matches(email)

fun authenticateWithBiometrics(context: Context, isEnglish: Boolean, onSuccess: () -> Unit) {
    val fragmentActivity = context as? FragmentActivity ?: return
    val executor = ContextCompat.getMainExecutor(context)
    val biometricPrompt = BiometricPrompt(fragmentActivity, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
        })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(if (isEnglish) "Biometric Login" else "Biometric Tasdeeq")
        .setSubtitle(if (isEnglish) "Log in using your secure credential" else "Apni secure tasdeeq se log in karein")
        .setNegativeButtonText(if (isEnglish) "Cancel" else "Mansookh karein")
        .build()

    biometricPrompt.authenticate(promptInfo)
}

@Composable
fun LoginScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDashboard: (String) -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onNavigateToFaceRecognition: () -> Unit,
    isEnglish: Boolean = false
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current

    val primaryGreen = if (isDark) SaharaStrongGreen else SaharaGreen

    val bgGradient = if (isDark) {
        listOf(SaharaStrongGreen.copy(alpha = 0.2f), MaterialTheme.colorScheme.background.copy(alpha = 0.6f), MaterialTheme.colorScheme.background)
    } else {
        listOf(SaharaStrongGreen.copy(alpha = 0.25f), SaharaPeach.copy(alpha = 0.1f), MaterialTheme.colorScheme.background.copy(alpha = 0.2f))
    }
    val blob1Color = SaharaStrongGreen.copy(alpha = if (isDark) 0.25f else 0.15f)
    val blob2Color = SaharaSky.copy(alpha = if (isDark) 0.2f else 0.18f)

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    val bgHazeState = remember { HazeState() }

    val isEmailValid = isValidEmail(email) || email.isEmpty()
    val isFormValid = isValidEmail(email) && password.isNotEmpty()

    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val blobRotation by infiniteTransition.animateFloat(
        initialValue = -10f, targetValue = 10f,
        animationSpec = infiniteRepeatable(animation = tween(7000, easing = EaseInOutSine), repeatMode = RepeatMode.Reverse),
        label = "blobRotation"
    )
    val blobScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(animation = tween(5000, easing = EaseInOutSine), repeatMode = RepeatMode.Reverse),
        label = "blobScale"
    )

    Box(modifier = Modifier.fillMaxSize()) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = bgHazeState)
                .background(Brush.verticalGradient(bgGradient))
        ) {
            Box(modifier = Modifier.size(350.dp).offset(x = (-80).dp, y = (-50).dp).rotate(blobRotation).scale(blobScale).background(Brush.radialGradient(listOf(blob1Color, Color.Transparent))))
            Box(modifier = Modifier.size(400.dp).align(Alignment.BottomEnd).offset(x = 100.dp, y = 50.dp).rotate(-blobRotation).scale(blobScale).background(Brush.radialGradient(listOf(blob2Color, Color.Transparent))))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = primaryGreen)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(if (isEnglish) "Welcome Back" else "Khush Amdeed", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = primaryGreen)
            Text(if (isEnglish) "Log in to your account" else "Apne account mein log in karein", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(40.dp))

            SaharaCard(
                variant = CardVariant.GLASS,
                hazeState = bgHazeState,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim().lowercase() },
                        label = { Text(if (isEnglish) "Email" else "Email") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = primaryGreen, modifier = Modifier.size(20.dp)) },
                        modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        isError = !isEmailValid,
                        supportingText = { if (!isEmailValid) Text(if (isEnglish) "Enter a valid email address" else "Sahih email address darj karein", style = MaterialTheme.typography.labelSmall) }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(if (isEnglish) "Password" else "Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = primaryGreen, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            val image = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(image, contentDescription = "Toggle Password", tint = primaryGreen, modifier = Modifier.size(20.dp))
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onNavigateToForgotPassword) {
                            Text(if (isEnglish) "Forgot Password?" else "Password bhool gaye?", color = primaryGreen)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    SaharaButton(
                        text = if (isEnglish) "Sign In" else "Sign In Karein",
                        onClick = { onNavigateToDashboard(email) },
                        enabled = isFormValid,
                        isFullWidth = true,
                        modifier = Modifier.height(48.dp),
                        variant = if (isFormValid) ButtonVariant.SAHARASTRONGGREENGLASS else ButtonVariant.DEFAULT,
                        hazeState = if (isFormValid) bgHazeState else null,
                        isEnglish = isEnglish
                    )
                }
            }

            OrDivider(isEnglish = isEnglish, isDark = isDark)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                GlassyIconButton(
                    iconRes = R.drawable.ic_google,
                    onClick = { },
                    hazeState = bgHazeState
                )
                GlassyIconButton(
                    iconVector = Icons.Default.Fingerprint,
                    onClick = {
                        authenticateWithBiometrics(context, isEnglish) {
                            onNavigateToDashboard("biometric_user@sahara.ai")
                        }
                    },
                    hazeState = bgHazeState
                )
                GlassyIconButton(
                    iconVector = Icons.Default.Face,
                    onClick = onNavigateToFaceRecognition,
                    hazeState = bgHazeState
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEnglish) "Don't have an account? " else "Naya account banana hai? ",
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isEnglish) "Sign Up" else "Sign Up",
                    fontWeight = FontWeight.Bold,
                    color = primaryGreen,
                    modifier = Modifier.clickable { onNavigateToRegister() }
                )
            }
        }
    }
}