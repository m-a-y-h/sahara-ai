package pk.edu.ucp.saharaai.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.ui.components.*
import pk.edu.ucp.saharaai.ui.theme.*

private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[a-zA-Z]{2,}\$")
private fun isValidEmail(email: String): Boolean = emailRegex.matches(email)

enum class ResetStep {
    ENTER_EMAIL,
    ENTER_CODE,
    NEW_PASSWORD,
    SUCCESS
}

@Composable
fun ForgotPasswordScreen(
    onNavigateBack: () -> Unit,
    isEnglish: Boolean = false,
    bypassCode: String = "999999"
) {
    val isDark = isSystemInDarkTheme()
    val primaryGreen = if (isDark) SaharaStrongGreen else SaharaGreen

    val bgGradient = if (isDark) {
        listOf(SaharaStrongGreen.copy(alpha = 0.2f), MaterialTheme.colorScheme.background.copy(alpha = 0.6f), MaterialTheme.colorScheme.background)
    } else {
        listOf(SaharaStrongGreen.copy(alpha = 0.25f), SaharaPeach.copy(alpha = 0.1f), MaterialTheme.colorScheme.background.copy(alpha = 0.2f))
    }
    val blob1Color = SaharaStrongGreen.copy(alpha = if (isDark) 0.25f else 0.15f)
    val blob2Color = SaharaSky.copy(alpha = if (isDark) 0.2f else 0.18f)

    val bgHazeState = remember { HazeState() }
    val rootHazeState = remember { HazeState() }

    var currentStep by remember { mutableStateOf(ResetStep.ENTER_EMAIL) }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var codeError by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    val isEmailValid = isValidEmail(email) || email.isEmpty()
    val hasMinLength = newPassword.length >= 8
    val hasUppercase = newPassword.any { it.isUpperCase() }
    val hasNumber = newPassword.any { it.isDigit() }
    val hasSpecial = newPassword.any { !it.isLetterOrDigit() }
    val isPasswordValid = hasMinLength && hasUppercase && hasNumber && hasSpecial
    val passwordsMatch = newPassword == confirmPassword || confirmPassword.isEmpty()

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
                .hazeSource(state = rootHazeState)
        ) {
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
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
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
                            tint = primaryGreen
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Password Recovery" else "Password Ki Bahali",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = primaryGreen
                        )
                        Text(
                            text = if (isEnglish) "Reset your access securely" else "Apna account dobara bahal karein",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                SaharaCard(
                    variant = CardVariant.GLASS,
                    hazeState = bgHazeState,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AnimatedContent(targetState = currentStep, label = "ResetStepAnimation") { step ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            when (step) {
                                ResetStep.ENTER_EMAIL -> {
                                    Text(
                                        text = if (isEnglish) "Forgot Password?" else "Password Bhool Gaye?",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (isEnglish) "Enter your email. We'll send a 6-digit code to verify your identity." else "Apni email daalein. Hum aapko 6-digit verification code bhejenge.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    Spacer(modifier = Modifier.height(24.dp))

                                    OutlinedTextField(
                                        value = email,
                                        onValueChange = { email = it.trim() },
                                        label = { Text(if (isEnglish) "Email Address" else "Email Address") },
                                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = primaryGreen) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true,
                                        isError = !isEmailValid,
                                        supportingText = { if (!isEmailValid) Text(if (isEnglish) "Enter a valid email address" else "Sahih email address darj karein") }
                                    )

                                    Spacer(modifier = Modifier.height(24.dp))

                                    val isEmailStepValid = isValidEmail(email) && !isLoading

                                    SaharaButton(
                                        text = if (isLoading) (if (isEnglish) "Sending..." else "Bhej rahe hain...") else (if (isEnglish) "Send Code" else "Code Bhejein"),
                                        onClick = {
                                            coroutineScope.launch {
                                                isLoading = true
                                                delay(1200)
                                                isLoading = false
                                                currentStep = ResetStep.ENTER_CODE
                                            }
                                        },
                                        enabled = isEmailStepValid,
                                        isFullWidth = true,
                                        variant = if (isEmailStepValid) ButtonVariant.SAHARASTRONGGREENGLASS else ButtonVariant.DEFAULT,
                                        hazeState = if (isEmailStepValid) bgHazeState else null,
                                        isEnglish = isEnglish
                                    )
                                }

                                ResetStep.ENTER_CODE -> {
                                    Text(
                                        text = if (isEnglish) "Enter Reset Code" else "Reset Code Darj Karein",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (isEnglish) "We sent a 6-digit code to $email" else "Humne $email par 6-digit code bheja hai.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    Spacer(modifier = Modifier.height(24.dp))

                                    OutlinedTextField(
                                        value = code,
                                        onValueChange = { if (it.length <= 6) { code = it; codeError = false } },
                                        label = { Text(if (isEnglish) "6-Digit Code" else "6-Digit Code") },
                                        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null, tint = primaryGreen) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true,
                                        isError = codeError,
                                        supportingText = { if (codeError) Text(if (isEnglish) "Invalid or expired code" else "Code ghalat hai ya expire ho chuka hai") }
                                    )

                                    Spacer(modifier = Modifier.height(24.dp))

                                    val isCodeStepValid = code.length == 6 && !isLoading

                                    SaharaButton(
                                        text = if (isLoading) (if (isEnglish) "Verifying..." else "Tasdeeq ho rahi hai...") else (if (isEnglish) "Verify Code" else "Tasdeeq Karein"),
                                        onClick = {
                                            coroutineScope.launch {
                                                isLoading = true
                                                delay(1000)
                                                isLoading = false
                                                if (code == bypassCode) {
                                                    currentStep = ResetStep.NEW_PASSWORD
                                                } else {
                                                    codeError = true
                                                }
                                            }
                                        },
                                        enabled = isCodeStepValid,
                                        isFullWidth = true,
                                        variant = if (isCodeStepValid) ButtonVariant.SAHARASTRONGGREENGLASS else ButtonVariant.DEFAULT,
                                        hazeState = if (isCodeStepValid) bgHazeState else null,
                                        isEnglish = isEnglish
                                    )
                                }

                                ResetStep.NEW_PASSWORD -> {
                                    Text(
                                        text = if (isEnglish) "Create New Password" else "Naya Password Banayein",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (isEnglish) "Your new password must be unique and secure." else "Aapka naya password mehfooz hona chahiye.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    Spacer(modifier = Modifier.height(24.dp))

                                    OutlinedTextField(
                                        value = newPassword,
                                        onValueChange = { newPassword = it },
                                        label = { Text(if (isEnglish) "New Password" else "Naya Password") },
                                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = primaryGreen) },
                                        trailingIcon = {
                                            val image = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                            IconButton(onClick = { showPassword = !showPassword }) {
                                                Icon(image, contentDescription = "Toggle Password", tint = primaryGreen)
                                            }
                                        },
                                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true,
                                        isError = !isPasswordValid && newPassword.isNotEmpty(),
                                        supportingText = {
                                            if (newPassword.isNotEmpty() && !isPasswordValid) {
                                                Text(
                                                    buildAnnotatedString {
                                                        val invalidColor = SaharaCoral
                                                        withStyle(SpanStyle(color = if (hasMinLength) primaryGreen else invalidColor)) {
                                                            append(if (isEnglish) "At least 8 chars" else "Kam az kam 8 huroof")
                                                        }
                                                        withStyle(SpanStyle(color = if (hasUppercase) primaryGreen else invalidColor)) {
                                                            append(if (isEnglish) ", 1 uppercase" else ", 1 bara harf")
                                                        }
                                                        withStyle(SpanStyle(color = if (hasNumber) primaryGreen else invalidColor)) {
                                                            append(if (isEnglish) ", 1 number" else ", 1 number")
                                                        }
                                                        withStyle(SpanStyle(color = if (hasSpecial) primaryGreen else invalidColor)) {
                                                            append(if (isEnglish) ", 1 special char" else ", 1 special character")
                                                        }
                                                    },
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = confirmPassword,
                                        onValueChange = { confirmPassword = it },
                                        label = { Text(if (isEnglish) "Confirm Password" else "Password Dobara") },
                                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = primaryGreen) },
                                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true,
                                        isError = !passwordsMatch && confirmPassword.isNotEmpty(),
                                        supportingText = { if (!passwordsMatch && confirmPassword.isNotEmpty()) Text(if (isEnglish) "Passwords do not match" else "Password aik jaisa nahi hai", color = SaharaCoral) }
                                    )

                                    Spacer(modifier = Modifier.height(24.dp))

                                    val isPasswordStepValid = isPasswordValid && passwordsMatch && newPassword.isNotEmpty() && !isLoading

                                    SaharaButton(
                                        text = if (isLoading) (if (isEnglish) "Updating..." else "Update ho raha hai...") else (if (isEnglish) "Update Password" else "Password Update Karein"),
                                        onClick = {
                                            coroutineScope.launch {
                                                isLoading = true
                                                delay(1200)
                                                isLoading = false
                                                currentStep = ResetStep.SUCCESS
                                            }
                                        },
                                        enabled = isPasswordStepValid,
                                        isFullWidth = true,
                                        variant = if (isPasswordStepValid) ButtonVariant.SAHARASTRONGGREENGLASS else ButtonVariant.DEFAULT,
                                        hazeState = if (isPasswordStepValid) bgHazeState else null,
                                        isEnglish = isEnglish
                                    )
                                }

                                ResetStep.SUCCESS -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = primaryGreen, modifier = Modifier.size(80.dp))
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = if (isEnglish) "Password Updated! 🎉" else "Password Update Ho Gaya!",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = if (isEnglish) "Your password has been changed successfully. You can now login with your new credentials." else "Aapka password kamyabi se badal diya gaya hai. Ab aap login kar sakte hain.",
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium
                                        )

                                        Spacer(modifier = Modifier.height(32.dp))

                                        SaharaButton(
                                            text = if (isEnglish) "Back to Sign In" else "Sign In Par Wapis",
                                            onClick = onNavigateBack,
                                            isFullWidth = true,
                                            variant = ButtonVariant.SAHARASTRONGGREENGLASS,
                                            hazeState = bgHazeState,
                                            isEnglish = isEnglish
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}