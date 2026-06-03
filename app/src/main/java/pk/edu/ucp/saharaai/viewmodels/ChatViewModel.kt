package pk.edu.ucp.saharaai.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.google.firebase.Timestamp
import java.util.UUID
import pk.edu.ucp.saharaai.data.model.FirestoreMessage
import pk.edu.ucp.saharaai.data.model.SaharaChatTurnMetadata
import pk.edu.ucp.saharaai.data.model.VoiceLevel
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import pk.edu.ucp.saharaai.data.repository.ChatRepository
import pk.edu.ucp.saharaai.data.repository.ReportRepository
import pk.edu.ucp.saharaai.data.repository.SaharaVoiceRepository

sealed class ChatUiState {
    object Idle    : ChatUiState()
    object Sending : ChatUiState()
    data class Error(val message: String) : ChatUiState()
}

class ChatViewModel : ViewModel() {

    private val auth = Firebase.auth
    val signedInUserId: String get() = auth.currentUser?.uid.orEmpty()

    private val TAG = "ChatViewModel"

    private val _messages   = MutableStateFlow<List<FirestoreMessage>>(emptyList())
    val messages: StateFlow<List<FirestoreMessage>> = _messages.asStateFlow()

    private val _isTyping   = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _uiState    = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    
    private val _aiMetadata = MutableStateFlow<Map<String, SaharaChatTurnMetadata>>(emptyMap())
    val aiMetadata: StateFlow<Map<String, SaharaChatTurnMetadata>> = _aiMetadata.asStateFlow()

    
    private val _saharaUnreachable = MutableStateFlow(false)
    val saharaUnreachable: StateFlow<Boolean> = _saharaUnreachable.asStateFlow()

    private val _identityVisible = MutableStateFlow(false)
    val identityVisible: StateFlow<Boolean> = _identityVisible.asStateFlow()

    private val _counselorProfile = MutableStateFlow<Map<String, Any>?>(null)
    val counselorProfile: StateFlow<Map<String, Any>?> = _counselorProfile.asStateFlow()

    private val _targetUserProfile = MutableStateFlow<Map<String, Any>?>(null)
    val targetUserProfile: StateFlow<Map<String, Any>?> = _targetUserProfile.asStateFlow()

    private val _sessionExpiresAt = MutableStateFlow(0L)
    val sessionExpiresAt: StateFlow<Long> = _sessionExpiresAt.asStateFlow()

    private val _chatBlocked = MutableStateFlow(false)
    val chatBlocked: StateFlow<Boolean> = _chatBlocked.asStateFlow()

    
    data class AiReplyEvent(
        val text: String,
        val metadata: SaharaChatTurnMetadata,
        val viaLiveModel: Boolean,
        val isPersisted: Boolean,
    )
    private val _aiReplyEvents = MutableSharedFlow<AiReplyEvent>(extraBufferCapacity = 8)
    val aiReplyEvents: SharedFlow<AiReplyEvent> = _aiReplyEvents.asSharedFlow()

    data class VoiceNoteEvent(
        val bubbleId: String,
        val analyzedBubbleText: String? = null,
        val replyText: String,
    )
    private val _voiceNoteEvents = MutableSharedFlow<VoiceNoteEvent>(extraBufferCapacity = 4)
    val voiceNoteEvents: SharedFlow<VoiceNoteEvent> = _voiceNoteEvents.asSharedFlow()

    private var currentSessionId: String = ""
    private var currentCounselorId: String = ""
    private var currentUserId: String = ""
    private var isRealtimeChat: Boolean = false
    private var messageListenJob: Job? = null
    private var identityListenJob: Job? = null
    private var sessionMetaJob: Job? = null
    private var profileLoadJob: Job? = null
    
    private var realtimePathUserId: String = ""
    
    private var isCounselorSide: Boolean = false
    
    private var counselorDisplayName: String = ""

