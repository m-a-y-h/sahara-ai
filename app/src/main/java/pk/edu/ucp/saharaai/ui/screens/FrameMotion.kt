package pk.edu.ucp.saharaai.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import pk.edu.ucp.saharaai.ui.theme.SaharaPeach
import pk.edu.ucp.saharaai.ui.theme.SaharaSky
import pk.edu.ucp.saharaai.ui.theme.SaharaStrongGreen
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

internal data class BackdropBlobMotion(
    val offsetX: Float,
    val offsetY: Float,
    val rotation: Float,
    val scale: Float
)

/**
 * Frame-driven decorative motion. Unlike duration-scaled Compose animations, these visual hints
 * remain visible when a device collapses animator durations.
 */
@Composable
internal fun rememberFrameOscillation(
    startValue: Float,
    endValue: Float,
    halfCycleMillis: Long,
    phaseFraction: Float = 0f
): Float {
    var value by remember(startValue, endValue) { mutableFloatStateOf(startValue) }

    LaunchedEffect(startValue, endValue, halfCycleMillis, phaseFraction) {
        if (startValue == endValue) {
            value = startValue
            return@LaunchedEffect
        }
        val cycleNanos = halfCycleMillis.coerceAtLeast(1L) * 2_000_000L
        var firstFrameNanos = 0L

        while (currentCoroutineContext().isActive) {
            val frameNanos = withFrameNanos { it }
            if (firstFrameNanos == 0L) firstFrameNanos = frameNanos
            val elapsedNanos = frameNanos - firstFrameNanos
            val normalized = (
                (elapsedNanos % cycleNanos).toDouble() / cycleNanos.toDouble() +
                    phaseFraction.toDouble()
                ) % 1.0
            val progress = ((1.0 - cos(normalized * 2.0 * PI)) / 2.0).toFloat()
            value = startValue + (endValue - startValue) * progress
        }
    }

    return value
}

@Composable
internal fun rememberFrameRotation(periodMillis: Long): Float {
    var rotation by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(periodMillis) {
        val cycleNanos = periodMillis.coerceAtLeast(1L) * 1_000_000L
        var firstFrameNanos = 0L

        while (currentCoroutineContext().isActive) {
            val frameNanos = withFrameNanos { it }
            if (firstFrameNanos == 0L) firstFrameNanos = frameNanos
            val elapsedNanos = frameNanos - firstFrameNanos
            rotation = ((elapsedNanos % cycleNanos).toFloat() / cycleNanos.toFloat()) * 360f
        }
    }

    return rotation
}

@Composable
internal fun rememberBackdropBlobMotion(isFast: Boolean = false): BackdropBlobMotion {
    val offsetX = rememberFrameOscillation(
        startValue = -34f,
        endValue = 34f,
        halfCycleMillis = if (isFast) 1_500 else 2_800
    )
    val offsetY = rememberFrameOscillation(
        startValue = -22f,
        endValue = 22f,
        halfCycleMillis = if (isFast) 1_900 else 3_500,
        phaseFraction = 0.25f
    )
    val rotation = rememberFrameOscillation(
        startValue = -8f,
        endValue = 8f,
        halfCycleMillis = if (isFast) 1_700 else 3_500
    )
    val scale = rememberFrameOscillation(
        startValue = 1f,
        endValue = if (isFast) 1.1f else 1.08f,
        halfCycleMillis = if (isFast) 1_500 else 3_000,
        phaseFraction = 0.1f
    )
    return BackdropBlobMotion(offsetX, offsetY, rotation, scale)
}

internal data class BackdropBlobSpec(
    val size: Dp,
    val offsetX: Dp,
    val offsetY: Dp,
    val color: Color,
    val alignment: Alignment? = null,
    val blurRadius: Dp = 0.dp,
)

