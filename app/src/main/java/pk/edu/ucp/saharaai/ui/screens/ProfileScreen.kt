package pk.edu.ucp.saharaai.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.ui.components.GlassAlertDialog
import pk.edu.ucp.saharaai.ui.components.*
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.util.PermissionCopy
import pk.edu.ucp.saharaai.util.rememberAppPermissionRequester
import pk.edu.ucp.saharaai.util.showLocalizedToast
import pk.edu.ucp.saharaai.viewmodels.ProfileViewModel

@Composable
fun ProfileScreen(
    navController: NavController,
    onNavigateBack: () -> Unit = { navController.popBackStack() },
    onNavigateToSettings: () -> Unit,
    onNavigateToEmergency: () -> Unit,
    onSignOut: () -> Unit,
    isEnglish: Boolean = false,
    fullName: String = "",
    email: String = "",
    isFromRegistration: Boolean = false,
    profileViewModel: ProfileViewModel = viewModel()
) {
    val hazeState = remember { HazeState() }
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    LaunchedEffect(fullName) { profileViewModel.load(context, fullName) }
    val resolvedName = profileViewModel.resolvedName
    val resolvedRegion = profileViewModel.resolvedRegion
    val memberSince = profileViewModel.memberSince
    val isPrivacyModeEnabled = profileViewModel.isPrivacyModeEnabled
    val avatarId = profileViewModel.avatarId
    val customAvatarUrl = profileViewModel.customAvatarUrl
    val customAvatarStatus = profileViewModel.customAvatarStatus
    val avatarMessage = profileViewModel.avatarMessage
    val isUploadingAvatar = profileViewModel.isUploadingAvatar
    val isProfileLoading = profileViewModel.isProfileLoading

    val finalEmail = email.ifBlank { GlobalAppState.userEmail }.ifBlank { "user@sahara.ai" }

    
    var showEditNameDialog by remember { mutableStateOf(false) }
    var editNameInput    by remember { mutableStateOf(resolvedName) }
    val editNameLoading = profileViewModel.isUpdatingName
    var editNameError    by remember { mutableStateOf("") }
    val regionLoading = profileViewModel.isUpdatingRegion
    var showAvatarPickerDialog by remember { mutableStateOf(false) }
    val regionPermissionRequester = rememberAppPermissionRequester(
        permission = Manifest.permission.ACCESS_FINE_LOCATION,
        isEnglish = isEnglish,
        copy = PermissionCopy(
            deniedEn = "Location permission was denied.",
            deniedUr = "Location ki ijazat nahi di gayi.",
            settingsEn = "Enable location permission in App settings to update your profile location.",
            settingsUr = "Profile location update karne ke liye App settings mein location ki ijazat dein.",
        ),
        onGranted = {
            profileViewModel.refreshRegionFromDevice(context) { updated ->
                if (!updated) {
                    context.showLocalizedToast(
                        isEnglish,
                        "Could not detect location yet.",
                        "Location abhi detect nahi hui.",
                    )
                }
            }
        },
    )
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val metadata = context.readAvatarUploadMetadata(uri)
        profileViewModel.submitCustomAvatar(
            uri = uri,
            fileName = metadata.fileName,
            mimeType = metadata.mimeType,
            sizeBytes = metadata.sizeBytes,
            email = finalEmail,
            isEnglish = isEnglish,
        )
    }

    fun refreshProfileLocation() {
        val hasPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasPermission || GlobalAppState.userLocation.isNotBlank()) {
            profileViewModel.refreshRegionFromDevice(context) { updated ->
                if (!updated) {
                    context.showLocalizedToast(
                        isEnglish,
                        "Could not detect location yet.",
                        "Location abhi detect nahi hui.",
                    )
                }
            }
        } else {
            regionPermissionRequester.request()
        }
    }

    val displayedEmail = remember(finalEmail, isPrivacyModeEnabled) {
        if (!isPrivacyModeEnabled) finalEmail
        else {
            val prefix = finalEmail.take(1).lowercase()
            val randomSuffix = (1..6).map { ('a'..'z').random() }.joinToString("")
            "$prefix...$randomSuffix@relay.sahara.ai"
        }
    }

    val bgGradient = if (isDark) {
        listOf(SaharaStrongGreen.copy(alpha = 0.2f), MaterialTheme.colorScheme.background.copy(alpha = 0.6f), MaterialTheme.colorScheme.background)
    } else {
        listOf(SaharaStrongGreen.copy(alpha = 0.25f), SaharaPeach.copy(alpha = 0.1f), MaterialTheme.colorScheme.background.copy(alpha = 0.2f))
    }
    val blob1Color = SaharaStrongGreen.copy(alpha = if (isDark) 0.25f else 0.15f)
    val blob2Color = SaharaSky.copy(alpha = if (isDark) 0.2f else 0.18f)

    val softTextColor = if (isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f)

    val blobMotion = rememberBackdropBlobMotion()

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .background(Brush.verticalGradient(bgGradient))
        ) {
            Box(
                modifier = Modifier
                    .size(350.dp)
                    .offset(x = (-80).dp, y = (-50).dp)
                    .primaryBlobMotion(blobMotion)
                    .background(Brush.radialGradient(listOf(blob1Color, Color.Transparent)))
            )
            Box(
                modifier = Modifier
                    .size(400.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 100.dp, y = 50.dp)
                    .secondaryBlobMotion(blobMotion)
                    .background(Brush.radialGradient(listOf(blob2Color, Color.Transparent)))
            )
        }

        Scaffold(
            bottomBar = { BottomNav(navController = navController, hazeState = hazeState) },
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Canonical "back arrow + title" header used by GameRecovery,
                // ActivityLog, etc. The descriptive subtitle was removed —
                // every other screen has just the title on this line and the
                // explanatory copy lives in the body (here: the avatar block
                // immediately below).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HazeBackButton(onClick = onNavigateBack, hazeState = hazeState)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = if (isEnglish) "Profile" else "Profile",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = SaharaStrongGreen
                    )
                }
                Spacer(Modifier.height(18.dp))

                ProfileAvatarSection(
                    name           = resolvedName,
                    email          = displayedEmail,
                    region         = resolvedRegion,
                    memberSince    = memberSince,
                    avatarId       = avatarId,
                    customAvatarUrl = customAvatarUrl,
                    customAvatarStatus = customAvatarStatus,
                    avatarMessage  = avatarMessage,
                    isUploadingAvatar = isUploadingAvatar,
                    isProfileLoading = isProfileLoading,
                    isUpdatingRegion = regionLoading,
                    softTextColor  = softTextColor,
                    onEditName     = {
                        editNameInput = resolvedName
                        editNameError = ""
                        showEditNameDialog = true
                    },
                    onRefreshRegion = { refreshProfileLocation() },
                    onChangeAvatar = { showAvatarPickerDialog = true },
                    onRequestAvatar = { avatarPicker.launch("image/*") },
                    isEnglish      = isEnglish
                )

                Spacer(Modifier.height(24.dp))

                ProfileMenuSection(
                    title = if (isEnglish) "General" else "Aam Tarteebat",
                    items = listOf(
                        MenuItem(Icons.Default.Settings, SaharaLavender,
                            if (isEnglish) "Settings" else "Settings",
                            onNavigateToSettings),
                        MenuItem(Icons.Default.History, SaharaSky,
                            if (isEnglish) "Activity Log" else "Sabiqa Record",
                            {
                                navController.navigate("activity-log") {
                                    launchSingleTop = true
                                }
                            }),
                        MenuItem(Icons.Default.People, SaharaStrongGreen,
                            if (isEnglish) "App Connections" else "App Connections",
                            {
                                navController.navigate("connections") {
                                    launchSingleTop = true
                                }
                            })
                    ),
                    hazeState = hazeState,
                    softTextColor = softTextColor
                )

                Spacer(Modifier.height(16.dp))

                ProfileMenuSection(
                    title = if (isEnglish) "Support & Safety" else "Hifazat",
                    items = listOf(
                        MenuItem(Icons.Default.ShieldMoon, SaharaCoral,
                            if (isEnglish) "Emergency Helplines" else "Hanggami Madad",
                            onNavigateToEmergency),
                        MenuItem(Icons.Default.HelpOutline, SaharaLavender,
                            if (isEnglish) "Help Center" else "Madad",
                            {
                                navController.navigate("help-center") {
                                    launchSingleTop = true
                                }
                            })
                    ),
                    hazeState = hazeState,
                    softTextColor = softTextColor
                )

                Spacer(Modifier.height(32.dp))

                SaharaButton(
                    text = if (isEnglish) "Sign Out" else "Log Out Karein",
                    onClick = onSignOut,
                    variant = ButtonVariant.DESTRUCTIVE,
                    isFullWidth = true,
                    modifier = Modifier.padding(bottom = 40.dp)
                )
            }
        }
    }

    if (showEditNameDialog) {
        GlassAlertDialog(
            hazeState = hazeState,
            onDismissRequest = { showEditNameDialog = false },
            title = { Text(if (isEnglish) "Edit Name" else "Naam Badlein") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editNameInput,
                        onValueChange = { editNameInput = it },
                        label = { Text(if (isEnglish) "Your name" else "Apna naam") },
                        singleLine = true,
                        isError = editNameError.isNotBlank(),
                        supportingText = { if (editNameError.isNotBlank()) Text(editNameError) }
                    )
                    if (editNameLoading) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = editNameInput.trim()
                        if (trimmed.isBlank()) {
                            editNameError = if (isEnglish) "Name cannot be empty" else "Naam khali nahi ho sakta"
                            return@TextButton
                        }
                        editNameError = ""
                        profileViewModel.updateName(context, trimmed) {
                            showEditNameDialog = false
                        }
                    },
                    enabled = !editNameLoading
                ) {
                    Text(if (isEnglish) "Save" else "Mahfooz Karein")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text(if (isEnglish) "Cancel" else "Cancel Karein")
                }
            }
        )
    }

    if (showAvatarPickerDialog) {
        AvatarPresetDialog(
            hazeState = hazeState,
            selectedAvatarId = avatarId,
            isEnglish = isEnglish,
            onSelect = {
                profileViewModel.updateAvatarId(it, isEnglish)
                showAvatarPickerDialog = false
            },
            onDismiss = { showAvatarPickerDialog = false }
        )
    }
}

