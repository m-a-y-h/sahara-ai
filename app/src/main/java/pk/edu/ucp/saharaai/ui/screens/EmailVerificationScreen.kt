package pk.edu.ucp.saharaai.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import pk.edu.ucp.saharaai.ui.components.*
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.viewmodels.EmailVerificationViewModel


@Composable
fun EmailVerificationScreen(
    email         : String,
    name          : String,
    uid           : String,
    onVerified    : () -> Unit,
    onNavigateBack: () -> Unit,
    isEnglish     : Boolean = false,
    verificationViewModel: EmailVerificationViewModel = viewModel()
) {
    val isDark       = isSystemInDarkTheme()
    val primaryGreen = if (isDark) SaharaStrongGreen else SaharaGreen
    val hazeState    = remember { HazeState() }

    
    var otpValue      by remember { mutableStateOf("") }            
    val isLoading = verificationViewModel.isLoading
    val isSending = verificationViewModel.isSending
    val errorMsg = verificationViewModel.errorMsg
    val isVerified = verificationViewModel.isVerified

    
    var expirySeconds  by remember { mutableStateOf(600) }
    
    var resendCooldown by remember { mutableStateOf(60) }
    var resendCount    by remember { mutableIntStateOf(0) }

    val focusRequester = remember { FocusRequester() }

    
    val sendOtp: () -> Unit = { verificationViewModel.sendOtp(email, name, uid, isEnglish) }

    
    LaunchedEffect(Unit) {
        if (uid.isNotBlank()) sendOtp()
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    
    LaunchedEffect(resendCount) {
        expirySeconds = 600
        while (expirySeconds > 0) { delay(1000L); expirySeconds-- }
    }

    
    LaunchedEffect(resendCount) {
        resendCooldown = 60
        while (resendCooldown > 0) { delay(1000L); resendCooldown-- }
    }

    val timerText = "${expirySeconds / 60}:${(expirySeconds % 60).toString().padStart(2, '0')}"

    
    Box(Modifier.fillMaxSize()) {
        
        ScreenBackdrop(hazeState)

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(24.dp))

            
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                HazeBackButton(onClick = onNavigateBack, hazeState = hazeState, tint = primaryGreen)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        if (isEnglish) "Email Verification" else "Email Verification",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryGreen
                    )
                    Text(
                        if (isEnglish) "Confirm your identity" else "Apni pehchaan confirm karein",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            SaharaCard(
                variant = CardVariant.GLASS,
                hazeState = hazeState,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {

                    if (isVerified) {
                        
                        Icon(Icons.Default.CheckCircle, null, tint = SaharaStrongGreen, modifier = Modifier.size(72.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (isEnglish) "Email Verified!" else "Email Verify Ho Gayi!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (isEnglish) "Your account is now active." else "Aapka account ab active hai.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        SaharaButton(
                            text      = if (isEnglish) "Continue" else "Aage Barein",
                            onClick   = onVerified,
                            isFullWidth = true,
                            variant   = ButtonVariant.SAHARASTRONGGREENGLASS,
                            hazeState = hazeState,
                            isEnglish = isEnglish
                        )

                    } else {
                        
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(primaryGreen.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (isSending) Icons.Default.Email else Icons.Default.VerifiedUser,
                                null,
                                tint = primaryGreen,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            if (isEnglish) "Enter verification code" else "Code darj karein",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (isEnglish) "We sent a 6-digit code to"
                            else           "6-digit code bheja gaya hai",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            email,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = primaryGreen,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(24.dp))

                        
                        OtpInputField(
                            value          = otpValue,
                            onValueChange  = { v ->
                                otpValue = v.filter { it.isDigit() }.take(6)
                                verificationViewModel.clearError()
                            },
                            focusRequester = focusRequester,
                            primaryColor   = primaryGreen,
                            modifier       = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))

                        
                        if (expirySeconds > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Text(
                                    if (isEnglish) "Code expires in " else "$timerText mein expire hoga",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isEnglish) Text(
                                    timerText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (expirySeconds < 60) SaharaCoral else primaryGreen
                                )
                            }
                        } else {
                            Text(
                                if (isEnglish) "Code expired. Please resend." else "Code expire ho gaya. Dobara bhejein.",
                                style = MaterialTheme.typography.labelSmall,
                                color = SaharaCoral
                            )
                        }

                        AnimatedVisibility(errorMsg.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Card(
                                colors   = CardDefaults.cardColors(containerColor = SaharaCoral.copy(.12f)),
                                shape    = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                    Text(errorMsg, color = SaharaCoral, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        
                        if (isLoading) {
                            CircularProgressIndicator(color = primaryGreen, modifier = Modifier.size(36.dp))
                        } else {
                            SaharaButton(
                                text      = if (isEnglish) "Verify Code" else "Code Verify Karein",
                                onClick   = {
                                    if (otpValue.length < 6) {
                                        verificationViewModel.reportError(if (isEnglish) "Please enter all 6 digits." else "Puri 6 digits daalen.")
                                        return@SaharaButton
                                    }
                                    verificationViewModel.verifyOtp(uid, otpValue, isEnglish)
                                },
                                enabled   = otpValue.length == 6 && !isLoading && expirySeconds > 0,
                                isFullWidth = true,
                                variant   = if (otpValue.length == 6 && expirySeconds > 0) ButtonVariant.SAHARASTRONGGREENGLASS else ButtonVariant.DEFAULT,
                                hazeState = if (otpValue.length == 6 && expirySeconds > 0) hazeState else null,
                                isEnglish = isEnglish
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        
                        if (isSending) {
                            Text(
                                if (isEnglish) "Sending code…" else "Code bheja ja raha hai…",
                                style = MaterialTheme.typography.labelMedium,
                                color = primaryGreen
                            )
                        } else if (resendCooldown > 0) {
                            Text(
                                if (isEnglish) "Resend available in ${resendCooldown}s"
                                else           "Dobara bhejne ke liye ${resendCooldown}s intzaar karein",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            TextButton(onClick = {
                                otpValue = ""
                                resendCount++
                                sendOtp()
                            }) {
                                Text(
                                    if (isEnglish) "Didn't receive it? Resend Code"
                                    else           "Code nahi aaya? Dobara Bhejein",
                                    color = primaryGreen,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}



@Composable
fun OtpInputField(
    value         : String,
    onValueChange : (String) -> Unit,
    focusRequester: FocusRequester,
    primaryColor  : Color,
    modifier      : Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()

    Box(modifier) {
        
        Row(
            modifier            = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(6) { i ->
                val char      = value.getOrNull(i)?.toString() ?: ""
                val isCurrent = value.length == i

                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)           
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isDark) MaterialTheme.colorScheme.surface.copy(.5f)
                            else        MaterialTheme.colorScheme.surface.copy(.8f)
                        )
                        .border(
                            width = if (isCurrent) 2.dp else 1.dp,
                            color = when {
                                isCurrent         -> primaryColor
                                char.isNotEmpty() -> primaryColor.copy(.5f)
                                else              -> MaterialTheme.colorScheme.outline.copy(.3f)
                            },
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (char.isNotEmpty()) {
                        Text(
                            text       = char,
                            fontSize   = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color      = primaryColor
                        )
                    } else if (isCurrent) {
                        
                        val alpha = rememberFrameOscillation(0f, 1f, 500)
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(24.dp)
                                .background(primaryColor.copy(alpha))
                        )
                    }
                }
            }
        }

        
        BasicTextField(
            value         = value,
            onValueChange = { new ->
                val digits = new.filter { it.isDigit() }.take(6)
                onValueChange(digits)
            },
            textStyle       = TextStyle(color = Color.Transparent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            cursorBrush     = SolidColor(Color.Transparent),
            modifier        = Modifier
                .matchParentSize()
                .focusRequester(focusRequester)
                
                .background(Color.Transparent)
        )
    }
}
