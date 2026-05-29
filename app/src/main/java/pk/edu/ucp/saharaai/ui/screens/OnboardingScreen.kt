package pk.edu.ucp.saharaai.ui.screens

import android.Manifest
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import pk.edu.ucp.saharaai.ui.components.*
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.util.PermissionCopy
import pk.edu.ucp.saharaai.util.rememberAppPermissionRequester
import pk.edu.ucp.saharaai.viewmodels.OnboardingViewModel

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    onNavigateToDashboard: () -> Unit,
    onNavigateBack: () -> Unit,
    isEnglish: Boolean = false,
    onboardingViewModel: OnboardingViewModel = viewModel()
) {
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

    val step = onboardingViewModel.step
    val ageGroup = onboardingViewModel.ageGroup
    val currentSituation = onboardingViewModel.currentSituation
    val selectedHelps = onboardingViewModel.selectedHelps
    val notificationsAllowed = onboardingViewModel.notificationsAllowed
    val locationAllowed = onboardingViewModel.locationAllowed
    val actigraphyAllowed = onboardingViewModel.actigraphyAllowed
    val selectedAvatarId = onboardingViewModel.selectedAvatarId
    val onboardingError = onboardingViewModel.errorMessage
    val notificationPermissionRequester = rememberAppPermissionRequester(
        permission = Manifest.permission.POST_NOTIFICATIONS,
        isEnglish = isEnglish,
        copy = PermissionCopy(
            deniedEn = "Notification permission was denied.",
            deniedUr = "Notifications ki ijazat nahi di gayi.",
            settingsEn = "Enable notifications in App settings to receive reminders.",
            settingsUr = "Reminders ke liye App settings mein notifications ki ijazat dein.",
        ),
        onGranted = { onboardingViewModel.notificationsAllowed = true },
        onDenied = { onboardingViewModel.notificationsAllowed = false },
    )
    val actigraphyPermissionRequester = rememberAppPermissionRequester(
        permission = Manifest.permission.ACTIVITY_RECOGNITION,
        isEnglish = isEnglish,
        copy = PermissionCopy(
            deniedEn = "Motion permission was denied.",
            deniedUr = "Motion ki ijazat nahi di gayi.",
            settingsEn = "Enable physical activity permission in App settings to estimate sleep automatically.",
            settingsUr = "Automatic neend estimate ke liye App settings mein physical activity ki ijazat dein.",
        ),
        onGranted = { onboardingViewModel.actigraphyAllowed = true },
        onDenied = { onboardingViewModel.actigraphyAllowed = false },
    )
    val locationPermissionRequester = rememberAppPermissionRequester(
        permission = Manifest.permission.ACCESS_FINE_LOCATION,
        isEnglish = isEnglish,
        copy = PermissionCopy(
            deniedEn = "Location permission was denied.",
            deniedUr = "Location ki ijazat nahi di gayi.",
            settingsEn = "Enable location in App settings for regional features.",
            settingsUr = "Regional features ke liye App settings mein location ki ijazat dein.",
        ),
        onGranted = { onboardingViewModel.locationAllowed = true },
        onDenied = { onboardingViewModel.locationAllowed = false },
    )

    val isNextEnabled = onboardingViewModel.isNextEnabled
    val headerTitle = onboardingViewModel.getHeaderTitle(isEnglish)
    val headerSubtitle = onboardingViewModel.getHeaderSubtitle(isEnglish)

    val blobMotion = rememberBackdropBlobMotion()

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
                Box(modifier = Modifier.size(350.dp).offset(x = (-80).dp, y = (-50).dp).primaryBlobMotion(blobMotion).background(Brush.radialGradient(listOf(blob1Color, Color.Transparent))))
                Box(modifier = Modifier.size(400.dp).align(Alignment.BottomEnd).offset(x = 100.dp, y = 50.dp).secondaryBlobMotion(blobMotion).background(Brush.radialGradient(listOf(blob2Color, Color.Transparent))))
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HazeBackButton(onClick = { if (step > 1) onboardingViewModel.previousStep() else onNavigateBack() }, hazeState = bgHazeState)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = if (isEnglish) "Create Account" else "Naya Account",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = primaryGreen
                            )
                        }
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
                                    1 -> StepOneAge(isEnglish, ageGroup, bgHazeState, softTextColor) { onboardingViewModel.ageGroup = it }
                                    2 -> StepTwoSituation(isEnglish, currentSituation, bgHazeState, softTextColor) { onboardingViewModel.currentSituation = it }
                                    3 -> StepThreeHelp(isEnglish, selectedHelps, bgHazeState, softTextColor) { help ->
                                        onboardingViewModel.toggleHelp(help)
                                    }
                                    4 -> StepFourPermissions(
                                        isE        = isEnglish,
                                        notif      = notificationsAllowed,
                                        location   = locationAllowed,
                                        actigraphy = actigraphyAllowed,
                                        h          = bgHazeState,
                                        stc        = softTextColor,
                                        onNotif    = { notificationPermissionRequester.request() },
                                        onLocation = { locationPermissionRequester.request() },
                                        onActigraphy = {
                                            actigraphyPermissionRequester.request()
                                        }
                                    )
                                    5 -> StepFiveAvatar(
                                        isE = isEnglish,
                                        selectedId = selectedAvatarId,
                                        h = bgHazeState,
                                        stc = softTextColor,
                                        onSelect = { onboardingViewModel.selectedAvatarId = it }
                                    )
                                }
                            }
                        }
                    }
                    if (onboardingError.isNotBlank()) {
                        Text(
                            text = onboardingError,
                            color = SaharaCoral,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (step > 1) SaharaButton(text = if (isEnglish) "Back" else "Wapas", onClick = { onboardingViewModel.previousStep() }, variant = ButtonVariant.OUTLINE, modifier = Modifier.weight(1f))
                        SaharaButton(
                            text = if (step == 5) (if(isEnglish) "Finish" else "Khatam") else (if (isEnglish) "Next" else "Aage"),
                            onClick = { onboardingViewModel.nextStep(onNavigateToDashboard) },
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
    notif: Boolean,
    location: Boolean,
    actigraphy: Boolean,
    h: HazeState,
    stc: Color,
    onNotif: () -> Unit,
    onLocation: () -> Unit,
    onActigraphy: () -> Unit
) {
    Column {
        PermissionRow(
            Icons.Default.LocationOn,
            if (isE) "Location" else "Location",
            if (isE) "Used for regional support and nearby help." else "Regional support aur nazdeeki madad ke liye.",
            location, onLocation, h, stc, isE
        )
        PermissionRow(
            Icons.Default.Notifications,
            if (isE) "Notifications" else "Notifications",
            if (isE) "Get alerts and reminders." else "Alerts aur reminders.",
            notif, onNotif, h, stc, isE
        )
        PermissionRow(
            Icons.Default.DirectionsWalk,
            if (isE) "Actigraphy" else "Actigraphy",
            if (isE) "Use motion for automatic sleep estimates." else "Neend estimate ke liye motion use hoti hai.",
            actigraphy, onActigraphy, h, stc, isE
        )
    }
}

private data class AvatarPreset(val id: String, val drawableRes: Int)

private fun avatarPresets(): List<AvatarPreset> {
    return listOf(
        AvatarPreset("avatar_01", pk.edu.ucp.saharaai.R.drawable.av_markhor),
        AvatarPreset("avatar_02", pk.edu.ucp.saharaai.R.drawable.av_indus_river_dolphin),
        AvatarPreset("avatar_03", pk.edu.ucp.saharaai.R.drawable.av_snow_leopard),
        AvatarPreset("avatar_04", pk.edu.ucp.saharaai.R.drawable.av_zebu),
        AvatarPreset("avatar_05", pk.edu.ucp.saharaai.R.drawable.av_himalayan_ibex),
        AvatarPreset("avatar_06", pk.edu.ucp.saharaai.R.drawable.av_mugger_crocodile),
        AvatarPreset("avatar_07", pk.edu.ucp.saharaai.R.drawable.av_pangolin),
        AvatarPreset("avatar_08", pk.edu.ucp.saharaai.R.drawable.av_chinkara),
        AvatarPreset("avatar_09", pk.edu.ucp.saharaai.R.drawable.av_sivatherium),
        AvatarPreset("avatar_10", pk.edu.ucp.saharaai.R.drawable.av_indohyus),
        AvatarPreset("avatar_11", pk.edu.ucp.saharaai.R.drawable.av_stegodon),
        AvatarPreset("avatar_12", pk.edu.ucp.saharaai.R.drawable.av_aurochs),
        AvatarPreset("avatar_13", pk.edu.ucp.saharaai.R.drawable.av_sindhu_cheetah),
        AvatarPreset("avatar_14", pk.edu.ucp.saharaai.R.drawable.av_pallass_cat),
        AvatarPreset("avatar_15", pk.edu.ucp.saharaai.R.drawable.av_flying_squirrel),
        AvatarPreset("avatar_16", pk.edu.ucp.saharaai.R.drawable.av_bijju),
        AvatarPreset("avatar_17", pk.edu.ucp.saharaai.R.drawable.av_gharial),
        AvatarPreset("avatar_18", pk.edu.ucp.saharaai.R.drawable.av_argali),
        AvatarPreset("avatar_19", pk.edu.ucp.saharaai.R.drawable.av_himalayan_red_panda),
        AvatarPreset("avatar_20", pk.edu.ucp.saharaai.R.drawable.av_sindhi_fish_cat),
        AvatarPreset("avatar_21", pk.edu.ucp.saharaai.R.drawable.av_blackbuck),
        AvatarPreset("avatar_22", pk.edu.ucp.saharaai.R.drawable.av_barasinga),
        AvatarPreset("avatar_23", pk.edu.ucp.saharaai.R.drawable.av_himalayan_monal),
        AvatarPreset("avatar_24", pk.edu.ucp.saharaai.R.drawable.av_lammergeier),
        AvatarPreset("avatar_25", pk.edu.ucp.saharaai.R.drawable.av_golden_mahseer),
        AvatarPreset("avatar_26", pk.edu.ucp.saharaai.R.drawable.av_harappan_unicorn),
        AvatarPreset("avatar_27", pk.edu.ucp.saharaai.R.drawable.av_harappan_tiger),
        AvatarPreset("avatar_28", pk.edu.ucp.saharaai.R.drawable.av_babbar_sher),
        AvatarPreset("avatar_29", pk.edu.ucp.saharaai.R.drawable.av_bully_kutta),
        AvatarPreset("avatar_30", pk.edu.ucp.saharaai.R.drawable.av_bhains)
    )
}

@Composable
fun StepFiveAvatar(isE: Boolean, selectedId: String, h: HazeState, stc: Color, onSelect: (String) -> Unit) {
    val presets = remember { avatarPresets() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        presets.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { preset ->
                    val selected = selectedId == preset.id
                    SaharaCard(
                        variant = if (selected) CardVariant.SELECTED_GLASS else CardVariant.GLASS,
                        hazeState = h,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) SaharaStrongGreen else stc.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { onSelect(preset.id) }
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize(0.85f)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = preset.drawableRes),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            if (selected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SaharaStrongGreen,
                                    modifier = Modifier.align(Alignment.TopEnd).size(18.dp)
                                )
                            }
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text(
            text = if (isE) "You can request a custom image from Profile after onboarding." else "Onboarding ke baad Profile se custom image request kar sakte hain.",
            style = MaterialTheme.typography.bodySmall,
            color = stc.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
}
