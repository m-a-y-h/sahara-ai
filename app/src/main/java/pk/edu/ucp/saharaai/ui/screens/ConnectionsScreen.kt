package pk.edu.ucp.saharaai.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.BuildConfig
import pk.edu.ucp.saharaai.R
import pk.edu.ucp.saharaai.ui.components.BottomNav
import pk.edu.ucp.saharaai.ui.components.ButtonVariant
import pk.edu.ucp.saharaai.ui.components.CardVariant
import pk.edu.ucp.saharaai.ui.components.SaharaButton
import pk.edu.ucp.saharaai.ui.components.SaharaCard
import pk.edu.ucp.saharaai.ui.components.HazeBackButton
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.utils.BlueskyOAuthCallbackStore
import pk.edu.ucp.saharaai.utils.SpotifyOAuthCallbackStore
import pk.edu.ucp.saharaai.utils.SteamOpenIdCallbackStore
import pk.edu.ucp.saharaai.viewmodels.SocialConnectionsViewModel

private const val BLUESKY_CONSENT_VERSION = "bluesky-public-analysis-v1"
private const val STEAM_CONSENT_VERSION = "steam-visible-activity-analysis-v1"
private const val SPOTIFY_CONSENT_VERSION = "spotify-identity-only-v1"
private const val YOUTUBE_CONSENT_VERSION = "youtube-subscriptions-risk-indicator-v1"
private const val YOUTUBE_READONLY_SCOPE = "https://www.googleapis.com/auth/youtube.readonly"




private fun validateUsername(platformId: String, username: String): String? {
    val u = username.trim()
    if (u.isBlank()) return "Username cannot be empty"
    return when (platformId) {
        "bluesky"  -> {
            if (!u.contains("."))
                "Must be a full handle e.g. alice.bsky.social"
            else if (u.contains(" "))
                "No spaces allowed"
            else null
        }
        "mastodon" -> {
            val regex = Regex("""^@[\w.+-]+@[\w.-]+\.[a-zA-Z]{2,}$""")
            if (!u.matches(regex))
                "Format must be @user@instance.tld"
            else null
        }
        "reddit"   -> {
            if (!u.startsWith("u/"))
                "Must start with u/  (e.g. u/johndoe)"
            else {
                val name = u.removePrefix("u/")
                val regex = Regex("""^[\w-]{3,20}$""")
                if (!name.matches(regex))
                    "Username must be 3–20 characters (letters, numbers, _ or -)"
                else null
            }
        }
        "telegram" -> {
            if (!u.startsWith("@"))
                "Must start with @  (e.g. @johndoe)"
            else {
                val name = u.removePrefix("@")
                val regex = Regex("""^\w{5,32}$""")
                if (!name.matches(regex))
                    "Username must be 5–32 characters (letters, numbers or _)"
                else null
            }
        }
        else -> null
    }
}


private fun autoFormat(platformId: String, username: String): String {
    val u = username.trim()
    return when (platformId) {
        "reddit"   -> if (!u.startsWith("u/") && u.isNotBlank()) "u/$u" else u
        "telegram" -> if (!u.startsWith("@")  && u.isNotBlank()) "@$u"  else u
        else       -> u
    }
}



private data class PlatformInfo(
    val id: String,
    val name: String,
    val iconRes: Int,
    val color: Color,
    val descEn: String,
    val descUr: String,
    val hintEn: String,       
    val hintUr: String
)

private val PLATFORMS = listOf(
    PlatformInfo(
        "bluesky", "Bluesky",
        R.drawable.ic_bluesky, Color(0xFF0085FF),
        "Verify account and analyze public posts", "Public posts ka tajziya",
        "e.g. johndoe.bsky.social",    "maslan johndoe.bsky.social"
    ),
    PlatformInfo(
        "steam", "Steam",
        R.drawable.ic_steam, Color(0xFF171A21),
        "Verify account and read visible game activity", "Visible game activity ka tajziya",
        "e.g. steamID64",              "maslan steamID64"
    ),
    PlatformInfo(
        "spotify", "Spotify",
        R.drawable.ic_spotify, Color(0xFF1DB954),
        "Verify identity only - no wellness analysis", "Sirf identity verify - tajziya nahin",
        "e.g. username",               "maslan username"
    ),
    PlatformInfo(
        "youtube", "YouTube",
        R.drawable.ic_youtube, Color(0xFFFF0000),
        "Analyze subscribed channels for risk indicator", "Subscribed channels ka risk tajziya",
        "YouTube channel",              "YouTube channel"
    )
)



