package pk.edu.ucp.saharaai.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import pk.edu.ucp.saharaai.data.model.AiChatSession
import pk.edu.ucp.saharaai.data.model.FirestoreMessage
import pk.edu.ucp.saharaai.data.model.SaharaChatTurnMetadata
import pk.edu.ucp.saharaai.data.model.SaharaMessageType
import pk.edu.ucp.saharaai.data.model.SaharaRiskLevel
import pk.edu.ucp.saharaai.data.remote.FirestoreService
import pk.edu.ucp.saharaai.data.remote.SaharaAiClient
import java.time.LocalDate
import java.time.ZoneId


object ChatRepository {
    private const val TAG = "ChatRepository"

    
    fun legacyAiSessionId(userId: String) = "ai_$userId"

    
    fun counselorSessionId(userId: String, counselorId: String): String {
        val sorted = listOf(userId, counselorId).sorted()
        return "${sorted[0]}_${sorted[1]}"
    }

    
    suspend fun sendMessage(
        sessionId: String,
        senderId: String,
        receiverId: String,
        content: String,
        isFromAI: Boolean = false,
        messageType: String = "TEXT",
        senderType: String = if (isFromAI) "ai" else "user",
    ): Result<String> {
        val msg = FirestoreMessage(
            sessionId   = sessionId,
            senderId    = senderId,
            receiverId  = receiverId,
            content     = content,
            isFromAI    = isFromAI,
            messageType = messageType,
            senderType  = senderType,
            timestamp   = Timestamp.now()
        )
        return FirestoreService.sendMessage(msg)
    }

    /**
     * AI messages are written with senderId=userId so they pass the current
     * Firestore rule (`senderId == request.auth.uid`). `isFromAI/senderType`
     * remain the rendering source of truth, so the UI still treats the message
     * as assistant-authored and older `senderId == "ai"` docs still read back.
     */
    suspend fun sendAiMessage(
        sessionId: String,
        userId: String,
        content: String,
        messageType: String = "TEXT",
        metadata: SaharaChatTurnMetadata? = null,
    ): Result<String> {
        val msg = FirestoreMessage(
            sessionId   = sessionId,
            senderId    = userId,
            receiverId  = "ai",
            content     = content,
            isFromAI    = true,
            messageType = messageType,
            senderType  = "ai",
            riskLevel = metadata?.riskLevel?.name.orEmpty(),
            triggerCounselor = metadata?.triggerCounselor ?: false,
            substanceDetected = metadata?.substanceDetected.orEmpty(),
            substancesDetected = metadata?.substancesDetected.orEmpty(),
            actionDestination = metadata?.actionDestination.orEmpty(),
            quickReplies = metadata?.quickReplies.orEmpty(),
            safetyFlags = metadata?.safetyFlags.orEmpty(),
            detectedSymptoms = metadata?.detectedSymptoms.orEmpty(),
            userIntent = metadata?.userIntent.orEmpty(),
            timestamp = Timestamp.now(),
        )
        return FirestoreService.sendMessage(msg)
    }

    
    fun getMessagesFlow(sessionId: String): Flow<List<FirestoreMessage>> =
        FirestoreService.getMessagesFlow(sessionId)

    
    suspend fun getMessagesOnce(sessionId: String): Result<List<FirestoreMessage>> =
        FirestoreService.getMessagesOnce(sessionId)

    fun createAiChatSession(
        userId: String,
        clientCreatedAtMillis: Long = System.currentTimeMillis(),
        sessionId: String = "",
    ): Result<AiChatSession> =
        FirestoreService.createAiChatSession(userId, clientCreatedAtMillis, sessionId)

    suspend fun getLatestAiChatSession(userId: String): Result<AiChatSession?> =
        FirestoreService.getLatestAiChatSession(userId)

    fun getAiChatSessionsFlow(userId: String): Flow<List<AiChatSession>> =
        FirestoreService.getAiChatSessionsFlow(userId)

    suspend fun getAiChatSessionsOnce(userId: String): Result<List<AiChatSession>> =
        FirestoreService.getAiChatSessionsOnce(userId)

    fun updateAiChatSessionAfterMessage(sessionId: String, preview: String) =
        FirestoreService.updateAiChatSessionAfterMessage(sessionId, preview)

