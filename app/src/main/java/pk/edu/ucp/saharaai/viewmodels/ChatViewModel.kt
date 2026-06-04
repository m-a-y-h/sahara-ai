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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import pk.edu.ucp.saharaai.data.model.AiChatSession
import pk.edu.ucp.saharaai.data.model.FirestoreMessage
import pk.edu.ucp.saharaai.data.model.SaharaChatTurnMetadata
import pk.edu.ucp.saharaai.data.model.SaharaMessageType
import pk.edu.ucp.saharaai.data.model.SaharaRiskLevel
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

    private val _aiSessions = MutableStateFlow<List<AiChatSession>>(emptyList())
    val aiSessions: StateFlow<List<AiChatSession>> = _aiSessions.asStateFlow()

    private val _currentAiSessionId = MutableStateFlow("")
    val currentAiSessionId: StateFlow<String> = _currentAiSessionId.asStateFlow()

    
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
    private var aiSessionListenJob: Job? = null
    private var identityListenJob: Job? = null
    private var sessionMetaJob: Job? = null
    private var profileLoadJob: Job? = null
    
    private var realtimePathUserId: String = ""
    
    private var isCounselorSide: Boolean = false
    
    private var counselorDisplayName: String = ""

    /** Last welcome text that was seeded into the AI session, so `startNewAiChat()`
     *  can re-seed an identical opener without the screen having to remember it. */
    private var lastAiWelcomeText: String = ""
    private val pendingSessionTitleRenames = mutableSetOf<String>()

    fun initSession(
        userId: String,
        isAiChat: Boolean,
        counselorId: String = "",
        targetUserId: String = "",
        counselorName: String = "",
        aiWelcomeText: String = "",
    ) {
        val nextRealtimePathUserId = if (targetUserId.isNotBlank()) targetUserId else userId
        val wasSameAiUser = isAiChat &&
            !isRealtimeChat &&
            currentUserId == userId &&
            currentSessionId.isNotBlank()

        messageListenJob?.cancel()
        identityListenJob?.cancel()
        sessionMetaJob?.cancel()
        profileLoadJob?.cancel()
        _identityVisible.value = false
        _counselorProfile.value = null
        _targetUserProfile.value = null
        _sessionExpiresAt.value = 0L
        _chatBlocked.value = false
        counselorDisplayName = counselorName.ifBlank { "Your Counselor" }
        currentCounselorId = counselorId
        currentUserId = userId
        isCounselorSide = targetUserId.isNotBlank()
        
        realtimePathUserId = nextRealtimePathUserId

        if (isAiChat) {
            aiSessionListenJob?.cancel()
            isRealtimeChat = false
            lastAiWelcomeText = aiWelcomeText

            if (!wasSameAiUser) {
                currentSessionId = ""
                _currentAiSessionId.value = ""
                _messages.value = emptyList()
                _aiMetadata.value = emptyMap()
            }

            aiSessionListenJob = viewModelScope.launch {
                ChatRepository.getAiChatSessionsFlow(userId)
                    .catch { e -> Log.w(TAG, "AI sessions flow error", e) }
                    .collect { sessions ->
                        _aiSessions.value = sessions
                        finalizeServerNamedSessions(sessions)
                        if (currentSessionId.isBlank() && sessions.isNotEmpty()) {
                            selectAiSession(
                                sessionId = sessions.first().sessionId,
                                seedWelcomeIfEmpty = false,
                            )
                        }
                    }
            }

            if (wasSameAiUser) {
                selectAiSession(currentSessionId, seedWelcomeIfEmpty = false)
            } else {
                viewModelScope.launch {
                    val latest = ChatRepository.getLatestAiChatSession(userId).getOrNull()
                    if (latest != null) {
                        selectAiSession(latest.sessionId, seedWelcomeIfEmpty = false)
                    } else {
                        val legacySessionId = ChatRepository.legacyAiSessionId(userId)
                        val legacyMessages = ChatRepository.getMessagesOnce(legacySessionId)
                            .getOrDefault(emptyList())
                        if (legacyMessages.isNotEmpty()) {
                            val firstMessageMillis = legacyMessages.first().timestamp.let {
                                it.seconds * 1000L + it.nanoseconds / 1_000_000L
                            }
                            ChatRepository.createAiChatSession(
                                userId = userId,
                                clientCreatedAtMillis = firstMessageMillis,
                                sessionId = legacySessionId,
                            )
                            selectAiSession(legacySessionId, seedWelcomeIfEmpty = false)
                        } else {
                            createAndSelectAiSession(seedWelcome = true)
                        }
                    }
                }
            }
        } else if (counselorId.isNotBlank()) {
            aiSessionListenJob?.cancel()
            _aiSessions.value = emptyList()
            _currentAiSessionId.value = ""
            isRealtimeChat = true
            val nextSessionId = RealtimeDBService.chatSessionPath(nextRealtimePathUserId, counselorId)
            val isSameSession = nextSessionId == currentSessionId
            if (!isSameSession) {
                _messages.value = emptyList()
                _aiMetadata.value = emptyMap()
            }
            currentSessionId = nextSessionId

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

    private fun createAndSelectAiSession(seedWelcome: Boolean): Boolean {
        val uid = currentUserId.ifBlank { auth.currentUser?.uid.orEmpty() }
        if (uid.isBlank()) return false
        return ChatRepository.createAiChatSession(uid).fold(
            onSuccess = { session ->
                _aiSessions.value = listOf(session) + _aiSessions.value.filterNot { it.sessionId == session.sessionId }
                selectAiSession(session.sessionId, seedWelcomeIfEmpty = seedWelcome)
                true
            },
            onFailure = {
                _uiState.value = ChatUiState.Error(it.message ?: "Could not create chat.")
                false
            },
        )
    }

    fun openAiChatSession(sessionId: String) {
        if (sessionId.isBlank() || isRealtimeChat) return
        selectAiSession(sessionId, seedWelcomeIfEmpty = false)
    }

    private fun selectAiSession(sessionId: String, seedWelcomeIfEmpty: Boolean) {
        if (sessionId.isBlank()) return
        val changed = sessionId != currentSessionId
        currentSessionId = sessionId
        _currentAiSessionId.value = sessionId
        if (changed) {
            _messages.value = emptyList()
            _aiMetadata.value = emptyMap()
        }

        messageListenJob?.cancel()
        if (seedWelcomeIfEmpty) {
            seedAiWelcomeIfEmpty(sessionId)
        }
        messageListenJob = viewModelScope.launch {
            ChatRepository.getMessagesFlow(sessionId)
                .catch { e -> Log.w(TAG, "AI messages flow error", e) }
                .collect { msgs ->
                    _messages.value = msgs
                    _aiMetadata.value = msgs.mapNotNull { it.toSaharaMetadataOrNull() }
                        .associateBy { it.messageId }
                }
        }
    }

    private fun seedAiWelcomeIfEmpty(sessionId: String) {
        val welcome = lastAiWelcomeText
        val uid = currentUserId
        if (welcome.isBlank() || uid.isBlank()) return
        viewModelScope.launch {
            val existing = ChatRepository.getMessagesOnce(sessionId).getOrDefault(emptyList())
            if (existing.isEmpty()) {
                ChatRepository.sendAiMessage(
                    sessionId = sessionId,
                    content = welcome,
                    userId = uid,
                ).onSuccess {
                    ChatRepository.updateAiChatSessionAfterMessage(sessionId, welcome)
                }
            }
        }
    }

    private fun finalizeServerNamedSessions(sessions: List<AiChatSession>) {
        sessions
            .filter { session ->
                session.createdAt != null &&
                    session.titleStatus != AiChatSession.TITLE_READY &&
                    session.title == "Untitled" &&
                    pendingSessionTitleRenames.add(session.sessionId)
            }
            .forEach { session ->
                val titleSource = session.clientCreatedAt.takeIf { session.clientCreatedAtMillis > 0 }
                    ?: session.createdAt
                    ?: Timestamp.now()
                val title = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
                    .format(Date(titleSource.seconds * 1000L + titleSource.nanoseconds / 1_000_000L))
                viewModelScope.launch {
                    ChatRepository.renameAiChatSessionFromServerTime(session.sessionId, title)
                        .onFailure {
                            pendingSessionTitleRenames.remove(session.sessionId)
                            Log.w(TAG, "Could not finalize AI session title", it)
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
            if (currentSessionId.isBlank()) {
                createAndSelectAiSession(seedWelcome = true)
            }
            val sessionId = currentSessionId
            if (sessionId.isBlank()) {
                _uiState.value = ChatUiState.Error("Could not open chat.")
                return@launch
            }

            
            val sendResult = ChatRepository.sendMessage(
                sessionId   = sessionId,
                senderId    = uid,
                receiverId  = "ai",
                content     = text,
                isFromAI    = false
            )

            if (sendResult.isFailure) {
                _uiState.value = ChatUiState.Error("Failed to send message.")
                return@launch
            }
            ChatRepository.updateAiChatSessionAfterMessage(sessionId, text)
            onUserMessageSaved?.invoke()

            
            _isTyping.value = true

            
            
            
            val aiMessageId = UUID.randomUUID().toString()
            val sahara = ChatRepository.askSahara(text, isEnglish, aiMessageId)
            _isTyping.value = false
            _saharaUnreachable.value = !sahara.viaLiveModel

            
            
            val storedMessageType = sahara.metadata.messageType.name
            val savedResult = ChatRepository.sendAiMessage(
                sessionId  = sessionId,
                userId     = uid,
                content    = sahara.text,
                messageType = storedMessageType,
                metadata   = sahara.metadata,
            )
            if (savedResult.isSuccess) {
                ChatRepository.updateAiChatSessionAfterMessage(sessionId, sahara.text)
            }

            
            
            
            
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

    /** Starts a separate AI session. Older sessions stay in history until the
     *  user deletes them from the history sheet. */
    fun startNewAiChat() {
        if (isRealtimeChat) return
        val uid = currentUserId
        if (uid.isBlank()) return
        _uiState.value = ChatUiState.Sending
        val created = createAndSelectAiSession(seedWelcome = true)
        _saharaUnreachable.value = false
        if (created) _uiState.value = ChatUiState.Idle
    }

    fun deleteAiChatSession(sessionId: String) {
        if (sessionId.isBlank() || isRealtimeChat) return
        viewModelScope.launch {
            ChatRepository.deleteAiChatSession(sessionId)
                .onSuccess {
                    pendingSessionTitleRenames.remove(sessionId)
                    if (currentSessionId == sessionId) {
                        val replacement = _aiSessions.value.firstOrNull { it.sessionId != sessionId }
                        if (replacement != null) {
                            selectAiSession(replacement.sessionId, seedWelcomeIfEmpty = false)
                        } else {
                            currentSessionId = ""
                            _currentAiSessionId.value = ""
                            _messages.value = emptyList()
                            _aiMetadata.value = emptyMap()
                            createAndSelectAiSession(seedWelcome = true)
                        }
                    }
                }
                .onFailure {
                    _uiState.value = ChatUiState.Error(it.message ?: "Could not delete chat.")
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
            val sessionId = currentSessionId
            val placeholderText = if (isEnglish)
                "Voice note ($secondsRecorded sec) - analyzing..."
            else
                "Voice note ($secondsRecorded sec) - sun raha hoon..."
            val userMsgIdResult = ChatRepository.sendMessage(
                sessionId   = sessionId,
                senderId    = uid,
                receiverId  = "ai",
                content     = placeholderText,
                isFromAI    = false,
                messageType = "VOICE_NOTE",
            )
            val userMsgId = userMsgIdResult.getOrNull()
            if (userMsgId != null) {
                ChatRepository.updateAiChatSessionAfterMessage(sessionId, placeholderText)
            }

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
                    ChatRepository.sendAiMessage(
                        sessionId   = sessionId,
                        content     = reply,
                        userId      = uid,
                    ).onSuccess {
                        ChatRepository.updateAiChatSessionAfterMessage(sessionId, reply)
                    }
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
                    ChatRepository.sendAiMessage(
                        sessionId   = sessionId,
                        content     = failureReply,
                        userId      = uid,
                    ).onSuccess {
                        ChatRepository.updateAiChatSessionAfterMessage(sessionId, failureReply)
                    }
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

    private fun FirestoreMessage.toSaharaMetadataOrNull(): SaharaChatTurnMetadata? {
        if (!isFromAI && senderType != "ai" && senderId != "ai") return null
        val hasMetadata =
            messageType.isNotBlank() ||
                riskLevel.isNotBlank() ||
                actionDestination.isNotBlank() ||
                quickReplies.isNotEmpty() ||
                userIntent.isNotBlank()
        if (!hasMetadata) return null
        return SaharaChatTurnMetadata(
            messageId = messageId,
            messageType = SaharaMessageType.fromWire(messageType, userIntent.ifBlank { null }),
            riskLevel = SaharaRiskLevel.fromWire(riskLevel),
            triggerCounselor = triggerCounselor,
            substanceDetected = substanceDetected.ifBlank { null },
            substancesDetected = substancesDetected,
            actionDestination = actionDestination.ifBlank { null },
            quickReplies = quickReplies,
            safetyFlags = safetyFlags,
            detectedSymptoms = detectedSymptoms,
            userIntent = userIntent.ifBlank { null },
        )
    }
}
