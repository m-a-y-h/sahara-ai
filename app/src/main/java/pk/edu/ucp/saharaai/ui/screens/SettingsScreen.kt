package pk.edu.ucp.saharaai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.ui.components.*
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.utils.ObservePermissionState
import pk.edu.ucp.saharaai.utils.PermissionCopy
import pk.edu.ucp.saharaai.utils.rememberAppPermissionRequester
import pk.edu.ucp.saharaai.viewmodels.SettingsViewModel

@Composable
fun SettingsScreen(
    navController: NavController,
    onNavigateBack: () -> Unit = { navController.popBackStack() },
    isEnglish: Boolean = false,
    userName: String = "User",
    onLanguageChange: (Boolean) -> Unit = {},
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val hazeState = remember { HazeState() }
    val isDark = isSystemInDarkTheme()

    
    val biometricManager = remember { BiometricManager.from(context) }
    val deviceHasBiometric = remember {
        biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    LaunchedEffect(Unit) { settingsViewModel.initialize(context) }
    val biometricEnabled = settingsViewModel.biometricEnabled
    val passwordBackupEnabled = settingsViewModel.passwordBackupEnabled
    val privacyModeEnabled = settingsViewModel.privacyModeEnabled

    val softTextColor = if (isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f)

    var pushNotifications by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    var sounds by remember { mutableStateOf(true) }
    var vibration by remember { mutableStateOf(true) }

    val notificationPermissionRequester = rememberAppPermissionRequester(
        permission = Manifest.permission.POST_NOTIFICATIONS,
        isEnglish = isEnglish,
        copy = PermissionCopy(
            deniedEn = "Notification permission was denied.",
            deniedUr = "Notifications ki ijazat nahi di gayi.",
            settingsEn = "Enable notifications in App settings to receive alerts.",
            settingsUr = "Alerts ke liye App settings mein notifications ki ijazat dein.",
        ),
        onGranted = { pushNotifications = true },
        onDenied = { pushNotifications = false },
    )
    ObservePermissionState(notificationPermissionRequester) {
        pushNotifications = it
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScreenBackdrop(hazeState)

        Scaffold(
            bottomBar = { BottomNav(navController = navController, hazeState = hazeState) },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HazeBackButton(
                        onClick = onNavigateBack,
                        hazeState = hazeState,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = if (isEnglish) "Settings" else "Tarteebat",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = SaharaStrongGreen,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(Modifier.height(32.dp))

                Text(
                    text = if (isEnglish) "Alerts" else "Itila-aat",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = softTextColor,
                    modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsToggleCard(
                        icon = Icons.Default.Notifications,
                        color = SaharaSky,
                        title = if (isEnglish) "Push Notifications" else "Push Notifications",
                        checked = pushNotifications,
                        hazeState = hazeState,
                        softTextColor = softTextColor,
                        isStaticStyle = true,
                        onCheckedChange = { 
                            if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionRequester.request()
                            } else {
                                pushNotifications = it
                            }
                        }
                    )
                    SettingsToggleCard(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        color = SaharaLavender,
                        title = if (isEnglish) "System Sounds" else "App mein Awaaz",
                        checked = sounds,
                        hazeState = hazeState,
                        softTextColor = softTextColor,
                        isStaticStyle = true,
                        onCheckedChange = { sounds = it }
                    )
                    SettingsToggleCard(
                        icon = Icons.Default.Vibration,
                        color = SaharaStrongGreen,
                        title = if (isEnglish) "Vibration & Haptics" else "Vibration",
                        checked = vibration,
                        hazeState = hazeState,
                        softTextColor = softTextColor,
                        isStaticStyle = true,
                        onCheckedChange = { vibration = it }
                    )
                }

                Spacer(Modifier.height(32.dp))

                
                Text(
                    text = if (isEnglish) "Security" else "Hifazat",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = softTextColor,
                    modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        if (deviceHasBiometric) {
                            CompactSecurityToggleCard(
                                icon = Icons.Default.Fingerprint,
                                color = SaharaStrongGreen,
                                title = if (isEnglish) "Fingerprint Login"
                                        else "Fingerprint Login",
                                checked = biometricEnabled,
                                hazeState = hazeState,
                                softTextColor = softTextColor,
                                modifier = Modifier.weight(1f),
                                onCheckedChange = { newVal ->
                                    settingsViewModel.toggleBiometric(context, newVal)
                                }
                            )
                        } else {

                            SaharaCard(
                                variant = CardVariant.GLASS,
                                hazeState = hazeState,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Fingerprint,
                                        null,
                                        tint = softTextColor.copy(alpha = 0.3f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = if (isEnglish) "Biometric Login" else "Biometric Login",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = softTextColor.copy(alpha = 0.4f)
                                    )
                                    Text(
                                        text = if (isEnglish) "Unavailable" else "Dastiyab nahi",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = softTextColor.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                        CompactSecurityToggleCard(
                            icon = Icons.Default.VisibilityOff,
                            color = SaharaSky,
                            title = if (isEnglish) "Email Mask" else "Email Chupayein",
                            checked = privacyModeEnabled,
                            hazeState = hazeState,
                            softTextColor = softTextColor,
                            modifier = Modifier.weight(1f),
                            onCheckedChange = { settingsViewModel.togglePrivacyMode(context, it) },
                        )
                    }

                    SettingsToggleCard(
                        icon = Icons.Default.Lock,
                        color = SaharaLavender,
                        title = if (isEnglish) "Email Password Backup" else "Email Password Backup",
                        checked = passwordBackupEnabled,
                        hazeState = hazeState,
                        softTextColor = softTextColor,
                        onCheckedChange = { enabled ->
                            if (enabled) settingsViewModel.requestPasswordBackup(context)
                        },
                    )
                }

                Spacer(Modifier.height(32.dp))

                Text(
                    text = if (isEnglish) "Language" else "Zaban",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = softTextColor,
                    modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsLanguageCard(
                        title = "Urdu (Roman)",
                        subtitle = "اردو",
                        isSelected = !isEnglish,
                        hazeState = hazeState,
                        softTextColor = softTextColor,
                        onClick = { onLanguageChange(false) }
                    )

                    SettingsLanguageCard(
                        title = "English",
                        subtitle = if (isEnglish) "English" else "انگریزی",
                        isSelected = isEnglish,
                        hazeState = hazeState,
                        softTextColor = softTextColor,
                        onClick = { onLanguageChange(true) }
                    )
                }

                Spacer(Modifier.height(40.dp))

                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Sahara AI", style = MaterialTheme.typography.labelMedium, color = SaharaStrongGreen.copy(0.6f))
                    Text("Version 1.0.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                }

                Spacer(Modifier.height(40.dp))
            }
        }

        BiometricEnrollmentDialog(
            state = settingsViewModel.biometricEnrollmentState,
            isEnglish = isEnglish,
            hazeState = hazeState,
            onDismiss = { settingsViewModel.dismissBiometricEnrollmentState() },
        )
        PasswordBackupDialog(
            state = settingsViewModel.passwordBackupState,
            isEnglish = isEnglish,
            hazeState = hazeState,
            onSubmit = { pwd -> settingsViewModel.completePasswordBackup(context, pwd) },
            onDismiss = { settingsViewModel.cancelPasswordBackup() },
        )
    }
}

@Composable
private fun BiometricEnrollmentDialog(
    state: SettingsViewModel.BiometricEnrollmentState,
    isEnglish: Boolean,
    hazeState: HazeState,
    onDismiss: () -> Unit,
) {
    if (state is SettingsViewModel.BiometricEnrollmentState.Idle) return

    GlassAlertDialog(
        hazeState = hazeState,
        onDismissRequest = {
            if (state !is SettingsViewModel.BiometricEnrollmentState.Working) onDismiss()
        },
        title = {
            Text(
                if (state is SettingsViewModel.BiometricEnrollmentState.Working) {
                    if (isEnglish) "Updating fingerprint login" else "Fingerprint login update ho raha hai"
                } else {
                    if (isEnglish) "Fingerprint login" else "Fingerprint login"
                }
            )
        },
        text = {
            when (state) {
                is SettingsViewModel.BiometricEnrollmentState.Working -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        color = SaharaStrongGreen,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (isEnglish)
                            "Securing this device for fingerprint login."
                        else
                            "Is device ko fingerprint login ke liye secure kiya ja raha hai."
                    )
                }
                is SettingsViewModel.BiometricEnrollmentState.Error -> Text(state.message)
                SettingsViewModel.BiometricEnrollmentState.Idle -> Unit
            }
        },
        confirmButton = {
            if (state is SettingsViewModel.BiometricEnrollmentState.Error) {
                TextButton(onClick = onDismiss) {
                    Text(if (isEnglish) "OK" else "Theek hai")
                }
            }
        },
    )
}

@Composable
private fun PasswordBackupDialog(
    state: SettingsViewModel.PasswordBackupState,
    isEnglish: Boolean,
    hazeState: HazeState,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (state is SettingsViewModel.PasswordBackupState.Idle) return

    val request = state as? SettingsViewModel.PasswordBackupState.Requested
    val submitting = state is SettingsViewModel.PasswordBackupState.Submitting
    val email = request?.email ?: (state as? SettingsViewModel.PasswordBackupState.Submitting)?.email.orEmpty()
    val requestErrorMessage = request?.errorMessage.orEmpty()
    var password by remember(state) { mutableStateOf("") }
    var confirmPassword by remember(state) { mutableStateOf("") }
    var passwordVisible by remember(state) { mutableStateOf(false) }

    if (state is SettingsViewModel.PasswordBackupState.Error) {
        GlassAlertDialog(
            hazeState = hazeState,
            onDismissRequest = onDismiss,
            title = { Text(if (isEnglish) "Password backup" else "Password backup") },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(if (isEnglish) "OK" else "Theek hai")
                }
            },
        )
        return
    }

    GlassAlertDialog(
        hazeState = hazeState,
        onDismissRequest = { if (!submitting) onDismiss() },
        title = {
            Text(
                if (isEnglish) "Add email/password login"
                else "Email/password login add karein"
            )
        },
        text = {
            Column {
                Text(
                    if (isEnglish)
                        "Set a backup password for this email. Google sign-in will keep working."
                    else
                        "Is email ke liye backup password set karein. Google sign-in bhi chalti rahegi."
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    enabled = !submitting,
                    label = { Text(if (isEnglish) "Password" else "Password") },
                    visualTransformation = if (passwordVisible) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                    ),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    singleLine = true,
                    enabled = !submitting,
                    label = { Text(if (isEnglish) "Confirm password" else "Password dobara") },
                    visualTransformation = if (passwordVisible) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    ),
                    isError = confirmPassword.isNotEmpty() && password != confirmPassword,
                    supportingText = {
                        if (confirmPassword.isNotEmpty() && password != confirmPassword) {
                            Text(
                                if (isEnglish) "Passwords do not match" else "Password aik jaisa nahi hai",
                                color = SaharaCoral,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (requestErrorMessage.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = requestErrorMessage,
                        color = SaharaCoral,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            val canSubmit = !submitting &&
                password.isNotBlank() &&
                confirmPassword.isNotBlank() &&
                password == confirmPassword
            TextButton(
                enabled = canSubmit,
                onClick = { onSubmit(password) },
            ) {
                Text(
                    if (submitting) (if (isEnglish) "Working..." else "Ho raha hai...")
                    else (if (isEnglish) "Save password" else "Password save karein"),
                    color = SaharaStrongGreen,
                )
            }
        },
        dismissButton = {
            TextButton(enabled = !submitting, onClick = onDismiss) {
                Text(if (isEnglish) "Cancel" else "Cancel")
            }
        },
    )
}

@Composable
fun SettingsToggleCard(
    icon: ImageVector,
    color: Color,
    title: String,
    checked: Boolean,
    hazeState: HazeState,
    softTextColor: Color,
    isStaticStyle: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val labelColor = if (isStaticStyle) {
        softTextColor
    } else {
        if (checked) {
            if (isDark) Color.White else SaharaStrongGreen
        } else {
            softTextColor
        }
    }

    SaharaCard(
        variant = CardVariant.GLASS,
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!isStaticStyle && checked) Modifier.border(1.5.dp, SaharaStrongGreen, RoundedCornerShape(24.dp))
                else Modifier
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).background(color.copy(0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = labelColor,
                    fontWeight = if (!isStaticStyle && checked) FontWeight.Bold else FontWeight.Normal
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SaharaStrongGreen,
                    uncheckedTrackColor = Color.Gray.copy(0.2f)
                )
            )
        }
    }
}

