package pk.edu.ucp.saharaai.ui.screens

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.R
import pk.edu.ucp.saharaai.ui.components.*
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.viewmodels.LoginViewModel

private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[a-zA-Z]{2,}\$")
private fun isValidEmail(email: String): Boolean = emailRegex.matches(email)

internal tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

private const val BIOMETRIC_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK

fun authenticateWithBiometrics(
    context: Context,
    isEnglish: Boolean,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val fragmentActivity = context.findFragmentActivity()
    if (fragmentActivity == null) {
        onError(if (isEnglish) "Unable to open biometric authentication." else "Biometric tasdeeq khul nahi saki.")
        return
    }
    if (BiometricManager.from(context).canAuthenticate(BIOMETRIC_AUTHENTICATORS) != BiometricManager.BIOMETRIC_SUCCESS) {
        onError(if (isEnglish) "No enrolled fingerprint or face unlock is available." else "Fingerprint ya face unlock enroll nahi hai.")
        return
    }
    val executor = ContextCompat.getMainExecutor(context)
    val biometricPrompt = BiometricPrompt(fragmentActivity, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError(if (isEnglish) "Biometric not recognized. Try again." else "Biometric pehchana nahi gaya. Dobara koshish karein.")
            }
        })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(if (isEnglish) "Biometric Login" else "Biometric Tasdeeq")
        .setSubtitle(if (isEnglish) "Log in using your secure credential" else "Apni secure tasdeeq se log in karein")
        .setNegativeButtonText(if (isEnglish) "Cancel" else "Cancel Karein")
        .setAllowedAuthenticators(BIOMETRIC_AUTHENTICATORS)
        .build()

    biometricPrompt.authenticate(promptInfo)
}

