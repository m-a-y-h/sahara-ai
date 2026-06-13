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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import pk.edu.ucp.saharaai.ui.components.*
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.viewmodels.ForgotPasswordViewModel

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
    onNavigateToLogin: () -> Unit = onNavigateBack,
    isEnglish: Boolean = false,
    passwordViewModel: ForgotPasswordViewModel = viewModel()
) {
    val isDark = isSystemInDarkTheme()
    val primaryGreen = if (isDark) SaharaStrongGreen else SaharaGreen

    val bgHazeState = remember { HazeState() }

    var currentStep by remember { mutableStateOf(ResetStep.ENTER_EMAIL) }
    var email by remember { mutableStateOf("") }
    val errorMsg = passwordViewModel.errorMessage
    val statusMsg = passwordViewModel.statusMessage
    val isLoading = passwordViewModel.isLoading
    var resendCooldown by remember { mutableIntStateOf(0) }

    val isEmailValid = isValidEmail(email) || email.isEmpty()

    LaunchedEffect(currentStep, resendCooldown) {
        if (currentStep == ResetStep.SUCCESS && resendCooldown > 0) {
            delay(1000L)
            resendCooldown--
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        ScreenBackdrop(bgHazeState)

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
                    HazeBackButton(onClick = onNavigateBack, hazeState = bgHazeState, tint = primaryGreen)
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
                                        text = if (isEnglish) "Enter your email and we'll send you a password reset link."
                                               else "Apni email daalein aur hum aapko password reset link bhejenge.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    Spacer(modifier = Modifier.height(24.dp))

                                    OutlinedTextField(
                                        value = email,
                                        onValueChange = { email = it.trim(); passwordViewModel.clearAuthError() },
                                        label = { Text(if (isEnglish) "Email Address" else "Email Address") },
                                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = primaryGreen) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true,
                                        isError = !isEmailValid,
                                        supportingText = { if (!isEmailValid) Text(if (isEnglish) "Enter a valid email address" else "Sahih email address darj karein") }
                                    )

                                    if (errorMsg.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = SaharaCoral.copy(alpha = 0.12f)
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Text(
                                                    text = "⚠️  ",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                Text(
                                                    text = errorMsg,
                                                    color = SaharaCoral,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))

                                    val isEmailStepValid = isValidEmail(email) && !isLoading

                                    if (isLoading) {
                                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(color = primaryGreen)
                                        }
                                    } else {
                                        SaharaButton(
                                            text = if (isEnglish) "Send Reset Link" else "Reset Link Bhejein",
                                            onClick = {
                                                passwordViewModel.requestResetLink(email, isEnglish, onSuccess = {
                                                    currentStep = ResetStep.SUCCESS
                                                    resendCooldown = 60
                                                })
                                            },
                                            enabled = isEmailStepValid,
                                            isFullWidth = true,
                                            variant = if (isEmailStepValid) ButtonVariant.SAHARASTRONGGREENGLASS else ButtonVariant.DEFAULT,
                                            hazeState = if (isEmailStepValid) bgHazeState else null,
                                            isEnglish = isEnglish
                                        )
                                    }
                                }

                                ResetStep.SUCCESS -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = primaryGreen,
                                            modifier = Modifier.size(80.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = if (isEnglish) "Check Your Email" else "Apni Email Dekhein",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = if (isEnglish)
                                                "We sent a password reset link to:"
                                            else
                                                "Hum ne password reset link bheji hai:",
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = email,
                                            textAlign = TextAlign.Center,
                                            color = primaryGreen,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = if (isEnglish)
                                                "Click the link in the email to reset your password. Check your spam folder if you don't see it."
                                            else
                                                "Email mein link par click karke password reset karein. Agar email nazar na aaye to spam folder dekhein.",
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )

                                        if (errorMsg.isNotBlank() || statusMsg.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (errorMsg.isNotBlank()) {
                                                        SaharaCoral.copy(alpha = 0.12f)
                                                    } else {
                                                        primaryGreen.copy(alpha = 0.12f)
                                                    }
                                                ),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = errorMsg.ifBlank { statusMsg },
                                                    color = if (errorMsg.isNotBlank()) SaharaCoral else primaryGreen,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(32.dp))

                                        SaharaButton(
                                            text = if (isEnglish) "Back to Sign In" else "Sign In Par Wapis",
                                            onClick = onNavigateToLogin,
                                            isFullWidth = true,
                                            variant = ButtonVariant.SAHARASTRONGGREENGLASS,
                                            hazeState = bgHazeState,
                                            isEnglish = isEnglish
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        when {
                                            isLoading -> Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                CircularProgressIndicator(
                                                    color = primaryGreen,
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    if (isEnglish) "Sending reset link..." else "Reset link bheja ja raha hai...",
                                                    color = primaryGreen,
                                                    style = MaterialTheme.typography.labelMedium,
                                                )
                                            }
                                            resendCooldown > 0 -> Text(
                                                text = if (isEnglish) {
                                                    "Resend available in ${resendCooldown}s"
                                                } else {
                                                    "Dobara bhejne ke liye ${resendCooldown}s intzaar karein"
                                                },
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.labelSmall,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            else -> TextButton(
                                                onClick = {
                                                    passwordViewModel.requestResetLink(
                                                        email,
                                                        isEnglish,
                                                        onSuccess = { resendCooldown = 60 },
                                                        isResend = true,
                                                    )
                                                }
                                            ) {
                                                Text(
                                                    text = if (isEnglish) "Didn't receive it? Resend" else "Email nahi aayi? Dobara Bhejein",
                                                    color = primaryGreen,
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                        }
                                    }
                                }

                                else -> {  }
                            }
                        }
                    }
                }
            }
        }
}
