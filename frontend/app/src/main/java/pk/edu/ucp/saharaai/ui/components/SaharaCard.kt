package pk.edu.ucp.saharaai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.blurEffect
import pk.edu.ucp.saharaai.ui.theme.SaharaGreen
import pk.edu.ucp.saharaai.ui.theme.SaharaHazeMaterials
import pk.edu.ucp.saharaai.ui.theme.SaharaStrongGreen
import pk.edu.ucp.saharaai.ui.theme.SaharaBorderGray

enum class CardVariant { DEFAULT, GLASS, GRADIENT, SELECTED_GLASS, SAHARA_GREEN_GLASS, DASHBOARD_GLASS }

@Composable
fun SaharaCard(
    modifier: Modifier = Modifier,
    variant: CardVariant = CardVariant.DEFAULT,
    hazeState: HazeState? = null,
    forceDarkTheme: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = forceDarkTheme || isSystemInDarkTheme()

    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .graphicsLayer {
                this.shape = shape
                clip = true
            }
            .then(
                when {
                    variant == CardVariant.GLASS && hazeState != null -> {
                        val glassStyle = SaharaHazeMaterials.defaultGlass(isDark)
                        Modifier.clip(shape).hazeEffect(state = hazeState) {
                            inputScale = HazeInputScale.Auto
                            blurEffect { style = glassStyle }
                        }
                    }
                    variant == CardVariant.SELECTED_GLASS && hazeState != null -> {
                        val selectedStyle = SaharaHazeMaterials.selectedGlass(isDark)
                        Modifier.clip(shape).hazeEffect(state = hazeState) {
                            inputScale = HazeInputScale.Auto
                            blurEffect { style = selectedStyle }
                        }
                    }
                    variant == CardVariant.SAHARA_GREEN_GLASS && hazeState != null -> {
                        val greenStyle = SaharaHazeMaterials.selectedGlass(isDark)
                        Modifier.clip(shape).hazeEffect(state = hazeState) {
                            inputScale = HazeInputScale.Auto
                            blurEffect { style = greenStyle }
                        }
                    }
                    variant == CardVariant.GRADIENT -> {
                        Modifier.background(Brush.linearGradient(listOf(SaharaGreen, SaharaStrongGreen)))
                    }
                    variant == CardVariant.GLASS -> {
                        Modifier.background(if (isDark) Color(0xFF121212).copy(alpha = 0.75f) else Color.White.copy(alpha = 0.85f))
                    }
                    variant == CardVariant.DASHBOARD_GLASS -> {
                        Modifier.background(if (isDark) Color(0xFF121212).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.65f))
                    }
                    else -> Modifier.background(if (isDark) Color(0xFF121212) else Color.White)
                }
            )

            .border(
                width = 1.dp,
                color = if (isDark)
                    Color.White.copy(alpha = 0.2f)
                else
                    SaharaBorderGray.copy(alpha = 0.29f),
                shape = shape
            )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            content()
        }
    }
}