package pk.edu.ucp.saharaai.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SentimentNeutral
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.ui.components.BottomNav
import pk.edu.ucp.saharaai.ui.components.ButtonVariant
import pk.edu.ucp.saharaai.ui.components.CardVariant
import pk.edu.ucp.saharaai.ui.components.SaharaButton
import pk.edu.ucp.saharaai.ui.components.SaharaCard
import pk.edu.ucp.saharaai.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class JournalEntry(
    val dateEn: String,
    val dateUr: String,
    val mood: String,
    val promptAnswerEn: String,
    val promptAnswerUr: String,
    val noteEn: String,
    val noteUr: String
)

object JournalDataStore {
    val entries = mutableStateListOf<JournalEntry>()

    init {
        val timeZone = TimeZone.getTimeZone("Asia/Karachi")
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault()).apply {
            this.timeZone = timeZone
        }
        val now = Calendar.getInstance(timeZone)

        val cal1 = now.clone() as Calendar
        cal1.add(Calendar.HOUR_OF_DAY, -5)

        val isToday1 = now.get(Calendar.DAY_OF_YEAR) == cal1.get(Calendar.DAY_OF_YEAR) && now.get(Calendar.YEAR) == cal1.get(Calendar.YEAR)
        val prefixEn1 = if (isToday1) "Today" else "Yesterday"
        val prefixUr1 = if (isToday1) "Aaj" else "Kal"
        val timeStr1 = timeFormat.format(cal1.time).lowercase()

        val cal2 = now.clone() as Calendar
        cal2.add(Calendar.HOUR_OF_DAY, -24)
        val timeStr2 = timeFormat.format(cal2.time).lowercase()

        entries.add(
            JournalEntry(
                dateEn = "$prefixEn1, $timeStr1",
                dateUr = "$prefixUr1, $timeStr1",
                mood = "Great",
                promptAnswerEn = "Family support",
                promptAnswerUr = "Family ka support",
                noteEn = "Today was a very productive day.",
                noteUr = "Aaj bahut productive din tha."
            )
        )
        entries.add(
            JournalEntry(
                dateEn = "Yesterday, $timeStr2",
                dateUr = "Kal, $timeStr2",
                mood = "Okay",
                promptAnswerEn = "Meditation helped",
                promptAnswerUr = "Meditation se behtar laga",
                noteEn = "I was a bit stressed but pushed through.",
                noteUr = "Thoda stress tha lekin guzar gaya."
            )
        )
    }
}

fun getMoodVisuals(mood: String): Pair<ImageVector, Color> {
    return when (mood) {
        "Great" -> Icons.Default.SentimentVerySatisfied to SaharaStrongGreen
        "Good" -> Icons.Default.SentimentSatisfiedAlt to SaharaSky
        "Okay" -> Icons.Default.SentimentNeutral to SaharaPeach
        "Bad" -> Icons.Default.SentimentDissatisfied to SaharaCoral
        else -> Icons.Default.SentimentNeutral to Color.Gray
    }
}

