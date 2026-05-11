package pk.edu.ucp.saharaai.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import pk.edu.ucp.saharaai.ui.components.*
import pk.edu.ucp.saharaai.ui.theme.*

data class QuizQuestion(
    val textEn: String,
    val textUr: String,
    val isReverseScored: Boolean = false
)

enum class QuizType { DAST10, DAST20, YOUTH_ASSESSMENT }

private val dast10Questions = listOf(
    QuizQuestion("Have you used drugs other than for medical reasons?", "Kya aapne medical zaroorat ke bina koi drugs use kiye hain?"),
    QuizQuestion("Do you use more than one drug at a time?", "Kya aap ek time mein ek se zyada drugs use karte hain?"),
    QuizQuestion("Are you able to stop using drugs when you want to?", "Kya aap jab chahein drugs chhor sakte hain?", isReverseScored = true),
    QuizQuestion("Have you had blackouts or flashbacks from drug use?", "Kya drugs ki wajah se aapko blackouts (kuch yaad na aana) hue hain?"),
    QuizQuestion("Do you ever feel bad or guilty about your drug use?", "Kya aapko drugs use karne par bura ya guilty feel hota hai?"),
    QuizQuestion("Does your family ever complain about your drug use?", "Kya aapki family kabhi aapke drugs use karne par gussa ya shikayat karti hai?"),
    QuizQuestion("Have you neglected your family because of drugs?", "Kya drugs ki wajah se aapne apni family ko ignore kiya hai?"),
    QuizQuestion("Have you done illegal things to get drugs?", "Kya drugs lene ke liye aapne koi illegal (ghair-qanooni) kaam kiya hai?"),
    QuizQuestion("Have you felt sick (withdrawals) when you stopped drugs?", "Kya drugs rokne par aapko beemari ya takleef (withdrawals) feel hui?"),
    QuizQuestion("Have you had medical problems because of drugs?", "Kya drugs ki wajah se aapko koi medical masla hua hai?")
)

private val dast20Questions = listOf(
    QuizQuestion("Have you used drugs other than for medical reasons?", "Kya aapne medical zaroorat ke bina drugs use kiye hain?"),
    QuizQuestion("Have you abused prescription drugs?", "Kya aapne doctor ki di hui dawai zaroorat se zyada use ki hai?"),
    QuizQuestion("Do you use more than one drug at a time?", "Kya aap ek waqt mein 1 se zyada drugs use karte hain?"),
    QuizQuestion("Can you get through the week without using drugs?", "Kya aap drugs ke bina poora hafta guzaar sakte hain?", isReverseScored = true),
    QuizQuestion("Are you always able to stop using drugs when you want to?", "Kya aap jab chahein drugs rok sakte hain?", isReverseScored = true),
    QuizQuestion("Have you had blackouts or flashbacks from drug use?", "Kya drugs ki wajah se aapko blackouts hue hain?"),
    QuizQuestion("Do you ever feel bad or guilty about your drug use?", "Kya aapko drugs lene par guilty feel hota hai?"),
    QuizQuestion("Does your spouse or parents complain about your drugs?", "Kya aapke parents ya partner drugs par shikayat karte hain?"),
    QuizQuestion("Has drug abuse created problems with your partner or parents?", "Kya drugs ki wajah se parents ya partner ke sath maslay hue?"),
    QuizQuestion("Have you lost friends because of your drug use?", "Kya drugs ki wajah se aapke dost aapse door hue?"),
    QuizQuestion("Have you neglected your family because of drugs?", "Kya drugs ki wajah se aapne apni family ko ignore kiya hai?"),
    QuizQuestion("Have you been in trouble at work because of drug abuse?", "Kya kaam ya job par drugs ki wajah se koi masla hua?"),
    QuizQuestion("Have you lost a job because of drug abuse?", "Kya drugs ki wajah se aapki job chhooti?"),
    QuizQuestion("Have you gotten into fights when under the influence?", "Nashe ki halat mein kya aapki laraian (fights) hui hain?"),
    QuizQuestion("Have you done illegal things to get drugs?", "Kya drugs lene ke liye aapne koi illegal kaam kiya hai?"),
    QuizQuestion("Have you been arrested for possession of illegal drugs?", "Kya drugs rakhne ki wajah se aap kabhi arrest hue hain?"),
    QuizQuestion("Have you ever experienced withdrawal symptoms (felt sick)?", "Kya drugs rokne par aap beemar (withdrawals) feel karte hain?"),
    QuizQuestion("Have you had medical problems because of drugs?", "Kya drugs ki wajah se aapko koi medical problem hui hai?"),
    QuizQuestion("Have you gone to anyone for help for a drug problem?", "Kya aapne kabhi drugs chhorne ke liye kisi se help mangi hai?"),
    QuizQuestion("Have you been involved in a treatment program specifically related to drug use?", "Kya aap kisi drug rehab ya treatment program ka hissa rahe hain?")
)