data class MenuItem(val icon: ImageVector, val color: Color, val label: String, val onClick: () -> Unit)

@Composable
private fun ProfileAvatarSection(
    name: String,
    email: String,
    region: String,
    memberSince: String,
    avatarId: String,
    customAvatarUrl: String,
    customAvatarStatus: String,
    avatarMessage: String,
    isUploadingAvatar: Boolean,
    isProfileLoading: Boolean,
    isUpdatingRegion: Boolean,
    softTextColor: Color,
    onEditName: () -> Unit,
    onRefreshRegion: () -> Unit,
    onChangeAvatar: () -> Unit,
    onRequestAvatar: () -> Unit,
    isEnglish: Boolean
) {
    var showAvatarMenu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(100.dp).background(SaharaStrongGreen.copy(0.1f), CircleShape))
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .clickable { showAvatarMenu = true }
                    .background(Brush.linearGradient(listOf(SaharaStrongGreen, SaharaSky)))
            ) {
                if (isProfileLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else if (customAvatarUrl.isNotBlank() && customAvatarStatus == "APPROVED") {
                    AsyncImage(
                        model = customAvatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(id = profileAvatarDrawable(avatarId)),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            DropdownMenu(
                expanded = showAvatarMenu,
                onDismissRequest = { showAvatarMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(if (isEnglish) "Change avatar" else "Avatar badlein") },
                    leadingIcon = { Icon(Icons.Default.AccountCircle, null) },
                    onClick = {
                        showAvatarMenu = false
                        onChangeAvatar()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (isUploadingAvatar) {
                                if (isEnglish) "Uploading..." else "Upload ho raha hai..."
                            } else {
                                if (isEnglish) "Request custom avatar" else "Custom avatar request karein"
                            }
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Upload, null) },
                    enabled = !isUploadingAvatar,
                    onClick = {
                        showAvatarMenu = false
                        onRequestAvatar()
                    }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.width(34.dp))
            Text(
                name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = SaharaStrongGreen,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp)
            )
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = onEditName,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = if (isEnglish) "Edit name" else "Naam badlein",
                    tint = SaharaStrongGreen.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(
            email,
            style = MaterialTheme.typography.bodyMedium,
            color = softTextColor.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(onClick = onRefreshRegion, enabled = !isUpdatingRegion) {
            if (isUpdatingRegion) {
                CircularProgressIndicator(
                    color = SaharaStrongGreen,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Icon(Icons.Default.LocationOn, null, tint = SaharaStrongGreen, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text(
                if (region.isBlank()) {
                    if (isEnglish) "Use current location" else "Current location use karein"
                } else {
                    region
                },
                color = SaharaStrongGreen,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (memberSince.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isEnglish) "Member since $memberSince" else "Member $memberSince se",
                style = MaterialTheme.typography.labelSmall,
                color = softTextColor.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        val statusText = when (customAvatarStatus) {
            "PENDING_REVIEW" -> if (isEnglish) "Custom avatar is awaiting admin review." else "Custom avatar admin review mein hai."
            "REJECTED" -> if (isEnglish) "Custom avatar was not approved." else "Custom avatar approve nahi hua."
            else -> ""
        }
        if (statusText.isNotBlank() || avatarMessage.isNotBlank()) {
            Text(
                text = avatarMessage.ifBlank { statusText },
                style = MaterialTheme.typography.labelSmall,
                color = if (customAvatarStatus == "REJECTED") SaharaCoral else SaharaStrongGreen,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

private data class AvatarUploadMetadata(val fileName: String, val mimeType: String, val sizeBytes: Long)

private data class ProfileAvatarPreset(val id: String, val drawableRes: Int)

@Composable
private fun AvatarPresetDialog(
    hazeState: HazeState,
    selectedAvatarId: String,
    isEnglish: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val presets = remember { profileAvatarPresets() }
    GlassAlertDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = { Text(if (isEnglish) "Choose Avatar" else "Avatar chunein") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                presets.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { preset ->
                            val selected = selectedAvatarId == preset.id
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SaharaStrongGreen.copy(alpha = if (selected) 0.16f else 0.06f))
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) SaharaStrongGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { onSelect(preset.id) }
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = preset.drawableRes),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            }
                        }
                        repeat(4 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEnglish) "Close" else "Band Karein")
            }
        }
    )
}

private fun Context.readAvatarUploadMetadata(uri: android.net.Uri): AvatarUploadMetadata {
    val resolver = contentResolver
    var fileName = "avatar"
    var sizeBytes = -1L
    resolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            if (nameIndex >= 0) fileName = cursor.getString(nameIndex) ?: fileName
            if (sizeIndex >= 0) sizeBytes = cursor.getLong(sizeIndex)
        }
    }
    if (sizeBytes < 0L) {
        sizeBytes = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
    }
    return AvatarUploadMetadata(
        fileName = fileName,
        mimeType = resolver.getType(uri).orEmpty(),
        sizeBytes = sizeBytes,
    )
}

private fun profileAvatarPresets(): List<ProfileAvatarPreset> = listOf(
    ProfileAvatarPreset("avatar_01", pk.edu.ucp.saharaai.R.drawable.av_markhor),
    ProfileAvatarPreset("avatar_02", pk.edu.ucp.saharaai.R.drawable.av_indus_river_dolphin),
    ProfileAvatarPreset("avatar_03", pk.edu.ucp.saharaai.R.drawable.av_snow_leopard),
    ProfileAvatarPreset("avatar_04", pk.edu.ucp.saharaai.R.drawable.av_zebu),
    ProfileAvatarPreset("avatar_05", pk.edu.ucp.saharaai.R.drawable.av_himalayan_ibex),
    ProfileAvatarPreset("avatar_06", pk.edu.ucp.saharaai.R.drawable.av_mugger_crocodile),
    ProfileAvatarPreset("avatar_07", pk.edu.ucp.saharaai.R.drawable.av_pangolin),
    ProfileAvatarPreset("avatar_08", pk.edu.ucp.saharaai.R.drawable.av_chinkara),
    ProfileAvatarPreset("avatar_09", pk.edu.ucp.saharaai.R.drawable.av_sivatherium),
    ProfileAvatarPreset("avatar_10", pk.edu.ucp.saharaai.R.drawable.av_indohyus),
    ProfileAvatarPreset("avatar_11", pk.edu.ucp.saharaai.R.drawable.av_stegodon),
    ProfileAvatarPreset("avatar_12", pk.edu.ucp.saharaai.R.drawable.av_aurochs),
    ProfileAvatarPreset("avatar_13", pk.edu.ucp.saharaai.R.drawable.av_sindhu_cheetah),
    ProfileAvatarPreset("avatar_14", pk.edu.ucp.saharaai.R.drawable.av_pallass_cat),
    ProfileAvatarPreset("avatar_15", pk.edu.ucp.saharaai.R.drawable.av_flying_squirrel),
    ProfileAvatarPreset("avatar_16", pk.edu.ucp.saharaai.R.drawable.av_bijju),
    ProfileAvatarPreset("avatar_17", pk.edu.ucp.saharaai.R.drawable.av_gharial),
    ProfileAvatarPreset("avatar_18", pk.edu.ucp.saharaai.R.drawable.av_argali),
    ProfileAvatarPreset("avatar_19", pk.edu.ucp.saharaai.R.drawable.av_himalayan_red_panda),
    ProfileAvatarPreset("avatar_20", pk.edu.ucp.saharaai.R.drawable.av_sindhi_fish_cat),
    ProfileAvatarPreset("avatar_21", pk.edu.ucp.saharaai.R.drawable.av_blackbuck),
    ProfileAvatarPreset("avatar_22", pk.edu.ucp.saharaai.R.drawable.av_barasinga),
    ProfileAvatarPreset("avatar_23", pk.edu.ucp.saharaai.R.drawable.av_himalayan_monal),
    ProfileAvatarPreset("avatar_24", pk.edu.ucp.saharaai.R.drawable.av_lammergeier),
    ProfileAvatarPreset("avatar_25", pk.edu.ucp.saharaai.R.drawable.av_golden_mahseer),
    ProfileAvatarPreset("avatar_26", pk.edu.ucp.saharaai.R.drawable.av_harappan_unicorn),
    ProfileAvatarPreset("avatar_27", pk.edu.ucp.saharaai.R.drawable.av_harappan_tiger),
    ProfileAvatarPreset("avatar_28", pk.edu.ucp.saharaai.R.drawable.av_babbar_sher),
    ProfileAvatarPreset("avatar_29", pk.edu.ucp.saharaai.R.drawable.av_bully_kutta),
    ProfileAvatarPreset("avatar_30", pk.edu.ucp.saharaai.R.drawable.av_bhains)
)

private fun profileAvatarDrawable(id: String): Int {
    val presets = profileAvatarPresets()
    val index = id.removePrefix("avatar_").toIntOrNull()?.minus(1) ?: 0
    return presets[index.coerceIn(0, presets.lastIndex)].drawableRes
}

@Composable
private fun PrivacyShieldCard(isPrivacyModeEnabled: Boolean, onToggle: (Boolean) -> Unit, isEnglish: Boolean, hazeState: HazeState, softTextColor: Color) {
    SaharaCard(
        variant = CardVariant.GLASS,
        hazeState = hazeState,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(SaharaStrongGreen.copy(0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPrivacyModeEnabled) Icons.Default.VerifiedUser else Icons.Default.GppBad,
                    null,
                    tint = SaharaStrongGreen
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEnglish) "Email Mask" else "Email Mask",
                    fontWeight = FontWeight.Bold,
                    color = softTextColor
                )
                Text(
                    text = if (isPrivacyModeEnabled) {
                        if (isEnglish) "Email masking is active" else "Email chhupayi gayi hai"
                    } else {
                        if (isEnglish) "Email masking is inactive" else "Email mask band hai"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = softTextColor.copy(alpha = 0.6f)
                )
            }
            Switch(
                checked = isPrivacyModeEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SaharaStrongGreen,
                    uncheckedTrackColor = Color.Gray.copy(0.2f)
                )
            )
        }
    }
}

@Composable
private fun ProfileMenuSection(title: String, items: List<MenuItem>, hazeState: HazeState, softTextColor: Color) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = softTextColor,
            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
        )
        SaharaCard(
            variant = CardVariant.GLASS,
            hazeState = hazeState,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                items.forEachIndexed { index, item ->
                    ProfileMenuItem(item, softTextColor)
                    if (index < items.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            color = softTextColor.copy(alpha = 0.08f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileMenuItem(item: MenuItem, softTextColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { item.onClick() }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(item.color.copy(0.15f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.icon, null, tint = item.color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = item.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = softTextColor
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = softTextColor.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
    }
}
