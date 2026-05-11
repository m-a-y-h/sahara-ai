package pk.edu.ucp.saharaai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.ui.components.*
import pk.edu.ucp.saharaai.ui.theme.*

@Composable
fun SettingsScreen(
    navController: NavController,
    onNavigateBack: () -> Unit = { navController.popBackStack() },
    isEnglish: Boolean = false,
    userName: String = "User",
    onLanguageChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val hazeState = remember { HazeState() }
    val isDark = isSystemInDarkTheme()

    val bgGradient = if (isDark) {
        listOf(SaharaStrongGreen.copy(alpha = 0.2f), MaterialTheme.colorScheme.background.copy(alpha = 0.6f), MaterialTheme.colorScheme.background)
    } else {
        listOf(SaharaStrongGreen.copy(alpha = 0.25f), SaharaPeach.copy(alpha = 0.1f), MaterialTheme.colorScheme.background.copy(alpha = 0.2f))
    }
    val blob1Color = SaharaStrongGreen.copy(alpha = if (isDark) 0.25f else 0.15f)
    val blob2Color = SaharaSky.copy(alpha = if (isDark) 0.2f else 0.18f)

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

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        pushNotifications = isGranted
    }

    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val blobRotation by infiniteTransition.animateFloat(
        initialValue = -10f, targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(7000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "rotation"
    )
    val blobScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(5000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "scale"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .background(Brush.verticalGradient(bgGradient))
        ) {
            Box(
                modifier = Modifier
                    .size(350.dp)
                    .offset(x = (-80).dp, y = (-50).dp)
                    .rotate(blobRotation)
                    .scale(blobScale)
                    .background(Brush.radialGradient(listOf(blob1Color, Color.Transparent)))
            )
            Box(
                modifier = Modifier
                    .size(400.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 100.dp, y = 50.dp)
                    .rotate(-blobRotation)
                    .scale(blobScale)
                    .background(Brush.radialGradient(listOf(blob2Color, Color.Transparent)))
            )
        }

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
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color.White.copy(0.1f) else Color.Black.copy(0.05f))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = SaharaStrongGreen)
                    }
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
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
    }
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