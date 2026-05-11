package pk.edu.ucp.saharaai.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.HazeColorEffect
import pk.edu.ucp.saharaai.BuildConfig
import pk.edu.ucp.saharaai.R
import pk.edu.ucp.saharaai.ui.components.*
import pk.edu.ucp.saharaai.ui.theme.*

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun WelcomeSettingsScreenPreview() {
    WelcomeSettingsScreen(
        isEnglish = true,
        onLanguageChange = {},
        onNavigateBack = {},
        onNavigateToNgo = {},
        onNavigateToCounselor = {}
    )
}

@Composable
fun WelcomeSettingsScreen(
    isEnglish: Boolean,
    onLanguageChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToNgo: () -> Unit,
    onNavigateToCounselor: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val bgHazeState = remember { HazeState() }
    val rootHazeState = remember { HazeState() }

    val bgGradient = if (isDark) {
        listOf(SaharaStrongGreen.copy(alpha = 0.2f), MaterialTheme.colorScheme.background.copy(alpha = 0.6f), MaterialTheme.colorScheme.background)
    } else {
        listOf(SaharaStrongGreen.copy(alpha = 0.25f), SaharaPeach.copy(alpha = 0.1f), MaterialTheme.colorScheme.background.copy(alpha = 0.2f))
    }
    val blob1Color = SaharaStrongGreen.copy(alpha = if (isDark) 0.25f else 0.15f)
    val blob2Color = SaharaSky.copy(alpha = if (isDark) 0.2f else 0.18f)

    val softTextColor = if (isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f)

    var showKeyDialog by remember { mutableStateOf(false) }
    var dialogType by remember { mutableStateOf("NGO") }

    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val blobRotation by infiniteTransition.animateFloat(
        initialValue = -10f, targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(7000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "rotation"
    )
    val blobScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(5000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "scale"
    )

    Box(modifier = Modifier.fillMaxSize()) {

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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SaharaStrongGreen)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEnglish) "App Settings" else "App ki Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = SaharaStrongGreen,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 24.dp, bottom = 24.dp)
            ) {
                if (!showKeyDialog) {
                    Text(
                        text = if (isEnglish) "Language" else "Zaban",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = softTextColor,
                        modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
                    )

                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingsOptionCard(
                            title = if (!isEnglish) "Urdu (Roman)" else "Urdu (Roman)",
                            subtitle = "اردو",
                            isSelected = !isEnglish,
                            hazeState = bgHazeState,
                            softTextColor = softTextColor
                        ) { onLanguageChange(false) }

                        SettingsOptionCard(
                            title = if (!isEnglish) "Angrezi" else "English",
                            subtitle = if (!isEnglish) "انگریزی" else "English",
                            isSelected = isEnglish,
                            hazeState = bgHazeState,
                            softTextColor = softTextColor
                        ) { onLanguageChange(true) }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                val annotatedText = buildAnnotatedString {
                    if (isEnglish) {
                        append("Are you an ")
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = "ngo",
                                styles = TextLinkStyles(style = SpanStyle(color = SaharaStrongGreen, fontWeight = FontWeight.Bold)),
                                linkInteractionListener = { dialogType = "NGO"; showKeyDialog = true }
                            )
                        ) { append("NGO") }
                        append(" or a ")
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = "counselor",
                                styles = TextLinkStyles(style = SpanStyle(color = SaharaStrongGreen, fontWeight = FontWeight.Bold)),
                                linkInteractionListener = { dialogType = "COUNSELOR"; showKeyDialog = true }
                            )
                        ) { append("Counselor") }
                        append("?")
                    } else {
                        append("Kya aap ")
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = "ngo",
                                styles = TextLinkStyles(style = SpanStyle(color = SaharaStrongGreen, fontWeight = FontWeight.Bold)),
                                linkInteractionListener = { dialogType = "NGO"; showKeyDialog = true }
                            )
                        ) { append("NGO") }
                        append(" ya ")
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = "counselor",
                                styles = TextLinkStyles(style = SpanStyle(color = SaharaStrongGreen, fontWeight = FontWeight.Bold)),
                                linkInteractionListener = { dialogType = "COUNSELOR"; showKeyDialog = true }
                            )
                        ) { append("Counselor") }
                        append(" hain?")
                    }
                }

                Text(
                    text = annotatedText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = softTextColor,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (showKeyDialog) {
            SecurityKeyOverlay(
                isEnglish = isEnglish,
                type = dialogType,
                hazeState = rootHazeState,
                onDismiss = { showKeyDialog = false },
                onSuccess = {
                    showKeyDialog = false
                    if (dialogType == "NGO") onNavigateToNgo() else onNavigateToCounselor()
                }
            )
        }
    }
}

