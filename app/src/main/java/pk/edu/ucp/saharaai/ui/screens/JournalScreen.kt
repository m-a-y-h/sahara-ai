package pk.edu.ucp.saharaai.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.ui.components.BottomNav
import pk.edu.ucp.saharaai.ui.components.GlassAlertDialog
import pk.edu.ucp.saharaai.ui.components.ButtonVariant
import pk.edu.ucp.saharaai.ui.components.CardVariant
import pk.edu.ucp.saharaai.ui.components.SaharaButton
import pk.edu.ucp.saharaai.ui.components.SaharaCard
import pk.edu.ucp.saharaai.ui.components.HazeBackButton
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.utils.showLocalizedToast
import pk.edu.ucp.saharaai.viewmodels.JournalViewModel
import java.text.SimpleDateFormat
import java.util.*


data class JournalEntry(
    val id: String = "",
    val mood: String = "",
    val prompt: String = "",
    val notes: String = "",
    val timestamp: Long = 0L
)


fun getMoodVisuals(mood: String): Pair<ImageVector, Color> = when (mood) {
    "Great", "happy" -> Icons.Default.SentimentVerySatisfied to SaharaStrongGreen
    "Good", "calm" -> Icons.Default.SentimentSatisfiedAlt to SaharaSky
    "Okay", "stressed" -> Icons.Default.SentimentNeutral to SaharaPeach
    "Bad", "sad" -> Icons.Default.SentimentDissatisfied to Color(0xFF7B61FF)
    "angry" -> Icons.Default.MoodBad to SaharaCoral
    "craving" -> Icons.Default.Warning to Color(0xFFFF6B35)
    else -> Icons.Default.SentimentNeutral to Color.Gray
}

fun formatJournalDate(timestamp: Long): String {
    val tz     = TimeZone.getDefault()
    val entry  = Calendar.getInstance(tz).apply { timeInMillis = timestamp }
    val today  = Calendar.getInstance(tz)
    val yesterday = Calendar.getInstance(tz).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault()).apply { timeZone = tz }
    val timeStr = timeFmt.format(entry.time).lowercase()
    return when {
        entry.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        entry.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)     -> "Today, $timeStr"
        entry.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
        entry.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Yesterday, $timeStr"
        else -> "${SimpleDateFormat("MMM d", Locale.getDefault()).format(entry.time)}, $timeStr"
    }
}


