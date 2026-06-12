package pk.edu.ucp.saharaai.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import pk.edu.ucp.saharaai.R
import pk.edu.ucp.saharaai.ui.theme.SaharaBorderGray
import pk.edu.ucp.saharaai.ui.theme.SaharaHazeMaterials

/**
 * A popup rendered in the same composition as the screen (not a Dialog/Popup
 * window), so it can sit above the app chrome, dim the full screen, AND blur
 * the content behind it as real glass.
 *
 * By default the panel uses the same `sahara_bg5` cropped, blurred, and tinted
 * treatment as the NGO / counselor verification popups. Passing
 * `panelBackdropResId = null` restores the older screen-glass behavior, which
 * needs the host screen to mark its content with `Modifier.hazeSource(hazeState)`.
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
    panelBackdropResId: Int? = R.drawable.sahara_bg5,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    // Style resolved here (composable scope) because the hazeEffect lambda below
    // is not composable. Mirrors SaharaCard's GLASS variant exactly.
    val glassStyle = SaharaHazeMaterials.defaultGlass(isDark)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.16f) else SaharaBorderGray.copy(alpha = 0.40f)
    val glassTint = if (isDark) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.6f)
    val panelBackdropHazeState = androidx.compose.runtime.remember { HazeState() }
    val panelHazeState = if (panelBackdropResId != null) panelBackdropHazeState else hazeState

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (isDark) 0.6f else 0.3f))
            .pointerInput(dismissOnScrimTap) {
                detectTapGestures(onTap = { if (dismissOnScrimTap) onDismiss() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                // Cap the card height and scroll its content so a tall popup
                // (long forms, avatar grids, time pickers) never overflows
                // the screen.
                .heightIn(max = 560.dp)
                .clip(shape)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
        ) {
            if (panelBackdropResId != null) {
                Image(
                    painter = painterResource(id = panelBackdropResId),
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(shape)
                        .hazeSource(state = panelBackdropHazeState),
                    contentScale = ContentScale.Crop,
                )
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    // The blurred glass backdrop — either reads the host screen,
                    // or the popup-only image source above when one is provided.
                    .hazeEffect(state = panelHazeState) {
                        inputScale = HazeInputScale.Auto
                        blurEffect {
                            if (panelBackdropResId != null) {
                                blurRadius = 12.dp
                                colorEffects = listOf(HazeColorEffect.tint(glassTint))
                            } else {
                                style = glassStyle
                            }
                        }
                    }
                    .then(
                        if (panelBackdropResId != null) {
                            Modifier.border(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    listOf(
                                        if (isDark) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.8f),
                                        if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.3f),
                                    )
                                ),
                                shape = shape,
                            )
                        } else {
                            Modifier.border(1.dp, borderColor, shape)
                        }
                    ),
            )

            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                content = content,
            )
        }
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
    panelBackdropResId: Int? = R.drawable.sahara_bg5,
) {
    GlassOverlay(
        hazeState = hazeState,
        onDismiss = onDismissRequest,
        isDark = isDark,
        dismissOnScrimTap = dismissOnScrimTap,
        panelBackdropResId = panelBackdropResId,
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
