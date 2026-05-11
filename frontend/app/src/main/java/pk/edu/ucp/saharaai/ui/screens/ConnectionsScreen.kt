package pk.edu.ucp.saharaai.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.R
import pk.edu.ucp.saharaai.ui.components.BottomNav
import pk.edu.ucp.saharaai.ui.components.ButtonVariant
import pk.edu.ucp.saharaai.ui.components.CardVariant
import pk.edu.ucp.saharaai.ui.components.SaharaButton
import pk.edu.ucp.saharaai.ui.components.SaharaCard
import pk.edu.ucp.saharaai.ui.theme.*

data class SocialConnection(
    val id: String,
    val name: String,
    val iconRes: Int,
    val color: Color,
    var connected: Boolean,
    var isConnecting: Boolean = false,
    var username: String = ""
)

@Composable
fun ConnectionsScreen(
    navController: NavController,
    onNavigateBack: () -> Unit = { navController.popBackStack() },
    isEnglish: Boolean = false
) {
    val hazeState = remember { HazeState() }
    val isDark = isSystemInDarkTheme()

    val connections = remember { mutableStateListOf(
        SocialConnection("bluesky", "Bluesky", R.drawable.ic_bluesky, Color(0xFF0085FF), false),
        SocialConnection("mastodon", "Mastodon", R.drawable.ic_mastodon, Color(0xFF6364FF), false),
        SocialConnection("reddit", "Reddit", R.drawable.ic_reddit, Color(0xFFFF4500), false),
        SocialConnection("telegram", "Telegram", R.drawable.ic_telegram, Color(0xFF26A5E4), false)
    )}

    val coroutineScope = rememberCoroutineScope()
    val connectedCount = connections.count { it.connected }

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
                    .padding(horizontal = 24.dp)
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SaharaStrongGreen)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Connections" else "Rabtay (Connections)",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = SaharaStrongGreen,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = if (isEnglish) "Link your social accounts" else "Apne social accounts connect karein",
                            style = MaterialTheme.typography.bodySmall,
                            color = softTextColor.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) SaharaStrongGreen.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, SaharaStrongGreen.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = if (isEnglish) "$connectedCount of ${connections.size} connected" else "${connections.size} mein se $connectedCount connected",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = SaharaStrongGreen,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                connections.forEachIndexed { index, connection ->

                    val description = when(connection.id) {
                        "reddit" -> if (isEnglish) "Post & comment analysis" else "Posts aur comments"
                        "bluesky" -> if (isEnglish) "Public sentiment" else "Awami jazbaat"
                        "telegram" -> if (isEnglish) "Private chat analysis" else "Private chat ka tajziya"
                        "mastodon" -> if (isEnglish) "Social trends" else "Social trends"
                        else -> ""
                    }

                    SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {

                                Box(
                                    modifier = Modifier.size(52.dp).background(connection.color.copy(alpha = 0.1f), RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val iconSize = if (connection.id == "reddit") 48.dp else 28.dp
                                    Image(
                                        painter = painterResource(id = connection.iconRes),
                                        contentDescription = "${connection.name} Logo",
                                        modifier = Modifier.size(iconSize)
                                    )

                                    if (connection.connected) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Connected",
                                            tint = SaharaStrongGreen,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .align(Alignment.BottomEnd)
                                                .offset(x = 4.dp, y = 4.dp)
                                                .background(if (isDark) Color.Black else Color.White, CircleShape)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(connection.name, fontWeight = FontWeight.Bold, color = softTextColor)
                                    if (connection.connected) {
                                        Text(connection.username, style = MaterialTheme.typography.bodySmall, color = SaharaStrongGreen, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    } else {
                                        Text(description, style = MaterialTheme.typography.bodySmall, color = softTextColor.copy(alpha = 0.6f), maxLines = 1)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            if (connection.connected) {
                                SaharaButton(
                                    text = if (isEnglish) "Unlink" else "Hataein",
                                    onClick = { connections[index] = connection.copy(connected = false, username = "") },
                                    variant = ButtonVariant.DESTRUCTIVE,
                                    modifier = Modifier.width(100.dp).height(36.dp),
                                    isEnglish = isEnglish
                                )
                            } else {
                                SaharaButton(
                                    text = if (connection.isConnecting) (if (isEnglish) "Wait..." else "Rukein...") else (if (isEnglish) "Connect" else "Jorein"),
                                    onClick = {
                                        if (!connection.isConnecting) {
                                            coroutineScope.launch {
                                                connections[index] = connection.copy(isConnecting = true)
                                                delay(1500)
                                                connections[index] = connection.copy(connected = true, isConnecting = false, username = "@user_${connection.id}")
                                            }
                                        }
                                    },
                                    variant = if (connection.isConnecting) ButtonVariant.OUTLINE else ButtonVariant.SAHARASTRONGGREENGLASS,
                                    modifier = Modifier.width(100.dp).height(36.dp),
                                    isEnglish = isEnglish,
                                    hazeState = hazeState,
                                    textStyle = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}