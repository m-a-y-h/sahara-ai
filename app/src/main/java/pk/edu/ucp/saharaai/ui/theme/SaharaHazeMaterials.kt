package pk.edu.ucp.saharaai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect

object SaharaHazeMaterials {

    @Composable
    fun defaultGlass(isDark: Boolean = isSystemInDarkTheme()): HazeBlurStyle {
        return HazeBlurStyle(
            blurRadius = 25.dp,
            noiseFactor = 0.05f,
            colorEffects = listOf(
                HazeColorEffect.tint(
                    if (isDark) Color.Black.copy(alpha = 0.25f)
                    else Color.White.copy(alpha = 0.45f)
                )
            )
        )
    }

    /** A heavier, more opaque variant for modal popups. Bigger blur radius
     *  and a stronger tint so the card reads as proper frosted glass — and so
     *  the popup still looks like a real surface on devices where the
     *  hardware blur path falls back to a tint-only render. */
    @Composable
    fun popupGlass(isDark: Boolean = isSystemInDarkTheme()): HazeBlurStyle {
        return HazeBlurStyle(
            blurRadius = 45.dp,
            noiseFactor = 0.06f,
            colorEffects = listOf(
                HazeColorEffect.tint(
                    if (isDark) Color.Black.copy(alpha = 0.55f)
                    else Color.White.copy(alpha = 0.70f)
                )
            )
        )
    }

    @Composable
    fun selectedGlass(isDark: Boolean = isSystemInDarkTheme()): HazeBlurStyle {
        return HazeBlurStyle(
            blurRadius = 25.dp,
            noiseFactor = 0.05f,
            colorEffects = listOf(
                HazeColorEffect.tint(
                    if (isDark) SaharaStrongGreen.copy(alpha = 0.15f)
                    else SaharaStrongGreen.copy(alpha = 0.15f)
                )
            )
        )
    }

    @Composable
    fun bottomNav(isDark: Boolean = isSystemInDarkTheme()): HazeBlurStyle {
        return HazeBlurStyle(
            blurRadius = 30.dp,
            noiseFactor = 0.08f,
            colorEffects = listOf(
                HazeColorEffect.tint(
                    if (isDark) Color(0xFF121212).copy(alpha = 0.6f)
                    else Color.White.copy(alpha = 0.55f)
                )
            )
        )
    }

    @Composable
    fun buttonGlass(isDark: Boolean = isSystemInDarkTheme()): HazeBlurStyle {
        return HazeBlurStyle(
            blurRadius = 15.dp,
            noiseFactor = 0.0f,
            colorEffects = listOf(
                HazeColorEffect.tint(
                    if (isDark) Color.White.copy(alpha = 0.1f)
                    else Color.Black.copy(alpha = 0.05f)
                )
            )
        )
    }

    @Composable
    fun buttonGreenGlass(isDark: Boolean = isSystemInDarkTheme()): HazeBlurStyle {
        return HazeBlurStyle(
            blurRadius = 15.dp,
            noiseFactor = 0.0f,
            colorEffects = listOf(
                HazeColorEffect.tint(
                    if (isDark) SaharaStrongGreen.copy(alpha = 0.35f)
                    else SaharaStrongGreen.copy(alpha = 0.35f)
                )
            )
        )
    }
}