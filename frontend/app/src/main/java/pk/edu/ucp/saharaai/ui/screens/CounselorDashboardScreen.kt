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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.R
import pk.edu.ucp.saharaai.ui.components.*
import pk.edu.ucp.saharaai.ui.theme.*

data class RiskAlert(
    val id: String,
    val ageRange: String,
    val riskScore: Int,
    val distanceEn: String,
    val distanceUr: String,
    val timeAgoEn: String,
    val timeAgoUr: String
)

@Composable
fun CounselorDashboardScreen(
    navController: NavController,
    isEnglish: Boolean
) {
    val isDark = isSystemInDarkTheme()
    val bgHazeState = remember { HazeState() }
    val softTextColor = if (isDark) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.75f)

    val dummyAlerts = listOf(
        RiskAlert("1", "18-24", 85, "2 miles away", "2 mile door", "2 mins ago", "2 minute pehle"),
        RiskAlert("2", "25-35", 72, "In your district", "Aapke zila mein", "14 mins ago", "14 minute pehle"),
        RiskAlert("3", "Under 18", 64, "5 miles away", "5 mile door", "1 hour ago", "1 ghanta pehle")
    )

    val infiniteTransition = rememberInfiniteTransition(label = "blobs")
    val xOffset by infiniteTransition.animateFloat(
        initialValue = -80f,
        targetValue = 80f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Reverse),
        label = "x"
    )

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
                modifier = Modifier.size(350.dp).offset(x = (-50 + xOffset).dp, y = (-100).dp)
                    .background(
                        if (isDark) SaharaCoral.copy(0.15f) else SaharaCoral.copy(0.2f),
                        CircleShape
                    ).blur(100.dp)
            )
            Box(
                modifier = Modifier.size(400.dp).align(Alignment.BottomEnd)
                    .offset(x = (50 - xOffset).dp, y = 150.dp).background(
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isDark) Color.Black.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = SaharaStrongGreen
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Counselor Dashboard" else "Counselor Dashboard",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = SaharaStrongGreen
                        )
                        Text(
                            text = if (isEnglish) "Region: Lahore, Punjab" else "Ilaqa: Lahore, Punjab",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
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
                                    value = "${dummyAlerts.size}", label = if (isEnglish) "Active Alerts" else "Naye Alerts",
                                    valueColor = SaharaCoral, softTextColor = softTextColor, hazeState = bgHazeState, modifier = Modifier.weight(1f)
                                )
                                QuickStat(
                                    value = "Online", label = if (isEnglish) "Duty Status" else "Duty Status",
                                    valueColor = SaharaStrongGreen, softTextColor = softTextColor, hazeState = bgHazeState, modifier = Modifier.weight(1f)
                                )
                            }
                            1 -> {
                                QuickStat(
                                    value = "4.9", label = if (isEnglish) "Your Rating" else "Aapki Rating",
                                    valueColor = SaharaWarning, softTextColor = softTextColor, hazeState = bgHazeState, icon = Icons.Default.Star, modifier = Modifier.weight(1f)
                                )
                                QuickStat(
                                    value = "124", label = if (isEnglish) "Sessions" else "Sessions",
                                    valueColor = SaharaSky, softTextColor = softTextColor, hazeState = bgHazeState, modifier = Modifier.weight(1f)
                                )
                            }
                            2 -> {
                                QuickStat(
                                    value = "< 2m", label = if (isEnglish) "Response Time" else "Jawab Ka Waqt",
                                    valueColor = SaharaGreen, softTextColor = softTextColor, hazeState = bgHazeState, modifier = Modifier.weight(1f)
                                )
                                QuickStat(
                                    value = "850+", label = if (isEnglish) "Helped" else "Madad Ki",
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
                    if (dummyAlerts.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isEnglish) "No active alerts in your area." else "Aapke ilaqay mein koi naya alert nahi.",
                                color = softTextColor.copy(alpha = 0.5f)
                            )
                        }
                    } else {
                        dummyAlerts.forEach { alert ->
                            AlertCard(
                                alert = alert,
                                isEnglish = isEnglish,
                                hazeState = bgHazeState,
                                softTextColor = softTextColor,
                                isDark = isDark,
                                onVideoClick = { /* TODO: Launch Video Session */ },
                                onVoiceClick = { /* TODO: Launch Voice Session */ }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
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