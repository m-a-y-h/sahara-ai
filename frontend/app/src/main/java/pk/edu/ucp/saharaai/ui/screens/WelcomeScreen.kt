package pk.edu.ucp.saharaai.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.HazeColorEffect
import kotlinx.coroutines.isActive
import pk.edu.ucp.saharaai.R
import pk.edu.ucp.saharaai.ui.components.ButtonVariant
import pk.edu.ucp.saharaai.ui.components.SaharaButton
import pk.edu.ucp.saharaai.ui.components.SaharaCard
import pk.edu.ucp.saharaai.ui.components.CardVariant
import pk.edu.ucp.saharaai.ui.theme.*
import kotlin.math.sqrt
import kotlin.random.Random

data class Leaf(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var angle: Float,
    var vAngle: Float,
    val radius: Float,
    val color: Color
)

@Composable
fun FloatingLeavesBackground() {
    val density = LocalDensity.current
    var ticks by remember { mutableLongStateOf(0L) }
    val leaves = remember { mutableStateListOf<Leaf>() }

    val leafColors = listOf(
        SaharaGreen.copy(alpha = 0.5f),
        SaharaPeach.copy(alpha = 0.5f),
        SaharaSky.copy(alpha = 0.5f),
        SaharaGreenLight.copy(alpha = 0.5f)
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        LaunchedEffect(widthPx, heightPx) {
            if (leaves.isEmpty() && widthPx > 0 && heightPx > 0) {
                for (i in 0 until 40) {
                    val radius = Random.nextFloat() * 30f + 30f
                    leaves.add(
                        Leaf(
                            x = Random.nextFloat() * (widthPx - radius * 2) + radius,
                            y = Random.nextFloat() * (heightPx - radius * 2) + radius,
                            vx = (Random.nextFloat() - 0.5f) * 1.5f,
                            vy = (Random.nextFloat() - 0.5f) * 1.5f,
                            angle = Random.nextFloat() * 360f,
                            vAngle = (Random.nextFloat() - 0.5f) * 0.8f,
                            radius = radius,
                            color = leafColors.random()
                        )
                    )
                }
            }
        }

        LaunchedEffect(Unit) {
            while (isActive) {
                withFrameMillis {
                    if (widthPx > 0 && heightPx > 0) {
                        for (i in leaves.indices) {
                            val l1 = leaves[i]

                            if (l1.x - l1.radius < 0f) {
                                l1.x = l1.radius
                                l1.vx *= -1f
                            } else if (l1.x + l1.radius > widthPx) {
                                l1.x = widthPx - l1.radius
                                l1.vx *= -1f
                            }

                            if (l1.y - l1.radius < 0f) {
                                l1.y = l1.radius
                                l1.vy *= -1f
                            } else if (l1.y + l1.radius > heightPx) {
                                l1.y = heightPx - l1.radius
                                l1.vy *= -1f
                            }

                            for (j in i + 1 until leaves.size) {
                                val l2 = leaves[j]
                                val dx = l2.x - l1.x
                                val dy = l2.y - l1.y
                                val dist = sqrt(dx * dx + dy * dy)
                                val minDist = l1.radius + l2.radius

                                if (dist < minDist && dist > 0f) {
                                    val overlap = minDist - dist
                                    val nx = dx / dist
                                    val ny = dy / dist

                                    l1.x -= nx * (overlap / 2f)
                                    l1.y -= ny * (overlap / 2f)
                                    l2.x += nx * (overlap / 2f)
                                    l2.y += ny * (overlap / 2f)

                                    val kx = l1.vx - l2.vx
                                    val ky = l1.vy - l2.vy
                                    val p = 2f * (nx * kx + ny * ky) / 2f

                                    l1.vx -= p * nx
                                    l1.vy -= p * ny
                                    l2.vx += p * nx
                                    l2.vy += p * ny
                                }
                            }
                            l1.x += l1.vx
                            l1.y += l1.vy
                            l1.angle += l1.vAngle
                        }
                    }
                    ticks++
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            ticks
            leaves.forEach { leaf ->
                withTransform({
                    translate(left = leaf.x, top = leaf.y)
                    rotate(degrees = leaf.angle)
                }) {
                    val leafPath = Path().apply {
                        moveTo(0f, -leaf.radius * 1.2f)
                        quadraticTo(leaf.radius * 0.8f, 0f, 0f, leaf.radius * 1.2f)
                        quadraticTo(-leaf.radius * 0.8f, 0f, 0f, -leaf.radius * 1.2f)
                        close()
                    }
                    drawPath(path = leafPath, color = leaf.color)

                    val veinColor = leaf.color.copy(alpha = 0.3f)
                    val veinWidth = 1.5.dp.toPx()

                    drawLine(
                        color = veinColor,
                        start = Offset(0f, -leaf.radius * 1.2f),
                        end = Offset(0f, leaf.radius * 1.2f),
                        strokeWidth = veinWidth
                    )

                    for (j in 1..3) {
                        val y = -leaf.radius * 0.8f + (leaf.radius * 1.6f * j / 4f)
                        val sideX = leaf.radius * 0.4f
                        drawLine(
                            color = veinColor,
                            start = Offset(0f, y),
                            end = Offset(sideX, y - leaf.radius * 0.2f),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = veinColor,
                            start = Offset(0f, y),
                            end = Offset(-sideX, y - leaf.radius * 0.2f),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToSettings: () -> Unit,
    isEnglish: Boolean = false
) {
    var showPrivacyPolicy by remember { mutableStateOf(false) }

    val bgHazeState = remember { HazeState() }
    val rootHazeState = remember { HazeState() }

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
            ) {
                Image(
                    painter = painterResource(id = R.drawable.wl_welcome),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.23f))
                )

                FloatingLeavesBackground()
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(30.dp))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_logo),
                        contentDescription = "Sahara AI Logo",
                        modifier = Modifier.size(130.dp)
                    )

                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(color = SaharaStrongGreen, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)) {
                                append("SAHARA ")
                            }
                            withStyle(style = SpanStyle(color = Color.White, fontWeight = FontWeight.Light, letterSpacing = 1.sp)) {
                                append("AI")
                            }
                        },
                        style = MaterialTheme.typography.headlineLarge
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isEnglish) "Your gentle companion for healing" else "Aapki recovery aur sukoon ka sathi",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    GlassyFeatureItem(
                        title = if (isEnglish) "Safe & Private" else "Mehfooz aur Private",
                        desc = if (isEnglish) "Your journey will be protected" else "Aapka safar mehfooz ho ga",
                        icon = Icons.Default.Security,
                        iconTint = SaharaStrongGreen,
                        hazeState = bgHazeState
                    )
                    GlassyFeatureItem(
                        title = if (isEnglish) "Always Here for You" else "Hamesha Aapke Saath",
                        desc = if (isEnglish) "Gentle AI support" else "AI support hazir hai",
                        icon = Icons.Default.WbSunny,
                        iconTint = SaharaPeach,
                        hazeState = bgHazeState
                    )
                    GlassyFeatureItem(
                        title = if (isEnglish) "Talk to Someone" else "Kisi Se Baat Karein",
                        desc = if (isEnglish) "Connect with caring counselors" else "Behtareen counselors se raabta",
                        icon = Icons.AutoMirrored.Filled.Chat,
                        iconTint = SaharaSky,
                        hazeState = bgHazeState
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SaharaButton(
                        text = if (isEnglish) "Begin Your Journey" else "Apna Safar Shuru Karein",
                        onClick = onNavigateToRegister,
                        variant = ButtonVariant.SAHARASTRONGGREENGLASS,
                        isFullWidth = true,
                        isEnglish = isEnglish,
                        hazeState = bgHazeState,
                        forceDarkTheme = true
                    )

                    TextButton(onClick = onNavigateToLogin) {
                        Text(
                            text = if (isEnglish) "I already have an account" else "Mera account pehle se maujood hai",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = buildAnnotatedString {
                            append(if (isEnglish) "By continuing, you agree to our " else "Aage barhne ka matlab hai ke aap hamari ")
                            withStyle(style = SpanStyle(color = SaharaStrongGreen, fontWeight = FontWeight.SemiBold)) {
                                append("Privacy Policy")
                            }
                            if (!isEnglish) append(" mante hain")
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clickable { showPrivacyPolicy = true }
                    )
                }
            }

            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .statusBarsPadding()
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 12.dp)
                    .clip(CircleShape)
                    .hazeEffect(state = bgHazeState) {
                        blurEffect {
                            blurRadius = 12.dp
                            colorEffects = listOf(
                                HazeColorEffect.tint(Color.White.copy(alpha = 0.08f))
                            )
                        }
                    }
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.25f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Language Settings",
                    tint = SaharaStrongGreen,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (showPrivacyPolicy) {
            PrivacyPolicyOverlay(
                isEnglish = isEnglish,
                hazeState = rootHazeState,
                onDismiss = { showPrivacyPolicy = false }
            )
        }
    }
}

