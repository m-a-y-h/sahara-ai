package pk.edu.ucp.saharaai.data.repository

import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import pk.edu.ucp.saharaai.BuildConfig
import pk.edu.ucp.saharaai.data.model.FirestoreMessage
import pk.edu.ucp.saharaai.data.model.SaharaChatTurnMetadata
import pk.edu.ucp.saharaai.data.model.SaharaMessageType
import pk.edu.ucp.saharaai.data.model.SaharaRiskLevel
import pk.edu.ucp.saharaai.data.remote.FirestoreService
import pk.edu.ucp.saharaai.data.remote.SaharaAiClient
import java.time.LocalDate
import java.time.ZoneId


object ChatRepository {

    
    fun aiSessionId(userId: String) = "ai_$userId"

    
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
        messageType: String = "TEXT"
    ): Result<String> {
        val msg = FirestoreMessage(
            sessionId   = sessionId,
            senderId    = senderId,
            receiverId  = receiverId,
            content     = content,
            isFromAI    = isFromAI,
            messageType = messageType,
            timestamp   = Timestamp.now()
        )
        return FirestoreService.sendMessage(msg)
    }

    
    fun getMessagesFlow(sessionId: String): Flow<List<FirestoreMessage>> =
        FirestoreService.getMessagesFlow(sessionId)

    
    suspend fun getMessagesOnce(sessionId: String): Result<List<FirestoreMessage>> =
        FirestoreService.getMessagesOnce(sessionId)

    suspend fun hasUserAiMessageToday(
        userId: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (userId.isBlank()) return false
        val today = LocalDate.now(zoneId)
        return getMessagesOnce(aiSessionId(userId)).getOrDefault(emptyList()).any { message ->
            !message.isFromAI &&
                message.senderId == userId &&
                message.timestamp.toDate().toInstant().atZone(zoneId).toLocalDate() == today
        }
    }

    
    suspend fun markRead(messageId: String): Result<Unit> =
        FirestoreService.markMessageRead(messageId)

    
    
    

    
    data class SaharaReply(
        val text: String,
        val metadata: SaharaChatTurnMetadata,
        val viaLiveModel: Boolean,
    )

    
    suspend fun askSahara(
        userText: String,
        isEnglish: Boolean,
        messageId: String,
    ): SaharaReply {
        val endpoint = BuildConfig.SAHARA_AI_CHAT_URL
        if (endpoint.isBlank()) {
            return fallback(userText, isEnglish, messageId)
        }

        val language = if (isEnglish) "english" else "roman_urdu"
        val result = SaharaAiClient.postChat(endpoint, userText, language)

        return result.fold(
            onSuccess = { response ->
                val reply = response.reply?.trim().orEmpty().ifBlank {
                    return@fold fallback(userText, isEnglish, messageId)
                }
                SaharaReply(
                    text = reply,
                    metadata = SaharaChatTurnMetadata(
                        messageId          = messageId,
                        messageType        = SaharaMessageType.fromWire(
                            response.messageType, response.userIntent
                        ),
                        riskLevel          = SaharaRiskLevel.fromWire(response.riskLevel),
                        triggerCounselor   = response.triggerCounselor ?: false,
                        substanceDetected  = response.substanceDetected,
                        substancesDetected = response.substancesDetected.orEmpty(),
                        actionDestination  = response.actionDestination,
                        quickReplies       = response.quickReplies.orEmpty(),
                        safetyFlags        = response.safetyFlags.orEmpty(),
                        detectedSymptoms   = response.detectedSymptoms.orEmpty(),
                        userIntent         = response.userIntent,
                    ),
                    viaLiveModel = true,
                )
            },
            onFailure = { fallback(userText, isEnglish, messageId) },
        )
    }

    private fun fallback(userText: String, isEnglish: Boolean, messageId: String): SaharaReply {
        val reply = buildLocalAIReply(userText, isEnglish)
        return SaharaReply(
            text = reply,
            metadata = SaharaChatTurnMetadata(
                messageId = messageId,
                messageType = SaharaMessageType.TEXT,
                riskLevel = SaharaRiskLevel.UNKNOWN,
                safetyFlags = listOf("sahara_ai_unreachable"),
            ),
            viaLiveModel = false,
        )
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
