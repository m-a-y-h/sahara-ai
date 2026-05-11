package pk.edu.ucp.saharaai.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import pk.edu.ucp.saharaai.BuildConfig
import pk.edu.ucp.saharaai.ui.components.*
import pk.edu.ucp.saharaai.ui.theme.*

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(onNavigateToDashboard: () -> Unit, isEnglish: Boolean = false) {
    val isDark = isSystemInDarkTheme()
    val bgHazeState = remember { HazeState() }
    val rootHazeState = remember { HazeState() }
    val softTextColor = if (isDark) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.70f)
    val primaryGreen = if (isDark) SaharaStrongGreen else SaharaGreen

    val bgGradient = if (isDark) {
        listOf(SaharaStrongGreen.copy(alpha = 0.2f), MaterialTheme.colorScheme.background.copy(alpha = 0.6f), MaterialTheme.colorScheme.background)
    } else {
        listOf(SaharaStrongGreen.copy(alpha = 0.25f), SaharaPeach.copy(alpha = 0.1f), MaterialTheme.colorScheme.background.copy(alpha = 0.2f))
    }
    val blob1Color = SaharaStrongGreen.copy(alpha = if (isDark) 0.25f else 0.15f)
    val blob2Color = SaharaSky.copy(alpha = if (isDark) 0.2f else 0.18f)

    var step by rememberSaveable { mutableIntStateOf(1) }
    var ageGroup by rememberSaveable { mutableStateOf("") }
    var currentSituation by rememberSaveable { mutableStateOf("") }
    var selectedHelps by rememberSaveable { mutableStateOf(setOf<String>()) }
    var cameraAllowed by rememberSaveable { mutableStateOf(false) }
    var micAllowed by rememberSaveable { mutableStateOf(false) }
    var notificationsAllowed by rememberSaveable { mutableStateOf(false) }
    var phoneAllowed by rememberSaveable { mutableStateOf(false) }
    var verificationCode by rememberSaveable { mutableStateOf("") }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraAllowed = it }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micAllowed = it }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAllowed = it }
    val phoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { phoneAllowed = it }

    val isNextEnabled = when (step) {
        1 -> ageGroup.isNotEmpty()
        2 -> currentSituation.isNotEmpty()
        3 -> selectedHelps.size >= 2
        5 -> verificationCode == "999999" || verificationCode == (BuildConfig.BYPASS_CODE ?: "999999")
        else -> true
    }

    val headerTitle = when (step) {
        1 -> if (isEnglish) "Your Age Group?" else "Aapka Age Group?"
        2 -> if (isEnglish) "Current Situation" else "Halaat"
        3 -> if (isEnglish) "What Help Do You Need?" else "Kaunsi Help Chahiye?"
        4 -> if (isEnglish) "Permissions" else "Ijazat"
        5 -> if (isEnglish) "Verification" else "Tasdeeq"
        else -> ""
    }
    val headerSubtitle = when (step) {
        1 -> if (isEnglish) "Customizes your journey." else "Behtar madad ke liye age batayein."
        2 -> if (isEnglish) "Tell us your focus." else "Yeh completely anonymous hai."
        3 -> if (isEnglish) "Select at least 2 categories." else "Kam az kam 2 options select karein."
        4 -> if (isEnglish) "Required for secure features." else "Behtar tajurbay ke liye ijazat dein."
        5 -> if (isEnglish) "Enter code from your email." else "Email per bheja gaya code darj karein."
        else -> ""
    }

    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val blobRotation by infiniteTransition.animateFloat(
        initialValue = -10f, targetValue = 10f,
        animationSpec = infiniteRepeatable(animation = tween(7000, easing = EaseInOutSine), repeatMode = RepeatMode.Reverse),
        label = "blobRotation"
    )
    val blobScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(animation = tween(5000, easing = EaseInOutSine), repeatMode = RepeatMode.Reverse),
        label = "blobScale"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = rootHazeState)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = bgHazeState)
                    .background(Brush.verticalGradient(bgGradient))
            ) {
                Box(modifier = Modifier.size(350.dp).offset(x = (-80).dp, y = (-50).dp).rotate(blobRotation).scale(blobScale).background(Brush.radialGradient(listOf(blob1Color, Color.Transparent))))
                Box(modifier = Modifier.size(400.dp).align(Alignment.BottomEnd).offset(x = 100.dp, y = 50.dp).rotate(-blobRotation).scale(blobScale).background(Brush.radialGradient(listOf(blob2Color, Color.Transparent))))
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "Create Account" else "Naya Account",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = primaryGreen
                        )
                        Text(
                            text = "$step/5",
                            color = primaryGreen,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .navigationBarsPadding()
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { step.toFloat() / 5f },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                        color = SaharaStrongGreen,
                        trackColor = SaharaGreenLight.copy(alpha = 0.4f)
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    StepHeader(title = headerTitle, subtitle = headerSubtitle, hazeState = bgHazeState, textColor = softTextColor)
                    Spacer(modifier = Modifier.height(16.dp))

                    Box(modifier = Modifier.weight(1f).fillMaxWidth().graphicsLayer { clip = true }) {
                        AnimatedContent(
                            targetState = step,
                            transitionSpec = {
                                fadeIn(tween(210, delayMillis = 90))
                                    .togetherWith(fadeOut(tween(150)))
                            },
                            label = "stepAnim"
                        ) { targetStep ->
                            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                when (targetStep) {
                                    1 -> StepOneAge(isEnglish, ageGroup, bgHazeState, softTextColor) { ageGroup = it }
                                    2 -> StepTwoSituation(isEnglish, currentSituation, bgHazeState, softTextColor) { currentSituation = it }
                                    3 -> StepThreeHelp(isEnglish, selectedHelps, bgHazeState, softTextColor) { help ->
                                        selectedHelps = if (selectedHelps.contains(help)) selectedHelps - help else selectedHelps + help
                                    }
                                    4 -> StepFourPermissions(isEnglish, cameraAllowed, micAllowed, notificationsAllowed, phoneAllowed, bgHazeState, softTextColor,
                                        { cameraLauncher.launch(Manifest.permission.CAMERA) },
                                        { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                        { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else notificationsAllowed = true },
                                        { phoneLauncher.launch(Manifest.permission.READ_PHONE_STATE) }
                                    )
                                    5 -> StepFiveVerification(isEnglish, verificationCode, bgHazeState, softTextColor) { verificationCode = it }
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (step > 1) SaharaButton(text = if (isEnglish) "Back" else "Wapas", onClick = { step-- }, variant = ButtonVariant.OUTLINE, modifier = Modifier.weight(1f))
                        SaharaButton(
                            text = if (step == 5) (if(isEnglish) "Finish" else "Khatam") else (if (isEnglish) "Next" else "Aage"),
                            onClick = {
                                if (step < 5) step++
                                else {
                                    GlobalAppState.isMinor = (ageGroup == "18 se Kam")
                                    NotificationManager.logWelcome()
                                    onNavigateToDashboard()
                                }
                            },
                            variant = ButtonVariant.SAHARASTRONGGREENGLASS,
                            enabled = isNextEnabled,
                            modifier = Modifier.weight(if (step > 1) 1f else 2f).graphicsLayer { alpha = if (isNextEnabled) 1f else 0.45f },
                            hazeState = bgHazeState,
                            isEnglish = isEnglish
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    hazeState: HazeState,
    softTextColor: Color,
    isCheckbox: Boolean = false,
    onClick: () -> Unit
) {
    val finalTitleColor = softTextColor
    val finalSubColor = softTextColor.copy(alpha = 0.7f)

    val borderModifier = if (isSelected) {
        Modifier.border(1.5.dp, SaharaStrongGreen, RoundedCornerShape(16.dp))
    } else {
        Modifier
    }

    SaharaCard(
        variant = CardVariant.GLASS,
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .height(76.dp)
            .then(borderModifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 24.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = finalTitleColor,
                    style = MaterialTheme.typography.titleSmall
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = finalSubColor
                    )
                )
            }

            if (isCheckbox) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = SaharaStrongGreen,
                        uncheckedColor = softTextColor.copy(0.3f)
                    )
                )
            } else {
                RadioButton(
                    selected = isSelected,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = SaharaStrongGreen,
                        unselectedColor = softTextColor.copy(0.3f)
                    )
                )
            }
        }
    }
}

