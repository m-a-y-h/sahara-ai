package pk.edu.ucp.saharaai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.blurEffect
import pk.edu.ucp.saharaai.ui.theme.SaharaHazeMaterials

/**
 * A glassmorphic popup rendered IN THE SAME COMPOSITION as the screen (not a
 * Dialog/Popup window). Because it shares the screen's [hazeState], its card
 * actually blurs the content behind it — which a Dialog window can never do.
 *
 * Host it at the top level of a screen's root Box (the same Box whose content is
 * a `hazeSource(hazeState)`), e.g.:
 *
 *     if (showConfirm) GlassOverlay(hazeState, onDismiss = { showConfirm = false }) { ... }
 *
 * Tapping the scrim dismisses; taps on the card are swallowed.
 */
@Composable
fun GlassOverlay(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    isDark: Boolean = isSystemInDarkTheme(),
    dismissOnScrimTap: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glassStyle = SaharaHazeMaterials.defaultGlass(isDark)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(dismissOnScrimTap) {
                detectTapGestures(onTap = { if (dismissOnScrimTap) onDismiss() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(24.dp))
                .hazeEffect(state = hazeState) {
                    inputScale = HazeInputScale.Auto
                    blurEffect { style = glassStyle }
                }
                .border(
                    1.dp,
                    Color.White.copy(alpha = if (isDark) 0.18f else 0.35f),
                    RoundedCornerShape(24.dp),
                )
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .padding(22.dp),
            content = content,
        )
    }
}
