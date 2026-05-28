package pk.edu.ucp.saharaai.lens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.ByteArrayOutputStream
import kotlin.math.abs


sealed class LensValidation {
    abstract val reason: String

    data class Valid(
        val faceBoundingBox: Rect,
        val previewSize: android.util.Size,
        override val reason: String = "Looks good — hold still",
    ) : LensValidation()

    data class Invalid(
        override val reason: String,
        val kind: InvalidKind,
    ) : LensValidation()

    enum class InvalidKind {
        NO_FACE,
        MULTIPLE_FACES,
        BAD_LIGHTING,
        FACE_TOO_SMALL,
        FACE_OFF_CENTER,
        NOT_STRAIGHT,
        WRONG_EXPRESSION,
        EYES_CLOSED,
        SPOOF_SUSPECTED,
        ERROR,
    }
}


class FaceScannerAnalyzer(
    private val ovalBoundsFractional: RectFractional,
    private val antiSpoofClassifier: FaceAntiSpoofingClassifier? = null,
    private val poseToleranceDeg: Float = 10f,
    private val minFaceFraction: Float = 0.18f,
    private val maxFaceFraction: Float = 0.80f,
    private val onValidation: (LensValidation) -> Unit,
) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.20f)
            .enableTracking()
            .build()
    )

    @Volatile
    private var inFlight = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (inFlight) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        
        val luminance = averageLuminance(imageProxy)
        if (luminance < MIN_LUMINANCE) {
            onValidation(LensValidation.Invalid(
                reason = "Too dark — move into better light",
                kind = LensValidation.InvalidKind.BAD_LIGHTING,
            ))
            imageProxy.close()
            return
        }
        if (luminance > MAX_LUMINANCE) {
            onValidation(LensValidation.Invalid(
                reason = "Too bright — move away from direct light",
                kind = LensValidation.InvalidKind.BAD_LIGHTING,
            ))
            imageProxy.close()
            return
        }

        inFlight = true
        val rotation = imageProxy.imageInfo.rotationDegrees
        val input = InputImage.fromMediaImage(mediaImage, rotation)
        val rotated = rotatedFrameSize(imageProxy, rotation)
        detector.process(input)
            .addOnSuccessListener { faces ->
                handleFaces(faces, imageProxy, rotated)
            }
            .addOnFailureListener {
                onValidation(LensValidation.Invalid(
                    reason = "Couldn't process frame — try again",
                    kind = LensValidation.InvalidKind.ERROR,
                ))
            }
            .addOnCompleteListener {
                inFlight = false
                imageProxy.close()
            }
    }

    

    private fun handleFaces(
        faces: List<Face>,
        imageProxy: ImageProxy,
        rotatedSize: android.util.Size,
    ) {
        if (faces.isEmpty()) {
            onValidation(LensValidation.Invalid(
                reason = "Looking for your face…",
                kind = LensValidation.InvalidKind.NO_FACE,
            ))
            return
        }
        if (faces.size > 1) {
            onValidation(LensValidation.Invalid(
                reason = "Step away from other people in frame",
                kind = LensValidation.InvalidKind.MULTIPLE_FACES,
            ))
            return
        }
        val face = faces.first()
        val box = face.boundingBox
        val frameW = rotatedSize.width
        val frameH = rotatedSize.height
        val faceFraction = (box.width().toFloat() * box.height()) /
            (frameW.toFloat() * frameH.toFloat()).coerceAtLeast(1f)

        
        if (faceFraction < minFaceFraction) {
            onValidation(LensValidation.Invalid(
                reason = "Move closer to the camera",
                kind = LensValidation.InvalidKind.FACE_TOO_SMALL,
            ))
            return
        }
        if (faceFraction > maxFaceFraction) {
            onValidation(LensValidation.Invalid(
                reason = "Move slightly back",
                kind = LensValidation.InvalidKind.FACE_TOO_SMALL,
            ))
            return
        }
        
        
        
        
        val oval = ovalBoundsFractional.toPixel(frameW, frameH)
        if (!boxIsWithin(box, oval, requiredOverlapFraction = 0.80f)) {
            onValidation(LensValidation.Invalid(
                reason = "Fit your face inside the oval",
                kind = LensValidation.InvalidKind.FACE_OFF_CENTER,
            ))
            return
        }

        
        if (abs(face.headEulerAngleX) > poseToleranceDeg ||
            abs(face.headEulerAngleY) > poseToleranceDeg ||
            abs(face.headEulerAngleZ) > poseToleranceDeg
        ) {
            onValidation(LensValidation.Invalid(
                reason = "Look straight at the camera",
                kind = LensValidation.InvalidKind.NOT_STRAIGHT,
            ))
            return
        }

        
        val smiling = face.smilingProbability ?: 0f
        val leftEye = face.leftEyeOpenProbability ?: 1f
        val rightEye = face.rightEyeOpenProbability ?: 1f
        if (smiling > MAX_SMILE_PROB) {
            onValidation(LensValidation.Invalid(
                reason = "Keep a neutral expression",
                kind = LensValidation.InvalidKind.WRONG_EXPRESSION,
            ))
            return
        }
        if (leftEye < MIN_EYE_OPEN_PROB || rightEye < MIN_EYE_OPEN_PROB) {
            onValidation(LensValidation.Invalid(
                reason = "Open your eyes",
                kind = LensValidation.InvalidKind.EYES_CLOSED,
            ))
            return
        }

        
        antiSpoofClassifier?.let { classifier ->
            val crop = cropFaceBitmap(imageProxy, box)
            if (crop != null) {
                val score = runCatching { classifier.spoofScore(crop) }.getOrNull()
                crop.recycle()
                if (score != null && score > FaceAntiSpoofingClassifier.DEFAULT_SPOOF_THRESHOLD) {
                    onValidation(LensValidation.Invalid(
                        reason = "Hold up — that doesn't look like a live face",
                        kind = LensValidation.InvalidKind.SPOOF_SUSPECTED,
                    ))
                    return
                }
            }
        }

        
        onValidation(LensValidation.Valid(
            faceBoundingBox = box,
            previewSize = rotatedSize,
        ))
    }

    
    
    

    private fun averageLuminance(image: ImageProxy): Int {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val sampleStride = 100
        var total = 0L
        var count = 0
        var i = 0
        while (i < buffer.remaining()) {
            total += buffer.get(i).toInt() and 0xFF
            count++
            i += sampleStride
        }
        return if (count == 0) 0 else (total / count).toInt()
    }

    private fun rotatedFrameSize(image: ImageProxy, rotation: Int): android.util.Size =
        if (rotation == 90 || rotation == 270) {
            android.util.Size(image.height, image.width)
        } else {
            android.util.Size(image.width, image.height)
        }

    private fun boxIsWithin(box: Rect, oval: Rect, requiredOverlapFraction: Float): Boolean {
        val intersect = Rect(box).apply { intersect(oval) }
        val intersectArea = (intersect.width().toFloat() * intersect.height()).coerceAtLeast(0f)
        val boxArea = (box.width().toFloat() * box.height()).coerceAtLeast(1f)
        return (intersectArea / boxArea) >= requiredOverlapFraction
    }

    @OptIn(ExperimentalGetImage::class)
    private fun cropFaceBitmap(image: ImageProxy, box: Rect): Bitmap? {
        
        
        
        val media = image.image ?: return null
        val nv21 = yuv420ToNv21(media)
        val yuv = YuvImage(nv21, android.graphics.ImageFormat.NV21, media.width, media.height, null)
        val out = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, media.width, media.height), 80, out)
        val full = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
            ?: return null
        val rotation = image.imageInfo.rotationDegrees.toFloat()
        val rotated = if (rotation != 0f) {
            val m = Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(full, 0, 0, full.width, full.height, m, true).also { full.recycle() }
        } else full
        
        val safe = Rect(
            box.left.coerceIn(0, rotated.width),
            box.top.coerceIn(0, rotated.height),
            box.right.coerceIn(0, rotated.width),
            box.bottom.coerceIn(0, rotated.height),
        )
        if (safe.width() <= 1 || safe.height() <= 1) {
            rotated.recycle()
            return null
        }
        val crop = Bitmap.createBitmap(rotated, safe.left, safe.top, safe.width(), safe.height())
        if (crop !== rotated) rotated.recycle()
        return crop
    }

    private fun yuv420ToNv21(image: android.media.Image): ByteArray {
        val ySize = image.planes[0].buffer.remaining()
        val uSize = image.planes[1].buffer.remaining()
        val vSize = image.planes[2].buffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        image.planes[0].buffer.get(nv21, 0, ySize)
        image.planes[2].buffer.get(nv21, ySize, vSize)
        image.planes[1].buffer.get(nv21, ySize + vSize, uSize)
        return nv21
    }

    companion object {
        private const val MIN_LUMINANCE = 40
        private const val MAX_LUMINANCE = 220
        private const val MAX_SMILE_PROB = 0.30f
        private const val MIN_EYE_OPEN_PROB = 0.75f
    }
}


data class RectFractional(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun toPixel(frameW: Int, frameH: Int): Rect = Rect(
        (left * frameW).toInt(),
        (top * frameH).toInt(),
        (right * frameW).toInt(),
        (bottom * frameH).toInt(),
    )

    companion object {
        
        val CENTERED_OVAL: RectFractional = RectFractional(
            left = 0.15f, top = 0.20f, right = 0.85f, bottom = 0.80f,
        )
    }
}
