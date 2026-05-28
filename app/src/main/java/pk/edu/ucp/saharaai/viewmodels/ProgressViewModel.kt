package pk.edu.ucp.saharaai.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import pk.edu.ucp.saharaai.ui.screens.GlobalAppState
import pk.edu.ucp.saharaai.ui.screens.ProgressState
import pk.edu.ucp.saharaai.ui.screens.RiskHistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProgressViewModel : ViewModel() {
    var progress by mutableStateOf(ProgressState())
        private set

    fun loadProgress(hasAssessment: Boolean) {
        val uid = Firebase.auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) {
            progress = ProgressState(isLoading = false)
            return
        }
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        viewModelScope.launch {
            progress = ProgressState(isLoading = true)
            val checkInData = if (hasAssessment) {
                RealtimeDBService.updateDailyCheckIn(uid, today).getOrNull() ?: emptyMap()
            } else {
                emptyMap()
            }
            val progressData = RealtimeDBService.loadProgressData(uid) ?: checkInData
            val riskHistory = RealtimeDBService.loadAssessmentHistory(uid, limit = 6).map { entry ->
                RiskHistoryEntry(
                    date = entry["date"]?.toString().orEmpty(),
                    score = (entry["score"] as? Int) ?: (entry["score"] as? Long)?.toInt() ?: 0,
                    riskLevel = entry["riskLevel"]?.toString().orEmpty(),
                    quizType = entry["quizType"]?.toString().orEmpty()
                )
            }
            val streak = (progressData["streak"] as? Long)?.toInt() ?: 0
            val unlocked = (progressData["unlockedAchievements"] as? Map<*, *>)
                ?.keys?.mapNotNull { it?.toString() }?.toSet().orEmpty()
            GlobalAppState.currentStreak = streak
            progress = ProgressState(
                streak = streak,
                longestStreak = (progressData["longestStreak"] as? Long)?.toInt() ?: 0,
                totalDays = (progressData["totalDays"] as? Long)?.toInt() ?: 0,
                lastCheckIn = progressData["lastCheckIn"]?.toString().orEmpty(),
                unlockedAchievementIds = unlocked,
                riskHistory = riskHistory,
                isLoading = false
            )
            if (hasAssessment) {
                streakAchievementIds(streak).filterNot(unlocked::contains).forEach { achievementId ->
                    RealtimeDBService.unlockAchievement(uid, achievementId)
                }
                if ("initial_assessment" !in unlocked) {
                    RealtimeDBService.unlockAchievement(uid, "initial_assessment")
                }
            }
        }
    }

    private fun streakAchievementIds(streak: Int): Set<String> = buildSet {
        if (streak >= 1) add("first_step")
        if (streak >= 3) add("consistency")
        if (streak >= 7) add("week_strong")
        if (streak >= 14) add("clean_fortnight")
        if (streak >= 30) add("month_milestone")
        if (streak >= 45) add("habit_breaker")
        if (streak >= 60) add("quarter_century")
        if (streak >= 90) add("resilience")
        if (streak >= 180) add("half_year")
        if (streak >= 200) add("iron_will")
        if (streak >= 250) add("unstoppable")
        if (streak >= 300) add("decade_days")
        if (streak >= 365) add("sovereign")
    }
}
