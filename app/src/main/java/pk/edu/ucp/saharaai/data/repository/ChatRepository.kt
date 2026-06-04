package pk.edu.ucp.saharaai.data.repository

import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import pk.edu.ucp.saharaai.BuildConfig
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
        val raw = runCatching {
            GeminiChatService.reply(
                userText = userText,
                history = history,
                priorSummaries = priorSummaries,
                isEnglish = isEnglish,
            )
        }.getOrDefault("")
        val rawReply = raw.trim()
        if (rawReply.isBlank()) {
            return fallback(userText, isEnglish, messageId)
        }

        // Crisis / substance signalling moved from server-side response
        // metadata to client-side heuristics — Gemini doesn't surface
        // structured flags, just text. The inline crisis-card attachment
        // still fires correctly because ChatScreen reads
        // [SaharaChatTurnMetadata.triggerCounselor] and message_type below.
        val substanceDetected = detectLocalSubstance(userText)
        val isCrisis = detectLocalCrisis(userText)
        val reply = refineLiveReply(
            reply = rawReply,
            substanceDetected = substanceDetected,
            userIntent = if (substanceDetected != null) "substance_use_support" else null,
            isEnglish = isEnglish,
        )
        return SaharaReply(
            text = reply,
            metadata = SaharaChatTurnMetadata(
                messageId          = messageId,
                messageType        = if (isCrisis) SaharaMessageType.CRISIS_CARD else SaharaMessageType.TEXT,
                riskLevel          = when {
                    isCrisis -> SaharaRiskLevel.HIGH
                    substanceDetected != null -> SaharaRiskLevel.MEDIUM
                    else -> SaharaRiskLevel.LOW
                },
                triggerCounselor   = isCrisis,
                substanceDetected  = substanceDetected,
                substancesDetected = substanceDetected?.let { listOf(it) }.orEmpty(),
                actionDestination  = if (isCrisis) "counselors" else null,
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

    private fun fallback(userText: String, isEnglish: Boolean, messageId: String): SaharaReply {
        val localSubstance = detectLocalSubstance(userText)
        val reply = localSubstance?.let { localSubstanceReply(it, isEnglish) }
            ?: buildLocalAIReply(userText, isEnglish)
        return SaharaReply(
            text = reply,
            metadata = SaharaChatTurnMetadata(
                messageId = messageId,
                messageType = if (localSubstance != null) SaharaMessageType.CRISIS_CARD else SaharaMessageType.TEXT,
                riskLevel = if (localSubstance != null) SaharaRiskLevel.MEDIUM else SaharaRiskLevel.UNKNOWN,
                substanceDetected = localSubstance,
                substancesDetected = localSubstance?.let { listOf(it) }.orEmpty(),
                actionDestination = if (localSubstance != null) "counselors" else null,
                quickReplies = if (localSubstance != null) {
                    if (isEnglish) listOf("Talk to counselor", "Safety check", "Open journal")
                    else listOf("Counselor se baat", "Safety check", "Journal kholo")
                } else emptyList(),
                safetyFlags = listOf("sahara_ai_unreachable"),
                userIntent = if (localSubstance != null) "substance_use_support" else null,
            ),
            viaLiveModel = false,
        )
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
            "I am reading this as $label. Tell me when you took it and what your body feels now. If breathing, chest pain, fainting, seizure, or blue lips are involved, open emergency immediately."
        } else {
            "Main isay $label samajh raha hoon. Kab li/ki, aur ab body mein kya feel ho raha hai? Saans, chest pain, fainting, fit, ya neelay hont ka masla ho to emergency foran kholo."
        }
    }

    
    fun buildLocalAIReply(userText: String, isEnglish: Boolean): String {
        val lower = userText.lowercase()
        return when {
            lower.containsAny("relapse", "cravings", "nasha", "overdose", "saans", "behosh") ->
                if (isEnglish)
                    "I'm here. Cravings can peak and pass. If breathing, fainting, or overdose is involved, please use the Emergency button or call 1122/115 right now."
                else
                    "Main yahin hoon. Cravings aati hain par guzar bhi jati hain. Agar saans, fainting, ya overdose ka masla hai to abhi Emergency button dabayein ya 1122/115 call karein."

            lower.containsAny("anxious", "panic", "ghabrahat", "nervous") ->
                if (isEnglish)
                    "Anxiety can feel overwhelming. Let's do a slow breath: in 4 seconds, out 6 seconds, five times. The Meditation tab has guided exercises too."
                else
                    "Ghabrahat bohot ho sakti hai. Aayen ek saans ki mashq karte hain: 4 seconds andar, 6 seconds bahar, 5 dafa. Meditation tab mein guided mashqein bhi hain."

            lower.containsAny("sad", "depressed", "udas", "cry", "hopeless") ->
                if (isEnglish)
                    "I'm sorry you're feeling this way. Writing one line in your Journal can help unload some of it — would you like to try?"
                else
                    "Mujhe afsos hai aap udas hain. Journal mein ek line likhna boj kam kar sakta hai — try karein?"

            lower.containsAny("help", "emergency", "madad", "sos") ->
                if (isEnglish)
                    "I'm here. If this is urgent, please tap the Emergency button or call 1122/115."
                else
                    "Main yahan hoon. Agar urgency hai, Emergency button dabayein ya 1122/115 call karein."

            else -> {
                val en = listOf(
                    "I'm listening. Tell me more about what's going on today.",
                    "That sounds heavy. What's the hardest part right now?",
                    "I'm here with you. What would help you feel even a little safer?",
                    "Taking it one moment at a time is okay. What just happened?",
                )
                val ur = listOf(
                    "Main sun raha hoon. Aaj kya ho raha hai, aur batayein.",
                    "Ye bhari lag raha hai. Abhi sab se mushkil kya hai?",
                    "Main aapke saath hoon. Thori si bhi safety kis cheez se aayegi?",
                    "Ek lamhe par ek lamha — theek hai. Abhi kya hua?",
                )
                if (isEnglish) en.random() else ur.random()
            }
        }
    }

    private fun String.containsAny(vararg keywords: String) = keywords.any { this.contains(it) }
}
