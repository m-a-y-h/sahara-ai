package pk.edu.ucp.saharaai.ui.screens
import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.ui.components.GlassAlertDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.ui.components.BottomNav
import pk.edu.ucp.saharaai.viewmodels.ChatUiState
import pk.edu.ucp.saharaai.viewmodels.ChatViewModel
import pk.edu.ucp.saharaai.ui.components.ButtonVariant
import pk.edu.ucp.saharaai.ui.components.CardVariant
import pk.edu.ucp.saharaai.ui.components.SaharaButton
import pk.edu.ucp.saharaai.ui.components.SaharaCard
import pk.edu.ucp.saharaai.ui.components.PrescriptionRedirectCard
import pk.edu.ucp.saharaai.ui.components.SaharaUnreachableBanner
import pk.edu.ucp.saharaai.ui.components.HazeBackButton
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.data.model.SaharaMessageType
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import pk.edu.ucp.saharaai.util.PermissionCopy
import pk.edu.ucp.saharaai.util.VoiceRecorder
import pk.edu.ucp.saharaai.util.rememberAppPermissionRequester
import pk.edu.ucp.saharaai.util.showLocalizedToast
import java.text.SimpleDateFormat
import java.util.*
import android.os.VibratorManager
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs

enum class MessageType { TEXT, QUICK_REPLIES, CRISIS_CARD, EXERCISE_CARD, VOICE_NOTE }
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isBot: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val type: MessageType = MessageType.TEXT,
    val options: List<String>? = null,
    val actionDestination: String? = null
) {
    fun getFormattedTime(): String {
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))
    }
}
object MockAIEngine {
    fun processInput(input: String, isEnglish: Boolean): ChatMessage {
        val lowerInput = input.lowercase()
        return when {
            lowerInput.contains("relapse") || lowerInput.contains("cravings") || lowerInput.contains("nasha") -> {
                ChatMessage(
                    text = if (isEnglish) "I hear you. Cravings are intense, but they are temporary. Please use these immediate resources." else "Main samajh raha hoon. Cravings aati hain, par ye waqti hain. Fauri tor par in resources ka istemal karein.",
                    isBot = true,
                    type = MessageType.CRISIS_CARD,
                    actionDestination = "emergency"
                )
            }
            lowerInput.contains("anxious") || lowerInput.contains("panic") || lowerInput.contains("ghabrahat") -> {
                ChatMessage(
                    text = if (isEnglish) "Anxiety can feel overwhelming. Let's ground ourselves. Try this quick breathing exercise." else "Ghabrahat bohot hawi ho sakti hai. Aayen mil kar saans ki mashq karte hain.",
                    isBot = true,
                    type = MessageType.EXERCISE_CARD,
                    actionDestination = "meditation"
                )
            }
            lowerInput.contains("sad") || lowerInput.contains("depressed") || lowerInput.contains("udas") -> {
                ChatMessage(
                    text = if (isEnglish) "I'm sorry you're feeling this way. Would you like to write about it in your journal, or talk to the community?" else "Mujhe afsos hai aap udas hain. Kya aap apne journal mein likhna chahenge ya kisi se baat karna chahenge?",
                    isBot = true,
                    type = MessageType.QUICK_REPLIES,
                    options = if (isEnglish) listOf("Open Journal", "Community", "Just chat") else listOf("Journal Kholein", "Community", "Baat karein")
                )
            }
            else -> {
                val genericResponsesEn = listOf(
                    "I understand. Tell me more about that.",
                    "That sounds challenging, but you are making progress.",
                    "I'm here for you. What do you think triggered this?",
                    "Taking it one day at a time is key. How did you handle it?"
                )
                val genericResponsesUr = listOf(
                    "Main samajh raha hoon. Is baray mein aur batayen.",
                    "Ye mushkil lag raha hai, par aap behtar ho rahe hain.",
                    "Main yahan aapke liye hoon. Aapko kya lagta hai iski wajah kya thi?",
                    "Har din ko naye siray se shuru karein. Aapne isay kaise handle kiya?"
                )
                ChatMessage(
                    text = if (isEnglish) genericResponsesEn.random() else genericResponsesUr.random(),
                    isBot = true,
                    type = MessageType.TEXT
                )
            }
        }
    }
}
@Composable
fun ChatScreen(
    navController: NavController,
    onNavigateBack: () -> Unit,
    isEnglish: Boolean = false,
    userName: String = "User",
    counselorId: String = "",
    counselorName: String = "SAHARA",
    forUserId: String = "",
    chatViewModel: ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("chat_frame_state", Context.MODE_PRIVATE) }
    val uid = remember(chatViewModel) { chatViewModel.signedInUserId }
    var selectedCounselorId by rememberSaveable(counselorId) { mutableStateOf(counselorId) }
    var selectedCounselorName by rememberSaveable(counselorName) { mutableStateOf(counselorName) }

    if (forUserId.isNotBlank()) {
        ChatConversationScreen(
            navController = navController,
            onNavigateBack = onNavigateBack,
            isEnglish = isEnglish,
            userName = userName,
            counselorId = counselorId,
            counselorName = counselorName,
            forUserId = forUserId,
            chatViewModel = chatViewModel,
            showFrameHint = false,
        )
        return
    }

    val initialPage = remember(counselorId) {
        if (counselorId.isNotBlank()) 1 else prefs.getInt("active_chat_frame", 0).coerceIn(0, 1)
    }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 2 })
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState.currentPage) {
        prefs.edit().putInt("active_chat_frame", pagerState.currentPage).apply()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    var maxPointers = 0
                    var totalDx = 0f
                    var totalDy = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        maxPointers = maxOf(maxPointers, pressed.size)
                        if (pressed.size >= 2) {
                            val avgDx = pressed.map { it.positionChange().x }.average().toFloat()
                            val avgDy = pressed.map { it.positionChange().y }.average().toFloat()
                            totalDx += avgDx
                            totalDy += avgDy
                        }
                        if (pressed.isEmpty()) break
                    }
                    if (maxPointers >= 2 && abs(totalDx) > 120f && abs(totalDx) > abs(totalDy) * 1.4f) {
                        val target = when {
                            totalDx < 0f -> 1
                            else -> 0
                        }
                        if (target != pagerState.currentPage) {
                            scope.launch { pagerState.animateScrollToPage(target) }
                        }
                    }
                }
            }
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            if (page == 0) {
                val aiVm: ChatViewModel = if (counselorId.isBlank()) {
                    chatViewModel
                } else {
                    viewModel(key = "chat_ai_$uid")
                }
                ChatConversationScreen(
                    navController = navController,
                    onNavigateBack = onNavigateBack,
                    isEnglish = isEnglish,
                    userName = userName,
                    counselorId = "",
                    counselorName = "SAHARA",
                    chatViewModel = aiVm,
                    showFrameHint = pagerState.currentPage == 0,
                )
            } else {
                if (selectedCounselorId.isBlank()) {
                    CounselorFrameOverview(
                        navController = navController,
                        onNavigateBack = onNavigateBack,
                        isEnglish = isEnglish,
                        uid = uid,
                        showFrameHint = pagerState.currentPage == 1,
                        onOpenCounselor = { id, name ->
                            selectedCounselorId = id
                            selectedCounselorName = name
                        },
                    )
                } else {
                    val counselorVm: ChatViewModel = if (counselorId.isNotBlank()) {
                        chatViewModel
                    } else {
                        viewModel(key = "chat_counselor_$selectedCounselorId")
                    }
                    ChatConversationScreen(
                        navController = navController,
                        onNavigateBack = onNavigateBack,
                        isEnglish = isEnglish,
                        userName = userName,
                        counselorId = selectedCounselorId,
                        counselorName = selectedCounselorName,
                        chatViewModel = counselorVm,
                        showFrameHint = pagerState.currentPage == 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatConversationScreen(
    navController: NavController,
    onNavigateBack: () -> Unit,
    isEnglish: Boolean = false,
    userName: String = "User",
    counselorId: String = "",
    counselorName: String = "SAHARA",
    forUserId: String = "",
    chatViewModel: ChatViewModel = viewModel(),
    showFrameHint: Boolean = false,
) {
    val isDark = isSystemInDarkTheme()
    val hazeState = remember { HazeState() }
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val microphonePermissionRequester = rememberAppPermissionRequester(
        permission = Manifest.permission.RECORD_AUDIO,
        isEnglish = isEnglish,
        copy = PermissionCopy(
            deniedEn = "Microphone permission was denied.",
            deniedUr = "Microphone ki ijazat nahi di gayi.",
            settingsEn = "Enable microphone permission in App settings to record voice notes.",
            settingsUr = "Voice note ke liye App settings mein microphone ki ijazat dein.",
            grantedEn = "Microphone enabled. Hold the mic button again to record.",
            grantedUr = "Microphone ki ijazat mil gayi. Record karne ke liye mic dobara daba kar rakhein.",
        ),
        onGranted = {},
    )

    val uid      = remember(chatViewModel) { chatViewModel.signedInUserId }
    val isAiMode = counselorId.isBlank()
    LaunchedEffect(uid, isAiMode, counselorId, forUserId) {
        if (uid.isNotBlank())
            chatViewModel.initSession(
                userId      = uid,
                isAiChat    = isAiMode,
                counselorId = counselorId,
                targetUserId = forUserId,
                counselorName = counselorName,
            )
    }
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
    var input by remember { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableIntStateOf(0) }
    val welcomeMsg = if (isAiMode) {
        if (isEnglish)
            "Hello, $userName! I'm SAHARA, your AI companion. How are you feeling today?"
        else
            "Salam $userName! Main SAHARA, aapka AI assistant. Aaj aap kaisa feel kar rahe hain?"
    } else {
        if (isEnglish)
            "Hello! I'm $counselorName. How can I help you today?"
        else
            "Salam! Main $counselorName hoon. Aaj aap kaise hain?"
    }
    val welcomeMessage = remember(welcomeMsg) { ChatMessage(text = welcomeMsg, isBot = true) }
    val transientMessages = remember { mutableStateListOf<ChatMessage>() }

    val vmMessages by chatViewModel.messages.collectAsState()
    val aiMetadata by chatViewModel.aiMetadata.collectAsState()
    val persistedMessages = vmMessages.map { msg ->
        val tsMillis = msg.timestamp.seconds * 1000L + msg.timestamp.nanoseconds / 1_000_000L
        val storedMetadata = aiMetadata[msg.messageId]
        val storedMessageType = storedMetadata?.messageType
            ?: SaharaMessageType.fromWire(msg.messageType, null)
        val localType = when (storedMessageType) {
            SaharaMessageType.CRISIS_CARD -> MessageType.CRISIS_CARD
            SaharaMessageType.QUICK_REPLIES -> MessageType.QUICK_REPLIES
            SaharaMessageType.EXERCISE_CARD -> MessageType.EXERCISE_CARD
            SaharaMessageType.VOICE_NOTE -> MessageType.VOICE_NOTE
            else -> MessageType.TEXT
        }
        ChatMessage(
            id = msg.messageId,
            text = msg.content,
            isBot = if (isAiMode) msg.isFromAI else msg.senderId != uid,
            timestamp = tsMillis,
            type = localType,
            options = storedMetadata?.quickReplies?.takeIf { it.isNotEmpty() },
            actionDestination = storedMetadata?.actionDestination ?: when (localType) {
                MessageType.CRISIS_CARD -> "emergency"
                MessageType.EXERCISE_CARD -> "meditation"
                else -> null
            },
        )
    }
    val displayMessages: List<ChatMessage> = when {
        uid.isBlank() && isAiMode -> listOf(welcomeMessage) + transientMessages
        else -> (persistedMessages + transientMessages).sortedBy { it.timestamp }.ifEmpty {
            listOf(welcomeMessage)
        }
    }

    val listState = rememberLazyListState()
    val isKeyboardVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
    val latestVisible by remember(displayMessages.size, isTyping) {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty() || displayMessages.isEmpty()) {
                true
            } else {
                val lastContentIndex = if (isTyping) displayMessages.size else displayMessages.lastIndex
                visibleItems.lastOrNull()?.index ?: 0 >= lastContentIndex - 1
            }
        }
    }
    var pendingReceivedMessages by remember { mutableIntStateOf(0) }
    var lastObservedMessageId by remember { mutableStateOf<String?>(null) }
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 45) }
    DisposableEffect(Unit) { onDispose { toneGenerator.release() } }
    LaunchedEffect(displayMessages.lastOrNull()?.id, isTyping, latestVisible) {
        if (displayMessages.isEmpty()) return@LaunchedEffect
        val latest = displayMessages.last()
        val isNewMessage = latest.id != lastObservedMessageId
        val isFirstLoad = lastObservedMessageId == null
        val shouldFollow = latestVisible || isFirstLoad || !latest.isBot
        if (shouldFollow) {
            listState.animateScrollToItem(if (isTyping) displayMessages.size else displayMessages.lastIndex)
            pendingReceivedMessages = 0
        } else if (isNewMessage && latest.isBot) {
            pendingReceivedMessages += 1
            toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 120)
        }
        lastObservedMessageId = latest.id
    }
    LaunchedEffect(latestVisible) {
        if (latestVisible) pendingReceivedMessages = 0
    }

    val saharaUnreachable by chatViewModel.saharaUnreachable.collectAsState()
    val chatUiState by chatViewModel.uiState.collectAsState()
    val identityVisible by chatViewModel.identityVisible.collectAsState()
    val counselorProfile by chatViewModel.counselorProfile.collectAsState()
    val targetUserProfile by chatViewModel.targetUserProfile.collectAsState()
    val sessionExpiresAt by chatViewModel.sessionExpiresAt.collectAsState()
    val chatBlocked by chatViewModel.chatBlocked.collectAsState()
    var countdownNow by remember { mutableStateOf(System.currentTimeMillis()) }
    var showHistorySheet by rememberSaveable { mutableStateOf(false) }
    var showProfileSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isAiMode, uid) {
        if (!isAiMode) return@LaunchedEffect
        chatViewModel.aiReplyEvents.collect { event ->
            isTyping = false
            if (!event.isPersisted) {
                val localType = when (event.metadata.messageType) {
                    SaharaMessageType.CRISIS_CARD -> MessageType.CRISIS_CARD
                    SaharaMessageType.QUICK_REPLIES -> MessageType.QUICK_REPLIES
                    SaharaMessageType.EXERCISE_CARD -> MessageType.EXERCISE_CARD
                    else -> MessageType.TEXT
                }
                transientMessages.add(
                    ChatMessage(
                        id = event.metadata.messageId,
                        text = event.text,
                        isBot = true,
                        type = localType,
                        options = event.metadata.quickReplies.takeIf { it.isNotEmpty() },
                        actionDestination = event.metadata.actionDestination ?: when (localType) {
                            MessageType.CRISIS_CARD -> "emergency"
                            MessageType.EXERCISE_CARD -> "meditation"
                            else -> null
                        },
                    )
                )
            }
        }
    }
    LaunchedEffect(chatViewModel) {
        chatViewModel.voiceNoteEvents.collect { event ->
            isTyping = false
            event.analyzedBubbleText?.let { text ->
                val index = transientMessages.indexOfFirst { it.id == event.bubbleId }
                if (index >= 0) transientMessages[index] = transientMessages[index].copy(text = text)
            }
            transientMessages.add(ChatMessage(text = event.replyText, isBot = true))
        }
    }
    LaunchedEffect(chatUiState) {
        val error = chatUiState as? ChatUiState.Error ?: return@LaunchedEffect
        isTyping = false
        context.showLocalizedToast(isEnglish, error.message, error.message)
        chatViewModel.clearError()
    }
    LaunchedEffect(sessionExpiresAt) {
        while (sessionExpiresAt > System.currentTimeMillis()) {
            countdownNow = System.currentTimeMillis()
            delay(1000)
        }
        countdownNow = System.currentTimeMillis()
    }
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDuration = 0
            while (isRecording) {
                delay(1000)
                recordingDuration++
            }
        }
    }
    val blobMotion = rememberBackdropBlobMotion()
    val handleSendMessage: (String) -> Unit = { text ->
        if (text.isNotBlank()) {
            input = ""
            focusManager.clearFocus()
            if (isAiMode) {
                isTyping = true
                if (uid.isNotBlank()) {
                    chatViewModel.sendUserMessageToAI(text.trim(), isEnglish) {
                        if (!GlobalAppState.hasCheckedIn) {
                            GlobalAppState.hasCheckedIn = true
                            GlobalAppState.currentStreak++
                        }
                    }
                } else {
                    transientMessages.add(ChatMessage(text = text.trim(), isBot = false))
                    coroutineScope.launch {
                        delay((900..1600).random().toLong())
                        isTyping = false
                        transientMessages.add(MockAIEngine.processInput(text, isEnglish))
                    }
                }
            } else {
                if (uid.isNotBlank()) {
                    chatViewModel.sendMessageToCounselor(text, counselorId)
                }
            }
        }
    }
    val isCounselorSide = forUserId.isNotBlank()
    var showBlockConfirm by remember { mutableStateOf(false) }
    val openCounselorCall: (Boolean) -> Unit = { video ->
        if (counselorId.isBlank()) {
            context.showLocalizedToast(
                isEnglish,
                "Call is unavailable for this chat.",
                "Is chat ke liye call available nahi.",
            )
        } else {
            val mode = if (video) "video" else "voice"
            val target = if (isCounselorSide) forUserId else "self"
            val nameArg = counselorName.replace(" ", "_").ifBlank { "Counselor" }
            navController.navigate("counselor-call/$counselorId/$nameArg/$mode/$target")
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            SaharaStrongGreen.copy(alpha = if (isDark) 0.15f else 0.1f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .size(350.dp)
                    .offset(x = (-80).dp, y = (-50).dp)
                    .primaryBlobMotion(blobMotion)
                    .background(Brush.radialGradient(listOf(SaharaStrongGreen.copy(alpha = 0.15f), Color.Transparent)))
            )
            Box(
                modifier = Modifier
                    .size(400.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 100.dp, y = 50.dp)
                    .secondaryBlobMotion(blobMotion)
                    .background(Brush.radialGradient(listOf(SaharaSky.copy(alpha = 0.15f), Color.Transparent)))
            )
        }

        Scaffold(
        bottomBar = { if (!isKeyboardVisible) BottomNav(navController = navController, hazeState = hazeState) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .pointerInput(Unit) {
                        detectDragGestures { _, dragAmount ->
                            if (dragAmount.y > 20) focusManager.clearFocus()
                        }
                    }
            ) {
                ChatTopBar(
                    onNavigateBack = onNavigateBack,
                    hazeState = hazeState,
                    isEnglish = isEnglish,
                    isDark = isDark,
                    displayName = if (isAiMode) "SAHARA AI" else counselorName,
                    isOnline = isAiMode || true,
                    subtitle = run {
                        if (isAiMode || sessionExpiresAt <= 0L) null
                        else {
                            val remMs = sessionExpiresAt - countdownNow
                            if (remMs <= 0L) (if (isEnglish) "Session ended" else "Session khatam")
                            else {
                                val totalMin = remMs / 60000L
                                if (isEnglish) "${totalMin / 60}h ${totalMin % 60}m left"
                                else "${totalMin / 60}h ${totalMin % 60}m baqi"
                            }
                        }
                    },
                    isAiMode = isAiMode,
                    isCounselorSide = isCounselorSide,
                    identityVisible = identityVisible,
                    onAvatarClick = { if (!isAiMode) showProfileSheet = true },
                    onHistory = { showHistorySheet = true },
                    onReport = {
                        chatViewModel.reportCurrentChat(
                            reason = "Chat concern",
                            description = "Reported from chat menu.",
                        )
                        context.showLocalizedToast(isEnglish, "Report submitted.", "Report submit ho gayi.")
                    },
                    onBlock = { showBlockConfirm = true },
                    onVoiceCall = { openCounselorCall(false) },
                    onVideoCall = { openCounselorCall(true) },
                    onToggleIdentity = { chatViewModel.setIdentityVisible(!identityVisible) },
                )
                SaharaUnreachableBanner(
                    visible = isAiMode && saharaUnreachable,
                    isEnglish = isEnglish,
                )
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        items(displayMessages) { message ->
                            val saharaMeta = aiMetadata[message.id]
                            when (message.type) {
                                MessageType.TEXT -> {
                                    ChatBubbleText(message, isDark)
                                    if (saharaMeta?.isPrescriptionRedirect == true) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        PrescriptionRedirectCard(isEnglish = isEnglish)
                                    }
                                }
                                MessageType.QUICK_REPLIES -> {
                                    ChatBubbleText(message, isDark)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ChatBubbleQuickReplies(message.options ?: emptyList()) { handleSendMessage(it) }
                                }
                                MessageType.CRISIS_CARD -> {
                                    ChatBubbleText(message, isDark)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ChatBubbleCrisisCard(isEnglish, navController, message.actionDestination)
                                }
                                MessageType.EXERCISE_CARD -> {
                                    ChatBubbleText(message, isDark)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ChatBubbleExerciseCard(isEnglish, navController, message.actionDestination)
                                }
                                MessageType.VOICE_NOTE -> ChatBubbleVoice(message, isDark)
                            }
                        }
                        if (isTyping) item { TypingIndicator(isDark) }
                    }
                    if (!latestVisible) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                        ) {
                            JumpToLatestButton(
                                hazeState = hazeState,
                                pendingCount = pendingReceivedMessages,
                                isDark = isDark,
                                onClick = {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(
                                            if (isTyping) displayMessages.size else displayMessages.lastIndex
                                        )
                                        pendingReceivedMessages = 0
                                    }
                                },
                            )
                        }
                    }
                }
                val voiceRecorder = remember { VoiceRecorder(context) }
                DisposableEffect(Unit) { onDispose { voiceRecorder.release() } }

                ChatInputArea(
                    input = input,
                    onInputChange = { input = it },
                    onSend = { handleSendMessage(input) },
                    isRecording = isRecording,
                    recordingDuration = recordingDuration,
                    onRecordStart = {
                        when {
                            microphonePermissionRequester.hasPermission() -> {
                                voiceRecorder.start().fold(
                                    onSuccess = {
                                        isRecording = true
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                                    },
                                    onFailure = {
                                        context.showLocalizedToast(
                                            isEnglish,
                                            "Could not start voice recording. Please try again.",
                                            "Voice recording shuru nahi ho saki. Dobara koshish karein.",
                                        )
                                    },
                                )
                            }
                            else -> microphonePermissionRequester.request()
                        }
                    },
                    onRecordEnd = lambda@{
                        val secondsRecorded = recordingDuration
                        isRecording = false
                        if (secondsRecorded <= 0) return@lambda
                        val clip = voiceRecorder.stop().getOrNull()
                        if (clip == null || clip.bytes.isEmpty()) {
                            transientMessages.add(
                                ChatMessage(
                                    text = if (isEnglish)
                                        "Voice note was too short to analyse — try a slightly longer clip."
                                    else
                                        "Voice note bohat choti thi — thora lamba clip try karein.",
                                    isBot = true,
                                )
                            )
                            return@lambda
                        }
                        val bubbleId = java.util.UUID.randomUUID().toString()
                        transientMessages.add(
                            ChatMessage(
                                id = bubbleId,
                                text = "Voice note ($secondsRecorded sec)",
                                isBot = false,
                                type = MessageType.VOICE_NOTE,
                            )
                        )

                        isTyping = true
                        chatViewModel.analyzeVoiceNote(
                            bubbleId = bubbleId,
                            secondsRecorded = secondsRecorded,
                            audioBytes = clip.bytes,
                            mimeType = clip.mimeType,
                            isEnglish = isEnglish,
                        )
                    },
                    isEnglish = isEnglish,
                    isDark = isDark
                )
                ChatNotice(
                    isAiMode = isAiMode,
                    isCounselorUserSide = !isAiMode && !isCounselorSide,
                    identityVisible = identityVisible,
                    isEnglish = isEnglish,
                    onHideIdentity = { chatViewModel.setIdentityVisible(false) },
                )
                Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding()))
            }
        }
        }
        TwoFingerGestureHintOverlay(
            visible = showFrameHint,
            isAiMode = isAiMode,
            isEnglish = isEnglish,
        )
        if (showHistorySheet) {
            ChatHistorySheet(
                messages = displayMessages,
                isEnglish = isEnglish,
                onDismiss = { showHistorySheet = false },
                onJump = { targetId ->
                    val index = displayMessages.indexOfFirst { it.id == targetId }
                    if (index >= 0) {
                        coroutineScope.launch { listState.animateScrollToItem(index) }
                    }
                    showHistorySheet = false
                },
            )
        }
        if (showProfileSheet && !isAiMode) {
            ChatProfileSheet(
                profile = if (isCounselorSide) targetUserProfile else counselorProfile,
                isCounselorProfile = !isCounselorSide,
                identityVisible = identityVisible,
                isEnglish = isEnglish,
                fallbackName = counselorName,
                onDismiss = { showProfileSheet = false },
            )
        }
    }

    if (showBlockConfirm) {
        GlassAlertDialog(
            hazeState = hazeState,
            onDismissRequest = { showBlockConfirm = false },
            title = { Text(if (isEnglish) "Block this chat?" else "Ye chat block karein?") },
            text = {
                Text(
                    if (isEnglish) "They won't be able to message you, and this session will be blocked."
                    else "Wo aapko message nahi kar sakenge, aur ye session block ho jayega."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBlockConfirm = false
                    chatViewModel.blockCurrentChat()
                    context.showLocalizedToast(isEnglish, "Chat blocked.", "Chat block ho gayi.")
                }) { Text(if (isEnglish) "Block" else "Block", color = SaharaCoral) }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) {
                    Text(if (isEnglish) "Cancel" else "Cancel")
                }
            }
        )
    }
}