/** The gradient + two drifting radial blobs that sit behind every screen's Scaffold. */
@Composable
internal fun ScreenBackdrop(
    hazeState: HazeState,
    bgGradient: List<Color> = if (isSystemInDarkTheme()) listOf(
        SaharaStrongGreen.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.background
    ) else listOf(
        SaharaStrongGreen.copy(alpha = 0.25f),
        SaharaPeach.copy(alpha = 0.1f),
        MaterialTheme.colorScheme.background.copy(alpha = 0.2f)
    ),
    blob1Color: Color = SaharaStrongGreen.copy(alpha = if (isSystemInDarkTheme()) 0.25f else 0.15f),
    blob2Color: Color = SaharaSky.copy(alpha = if (isSystemInDarkTheme()) 0.2f else 0.18f),
    primaryBlob: BackdropBlobSpec? = BackdropBlobSpec(
        size = 350.dp,
        offsetX = (-80).dp,
        offsetY = (-50).dp,
        color = blob1Color,
    ),
    secondaryBlob: BackdropBlobSpec? = BackdropBlobSpec(
        size = 400.dp,
        offsetX = 100.dp,
        offsetY = 50.dp,
        color = blob2Color,
        alignment = Alignment.BottomEnd,
    ),
    isFast: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(state = hazeState)
            .background(Brush.verticalGradient(bgGradient))
    ) {
        BackdropBlobs(primaryBlob, secondaryBlob, isFast)
    }
}

@Composable
internal fun BoxScope.BackdropBlobs(
    primaryBlob: BackdropBlobSpec?,
    secondaryBlob: BackdropBlobSpec?,
    isFast: Boolean = false,
) {
    val blobMotion = rememberBackdropBlobMotion(isFast)
    primaryBlob?.let { BackdropBlob(it, blobMotion, isPrimary = true) }
    secondaryBlob?.let { BackdropBlob(it, blobMotion, isPrimary = false) }
}

@Composable
private fun BoxScope.BackdropBlob(
    spec: BackdropBlobSpec,
    motion: BackdropBlobMotion,
    isPrimary: Boolean,
) {
    var modifier = Modifier.size(spec.size)
    spec.alignment?.let { modifier = modifier.align(it) }
    modifier = modifier
        .offset(x = spec.offsetX, y = spec.offsetY)
        .then(if (isPrimary) Modifier.primaryBlobMotion(motion) else Modifier.secondaryBlobMotion(motion))

    Box(
        modifier = if (spec.blurRadius > 0.dp) {
            modifier
                .background(spec.color, CircleShape)
                .blur(spec.blurRadius)
        } else {
            modifier.background(Brush.radialGradient(listOf(spec.color, Color.Transparent)))
        }
    )
}

internal fun Modifier.primaryBlobMotion(motion: BackdropBlobMotion): Modifier = this
    .offset(x = motion.offsetX.dp, y = motion.offsetY.dp)
    .rotate(motion.rotation)
    .scale(motion.scale)

internal fun Modifier.secondaryBlobMotion(motion: BackdropBlobMotion): Modifier = this
    .offset(x = (-motion.offsetX).dp, y = (-motion.offsetY).dp)
    .rotate(-motion.rotation)
    .scale(motion.scale)

internal suspend fun runFrameTween(durationMillis: Long, onFrame: (Float) -> Unit) {
    val durationNanos = durationMillis.coerceAtLeast(1L) * 1_000_000L
    var firstFrameNanos = 0L
    var complete = false

    while (!complete && currentCoroutineContext().isActive) {
        val frameNanos = withFrameNanos { it }
        if (firstFrameNanos == 0L) firstFrameNanos = frameNanos
        val progress = (
            (frameNanos - firstFrameNanos).toFloat() / durationNanos.toFloat()
            ).coerceIn(0f, 1f)
        val smoothProgress = progress * progress * (3f - 2f * progress)
        onFrame(smoothProgress)
        complete = progress >= 1f
    }

    if (complete) onFrame(1f)
}

internal suspend fun ScrollState.frameScrollTo(target: Int, durationMillis: Long) {
    val start = value
    val durationNanos = durationMillis.coerceAtLeast(1L) * 1_000_000L
    var firstFrameNanos = 0L
    var complete = false

    while (!complete && currentCoroutineContext().isActive) {
        val frameNanos = withFrameNanos { it }
        if (firstFrameNanos == 0L) firstFrameNanos = frameNanos
        val progress = (
            (frameNanos - firstFrameNanos).toFloat() / durationNanos.toFloat()
            ).coerceIn(0f, 1f)
        val smoothProgress = progress * progress * (3f - 2f * progress)
        scrollTo((start + (target - start) * smoothProgress).roundToInt())
        complete = progress >= 1f
    }

    if (complete) scrollTo(target)
}
