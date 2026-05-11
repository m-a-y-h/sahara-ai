package pk.edu.ucp.saharaai.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.blurEffect
import pk.edu.ucp.saharaai.ui.screens.GlobalAppState
import pk.edu.ucp.saharaai.ui.theme.SaharaGreenLight
import pk.edu.ucp.saharaai.ui.theme.SaharaHazeMaterials
import pk.edu.ucp.saharaai.ui.theme.SaharaStrongGreen

data class NavItem(val label: String, val icon: ImageVector, val route: String)

@Composable
fun BottomNav(navController: NavController, hazeState: HazeState? = null) {
    val isDark = isSystemInDarkTheme()

    val items = mutableListOf(NavItem("Home", Icons.Default.Home, "dashboard"))

    if (GlobalAppState.hasCompletedInitialAssessment) {
        items.add(NavItem("Progress", Icons.Default.Timeline, "progress"))
    } else {
        items.add(NavItem("Assessment", Icons.AutoMirrored.Filled.Assignment, "assessment"))
    }

    items.addAll(
        listOf(
            NavItem("Chat", Icons.AutoMirrored.Filled.Chat, "chat"),
            NavItem("Recovery", Icons.Default.Favorite, "recovery"),
            NavItem("Profile", Icons.Default.Person, "profile")
        )
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseScale"
    )

    val navStyle = SaharaHazeMaterials.bottomNav(isDark)

    NavigationBar(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets.navigationBars,
        modifier = Modifier
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(state = hazeState) {
                        inputScale = HazeInputScale.Auto
                        blurEffect {
                            style = navStyle
                        }
                    }
                } else {
                    Modifier.background(if (isDark) Color(0xFF121212).copy(alpha = 0.9f) else Color.White.copy(alpha = 0.95f))
                }
            )
            .drawBehind {
                drawLine(
                    color = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.1f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        items.forEach { item ->
            val isSelected = currentRoute == item.route
            val itemScale by animateFloatAsState(
                targetValue = if (isSelected) 1.05f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "itemScale"
            )
            val iconScale = (if (item.route == "assessment") pulseScale else 1f) * itemScale

            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label, modifier = Modifier.scale(iconScale)) },
                label = { Text(text = item.label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) },
                selected = isSelected,
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SaharaStrongGreen,
                    selectedTextColor = SaharaStrongGreen,
                    indicatorColor = SaharaGreenLight.copy(alpha = if (isDark) 0.25f else 0.5f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                ),
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}