package pk.edu.ucp.saharaai.ui.screens
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.ui.components.BottomNav
import pk.edu.ucp.saharaai.ui.components.ButtonVariant
import pk.edu.ucp.saharaai.ui.components.SaharaButton
import pk.edu.ucp.saharaai.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import android.os.VibratorManager
import androidx.compose.ui.unit.IntOffset
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
    userName: String = "User"
) {
    val isDark = isSystemInDarkTheme()
    val hazeState = remember { HazeState() }
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
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
    val welcomeMsg = if (isEnglish)
        "Hello, $userName! I'm SAHARA, your AI companion. How are you feeling today?"
    else
        "Salam $userName! Main SAHARA, aapka AI assistant. Aaj aap kaisa feel kar rahe hain?"
    val messages = remember { mutableStateListOf(ChatMessage(text = welcomeMsg, isBot = true)) }
    val listState = rememberLazyListState()
    val isKeyboardVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(if (isTyping) messages.size else messages.size - 1)
        }
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
    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val blobRotation by infiniteTransition.animateFloat(
        initialValue = -10f, targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(6000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "rotation"
    )
    val blobScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(5000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "scale"
    )
    val handleSendMessage: (String) -> Unit = { text ->
        if (text.isNotBlank()) {
            messages.add(ChatMessage(text = text.trim(), isBot = false))
            input = ""
            isTyping = true
            focusManager.clearFocus()
            if (!GlobalAppState.hasCheckedIn) {
                GlobalAppState.hasCheckedIn = true
                GlobalAppState.currentStreak++
            }
            coroutineScope.launch {
                delay((1000..2000).random().toLong())
                isTyping = false
                messages.add(MockAIEngine.processInput(text, isEnglish))
            }
        }
    }
    Scaffold(
        bottomBar = { if (!isKeyboardVisible) BottomNav(navController = navController, hazeState = hazeState) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
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
                    .rotate(blobRotation)
                    .scale(blobScale)
                    .background(Brush.radialGradient(listOf(SaharaStrongGreen.copy(alpha = 0.15f), Color.Transparent)))
            )
            Box(
                modifier = Modifier
                    .size(400.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 100.dp, y = 50.dp)
                    .rotate(-blobRotation)
                    .scale(blobScale)
                    .background(Brush.radialGradient(listOf(SaharaSky.copy(alpha = 0.15f), Color.Transparent)))
            )
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
                    isEnglish = isEnglish,
                    isDark = isDark,
                    onClearChat = {
                        messages.clear()
                        messages.add(ChatMessage(text = welcomeMsg, isBot = true))
                    }
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(messages) { message ->
                        when (message.type) {
                            MessageType.TEXT -> ChatBubbleText(message, isDark)
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
                ChatInputArea(
                    input = input,
                    onInputChange = { input = it },
                    onSend = { handleSendMessage(input) },
                    isRecording = isRecording,
                    recordingDuration = recordingDuration,
                    onRecordStart = {
                        isRecording = true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                    },
                    onRecordEnd = {
                        isRecording = false
                        if (recordingDuration > 0) {
                            messages.add(ChatMessage(text = "Voice note ($recordingDuration sec)", isBot = false, type = MessageType.VOICE_NOTE))
                            coroutineScope.launch {
                                isTyping = true; delay(1500); isTyping = false
                                messages.add(ChatMessage(
                                    text = if (isEnglish) "I heard your voice note. I'm analyzing your vocal tone for stress patterns."
                                    else "Maine aapki voice note sun li. Main aapki awaz se stress analyze kar raha hoon.",
                                    isBot = true
                                ))
                            }
                        }
                    },
                    isEnglish = isEnglish,
                    isDark = isDark
                )
                Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding()))
            }
        }
    }
}
@Composable
fun ChatTopBar(
    onNavigateBack: () -> Unit,
    isEnglish: Boolean,
    isDark: Boolean,
    onClearChat: () -> Unit
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
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SaharaStrongGreen)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SaharaStrongGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Favorite, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("SAHARA AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (isEnglish) "Online" else "Online",
                        style = MaterialTheme.typography.bodySmall,
                        color = SaharaStrongGreen
                    )
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (isEnglish) "Clear Chat" else "Chat Saaf Karein") },
                        onClick = {
                            onClearChat()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, null) }
                    )
                }
            }
        }
    }
}
@Composable
fun ChatBubbleText(message: ChatMessage, isDark: Boolean) {
    val alignment = if (message.isBot) Alignment.Start else Alignment.End
    val shape = if (message.isBot) RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    else RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    val backgroundColor = if (message.isBot)
        if (isDark) Color(0xFF121212).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.35f)
    else
        SaharaStrongGreen
    val textColor = if (message.isBot) MaterialTheme.colorScheme.onSurface else Color.White
    val timeColor = if (message.isBot)
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    else
        Color.White.copy(alpha = 0.75f)
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentWidth(alignment)
                .clip(shape)
                .background(backgroundColor)
                .border(
                    width = if (isDark) 1.dp else 1.5.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.4f), Color.Transparent, Color.White.copy(alpha = 0.1f))
                    ),
                    shape = shape
                )
                .padding(16.dp)
        ) {
            Column {
                Text(text = message.text, color = textColor, style = MaterialTheme.typography.bodyLarge, lineHeight = 22.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.getFormattedTime(),
                    color = timeColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
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
    val alignment = if (message.isBot) Alignment.Start else Alignment.End
    val backgroundColor = if (message.isBot) MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.6f else 0.9f) else SaharaStrongGreen
    val textColor = if (message.isBot) MaterialTheme.colorScheme.onSurface else Color.White
    val shape = if (message.isBot) RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp) else RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .wrapContentWidth(alignment)
                .clip(shape)
                .background(backgroundColor)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = textColor, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(0.4f, 0.8f, 0.6f, 1f, 0.5f, 0.9f, 0.3f).forEach { height ->
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height((24 * height).dp)
                                .background(textColor.copy(alpha = 0.7f), CircleShape)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    message.getFormattedTime(),
                    color = textColor.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp
                )
            }
        }
    }
}
@Composable
fun TypingIndicator(isDark: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val dot1 by infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(600, 0), RepeatMode.Reverse), label = "dot1")
    val dot2 by infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(600, 200), RepeatMode.Reverse), label = "dot2")
    val dot3 by infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(600, 400), RepeatMode.Reverse), label = "dot3")
    Box(
        modifier = Modifier
            .wrapContentSize()
            .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.6f else 0.9f))
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).offset { IntOffset(0, (-4 * dot1).dp.roundToPx()) }.background(SaharaStrongGreen, CircleShape))
            Box(modifier = Modifier.size(6.dp).offset { IntOffset(0, (-4 * dot2).dp.roundToPx()) }.background(SaharaStrongGreen, CircleShape))
            Box(modifier = Modifier.size(6.dp).offset { IntOffset(0, (-4 * dot3).dp.roundToPx()) }.background(SaharaStrongGreen, CircleShape))
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
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.6f else 0.85f),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            AnimatedVisibility(visible = isRecording) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(SaharaCoral))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "00:${recordingDuration.toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SaharaCoral
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        if (isEnglish) "Release to send" else "Bhejne ke liye chorein",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.5f else 1f),
                    border = BorderStroke(
                        1.dp,
                        if (input.isNotBlank()) SaharaStrongGreen
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = 120.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        BasicTextField(
                            value = input,
                            onValueChange = onInputChange,
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            cursorBrush = SolidColor(SaharaStrongGreen),
                            decorationBox = { innerTextField ->
                                if (input.isEmpty()) {
                                    Text(
                                        if (isEnglish) "Message SAHARA..." else "SAHARA ko message likhein...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                val inputIsBlank = input.isBlank()
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (input.isNotBlank() || isRecording) SaharaStrongGreen
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
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
                                modifier = Modifier.size(20.dp).offset(x = 2.dp)
                            )
                        } else {
                            val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
                                initialValue = 1f,
                                targetValue = if (isRecording) 1.2f else 1f,
                                animationSpec = infiniteRepeatable(
                                    tween(500, easing = FastOutSlowInEasing),
                                    RepeatMode.Reverse
                                ),
                                label = "micPulse"
                            )
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Hold to record",
                                tint = if (isRecording) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp).scale(pulse)
                            )
                        }
                    }
                }
            }
        }
    }
}