package pk.edu.ucp.saharaai.data.repository

import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.HarmBlockThreshold
import com.google.firebase.ai.type.HarmCategory
import com.google.firebase.ai.type.SafetySetting
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import pk.edu.ucp.saharaai.data.remote.SaharaAiClient

/**
 * Direct Gemini chat via the Firebase AI Logic SDK ("firebase-ai").
 *
 * This replaces the Modal-hosted /v1/chat proxy. Modal was being used as a
 * thin shim to call Google's generative-language API on behalf of the
 * Android client, but for an Android app talking to Gemini, the industry-
 * standard pattern is to use Firebase AI Logic's first-party SDK:
 *   - The Gemini API key lives on Firebase's backend, never in the APK.
 *   - Firebase App Check (when enabled) gates the calls against abuse.
 *   - No extra proxy hop, no Modal deploy step, no second 12-factor
 *     surface to debug. One round-trip from device -> Firebase -> Gemini.
 *
 * The wire-style still mirrors what SaharaAiClient.postChat used to send
 * (system prompt + prior summaries + history of {role, content} +
 * the new user input) so ChatRepository / ChatViewModel didn't need to
 * change their data shape — they just call this object instead of the
 * old HTTP client.
 */
object GeminiChatService {

    // gemini-3.5-flash is the current free-tier Flash model. The 2.0
    // Flash variants were end-of-life'd in June 2026; 3.x is the
    // supported track. Override at run-time by editing this constant if
    // a different version is preferred — Firebase AI Logic exposes
    // every Gemini model via the same SDK surface.
    private const val MODEL_NAME = "gemini-3.5-flash"

    // Safety filters are explicitly loosened for all four categories. A
    // mental-health/substance-use chat legitimately surfaces words like
    // "khudkushi", "sharab", "behoshi" — Gemini's default thresholds
    // would short-circuit those turns with a refusal, and the user sees
    // a silent empty reply. Our own crisis-card heuristic
    // (CRISIS_TERMS in ChatRepository) handles routing instead.
    private val SAFETY = listOf(
        SafetySetting(HarmCategory.HARASSMENT, HarmBlockThreshold.NONE),
        SafetySetting(HarmCategory.HATE_SPEECH, HarmBlockThreshold.NONE),
        SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, HarmBlockThreshold.NONE),
        SafetySetting(HarmCategory.DANGEROUS_CONTENT, HarmBlockThreshold.NONE),
    )

    /** Generate a turn-reply to [userText], with the prior turns of this
     *  session in [history] (oldest-first) and previously summarised
     *  batches in [priorSummaries]. Returns the model's plain-text
     *  output, trimmed; an empty string if Gemini returns nothing. */
    suspend fun reply(
        userText: String,
        history: List<SaharaAiClient.HistoryTurn>,
        priorSummaries: List<String>,
        isEnglish: Boolean,
    ): String {
        val languageHint = if (isEnglish) "english" else "roman_urdu"
        val model = buildModel(
            systemText = buildChatSystemPrompt(languageHint, priorSummaries),
            maxOutputTokens = 512,
        )
        val chat = model.startChat(
            history = history.map { turn ->
                val role = if (turn.role == "assistant") "model" else "user"
                content(role = role) { text(turn.content) }
            }
        )
        return chat.sendMessage(userText).extractText()
    }

    /** Compress a 16-message batch (8 user + 8 assistant) into 3-5
     *  short sentences that preserve substance / risk / emotional-state
     *  signals. Used by ChatViewModel's batch-summarisation cycle. */
    suspend fun summarise(
        messages: List<SaharaAiClient.HistoryTurn>,
        isEnglish: Boolean,
    ): String {
        val languageHint = if (isEnglish) "english" else "roman_urdu"
        val model = buildModel(
            systemText = buildSummariseSystemPrompt(languageHint),
            maxOutputTokens = 320,
        )
        val transcript = buildString {
            messages.forEach { t ->
                val who = if (t.role == "assistant") "Assistant" else "User"
                appendLine("$who: ${t.content}")
            }
        }.trim()
        return model.generateContent(transcript).extractText()
    }

    private fun buildModel(systemText: String, maxOutputTokens: Int): GenerativeModel {
        return Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.6f
                topP = 0.95f
                topK = 40
                this.maxOutputTokens = maxOutputTokens
            },
            safetySettings = SAFETY,
            systemInstruction = content { text(systemText) },
        )
    }

    private fun buildChatSystemPrompt(
        languageHint: String,
        priorSummaries: List<String>,
    ): String {
        val parts = mutableListOf(
            "You are SAHARA, a warm bilingual companion for Pakistani users who may be " +
                "dealing with substance use, stress, or low mood. You are NOT a doctor.",
            "RULES (follow strictly):\n" +
                "1. Reply with 2 to 4 short, natural sentences. Never one-liners.\n" +
                "2. Match the user's language exactly. If they wrote Roman Urdu, reply in Roman Urdu " +
                "(Hindi-Urdu in Latin letters). If English, English. If mixed, mix the same way. " +
                "NEVER use Urdu script.\n" +
                "3. NEVER greet the user. NEVER say 'Salam', 'Hello', 'Hi', or introduce yourself. " +
                "You are already in conversation.\n" +
                "4. NEVER repeat your previous reply or re-ask a question the user already answered.\n" +
                "5. If the user's message is short or ambiguous (e.g. 'gla khrab ha', '?', 'ok'), " +
                "DO NOT ask 'what danger are you in' or generic questions. Instead, gently ask a " +
                "CONCRETE follow-up about THIS message (e.g. 'kab se khrab hai gala? Kuch liya hai aaj?').\n" +
                "6. NEVER give medical advice, dosages, diagnoses, or recovery protocols. " +
                "NEVER moralise about substance use.\n" +
                "7. Acknowledge what the user said in your own words first, THEN ask one focused " +
                "follow-up question.",
            "If the user mentions chest pain, fainting, blue lips, a fit, no breathing, suicidal " +
                "thoughts, or self-harm, reply warmly and add ONE line asking them to open the " +
                "counselor list or press the Emergency button right now. Do not panic them.",
        )
        when (languageHint) {
            "english" -> parts += "Reply in English this turn."
            "roman_urdu" -> parts += "Reply in Roman Urdu this turn (Hindi-Urdu in Latin letters, no Urdu script)."
        }
        priorSummaries.filter { it.isNotBlank() }
            .takeIf { it.isNotEmpty() }
            ?.let { summaries ->
                parts += "Earlier in this conversation (compressed summaries, oldest first; " +
                    "treat as established background, do NOT mention them explicitly): " +
                    summaries.joinToString(" | ")
            }
        return parts.joinToString("\n\n")
    }

    private fun buildSummariseSystemPrompt(languageHint: String): String {
        return "You are compressing a Sahara mental-health chat into 3-5 short sentences. " +
            "Preserve: any substance mentioned (name, frequency, context), risk signals " +
            "(suicidal thoughts, withdrawal, self-harm), the user's emotional state, and " +
            "the assistant's last guidance. Use the same language the user used " +
            "($languageHint). No new advice — just the compressed factual summary."
    }

    private fun GenerateContentResponse.extractText(): String = text.orEmpty().trim()
}
