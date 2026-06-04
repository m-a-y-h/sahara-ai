package pk.edu.ucp.saharaai.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Press-and-hold voice-to-text driver for the chat input.
 *
 * Replaces the previous "record audio file -> upload to Modal sahara-voice
 * for emotion analysis -> get a tone label" path. For the chat use case we
 * just want what the user said as text so it can flow into the same Gemini
 * pipeline as typed messages. Android's on-device [SpeechRecognizer] is the
 * obvious fit: free, no extra quota, low-latency, supports Roman-Urdu-ish
 * code-mixed input when targeted at `en-IN`.
 *
 * Lifecycle: build one [VoiceTranscriber] per chat-screen composition,
 * call [start] on press, [stop] on release, [release] in
 * `DisposableEffect.onDispose`. Calls are idempotent — calling [start]
 * while already listening is a no-op.
 */
class VoiceTranscriber(private val context: Context) {

    interface Callbacks {
        /** Live partial recognition results — useful for showing "..." in
         *  the input field while the user speaks. May be invoked many
         *  times before [onFinal]. Safe to ignore. */
        fun onPartial(text: String)

        /** Final transcript after the user releases the mic. May be empty
         *  if Google's STT couldn't make anything of the audio. */
        fun onFinal(text: String)

        /** Recoverable error (no audio, permission, network). Caller
         *  should surface a localised toast. */
        fun onError(message: String)
    }

    private var recognizer: SpeechRecognizer? = null
    private var listening: Boolean = false

    fun start(isEnglish: Boolean, callbacks: Callbacks) {
        if (listening) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callbacks.onError(
                if (isEnglish) "Voice input isn't available on this device."
                else "Voice input is device par dastiyab nahi."
            )
            return
        }

        // Tear down any prior session before starting a fresh one — the
        // RecognitionListener stays attached otherwise and stale callbacks
        // can fire into a disposed Compose state.
        recognizer?.destroy()

        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                    .trim()
                listening = false
                callbacks.onFinal(text)
            }

            override fun onPartialResults(partial: Bundle?) {
                val text = partial
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isNotEmpty()) callbacks.onPartial(text)
            }

            override fun onError(error: Int) {
                listening = false
                val message = mapErrorMessage(error, isEnglish)
                Log.w("VoiceTranscriber", "STT error $error -> $message")
                if (message != null) callbacks.onError(message)
            }

            // The remaining listener methods are noise for this use case.
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            // en-IN handles Pakistani-Roman-Urdu code-mixing better than
            // either ur-PK (which writes Urdu script, not Roman) or en-US
            // (which mis-transcribes Urdu words). Toggle to en-US when the
            // user is in English UI mode.
            val locale = if (isEnglish) Locale.US.toLanguageTag() else "en-IN"
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // No system UI; we drive the press-and-hold ourselves.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        }

        listening = true
        runCatching { r.startListening(intent) }
            .onFailure {
                listening = false
                callbacks.onError(
                    if (isEnglish) "Could not start voice input." else "Voice input shuru nahi ho saki."
                )
            }
    }

    fun stop() {
        if (!listening) return
        // stopListening() finalises whatever was captured and triggers
        // onResults; calling destroy() here would cut the final callback off.
        runCatching { recognizer?.stopListening() }
    }

    fun cancel() {
        if (!listening) return
        listening = false
        runCatching { recognizer?.cancel() }
    }

    fun release() {
        listening = false
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun mapErrorMessage(code: Int, isEnglish: Boolean): String? = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> if (isEnglish)
            "Couldn't catch that — try again." else "Samajh nahi aaya - dobara try karein."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> null   // benign, ignore
        SpeechRecognizer.ERROR_AUDIO -> if (isEnglish)
            "Microphone trouble. Try again." else "Microphone mein masla. Dobara karein."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> if (isEnglish)
            "Network problem — voice needs internet." else "Net ka masla - voice ko internet chahiye."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> if (isEnglish)
            "Microphone permission is off." else "Microphone ki ijazat nahi hai."
        else -> if (isEnglish)
            "Voice input failed ($code)." else "Voice input nakam ($code)."
    }
}
