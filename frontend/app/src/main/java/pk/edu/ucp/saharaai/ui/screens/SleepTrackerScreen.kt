package pk.edu.ucp.saharaai.ui.screens

import android.app.Application
import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.navigation.NavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.ui.components.BottomNav
import pk.edu.ucp.saharaai.ui.components.ButtonVariant
import pk.edu.ucp.saharaai.ui.components.SaharaButton
import pk.edu.ucp.saharaai.ui.theme.*

data class SleepData(
    val dayEn: String,
    val dayUr: String,
    val hours: Float,
    val qualityEn: String,
    val qualityUr: String,
    val qualityType: String,
    val bedtime: String,
    val waketime: String
)

private fun hoursToType(h: Float)    = when { h >= 8f -> "excellent"; h >= 7f -> "good"; h >= 6f -> "okay"; else -> "poor" }
private fun hoursToLabelEn(h: Float) = when { h >= 8f -> "Excellent"; h >= 7f -> "Good"; h >= 6f -> "Okay"; else -> "Poor" }
private fun hoursToLabelUr(h: Float) = when { h >= 8f -> "Behtareen"; h >= 7f -> "Acha"; h >= 6f -> "Theek"; else -> "Kharab" }

@Composable
private fun qualityColor(type: String): Color = when (type) {
    "excellent" -> SaharaStrongGreen
    "good"      -> SaharaSky
    "okay"      -> SaharaWarning
    "poor"      -> SaharaCoral
    else        -> MaterialTheme.colorScheme.onSurfaceVariant
}


class SleepTrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("sleep_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    var sleepList by mutableStateOf(loadSleepList())
        private set

    var sleepGoal by mutableStateOf(prefs.getFloat("sleep_goal", 8f))
        private set

    fun updateGoal(newGoal: Float) {
        sleepGoal = newGoal
        prefs.edit().putFloat("sleep_goal", newGoal).apply()
    }

    fun logSleep(index: Int, data: SleepData) {
        val updatedList = sleepList.toMutableList()
        updatedList[index] = data
        sleepList = updatedList
        saveSleepList(updatedList)
    }

    private fun saveSleepList(list: List<SleepData>) {
        val json = gson.toJson(list)
        prefs.edit().putString("sleep_list", json).apply()
    }

    private fun loadSleepList(): List<SleepData> {
        val json = prefs.getString("sleep_list", null)
        return if (json != null) {
            val type = object : TypeToken<List<SleepData>>() {}.type
            gson.fromJson(json, type)
        } else {
            listOf(
                SleepData("Mon", "Mon", 7.5f, "Good", "Acha", "good", "22:30", "06:00"),
                SleepData("Tue", "Tue", 6.0f, "Okay", "Theek", "okay", "23:00", "05:00"),
                SleepData("Wed", "Wed", 8.0f, "Excellent", "Behtareen", "excellent", "22:00", "06:00"),
                SleepData("Thu", "Thu", 5.5f, "Poor", "Kharab", "poor", "00:00", "05:30"),
                SleepData("Fri", "Fri", 7.0f, "Good", "Acha", "good", "22:45", "05:45"),
                SleepData("Sat", "Sat", 8.5f, "Excellent", "Behtareen", "excellent", "22:00", "06:30"),
                SleepData("Sun", "Sun", -1f, "—", "—", "none", "—", "—")
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTrackerScreen(
    navController: NavController,
    onNavigateBack: () -> Unit = { navController.popBackStack() },
    isEnglish: Boolean = false,
    viewModel: SleepTrackerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val isDark = isSystemInDarkTheme()
    val hazeState = remember { HazeState() }
    val sleepList = viewModel.sleepList
    val sleepGoal = viewModel.sleepGoal

    val recorded = sleepList.filter { it.hours >= 0f }
    val avgHours = if (recorded.isEmpty()) 0f else recorded.map { it.hours }.average().toFloat()
    val todayLogged = sleepList[6].hours >= 0f

    var selectedDay by remember { mutableStateOf(6) }
    val selData = sleepList[selectedDay]

    val bedtimeState  = rememberTimePickerState(initialHour = 22, initialMinute = 30, is24Hour = true)
    val waketimeState = rememberTimePickerState(initialHour = 6,  initialMinute = 30, is24Hour = true)

    var bedtimeHour    by remember { mutableStateOf(-1) }
    var bedtimeMinute  by remember { mutableStateOf(0)  }
    var waketimeHour   by remember { mutableStateOf(-1) }
    var waketimeMinute by remember { mutableStateOf(0)  }

    var showBedtimePicker  by remember { mutableStateOf(false) }
    var showWaketimePicker by remember { mutableStateOf(false) }
    var showGoalDialog     by remember { mutableStateOf(false) }

    val bestDay      = recorded.maxByOrNull { it.hours }
    val worstDay     = recorded.minByOrNull { it.hours }
    val goalProgress = (avgHours / sleepGoal).coerceIn(0f, 1f)

    val calculatedHours: Float? = if (bedtimeHour >= 0 && waketimeHour >= 0) {
        val bedMin  = bedtimeHour  * 60 + bedtimeMinute
        val wakeMin = waketimeHour * 60 + waketimeMinute
        val diff    = if (wakeMin > bedMin) wakeMin - bedMin else (1440 - bedMin) + wakeMin
        diff / 60f
    } else null

    val insightEn = when (selData.qualityType) {
        "excellent" -> "Great sleep! Consistent early bedtimes are working well."
        "good"      -> "Solid rest. Maintain this schedule for best results."
        "okay"      -> "You can do better. Try sleeping 30 min earlier."
        "poor"      -> "Short sleep detected. Avoid screens 1 hr before bed."
        else        -> "Log tonight's sleep below to see your insight."
    }
    val insightUr = when (selData.qualityType) {
        "excellent" -> "Bohat achhi neend! Waqt par sonay ka silsila jari rakkhein."
        "good"      -> "Theek neend. Is waqt ko barqarar rakhnay ki koshish karein."
        "okay"      -> "Behtar ho sakta hai. Aaj 30 min pehle sonay ki koshish karein."
        "poor"      -> "Neend kam thi. Sonay se 1 ghanta pehle screen band karein."
        else        -> "Aaj raat ka record neeche darj karein."
    }

    if (showBedtimePicker) {
        AlertDialog(
            onDismissRequest = { showBedtimePicker = false },
            title = { Text(if (isEnglish) "Select Bedtime" else "Sonay ka Waqt Chunein") },
            text  = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = bedtimeState) } },
            confirmButton = {
                TextButton(onClick = {
                    bedtimeHour   = bedtimeState.hour
                    bedtimeMinute = bedtimeState.minute
                    showBedtimePicker = false
                }) { Text("OK", color = SaharaLavender) }
            },
            dismissButton = {
                TextButton(onClick = { showBedtimePicker = false }) { Text(if (isEnglish) "Cancel" else "Wapas") }
            }
        )
    }

    if (showWaketimePicker) {
        AlertDialog(
            onDismissRequest = { showWaketimePicker = false },
            title = { Text(if (isEnglish) "Select Wake Time" else "Uthnay ka Waqt Chunein") },
            text  = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = waketimeState) } },
            confirmButton = {
                TextButton(onClick = {
                    waketimeHour   = waketimeState.hour
                    waketimeMinute = waketimeState.minute
                    showWaketimePicker = false
                }) { Text("OK", color = SaharaSky) }
            },
            dismissButton = {
                TextButton(onClick = { showWaketimePicker = false }) { Text(if (isEnglish) "Cancel" else "Wapas") }
            }
        )
    }

    if (showGoalDialog) {
        var tempGoal by remember { mutableStateOf(sleepGoal) }
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text(if (isEnglish) "Set Sleep Goal" else "Neend ka Hadaf") },
            text = {
                Column {
                    Text("${tempGoal.toInt()}h", style = MaterialTheme.typography.headlineMedium, color = SaharaLavender, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Slider(value = tempGoal, onValueChange = { tempGoal = it }, valueRange = 7f..10f, steps = 2)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.updateGoal(tempGoal); showGoalDialog = false }) {
                    Text("Save", color = SaharaLavender)
                }
            }
        )
    }

    val bgGradient = if (isDark)
        listOf(SaharaLavender.copy(0.22f), MaterialTheme.colorScheme.background.copy(0.6f), MaterialTheme.colorScheme.background)
    else
        listOf(SaharaLavender.copy(0.28f), SaharaSkyLight.copy(0.12f), MaterialTheme.colorScheme.background.copy(0.2f))

    Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState).background(Brush.verticalGradient(bgGradient))) {
        Scaffold(
            bottomBar = { BottomNav(navController = navController, hazeState = hazeState) },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(0.5f), CircleShape)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = SaharaLavender)
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(text = if (isEnglish) "Sleep Tracker" else "Neend ka Record", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = SaharaLavender)
                }

                Spacer(Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(if (isEnglish) "Average" else "Average", if (recorded.isEmpty()) "—" else "%.1f".format(avgHours) + "h", SaharaSky, hazeState, Modifier.weight(1f))
                    StatTile(if (isEnglish) "Best" else "Behtareen", bestDay?.let { "%.1f".format(it.hours) + "h" } ?: "—", SaharaStrongGreen, hazeState, Modifier.weight(1f))
                    StatTile(if (isEnglish) "Worst" else "Kamtareen", worstDay?.let { "%.1f".format(it.hours) + "h" } ?: "—", SaharaCoral, hazeState, Modifier.weight(1f))
                }

                Spacer(Modifier.height(14.dp))

                GlassCard(hazeState, SaharaLavender.copy(0.25f)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column {
                            Text(if (isEnglish) "Sleep Goal" else "Neend ka Hadaf", style = MaterialTheme.typography.bodySmall)
                            Text("${sleepGoal.toInt()}h", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = SaharaLavender)
                        }
                        TextButton(onClick = { showGoalDialog = true }) { Text("Edit", color = SaharaLavender) }
                    }
                    LinearProgressIndicator(progress = { goalProgress }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)), color = SaharaLavender)
                }

                Spacer(Modifier.height(14.dp))

                GlassCard(hazeState, SaharaLavender.copy(0.2f)) {
                    Text(if (isEnglish) "Weekly Overview" else "Haftawar Jaiza", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(30.dp))
                    Row(modifier = Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        sleepList.forEachIndexed { index, data ->
                            val hasRec = data.hours >= 0f
                            val isSel = selectedDay == index
                            val barColor = qualityColor(data.qualityType)
                            val barHeight by animateDpAsState(targetValue = if (hasRec) (data.hours * 12).dp else 4.dp)

                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).clickable { selectedDay = index }) {
                                if (isSel && hasRec) {
                                    Surface(color = barColor, shape = RoundedCornerShape(4.dp)) {
                                        Text("%.1f".format(data.hours), fontSize = 8.sp, color = Color.White, modifier = Modifier.padding(2.dp))
                                    }
                                }
                                Box(modifier = Modifier.width(15.dp).height(barHeight).clip(RoundedCornerShape(4.dp)).background(if (isSel) barColor else barColor.copy(0.3f)))
                                Text(if (isEnglish) data.dayEn else data.dayUr, fontSize = 9.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                val detailColor = qualityColor(selData.qualityType)
                GlassCard(hazeState, detailColor.copy(0.4f)) {
                    Text(if (isEnglish) "${selData.dayEn}'s Sleep" else "${selData.dayUr} ki Neend")
                    if (selData.hours >= 0f) {
                        Text("%.1f hours".format(selData.hours), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(if (isEnglish) insightEn else insightUr, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(24.dp))

                if (!todayLogged) {
                    Text(if (isEnglish) "Log Tonight's Sleep" else "Aaj Raat ka Record", fontWeight = FontWeight.Bold)
                    GlassCard(hazeState, SaharaLavender.copy(0.2f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TimePickerButton(if (isEnglish) "Bedtime" else "Sonay ka Waqt", if (bedtimeHour >= 0) "%02d:%02d".format(bedtimeHour, bedtimeMinute) else null, Icons.Default.Bedtime, SaharaLavender, isDark, { showBedtimePicker = true }, Modifier.weight(1f))
                            TimePickerButton(if (isEnglish) "Wake Time" else "Uthne ka Waqt", if (waketimeHour >= 0) "%02d:%02d".format(waketimeHour, waketimeMinute) else null, Icons.Default.WbSunny, SaharaSky, isDark, { showWaketimePicker = true }, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(14.dp))
                        SaharaButton(
                            text = if (isEnglish) "Save Sleep Log" else "Record Save Karein",
                            onClick = {
                                calculatedHours?.let { h ->
                                    viewModel.logSleep(6, SleepData("Sun", "Sun", h, hoursToLabelEn(h), hoursToLabelUr(h), hoursToType(h), "%02d:%02d".format(bedtimeHour, bedtimeMinute), "%02d:%02d".format(waketimeHour, waketimeMinute)))
                                    selectedDay = 6
                                }
                            },
                            variant = if (calculatedHours != null) ButtonVariant.GRADIENT else ButtonVariant.OUTLINE,
                            isFullWidth = true
                        )
                    }
                } else {
                    GlassCard(hazeState, SaharaStrongGreen.copy(0.3f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = SaharaStrongGreen)
                            Spacer(Modifier.width(12.dp))
                            Text(if (isEnglish) "Tonight's sleep recorded." else "Aaj ka record mehfooz hai.")
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun GlassCard(hazeState: HazeState, borderColor: Color, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).hazeEffect(state = hazeState) {
        blurEffect { blurRadius = 25.dp; colorEffects = listOf(HazeColorEffect.tint(Color.White.copy(0.08f))) }
    }.border(1.dp, borderColor, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Column(content = content)
    }
}

@Composable
private fun StatTile(label: String, value: String, color: Color, hazeState: HazeState, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(14.dp)).hazeEffect(state = hazeState) {
        blurEffect { blurRadius = 25.dp; colorEffects = listOf(HazeColorEffect.tint(Color.White.copy(0.08f))) }
    }.border(1.dp, color.copy(0.25f), RoundedCornerShape(14.dp)).padding(12.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, fontSize = 10.sp)
        }
    }
}

@Composable
private fun TimePickerButton(label: String, time: String?, icon: ImageVector, color: Color, isDark: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(if (time != null) color.copy(0.1f) else Color.Gray.copy(0.1f)).clickable(onClick = onClick).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(label, fontSize = 10.sp)
                Text(time ?: "Set", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MiniTimeBadge(icon: ImageVector, time: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(0.12f)) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(10.dp))
            Spacer(Modifier.width(3.dp))
            Text(time, fontSize = 10.sp, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}