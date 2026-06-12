package pk.edu.ucp.saharaai.ui.screens

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import io.livekit.android.compose.local.RoomScope
import io.livekit.android.compose.state.rememberTracks
import io.livekit.android.compose.ui.VideoTrackView
import io.livekit.android.room.track.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.edu.ucp.saharaai.BuildConfig
import pk.edu.ucp.saharaai.data.remote.LiveKitTokenClient
import pk.edu.ucp.saharaai.data.remote.LiveKitTokenResponse
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import pk.edu.ucp.saharaai.ui.components.GlassAlertDialog
import pk.edu.ucp.saharaai.ui.theme.SaharaCoral
import pk.edu.ucp.saharaai.ui.theme.SaharaSky
import pk.edu.ucp.saharaai.ui.theme.SaharaStrongGreen
import pk.edu.ucp.saharaai.utils.ObservePermissionState
import pk.edu.ucp.saharaai.utils.PermissionCopy
import pk.edu.ucp.saharaai.utils.rememberAppPermissionsRequester
import dev.chrisbanes.haze.HazeState

@Composable
fun InAppCallScreen(
    navController: NavController,
    isEnglish: Boolean,
    counselorKey: String,
    counselorName: String,
    mode: String,
    forUserId: String,
) {
    val hazeState = remember { HazeState() }
    val isVideo = mode.equals("video", ignoreCase = true)
    val currentUid = remember { Firebase.auth.currentUser?.uid.orEmpty() }
    val isCounselorSide = forUserId != "self"
    val userUid = if (isCounselorSide) forUserId else currentUid
    val roomName = remember(userUid, counselorKey) { "sahara_${userUid}_$counselorKey" }

    var permissionsGranted by remember { mutableStateOf(false) }
    var callEnabled by remember { mutableStateOf(false) }
    var counselorLoaded by remember { mutableStateOf(false) }
    var identityVisible by remember { mutableStateOf(false) }
    var userProfile by remember { mutableStateOf<Map<String, Any>?>(null) }
    var counselorProfile by remember { mutableStateOf<Map<String, Any>?>(null) }
    var tokenResponse by remember { mutableStateOf<LiveKitTokenResponse?>(null) }
    var tokenError by remember { mutableStateOf("") }
    var isLoadingToken by remember { mutableStateOf(false) }
    var showEndPrompt by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val permissionRequester = rememberAppPermissionsRequester(
        permissions = if (isVideo) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        },
        isEnglish = isEnglish,
        copy = PermissionCopy(
            deniedEn = "Camera or microphone permission was denied.",
            deniedUr = "Camera ya microphone permission nahi mili.",
            settingsEn = "Enable camera and microphone permissions in App settings to use calls.",
            settingsUr = "Calls ke liye App settings mein camera aur microphone permissions dein.",
        ),
        onGranted = { permissionsGranted = true },
        onDenied = { permissionsGranted = false },
    )
    ObservePermissionState(permissionRequester) {
        permissionsGranted = it
    }

    LaunchedEffect(counselorKey) {
        if (counselorKey.isBlank()) return@LaunchedEffect
        RealtimeDBService.listenToCounselorData(counselorKey).collect { data ->
            counselorLoaded = true
            callEnabled = data?.get("callEnabled") as? Boolean ?: false
            counselorProfile = data
        }
    }

    LaunchedEffect(userUid, counselorKey) {
        if (userUid.isBlank() || counselorKey.isBlank()) return@LaunchedEffect
        RealtimeDBService.getUserProfile(userUid).onSuccess { userProfile = it }
        RealtimeDBService.listenChatIdentityVisible(userUid, counselorKey).collect {
            identityVisible = it
        }
    }

    val peerDisplayName = remember(isCounselorSide, identityVisible, userProfile, counselorProfile, counselorName) {
        if (isCounselorSide) {
            // Counselor is viewing. If user is anonymous, show Anonymous.
            if (identityVisible) {
                userProfile.profileText("name", "fullName").ifBlank { "Sahara User" }
            } else {
                "Anonymous User"
            }
        } else {
            // User is viewing. Show counselor name.
            counselorProfile.profileText("assignedName", "name").ifBlank { counselorName }
        }
    }
    val localDisplayName = remember(isCounselorSide, identityVisible, userProfile, counselorProfile) {
        if (isCounselorSide) {
            counselorProfile.profileText("assignedName", "name").ifBlank { "Counselor" }
        } else if (identityVisible) {
            userProfile.profileText("name", "fullName").ifBlank { "Sahara User" }
        } else {
            "Anonymous User"
        }
    }
    val identity = remember(isCounselorSide, currentUid, counselorKey) {
        if (isCounselorSide) "counselor_$counselorKey" else "user_$currentUid"
    }

    LaunchedEffect(permissionsGranted, callEnabled, roomName, identity, localDisplayName) {
        if (!permissionsGranted || !callEnabled || userUid.isBlank() || counselorKey.isBlank()) return@LaunchedEffect
        if (tokenResponse != null || isLoadingToken) return@LaunchedEffect
        isLoadingToken = true
        tokenError = ""
        val result = withContext(Dispatchers.IO) {
            LiveKitTokenClient.fetchToken(
                tokenUrl = BuildConfig.LIVEKIT_TOKEN_URL,
                fallbackServerUrl = BuildConfig.LIVEKIT_URL,
                roomName = roomName,
                identity = identity,
                displayName = localDisplayName,
                mode = if (isVideo) "video" else "voice",
                counselorKey = counselorKey,
                userId = userUid,
            )
        }
        result.onSuccess { tokenResponse = it }
            .onFailure { tokenError = it.message ?: "Could not start the call." }
        isLoadingToken = false
    }

    fun leaveCall(turnOffCalls: Boolean) {
        if (turnOffCalls && counselorKey.isNotBlank()) {
            scope.launch {
                RealtimeDBService.setCounselorCallAvailability(counselorKey, false)
            }
        }
        navController.popBackStack()
    }

    if (showEndPrompt) {
        GlassAlertDialog(
            hazeState = hazeState,
            onDismissRequest = { showEndPrompt = false },
            title = { Text(if (isEnglish) "Turn calls off?" else "Calls band karein?") },
            text = {
                Text(
                    if (isEnglish) "For your safety, you can close call availability after this call."
                    else "Apni hifazat ke liye is call ke baad call availability band kar sakte hain."
                )
            },
            confirmButton = {
                TextButton(onClick = { leaveCall(true) }) {
                    Text(if (isEnglish) "Turn Off" else "Band Karein", color = SaharaCoral)
                }
            },
            dismissButton = {
                TextButton(onClick = { leaveCall(false) }) {
                    Text(if (isEnglish) "Keep On" else "On Rakhein")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF07120E),
                        Color(0xFF10251D),
                        Color(0xFF07120E),
                    )
                )
            )
            .systemBarsPadding()
            .padding(18.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(SaharaStrongGreen, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (isVideo) Icons.Default.Videocam else Icons.Default.Mic, null, tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        peerDisplayName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (isVideo) "LiveKit video call" else "LiveKit voice call",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Surface(
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        !permissionsGranted -> CallStatusMessage(
                            text = if (isEnglish) "Camera and microphone permission is needed for this call." else "Is call ke liye camera aur microphone permission chahiye.",
                            action = if (isEnglish) "Allow" else "Allow",
                            onAction = { permissionRequester.request() },
                        )
                        counselorLoaded && !callEnabled -> CallStatusMessage(
                            text = if (isEnglish) "This counselor is not taking calls right now." else "Ye counselor abhi calls nahi le raha.",
                        )
                        tokenError.isNotBlank() -> CallStatusMessage(text = tokenError)
                        isLoadingToken || tokenResponse == null -> CircularProgressIndicator(color = SaharaStrongGreen)
                        else -> LiveKitCallRoom(tokenResponse!!, isVideo)
                    }
                }
            }

            Button(
                onClick = {
                    if (isCounselorSide) showEndPrompt = true else leaveCall(false)
                },
                colors = ButtonDefaults.buttonColors(containerColor = SaharaCoral),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.CallEnd, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isEnglish) "End Call" else "Call Khatam Karein", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LiveKitCallRoom(tokenResponse: LiveKitTokenResponse, isVideo: Boolean) {
    RoomScope(
        url = tokenResponse.serverUrl,
        token = tokenResponse.token,
        audio = true,
        video = isVideo,
        connect = true,
    ) {
        val trackRefs by rememberTracks(listOf(Track.Source.CAMERA))
        if (isVideo && trackRefs.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(trackRefs) { trackReference ->
                    VideoTrackView(
                        trackReference = trackReference,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .background(Color.Black, RoundedCornerShape(14.dp)),
                    )
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Person, null, tint = SaharaSky, modifier = Modifier.size(72.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    "Connected",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun CallStatusMessage(
    text: String,
    action: String = "",
    onAction: () -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(24.dp),
    ) {
        Text(text, color = Color.White, textAlign = TextAlign.Center)
        if (action.isNotBlank()) {
            Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = SaharaStrongGreen)) {
                Text(action)
            }
        }
    }
}

private fun Map<String, Any>?.profileText(vararg keys: String): String {
    if (this == null) return ""
    return keys.firstNotNullOfOrNull { key ->
        this[key]?.toString()?.trim()?.takeIf { it.isNotBlank() }
    }.orEmpty()
}
