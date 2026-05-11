package pk.edu.ucp.saharaai.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.R
import pk.edu.ucp.saharaai.ui.components.CardVariant
import pk.edu.ucp.saharaai.ui.components.SaharaCard
import pk.edu.ucp.saharaai.ui.theme.*

@Composable
fun NgoDashboardScreen(
    navController: NavController,
    onNavigateBack: () -> Unit = { navController.popBackStack() },
    isEnglish: Boolean = false
) {
    val isDark = isSystemInDarkTheme()
    val bgHazeState = remember { HazeState() }
    val softTextColor = if (isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f)
    val titleColor = SaharaStrongGreen

    val pieData = listOf(
        15f to SaharaCoral,
        30f to SaharaWarning,
        40f to SaharaSky,
        15f to SaharaGreen
    )
    val lineDataCases = listOf(45f, 52f, 38f, 61f, 48f, 35f)
    val lineDataRecovered = listOf(32f, 48f, 55f, 42f, 58f, 62f)

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

        Box(modifier = Modifier.fillMaxSize().hazeSource(bgHazeState)) {
            Image(
                painter = painterResource(id = R.drawable.sahara_bg5),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (isDark) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.1f)))
            }

            Box(modifier = Modifier.size(350.dp).offset(x = (-80).dp, y = (-50).dp).rotate(blobRotation).scale(blobScale).background(if (isDark) SaharaGreen.copy(0.15f) else SaharaGreen.copy(0.2f), CircleShape).blur(80.dp))
            Box(modifier = Modifier.size(400.dp).align(Alignment.BottomEnd).offset(x = 100.dp, y = 50.dp).rotate(-blobRotation).scale(blobScale).background(if (isDark) SaharaSky.copy(0.15f) else SaharaSky.copy(0.25f), CircleShape).blur(96.dp))
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
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isDark) Color.Black.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = titleColor)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = if (isEnglish) "NGO Dashboard" else "NGO Dashboard",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = titleColor
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 24.dp, bottom = 24.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    NgoStatCard(
                        title = if (isEnglish) "Total Cases" else "Kul Cases",
                        value = "1,000",
                        icon = Icons.Default.Group,
                        textColor = softTextColor,
                        hazeState = bgHazeState,
                        modifier = Modifier.weight(1f)
                    )
                    NgoStatCard(
                        title = if (isEnglish) "Active Recovery" else "Zair-e-Ilaj",
                        value = "253",
                        icon = Icons.Default.TrendingUp,
                        textColor = softTextColor,
                        hazeState = bgHazeState,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    NgoStatCard(
                        title = if (isEnglish) "Completed" else "Mukammal",
                        value = "747",
                        icon = Icons.Default.CheckCircle,
                        textColor = softTextColor,
                        hazeState = bgHazeState,
                        modifier = Modifier.weight(1f)
                    )
                    NgoStatCard(
                        title = if (isEnglish) "High Risk" else "Shadeed Khatra",
                        value = "42",
                        icon = Icons.Default.Warning,
                        textColor = softTextColor,
                        hazeState = bgHazeState,
                        modifier = Modifier.weight(1f),
                        isAlert = true
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                SaharaCard(variant = CardVariant.GLASS, hazeState = bgHazeState, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "Risk Distribution" else "Khatre ki Taqseem",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = softTextColor
                        )
                        IconButton(
                            onClick = { /* Download Report */ },
                            modifier = Modifier
                                .size(32.dp)
                                .background(if (isDark) Color.White.copy(0.1f) else Color.Black.copy(0.05f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Download",
                                tint = softTextColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.size(150.dp)) {
                            var startAngle = -90f
                            pieData.forEach { (percentage, color) ->
                                val sweepAngle = (percentage / 100f) * 360f
                                drawArc(
                                    color = color,
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle,
                                    useCenter = false,
                                    style = Stroke(width = 45.dp.toPx(), cap = StrokeCap.Butt)
                                )
                                startAngle += sweepAngle
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("100%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = softTextColor)
                            Text(if (isEnglish) "Assessed" else "Jaize", style = MaterialTheme.typography.labelSmall, color = softTextColor.copy(alpha = 0.7f))
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            LegendItem(color = SaharaCoral, label = if (isEnglish) "Severe 15%" else "Shadeed 15%", textColor = softTextColor)
                            LegendItem(color = SaharaWarning, label = if (isEnglish) "Substantial 30%" else "Khatarnak 30%", textColor = softTextColor)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            LegendItem(color = SaharaSky, label = if (isEnglish) "Moderate 40%" else "Darmiyana 40%", textColor = softTextColor)
                            LegendItem(color = SaharaGreen, label = if (isEnglish) "Low 15%" else "Mamooli 15%", textColor = softTextColor)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                SaharaCard(variant = CardVariant.GLASS, hazeState = bgHazeState, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "Monthly Trends" else "Mahana Rujhanat",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = softTextColor
                        )
                        IconButton(
                            onClick = { /* Download Report */ },
                            modifier = Modifier
                                .size(32.dp)
                                .background(if (isDark) Color.White.copy(0.1f) else Color.Black.copy(0.05f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Download",
                                tint = softTextColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val maxVal = 70f
                            val stepX = size.width / (lineDataCases.size - 1).coerceAtLeast(1)

                            val casesPath = Path()
                            val recoveredPath = Path()

                            lineDataCases.forEachIndexed { i, value ->
                                val x = i * stepX
                                val y = size.height - ((value / maxVal) * size.height)
                                if (i == 0) casesPath.moveTo(x, y) else casesPath.lineTo(x, y)
                            }

                            lineDataRecovered.forEachIndexed { i, value ->
                                val x = i * stepX
                                val y = size.height - ((value / maxVal) * size.height)
                                if (i == 0) recoveredPath.moveTo(x, y) else recoveredPath.lineTo(x, y)
                            }

                            drawPath(casesPath, color = SaharaWarning, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                            drawPath(recoveredPath, color = SaharaGreen, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

                            lineDataCases.forEachIndexed { i, value ->
                                val x = i * stepX
                                val y = size.height - ((value / maxVal) * size.height)
                                drawCircle(color = Color.White, radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
                                drawCircle(color = SaharaWarning, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
                            }
                            lineDataRecovered.forEachIndexed { i, value ->
                                val x = i * stepX
                                val y = size.height - ((value / maxVal) * size.height)
                                drawCircle(color = Color.White, radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
                                drawCircle(color = SaharaGreen, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(SaharaWarning))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isEnglish) "New Cases" else "Naye Cases", style = MaterialTheme.typography.labelMedium, color = softTextColor)

                        Spacer(modifier = Modifier.width(24.dp))

                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(SaharaGreen))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isEnglish) "Recovered" else "Sehat Yab", style = MaterialTheme.typography.labelMedium, color = softTextColor)
                    }
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun NgoStatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    textColor: Color,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    isAlert: Boolean = false
) {
    val primaryColor = if (isAlert) SaharaCoral else SaharaGreen
    val iconBgColor = if (isAlert) SaharaCoral.copy(alpha = 0.15f) else SaharaGreen.copy(alpha = 0.15f)

    SaharaCard(
        variant = CardVariant.GLASS,
        hazeState = hazeState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(iconBgColor, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = textColor)
            Spacer(modifier = Modifier.height(2.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, color = textColor, fontWeight = FontWeight.SemiBold)
    }
}