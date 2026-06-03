package pk.edu.ucp.saharaai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
    val glassStyle = SaharaHazeMaterials.popupGlass(isDark)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(dismissOnScrimTap) {
                detectTapGestures(onTap = { if (dismissOnScrimTap) onDismiss() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                // Cap the card height and scroll its content so a tall popup (long
                // forms, avatar grids, time pickers) never overflows the screen.
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(24.dp))
                // Opaque fallback layer so the card still reads as a real
                // surface on devices where the hardware blur path falls back
                // to a no-op. On devices that DO blur, this colour sits
                // behind the haze effect and just tints it slightly more —
                // matching how Material 3's `surfaceContainerHigh` looks at
                // a high tonal elevation.
                .background(
                    if (isDark) Color(0xFF1A1F22).copy(alpha = 0.75f)
                    else Color.White.copy(alpha = 0.75f)
                )
                .hazeEffect(state = hazeState) {
                    inputScale = HazeInputScale.Auto
                    blurEffect { style = glassStyle }
                }
                .border(
                    1.dp,
                    Color.White.copy(alpha = if (isDark) 0.18f else 0.55f),
                    RoundedCornerShape(24.dp),
                )
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .padding(22.dp)
                .verticalScroll(rememberScrollState()),
            content = content,
        )
    }
}

/**
 * A near drop-in glassmorphic replacement for Material3's [androidx.compose.material3.AlertDialog].
 * Keeps the familiar `icon` / `title` / `text` / `confirmButton` / `dismissButton`
 * slots, but renders through [GlassOverlay] so the popup is real glass (it blurs
 * the screen behind it) instead of an opaque windowed dialog.
 *
 * Because it relies on [GlassOverlay], host it at the root of a screen whose
 * background is a `hazeSource(hazeState)` — not nested inside a padded column,
 * since the overlay fills the whole screen.
 */
@Composable
fun GlassAlertDialog(
    hazeState: HazeState,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    isDark: Boolean = isSystemInDarkTheme(),
    dismissOnScrimTap: Boolean = true,
) {
    GlassOverlay(
        hazeState = hazeState,
        onDismiss = onDismissRequest,
        isDark = isDark,
        dismissOnScrimTap = dismissOnScrimTap,
    ) {
        icon?.let {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { it() }
            Spacer(Modifier.height(12.dp))
        }
        title?.let {
            it()
            Spacer(Modifier.height(10.dp))
        }
        text?.let {
            it()
            Spacer(Modifier.height(20.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dismissButton?.let {
                it()
                Spacer(Modifier.width(8.dp))
            }
            confirmButton()
        }
    }
}