@Composable
fun GlassyFeatureItem(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    hazeState: HazeState
) {
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .hazeEffect(state = hazeState) {
                blurEffect {
                    blurRadius = 25.dp
                    colorEffects = listOf(
                        HazeColorEffect.tint(Color.White.copy(alpha = 0.08f))
                    )
                }
            }
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = shape
            )
            .drawBehind {
                drawLine(
                    color = Color.White.copy(alpha = 0.2f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .hazeEffect(state = hazeState) {
                        blurEffect {
                            blurRadius = 12.dp
                            colorEffects = listOf(
                                HazeColorEffect.tint(Color.White.copy(alpha = 0.12f))
                            )
                        }
                    }
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.4f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .offset(y = (-2).dp)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = desc,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                )
            }
        }
    }
}

@Composable
fun PrivacyPolicyOverlay(isEnglish: Boolean, hazeState: HazeState, onDismiss: () -> Unit) {
    val scrimAlpha = 0.5f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        SaharaCard(
            variant = CardVariant.GLASS,
            hazeState = hazeState,
            forceDarkTheme = true,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Privacy Policy",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = SaharaStrongGreen
                    )
                    Text(
                        if (isEnglish) "Last updated: January 2026" else "Aakhri update: January 2026",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .weight(1f)
            ) {
                Text(if (isEnglish) "Your Safe Space" else "Aapki Mehfooz Jagah", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (isEnglish)
                        "SAHARA AI is built specifically for youth dealing with non-prescription drug usage and mental health challenges. We know privacy is your absolute top priority. This policy explains how we mathematically guarantee it."
                    else
                        "SAHARA AI khaas tor par youth ke liye banaya gaya hai jo recovery aur mental health challenges face kar rahay hain. Hum jante hain ke aapki privacy sab se ahem hai. Ye policy explain karti hai ke hum isay kaise mehfooz banate hain.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(if (isEnglish) "Bank-Grade Encryption (HIPAA Compliant)" else "Medical-Grade Security (HIPAA Compliant)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (isEnglish)
                        "We employ AES-256 encryption across the entire platform. We adhere strictly to HIPAA guidelines to protect your highly sensitive behavioral and health data. No compromises."
                    else
                        "Hum poori app mein AES-256 encryption istemaal karte hain aur HIPAA guidelines ko follow karte hain taake aapka data bank ki tarah mehfooz rahay. Is mamlay mein koi compromise nahi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(if (isEnglish) "Counselor & NGO Anonymity" else "Counselor Aur NGO Anonymity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                LocalBulletPoint(if (isEnglish) "Counselors NEVER see your real name. You are shown only as a secure, anonymous alias on their dashboard." else "Counselors ko aapka asal naam kabhi nahi dikhaya jata. Aapki shanakht unke dashboard par puri tarah khufiya rehti hai.")
                LocalBulletPoint(if (isEnglish) "Partner NGOs only receive anonymized, geographical aggregate statistics (e.g., regional drug usage numbers) to assist in public health. Your PII (Personally Identifiable Information) is never shared." else "NGOs ko sirf ilaqay ke aam statistics (jaise numbers aur heatmaps) milte hain taake wo madad kar sakein. Aapki personal details (PII) bilkul share nahi ki jati.")

                Spacer(modifier = Modifier.height(24.dp))

                Text(if (isEnglish) "Data Collection" else "Data Ikkattha Karna (Data Collection)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                LocalBulletPoint(if (isEnglish) "Self-assessment responses to personalize your recovery path" else "Risk analysis aur recovery path ke liye aapke jawabaat")
                LocalBulletPoint(if (isEnglish) "Voice recordings for AI emotion detection (processed securely and strictly locally where possible)" else "Emotion detection ke liye voice recordings (zyada tar phone tak mehdood processing)")

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    if (isEnglish)
                        "By using Sahara AI, you agree to the terms outlined in this policy. Your conversations with our AI and counselors are strictly confidential."
                    else
                        "Sahara AI use karne ka matlab hai ke aap in terms ko mante hain. AI aur counselors ke sath aapki baatein bilkul raaz mein rakhi jati hain.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SaharaButton(
                text = if (isEnglish) "Understood" else "Samajh aa gayi",
                onClick = onDismiss,
                variant = ButtonVariant.GLASS,
                isFullWidth = true,
                isEnglish = isEnglish,
                hazeState = hazeState,
                forceDarkTheme = true
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun WelcomeScreenPreview() {
    WelcomeScreen({}, {}, {})
}

@Composable
private fun LocalBulletPoint(text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("• ", color = SaharaStrongGreen, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
    }
}