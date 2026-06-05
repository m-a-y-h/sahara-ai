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

    /** The `SAHARASTRONGGREENGLASS` button variant uses this style on the
     *  haze backdrop.
     *
     *  Light-theme tint bumped from 0.35 -> 0.65 because the lower value
     *  vanished against bright light-mode backgrounds (Login, Register,
     *  Onboarding, etc.) and made the white text on the button read as
     *  "white text on white paint." 0.65 keeps the button visibly green
     *  whether the backdrop is a soft gradient or a pure white surface,
     *  without going so opaque the haze blur stops mattering.
     *
     *  Dark-theme tint untouched at 0.35 because WelcomeScreen passes
     *  `forceDarkTheme = true` and uses that branch — the user has signed
     *  off on Welcome looking exactly as it does today. Changing the dark
     *  alpha here would change Welcome. */
    @Composable
    fun buttonGreenGlass(isDark: Boolean = isSystemInDarkTheme()): HazeBlurStyle {
        return HazeBlurStyle(
            blurRadius = 15.dp,
            noiseFactor = 0.0f,
            colorEffects = listOf(
                HazeColorEffect.tint(
                    if (isDark) SaharaStrongGreen.copy(alpha = 0.35f)
                    else SaharaStrongGreen.copy(alpha = 0.65f)
                )
            )
        )
    }
}
