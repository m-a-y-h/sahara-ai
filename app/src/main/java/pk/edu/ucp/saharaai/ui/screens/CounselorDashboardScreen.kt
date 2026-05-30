package pk.edu.ucp.saharaai.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.R
import pk.edu.ucp.saharaai.data.model.EmergencyAlert
import pk.edu.ucp.saharaai.ui.components.*
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.viewmodels.CounselorDashboardViewModel

data class RiskAlert(
    val id: String,
    val ageRange: String,
    val riskScore: Int,
    val distanceEn: String,
    val distanceUr: String,
    val timeAgoEn: String,
    val timeAgoUr: String
)


private fun EmergencyAlert.toRiskAlert(): RiskAlert {
    val minsAgo = ((com.google.firebase.Timestamp.now().seconds - createdAt.seconds) / 60).toInt()
    val timeEn = when {
        minsAgo < 1  -> "Just now"
        minsAgo < 60 -> "$minsAgo mins ago"
        else         -> "${minsAgo / 60} hr ago"
    }
    val timeUr = when {
        minsAgo < 1  -> "Abhi abhi"
        minsAgo < 60 -> "$minsAgo minute pehle"
        else         -> "${minsAgo / 60} ghanta pehle"
    }
    return RiskAlert(
        id         = alertId,
        ageRange   = "Unknown",
        riskScore  = riskScore.toInt(),
        distanceEn = locationName.ifBlank { "Location unknown" },
        distanceUr = locationName.ifBlank { "Location maloom nahi" },
        timeAgoEn  = timeEn,
        timeAgoUr  = timeUr
    )
}

