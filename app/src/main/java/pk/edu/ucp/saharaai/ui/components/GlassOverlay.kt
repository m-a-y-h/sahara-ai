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
import pk.edu.ucp.saharaai.ui.theme.SaharaBorderGray
import pk.edu.ucp.saharaai.ui.theme.SaharaHazeMaterials

/**
 * A popup rendered in the same composition as the screen (not a Dialog/Popup
 * window), so it can sit above the app chrome, dim the full screen, AND blur
 * the content behind it as real glass.
 *
 * The blur is the same recipe the Welcome privacy popup uses via
 * SaharaCard(GLASS): the blurred + tinted backdrop *is* the panel fill, so the
 * screen shows through. For that to render, the host screen must mark its
 * content with `Modifier.hazeSource(hazeState)` and pass the **same**
 * `hazeState` here — otherwise the blur has no source to read and the panel
 * looks empty.
 *
 * Host it at the top level of a screen's root Box, e.g.:
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
    val shape = RoundedCornerShape(24.dp)
    // Style resolved here (composable scope) because the hazeEffect lambda below
    // is not composable. Mirrors SaharaCard's GLASS variant exactly.
    val glassStyle = SaharaHazeMaterials.defaultGlass(isDark)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.16f) else SaharaBorderGray.copy(alpha = 0.40f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (isDark) 0.62f else 0.48f))
            .pointerInput(dismissOnScrimTap) {
                detectTapGestures(onTap = { if (dismissOnScrimTap) onDismiss() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                // Cap the card height and scroll its content so a tall popup
                // (long forms, avatar grids, time pickers) never overflows
                // the screen.
                .heightIn(max = 560.dp)
                .clip(shape)
                // The blurred glass backdrop — replaces the old opaque panel fill.
                .hazeEffect(state = hazeState) {
                    inputScale = HazeInputScale.Auto
                    blurEffect { style = glassStyle }
                }
                .border(1.dp, borderColor, shape)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .padding(22.dp)
                .verticalScroll(rememberScrollState()),
            content = content,
        )
    }
}

/**
 * A near drop-in in-app replacement for Material3's [androidx.compose.material3.AlertDialog].
 * Keeps the familiar `icon` / `title` / `text` / `confirmButton` / `dismissButton`
 * slots, but renders through [GlassOverlay] so the popup can dim the whole app
 * and stay visually consistent with the custom navigation chrome.
 *
 * Host it at the root of a screen, not nested inside a padded column, since the
 * overlay fills the whole screen.
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
