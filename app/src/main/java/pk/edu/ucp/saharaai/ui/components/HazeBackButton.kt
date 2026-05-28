package pk.edu.ucp.saharaai.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.blurEffect
import pk.edu.ucp.saharaai.ui.theme.SaharaHazeMaterials
import pk.edu.ucp.saharaai.ui.theme.SaharaStrongGreen

@Composable
fun HazeBackButton(
    onClick: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    tint: Color = SaharaStrongGreen,
) {
    val isDark = isSystemInDarkTheme()
    val style = SaharaHazeMaterials.defaultGlass(isDark)
    IconButton(
        onClick = onClick,
        modifier = modifier
            .clip(CircleShape)
            .hazeEffect(state = hazeState) {
                inputScale = HazeInputScale.Auto
                blurEffect { this.style = style }
            }
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = tint)
    }
}