@Composable
fun CounselorDashboardScreen(
    navController: NavController,
    isEnglish: Boolean,
    counselorKey: String = "",
    onSignOut: () -> Unit = {},
    dashboardViewModel: CounselorDashboardViewModel = viewModel()
) {
    val isDark = isSystemInDarkTheme()
    val bgHazeState = remember { HazeState() }
    val softTextColor = if (isDark) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.75f)

    
    val counselorId  = remember(dashboardViewModel) { dashboardViewModel.signedInCounselorId }
    val realtimeData by dashboardViewModel.realtimeData.collectAsState()
    val openAlerts   by dashboardViewModel.openAlerts.collectAsState()
    val isOnline     by dashboardViewModel.isOnline.collectAsState()
    val isInvisible  by dashboardViewModel.isInvisible.collectAsState()
    val callEnabled  by dashboardViewModel.callEnabled.collectAsState()
    val chatSessions by dashboardViewModel.chatSessions.collectAsState()
    val isChatSessionsLoading by dashboardViewModel.isChatSessionsLoading.collectAsState()
    val canLoadChatSessions = counselorKey.isNotBlank() || counselorId.isNotBlank()

    
    val displayName   = (realtimeData?.get("assignedName") as? String)?.ifBlank { null }
        ?: (realtimeData?.get("name") as? String)
        ?: (if (isEnglish) "Counselor Dashboard" else "Counselor Dashboard")
    val displayRegion = (realtimeData?.get("region") as? String)?.ifBlank { null }
        ?: (if (isEnglish) "Pakistan" else "Pakistan")

    LaunchedEffect(counselorKey) {
        if (counselorKey.isNotBlank()) {
            dashboardViewModel.listenToRealtimeProfile(counselorKey)
            dashboardViewModel.listenToChatSessionsRealtime(counselorKey)
        } else if (counselorId.isNotBlank()) {

            dashboardViewModel.loadProfile(counselorId)
            dashboardViewModel.listenToChatSessions(counselorId)
        }
        if (counselorId.isNotBlank()) {
            dashboardViewModel.loadOpenAlerts(counselorId)
            dashboardViewModel.loadReports()
        }
    }

    // Counselor presence: while this dashboard is composed, the counselor is
    // auto-online via Firebase presence (the helper also registers an
    // onDisconnect, so a crash/app-kill flips them offline). The "Visible"
    // switch below is just an override that hides them from users without
    // tearing down the connection.
    DisposableEffect(counselorKey) {
        if (counselorKey.isNotBlank()) {
            dashboardViewModel.attachPresence(counselorKey)
        }
        onDispose { dashboardViewModel.detachPresence() }
    }

    
    val displayAlerts = remember(openAlerts) {
        openAlerts.map { it.toRiskAlert() }
    }

    val blobMotion = rememberBackdropBlobMotion()

    val pagerState = rememberPagerState(pageCount = { 3 })

    Box(modifier = Modifier.fillMaxSize()) {

        Box(modifier = Modifier.fillMaxSize().hazeSource(bgHazeState)) {
            Image(
                painter = painterResource(id = R.drawable.sahara_bg3),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (isDark) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.1f)))
            }

            Box(
                modifier = Modifier.size(350.dp).offset(x = (-50).dp, y = (-100).dp).primaryBlobMotion(blobMotion)
                    .background(
                        if (isDark) SaharaCoral.copy(0.15f) else SaharaCoral.copy(0.2f),
                        CircleShape
                    ).blur(100.dp)
            )
            Box(
                modifier = Modifier.size(400.dp).align(Alignment.BottomEnd)
                    .offset(x = 50.dp, y = 150.dp).secondaryBlobMotion(blobMotion).background(
                        if (isDark) SaharaSky.copy(0.15f) else SaharaSky.copy(0.25f),
                        CircleShape
                    ).blur(120.dp)
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = SaharaStrongGreen
                        )
                        Text(
                            text = displayRegion,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }

                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // The switch is the counselor's manual "appear offline"
                        // override. Online state itself is driven by Firebase
                        // presence (attachPresence above), so flipping this off
                        // makes them invisible to users without dropping the
                        // dashboard's live connection.
                        val visible = !isInvisible
                        Switch(
                            checked = visible,
                            onCheckedChange = {
                                if (counselorKey.isNotBlank())
                                    dashboardViewModel.toggleInvisible(counselorKey)
                                else if (counselorId.isNotBlank())
                                    dashboardViewModel.toggleAvailability(counselorId)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor   = Color.White,
                                checkedTrackColor   = SaharaStrongGreen,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = SaharaCoral.copy(alpha = 0.6f),
                            ),
                            modifier = Modifier.height(28.dp),
                        )
                        Text(
                            text = if (visible) (if (isEnglish) "Visible" else "Visible")
                                   else (if (isEnglish) "Invisible" else "Invisible"),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (visible) SaharaStrongGreen else SaharaCoral,
                            fontWeight = FontWeight.Bold,
                        )
                        } 
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        IconButton(onClick = onSignOut) {
                            Icon(
                                Icons.Default.Logout,
                                contentDescription = if (isEnglish) "Sign Out" else "Log Out",
                                tint = SaharaCoral,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } 
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(top = 24.dp, bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEnglish) "Take voice & video calls" else "Voice aur video calls lein",
                            color = softTextColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (callEnabled)
                                (if (isEnglish) "Patients can call you now" else "Log abhi aapko call kar sakte hain")
                            else (if (isEnglish) "Off — turn on to receive calls" else "Band — calls lene ke liye on karein"),
                            color = softTextColor.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Switch(
                        checked = callEnabled,
                        onCheckedChange = { enabled ->
                            val key = counselorKey.ifBlank { counselorId }
                            if (key.isNotBlank()) dashboardViewModel.setCallAvailability(key, enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SaharaStrongGreen,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = SaharaCoral.copy(alpha = 0.6f)
                        )
                    )
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (page) {
                            0 -> {
                                QuickStat(
                                    value = "${displayAlerts.size}",
                                    label = if (isEnglish) "Active Alerts" else "Naye Alerts",
                                    valueColor = SaharaCoral, softTextColor = softTextColor, hazeState = bgHazeState, modifier = Modifier.weight(1f)
                                )
                                // Show the EFFECTIVE status the user sees: "Online" only when
                                // connected AND not invisible; "Hidden" when invisible but still
                                // connected; "Offline" only on the brief presence-not-yet-set
                                // case (also useful if the helper ever fails to attach).
                                val effectiveLabel = when {
                                    isInvisible -> if (isEnglish) "Hidden" else "Hidden"
                                    isOnline    -> if (isEnglish) "Online" else "Online"
                                    else        -> if (isEnglish) "Offline" else "Offline"
                                }
                                val effectiveColor = when {
                                    isInvisible -> SaharaPeach
                                    isOnline    -> SaharaStrongGreen
                                    else        -> SaharaCoral
                                }
                                QuickStat(
                                    value = effectiveLabel,
                                    label = if (isEnglish) "Duty Status" else "Duty Status",
                                    valueColor = effectiveColor,
                                    softTextColor = softTextColor, hazeState = bgHazeState, modifier = Modifier.weight(1f)
                                )
                            }
                            1 -> {
                                val ratingVal = (realtimeData?.get("rating") as? Double) ?: 0.0
                                val sessionCount = ((realtimeData?.get("sessionCount") as? Long)?.toInt()) ?: 0
                                QuickStat(
                                    value = "%.1f".format(ratingVal),
                                    label = if (isEnglish) "Your Rating" else "Aapki Rating",
                                    valueColor = SaharaWarning, softTextColor = softTextColor, hazeState = bgHazeState, icon = Icons.Default.Star, modifier = Modifier.weight(1f)
                                )
                                QuickStat(
                                    value = "$sessionCount",
                                    label = if (isEnglish) "Sessions" else "Sessions",
                                    valueColor = SaharaSky, softTextColor = softTextColor, hazeState = bgHazeState, modifier = Modifier.weight(1f)
                                )
                            }
                            2 -> {
                                val totalRatings = ((realtimeData?.get("totalRatings") as? Long)?.toInt()) ?: 0
                                QuickStat(
                                    value = "< 2m", label = if (isEnglish) "Response Time" else "Jawab Ka Waqt",
                                    valueColor = SaharaGreen, softTextColor = softTextColor, hazeState = bgHazeState, modifier = Modifier.weight(1f)
                                )
                                QuickStat(
                                    value = "$totalRatings",
                                    label = if (isEnglish) "Total Ratings" else "Ratings",
                                    valueColor = SaharaStrongGreen, softTextColor = softTextColor, hazeState = bgHazeState, modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(3) { iteration ->
                        val isSelected = pagerState.currentPage == iteration
                        val color = if (isSelected) SaharaStrongGreen else softTextColor.copy(alpha = 0.2f)
                        val width by animateDpAsState(targetValue = if (isSelected) 24.dp else 8.dp, label = "dotWidth")

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .clip(CircleShape)
                                .background(color)
                                .height(8.dp)
                                .width(width)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 32.dp, bottom = 16.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = SaharaCoral,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEnglish) "High Risk Alerts (>60%)" else "Shadeed Khatre Ke Alerts (>60%)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = softTextColor
                    )
                }

                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    if (displayAlerts.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isEnglish) "No active alerts in your area." else "Aapke ilaqay mein koi naya alert nahi.",
                                color = softTextColor.copy(alpha = 0.5f)
                            )
                        }
                    } else {
                        displayAlerts.forEach { alert ->
                            AlertCard(
                                alert = alert,
                                isEnglish = isEnglish,
                                hazeState = bgHazeState,
                                softTextColor = softTextColor,
                                isDark = isDark,
                                onVideoClick = {
                                    if (counselorId.isNotBlank())
                                        dashboardViewModel.acknowledgeAlert(alert.id, counselorId)
                                    else if (counselorKey.isNotBlank())
                                        dashboardViewModel.acknowledgeAlert(alert.id, counselorKey)
                                },
                                onVoiceClick = {
                                    if (counselorId.isNotBlank())
                                        dashboardViewModel.acknowledgeAlert(alert.id, counselorId)
                                    else if (counselorKey.isNotBlank())
                                        dashboardViewModel.acknowledgeAlert(alert.id, counselorKey)
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }

                
                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 32.dp, bottom = 16.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = SaharaSky,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEnglish) "Active Chats" else "Active Chats",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = softTextColor
                    )
                    if (chatSessions.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(SaharaSky.copy(alpha = 0.15f),
                                    androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "${chatSessions.size}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SaharaSky
                            )
                        }
                    }
                }

                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    if (canLoadChatSessions && isChatSessionsLoading) {
                        SaharaCard(
                            variant = CardVariant.GLASS,
                            hazeState = bgHazeState,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = SaharaSky,
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = if (isEnglish) "Loading active chats..." else "Active chats load ho rahe hain...",
                                        color = softTextColor.copy(alpha = 0.55f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    } else if (chatSessions.isEmpty()) {
                        SaharaCard(
                            variant = CardVariant.GLASS,
                            hazeState = bgHazeState,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.ChatBubbleOutline,
                                        contentDescription = null,
                                        tint = softTextColor.copy(0.3f),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (isEnglish) "No active chats yet" else "Abhi koi chat nahi",
                                        color = softTextColor.copy(alpha = 0.5f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    } else {
                        chatSessions.forEach { session ->
                            val userId      = session["uid"]?.toString() ?: ""
                            val sessionId   = session["sessionId"]?.toString() ?: ""
                            val lastMsg     = session["lastMessage"]?.toString() ?: ""
                            val sessKey     = session["counselorKey"]?.toString() ?: counselorKey
                            CounselorChatCard(
                                userId        = userId,
                                sessionId     = sessionId,
                                lastMessage   = lastMsg,
                                isEnglish     = isEnglish,
                                hazeState     = bgHazeState,
                                softTextColor = softTextColor,
                                isDark        = isDark,
                                onOpenChat    = {
                                    
                                    navController.navigate("counselor-opens-chat/$userId/$sessKey")
                                }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun CounselorChatCard(
    userId: String,
    sessionId: String,
    lastMessage: String,
    isEnglish: Boolean,
    hazeState: HazeState,
    softTextColor: Color,
    isDark: Boolean,
    onOpenChat: () -> Unit
) {
    SaharaCard(
        variant = CardVariant.GLASS,
        hazeState = hazeState,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(SaharaSky.copy(alpha = 0.2f),
                        androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = SaharaSky,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEnglish) "User (${userId.take(8)}...)" else "User (${userId.take(8)}...)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (isDark) Color.White else Color.Black
                )
                Text(
                    text = lastMessage.ifBlank { if (isEnglish) "No messages yet" else "Koi message nahi" },
                    style = MaterialTheme.typography.bodySmall,
                    color = softTextColor.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onOpenChat,
                modifier = Modifier
                    .size(40.dp)
                    .background(SaharaSky.copy(alpha = 0.15f),
                        androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Open Chat",
                    tint = SaharaSky,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun QuickStat(
    value: String,
    label: String,
    valueColor: Color,
    softTextColor: Color,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    SaharaCard(
        variant = CardVariant.GLASS,
        hazeState = hazeState,
        modifier = modifier.height(100.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = valueColor, modifier = Modifier.size(22.dp).padding(end = 4.dp))
                }
                Text(
                    text = value,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = valueColor
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = softTextColor,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun AlertCard(
    alert: RiskAlert,
    isEnglish: Boolean,
    hazeState: HazeState,
    softTextColor: Color,
    isDark: Boolean,
    onVideoClick: () -> Unit,
    onVoiceClick: () -> Unit
) {
    val isCritical = alert.riskScore >= 80
    val scoreColor = if (isCritical) SaharaCoral else SaharaWarning

    val voiceBtnBg = if (isDark) Color.White.copy(alpha = 0.15f) else SaharaStrongGreen.copy(alpha = 0.15f)
    val voiceBtnContent = if (isDark) Color.White else SaharaStrongGreen

    SaharaCard(
        variant = CardVariant.GLASS,
        hazeState = hazeState,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(SaharaStrongGreen.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Anonymous User",
                        tint = SaharaStrongGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEnglish) "Anonymous User" else "Gumnaam Sarif",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (isDark) Color.White else Color.Black,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isEnglish) "Age: ${alert.ageRange}" else "Umar: ${alert.ageRange}",
                        style = MaterialTheme.typography.labelMedium,
                        color = softTextColor,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = if (isEnglish) alert.timeAgoEn else alert.timeAgoUr,
                    style = MaterialTheme.typography.labelSmall,
                    color = softTextColor.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = softTextColor.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isEnglish) alert.distanceEn else alert.distanceUr,
                        style = MaterialTheme.typography.bodySmall,
                        color = softTextColor.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .background(scoreColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isEnglish) "Risk: ${alert.riskScore}%" else "Khatra: ${alert.riskScore}%",
                        fontWeight = FontWeight.ExtraBold,
                        color = scoreColor,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onVoiceClick,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = voiceBtnBg),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, tint = voiceBtnContent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (isEnglish) "Voice" else "Voice", color = voiceBtnContent, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onVideoClick,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SaharaStrongGreen.copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (isEnglish) "Video" else "Video", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