@Composable
fun JournalScreen(
    navController: NavController,
    onNavigateBack: () -> Unit = { navController.popBackStack() },
    isEnglish: Boolean = false
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val hazeState = remember { HazeState() }

    var showNewEntry by remember { mutableStateOf(false) }
    var selectedMood by remember { mutableStateOf<String?>(null) }
    var contextText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }

    val pastEntries = JournalDataStore.entries

    val isFormComplete = selectedMood != null && notesText.isNotBlank()

    val dynamicPromptEn = when (selectedMood) {
        "Bad", "Okay" -> "What's bothering you?"
        else -> "I am thankful for..."
    }

    val dynamicPromptUr = when (selectedMood) {
        "Bad", "Okay" -> "Kya pareshani hai?"
        else -> "Aaj ki achi baat..."
    }

    val bgGradient = if (isDark) {
        listOf(SaharaStrongGreen.copy(alpha = 0.2f), MaterialTheme.colorScheme.background.copy(alpha = 0.6f), MaterialTheme.colorScheme.background)
    } else {
        listOf(SaharaStrongGreen.copy(alpha = 0.25f), SaharaPeach.copy(alpha = 0.1f), MaterialTheme.colorScheme.background.copy(alpha = 0.2f))
    }
    val blobColor1 = SaharaStrongGreen.copy(alpha = if (isDark) 0.25f else 0.15f)
    val blobColor2 = SaharaSky.copy(alpha = if (isDark) 0.2f else 0.18f)

    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val blobRotation by infiniteTransition.animateFloat(initialValue = -10f, targetValue = 10f, animationSpec = infiniteRepeatable(tween(6000, easing = EaseInOutSine), RepeatMode.Reverse), label = "rotation")
    val blobScale by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 1.15f, animationSpec = infiniteRepeatable(tween(5000, easing = EaseInOutSine), RepeatMode.Reverse), label = "scale")

    Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState).background(Brush.verticalGradient(bgGradient))) {
        Box(modifier = Modifier.size(350.dp).offset(x = (-80).dp, y = (-50).dp).rotate(blobRotation).scale(blobScale).background(Brush.radialGradient(listOf(blobColor1, Color.Transparent))))
        Box(modifier = Modifier.size(400.dp).align(Alignment.BottomEnd).offset(x = 100.dp, y = 50.dp).rotate(-blobRotation).scale(blobScale).background(Brush.radialGradient(listOf(blobColor2, Color.Transparent))))

        Scaffold(
            bottomBar = { BottomNav(navController = navController, hazeState = hazeState) },
            containerColor = Color.Transparent,
            floatingActionButton = {
                if (!showNewEntry) {
                    FloatingActionButton(
                        onClick = { showNewEntry = true },
                        containerColor = SaharaStrongGreen,
                        contentColor = Color.White,
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
                    ) { Icon(Icons.Default.Add, contentDescription = "New Entry") }
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 24.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onNavigateBack, modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SaharaStrongGreen)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(if (isEnglish) "My Diary" else "Meri Diary", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = SaharaStrongGreen)
                            Text(if (isEnglish) "Express your feelings" else "Apne jazbaat likhein", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }

                if (showNewEntry) {
                    item {
                        SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth()) {
                            Text(if (isEnglish) "How are you feeling today?" else "Aaj kaisa feel ho raha hai?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(20.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                MoodButton(if (isEnglish) "Great" else "Bohat Acha", Icons.Default.SentimentVerySatisfied, selectedMood == "Great", SaharaStrongGreen) { selectedMood = "Great" }
                                MoodButton(if (isEnglish) "Good" else "Acha", Icons.Default.SentimentSatisfiedAlt, selectedMood == "Good", SaharaSky) { selectedMood = "Good" }
                                MoodButton(if (isEnglish) "Okay" else "Theek", Icons.Default.SentimentNeutral, selectedMood == "Okay", SaharaPeach) { selectedMood = "Okay" }
                                MoodButton(if (isEnglish) "Bad" else "Kharab", Icons.Default.SentimentDissatisfied, selectedMood == "Bad", SaharaCoral) { selectedMood = "Bad" }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            OutlinedTextField(
                                value = contextText,
                                onValueChange = { contextText = it },
                                label = { Text(if (isEnglish) dynamicPromptEn else dynamicPromptUr) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SaharaStrongGreen, unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = notesText,
                                onValueChange = { notesText = it },
                                label = { Text(if (isEnglish) "Detailed Notes" else "Tafseeli Notes") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 4,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SaharaStrongGreen, unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SaharaButton(
                                    text = if (isEnglish) "Cancel" else "Khatam",
                                    onClick = {
                                        showNewEntry = false
                                        selectedMood = null
                                        contextText = ""
                                        notesText = ""
                                    },
                                    variant = ButtonVariant.OUTLINE,
                                    modifier = Modifier.weight(1f),
                                    isEnglish = isEnglish
                                )
                                SaharaButton(
                                    text = if (isEnglish) "Save Entry" else "Save Karein",
                                    onClick = {
                                        if (!isFormComplete) {
                                            Toast.makeText(context, if (isEnglish) "Please select a mood and write a note." else "Zaroori maloomat likhein.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault()).apply {
                                                timeZone = TimeZone.getTimeZone("Asia/Karachi")
                                            }
                                            val currentTime = timeFormat.format(Calendar.getInstance().time).lowercase()

                                            JournalDataStore.entries.add(
                                                0,
                                                JournalEntry(
                                                    dateEn = "Today, $currentTime", dateUr = "Aaj, $currentTime",
                                                    mood = selectedMood!!,
                                                    promptAnswerEn = contextText, promptAnswerUr = contextText,
                                                    noteEn = notesText, noteUr = notesText
                                                )
                                            )
                                            showNewEntry = false
                                            selectedMood = null
                                            contextText = ""
                                            notesText = ""
                                        }
                                    },
                                    variant = ButtonVariant.DEFAULT,
                                    modifier = Modifier.weight(1f).alpha(if (isFormComplete) 1f else 0.5f),
                                    isEnglish = isEnglish
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }

                item {
                    Text(if (isEnglish) "Past Entries" else "Pichli Entries", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 8.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                }

                items(pastEntries) { entry ->
                    val date = if (isEnglish) entry.dateEn else entry.dateUr
                    val note = if (isEnglish) entry.noteEn else entry.noteUr
                    val promptAnswer = if (isEnglish) entry.promptAnswerEn else entry.promptAnswerUr

                    val (icon, color) = getMoodVisuals(entry.mood)

                    SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(44.dp).background(color.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(date, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                                if (promptAnswer.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.1f)) {
                                        Text(
                                            text = promptAnswer,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = color,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MoodButton(label: String, icon: ImageVector, isSelected: Boolean, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) color.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}