private val youthQuestions = listOf(
    QuizQuestion("Have you used drugs other than those required for medical reasons?", "Kya aapne medical zaroorat ke ilawa koi drugs use kiye hain?"),
    QuizQuestion("Have you abused prescription drugs?", "Kya aapne doctor ki di hui dawai zaroorat se zyada use ki hai?"),
    QuizQuestion("Do you abuse more than one drug at a time?", "Kya aap ek waqt mein ek se zyada drugs use koh hain?"),
    QuizQuestion("Can you get through the week without using drugs?", "Kya aap poora hafta drugs ke bina guzaar sakte hain?", isReverseScored = true),
    QuizQuestion("Are you always able to stop using drugs when you want to?", "Kya aap jab chahein drugs rok sakte hain?", isReverseScored = true),
    QuizQuestion("Have you had \"blackouts\" or \"flashbacks\" as a result or drug use?", "Kya drugs ki wajah se aapko blackouts (kuch yaad na rehna) ya flashbacks hue hain?"),
    QuizQuestion("Do you every feel bad or guilty about your drug use?", "Kya aapko drugs use karne par bura ya guilty feel hota hai?"),
    QuizQuestion("Do your parents ever complain about your involvement with drugs?", "Kya aapke parents aapke drugs use karne par shikayat karte hain?"),
    QuizQuestion("Has drug abuse created problems between you and your parents?", "Kya drugs ki wajah se aapka aur aapke parents ka koi masla hua hai?"),
    QuizQuestion("Have you lost friends because of your use of drugs?", "Kya drugs ki wajah se aapke dost aapse door hue hain?"),
    QuizQuestion("Have you neglected your family because of your use of drugs?", "Kya drugs ki wajah se aapne apni family ko ignore kiya hai?"),
    QuizQuestion("Have you been in trouble at school because of drug abuse?", "Kya school mein drugs ki wajah se aapko kisi pareshani ka saamna karna para?"),
    QuizQuestion("Have you missed school assignments because of drug abuse?", "Kya drugs ki wajah se aapke school assignments reh gaye hain?"),
    QuizQuestion("Have you gotten into fights when under the influence of drugs?", "Nashe ki halat mein kya aapki laraian (fights) hui hain?"),
    QuizQuestion("Have you engaged in illegal activities in order to obtain drugs?", "Kya drugs lene ke liye aapne koi illegal (ghair-qanooni) kaam kiya hai?"),
    QuizQuestion("Have you been arrested for possession of illegal drugs?", "Kya drugs rakhne ki wajah se aap kabhi arrest hue hain?"),
    QuizQuestion("Have you ever experienced withdrawal symptoms (felt sick) when you stopped taking drugs?", "Kya drugs rokne par aapko beemari ya takleef (withdrawals) feel hoti hai?"),
    QuizQuestion("Have you had medical problems as a result of your drug use (e.g. memory loss, hepatitis, convulsions, bleeding, etc.)?", "Kya drugs ki wajah se aapko koi medical masla (maslan memory loss, hepatitis) hua hai?"),
    QuizQuestion("Have you gone to anyone for help for drug problem?", "Kya aapne kabhi drugs chhorne ke liye kisi se help mangi hai?"),
    QuizQuestion("Have you been involved in a treatment program specifically related to drug use?", "Kya aap kisi drug treatment ya rehab program ka hissa rahe hain?")
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AssessmentScreen(
    navController: NavController,
    onNavigateBack: () -> Unit,
    onComplete: (score: Int) -> Unit,
    isEnglish: Boolean = false
) {
    val isDark = isSystemInDarkTheme()
    var selectedQuiz by remember { mutableStateOf<List<QuizQuestion>?>(null) }
    var currentQ by remember { mutableIntStateOf(0) }
    val answers = remember { mutableStateMapOf<Int, Boolean>() }

    val accentGreen = SaharaStrongGreen
    val bgGradient = if (isDark) {
        listOf(
            SaharaStrongGreen.copy(alpha = 0.2f),
            MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.background
        )
    } else {
        listOf(
            SaharaStrongGreen.copy(alpha = 0.25f),
            SaharaPeach.copy(alpha = 0.1f),
            MaterialTheme.colorScheme.background.copy(alpha = 0.2f)
        )
    }

    val blob1Color = SaharaStrongGreen.copy(alpha = if (isDark) 0.25f else 0.15f)
    val blob2Color = SaharaStrongGreen.copy(alpha = if (isDark) 0.2f else 0.18f)

    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val blobRotation by infiniteTransition.animateFloat(
        initialValue = -10f, targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(6000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "rotation"
    )
    val blobScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(5000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(bgGradient))
    ) {
        Box(modifier = Modifier
            .size(350.dp)
            .offset(x = (-80).dp, y = (-50).dp)
            .rotate(blobRotation)
            .scale(blobScale)
            .background(Brush.radialGradient(listOf(blob1Color, Color.Transparent))))

        Box(modifier = Modifier
            .size(400.dp)
            .align(Alignment.BottomEnd)
            .offset(x = 100.dp, y = 50.dp)
            .rotate(-blobRotation)
            .scale(blobScale)
            .background(Brush.radialGradient(listOf(blob2Color, Color.Transparent))))

        Scaffold(
            bottomBar = { BottomNav(navController = navController, hazeState = null) },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
            ) {
                AssessmentHeader(isEnglish, onNavigateBack)
                Spacer(modifier = Modifier.height(32.dp))

                AnimatedContent(
                    targetState = selectedQuiz != null,
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    }, label = "ScreenTransition"
                ) { isQuizStarted ->
                    if (!isQuizStarted) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = if (isEnglish) "Select Assessment Type" else "Assessment Select Karein",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            QuizSelectionCard(
                                title = if (isEnglish) "Quick Test (DAST-10)" else "Short Test (DAST-10)",
                                description = if (isEnglish) "Standard 10-question evaluation." else "10 sawaalon par mushtamil aasan test.",
                                icon = Icons.Default.Timer,
                                color = SaharaStrongGreen,
                                isDark = isDark
                            ) { selectedQuiz = dast10Questions }

                            if (GlobalAppState.isMinor) {
                                val uriHandler = LocalUriHandler.current
                                val annotatedDescription = buildAnnotatedString {
                                    val fullText = if (isEnglish) 
                                        "Tailored for individuals under 18, authored by Dr. Harvey A. Skinner, 1982." 
                                        else "18 saal se kam umer logon ke liye design kiya gaya, authored by Dr. Harvey A. Skinner, 1982."
                                    
                                    val linkText = "Dr. Harvey A. Skinner, 1982"
                                    val startIndex = fullText.indexOf(linkText)
                                    val endIndex = startIndex + linkText.length
                                    
                                    append(fullText)
                                    
                                    if (startIndex >= 0) {
                                        addStringAnnotation(
                                            tag = "URL",
                                            annotation = "https://www.sandiego.edu/teamup/documents/DRL-DAST-2008.pdf",
                                            start = startIndex,
                                            end = endIndex
                                        )
                                        addStyle(
                                            style = SpanStyle(textDecoration = TextDecoration.Underline),
                                            start = startIndex,
                                            end = endIndex
                                        )
                                    }
                                }

                                QuizSelectionCard(
                                    title = if (isEnglish) "Dast 20 (Minors)" else "Dast 20 (Minors)",
                                    description = "",
                                    icon = Icons.Default.ChildCare,
                                    color = SaharaSky,
                                    isDark = isDark,
                                    annotatedDescription = annotatedDescription,
                                    onDescriptionClick = { offset ->
                                        annotatedDescription.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                            .firstOrNull()?.let { annotation ->
                                                uriHandler.openUri(annotation.item)
                                            }
                                    }
                                ) { selectedQuiz = youthQuestions }
                            } else {
                                QuizSelectionCard(
                                    title = if (isEnglish) "Detailed Test (DAST-20)" else "Detailed Test (DAST-20)",
                                    description = if (isEnglish) "Comprehensive 20-question evaluation." else "20 sawaalon par mushtamil mukammal test.",
                                    icon = Icons.Default.Assignment,
                                    color = SaharaPeach,
                                    isDark = isDark
                                ) { selectedQuiz = dast20Questions }
                            }
                        }
                    } else {
                        val questions = selectedQuiz!!
                        ActiveQuizSection(
                            questions = questions,
                            currentQ = currentQ,
                            answers = answers,
                            isEnglish = isEnglish,
                            onPrevious = { currentQ-- },
                            onNext = { currentQ++ },
                            onComplete = {
                                var score = 0
                                answers.forEach { (index, isYes) ->
                                    val q = questions[index]
                                    if (q.isReverseScored) {
                                        if (!isYes) score++
                                    } else {
                                        if (isYes) score++
                                    }
                                }

                                val normalizedScore = if (questions.size > 10) {
                                    when (score) {
                                        0 -> 0
                                        in 1..5 -> 1
                                        in 6..10 -> 3
                                        in 11..15 -> 6
                                        else -> 9
                                    }
                                } else {
                                    score
                                }

                                NotificationManager.logAssessment(
                                    oldScore = GlobalAppState.dast10Score,
                                    newScore = normalizedScore
                                )

                                GlobalAppState.dast10Score = normalizedScore
                                GlobalAppState.hasCompletedInitialAssessment = true
                                onComplete(normalizedScore)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AssessmentHeader(isEnglish: Boolean, onNavigateBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SaharaStrongGreen)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isEnglish) "Assessment" else "Jaeza",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = SaharaStrongGreen
            )
            Text(
                text = if (isEnglish) "Evaluate your situation" else "Halaat ka jaeza lein",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (GlobalAppState.isMinor) {
            Surface(
                shape = CircleShape,
                color = SaharaPeach.copy(alpha = 0.15f),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = SaharaPeach,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Minor",
                        style = MaterialTheme.typography.labelSmall,
                        color = SaharaPeach,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun QuizSelectionCard(
    title: String,
    description: String,
    icon: ImageVector,
    color: Color,
    isDark: Boolean,
    annotatedDescription: AnnotatedString? = null,
    onDescriptionClick: ((Int) -> Unit)? = null,
    onClick: () -> Unit
) {
    SaharaCard(
        variant = CardVariant.DASHBOARD_GLASS,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color.copy(alpha = if (isDark) 0.25f else 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(2.dp))
                if (annotatedDescription != null && onDescriptionClick != null) {
                    androidx.compose.foundation.text.ClickableText(
                        text = annotatedDescription,
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        onClick = onDescriptionClick
                    )
                } else {
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ActiveQuizSection(
    questions: List<QuizQuestion>,
    currentQ: Int,
    answers: MutableMap<Int, Boolean>,
    isEnglish: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onComplete: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (currentQ + 1) / questions.size.toFloat(),
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (isEnglish) "Progress" else "Progress", style = MaterialTheme.typography.labelMedium, color = SaharaStrongGreen)
            Text("${currentQ + 1} / ${questions.size}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = SaharaStrongGreen)
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = SaharaStrongGreen,
            trackColor = SaharaGreenLight.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(40.dp))

        AnimatedContent(
            targetState = currentQ,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally(initialOffsetX = { it }) + fadeIn() togetherWith slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
                } else {
                    slideInHorizontally(initialOffsetX = { -it }) + fadeIn() togetherWith slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                }
            }, label = "QuestionAnimation"
        ) { questionIndex ->
            SaharaCard(
                variant = CardVariant.DASHBOARD_GLASS,
                modifier = Modifier.fillMaxWidth().wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SaharaCoral.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = if (isEnglish) "In the past 12 months..." else "Pichle 12 mahino mein...",
                            style = MaterialTheme.typography.labelMedium,
                            color = SaharaCoral,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = if (isEnglish) questions[questionIndex].textEn else questions[questionIndex].textUr,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(120.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val isYesSelected = answers[questionIndex] == true
                        val isNoSelected = answers[questionIndex] == false

                        SaharaButton(
                            text = if (isEnglish) "Yes" else "Haan",
                            onClick = { answers[questionIndex] = true },
                            variant = if (isYesSelected) ButtonVariant.DEFAULT else ButtonVariant.OUTLINE,
                            modifier = Modifier.weight(1f),
                            isEnglish = isEnglish
                        )

                        SaharaButton(
                            text = if (isEnglish) "No" else "Nahi",
                            onClick = { answers[questionIndex] = false },
                            variant = if (isNoSelected) ButtonVariant.DEFAULT else ButtonVariant.OUTLINE,
                            modifier = Modifier.weight(1f),
                            isEnglish = isEnglish
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (currentQ > 0) {
                SaharaButton(
                    text = if (isEnglish) "Back" else "Pichla",
                    onClick = onPrevious,
                    variant = ButtonVariant.OUTLINE,
                    modifier = Modifier.weight(1f),
                    isEnglish = isEnglish
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            val hasAnsweredCurrent = answers.containsKey(currentQ)
            SaharaButton(
                text = if (currentQ < questions.size - 1) (if (isEnglish) "Next" else "Agla") else (if (isEnglish) "Finish" else "Mukammal"),
                onClick = {
                    if (currentQ < questions.size - 1) onNext() else onComplete()
                },
                enabled = hasAnsweredCurrent,
                variant = ButtonVariant.DEFAULT,
                modifier = Modifier.weight(1f),
                isEnglish = isEnglish
            )
        }
    }
}