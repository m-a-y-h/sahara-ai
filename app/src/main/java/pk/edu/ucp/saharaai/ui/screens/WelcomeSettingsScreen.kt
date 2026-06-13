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
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.HazeColorEffect
import pk.edu.ucp.saharaai.BuildConfig
import pk.edu.ucp.saharaai.R
import pk.edu.ucp.saharaai.ui.components.*
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.viewmodels.WelcomeSettingsViewModel

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun WelcomeSettingsScreenPreview() {
    WelcomeSettingsScreen(
        isEnglish = true,
        onLanguageChange = {},
        onNavigateBack = {},
        onNavigateToNgo = {},
        onNavigateToAdmin = {},
        onNavigateToCounselor = {},
        onNavigateToRegistration = {}
    )
}

@Composable
fun WelcomeSettingsScreen(
    isEnglish: Boolean,
    onLanguageChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToNgo: (String) -> Unit,
    onNavigateToAdmin: () -> Unit,
    onNavigateToCounselor: (String) -> Unit,
    onNavigateToRegistration: (String) -> Unit,
    settingsViewModel: WelcomeSettingsViewModel = viewModel()
) {
    val isDark = isSystemInDarkTheme()
    val bgHazeState = remember { HazeState() }
    val rootHazeState = remember { HazeState() }

    val bgGradient = if (isDark) {
        listOf(SaharaStrongGreen.copy(alpha = 0.2f), MaterialTheme.colorScheme.background.copy(alpha = 0.6f), MaterialTheme.colorScheme.background)
    } else {
        listOf(SaharaStrongGreen.copy(alpha = 0.25f), SaharaPeach.copy(alpha = 0.1f), MaterialTheme.colorScheme.background.copy(alpha = 0.2f))
    }
    val softTextColor = if (isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f)

    var showKeyDialog by remember { mutableStateOf(false) }
    var dialogType by remember { mutableStateOf("ORGANIZATION") }

    Box(modifier = Modifier.fillMaxSize()) {

        ScreenBackdrop(bgHazeState, bgGradient)

        Column(modifier = Modifier.fillMaxSize()) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HazeBackButton(onClick = onNavigateBack, hazeState = bgHazeState)
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val annotatedText = buildAnnotatedString {
                        if (isEnglish) {
                            append("Enter as ")
                            withLink(
                                LinkAnnotation.Clickable(
                                    tag = "organization",
                                    styles = TextLinkStyles(style = SpanStyle(color = SaharaStrongGreen, fontWeight = FontWeight.Bold)),
                                    linkInteractionListener = { dialogType = "ORGANIZATION"; showKeyDialog = true }
                                )
                            ) { append("NGO / Admin") }
                            append(" or ")
                            withLink(
                                LinkAnnotation.Clickable(
                                    tag = "counselor",
                                    styles = TextLinkStyles(style = SpanStyle(color = SaharaStrongGreen, fontWeight = FontWeight.Bold)),
                                    linkInteractionListener = { dialogType = "COUNSELOR"; showKeyDialog = true }
                                )
                            ) { append("Counselor") }
                        } else {
                            append("Dakhil: ")
                            withLink(
                                LinkAnnotation.Clickable(
                                    tag = "organization",
                                    styles = TextLinkStyles(style = SpanStyle(color = SaharaStrongGreen, fontWeight = FontWeight.Bold)),
                                    linkInteractionListener = { dialogType = "ORGANIZATION"; showKeyDialog = true }
                                )
                            ) { append("NGO / Admin") }
                            append(" ya ")
                            withLink(
                                LinkAnnotation.Clickable(
                                    tag = "counselor",
                                    styles = TextLinkStyles(style = SpanStyle(color = SaharaStrongGreen, fontWeight = FontWeight.Bold)),
                                    linkInteractionListener = { dialogType = "COUNSELOR"; showKeyDialog = true }
                                )
                            ) { append("Counselor") }
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
        }

        if (showKeyDialog) {
            SecurityKeyOverlay(
                isEnglish = isEnglish,
                type = dialogType,
                hazeState = rootHazeState,
                onDismiss = { showKeyDialog = false },
                onApply = {
                    showKeyDialog = false
                    onNavigateToRegistration(if (dialogType == "ORGANIZATION") "NGO" else "COUNSELOR")
                },
                onVerify = { key, onSuccess, onFailure ->
                    settingsViewModel.verifyKey(dialogType, key, isEnglish, onSuccess, onFailure)
                },
                onSuccess = { validatedKey ->
                    showKeyDialog = false
                    if (dialogType == "ORGANIZATION" &&
                        BuildConfig.ADMIN_KEY.isNotBlank() &&
                        validatedKey == BuildConfig.ADMIN_KEY
                    ) onNavigateToAdmin()
                    else if (dialogType == "ORGANIZATION") onNavigateToNgo(validatedKey)
                    else onNavigateToCounselor(validatedKey)
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
    onApply: () -> Unit,
    onVerify: (String, (String) -> Unit, (String) -> Unit) -> Unit,
    onSuccess: (String) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    var keyInput   by remember { mutableStateOf("") }
    var isError    by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    var errorMsg   by remember { mutableStateOf("") }

    val title = if (type == "ORGANIZATION")
        (if (isEnglish) "NGO / Admin Verification" else "NGO / Admin Tasdeeq")
    else
        (if (isEnglish) "Counselor Verification" else "Counselor ki Tasdeeq")

    val textColor          = if (isDark) Color.White.copy(alpha = 0.95f) else Color(0xFF1A1A1A)
    val dynamicBorderColor = if (isError) SaharaCoral else Color.Transparent
    val glassTint          = if (isDark) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.6f)

    fun verify() {
        val trimmed = keyInput.trim()
        if (trimmed.isBlank()) { isError = true; errorMsg = if (isEnglish) "Please enter a key." else "Key darj karein."; return }
        isChecking = true
        isError    = false
        errorMsg   = ""
        onVerify(
            trimmed,
            { validatedKey ->
                isChecking = false
                onSuccess(validatedKey)
            },
            { message ->
                isChecking = false
                isError    = true
                errorMsg   = message
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (isDark) 0.6f else 0.3f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
        ) {
            Image(
                painter = painterResource(id = R.drawable.sahara_bg5),
                contentDescription = null,
                modifier = Modifier.matchParentSize().clip(RoundedCornerShape(24.dp)).hazeSource(state = hazeState),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(24.dp))
                    .hazeEffect(state = hazeState) {
                        blurEffect { blurRadius = 12.dp; colorEffects = listOf(HazeColorEffect.tint(glassTint)) }
                    }
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(listOf(
                            if (isDark) Color.White.copy(0.3f) else Color.White.copy(0.8f),
                            if (isDark) Color.White.copy(0.05f) else Color.White.copy(0.3f)
                        )),
                        shape = RoundedCornerShape(24.dp)
                    )
            )

            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SaharaStrongGreen)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = textColor)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (type == "COUNSELOR")
                        (if (isEnglish) "Enter your unique counselor key provided by your admin." else "Admin ki taraf se di gayi apni unique counselor key darj karein.")
                    else
                        (if (isEnglish) "Enter the admin key or your issued NGO key." else "Admin key ya issue ki hui NGO key darj karein."),
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
                        onValueChange = { keyInput = it; isError = false; errorMsg = "" },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor, fontSize = 15.sp),
                        singleLine = true,
                        cursorBrush = SolidColor(SaharaStrongGreen),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (keyInput.isEmpty()) {
                                    Text(
                                        text = if (type == "COUNSELOR")
                                            (if (isEnglish) "Paste your counselor key here..." else "Yahan apni counselor key darj karein...")
                                        else
                                            (if (isEnglish) "Enter NGO / admin key..." else "NGO / admin key darj karein..."),
                                        style = MaterialTheme.typography.bodyMedium.copy(color = textColor.copy(0.4f), fontSize = 15.sp)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                if (isError && errorMsg.isNotBlank()) {
                    Text(errorMsg, color = SaharaCoral, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp, start = 8.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (isChecking) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = SaharaStrongGreen, strokeWidth = 2.dp)
                            Text(
                                text = if (isEnglish) "Verifying key..." else "Key check ho rahi hai...",
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(0.7f)
                            )
                        }
                    }
                } else {
                    SaharaButton(
                        text = if (isEnglish) "Verify & Enter" else "Tasdeeq Karein",
                        onClick = { verify() },
                        variant = ButtonVariant.DEFAULT,
                        isFullWidth = true,
                        hazeState = hazeState,
                        isEnglish = isEnglish
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isEnglish)
                        "Need access? Submit documents for manual review."
                    else
                        "Access chahiye? Review ke liye documents dein.",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.72f),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Text(
                    text = if (type == "COUNSELOR") {
                        if (isEnglish) "Apply as Counselor" else "Darkhwast bā-hesiyat-e-Counselor"
                    } else {
                        if (isEnglish) "Apply as NGO" else "Darkhwast bā-hesiyat-e-NGO"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = SaharaStrongGreen,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onApply,
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}