@Composable
private fun CompactSecurityToggleCard(
    icon: ImageVector,
    color: Color,
    title: String,
    checked: Boolean,
    hazeState: HazeState,
    softTextColor: Color,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.size(36.dp).background(color.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = softTextColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SaharaStrongGreen,
                    uncheckedTrackColor = Color.Gray.copy(alpha = 0.2f),
                ),
            )
        }
    }
}

@Composable
fun SettingsLanguageCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    hazeState: HazeState,
    softTextColor: Color,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()

    val finalTitleColor = if (isDark) {
        if (isSelected) Color.White else softTextColor
    } else {
        if (isSelected) SaharaStrongGreen else softTextColor
    }

    val finalSubColor = if (isDark) {
        if (isSelected) Color.White.copy(0.7f) else softTextColor.copy(0.5f)
    } else {
        if (isSelected) SaharaStrongGreen.copy(0.7f) else softTextColor.copy(0.7f)
    }

    SaharaCard(
        variant = CardVariant.GLASS,
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.border(1.5.dp, SaharaStrongGreen, RoundedCornerShape(24.dp))
                else Modifier
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    color = finalTitleColor,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, color = finalSubColor)
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = SaharaStrongGreen,
                    unselectedColor = softTextColor.copy(0.3f)
                )
            )
        }
    }
}
