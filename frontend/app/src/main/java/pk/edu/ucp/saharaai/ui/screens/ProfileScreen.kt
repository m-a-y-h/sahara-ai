package pk.edu.ucp.saharaai.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.ui.components.*
import pk.edu.ucp.saharaai.ui.theme.*

@Composable
fun ProfileScreen(
    navController: NavController,
    onNavigateToSettings: () -> Unit,
    onNavigateToEmergency: () -> Unit,
    onSignOut: () -> Unit,
    isEnglish: Boolean = false,
    fullName: String = "",
    email: String = "",
    isFromRegistration: Boolean = false
) {
    val hazeState = remember { HazeState() }
    val isDark = isSystemInDarkTheme()

    val finalName = fullName.ifBlank { GlobalAppState.userName }.ifBlank { "Sahara User" }
    val finalEmail = email.ifBlank { GlobalAppState.userEmail }.ifBlank { "user@sahara.ai" }

    var isPrivacyModeEnabled by remember { mutableStateOf(true) }

    val displayedEmail = remember(finalEmail, isPrivacyModeEnabled) {
        if (!isPrivacyModeEnabled) finalEmail
        else {
            val prefix = finalEmail.take(1).lowercase()
            val randomSuffix = (1..6).map { ('a'..'z').random() }.joinToString("")
            "$prefix...$randomSuffix@relay.sahara.ai"
        }
    }

    val bgGradient = if (isDark) {
        listOf(SaharaStrongGreen.copy(alpha = 0.2f), MaterialTheme.colorScheme.background.copy(alpha = 0.6f), MaterialTheme.colorScheme.background)
    } else {
        listOf(SaharaStrongGreen.copy(alpha = 0.25f), SaharaPeach.copy(alpha = 0.1f), MaterialTheme.colorScheme.background.copy(alpha = 0.2f))
    }
    val blob1Color = SaharaStrongGreen.copy(alpha = if (isDark) 0.25f else 0.15f)
    val blob2Color = SaharaSky.copy(alpha = if (isDark) 0.2f else 0.18f)

    val softTextColor = if (isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f)

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
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))

                ProfileAvatarSection(finalName, displayedEmail, softTextColor)

                Spacer(Modifier.height(32.dp))

                PrivacyShieldCard(
                    isPrivacyModeEnabled = isPrivacyModeEnabled,
                    onToggle = { isPrivacyModeEnabled = it },
                    isEnglish = isEnglish,
                    hazeState = hazeState,
                    softTextColor = softTextColor
                )

                Spacer(Modifier.height(24.dp))

                ProfileMenuSection(
                    title = if (isEnglish) "General" else "Aam Tarteebat",
                    items = listOf(
                        MenuItem(Icons.Default.Settings, SaharaLavender, if (isEnglish) "Settings" else "Settings", onNavigateToSettings),
                        MenuItem(Icons.Default.History, SaharaSky, if (isEnglish) "Activity Log" else "Sabiqa Record", {}),
                        MenuItem(Icons.Default.People, SaharaStrongGreen, if (isEnglish) "App Connections" else "App Connections", { navController.navigate("connections") })
                    ),
                    hazeState = hazeState,
                    softTextColor = softTextColor
                )

                Spacer(Modifier.height(16.dp))

                ProfileMenuSection(
                    title = if (isEnglish) "Support & Safety" else "Hifazat",
                    items = listOf(
                        MenuItem(Icons.Default.ShieldMoon, SaharaCoral, if (isEnglish) "Emergency Helplines" else "Hanggami Madad", onNavigateToEmergency),
                        MenuItem(Icons.Default.HelpOutline, SaharaLavender, if (isEnglish) "Help Center" else "Madad", {})
                    ),
                    hazeState = hazeState,
                    softTextColor = softTextColor
                )

                Spacer(Modifier.height(32.dp))

                SaharaButton(
                    text = if (isEnglish) "Sign Out" else "Log Out Karein",
                    onClick = onSignOut,
                    variant = ButtonVariant.DESTRUCTIVE,
                    isFullWidth = true,
                    modifier = Modifier.padding(bottom = 40.dp)
                )
            }
        }
    }
}

data class MenuItem(val icon: ImageVector, val color: Color, val label: String, val onClick: () -> Unit)

@Composable
private fun ProfileAvatarSection(name: String, email: String, softTextColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(100.dp).background(SaharaStrongGreen.copy(0.1f), CircleShape))
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(SaharaStrongGreen, SaharaSky)))
            ) {
                Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(45.dp).align(Alignment.Center))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = SaharaStrongGreen)
        Text(email, style = MaterialTheme.typography.bodyMedium, color = softTextColor.copy(alpha = 0.6f))
    }
}

@Composable
private fun PrivacyShieldCard(isPrivacyModeEnabled: Boolean, onToggle: (Boolean) -> Unit, isEnglish: Boolean, hazeState: HazeState, softTextColor: Color) {
    SaharaCard(
        variant = CardVariant.GLASS,
        hazeState = hazeState,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(SaharaStrongGreen.copy(0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPrivacyModeEnabled) Icons.Default.VerifiedUser else Icons.Default.GppBad,
                    null,
                    tint = SaharaStrongGreen
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEnglish) "Email Mask" else "Email Mask",
                    fontWeight = FontWeight.Bold,
                    color = softTextColor
                )
                Text(
                    text = if (isPrivacyModeEnabled) {
                        if (isEnglish) "Email masking is active" else "Email chupi hui hai"
                    } else {
                        if (isEnglish) "Email masking is inactive" else "Email mask band hai"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = softTextColor.copy(alpha = 0.6f)
                )
            }
            Switch(
                checked = isPrivacyModeEnabled,
                onCheckedChange = onToggle,
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
private fun ProfileMenuSection(title: String, items: List<MenuItem>, hazeState: HazeState, softTextColor: Color) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = softTextColor,
            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
        )
        SaharaCard(
            variant = CardVariant.GLASS,
            hazeState = hazeState,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                items.forEachIndexed { index, item ->
                    ProfileMenuItem(item, softTextColor)
                    if (index < items.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            color = softTextColor.copy(alpha = 0.08f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileMenuItem(item: MenuItem, softTextColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { item.onClick() }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(item.color.copy(0.15f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.icon, null, tint = item.color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = item.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = softTextColor
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = softTextColor.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
    }
}