    suspend fun renameAiChatSessionFromServerTime(sessionId: String, title: String): Result<Unit> =
        FirestoreService.renameAiChatSessionFromServerTime(sessionId, title)

    suspend fun deleteAiChatSession(sessionId: String): Result<Unit> =
        FirestoreService.deleteAiChatSession(sessionId)

    suspend fun hasUserAiMessageToday(
        userId: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (userId.isBlank()) return false
        val today = LocalDate.now(zoneId)
        val sessionIds = buildList {
            addAll(getAiChatSessionsOnce(userId).getOrDefault(emptyList()).map { it.sessionId })
            val legacy = legacyAiSessionId(userId)
            if (legacy !in this) add(legacy)
        }
        return sessionIds.any { sessionId ->
            getMessagesOnce(sessionId).getOrDefault(emptyList()).any { message ->
                !message.isFromAI &&
                    message.senderId == userId &&
                    message.timestamp.toDate().toInstant().atZone(zoneId).toLocalDate() == today
            }
        }
    }


    suspend fun markRead(messageId: String): Result<Unit> =
        FirestoreService.markMessageRead(messageId)

    suspend fun updateMessageContent(messageId: String, newContent: String): Result<Unit> =
        FirestoreService.updateMessageContent(messageId, newContent)

    /** Nukes every message in [sessionId]. Used by the "New chat" action. */
    suspend fun clearSession(sessionId: String): Result<Unit> =
        FirestoreService.deleteAllMessages(sessionId)

    /**
     * Asks Qalb to compress a 16-message batch into a short paragraph that
     * preserves the user's situation, concerns, substance/risk mentions,
     * and the assistant's guidance. Returns the summary text or null if
     * the endpoint isn't configured / the call fails — caller falls back
     * to keeping the live history intact in that case.
     */
    suspend fun summarizeBatch(
        messages: List<SaharaAiClient.HistoryTurn>,
        isEnglish: Boolean,
    ): String? = runCatching {
        GeminiChatService.summarise(messages, isEnglish).takeIf { it.isNotBlank() }
    }.getOrNull()

    /**
     * Persists a fresh batch summary onto the session document and bumps
     * `summarizedThroughMs` so the next history build skips the messages
     * the summary now represents.
     */
    suspend fun appendBatchSummary(
        sessionId: String,
        summary: String,
        summarizedThroughMs: Long,
    ): Result<Unit> = FirestoreService.appendAiChatBatchSummary(
        sessionId = sessionId,
        summary = summary,
        summarizedThroughMs = summarizedThroughMs,
    )

    
    
    

    
    data class SaharaReply(
        val text: String,
        val metadata: SaharaChatTurnMetadata,
        val viaLiveModel: Boolean,
    )

    class SaharaUnavailableException(
        message: String = "Please check your internet connection and try again.",
    ) : IllegalStateException(message)

    
    suspend fun askSahara(
        userText: String,
        isEnglish: Boolean,
        messageId: String,
        history: List<SaharaAiClient.HistoryTurn> = emptyList(),
        priorSummaries: List<String> = emptyList(),
    ): SaharaReply {
        // Direct call to Gemini via the Firebase AI Logic SDK. Modal is no
        // longer in the chat critical path — that proxy was overengineering
        // for what is fundamentally an Android-talks-to-Gemini call, and
        // Firebase AI Logic is the industry-standard pattern: first-party
        // SDK, key off-device, no extra deploy surface.
        val rawReply = askGeminiWithOneRetry(
            userText = userText,
            history = history,
            priorSummaries = priorSummaries,
            isEnglish = isEnglish,
        ).trim()
        if (rawReply.isBlank()) {
            throw SaharaUnavailableException()
        }

        // Crisis / substance signalling moved from server-side response
        // metadata to client-side heuristics — Gemini doesn't surface
        // structured flags, just text. The inline crisis-card attachment
        // still fires correctly because ChatScreen reads
        // [SaharaChatTurnMetadata.triggerCounselor] and message_type below.
        val specificSubstance = detectLocalSubstance(userText)
        val substanceDetected = specificSubstance
            ?: if (userText.hasGenericSubstanceConcern()) "Substance use concern" else null
        val isCrisis = detectLocalCrisis(userText)
        val shouldAttachSupport = isCrisis || substanceDetected != null
        val reply = refineLiveReply(
            reply = rawReply,
            substanceDetected = specificSubstance,
            userIntent = if (substanceDetected != null) "substance_use_support" else null,
            isEnglish = isEnglish,
        ).normalizeSaharaSelfGender(isEnglish)
            .compactSaharaReply(isEnglish)
        return SaharaReply(
            text = reply,
            metadata = SaharaChatTurnMetadata(
                messageId          = messageId,
                messageType        = if (shouldAttachSupport) SaharaMessageType.CRISIS_CARD else SaharaMessageType.TEXT,
                riskLevel          = when {
                    isCrisis -> SaharaRiskLevel.HIGH
                    substanceDetected != null -> SaharaRiskLevel.MEDIUM
                    else -> SaharaRiskLevel.LOW
                },
                triggerCounselor   = shouldAttachSupport,
                substanceDetected  = substanceDetected,
                substancesDetected = substanceDetected?.let { listOf(it) }.orEmpty(),
                actionDestination  = if (shouldAttachSupport) "emergency" else null,
                quickReplies       = emptyList(),
                safetyFlags        = buildList {
                    if (isCrisis) add("crisis")
                    substanceDetected?.let { add(it) }
                },
                detectedSymptoms   = emptyList(),
                userIntent         = if (substanceDetected != null) "substance_use_support" else null,
            ),
            viaLiveModel = true,
        )
    }

