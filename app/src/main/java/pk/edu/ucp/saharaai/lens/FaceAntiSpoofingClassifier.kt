package pk.edu.ucp.saharaai.lens

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs


class FaceAntiSpoofingClassifier private constructor(
    private val interpreter: Interpreter,
) : Closeable {

    
    fun spoofScore(faceBitmap: Bitmap): Float {
        val resized = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true)
        val input = normalizeImage(resized)
        
        
        
        val clssPred = Array(1) { FloatArray(NUM_CLASSES) }
        val leafMask = Array(1) { FloatArray(NUM_CLASSES) }
        val outputs = mutableMapOf<Int, Any>(
            interpreter.getOutputIndex("Identity")   to clssPred,
            interpreter.getOutputIndex("Identity_1") to leafMask,
        )
        interpreter.runForMultipleInputsOutputs(arrayOf<Any>(input), outputs)

        var score = 0f
        for (i in 0 until NUM_CLASSES) {
            score += abs(clssPred[0][i]) * leafMask[0][i]
        }
        if (resized !== faceBitmap) resized.recycle()
        return score
    }

    
    fun isReal(faceBitmap: Bitmap, threshold: Float = DEFAULT_SPOOF_THRESHOLD): Boolean =
        spoofScore(faceBitmap) <= threshold

    override fun close() {
        interpreter.close()
    }

    companion object {
        private const val MODEL_FILE = "FaceAntiSpoofing.tflite"
        private const val INPUT_SIZE = 256
        private const val NUM_CLASSES = 8

        
        const val DEFAULT_SPOOF_THRESHOLD: Float = 0.2f

        
        fun create(context: Context, numThreads: Int = 2): FaceAntiSpoofingClassifier {
            val model = loadModelFile(context)
            val opts = Interpreter.Options().apply {
                setNumThreads(numThreads)
            }
            return FaceAntiSpoofingClassifier(Interpreter(model, opts))
        }

        private fun loadModelFile(context: Context): MappedByteBuffer {
            val afd = context.assets.openFd(MODEL_FILE)
            FileInputStream(afd.fileDescriptor).use { fis ->
                val channel = fis.channel
                return channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    afd.startOffset,
                    afd.declaredLength,
                )
            }
        }

        private fun normalizeImage(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            
            
            val out = Array(1) {
                Array(h) {
                    Array(w) { FloatArray(3) }
                }
            }
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val v = pixels[y * w + x]
                    out[0][y][x][0] = ((v shr 16) and 0xFF) / 255.0f
                    out[0][y][x][1] = ((v shr 8) and 0xFF) / 255.0f
                    out[0][y][x][2] = (v and 0xFF) / 255.0f
                }
            }
            return out
        }
    }
}
