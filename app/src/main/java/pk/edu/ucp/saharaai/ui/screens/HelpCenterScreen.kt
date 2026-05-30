package pk.edu.ucp.saharaai.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.ui.components.*
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.viewmodels.HelpCenterViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

private data class FaqItem(val question: String, val answer: String)
private data class ContactItem(val icon: ImageVector, val color: Color, val label: String, val action: () -> Unit)

@Composable
fun HelpCenterScreen(
    onNavigateBack    : () -> Unit,
    onNavigateToEmergency: () -> Unit,
    isEnglish         : Boolean = false,
    helpViewModel: HelpCenterViewModel = viewModel(),
) {
    val isDark       = isSystemInDarkTheme()
    val primaryGreen = if (isDark) SaharaStrongGreen else SaharaGreen
    val softText     = if (isDark) Color.White.copy(.9f) else Color.Black.copy(.85f)
    val hazeState    = remember { HazeState() }
    val context      = LocalContext.current
    val reports = helpViewModel.reports
    val unresolvedBugCount = reports.count { it.status != "RESOLVED" }
    var showBugReports by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { helpViewModel.initialize() }

    val bgGradient = if (isDark)
        listOf(SaharaStrongGreen.copy(.2f), MaterialTheme.colorScheme.background.copy(.6f), MaterialTheme.colorScheme.background)
    else
        listOf(SaharaStrongGreen.copy(.25f), SaharaPeach.copy(.1f), MaterialTheme.colorScheme.background.copy(.2f))

    val blobMotion = rememberBackdropBlobMotion()

    
    val faqs = if (isEnglish) listOf(
        FaqItem("What is Sahara AI?",
            "Sahara AI is an AI-powered Android application designed to detect early signs of illegal narcotic substance abuse and emotional distress among youth in Pakistan. It analyzes user behavior, emotional patterns, voice tone, and interaction data using AI to identify risks early — without directly detecting drug usage. It also provides multilingual support, anonymous counseling, and recovery tools."),
        FaqItem("What substances does Sahara AI focus on?",
            "Sahara AI specifically targets illegal narcotic substances such as cocaine, heroin, methamphetamine, cannabis, ecstasy, and similar harmful addictive drugs. It does NOT deal with prescribed medicines, pharmaceutical drugs, or any medical treatment systems."),
        FaqItem("How does the AI detect addiction risk?",
            "The app uses behavioral questionnaire analysis (DAST-10), voice emotion detection, mood pattern tracking, and machine learning models to classify your addiction risk level and identify early warning signs. Results are shown as Low, Moderate, or High risk."),
        FaqItem("Is my data private and secure?",
            "Yes. All user profiles are anonymous and data is encrypted (AES-256). You can enable Email Mask from your Profile to hide your real email address. We never share personal data with third parties. The system is designed with privacy-first principles for Pakistan's stigma-sensitive society."),
        FaqItem("How do I connect with a counselor?",
            "Go to the Counselors tab on the home screen. Browse available counselors and start a secure, anonymous real-time chat. All conversations are encrypted and your identity remains protected."),
        FaqItem("What is the DAST-10 Assessment?",
            "DAST-10 (Drug Abuse Screening Test) is a clinically validated 10-question screening tool that measures the degree of problems related to substance use. Your score is classified as Low (0–5), Moderate (6–10), or High (11+) risk. Access it from the Assessment screen on the dashboard."),
        FaqItem("Can I use the app without internet?",
            "Some features like mood journaling work offline. However, AI chat, counselor messaging, risk assessment, voice emotion analysis, and Firebase sync all require an active internet connection."),
        FaqItem("How do I reset my password?",
            "On the login screen, tap 'Forgot Password'. Enter your registered email and a reset link will be sent within a few minutes. Check your spam folder if it doesn't arrive."),
        FaqItem("Is Sahara AI a replacement for medical treatment?",
            "No. Sahara AI is a support and early-detection tool, not a substitute for professional medical advice, clinical diagnosis, or psychiatric treatment. In a medical emergency, always call 115 or visit your nearest hospital immediately."),
        FaqItem("Who is Sahara AI designed for?",
            "The app is designed for Pakistani students, teenagers, and young adults facing emotional stress, peer pressure, depression, or addiction-related challenges. It is available in both English and Roman Urdu.")
    ) else listOf(
        FaqItem("Sahara AI kya hai?",
            "Sahara AI ek AI-powered Android app hai jo Pakistan ke naujawanon mein ghair qanooni narcotic maddon ke istemal ke aarey marks pehchaanta hai. Ye user ke behavior, jazbaati patterns, awaaz, aur data ko analyze karta hai. Isme anonymous counseling, mood tracking, aur recovery tools bhi hain."),
        FaqItem("Sahara AI kin maddon par focus karta hai?",
            "Sahara AI sirf ghair qanooni narcotic madde jaise cocaine, heroin, methamphetamine, cannabis, ecstasy wagerah par focus karta hai. Ye prescription medicines ya pharmaceutical drugs se bilkul deal nahi karta."),
        FaqItem("AI risk kaise detect karta hai?",
            "App DAST-10 questionnaire, awaaz ki emotion analysis, mood patterns, aur machine learning models istemal karta hai. Nateeja Low, Moderate, ya High risk ke tor par dikhaya jata hai."),
        FaqItem("Kya mera data mehfooz hai?",
            "Haan. Sabhi profiles anonymous hain aur data AES-256 se encrypt hota hai. Profile mein Email Mask on karke apni real email chupayi ja sakti hai. Hum aapka data kisi ke saath share nahi karte."),
        FaqItem("Counselor se kaise baat karein?",
            "Home screen par Counselors tab mein jayen. Koi bhi counselor chunein aur secure, anonymous chat shuru karein. Saari guftagoo encrypt hoti hai."),
        FaqItem("DAST-10 kya hai?",
            "DAST-10 ek 10 sawalon wala clinically validated test hai jo substance use ka darje determine karta hai. Score Low (0-5), Moderate (6-10), ya High (11+) risk bata ta hai. Dashboard par Assessment screen se test dein."),
        FaqItem("Kya bina internet ke app chalega?",
            "Mood journaling jaise kuch features offline kaam karte hain. Lekin AI chat, counselor messaging, assessment, awaaz analysis, aur Firebase sync ke liye internet zaroori hai."),
        FaqItem("Password bhool gaya — kya karein?",
            "Login screen par 'Forgot Password' dabayein. Email darj karein aur kuch minton mein reset link aajayega. Spam folder bhi check karein."),
        FaqItem("Kya Sahara AI medical ilaaj ki jagah hai?",
            "Nahi. Sahara AI sirf support aur early detection tool hai, professional medical treatment ka mutabadil nahi. Emergency mein 115 call karein ya qareebi hospital jayen."),
        FaqItem("Ye app kin ke liye hai?",
            "Ye app Pakistani talba, teenagers, aur naujawanon ke liye hai jo jazbati dabao, peer pressure, depression, ya addiction se guzar rahe hain. English aur Roman Urdu dono mein available hai.")
    )

    
    var expandedIdx by remember { mutableIntStateOf(-1) }

    
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .hazeSource(hazeState)
                .background(Brush.verticalGradient(bgGradient))
        ) {
            Box(Modifier.size(350.dp).offset((-80).dp, (-50).dp).primaryBlobMotion(blobMotion)
                .background(Brush.radialGradient(listOf(SaharaStrongGreen.copy(if (isDark) .25f else .15f), Color.Transparent))))
            Box(Modifier.size(400.dp).align(Alignment.BottomEnd).offset(100.dp, 50.dp).secondaryBlobMotion(blobMotion)
                .background(Brush.radialGradient(listOf(SaharaSky.copy(if (isDark) .2f else .18f), Color.Transparent))))
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))

            
            Row(verticalAlignment = Alignment.CenterVertically) {
                HazeBackButton(onClick = onNavigateBack, hazeState = hazeState, tint = primaryGreen)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        if (isEnglish) "Help Center" else "Madad",
                        style      = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color      = primaryGreen
                    )
                    Text(
                        if (isEnglish) "FAQs & support resources" else "Sawalaat aur madad",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            
            SectionHeading(
                title = if (isEnglish) "Immediate Help" else "Fauri Madad",
                subtitle = if (isEnglish) "Emergency and support channels" else "Emergency aur support rabtay",
                icon = Icons.Default.HealthAndSafety,
                color = SaharaCoral,
                softText = softText,
            )

            UrgentSupportPanel(
                isEnglish = isEnglish,
                hazeState = hazeState,
                unresolvedBugCount = unresolvedBugCount,
                onEmergency = onNavigateToEmergency,
                onEmail = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:support@saharaai.pk")
                        putExtra(Intent.EXTRA_SUBJECT, "Sahara AI Support")
                    }
                    context.startActivity(Intent.createChooser(intent, "Send Email"))
                },
                onBug = { showBugReports = true },
            )

            Spacer(Modifier.height(28.dp))

            
            Text(
                if (isEnglish) "Frequently Asked Questions" else "Aksar Poochhe Jane Wale Sawalaat",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = softText,
                modifier   = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )

            SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    faqs.forEachIndexed { idx, faq ->
                        FaqRow(
                            faq       = faq,
                            isExpanded = expandedIdx == idx,
                            onToggle  = { expandedIdx = if (expandedIdx == idx) -1 else idx },
                            softText  = softText,
                            accentColor = primaryGreen
                        )
                        if (idx < faqs.lastIndex) {
                            HorizontalDivider(
                                Modifier.padding(horizontal = 16.dp),
                                color = softText.copy(.08f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            
            SectionHeading(
                title = if (isEnglish) "About Sahara" else "Sahara Ke Bare Mein",
                subtitle = if (isEnglish) "Purpose, privacy and access" else "Maqsad, privacy aur rasai",
                icon = Icons.Default.Info,
                color = primaryGreen,
                softText = softText,
            )

            AboutSaharaPanel(
                isEnglish = isEnglish,
                hazeState = hazeState,
                softText = softText,
                primaryGreen = primaryGreen,
            )

            Spacer(Modifier.height(40.dp))
        }
        if (showBugReports) {
            BugReportsDialog(
                hazeState = hazeState,
                reports = reports,
                isEnglish = isEnglish,
                viewModel = helpViewModel,
                onDismiss = { showBugReports = false },
            )
        }
    }
}

@Composable
private fun FaqRow(
    faq        : FaqItem,
    isExpanded : Boolean,
    onToggle   : () -> Unit,
    softText   : Color,
    accentColor: Color
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(remember { MutableInteractionSource() }, null) { onToggle() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                faq.question,
                modifier   = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                color      = if (isExpanded) accentColor else softText
            )
            Icon(
                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null,
                tint     = if (isExpanded) accentColor else softText.copy(.4f),
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(
            visible = isExpanded,
            enter   = expandVertically(),
            exit    = shrinkVertically()
        ) {
            Text(
                faq.answer,
                style    = MaterialTheme.typography.bodyMedium,
                color    = softText.copy(.75f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    softText: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(color.copy(.14f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = softText)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = softText.copy(.62f))
        }
    }
}

@Composable
private fun UrgentSupportPanel(
    isEnglish: Boolean,
    hazeState: HazeState,
    unresolvedBugCount: Int,
    onEmergency: () -> Unit,
    onEmail: () -> Unit,
    onBug: () -> Unit,
) {
    SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onEmergency() },
                shape = RoundedCornerShape(20.dp),
                color = SaharaCoral.copy(alpha = .12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, SaharaCoral.copy(alpha = .25f)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(48.dp).background(SaharaCoral, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.LocalPhone, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isEnglish) "Emergency Helplines" else "Hanggami Madad",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SaharaCoral,
                        )
                        Text(
                            if (isEnglish) "Call for urgent, immediate support" else "Foran madad ke liye helpline kholein",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SaharaCoral)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondarySupportAction(
                    icon = Icons.Default.Email,
                    color = SaharaSky,
                    title = if (isEnglish) "Email Support" else "Email Madad",
                    subtitle = if (isEnglish) "Write to us" else "Rabita karein",
                    onClick = onEmail,
                    modifier = Modifier.weight(1f),
                )
                SecondarySupportAction(
                    icon = Icons.Default.BugReport,
                    color = if (unresolvedBugCount > 0) SaharaCoral else SaharaLavender,
                    title = if (isEnglish) "Report Bug" else "Bug Batayen",
                    subtitle = if (unresolvedBugCount > 0) {
                        if (isEnglish) "$unresolvedBugCount open" else "$unresolvedBugCount baqi"
                    } else {
                        if (isEnglish) "Technical issue" else "App masla"
                    },
                    badgeCount = unresolvedBugCount,
                    onClick = onBug,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                if (isEnglish) {
                    "Sahara is not a replacement for emergency or medical care."
                } else {
                    "Sahara emergency ya medical ilaaj ka mutabadil nahi hai."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SecondarySupportAction(
    icon: ImageVector,
    color: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    badgeCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(17.dp),
        color = color.copy(alpha = .08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = .16f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Box(
                Modifier.size(38.dp).background(color.copy(.16f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                if (badgeCount > 0) {
                    Badge(
                        containerColor = SaharaCoral,
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-7).dp),
                    ) {
                        Text("$badgeCount", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AboutSaharaPanel(
    isEnglish: Boolean,
    hazeState: HazeState,
    softText: Color,
    primaryGreen: Color,
) {
    SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp)
                        .background(
                            Brush.linearGradient(listOf(primaryGreen.copy(.9f), SaharaSky.copy(.82f))),
                            RoundedCornerShape(17.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Spa, null, tint = Color.White, modifier = Modifier.size(27.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sahara AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = primaryGreen)
                    Text(
                        if (isEnglish) "Recovery support with privacy" else "Privacy ke saath recovery support",
                        style = MaterialTheme.typography.bodySmall,
                        color = softText.copy(.66f),
                    )
                }
                Surface(shape = RoundedCornerShape(50), color = primaryGreen.copy(.12f)) {
                    Text("v1.0.0", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = primaryGreen, style = MaterialTheme.typography.labelMedium)
                }
            }

            Text(
                if (isEnglish) {
                    "Designed to support mental wellness and identify early addiction-risk signals through private, consent-based tools."
                } else {
                    "Mental wellness aur addiction risk ki ibtedai alamat ko samajhne ke liye privacy par mabni support tools."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = softText.copy(.8f),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AboutPill(Icons.Default.Shield, if (isEnglish) "Private" else "Mehfooz", SaharaStrongGreen, Modifier.weight(1f))
                AboutPill(Icons.Default.Language, "Roman Urdu", SaharaLavender, Modifier.weight(1f))
                AboutPill(Icons.Default.LocationOn, "Pakistan", SaharaCoral, Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AboutDetailTile(
                    icon = Icons.Default.Psychology,
                    color = SaharaSky,
                    title = if (isEnglish) "Purpose" else "Maqsad",
                    value = if (isEnglish) "Wellness + risk support" else "Wellness aur risk support",
                    modifier = Modifier.weight(1f),
                )
                AboutDetailTile(
                    icon = Icons.Default.Security,
                    color = SaharaStrongGreen,
                    title = if (isEnglish) "Privacy" else "Privacy",
                    value = if (isEnglish) "Anonymous profile" else "Anonymous profile",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AboutPill(icon: ImageVector, text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = color.copy(.09f)) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(17.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

@Composable
private fun AboutDetailTile(
    icon: ImageVector,
    color: Color,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(15.dp), color = color.copy(.07f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