@Composable
fun ConnectionsScreen(
    navController: NavController,
    onNavigateBack: () -> Unit = { navController.popBackStack() },
    isEnglish: Boolean = false,
    viewModel: SocialConnectionsViewModel = viewModel()
) {
    val context    = LocalContext.current
    val hazeState  = remember { HazeState() }
    val isDark     = isSystemInDarkTheme()
    val uid        = remember(viewModel) { viewModel.signedInUserId }

    
    LaunchedEffect(uid) { if (uid.isNotBlank()) viewModel.init(uid) }

    val connected by viewModel.connected.collectAsState()
    val saving    by viewModel.saving.collectAsState()
    val error     by viewModel.error.collectAsState()
    val oauthCallback by BlueskyOAuthCallbackStore.pending.collectAsState()
    val steamCallback by SteamOpenIdCallbackStore.pending.collectAsState()
    val spotifyCallback by SpotifyOAuthCallbackStore.pending.collectAsState()

    
    var dialogPlatform by remember { mutableStateOf<PlatformInfo?>(null) }
    var usernameInput  by remember { mutableStateOf("") }
    var showBlueskyConsent by remember { mutableStateOf(false) }
    var blueskyHandle by remember { mutableStateOf("") }
    var blueskyConsentAccepted by remember { mutableStateOf(false) }
    var showSteamConsent by remember { mutableStateOf(false) }
    var steamConsentAccepted by remember { mutableStateOf(false) }
    var showSpotifyConsent by remember { mutableStateOf(false) }
    var spotifyConsentAccepted by remember { mutableStateOf(false) }
    var showYouTubeConsent by remember { mutableStateOf(false) }
    var youtubeConsentAccepted by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val youtubeAuthorizationClient = remember(context) { Identity.getAuthorizationClient(context) }
    val youtubeScopes = remember { listOf(Scope(YOUTUBE_READONLY_SCOPE)) }

    val storeYouTubeData: (AuthorizationResult) -> Unit = { authorizationResult ->
        val accessToken = authorizationResult.accessToken.orEmpty()
        viewModel.completeYouTubeAuthorization(accessToken, YOUTUBE_CONSENT_VERSION)
    }

    val disconnectYouTube: () -> Unit = {
        val authorizationRequest = AuthorizationRequest.builder()
            .setRequestedScopes(youtubeScopes)
            .build()
        youtubeAuthorizationClient.authorize(authorizationRequest)
            .addOnSuccessListener { result ->
                val account = result.toGoogleSignInAccount()?.account
                if (!result.hasResolution() && account != null) {
                    val revokeRequest = RevokeAccessRequest.builder()
                        .setAccount(account)
                        .setScopes(youtubeScopes)
                        .build()
                    youtubeAuthorizationClient.revokeAccess(revokeRequest)
                        .addOnCompleteListener { revokeResult ->
                            viewModel.disconnect("youtube")
                            if (!revokeResult.isSuccessful) {
                                viewModel.reportError(
                                    "YouTube link removed. Also remove Sahara access in Google account settings."
                                )
                            }
                        }
                } else {
                    viewModel.disconnect("youtube")
                }
            }
            .addOnFailureListener {
                viewModel.disconnect("youtube")
                viewModel.reportError(
                    "YouTube link removed. Also remove Sahara access in Google account settings."
                )
            }
    }

    val startYouTubeAuthorization = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            viewModel.reportError("YouTube authorization was cancelled.")
        } else {
            runCatching {
                youtubeAuthorizationClient.getAuthorizationResultFromIntent(result.data)
            }.onSuccess(storeYouTubeData)
                .onFailure { viewModel.reportError("YouTube authorization failed.") }
        }
    }

    
    LaunchedEffect(error) {
        if (!error.isNullOrBlank()) {
            snackbarHostState.showSnackbar(error!!)
            viewModel.clearError()
        }
    }

    LaunchedEffect(oauthCallback, uid) {
        val result = oauthCallback ?: return@LaunchedEffect
        when {
            result.error.isNotBlank() -> viewModel.reportError("Bluesky connection failed: ${result.error}")
            uid.isBlank() -> viewModel.reportError("Log in to Sahara before connecting Bluesky.")
            else -> viewModel.completeBlueskyConnection(
                handle = result.handle,
                did = result.did,
                consentVersion = BLUESKY_CONSENT_VERSION
            )
        }
        BlueskyOAuthCallbackStore.consume(result)
    }

    LaunchedEffect(steamCallback, uid) {
        val result = steamCallback ?: return@LaunchedEffect
        when {
            result.error.isNotBlank() -> viewModel.reportError("Steam connection failed: ${result.error}")
            uid.isBlank() -> viewModel.reportError("Log in to Sahara before connecting Steam.")
            else -> viewModel.completeSteamConnection(
                steamId = result.steamId,
                displayName = result.displayName,
                consentVersion = STEAM_CONSENT_VERSION
            )
        }
        SteamOpenIdCallbackStore.consume(result)
    }

    LaunchedEffect(spotifyCallback, uid) {
        val result = spotifyCallback ?: return@LaunchedEffect
        when {
            result.error.isNotBlank() -> viewModel.reportError("Spotify connection failed: ${result.error}")
            uid.isBlank() -> viewModel.reportError("Log in to Sahara before connecting Spotify.")
            else -> viewModel.completeSpotifyConnection(
                spotifyId = result.spotifyId,
                displayName = result.displayName,
                consentVersion = SPOTIFY_CONSENT_VERSION
            )
        }
        SpotifyOAuthCallbackStore.consume(result)
    }

    val connectedCount = connected.size

    
    val bgGradient = if (isDark)
        listOf(SaharaStrongGreen.copy(.2f), MaterialTheme.colorScheme.background.copy(.6f), MaterialTheme.colorScheme.background)
    else
        listOf(SaharaStrongGreen.copy(.25f), SaharaPeach.copy(.1f), MaterialTheme.colorScheme.background.copy(.2f))

    val blobMotion = rememberBackdropBlobMotion()

    val softText = if (isDark) Color.White.copy(.9f) else Color.Black.copy(.85f)

    if (showBlueskyConsent) {
        val normalizedHandle = blueskyHandle.trim().removePrefix("@")
        val validationError = if (normalizedHandle.isBlank()) {
            null
        } else {
            validateUsername("bluesky", normalizedHandle)
        }
        val canContinue = uid.isNotBlank() &&
            normalizedHandle.isNotBlank() &&
            validationError == null &&
            blueskyConsentAccepted

        AlertDialog(
            onDismissRequest = {
                showBlueskyConsent = false
                blueskyHandle = ""
                blueskyConsentAccepted = false
            },
            title = {
                Text(
                    if (isEnglish) "Connect Bluesky" else "Bluesky Connect Karein",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (isEnglish)
                            "Sahara will verify this account through Bluesky login, then analyze its public posts and replies."
                        else
                            "Sahara Bluesky login se account verify karega, phir public posts aur replies ka tajziya karega.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        if (isEnglish)
                            "Analysis purpose: flag language suggesting possible substance influence, fear, sadness, or disturbed sentiment."
                        else
                            "Maqsad: nashay ke mumkin asar, khauf, udaasi ya pareshan jazbaat wale alfaaz flag karna.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (isEnglish)
                            "Sahara requests identity verification only. It will not read direct messages, post, like, follow, or edit your account."
                        else
                            "Sahara sirf identity verification mangta hai. Direct messages parhna, post, like, follow ya account edit nahin karega.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SaharaStrongGreen
                    )
                    OutlinedTextField(
                        value = blueskyHandle,
                        onValueChange = { blueskyHandle = it.trim() },
                        label = { Text(if (isEnglish) "Bluesky handle" else "Bluesky handle") },
                        placeholder = { Text("e.g. johndoe.bsky.social") },
                        singleLine = true,
                        isError = validationError != null,
                        supportingText = {
                            validationError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = blueskyConsentAccepted,
                            onCheckedChange = { blueskyConsentAccepted = it }
                        )
                        Text(
                            if (isEnglish)
                                "I consent to public-post analysis for these signals."
                            else
                                "Main public posts ke is tajziye ki ijazat deta/deti hoon.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        if (isEnglish)
                            "This research prototype is not a medical diagnosis."
                        else
                            "Yeh research prototype medical tashkhees nahin hai.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = canContinue,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0085FF)),
                    onClick = {
                        val startUri = Uri.parse(
                            "${BuildConfig.BLUESKY_POC_BASE_URL.trimEnd('/')}/oauth/bluesky/start"
                        ).buildUpon()
                            .appendQueryParameter("handle", normalizedHandle)
                            .build()
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, startUri))
                            showBlueskyConsent = false
                            blueskyHandle = ""
                            blueskyConsentAccepted = false
                        } catch (_: ActivityNotFoundException) {
                            viewModel.reportError("No browser is available for Bluesky authorization.")
                        }
                    }
                ) {
                    Text(
                        if (isEnglish) "Continue to Bluesky" else "Bluesky Par Jayein",
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBlueskyConsent = false
                    blueskyHandle = ""
                    blueskyConsentAccepted = false
                }) {
                    Text(if (isEnglish) "Cancel" else "Waapis")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showSteamConsent) {
        AlertDialog(
            onDismissRequest = {
                showSteamConsent = false
                steamConsentAccepted = false
            },
            title = {
                Text(
                    if (isEnglish) "Connect Steam" else "Steam Connect Karein",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (isEnglish)
                            "Sahara will use Steam sign-in to verify your Steam account identity."
                        else
                            "Sahara Steam sign-in se aapke Steam account ki pehchan verify karega.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        if (isEnglish)
                            "With your consent, Sahara may read game activity visible through Steam, such as public profile information, recently played games and playtime, to support later wellbeing analysis."
                        else
                            "Aapki ijazat se Sahara Steam par visible profile, recently played games aur playtime ko baad ke wellbeing tajziye ke liye parh sakta hai.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (isEnglish)
                            "Steam sign-in verifies identity; it does not provide a detailed data-permission popup. Sahara will not buy, trade, message, or change your Steam account."
                        else
                            "Steam sign-in identity verify karta hai; detailed data-permission popup nahin deta. Sahara buy, trade, message ya account change nahin karega.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SaharaStrongGreen
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = steamConsentAccepted,
                            onCheckedChange = { steamConsentAccepted = it }
                        )
                        Text(
                            if (isEnglish)
                                "I consent to analysis of visible Steam activity."
                            else
                                "Main visible Steam activity ke tajziye ki ijazat deta/deti hoon.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        if (isEnglish)
                            "Your Steam privacy settings can prevent activity retrieval. This research prototype is not a diagnosis."
                        else
                            "Steam privacy settings activity retrieval ko rok sakti hain. Yeh prototype tashkhees nahin hai.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = uid.isNotBlank() && steamConsentAccepted,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF171A21)),
                    onClick = {
                        val startUri = Uri.parse(
                            "${BuildConfig.STEAM_POC_BASE_URL.trimEnd('/')}/openid/steam/start"
                        )
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, startUri))
                            showSteamConsent = false
                            steamConsentAccepted = false
                        } catch (_: ActivityNotFoundException) {
                            viewModel.reportError("No browser is available for Steam sign-in.")
                        }
                    }
                ) {
                    Text(
                        if (isEnglish) "Continue to Steam" else "Steam Par Jayein",
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSteamConsent = false
                    steamConsentAccepted = false
                }) {
                    Text(if (isEnglish) "Cancel" else "Waapis")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showSpotifyConsent) {
        AlertDialog(
            onDismissRequest = {
                showSpotifyConsent = false
                spotifyConsentAccepted = false
            },
            title = {
                Text(
                    if (isEnglish) "Connect Spotify" else "Spotify Connect Karein",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (isEnglish)
                            "Sahara will use Spotify login to verify your account and store only the Spotify ID and display name needed to show it is linked."
                        else
                            "Sahara Spotify login se account verify karega aur link dikhane ke liye sirf Spotify ID aur display name save karega.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        if (isEnglish)
                            "Sahara will not retrieve or analyze your playlists, recent listening, top tracks, saved music, or listening history for distress or substance-risk signals."
                        else
                            "Sahara aapki playlists, recent listening, top tracks, saved music ya history ko distress ya substance-risk ke liye analyze nahin karega.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SaharaStrongGreen
                    )
                    Text(
                        if (isEnglish)
                            "Spotify policy does not allow Spotify content to be used to create inferred health-related profiles."
                        else
                            "Spotify policy Spotify content se health-related andazay banane ki ijazat nahin deti.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = spotifyConsentAccepted,
                            onCheckedChange = { spotifyConsentAccepted = it }
                        )
                        Text(
                            if (isEnglish)
                                "I agree to link my Spotify identity only."
                            else
                                "Main sirf apni Spotify identity link karne se razi hoon.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = uid.isNotBlank() && spotifyConsentAccepted,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                    onClick = {
                        val startUri = Uri.parse(
                            "${BuildConfig.SPOTIFY_POC_BASE_URL.trimEnd('/')}/oauth/spotify/start"
                        )
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, startUri))
                            showSpotifyConsent = false
                            spotifyConsentAccepted = false
                        } catch (_: ActivityNotFoundException) {
                            viewModel.reportError("No browser is available for Spotify login.")
                        }
                    }
                ) {
                    Text(
                        if (isEnglish) "Continue to Spotify" else "Spotify Par Jayein",
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSpotifyConsent = false
                    spotifyConsentAccepted = false
                }) {
                    Text(if (isEnglish) "Cancel" else "Waapis")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showYouTubeConsent) {
        val openPolicyLink: (String) -> Unit = { address ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address)))
            } catch (_: ActivityNotFoundException) {
                viewModel.reportError("No browser is available to open the policy link.")
            }
        }
        AlertDialog(
            onDismissRequest = {
                showYouTubeConsent = false
                youtubeConsentAccepted = false
            },
            title = {
                Text(
                    if (isEnglish) "Connect YouTube" else "YouTube Connect Karein",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (isEnglish)
                            "Sahara will use Google authorization to verify your YouTube channel and retrieve the channels you subscribe to."
                        else
                            "Sahara Google authorization se aapka YouTube channel verify karega aur aapke subscribed channels retrieve karega.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        if (isEnglish)
                            "Google will request View your YouTube account (youtube.readonly). Sahara stores your channel ID/title and up to 1,000 subscribed channel IDs/titles."
                        else
                            "Google View your YouTube account (youtube.readonly) permission mangega. Sahara channel ID/title aur 1,000 tak subscribed channel IDs/titles save karega.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (isEnglish)
                            "Your subscribed-channel list will be used later as an input to Sahara's possible non-prescribed drug-use risk indicator. It is a support signal, not a diagnosis or medical assessment. Sahara will not read videos, comments, or viewing history in this flow."
                        else
                            "Aapke subscribed channels baad mein Sahara ke possible non-prescribed drug-use risk indicator mein input honge. Yeh support signal hai, diagnosis ya medical assessment nahin. Sahara is flow mein videos, comments ya viewing history nahin parhega.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SaharaStrongGreen
                    )
                    Text(
                        if (isEnglish)
                            "Saved YouTube channel and subscription data expires after 30 days unless refreshed. Unlink removes it and revokes Sahara's YouTube access."
                        else
                            "Saved YouTube channel aur subscription data 30 din baad expire hota hai jab tak refresh na ho. Unlink isay remove karke YouTube access revoke karta hai.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            openPolicyLink("https://www.youtube.com/t/terms")
                        }) {
                            Text("YouTube Terms", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = {
                            openPolicyLink("https://policies.google.com/privacy")
                        }) {
                            Text("Google Privacy", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = youtubeConsentAccepted,
                            onCheckedChange = { youtubeConsentAccepted = it }
                        )
                        Text(
                            if (isEnglish)
                                "I agree to the YouTube Terms and to subscription analysis for Sahara's risk indicator."
                            else
                                "Main YouTube Terms aur Sahara risk indicator ke liye subscription analysis se razi hoon.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = uid.isNotBlank() && youtubeConsentAccepted,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                    onClick = {
                        val authorizationRequest = AuthorizationRequest.builder()
                            .setRequestedScopes(youtubeScopes)
                            .build()
                        youtubeAuthorizationClient.authorize(authorizationRequest)
                            .addOnSuccessListener { result ->
                                if (result.hasResolution()) {
                                    val pendingIntent = result.pendingIntent
                                    if (pendingIntent == null) {
                                        viewModel.reportError("Google could not open YouTube authorization.")
                                    } else {
                                        startYouTubeAuthorization.launch(
                                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                        )
                                    }
                                } else {
                                    storeYouTubeData(result)
                                }
                            }
                            .addOnFailureListener {
                                viewModel.reportError("Unable to start YouTube authorization.")
                            }
                        showYouTubeConsent = false
                        youtubeConsentAccepted = false
                    }
                ) {
                    Text(
                        if (isEnglish) "Continue to Google" else "Google Par Jayein",
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showYouTubeConsent = false
                    youtubeConsentAccepted = false
                }) {
                    Text(if (isEnglish) "Cancel" else "Waapis")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    
    dialogPlatform?.let { platform ->
        val keyboard = LocalSoftwareKeyboardController.current

        
        val validationError = remember(usernameInput) {
            if (usernameInput.isBlank()) null   
            else validateUsername(platform.id, autoFormat(platform.id, usernameInput))
        }
        val isValid = usernameInput.isNotBlank() && validationError == null

        AlertDialog(
            onDismissRequest = { dialogPlatform = null; usernameInput = "" },
            title = {
                Text(
                    if (isEnglish) "Connect ${platform.name}" else "${platform.name} Connect Karein",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isEnglish) "Enter your ${platform.name} username:"
                        else           "Apna ${platform.name} username darj karein:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value         = usernameInput,
                        onValueChange = { usernameInput = it.trim() },
                        placeholder = {
                            Text(
                                if (isEnglish) platform.hintEn else platform.hintUr,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        singleLine = true,
                        isError    = validationError != null,
                        supportingText = {
                            if (validationError != null) {
                                Text(
                                    validationError,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            } else if (isValid) {
                                Text(
                                    if (isEnglish) "✓ Valid format" else "✓ Format sahi hai",
                                    color = SaharaStrongGreen,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction    = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                        shape  = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = if (isValid) SaharaStrongGreen else platform.color,
                            unfocusedBorderColor = platform.color.copy(.4f),
                            errorBorderColor     = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val formatted = autoFormat(platform.id, usernameInput)
                        if (validateUsername(platform.id, formatted) == null) {
                            viewModel.connect(platform.id, formatted)
                            dialogPlatform = null
                            usernameInput  = ""
                        }
                    },
                    enabled = isValid,
                    colors  = ButtonDefaults.buttonColors(containerColor = platform.color)
                ) {
                    Text(if (isEnglish) "Connect" else "Jorein", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogPlatform = null; usernameInput = "" }) {
                    Text(if (isEnglish) "Cancel" else "Waapis", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    
    Box(modifier = Modifier.fillMaxSize()) {
        
        Box(
            modifier = Modifier.fillMaxSize().hazeSource(hazeState)
                .background(Brush.verticalGradient(bgGradient))
        ) {
            Box(Modifier.size(350.dp).offset((-80).dp, (-50).dp).primaryBlobMotion(blobMotion)
                .background(Brush.radialGradient(listOf(SaharaStrongGreen.copy(if (isDark) .25f else .15f), Color.Transparent))))
            Box(Modifier.size(400.dp).align(Alignment.BottomEnd).offset(100.dp, 50.dp).secondaryBlobMotion(blobMotion)
                .background(Brush.radialGradient(listOf(SaharaSky.copy(if (isDark) .2f else .18f), Color.Transparent))))
        }

        Scaffold(
            bottomBar = { BottomNav(navController = navController, hazeState = hazeState) },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HazeBackButton(onClick = onNavigateBack, hazeState = hazeState)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            if (isEnglish) "Connections" else "Rabtay (Connections)",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = SaharaStrongGreen,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            if (isEnglish) "Link your social accounts" else "Apne social accounts connect karein",
                            style = MaterialTheme.typography.bodySmall,
                            color = softText.copy(.6f)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) SaharaStrongGreen.copy(.25f) else Color.White.copy(.7f),
                    border = BorderStroke(1.dp, SaharaStrongGreen.copy(.4f))
                ) {
                    Text(
                        if (isEnglish) "$connectedCount of ${PLATFORMS.size} connected"
                        else           "${PLATFORMS.size} mein se $connectedCount connected",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = SaharaStrongGreen,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                
                if (uid.isBlank()) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SaharaCoral.copy(.12f))
                    ) {
                        Text(
                            if (isEnglish) "Please log in to save connections."
                            else           "Connections save karne ke liye login karein.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SaharaCoral,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                
                PLATFORMS.forEach { platform ->
                    val connection  = connected[platform.id]
                    val username    = connection?.displayIdentifier
                    val isConnected = connection != null
                    val isSaving    = saving == platform.id

                    SaharaCard(
                        variant  = CardVariant.GLASS,
                        hazeState = hazeState,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.size(52.dp)
                                        .background(platform.color.copy(.1f), RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (platform.iconRes != -1) {
                                        Image(
                                            painter = painterResource(platform.iconRes),
                                            contentDescription = platform.name,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    } else {
                                        val vector = when (platform.id) {
                                            "steam" -> Icons.Default.Games
                                            "spotify" -> Icons.Default.Album
                                            "youtube" -> Icons.Default.Subscriptions
                                            else -> Icons.Default.Public
                                        }
                                        Icon(
                                            imageVector = vector,
                                            contentDescription = platform.name,
                                            tint = platform.color,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    if (isConnected) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Connected",
                                            tint = SaharaStrongGreen,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .align(Alignment.BottomEnd)
                                                .offset(4.dp, 4.dp)
                                                .background(
                                                    if (isDark) Color.Black else Color.White,
                                                    CircleShape
                                                )
                                        )
                                    }
                                }

                                Spacer(Modifier.width(16.dp))

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        platform.name,
                                        fontWeight = FontWeight.Bold,
                                        color = softText
                                    )
                                    if (isConnected) {
                                        Text(
                                            username!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SaharaStrongGreen,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                        if (platform.id == "bluesky" && connection.verified) {
                                            Text(
                                                if (isEnglish) "OAuth linked - public posts only" else "OAuth linked - sirf public posts",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = softText.copy(.65f),
                                                maxLines = 1
                                            )
                                        }
                                        if (platform.id == "steam" && connection.verified) {
                                            Text(
                                                if (isEnglish) "OpenID linked - visible activity only" else "OpenID linked - sirf visible activity",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = softText.copy(.65f),
                                                maxLines = 1
                                            )
                                        }
                                        if (platform.id == "spotify" && connection.verified) {
                                            Text(
                                                if (isEnglish) "OAuth linked - identity only" else "OAuth linked - sirf identity",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = softText.copy(.65f),
                                                maxLines = 1
                                            )
                                        }
                                        if (platform.id == "youtube" && connection.verified) {
                                            Text(
                                                if (isEnglish)
                                                    "OAuth linked - ${connection.subscriptionCount} subscriptions saved"
                                                else
                                                    "OAuth linked - ${connection.subscriptionCount} subscriptions saved",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = softText.copy(.65f),
                                                maxLines = 1
                                            )
                                        }
                                    } else {
                                        Text(
                                            if (isEnglish) platform.descEn else platform.descUr,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = softText.copy(.6f),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            
                            when {
                                isSaving -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 2.5.dp,
                                        color = platform.color
                                    )
                                }
                                isConnected -> {
                                    SaharaButton(
                                        text = if (isEnglish) "Unlink" else "Hataein",
                                        onClick = {
                                            if (platform.id == "youtube") {
                                                disconnectYouTube()
                                            } else {
                                                viewModel.disconnect(platform.id)
                                            }
                                        },
                                        variant = ButtonVariant.DESTRUCTIVE,
                                        modifier = Modifier.width(100.dp).height(36.dp),
                                        isEnglish = isEnglish
                                    )
                                }
                                else -> {
                                    SaharaButton(
                                        text = if (isEnglish) "Connect" else "Jorein",
                                        onClick = {
                                            if (platform.id == "bluesky") {
                                                blueskyHandle = ""
                                                blueskyConsentAccepted = false
                                                showBlueskyConsent = true
                                            } else if (platform.id == "steam") {
                                                steamConsentAccepted = false
                                                showSteamConsent = true
                                            } else if (platform.id == "spotify") {
                                                spotifyConsentAccepted = false
                                                showSpotifyConsent = true
                                            } else if (platform.id == "youtube") {
                                                youtubeConsentAccepted = false
                                                showYouTubeConsent = true
                                            } else {
                                                usernameInput = ""
                                                dialogPlatform = platform
                                            }
                                        },
                                        variant = ButtonVariant.SAHARASTRONGGREENGLASS,
                                        modifier = Modifier.width(100.dp).height(36.dp),
                                        isEnglish = isEnglish,
                                        hazeState = hazeState,
                                        textStyle = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }

                
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = SaharaSky.copy(if (isDark) .12f else .08f)
                    )
                ) {
                    Text(
                        if (isEnglish)
                            "Linked accounts store a verified identity and consent record. Bluesky uses public posts; Steam uses visible activity; YouTube uses authorized subscriptions for the later risk indicator; Spotify remains identity-only."
                        else
                            "Linked accounts verified identity aur consent record rakhte hain. Bluesky public posts, Steam visible activity aur YouTube authorized subscriptions later risk indicator ke liye use karta hai; Spotify sirf identity link hai.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) SaharaSky.copy(.9f) else SaharaSky,
                        modifier = Modifier.padding(14.dp)
                    )
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