@Composable
private fun CounselorFrameOverview(
    navController: NavController,
    onNavigateBack: () -> Unit,
    isEnglish: Boolean,
    uid: String,
    showFrameHint: Boolean,
    onOpenCounselor: (String, String) -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val hazeState = remember { HazeState() }
    val blobMotion = rememberBackdropBlobMotion()
    var sessions by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoadingSessions by remember { mutableStateOf(true) }

    LaunchedEffect(uid) {
        if (uid.isBlank()) {
            isLoadingSessions = false
            return@LaunchedEffect
        }
        isLoadingSessions = true
        RealtimeDBService.listenToUserCounselorChats(uid).collect {
            sessions = it
            isLoadingSessions = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            SaharaSky.copy(alpha = if (isDark) 0.18f else 0.12f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                            MaterialTheme.colorScheme.background,
                        )
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .offset(x = (-90).dp, y = (-40).dp)
                    .primaryBlobMotion(blobMotion)
                    .background(Brush.radialGradient(listOf(SaharaSky.copy(alpha = 0.16f), Color.Transparent)))
            )
            Box(
                modifier = Modifier
                    .size(380.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 120.dp, y = 60.dp)
                    .secondaryBlobMotion(blobMotion)
                    .background(Brush.radialGradient(listOf(SaharaStrongGreen.copy(alpha = 0.14f), Color.Transparent)))
            )
        }

        Scaffold(
            bottomBar = { BottomNav(navController = navController, hazeState = hazeState) },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HazeBackButton(onClick = onNavigateBack, hazeState = hazeState)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Counselor Chats" else "Counselor Chats",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = SaharaStrongGreen,
                        )
                        Text(
                            text = if (isEnglish) "Swipe left with two fingers to reach this frame" else "Is frame ke liye 2 ungliyon se left swipe karein",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (isLoadingSessions) {
                        item {
                            SaharaCard(
                                variant = CardVariant.GLASS,
                                hazeState = hazeState,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(120.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(color = SaharaStrongGreen)
                                        Text(
                                            text = if (isEnglish) "Loading counselor chats..." else "Counselor chats load ho rahe hain...",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    } else if (sessions.isEmpty()) {
                        item {
                            SaharaCard(
                                variant = CardVariant.GLASS,
                                hazeState = hazeState,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    Icons.Default.People,
                                    contentDescription = null,
                                    tint = SaharaStrongGreen,
                                    modifier = Modifier.size(34.dp),
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (isEnglish) "No counselor conversations yet" else "Abhi koi counselor chat nahi",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = if (isEnglish) "Choose a counselor to start human support." else "Insani support ke liye counselor chunein.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                SaharaButton(
                                    text = if (isEnglish) "Browse Counselors" else "Counselors Dekhein",
                                    onClick = { navController.navigate("counselors") { launchSingleTop = true } },
                                    isFullWidth = true,
                                    variant = ButtonVariant.SAHARASTRONGGREENGLASS,
                                    isEnglish = isEnglish,
                                )
                            }
                        }
                    } else {
                        item {
                            SaharaButton(
                                text = if (isEnglish) "Browse Counselors" else "Counselors Dekhein",
                                onClick = { navController.navigate("counselors") { launchSingleTop = true } },
                                isFullWidth = true,
                                variant = ButtonVariant.OUTLINE,
                                isEnglish = isEnglish,
                            )
                        }
                        items(sessions) { session ->
                            val counselorKey = session["counselorKey"]?.toString().orEmpty()
                            val counselorName = session["counselorName"]?.toString()?.ifBlank { counselorKey } ?: counselorKey
                            val lastMessage = session["lastMessage"]?.toString().orEmpty()
                            val lastTimestamp = (session["lastTimestamp"] as? Long) ?: 0L
                            SaharaCard(
                                variant = CardVariant.GLASS,
                                hazeState = hazeState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = counselorKey.isNotBlank()) {
                                        onOpenCounselor(counselorKey, counselorName)
                                    },
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(SaharaStrongGreen),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Default.Person, null, tint = Color.White)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(counselorName, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = lastMessage.ifBlank {
                                                if (isEnglish) "Open conversation" else "Conversation kholein"
                                            },
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    if (lastTimestamp > 0L) {
                                        Text(
                                            text = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(lastTimestamp)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        TwoFingerGestureHintOverlay(
            visible = showFrameHint,
            isAiMode = false,
            isEnglish = isEnglish,
        )
    }
}

@Composable
private fun JumpToLatestButton(
    hazeState: HazeState,
    pendingCount: Int,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (pendingCount > 0) 1.1f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "jumpToLatestScale",
    )
    val glassStyle = SaharaHazeMaterials.defaultGlass(isDark)
    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .hazeEffect(state = hazeState) {
                inputScale = HazeInputScale.Auto
                blurEffect { this.style = glassStyle }
            }
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Latest messages", tint = SaharaStrongGreen)
        if (pendingCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .sizeIn(minWidth = 18.dp, minHeight = 18.dp)
                    .background(SaharaCoral, CircleShape)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = pendingCount.coerceAtMost(99).toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ChatNotice(
    isAiMode: Boolean,
    isCounselorUserSide: Boolean,
    identityVisible: Boolean,
    isEnglish: Boolean,
    onHideIdentity: () -> Unit,
) {
    when {
        isAiMode -> Text(
            text = if (isEnglish)
                "SAHARA AI does not provide medical advice. Please double-check its messages."
            else
                "SAHARA AI medical advice nahi deta. Is ke messages double-check karein.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
        isCounselorUserSide && !identityVisible -> Text(
            text = if (isEnglish)
                "Your name and email are hidden from this counselor."
            else
                "Aapka naam aur email is counselor se hidden hain.",
            style = MaterialTheme.typography.labelSmall,
            color = SaharaStrongGreen,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
        isCounselorUserSide -> Text(
            text = buildAnnotatedString {
                append(if (isEnglish) "Your name and email are visible to the counselor, tap " else "Aapka naam aur email counselor ko visible hain, ")
                withStyle(SpanStyle(color = SaharaSky, textDecoration = TextDecoration.Underline)) {
                    append(if (isEnglish) "here" else "yahan")
                }
                append(if (isEnglish) " to hide it." else " tap kar ke hide karein.")
            },
            style = MaterialTheme.typography.labelSmall,
            color = SaharaCoral,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                .clickable { onHideIdentity() }
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun TwoFingerGestureHintOverlay(
    visible: Boolean,
    isAiMode: Boolean,
    isEnglish: Boolean,
) {
    var show by remember(visible, isAiMode) { mutableStateOf(visible) }
    LaunchedEffect(visible, isAiMode) {
        if (visible) {
            show = true
            delay(2600)
            show = false
        }
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = show,
            enter = fadeIn() + scaleIn(initialScale = 0.96f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
        ) {
            val transition = rememberInfiniteTransition(label = "twoFingerHint")
            val start = if (isAiMode) 34f else -34f
            val end = -start
            val offset by transition.animateFloat(
                initialValue = start,
                targetValue = end,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "twoFingerHintOffset",
            )
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(96.dp)
                            .height(38.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            modifier = Modifier.offset(x = offset.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.size(18.dp).background(SaharaStrongGreen, CircleShape))
                            Box(Modifier.size(18.dp).background(SaharaStrongGreen, CircleShape))
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isAiMode) {
                            if (isEnglish) "Use two fingers and swipe left for counselor chats."
                            else "2 ungliyon se left swipe karein counselor chats ke liye."
                        } else {
                            if (isEnglish) "Use two fingers and swipe right for SAHARA AI."
                            else "2 ungliyon se right swipe karein SAHARA AI ke liye."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatHistorySheet(
    messages: List<ChatMessage>,
    isEnglish: Boolean,
    onDismiss: () -> Unit,
    onJump: (String) -> Unit,
) {
    val grouped = remember(messages) {
        messages.groupBy { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(it.timestamp)) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = if (isEnglish) "Chat History" else "Chat History",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (messages.isEmpty()) {
                Text(
                    text = if (isEnglish) "No messages yet." else "Abhi koi message nahi.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    modifier = Modifier.heightIn(max = 460.dp),
                ) {
                    grouped.forEach { (date, dayMessages) ->
                        item {
                            Text(
                                text = date,
                                style = MaterialTheme.typography.labelLarge,
                                color = SaharaStrongGreen,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        items(dayMessages) { message ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onJump(message.id) },
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        text = if (message.isBot) {
                                            if (isEnglish) "Received" else "Received"
                                        } else {
                                            if (isEnglish) "You" else "Aap"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = message.text,
                                        maxLines = 2,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatProfileSheet(
    profile: Map<String, Any>?,
    isCounselorProfile: Boolean,
    identityVisible: Boolean,
    isEnglish: Boolean,
    fallbackName: String,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val name = when {
            isCounselorProfile -> profile.profileText("assignedName", "name", "fullName").ifBlank { fallbackName }
            identityVisible -> profile.profileText("fullName", "name").ifBlank { if (isEnglish) "User" else "User" }
            else -> if (isEnglish) "Anonymous user" else "Anonymous user"
        }
        val email = if (!isCounselorProfile && identityVisible) profile.profileText("email").ifBlank { "Hidden" } else "Hidden"
        val rating = profile.profileNumber("rating") ?: 0.0
        val handledCases = profile.profileNumber("sessionCount", "handledCases", "casesHandled")?.toInt() ?: 0
        val specialization = profile.profileText("specialization", "specialty").ifBlank {
            if (isEnglish) "Mental health support" else "Mental health support"
        }
        val bio = profile.profileText("bio", "about").ifBlank {
            if (isEnglish) "No bio available." else "Bio available nahi."
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(SaharaStrongGreen),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (isCounselorProfile) specialization else email,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            if (isCounselorProfile) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ProfileMetric(
                        icon = Icons.Default.Star,
                        label = if (isEnglish) "Rating" else "Rating",
                        value = "%.1f".format(rating),
                        modifier = Modifier.weight(1f),
                    )
                    ProfileMetric(
                        icon = Icons.Default.AssignmentTurnedIn,
                        label = if (isEnglish) "Cases" else "Cases",
                        value = handledCases.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(bio, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    text = if (identityVisible) {
                        if (isEnglish) "This user has allowed their name and email to be visible in this chat."
                        else "Is user ne is chat mein apna naam aur email visible rakha hai."
                    } else {
                        if (isEnglish) "This user is anonymous in this chat." else "Ye user is chat mein anonymous hai."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                profile.profileText("ageGroup").takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("${if (isEnglish) "Age" else "Age"}: $it", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun ProfileMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, tint = SaharaStrongGreen)
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun Map<String, Any>?.profileText(vararg keys: String): String {
    if (this == null) return ""
    return keys.firstNotNullOfOrNull { key ->
        this[key]?.toString()?.trim()?.takeIf { it.isNotBlank() }
    }.orEmpty()
}

private fun Map<String, Any>?.profileNumber(vararg keys: String): Double? {
    if (this == null) return null
    return keys.firstNotNullOfOrNull { key ->
        when (val value = this[key]) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }
}

@Composable
fun ChatTopBar(
    onNavigateBack: () -> Unit,
    hazeState: HazeState,
    isEnglish: Boolean,
    isDark: Boolean,
    displayName: String = "SAHARA AI",
    isOnline: Boolean = true,
    subtitle: String? = null,
    isAiMode: Boolean,
    isCounselorSide: Boolean,
    identityVisible: Boolean,
    onAvatarClick: () -> Unit,
    onHistory: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    onVoiceCall: () -> Unit,
    onVideoCall: () -> Unit,
    onToggleIdentity: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.6f else 0.85f),
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HazeBackButton(onClick = onNavigateBack, hazeState = hazeState)
            Spacer(modifier = Modifier.width(12.dp))
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SaharaStrongGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isAiMode) Icons.Default.Favorite else Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(enabled = !isAiMode) { onAvatarClick() }
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        subtitle ?: (if (isOnline) (if (isEnglish) "Online" else "Online") else (if (isEnglish) "Away" else "Away")),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (subtitle != null) SaharaSky else if (isOnline) SaharaStrongGreen else SaharaWarning
                    )
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (isAiMode) {
                        DropdownMenuItem(
                            text = { Text(if (isEnglish) "Chat History" else "Chat History") },
                            onClick = {
                                showMenu = false
                                onHistory()
                            },
                            leadingIcon = { Icon(Icons.Default.History, null) }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(if (isEnglish) "Report" else "Report") },
                            onClick = {
                                showMenu = false
                                onReport()
                            },
                            leadingIcon = { Icon(Icons.Default.Flag, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isEnglish) "Block" else "Block") },
                            onClick = {
                                showMenu = false
                                onBlock()
                            },
                            leadingIcon = { Icon(Icons.Default.Block, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isEnglish) "Voice Call" else "Voice Call") },
                            onClick = {
                                showMenu = false
                                onVoiceCall()
                            },
                            leadingIcon = { Icon(Icons.Default.Call, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isEnglish) "Video Call" else "Video Call") },
                            onClick = {
                                showMenu = false
                                onVideoCall()
                            },
                            leadingIcon = { Icon(Icons.Default.Videocam, null) }
                        )
                        if (!isCounselorSide) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (identityVisible) {
                                            if (isEnglish) "Hide my identity" else "Identity chhupayein"
                                        } else {
                                            if (isEnglish) "Show my identity" else "Identity dikhayein"
                                        }
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onToggleIdentity()
                                },
                                leadingIcon = {
                                    Icon(
                                        if (identityVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        null
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun ChatBubbleText(message: ChatMessage, isDark: Boolean) {
    val isReceived = message.isBot

    val shape = if (isReceived)
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    else
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)

    val bgColor = when {
        isReceived && isDark  -> Color(0xFF1F2C34)
        isReceived            -> Color.White
        else                  -> SaharaStrongGreen
    }
    val textColor = if (isReceived && !isDark) Color(0xFF111111) else Color.White
    val timeColor = if (isReceived && !isDark) Color(0xFF8696A0) else Color.White.copy(alpha = 0.65f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = if (isReceived) Arrangement.Start else Arrangement.End
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 80.dp, max = 260.dp)
                .clip(shape)
                .background(bgColor)
                .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 5.dp)
        ) {
            Text(
                text      = message.text,
                color     = textColor,
                fontSize  = 15.sp,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text     = message.getFormattedTime(),
                color    = timeColor,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}
@Composable
fun ChatBubbleQuickReplies(options: List<String>, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start)
    ) {
        options.forEach { option ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SaharaSky.copy(alpha = 0.15f),
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onSelect(option) }
            ) {
                Text(
                    text = option,
                    color = SaharaSky,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}
@Composable
fun ChatBubbleCrisisCard(isEnglish: Boolean, navController: NavController, destination: String?) {
    val isDark = isSystemInDarkTheme()
    Surface(
        color = if (isDark) Color(0xFF121212).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, Brush.verticalGradient(listOf(SaharaCoral.copy(0.5f), SaharaCoral.copy(0.1f)))),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(0.85f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = SaharaCoral, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isEnglish) "Emergency Toolkit" else "Hanggami Madad", fontWeight = FontWeight.Bold, color = SaharaCoral)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (isEnglish) "Access your coping strategies or contact a professional immediately."
                else "Apni coping strategies dekhein ya foran kisi mahir se rabta karein.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            SaharaButton(
                text = if (isEnglish) "Open Toolkit" else "Toolkit Kholein",
                onClick = { destination?.let { navController.navigate(it) } },
                variant = ButtonVariant.SAHARASTRONGGREENGLASS,
                isFullWidth = true,
                isEnglish = isEnglish
            )
        }
    }
}
@Composable
fun ChatBubbleExerciseCard(isEnglish: Boolean, navController: NavController, destination: String?) {
    val isDark = isSystemInDarkTheme()
    Surface(
        color = if (isDark) Color(0xFF121212).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, Brush.verticalGradient(listOf(SaharaSky.copy(0.5f), SaharaSky.copy(0.1f)))),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(0.85f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Spa, contentDescription = null, tint = SaharaSky, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isEnglish) "Breathing Exercise" else "Saans Ki Mashq", fontWeight = FontWeight.Bold, color = SaharaSky)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (isEnglish) "Take 3 minutes to regulate your nervous system."
                else "Apne asabi nizam ko normal karne ke liye 3 minute nikalen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            SaharaButton(
                text = if (isEnglish) "Start Exercise" else "Mashq Shuru Karein",
                onClick = { destination?.let { navController.navigate(it) } },
                variant = ButtonVariant.OUTLINE,
                isFullWidth = true,
                isEnglish = isEnglish
            )
        }
    }
}
@Composable
fun ChatBubbleVoice(message: ChatMessage, isDark: Boolean) {
    val isReceived = message.isBot
    val shape = if (isReceived)
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    else
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    val bgColor = when {
        isReceived && isDark  -> Color(0xFF1F2C34)
        isReceived            -> Color.White
        else                  -> SaharaStrongGreen
    }
    val iconTint   = if (isReceived && !isDark) SaharaStrongGreen else Color.White
    val waveColor  = if (isReceived && !isDark) Color(0xFF8696A0) else Color.White.copy(0.8f)
    val timeColor  = if (isReceived && !isDark) Color(0xFF8696A0) else Color.White.copy(0.65f)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = if (isReceived) Arrangement.Start else Arrangement.End
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 160.dp, max = 240.dp)
                .clip(shape)
                .background(bgColor)
                .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = iconTint, modifier = Modifier.size(26.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(0.4f, 0.8f, 0.6f, 1f, 0.5f, 0.9f, 0.3f, 0.7f, 0.5f).forEach { h ->
                        Box(modifier = Modifier.width(3.dp).height((20 * h).dp).background(waveColor, CircleShape))
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = message.getFormattedTime(), color = timeColor, fontSize = 11.sp, modifier = Modifier.align(Alignment.End))
        }
    }
}
@Composable
fun TypingIndicator(isDark: Boolean) {
    val dot1 = rememberFrameOscillation(0f, 1f, 600)
    val dot2 = rememberFrameOscillation(0f, 1f, 600, phaseFraction = 0.17f)
    val dot3 = rememberFrameOscillation(0f, 1f, 600, phaseFraction = 0.33f)
    val dotColor = if (isDark) Color.White.copy(0.6f) else Color(0xFF8696A0)
    val bgColor  = if (isDark) Color(0xFF1F2C34) else Color.White

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .wrapContentSize()
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                .background(bgColor)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(7.dp).offset { IntOffset(0, (-5 * dot1).dp.roundToPx()) }.background(dotColor, CircleShape))
                Box(modifier = Modifier.size(7.dp).offset { IntOffset(0, (-5 * dot2).dp.roundToPx()) }.background(dotColor, CircleShape))
                Box(modifier = Modifier.size(7.dp).offset { IntOffset(0, (-5 * dot3).dp.roundToPx()) }.background(dotColor, CircleShape))
            }
        }
    }
}
@Composable
fun ChatInputArea(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isRecording: Boolean,
    recordingDuration: Int,
    onRecordStart: () -> Unit,
    onRecordEnd: () -> Unit,
    isEnglish: Boolean,
    isDark: Boolean
) {
    val barBg   = if (isDark) Color(0xFF1F2C34) else Color(0xFFF0F2F5)
    val fieldBg = if (isDark) Color(0xFF2A3942) else Color.White
    val hintColor = if (isDark) Color.White.copy(0.4f) else Color(0xFF8696A0)
    val textColor = if (isDark) Color.White else Color(0xFF111111)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(barBg)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        AnimatedVisibility(visible = isRecording) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(SaharaCoral))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "00:${recordingDuration.toString().padStart(2, '0')}",
                    fontWeight = FontWeight.Bold,
                    color = SaharaCoral,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isEnglish) "Release to send" else "Bhejne ke liye chorein",
                    fontSize = 12.sp,
                    color = hintColor
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp, max = 120.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(fieldBg)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = textColor, fontSize = 15.sp, lineHeight = 20.sp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    cursorBrush = SolidColor(SaharaStrongGreen),
                    decorationBox = { innerTextField ->
                        if (input.isEmpty()) {
                            Text(
                                text = if (isEnglish) "Message..." else "Message likhein...",
                                color = hintColor,
                                fontSize = 15.sp
                            )
                        }
                        innerTextField()
                    }
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            val inputIsBlank = input.isBlank()
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SaharaStrongGreen)
                    .pointerInput(inputIsBlank) {
                        if (inputIsBlank) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                onRecordStart()
                                waitForUpOrCancellation()
                                onRecordEnd()
                            }
                        }
                    }
                    .clickable(enabled = input.isNotBlank()) { onSend() },
                contentAlignment = Alignment.Center
            ) {
                Crossfade(targetState = input.isNotBlank(), label = "InputIcon") { hasText ->
                    if (hasText) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp).offset(x = 1.dp)
                        )
                    } else {
                        val pulse = rememberFrameOscillation(
                            startValue = 1f,
                            endValue = if (isRecording) 1.25f else 1f,
                            halfCycleMillis = 500
                        )
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Hold to record",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp).scale(pulse)
                        )
                    }
                }
            }
        }
    }
}
