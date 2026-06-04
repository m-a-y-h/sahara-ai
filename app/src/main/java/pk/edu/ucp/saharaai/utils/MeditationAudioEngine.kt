package pk.edu.ucp.saharaai.utils

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.ToneGenerator
import android.media.AudioManager
import kotlin.math.PI
import kotlin.math.sin


class MeditationAudioEngine {

    companion object {
        private const val SAMPLE_RATE = 44_100          
        private const val FADE_STEPS  = 80              
        private const val CHUNK_FRAMES = 2_048          

        
        fun frequencyFor(titleEn: String): Double = when {
            titleEn.contains("Sleep",      ignoreCase = true) -> 174.0
            titleEn.contains("Grounding",  ignoreCase = true) -> 285.0
            titleEn.contains("Morning",    ignoreCase = true) -> 396.0
            titleEn.contains("Anxiety",    ignoreCase = true) -> 417.0
            titleEn.contains("Body Scan",  ignoreCase = true) -> 432.0
            titleEn.contains("Focus",      ignoreCase = true) -> 528.0
            titleEn.contains("Gratitude",  ignoreCase = true) -> 741.0
            titleEn.contains("Stress",     ignoreCase = true) -> 417.0
            titleEn.contains("Alternate",  ignoreCase = true) -> 432.0
            titleEn.contains("Diaphragm",  ignoreCase = true) -> 396.0
            titleEn.contains("Resonance",  ignoreCase = true) -> 528.0
            titleEn.contains("Pursed",     ignoreCase = true) -> 285.0
            else                                              -> 432.0  
        }
    }

    @Volatile private var running   = false
    @Volatile private var targetVol = 0f     
    private var thread: Thread? = null
    private var audioTrack: AudioTrack? = null

    
    fun play(frequency: Double = 432.0, volumeFraction: Float = 0.35f) {
        stop()   

        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(CHUNK_FRAMES * 4)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
        running   = true
        targetVol = volumeFraction

        thread = Thread {
            generateLoop(frequency, volumeFraction)
        }.also { it.isDaemon = true; it.start() }
    }

    
    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }

    

    private fun generateLoop(freq: Double, maxVol: Float) {
        val track = audioTrack ?: return
        val buf   = ShortArray(CHUNK_FRAMES * 2)   
        val twoPi = 2.0 * PI
        val dPhase = twoPi * freq / SAMPLE_RATE

        
        val dPhase2 = twoPi * (freq * 2.0) / SAMPLE_RATE

        var phase1 = 0.0
        var phase2 = 0.0
        var currentVol = 0f                         
        val fadeIncrement = maxVol / FADE_STEPS

        while (running) {
            
            currentVol = when {
                currentVol < targetVol - fadeIncrement -> currentVol + fadeIncrement
                currentVol > targetVol + fadeIncrement -> currentVol - fadeIncrement
                else                                   -> targetVol
            }

            for (i in 0 until CHUNK_FRAMES) {
                val fundamental = sin(phase1) * 0.93
                val harmonic    = sin(phase2) * 0.07
                val sample      = ((fundamental + harmonic) * Short.MAX_VALUE * currentVol).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()

                buf[i * 2]     = sample   
                buf[i * 2 + 1] = sample   

                phase1 = (phase1 + dPhase)  % twoPi
                phase2 = (phase2 + dPhase2) % twoPi
            }

            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.write(buf, 0, buf.size)
            }
        }

        
        val fadeOut = (FADE_STEPS * 2)
        val fadeStep = currentVol / fadeOut
        for (step in 0 until fadeOut) {
            val vol = (currentVol - step * fadeStep).coerceAtLeast(0f)
            for (i in 0 until CHUNK_FRAMES) {
                val sample = (sin(phase1) * Short.MAX_VALUE * vol).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
                buf[i * 2] = sample; buf[i * 2 + 1] = sample
                phase1 = (phase1 + dPhase) % twoPi
            }
            if (track.state == AudioTrack.STATE_INITIALIZED) track.write(buf, 0, buf.size)
        }
    }
}




fun playBreathCue(isInhale: Boolean) {
    try {
        val tone = if (isInhale) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_BEEP2
        ToneGenerator(AudioManager.STREAM_MUSIC, 35)  
            .also { it.startTone(tone, 120) }
    } catch (_: Exception) {  }
}
