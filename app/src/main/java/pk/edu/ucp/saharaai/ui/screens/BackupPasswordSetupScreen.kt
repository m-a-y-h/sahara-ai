package pk.edu.ucp.saharaai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import pk.edu.ucp.saharaai.data.repository.AuthRepository
import pk.edu.ucp.saharaai.data.repository.FirebaseAuthFailure
import pk.edu.ucp.saharaai.ui.components.ButtonVariant
import pk.edu.ucp.saharaai.ui.components.SaharaButton
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.util.BiometricCredentialVault

/**
 * Shown once after a successful Google sign-in for users whose Firebase
 * account does not yet have an email/password credential attached.
 *
 * Why exist: signing in with Google gives us no password we can seal inside
 * the keystore-backed [BiometricCredentialVault], so the biometric chip on
 * Login never appears for these users. Setting a password here calls
 * [AuthRepository.linkEmailPassword], which adds the credential to the
 * SAME Firebase user — Google sign-in still works, AND email/password +
 * fingerprint become available.
 *
 * Skippable. If skipped, the user can still wire it up later via
 * Settings -> Fingerprint Login.
 */
@Composable
fun BackupPasswordSetupScreen(
    email: String,
    onboardingCompleted: Boolean,
    isEnglish: Boolean,
    onContinue: (onboardingCompleted: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val hazeState = remember { HazeState() }
    val authRepository = remember { AuthRepository() }
    val scope = rememberCoroutineScope()

    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isWorking by remember { mutableStateOf(false) }
    var inlineError by remember { mutableStateOf("") }

    val primaryGreen = if (isDark) SaharaStrongGreen else SaharaGreen
    val passwordsMatch = password.isNotEmpty() && password == confirmPassword
    val isValid = password.length >= 6 && passwordsMatch && !isWorking

    val bgGradient = if (isDark) {
        listOf(SaharaStrongGreen.copy(alpha = 0.20f), MaterialTheme.colorScheme.background.copy(alpha = 0.6f), MaterialTheme.colorScheme.background)
    } else {
        listOf(SaharaStrongGreen.copy(alpha = 0.25f), SaharaPeach.copy(alpha = 0.1f), MaterialTheme.colorScheme.background.copy(alpha = 0.2f))
    }

    fun submit() {
        if (!isValid) return
        inlineError = ""
        isWorking = true
        scope.launch {
            val failure = authRepository.linkEmailPassword(email, password)
            if (failure != null) {
                isWorking = false
                inlineError = failureMessage(failure, isEnglish)
                return@launch
            }
            BiometricCredentialVault.save(context, email, password)
            val prefs = context.applicationContext
                .getSharedPreferences("sahara_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putBoolean("biometric_enabled", true).apply()
            val uid = Firebase.auth.currentUser?.uid.orEmpty()
            if (uid.isNotBlank()) {
                runCatching { RealtimeDBService.setBiometricEnabled(uid, true) }
            }
            isWorking = false
            onContinue(onboardingCompleted)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .background(Brush.verticalGradient(bgGradient))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            Spacer(Modifier.height(48.dp))
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(primaryGreen.copy(alpha = 0.15f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = primaryGreen,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = if (isEnglish) "Set a backup password" else "Backup password set karein",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = primaryGreen,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isEnglish)
                    "Sahara seals this password behind your fingerprint on this device. You'll still sign in with Google the way you do now — this just unlocks biometric login and an offline backup."
                else
                    "Sahara is password ko aapke fingerprint ke peeche device par mehfooz rakhega. Google sign-in pehle ki tarah chalti rahegi — yeh sirf fingerprint login aur offline backup ke liye hai.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = email,
                style = MaterialTheme.typography.bodySmall,
                color = primaryGreen,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                enabled = !isWorking,
                label = { Text(if (isEnglish) "New password" else "Naya password") },
                leadingIcon = { Icon(Icons.Default.Lock, null, tint = primaryGreen) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = primaryGreen,
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                singleLine = true,
                enabled = !isWorking,
                label = { Text(if (isEnglish) "Confirm password" else "Password dobara") },
                leadingIcon = { Icon(Icons.Default.Lock, null, tint = primaryGreen) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                isError = confirmPassword.isNotEmpty() && !passwordsMatch,
                supportingText = {
                    when {
                        confirmPassword.isNotEmpty() && !passwordsMatch ->
                            Text(if (isEnglish) "Passwords don't match" else "Passwords match nahi karte", color = SaharaCoral)
                        password.isNotEmpty() && password.length < 6 ->
                            Text(if (isEnglish) "Use at least 6 characters" else "Kam-az-kam 6 letter rakhein")
                        else -> Text("")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (inlineError.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = inlineError,
                    color = SaharaCoral,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(24.dp))

            SaharaButton(
                text = if (isWorking) (if (isEnglish) "Saving..." else "Save ho raha hai...")
                       else (if (isEnglish) "Set password" else "Password set karein"),
                onClick = { submit() },
                isFullWidth = true,
                enabled = isValid,
                variant = if (isValid) ButtonVariant.SAHARASTRONGGREENGLASS else ButtonVariant.DEFAULT,
                hazeState = if (isValid) hazeState else null,
                isEnglish = isEnglish,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = { if (!isWorking) onContinue(onboardingCompleted) },
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (isEnglish) "Skip for now" else "Abhi skip karein",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isEnglish)
                    "You can set this up later from Settings -> Fingerprint Login."
                else
                    "Aap baad mein Settings -> Fingerprint Login se bhi set kar sakte hain.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(48.dp))
        }
    }
}

private fun failureMessage(failure: FirebaseAuthFailure, isEnglish: Boolean): String = when (failure) {
    FirebaseAuthFailure.EMAIL_ALREADY_IN_USE -> if (isEnglish)
        "That email is already linked to another account."
    else "Yeh email kisi aur account ke saath link hai."
    FirebaseAuthFailure.WEAK_PASSWORD -> if (isEnglish)
        "Password is too weak - try at least 8 characters with letters and numbers."
    else "Password kamzor hai - 8+ letters letters/numbers ke saath rakhein."
    FirebaseAuthFailure.NETWORK -> if (isEnglish)
        "Network error. Check your connection and try again."
    else "Network error. Connection check karke dobara koshish karein."
    FirebaseAuthFailure.TOO_MANY_REQUESTS -> if (isEnglish)
        "Too many attempts. Try again in a few minutes."
    else "Bohat zyada koshishein. Kuch deir baad try karein."
    FirebaseAuthFailure.USER_DISABLED -> if (isEnglish)
        "This account has been disabled."
    else "Yeh account disable kar diya gaya hai."
    FirebaseAuthFailure.EMAIL_PASSWORD_DISABLED,
    FirebaseAuthFailure.CONFIGURATION_MISSING,
    FirebaseAuthFailure.INVALID_CREDENTIALS,
    FirebaseAuthFailure.UNKNOWN -> if (isEnglish)
        "Could not set the password. Try again."
    else "Password set nahi ho saka. Dobara try karein."
}