@Composable fun StepOneAge(isE: Boolean, s: String, h: HazeState, stc: Color, onS: (String) -> Unit) {
    Column {
        val options = listOf(
            Triple("18 se Kam", if(isE)"Under 18" else "18 se Kam", if(isE)"Minor protection active" else "Hifazat enable hai"),
            Triple("18 - 24 Saal", if(isE)"18-24 Years" else "18 - 24 Saal", if(isE)"Young adult support" else "No-jawan sathi"),
            Triple("25 - 35 Saal", if(isE)"25-35 Years" else "25 - 35 Saal", if(isE)"Adult resources" else "Baaligh resources"),
            Triple("35+ Saal", if(isE)"35+ Years" else "35+ Saal", if(isE)"Mature support" else "Behtar mashwara")
        )
        options.forEach { (id, t, sb) -> OptionCard(t, sb, s == id, h, stc) { onS(id) } }

        if (s == "18 se Kam") {
            Spacer(modifier = Modifier.height(10.dp))
            SaharaCard(
                variant = CardVariant.GLASS,
                hazeState = h,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 76.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(horizontal = 24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = SaharaPeach,
                        modifier = Modifier.size(22.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isE) "Minor Protection Active" else "Minor Protection Active",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = SaharaPeach,
                            style = MaterialTheme.typography.titleSmall
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = if (isE) "Content is age-appropriate." else "Aapke liye special safeguards.",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = stc.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRow(icon: ImageVector, title: String, desc: String, allowed: Boolean, onAllow: () -> Unit, hazeState: HazeState, stc: Color, isE: Boolean) {
    SaharaCard(
        variant = CardVariant.GLASS,
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .height(76.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().fillMaxHeight()
        ) {
            Spacer(modifier = Modifier.width(14.dp))
            Icon(icon, null, tint = SaharaStrongGreen, modifier = Modifier.size(24.dp))

            Spacer(modifier = Modifier.width(18.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = stc,
                    style = MaterialTheme.typography.titleSmall
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = stc.copy(0.5f),
                    maxLines = 1
                )
            }

            if (allowed) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SaharaStrongGreen,
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(28.dp)
                )
            } else {
                SaharaButton(
                    text = if(isE) "Allow" else "Ijazat",
                    onClick = onAllow,
                    variant = ButtonVariant.SAHARASTRONGGREENGLASS,
                    modifier = Modifier.width(96.dp).height(40.dp).padding(end = 16.dp),
                    hazeState = hazeState,
                    isEnglish = isE
                )
            }
        }
    }
}

@Composable
fun StepFiveVerification(isE: Boolean, code: String, h: HazeState, stc: Color, onC: (String) -> Unit) {
    var timeLeft by remember { mutableIntStateOf(30) }
    LaunchedEffect(timeLeft) {
        if (timeLeft > 0) {
            delay(1000)
            timeLeft--
        }
    }
    val isSuccess = code == "999999" || code == (BuildConfig.BYPASS_CODE ?: "999999")
    val isError = code.length == 6 && !isSuccess
    val dynamicColor = when {
        isSuccess -> SaharaStrongGreen
        isError -> SaharaCoral
        else -> Color.Transparent
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SaharaCard(
            variant = CardVariant.GLASS,
            hazeState = h,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .border(1.5.dp, dynamicColor, RoundedCornerShape(24.dp))
        ) {
            BasicTextField(
                value = code,
                onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onC(it) },
                modifier = Modifier.fillMaxSize(),
                textStyle = TextStyle(
                    textAlign = TextAlign.Center,
                    color = if (isError) SaharaCoral else SaharaStrongGreen,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 12.sp,
                    fontSize = 26.sp
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                cursorBrush = SolidColor(SaharaStrongGreen),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        if (code.isEmpty()) {
                            Text(
                                "□ □ □ □ □ □",
                                style = TextStyle(
                                    textAlign = TextAlign.Center,
                                    letterSpacing = 8.sp,
                                    fontSize = 22.sp,
                                    color = stc.copy(0.3f)
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }

        SaharaButton(
            text = if (timeLeft > 0) (if (isE) "Resend in ${timeLeft}s" else "Code Dobara (${timeLeft}s)")
            else (if (isE) "Resend Code" else "Dobara Code Bhejein"),
            onClick = { if (timeLeft == 0) timeLeft = 30 },
            variant = if (timeLeft > 0) ButtonVariant.GLASS else ButtonVariant.SAHARASTRONGGREENGLASS,
            isFullWidth = true,
            hazeState = h,
            isEnglish = isE
        )
    }
}

@Composable
fun StepHeader(title: String, subtitle: String, hazeState: HazeState, textColor: Color) {
    SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth().height(160.dp)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, color = textColor)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = textColor.copy(alpha = 0.6f), textAlign = TextAlign.Center)
        }
    }
}

@Composable fun StepTwoSituation(isE: Boolean, s: String, h: HazeState, stc: Color, onS: (String) -> Unit) {
    Column {
        val options = listOf(
            Triple("Exploring", if(isE)"Just Exploring" else "Sirf Dekh Raha Hun", if(isE)"Need information" else "Information chahiye"),
            Triple("Self", if(isE)"Concerned About Myself" else "Apne Liye Pareshan", if(isE)"Self-assessment" else "Self-assessment"),
            Triple("Recovery", if(isE)"In Recovery" else "Recovery Mein", if(isE)"Need support" else "Support chahiye"),
            Triple("HelpOther", if(isE)"Helping Someone" else "Kisi Ki Madad", if(isE)"Family or Friend" else "Family/Friend")
        )
        options.forEach { (id, t, sb) -> OptionCard(t, sb, s == id, h, stc) { onS(id) } }
    }
}

@Composable fun StepThreeHelp(isE: Boolean, s: Set<String>, h: HazeState, stc: Color, onT: (String) -> Unit) {
    Column {
        val options = listOf(
            Triple("AI Chatbot", "AI Chatbot", if(isE)"24/7 support" else "24/7 madad"),
            Triple("Counselor", "Professional Counselor", if(isE)"Anonymous chat" else "Anonymous guftagu"),
            Triple("Tracker", "Recovery Tracker", if(isE)"Progress tracking" else "Progress tracking"),
            Triple("Community", "Community Support", if(isE)"Peer stories" else "Peer stories"),
            Triple("Resources", "Educational Resources", if(isE)"Articles & tips" else "Articles aur tips")
        )
        options.forEach { (id, t, sb) -> OptionCard(t, sb, s.contains(id), h, stc, true) { onT(id) } }
    }
}

@Composable
fun StepFourPermissions(
    isE: Boolean,
    cam: Boolean,
    mic: Boolean,
    notif: Boolean,
    phone: Boolean,
    h: HazeState,
    stc: Color,
    onCam: () -> Unit,
    onMic: () -> Unit,
    onNotif: () -> Unit,
    onPhone: () -> Unit
) {
    Column {
        PermissionRow(
            Icons.Default.CameraAlt,
            if (isE) "Camera" else "Camera",
            if (isE) "Required for video calls." else "Video calls ke liye.",
            cam, onCam, h, stc, isE
        )
        PermissionRow(
            Icons.Default.Mic,
            if (isE) "Microphone" else "Microphone",
            if (isE) "Required for voice chat." else "Voice chat ke liye.",
            mic, onMic, h, stc, isE
        )
        PermissionRow(
            Icons.Default.Notifications,
            if (isE) "Notifications" else "Notifications",
            if (isE) "Get alerts and reminders." else "Alerts aur reminders.",
            notif, onNotif, h, stc, isE
        )
        PermissionRow(
            Icons.Default.Phone,
            if (isE) "Phone State" else "Phone State",
            if (isE) "Manage calls during session." else "Call sessions ke liye.",
            phone, onPhone, h, stc, isE
        )
    }
}