@Composable
fun LoginScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDashboard: (String) -> Unit,
    onBiometricSuccess: () -> Unit = {},
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onNavigateToFaceLogin: () -> Unit = {},
    isEnglish: Boolean = false,
    loginViewModel: LoginViewModel = viewModel()
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current

    
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val isLoading = loginViewModel.isLoading
    val errorMsg = loginViewModel.errorMessage

    val prefs = remember { context.getSharedPreferences("sahara_prefs", Context.MODE_PRIVATE) }

    
    
    
    // The biometric chip is only useful if the vault has been armed — i.e. the
    // user has signed in manually at least once on this device, so we have a
    // sealed (email, password) pair waiting behind their biometric. After
    // sign-out the vault is intentionally NOT cleared, so this stays true and
    // they can come back in with a fingerprint.
    val showBiometric = remember {
        pk.edu.ucp.saharaai.utils.BiometricCredentialVault.isArmed(context)
    }

    val primaryGreen = if (isDark) SaharaStrongGreen else SaharaGreen

    val bgGradient = if (isDark) {
        listOf(SaharaStrongGreen.copy(alpha = 0.2f), MaterialTheme.colorScheme.background.copy(alpha = 0.6f), MaterialTheme.colorScheme.background)
    } else {
        listOf(SaharaStrongGreen.copy(alpha = 0.25f), SaharaPeach.copy(alpha = 0.1f), MaterialTheme.colorScheme.background.copy(alpha = 0.2f))
    }
    val blob1Color = SaharaStrongGreen.copy(alpha = if (isDark) 0.25f else 0.15f)
    val blob2Color = SaharaSky.copy(alpha = if (isDark) 0.2f else 0.18f)

    val bgHazeState = remember { HazeState() }

    val isEmailValid = isValidEmail(email) || email.isEmpty()
    val isFormValid = isValidEmail(email) && password.isNotEmpty()

    val blobMotion = rememberBackdropBlobMotion()

    Box(modifier = Modifier.fillMaxSize()) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = bgHazeState)
                .background(Brush.verticalGradient(bgGradient))
        ) {
            Box(modifier = Modifier.size(350.dp).offset(x = (-80).dp, y = (-50).dp).primaryBlobMotion(blobMotion).background(Brush.radialGradient(listOf(blob1Color, Color.Transparent))))
            Box(modifier = Modifier.size(400.dp).align(Alignment.BottomEnd).offset(x = 100.dp, y = 50.dp).secondaryBlobMotion(blobMotion).background(Brush.radialGradient(listOf(blob2Color, Color.Transparent))))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            HazeBackButton(onClick = onNavigateBack, hazeState = bgHazeState, tint = primaryGreen)

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
                        onValueChange = { email = it.trim().lowercase(); loginViewModel.clearAuthError() },
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
                        onValueChange = { password = it; loginViewModel.clearAuthError() },
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

                    if (errorMsg.isNotBlank()) {
                        Text(
                            text = errorMsg,
                            color = SaharaCoral,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = SaharaStrongGreen)
                        }
                    } else {
                        SaharaButton(
                            text = if (isEnglish) "Sign In" else "Sign In Karein",
                            onClick = {
                                // Capture the password before VM clears the form so we can seal
                                // it into the biometric vault on success. Vault is overwritten
                                // each successful login, so a password change always re-arms.
                                val capturedPassword = password
                                loginViewModel.signIn(email, password, isEnglish) { cleanEmail ->
                                    pk.edu.ucp.saharaai.utils.BiometricCredentialVault.save(
                                        context, cleanEmail, capturedPassword
                                    )
                                    prefs.edit().putBoolean("biometric_enabled", true).apply()
                                    onNavigateToDashboard(cleanEmail)
                                }
                            },
                            enabled = isFormValid,
                            isFullWidth = true,
                            modifier = Modifier.height(48.dp),
                            variant = if (isFormValid) ButtonVariant.SAHARASTRONGGREENGLASS else ButtonVariant.DEFAULT,
                            hazeState = if (isFormValid) bgHazeState else null,
                            isEnglish = isEnglish
                        )
                    }
                }
            }

            OrDivider(isEnglish = isEnglish, isDark = isDark)

            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                
                AuthCircleButton(
                    onClick = {
                        if (isLoading) return@AuthCircleButton
                        loginViewModel.signInWithGoogle(context, isEnglish, onNavigateToDashboard)
                    },
                    isDark = isDark
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_google),
                        contentDescription = "Google",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp)
                    )
                }

                
                if (showBiometric) {
                    Spacer(modifier = Modifier.width(24.dp))
                    AuthCircleButton(
                        onClick = {
                            loginViewModel.clearAuthError()
                            authenticateWithBiometrics(
                                context = context,
                                isEnglish = isEnglish,
                                onSuccess = {
                                    // After the system biometric prompt succeeds, decrypt the
                                    // saved (email, password) from the keystore-backed vault
                                    // and run a real Firebase sign-in. No vault entry =
                                    // biometric is "armed" only after a successful manual
                                    // login, so this path is guaranteed to have creds.
                                    val creds = pk.edu.ucp.saharaai.utils.BiometricCredentialVault
                                        .load(context)
                                    if (creds == null) {
                                        loginViewModel.reportError(
                                            if (isEnglish)
                                                "Sign in with your password once to enable fingerprint login."
                                            else
                                                "Pehli baar password se login karein, phir fingerprint kaam karega."
                                        )
                                        return@authenticateWithBiometrics
                                    }
                                    loginViewModel.signIn(creds.first, creds.second, isEnglish) {
                                        onBiometricSuccess()
                                    }
                                },
                                onError = loginViewModel::reportError
                            )
                        },
                        isDark = isDark
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = if (isEnglish) "Fingerprint login" else "Fingerprint se login",
                            tint = primaryGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 32.dp),
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


@Composable
private fun AuthCircleButton(
    onClick: () -> Unit,
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    val bgColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.85f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f)

    Box(
        modifier = Modifier
            .size(56.dp)
            .background(bgColor, CircleShape)
            .border(1.dp, borderColor, CircleShape)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
