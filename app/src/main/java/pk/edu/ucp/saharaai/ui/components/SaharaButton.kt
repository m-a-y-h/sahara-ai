package pk.edu.ucp.saharaai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.blurEffect
import pk.edu.ucp.saharaai.ui.theme.*

enum class ButtonVariant {
    DEFAULT,
    DESTRUCTIVE,
    OUTLINE,
    GHOST,
    GRADIENT,
    CORAL,
    SUCCESS,
    GLASS,
    SAHARASTRONGGREENGLASS
}

@Composable
fun SaharaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.GRADIENT,
    isFullWidth: Boolean = false,
    enabled: Boolean = true,
    isEnglish: Boolean = false,
    hazeState: HazeState? = null,
    height: androidx.compose.ui.unit.Dp = 56.dp,
    forceDarkTheme: Boolean = false,
    textStyle: TextStyle? = null
) {
    val isDark = forceDarkTheme || isSystemInDarkTheme()

    val finalTextStyle = textStyle ?: MaterialTheme.typography.titleMedium

    val shape = RoundedCornerShape(16.dp)
    val baseModifier = if (isFullWidth) modifier.fillMaxWidth() else modifier
    val commonModifier = baseModifier.height(height)

    when (variant) {
        ButtonVariant.SAHARASTRONGGREENGLASS -> {
            Button(
                onClick = onClick, enabled = enabled, modifier = commonModifier, shape = shape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .then(
                            if (hazeState != null) {
                                val greenStyle = SaharaHazeMaterials.buttonGreenGlass(isDark)
                                Modifier.hazeEffect(state = hazeState) {
                                    inputScale = HazeInputScale.Auto
                                    blurEffect { style = greenStyle }
                                }
                            } else {
                                Modifier.background(SaharaStrongGreen.copy(alpha = if (isDark) 0.4f else 0.6f))
                            }
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.2f))
                            ),
                            shape = shape
                        )
                ) {
                    Text(
                        text = text, modifier = Modifier.align(Alignment.Center),
                        // ExtraBold (was Bold) so the white text doesn't
                        // fade against the green tint in screenshots —
                        // user complaint was that snapshots of the chat /
                        // login screens lost the button label entirely
                        // when re-encoded. White in both modes.
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        style = finalTextStyle
                    )
                }
            }
        }
        ButtonVariant.GLASS -> {
            Button(
                onClick = onClick, enabled = enabled, modifier = commonModifier, shape = shape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .then(
                            if (hazeState != null) {
                                val buttonStyle = SaharaHazeMaterials.buttonGlass(isDark)
                                Modifier.hazeEffect(state = hazeState) {
                                    inputScale = HazeInputScale.Auto
                                    blurEffect { style = buttonStyle }
                                }
                            } else {
                                Modifier.background(if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f))
                            }
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    if (isDark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.2f),
                                    if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f)
                                )
                            ),
                            shape = shape
                        )
                ) {
                    Text(
                        text = text, modifier = Modifier.align(Alignment.Center), fontWeight = FontWeight.ExtraBold,
                        color = if (isDark) Color.White else Color.Black.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
        ButtonVariant.GRADIENT, ButtonVariant.CORAL -> {
            val gradientColors = when {
                !enabled -> listOf(Color.LightGray.copy(alpha = 0.5f), Color.LightGray.copy(alpha = 0.3f))
                variant == ButtonVariant.GRADIENT -> listOf(SaharaStrongGreen, SaharaSage)
                else -> listOf(SaharaCoral, SaharaPeach)
            }

            Button(
                onClick = onClick, enabled = enabled, modifier = commonModifier, shape = shape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(gradientColors)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = text, fontWeight = FontWeight.ExtraBold,
                        color = if (enabled) Color.White else Color.Gray,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        ButtonVariant.OUTLINE -> {
            OutlinedButton(
                onClick = onClick, enabled = enabled, modifier = commonModifier, shape = shape,
                border = BorderStroke(1.dp, if (enabled) SaharaStrongGreen else Color.LightGray),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent, contentColor = SaharaStrongGreen, disabledContentColor = Color.Gray
                ),
                contentPadding = PaddingValues()
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = text, fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        else -> {
            val containerColor = when (variant) {
                ButtonVariant.DESTRUCTIVE -> SaharaCoral
                ButtonVariant.SUCCESS -> SaharaStrongGreen
                ButtonVariant.GHOST -> Color.Transparent
                else -> SaharaStrongGreen
            }

            Button(
                onClick = onClick, enabled = enabled, modifier = commonModifier, shape = shape,
                colors = ButtonDefaults.buttonColors(containerColor = containerColor, disabledContainerColor = Color.LightGray.copy(alpha = 0.5f)),
                contentPadding = PaddingValues()
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = text, fontWeight = FontWeight.ExtraBold,
                        color = when {
                            !enabled -> Color.Gray
                            variant == ButtonVariant.GHOST -> SaharaStrongGreen
                            else -> Color.White
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}