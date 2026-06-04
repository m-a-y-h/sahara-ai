package pk.edu.ucp.saharaai.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File


class VoiceRecorder(private val context: Context) {

    data class RecordedClip(val bytes: ByteArray, val mimeType: String, val durationMs: Long)

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt: Long = 0L

    fun start(): Result<Unit> = runCatching {
        if (recorder != null) error("recorder already running")
        val file = File.createTempFile("sahara_voice_", ".m4a", context.cacheDir)
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(16_000)
        r.setAudioChannels(1)
        r.setAudioEncodingBitRate(64_000)
        r.setOutputFile(file.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        outputFile = file
        startedAt = System.currentTimeMillis()
    }

    fun stop(): Result<RecordedClip> = runCatching {
        val r = recorder ?: error("recorder not running")
        val file = outputFile ?: error("missing output file")
        val durationMs = System.currentTimeMillis() - startedAt
        try {
            r.stop()
        } catch (_: RuntimeException) {
            
            
            r.reset()
            r.release()
            recorder = null
            outputFile = null
            file.delete()
            return@runCatching RecordedClip(ByteArray(0), "audio/m4a", 0L)
        }
        r.reset()
        r.release()
        recorder = null
        outputFile = null
        val bytes = file.readBytes()
        file.delete()
        RecordedClip(bytes = bytes, mimeType = "audio/m4a", durationMs = durationMs)
    }

    fun cancel() {
        recorder?.runCatching { stop() }
        recorder?.runCatching { reset() }
        recorder?.release()
        recorder = null
        outputFile?.runCatching { delete() }
        outputFile = null
    }

    fun release() {
        cancel()
    }

    val isRecording: Boolean get() = recorder != null
}