    /** Last welcome text that was seeded into the AI session, so `startNewAiChat()`
     *  can re-seed an identical opener without the screen having to remember it. */
    private var lastAiWelcomeText: String = ""

    fun initSession(
        userId: String,
        isAiChat: Boolean,
        counselorId: String = "",
        targetUserId: String = "",
        counselorName: String = "",
        aiWelcomeText: String = "",
    ) {
        messageListenJob?.cancel()
        identityListenJob?.cancel()
        sessionMetaJob?.cancel()
        profileLoadJob?.cancel()
        _messages.value = emptyList()
        _identityVisible.value = false
        _counselorProfile.value = null
        _targetUserProfile.value = null
        _sessionExpiresAt.value = 0L
        _chatBlocked.value = false
        counselorDisplayName = counselorName.ifBlank { "Your Counselor" }
        currentCounselorId = counselorId
        currentUserId = userId
        isCounselorSide = targetUserId.isNotBlank()
        
        realtimePathUserId = if (targetUserId.isNotBlank()) targetUserId else userId

        if (isAiChat) {
            isRealtimeChat = false
            currentSessionId = ChatRepository.aiSessionId(userId)
            lastAiWelcomeText = aiWelcomeText
            // Seed the welcome bubble as the first persisted message if the
            // session is empty. Doing it here (not in Compose) means the
            // welcome survives screen recreation, app restart, and the
            // Firestore snapshot can't blow it away on the first real send.
            if (aiWelcomeText.isNotBlank()) {
                viewModelScope.launch {
                    val existing = ChatRepository.getMessagesOnce(currentSessionId)
                        .getOrDefault(emptyList())
                    if (existing.isEmpty()) {
                        ChatRepository.sendMessage(
                            sessionId   = currentSessionId,
                            senderId    = "ai",
                            receiverId  = userId,
                            content     = aiWelcomeText,
                            isFromAI    = true,
                        )
                    }
                }
            }
            messageListenJob = viewModelScope.launch {
                ChatRepository.getMessagesFlow(currentSessionId)
                    .catch { e -> Log.w(TAG, "AI messages flow error", e) }
                    .collect { msgs ->
                        _messages.value = msgs
                    }
            }
        } else if (counselorId.isNotBlank()) {
            isRealtimeChat = true
            currentSessionId = RealtimeDBService.chatSessionPath(realtimePathUserId, counselorId)

            identityListenJob = viewModelScope.launch {
                RealtimeDBService.listenChatIdentityVisible(realtimePathUserId, counselorId)
                    .catch { e -> Log.w(TAG, "identity flow error", e) }
                    .collect {
                        _identityVisible.value = it
                    }
            }

            sessionMetaJob = viewModelScope.launch {
                RealtimeDBService.listenChatSessionMeta(realtimePathUserId, counselorId)
                    .catch { e -> Log.w(TAG, "session meta flow error", e) }
                    .collect { meta ->
                        _sessionExpiresAt.value = (meta["expiresAt"] as? Long) ?: 0L
                        _chatBlocked.value = meta.containsKey("blocked")
                    }
            }

            profileLoadJob = viewModelScope.launch {
                RealtimeDBService.getCounselorProfileByKey(counselorId).onSuccess {
                    _counselorProfile.value = it
                }
                RealtimeDBService.getUserProfile(realtimePathUserId).onSuccess {
                    _targetUserProfile.value = it
                }
            }
            
            messageListenJob = viewModelScope.launch {
                RealtimeDBService.listenToChatMessages(realtimePathUserId, counselorId)
                    .catch { e -> Log.w(TAG, "counselor messages flow error", e) }
                    .collect { rawMsgs ->
                    _messages.value = rawMsgs.map { map ->
                        val text     = map["text"] as? String ?: ""
                        val senderId = map["senderId"] as? String ?: ""
                        val msgId    = map["messageId"] as? String ?: ""
                        val tsMillis = map["timestamp"] as? Long ?: 0L

                        
                        
                        
                        
                        
                        val senderType = when (map["senderType"] as? String) {
                            "user"      -> "user"
                            "counselor" -> "counselor"
                            else        -> if (senderId == realtimePathUserId) "user" else "counselor"
                        }

                        FirestoreMessage(
                            messageId  = msgId,
                            sessionId  = currentSessionId,
                            senderId   = senderId,
                            receiverId = if (senderType == "user") counselorId else realtimePathUserId,
                            content    = text,
                            isFromAI   = false,
                            senderType = senderType,
                            timestamp  = Timestamp(tsMillis / 1000, ((tsMillis % 1000) * 1_000_000).toInt())
                        )
                    }
                }
            }
        }
    }

    
    fun sendUserMessageToAI(
        text: String,
        isEnglish: Boolean,
        onUserMessageSaved: (() -> Unit)? = null,
    ) {
        if (text.isBlank()) return
        val uid = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            _uiState.value = ChatUiState.Sending

            
            val sendResult = ChatRepository.sendMessage(
                sessionId   = currentSessionId,
                senderId    = uid,
                receiverId  = "ai",
                content     = text,
                isFromAI    = false
            )

            if (sendResult.isFailure) {
                _uiState.value = ChatUiState.Error("Failed to send message.")
                return@launch
            }
            onUserMessageSaved?.invoke()

            
            _isTyping.value = true

            
            
            
            val aiMessageId = UUID.randomUUID().toString()
            val sahara = ChatRepository.askSahara(text, isEnglish, aiMessageId)
            _isTyping.value = false
            _saharaUnreachable.value = !sahara.viaLiveModel

            
            
            val storedMessageType = sahara.metadata.messageType.name
            val savedResult = ChatRepository.sendMessage(
                sessionId   = currentSessionId,
                senderId    = "ai",
                receiverId  = uid,
                content     = sahara.text,
                isFromAI    = true,
                messageType = storedMessageType,
            )

            
            
            
            
            val storedId = savedResult.getOrNull() ?: aiMessageId
            val metadataForStorage = sahara.metadata.copy(messageId = storedId)
            _aiMetadata.value = _aiMetadata.value + (storedId to metadataForStorage)

            
            
            
            _aiReplyEvents.tryEmit(
                AiReplyEvent(
                    text = sahara.text,
                    metadata = metadataForStorage,
                    viaLiveModel = sahara.viaLiveModel,
                    isPersisted = savedResult.isSuccess,
                )
            )

            _uiState.value = ChatUiState.Idle
        }
    }

    
    fun sendMessageToCounselor(text: String, counselorId: String) {
        if (text.isBlank()) return
        val uid = auth.currentUser?.uid ?: return
        if (_chatBlocked.value) {
            _uiState.value = ChatUiState.Error("This chat is blocked.")
            return
        }

        viewModelScope.launch {
            _uiState.value = ChatUiState.Sending
            
            val result = if (isCounselorSide) {
                
                RealtimeDBService.sendChatMessageFromCounselor(
                    userUid      = realtimePathUserId,
                    counselorKey = counselorId,
                    counselorUid = uid,
                    text         = text
                )
            } else {
                
                RealtimeDBService.sendChatMessage(
                    uid          = uid,
                    counselorKey = counselorId,
                    text         = text,
                    senderType   = "user"
                )
            }
            if (result.isSuccess) {
                _uiState.value = ChatUiState.Idle
                
                if (isCounselorSide && realtimePathUserId.isNotBlank()) {
                    
                    val encodedName = counselorDisplayName.replace(" ", "_")
                    RealtimeDBService.saveUserNotification(
                        uid         = realtimePathUserId,
                        titleEn     = "Message from $counselorDisplayName",
                        titleUr     = "$counselorDisplayName ka Paigham",
                        bodyEn      = "Your counselor has sent you a new message.",
                        bodyUr      = "Aapke counselor ne aapko naya paigham bheja hai.",
                        type        = "COUNSELOR",
                        actionRoute = "counselor-chat/$currentCounselorId/$encodedName"
                    )
                }
            } else {
                _uiState.value = ChatUiState.Error(result.exceptionOrNull()?.message ?: "Send failed.")
            }
        }
    }

    
    fun markRead(messageId: String) {
        viewModelScope.launch {
            ChatRepository.markRead(messageId)
        }
    }

    fun clearError() { _uiState.value = ChatUiState.Idle }

    fun setIdentityVisible(visible: Boolean) {
        if (!isRealtimeChat || realtimePathUserId.isBlank() || currentCounselorId.isBlank()) return
        viewModelScope.launch {
            RealtimeDBService.setChatIdentityVisible(realtimePathUserId, currentCounselorId, visible)
                .onFailure { _uiState.value = ChatUiState.Error(it.message ?: "Could not update privacy.") }
        }
    }

    fun reportCurrentChat(reason: String, description: String) {
        if (!isRealtimeChat || currentUserId.isBlank()) return
        val targetId = if (isCounselorSide) realtimePathUserId else currentCounselorId
        if (targetId.isBlank()) return
        viewModelScope.launch {
            ReportRepository.submitReport(
                reportedBy = currentUserId,
                targetId = targetId,
                targetType = if (isCounselorSide) "USER_CHAT" else "COUNSELOR_CHAT",
                reason = reason,
                description = description,
            ).onFailure {
                _uiState.value = ChatUiState.Error(it.message ?: "Could not submit report.")
            }
        }
    }

    fun blockCurrentChat() {
        if (!isRealtimeChat || currentUserId.isBlank() || realtimePathUserId.isBlank() || currentCounselorId.isBlank()) return
        viewModelScope.launch {
            RealtimeDBService.blockChatSession(
                blockedBy = currentUserId,
                userUid = realtimePathUserId,
                counselorKey = currentCounselorId,
            ).onFailure {
                _uiState.value = ChatUiState.Error(it.message ?: "Could not block this chat.")
            }
        }
    }

    fun extendCurrentSession(hours: Long = 24L) {
        if (!isRealtimeChat || !isCounselorSide || realtimePathUserId.isBlank() || currentCounselorId.isBlank()) return
        viewModelScope.launch {
            RealtimeDBService.extendChatSession(realtimePathUserId, currentCounselorId, hours)
                .onSuccess { _sessionExpiresAt.value = it }
                .onFailure { _uiState.value = ChatUiState.Error(it.message ?: "Could not extend this session.") }
        }
    }

    
    fun sendQuickReply(text: String, isEnglish: Boolean) {
        if (text.isBlank()) return
        if (isRealtimeChat) {
            
            sendMessageToCounselor(text, currentCounselorId)
        } else {
            sendUserMessageToAI(text, isEnglish)
        }
    }

    /** Wipes the current AI session's messages from Firestore and re-seeds the
     *  welcome bubble. Used by the chat top-bar "New chat" action. */
    fun startNewAiChat() {
        if (isRealtimeChat || currentSessionId.isBlank()) return
        val uid = currentUserId
        if (uid.isBlank()) return
        val welcome = lastAiWelcomeText
        viewModelScope.launch {
            _uiState.value = ChatUiState.Sending
            ChatRepository.clearSession(currentSessionId)
                .onSuccess {
                    _aiMetadata.value = emptyMap()
                    _saharaUnreachable.value = false
                    if (welcome.isNotBlank()) {
                        ChatRepository.sendMessage(
                            sessionId  = currentSessionId,
                            senderId   = "ai",
                            receiverId = uid,
                            content    = welcome,
                            isFromAI   = true,
                        )
                    }
                    _uiState.value = ChatUiState.Idle
                }
                .onFailure {
                    _uiState.value = ChatUiState.Error(it.message ?: "Could not reset chat.")
                }
        }
    }

    /** Records a user voice note as a real Firestore message (so it survives
     *  navigation + app restart), runs the analyzer, then either rewrites the
     *  user bubble with the tone label and writes an AI reply, or writes a
     *  failure reply if the analyzer is unreachable.
     *
     *  Returns immediately; updates flow through the existing messages
     *  listener.
     *
     *  Replaces the old `analyzeVoiceNote(bubbleId, ...)` flow that kept voice
     *  notes in Compose `transientMessages` only and lost them on navigation.
     */
    fun sendVoiceNoteToAI(
        secondsRecorded: Int,
        audioBytes: ByteArray,
        mimeType: String,
        isEnglish: Boolean,
    ) {
        val uid = auth.currentUser?.uid ?: return
        if (currentSessionId.isBlank()) return
        viewModelScope.launch {
            _isTyping.value = true
            val placeholderText = if (isEnglish)
                "Voice note ($secondsRecorded sec) - analyzing..."
            else
                "Voice note ($secondsRecorded sec) - sun raha hoon..."
            val userMsgIdResult = ChatRepository.sendMessage(
                sessionId   = currentSessionId,
                senderId    = uid,
                receiverId  = "ai",
                content     = placeholderText,
                isFromAI    = false,
                messageType = "VOICE_NOTE",
            )
            val userMsgId = userMsgIdResult.getOrNull()

            SaharaVoiceRepository.analyze(audioBytes, mimeType).fold(
                onSuccess = { response ->
                    val level = VoiceLevel.fromWire(response.screening?.level)
                    val topClass = response.screening?.topScreeningClass ?: "neutral"
                    val labelSuffix = when (level) {
                        VoiceLevel.HIGH -> if (isEnglish) "tone: $topClass (high distress)" else "tone: $topClass (zyada distress)"
                        VoiceLevel.ELEVATED -> if (isEnglish) "tone: $topClass (elevated)" else "tone: $topClass (barhi hui)"
                        VoiceLevel.NEUTRAL -> if (isEnglish) "tone: steady" else "tone: theek"
                        VoiceLevel.UNCERTAIN -> if (isEnglish) "tone: couldn't read" else "tone: saaf nahi pata laga"
                        VoiceLevel.UNKNOWN -> if (isEnglish) "tone: unknown" else "tone: pata nahi"
                    }
                    val reply = when (level) {
                        VoiceLevel.HIGH -> if (isEnglish)
                            "Your voice sounds heavy right now. Sahara counselors are available, or call 1122/115 if it's urgent. Want me to open the counselor list?"
                        else
                            "Awaz mein boj sun raha hoon. Sahara counselor available hain, ya urgent ho to 1122/115 call karein. Counselor list kholoon?"
                        VoiceLevel.ELEVATED -> if (isEnglish)
                            "I can hear some tension. Want to try a 60-second breathing exercise, or tell me what's on your mind?"
                        else
                            "Thori tension sun raha hoon. 60-second saans ki mashq try karein ya batayein kya chal raha hai?"
                        VoiceLevel.NEUTRAL -> if (isEnglish)
                            "Your voice sounds steady. Tell me more about today - anything I should know?"
                        else
                            "Awaz steady lag rahi hai. Aaj ke baray mein batayein - kuch important?"
                        VoiceLevel.UNCERTAIN, VoiceLevel.UNKNOWN -> if (isEnglish)
                            "I couldn't read the clip cleanly. Try a quieter spot, or just type what's going on."
                        else
                            "Clip saaf nahi samjhi. Khamosh jagah mein dobara try karein, ya type kar dein."
                    }
                    val finalUserBubble = "Voice note ($secondsRecorded sec) - $labelSuffix"
                    if (userMsgId != null) {
                        ChatRepository.updateMessageContent(userMsgId, finalUserBubble)
                    }
                    ChatRepository.sendMessage(
                        sessionId   = currentSessionId,
                        senderId    = "ai",
                        receiverId  = uid,
                        content     = reply,
                        isFromAI    = true,
                    )
                },
                onFailure = {
                    val failureUserBubble = "Voice note ($secondsRecorded sec)"
                    if (userMsgId != null) {
                        ChatRepository.updateMessageContent(userMsgId, failureUserBubble)
                    }
                    val failureReply = if (isEnglish)
                        "Couldn't reach the voice analyser right now. Please type how you're feeling."
                    else
                        "Voice analyser tak abhi raabta nahi ho saka. Type kar ke batayein."
                    ChatRepository.sendMessage(
                        sessionId   = currentSessionId,
                        senderId    = "ai",
                        receiverId  = uid,
                        content     = failureReply,
                        isFromAI    = true,
                    )
                },
            )
            _isTyping.value = false
        }
    }

    @Deprecated("Use sendVoiceNoteToAI which persists voice notes to Firestore.")
    fun analyzeVoiceNote(
        bubbleId: String,
        secondsRecorded: Int,
        audioBytes: ByteArray,
        mimeType: String,
        isEnglish: Boolean,
    ) {
        viewModelScope.launch {
            SaharaVoiceRepository.analyze(audioBytes, mimeType).fold(
                onSuccess = { response ->
                    val level = VoiceLevel.fromWire(response.screening?.level)
                    val topClass = response.screening?.topScreeningClass ?: "neutral"
                    val labelSuffix = when (level) {
                        VoiceLevel.HIGH -> if (isEnglish) "tone: $topClass (high distress)" else "tone: $topClass (zyada distress)"
                        VoiceLevel.ELEVATED -> if (isEnglish) "tone: $topClass (elevated)" else "tone: $topClass (barhi hui)"
                        VoiceLevel.NEUTRAL -> if (isEnglish) "tone: steady" else "tone: theek"
                        VoiceLevel.UNCERTAIN -> if (isEnglish) "tone: couldn't read" else "tone: saaf nahi pata laga"
                        VoiceLevel.UNKNOWN -> if (isEnglish) "tone: unknown" else "tone: pata nahi"
                    }
                    val reply = when (level) {
                        VoiceLevel.HIGH -> if (isEnglish)
                            "Your voice sounds heavy right now. Sahara counselors are available, or call 1122/115 if it's urgent. Want me to open the counselor list?"
                        else
                            "Awaz mein boj sun raha hoon. Sahara counselor available hain, ya urgent ho to 1122/115 call karein. Counselor list kholoon?"
                        VoiceLevel.ELEVATED -> if (isEnglish)
                            "I can hear some tension. Want to try a 60-second breathing exercise, or tell me what's on your mind?"
                        else
                            "Thori tension sun raha hoon. 60-second saans ki mashq try karein ya batayein kya chal raha hai?"
                        VoiceLevel.NEUTRAL -> if (isEnglish)
                            "Your voice sounds steady. Tell me more about today - anything I should know?"
                        else
                            "Awaz steady lag rahi hai. Aaj ke baray mein batayein - kuch important?"
                        VoiceLevel.UNCERTAIN, VoiceLevel.UNKNOWN -> if (isEnglish)
                            "I couldn't read the clip cleanly. Try a quieter spot, or just type what's going on."
                        else
                            "Clip saaf nahi samjhi. Khamosh jagah mein dobara try karein, ya type kar dein."
                    }
                    _voiceNoteEvents.emit(
                        VoiceNoteEvent(
                            bubbleId = bubbleId,
                            analyzedBubbleText = "Voice note ($secondsRecorded sec) - $labelSuffix",
                            replyText = reply,
                        )
                    )
                },
                onFailure = {
                    _voiceNoteEvents.emit(
                        VoiceNoteEvent(
                            bubbleId = bubbleId,
                            replyText = if (isEnglish)
                                "Couldn't reach the voice analyser right now. Please type how you're feeling."
                            else
                                "Voice analyser tak abhi raabta nahi ho saka. Type kar ke batayein."
                        )
                    )
                },
            )
        }
    }

    
    fun metadataFor(messageId: String): SaharaChatTurnMetadata? = _aiMetadata.value[messageId]
}
