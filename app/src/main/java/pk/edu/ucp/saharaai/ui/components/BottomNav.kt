package pk.edu.ucp.saharaai.ui.components

import android.content.Context
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.blurEffect
import pk.edu.ucp.saharaai.ui.screens.GlobalAppState
import pk.edu.ucp.saharaai.ui.screens.rememberFrameOscillation
import pk.edu.ucp.saharaai.ui.theme.SaharaGreenLight
import pk.edu.ucp.saharaai.ui.theme.SaharaHazeMaterials
import pk.edu.ucp.saharaai.ui.theme.SaharaStrongGreen
import pk.edu.ucp.saharaai.util.showAssessmentRequiredToast

data class NavItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
    val requiresAssessment: Boolean = false,
)

@Composable
fun BottomNav(navController: NavController, hazeState: HazeState) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val isEnglish = context.getSharedPreferences("sahara_prefs", Context.MODE_PRIVATE)
        .getBoolean("is_english", false)

    val items = mutableListOf(NavItem("Home", Icons.Default.Home, "dashboard"))

    if (GlobalAppState.hasCompletedInitialAssessment) {
        items.add(NavItem("Progress", Icons.Default.Timeline, "progress"))
    } else {
        items.add(NavItem("Assessment", Icons.AutoMirrored.Filled.Assignment, "assessment"))
        if (GlobalAppState.hasEverCompletedAssessment) {
            items.add(NavItem("Progress", Icons.Default.Timeline, "progress"))
        }
    }

    items.addAll(
        listOf(
            NavItem("Chat", Icons.AutoMirrored.Filled.Chat, "chat", requiresAssessment = true),
            NavItem("Recovery", Icons.Default.Favorite, "recovery", requiresAssessment = true),
            NavItem("Profile", Icons.Default.Person, "profile")
        )
    )

    val pulseScale = rememberFrameOscillation(1f, 1.1f, 600)

    val navStyle = SaharaHazeMaterials.bottomNav(isDark)

    NavigationBar(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets.navigationBars,
        modifier = Modifier
            .hazeEffect(state = hazeState) {
                inputScale = HazeInputScale.Auto
                blurEffect {
                    style = navStyle
                }
            }
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
            val isLocked = item.requiresAssessment && !GlobalAppState.hasCompletedInitialAssessment
            val itemScale by animateFloatAsState(
                targetValue = if (isSelected) 1.05f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "itemScale"
            )
            val iconScale = (if (item.route == "assessment") pulseScale else 1f) * itemScale

            NavigationBarItem(
                icon = {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        modifier = Modifier
                            .scale(iconScale)
                            .graphicsLayer { alpha = if (isLocked) 0.45f else 1f }
                    )
                },
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
                    if (isLocked) {
                        showAssessmentRequiredToast(context, isEnglish)
                        return@NavigationBarItem
                    }
                    if (currentRoute != item.route) {
                        val resetRootDestination = item.route == "dashboard" || item.route == "profile"
                        navController.navigate(item.route) {
                            popUpTo("dashboard") { saveState = !resetRootDestination }
                            launchSingleTop = true
                            restoreState = !resetRootDestination
                        }
                    }
                }
            )
        }
    }
}