@Composable
fun JournalScreen(
    navController: NavController,
    onNavigateBack: () -> Unit = { navController.popBackStack() },
    isEnglish: Boolean = false,
    journalViewModel: JournalViewModel = viewModel()
) {
    val context = LocalContext.current
    val isDark  = isSystemInDarkTheme()
    val hazeState = remember { HazeState() }

    
    val entries = journalViewModel.entries
    val isLoading = journalViewModel.isLoading
    val isSaving = journalViewModel.isSaving
    val entriesToday = journalViewModel.todayEntryCount
    var showNewEntry by remember { mutableStateOf(false) }
    var selectedMood by remember { mutableStateOf<String?>(null) }
    var contextText  by remember { mutableStateOf("") }
    var notesText    by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<JournalEntry?>(null) }

    val isFormComplete = selectedMood != null && notesText.isNotBlank()

    val dynamicPrompt = when (selectedMood) {
        "stressed", "angry", "sad", "craving" ->
            if (isEnglish) "What's bothering you?" else "Kya pareshani hai?"
        else          -> if (isEnglish) "I am thankful for..."  else "Aaj ki achi baat..."
    }

    LaunchedEffect(Unit) {
        journalViewModel.loadEntries()
    }

    
    Box(modifier = Modifier.fillMaxSize()) {
        ScreenBackdrop(hazeState)

        Scaffold(
            bottomBar = { BottomNav(navController = navController, hazeState = hazeState) },
            containerColor = Color.Transparent,
            floatingActionButton = {
                AnimatedVisibility(
                    visible = !showNewEntry && entriesToday < JournalViewModel.MAX_DAILY_ENTRIES,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    FloatingActionButton(
                        onClick = { showNewEntry = true },
                        containerColor = SaharaStrongGreen,
                        contentColor   = Color.White,
                        shape          = CircleShape
                    ) { Icon(Icons.Default.Add, contentDescription = "New Entry") }
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp)
            ) {

                
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HazeBackButton(onClick = onNavigateBack, hazeState = hazeState)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                if (isEnglish) "My Diary" else "Meri Diary",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = SaharaStrongGreen
                            )
                            Text(
                                if (isEnglish) "Express your feelings" else "Apne jazbaat likhein",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }

                item {
                    val atLimit = entriesToday >= JournalViewModel.MAX_DAILY_ENTRIES
                    Text(
                        text = if (isEnglish) {
                            "$entriesToday/${JournalViewModel.MAX_DAILY_ENTRIES} journal entries used today"
                        } else {
                            "Aaj $entriesToday/${JournalViewModel.MAX_DAILY_ENTRIES} journal entries use huin"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (atLimit) SaharaCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    if (atLimit) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (isEnglish)
                                "Daily journal limit reached. You can record another entry tomorrow."
                            else
                                "Aaj ki journal limit puri ho gayi. Kal nayi entry likhein.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SaharaCoral,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                
                if (showNewEntry) {
                    item {
                        SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (isEnglish) "How are you feeling today?" else "Aaj kaisa feel ho raha hai?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(20.dp))

                            
                            MOODS.chunked(3).forEach { rowMoods ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowMoods.forEach { mood ->
                                        MoodCard(
                                            mood = mood,
                                            isSelected = selectedMood == mood.id,
                                            isSaving = isSaving,
                                            isEnglish = isEnglish,
                                            softText = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            selectedMood = mood.id
                                        }
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            OutlinedTextField(
                                value = contextText,
                                onValueChange = { contextText = it },
                                label = { Text(dynamicPrompt) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor   = SaharaStrongGreen,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.2f),
                                    focusedContainerColor   = MaterialTheme.colorScheme.surface.copy(0.5f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(0.5f)
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = notesText,
                                onValueChange = { notesText = it },
                                label = { Text(if (isEnglish) "Detailed Notes *" else "Tafseeli Notes *") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 4,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor   = SaharaStrongGreen,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.2f),
                                    focusedContainerColor   = MaterialTheme.colorScheme.surface.copy(0.5f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(0.5f)
                                )
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SaharaButton(
                                    text = if (isEnglish) "Cancel" else "Khatam",
                                    onClick = {
                                        showNewEntry = false
                                        selectedMood = null
                                        contextText  = ""
                                        notesText    = ""
                                    },
                                    variant = ButtonVariant.OUTLINE,
                                    modifier = Modifier.weight(1f),
                                    isEnglish = isEnglish
                                )

                                if (isSaving) {
                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = SaharaStrongGreen, modifier = Modifier.size(32.dp))
                                    }
                                } else {
                                    SaharaButton(
                                        text = if (isEnglish) "Save Entry" else "Save Karein",
                                        onClick = {
                                            if (!isFormComplete) {
                                                context.showLocalizedToast(isEnglish, "Select a mood and write a note.", "Mood aur note zaroori hain.")
                                                return@SaharaButton
                                            }
                                            journalViewModel.saveEntry(
                                                mood = selectedMood!!,
                                                prompt = contextText,
                                                notes = notesText
                                            ) { saved, failureMessage ->
                                                if (saved) {
                                                    showNewEntry = false
                                                    selectedMood = null
                                                    contextText  = ""
                                                    notesText    = ""
                                                    context.showLocalizedToast(isEnglish, "Entry saved!", "Entry save ho gayi!")
                                                } else {
                                                    context.showLocalizedToast(
                                                        isEnglish,
                                                        failureMessage ?: "Failed to save. Try again.",
                                                        "Save nahi hua. Dobara koshish karein.",
                                                    )
                                                }
                                            }
                                        },
                                        variant  = ButtonVariant.DEFAULT,
                                        modifier = Modifier.weight(1f).alpha(if (isFormComplete) 1f else 0.5f),
                                        isEnglish = isEnglish
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }

                
                item {
                    Text(
                        if (isEnglish) "Past Entries" else "Pichli Entries",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                
                if (isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = SaharaStrongGreen)
                        }
                    }
                } else if (entries.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Book, null, tint = SaharaStrongGreen.copy(0.4f), modifier = Modifier.size(56.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    if (isEnglish) "No entries yet.\nTap + to write your first entry." else "Abhi koi entry nahi.\n+ dabayein aur apni pehli entry likhein.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    
                    items(entries, key = { it.id }) { entry ->
                        val (icon, color) = getMoodVisuals(entry.mood)

                        SaharaCard(
                            variant  = CardVariant.DASHBOARD_GLASS,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(color.copy(0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                
                                Column(modifier = Modifier.weight(1f)) {
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            formatJournalDate(entry.timestamp),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = color.copy(0.12f)
                                        ) {
                                            Text(
                                                journalMoodLabel(entry.mood, isEnglish),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = color,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    
                                    if (entry.prompt.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "\"${entry.prompt}\"",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = color,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        entry.notes,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { deleteTarget = entry },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete entry",
                                        tint = SaharaCoral.copy(0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        deleteTarget?.let { target ->
            GlassAlertDialog(
                hazeState = hazeState,
                isDark = isDark,
                onDismissRequest = { deleteTarget = null },
                title = { Text(if (isEnglish) "Delete Entry?" else "Entry Delete Karein?") },
                text  = { Text(if (isEnglish) "This journal entry will be permanently deleted." else "Yeh entry hamesha ke liye delete ho jaegi.") },
                confirmButton = {
                    TextButton(onClick = {
                        deleteTarget = null
                        journalViewModel.deleteEntry(target.id) { deleted ->
                            if (!deleted) {
                                context.showLocalizedToast(isEnglish, "Delete failed.", "Delete nahi hua.")
                            }
                        }
                    }) {
                        Text(if (isEnglish) "Delete" else "Delete", color = SaharaCoral, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text(if (isEnglish) "Cancel" else "Cancel Karein")
                    }
                }
            )
        }
    }
}

private fun journalMoodLabel(mood: String, isEnglish: Boolean): String =
    moodDefById(mood)?.let { if (isEnglish) it.labelEn else it.labelUr } ?: when (mood) {
        "Great" -> if (isEnglish) "Great" else "Bohat Acha"
        "Good" -> if (isEnglish) "Good" else "Acha"
        "Okay" -> if (isEnglish) "Okay" else "Theek"
        "Bad" -> if (isEnglish) "Bad" else "Kharab"
        else -> mood
    }

private data class MoodDef(
    val id: String,
    val emoji: String,
    val labelEn: String,
    val labelUr: String,
    val color: Color
)

private val MOODS = listOf(
    MoodDef("happy", "😊", "Happy", "Khush", SaharaStrongGreen),
    MoodDef("calm", "🙂", "Calm", "Pur-sukoon", SaharaSky),
    MoodDef("stressed", "😐", "Stressed", "Pareshan", SaharaPeach),
    MoodDef("sad", "😔", "Sad", "Udaas", Color(0xFF7B61FF)),
    MoodDef("angry", "😠", "Angry", "Gussa", SaharaCoral),
    MoodDef("craving", "⚠️", "Craving", "Craving", Color(0xFFFF6B35))
)

private fun moodDefById(id: String): MoodDef? = MOODS.firstOrNull { it.id == id }

@Composable
private fun MoodCard(
    mood: MoodDef,
    isSelected: Boolean,
    isSaving: Boolean,
    isEnglish: Boolean,
    softText: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) mood.color.copy(alpha = 0.18f) else softText.copy(alpha = 0.06f),
        label = "journalMoodBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) mood.color else softText.copy(alpha = 0.12f),
        label = "journalMoodBorder"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 1f,
        label = "journalMoodScale"
    )

    Surface(
        onClick = onClick,
        enabled = !isSaving,
        modifier = modifier
            .height(86.dp)
            .scale(scale),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = mood.emoji,
                fontSize = 24.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (isEnglish) mood.labelEn else mood.labelUr,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) mood.color else softText.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