    private suspend fun askGeminiWithOneRetry(
        userText: String,
        history: List<SaharaAiClient.HistoryTurn>,
        priorSummaries: List<String>,
        isEnglish: Boolean,
    ): String {
        var lastError: Throwable? = null
        repeat(2) { attempt ->
            val result = runCatching {
                GeminiChatService.reply(
                    userText = userText,
                    history = history,
                    priorSummaries = priorSummaries,
                    isEnglish = isEnglish,
                )
            }
            val reply = result.getOrElse { error ->
                lastError = error
                Log.w(TAG, "Gemini SDK reply failed on attempt ${attempt + 1}", error)
                ""
            }.trim()
            if (reply.isNotBlank()) return reply
            if (attempt == 0) delay(450)
        }
        lastError?.let { throw SaharaUnavailableException() }
        return ""
    }

    private fun refineLiveReply(
        reply: String,
        substanceDetected: String?,
        userIntent: String?,
        isEnglish: Boolean,
    ): String {
        val substance = substanceDetected?.takeIf { it.isNotBlank() && it != "unknown" }
            ?: return reply
        if (userIntent !in setOf("substance_use_support", "craving_panic_or_relapse")) return reply
        val lower = reply.lowercase()
        val asksUnknownSubstance = lower.contains("kya use kiya") ||
            lower.contains("what did you use") ||
            lower.contains("what you used")
        if (!asksUnknownSubstance) return reply
        return localSubstanceReply(substance, isEnglish)
    }

    private fun String.normalizeSaharaSelfGender(isEnglish: Boolean): String {
        if (isEnglish) return this
        return this
            .replace(
                Regex("\\b[Mm]ain\\b([^.!?\\n]{0,180}?)\\bsakti\\s+hoon\\b")
            ) { match -> "Main${match.groupValues[1]}sakta hoon" }
            .replace(
                Regex("\\b[Mm]ain\\b([^.!?\\n]{0,180}?)\\brahi\\s+hoon\\b")
            ) { match -> "Main${match.groupValues[1]}raha hoon" }
            .replace(
                Regex("\\b[Mm]ain\\b([^.!?\\n]{0,180}?)\\bkarungi\\b")
            ) { match -> "Main${match.groupValues[1]}karunga" }
            .replace(
                Regex("\\bnahi\\s+de\\s+sakti\\b", RegexOption.IGNORE_CASE),
                "nahi de sakta",
            )
    }

    private fun String.compactSaharaReply(isEnglish: Boolean): String {
        var text = trim()
            .replace(Regex("\\s+"), " ")
            .removeLeadingFiller(isEnglish)
            .trim()
        if (text.isBlank()) return trim()

        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (sentences.size > 2) {
            text = sentences.take(2).joinToString(" ")
        }
        if (text.length <= 360) return text

        val compact = text.take(360).trimEnd()
        val lastStop = listOf(
            compact.lastIndexOf('.'),
            compact.lastIndexOf('!'),
            compact.lastIndexOf('?'),
        ).maxOrNull() ?: -1
        return if (lastStop >= 120) compact.take(lastStop + 1) else "$compact..."
    }