@Composable
fun SettingsOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    hazeState: HazeState,
    softTextColor: Color,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val finalTitleColor = if (isDark) {
        if (isSelected) Color.White else softTextColor
    } else {
        if (isSelected) SaharaStrongGreen else softTextColor
    }

    val finalSubColor = if (isDark) {
        if (isSelected) Color.White.copy(0.7f) else softTextColor.copy(0.5f)
    } else {
        if (isSelected) SaharaStrongGreen.copy(0.7f) else softTextColor.copy(0.7f)
    }

    SaharaCard(
        variant = CardVariant.GLASS,
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .heightIn(min = 56.dp)
            .then(
                if (isSelected) Modifier.border(1.5.dp, SaharaStrongGreen, RoundedCornerShape(24.dp))
                else Modifier
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    color = finalTitleColor,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, color = finalSubColor)
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = SaharaStrongGreen, unselectedColor = softTextColor.copy(0.3f))
            )
        }
    }
}

@Composable
fun SecurityKeyOverlay(
    isEnglish: Boolean,
    type: String,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    var keyInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    val expectedKey = if (type == "NGO") BuildConfig.NGO_KEY else BuildConfig.COUNSELOR_KEY
    val title = if (type == "NGO") (if (isEnglish) "NGO Verification" else "NGO ki Tasdeeq") else (if (isEnglish) "Counselor Verification" else "Counselor ki Tasdeeq")

    val textColor = if (isDark) Color.White.copy(alpha = 0.95f) else Color(0xFF1A1A1A)
    val dynamicBorderColor = if (isError) SaharaCoral else Color.Transparent

    val glassTint = if (isDark) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.6f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (isDark) 0.6f else 0.3f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Image(
                painter = painterResource(id = R.drawable.sahara_bg5),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(24.dp))
                    .hazeSource(state = hazeState),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(24.dp))
                    .hazeEffect(state = hazeState) {
                        blurEffect {
                            blurRadius = 12.dp
                            colorEffects = listOf(HazeColorEffect.tint(glassTint))
                        }
                    }
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                if (isDark) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.8f),
                                if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.3f)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
            )

            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SaharaStrongGreen
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = textColor)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isEnglish) "Please enter your provided encryption key to access the secure dashboard." else "Mehfooz dashboard tak rasai ke liye apni encryption key darj karein.",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.85f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                val inputBgColor = if (isDark) Color.Black.copy(0.4f) else Color.White.copy(0.7f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(inputBgColor, RoundedCornerShape(16.dp))
                        .border(1.5.dp, dynamicBorderColor, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    BasicTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it; isError = false },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor, fontSize = 14.sp),
                        minLines = 2,
                        maxLines = 4,
                        cursorBrush = SolidColor(SaharaStrongGreen),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (keyInput.isEmpty()) {
                                    Text(
                                        text = if (isEnglish) "Paste key here..." else "Yahan key paste karein...",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = textColor.copy(0.5f), fontSize = 14.sp)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                if (isError) {
                    Text(
                        text = if (isEnglish) "Invalid security key." else "Security key galat hai.",
                        color = SaharaCoral,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 8.dp, start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                SaharaButton(
                    text = if (isEnglish) "Verify & Login" else "Tasdeeq Karein",
                    onClick = {
                        if (keyInput == expectedKey || keyInput == BuildConfig.BYPASS_CODE) {
                            onSuccess()
                        } else {
                            isError = true
                        }
                    },
                    variant = ButtonVariant.DEFAULT,
                    isFullWidth = true,
                    hazeState = hazeState,
                    isEnglish = isEnglish
                )
            }
        }
    }
}