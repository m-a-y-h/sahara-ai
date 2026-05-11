package pk.edu.ucp.saharaai.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import pk.edu.ucp.saharaai.ui.components.BottomNav
import pk.edu.ucp.saharaai.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    navController: NavController,
    isEnglish: Boolean = false,
    onNavigateBack: () -> Unit = { navController.popBackStack() }
) {
    val hazeState = remember { HazeState() }
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    val bgGradient = if (isDark)
        listOf(SaharaLavender.copy(0.22f), MaterialTheme.colorScheme.background.copy(0.6f), MaterialTheme.colorScheme.background)
    else
        listOf(SaharaLavender.copy(0.28f), SaharaSkyLight.copy(0.12f), MaterialTheme.colorScheme.background.copy(0.2f))

    val inf = rememberInfiniteTransition(label = "pulse")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(10000, easing = LinearEasing)), label = "r")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(state = hazeState)
            .background(Brush.verticalGradient(bgGradient))
    ) {
        Box(modifier = Modifier.size(300.dp).offset(x = 200.dp, y = (-100).dp).rotate(rot)
            .background(Brush.radialGradient(listOf(SaharaLavender.copy(0.2f), Color.Transparent))))

        Scaffold(
            bottomBar = { BottomNav(navController = navController, hazeState = hazeState) },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(Modifier.height(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(0.5f), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = SaharaLavender)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Community" else "Community",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = SaharaLavender
                        )
                        Text(
                            text = if (isEnglish) "Connect with peers safely" else "Dosron ke saath rabta karein",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.weight(0.8f))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.extraLarge)
                        .hazeEffect(state = hazeState) {
                            blurEffect {
                                blurRadius = 25.dp
                                colorEffects = listOf(HazeColorEffect.tint(Color.White.copy(0.08f)))
                            }
                        }
                        .background(Color.White.copy(0.05f))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.HourglassTop,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = SaharaLavender
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = if (isEnglish) "Coming Soon" else "Anqareeb",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (isEnglish)
                                "We are building a safe and anonymous space for you to share your journey."
                            else "Hum aap ke liye aik mehfooz jagah bana rahe hain jahan aap apna safar share kar sakein ge.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    }
                }

                Spacer(Modifier.weight(1f))
            }
        }
    }
}