    private fun String.removeLeadingFiller(isEnglish: Boolean): String {
        val pattern = if (isEnglish) {
            Regex(
                "^\\s*(I understand[^.!?]*[.!?]\\s*|I hear you[^.!?]*[.!?]\\s*|I'm sorry[^.!?]*[.!?]\\s*|I am sorry[^.!?]*[.!?]\\s*)",
                RegexOption.IGNORE_CASE,
            )
        } else {
            Regex(
                "^\\s*(Main samajh raha hoon[^.!?]*[.!?]\\s*|Main sun raha hoon[^.!?]*[.!?]\\s*|Main aapki baat sun raha hoon[^.!?]*[.!?]\\s*|Main aapke saath hoon[^.!?]*[.!?]\\s*|Mujhe afsos hai[^.!?]*[.!?]\\s*)",
                RegexOption.IGNORE_CASE,
            )
        }
        return replace(pattern, "")
    }

    /**
     * Bilingual crisis-vocabulary scan. Drives `triggerCounselor=true` on
     * the metadata, which makes ChatScreen render the inline "Talk to a
     * counselor" attachment under the bot bubble. Gemini gives us natural
     * text only; the structured signalling is our responsibility.
     */
    private fun detectLocalCrisis(userText: String): Boolean {
        val lower = userText.lowercase()
        return lower.containsAny(
            "suicide", "khudkushi", "khud kushi", "self harm", "self-harm",
            "kill myself", "marna chahta", "marna chahti",
            "overdose", "od kiya", "blue lips", "neelay hont", "neele hont",
            "fainting", "behosh", "behoshi", "saans nahi",
            "chest pain", "seene mein dard", "fit aya", "fit aaya",
            "withdrawal", "withdrawals",
        )
    }

    private fun detectLocalSubstance(userText: String): String? {
        val lower = userText.lowercase()
        return when {
            lower.containsAny("charas", "chars", "chrs", "churs", "ganja", "weed", "hash") ->
                "Cannabis / Charas"
            lower.containsAny("xanax", "xnx", "zanax", "rivo", "lexo", "benzo") ->
                "Unprescribed Xanax / Benzodiazepines"
            lower.containsAny("ice", "aiis", "ayis", "meth", "crystal") ->
                "Ice / Methamphetamine"
            lower.containsAny("chitta", "heroin", "smack", "afeem", "opioid") ->
                "Heroin / Opioids"
            lower.containsAny("sharab", "daru", "daaru", "alcohol") ->
                "Alcohol"
            else -> null
        }
    }

    private fun localSubstanceReply(substance: String, isEnglish: Boolean): String {
        val label = when (substance) {
            "Cannabis / Charas" -> "charas/cannabis"
            "Unprescribed Xanax / Benzodiazepines" -> "Xanax/benzo"
            "Ice / Methamphetamine" -> "ice/meth"
            "Heroin / Opioids" -> "chitta/heroin/opioid"
            "Alcohol" -> if (isEnglish) "alcohol" else "sharab/alcohol"
            else -> substance
        }
        return if (isEnglish) {
            "This sounds like $label. When did you last take it, and what is happening in your body now? If breathing, chest pain, fainting, seizure, or blue lips are involved, open emergency."
        } else {
            "Yeh $label lag raha hai. Last kab li, aur ab body mein kya ho raha hai? Saans rukna, seenay mein dard, behoshi, fit, ya neelay hont ho to emergency kholo."
        }
    }

    private fun String.hasGenericSubstanceConcern(): Boolean {
        val clean = lowercase()
        return clean.containsAny(
            "nashay ki", "nashe ki", "nasha ki", "nashay", "nashe", "nasha",
            "latt", "lat lag", "lat lagi", "lat lag gayi", "aadat lag",
            "addiction", "addicted", "craving", "cravings",
        )
    }

    private fun String.containsAny(vararg keywords: String) = keywords.any { this.contains(it) }
}
