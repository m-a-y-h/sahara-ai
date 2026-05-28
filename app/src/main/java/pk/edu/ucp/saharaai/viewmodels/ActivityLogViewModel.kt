package pk.edu.ucp.saharaai.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import pk.edu.ucp.saharaai.KEY_ASSESSMENT_SCORE
import pk.edu.ucp.saharaai.KEY_ASSESSMENT_TIMESTAMP
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import java.util.Calendar

data class WeeklyReport(
    val dast10Score: Int = -1,
    val assessmentTs: Long = 0L,
    val totalSessions: Int = 0,
    val totalScreenMs: Long = 0L,
    val topApps: List<Pair<String, Long>> = emptyList(),
    val activeDays: Set<Int> = emptySet(),
    val moodLogCount: Int = 0,
    val moodDistribution: Map<String, Int> = emptyMap(),
    val journalCount: Int = 0,
    val chatMsgCount: Int = 0,
    val memberSinceDays: Int = 0,
    val emailVerified: Boolean = false,
    val flags: List<String> = emptyList()
)

class ActivityLogViewModel : ViewModel() {
    var isLoading by mutableStateOf(true)
        private set
    var report by mutableStateOf(WeeklyReport())
        private set

    fun loadReport(context: Context, weekStart: Long, weekEnd: Long, isEnglish: Boolean) {
        val uid = Firebase.auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) {
            isLoading = false
            return
        }
        viewModelScope.launch {
            isLoading = true
            val prefs = context.applicationContext.getSharedPreferences("sahara_prefs", Context.MODE_PRIVATE)
            val dastScore = prefs.getInt(KEY_ASSESSMENT_SCORE, -1)
            val dastTs = prefs.getLong(KEY_ASSESSMENT_TIMESTAMP, 0L)
            val db = FirebaseDatabase.getInstance()
            val usageSnapshot = runCatching {
                db.getReference("app_usage_events").child(uid)
                    .orderByChild("startTimeMillis").startAt(weekStart.toDouble()).get().await()
            }.getOrNull()
            val sessions = usageSnapshot?.children
                ?.mapNotNull { it.value as? Map<String, Any> }.orEmpty()
            val totalScreenMs = sessions.sumOf { (it["durationMillis"] as? Long) ?: 0L }
            val appTime = mutableMapOf<String, Long>()
            val activeDays = mutableSetOf<Int>()
            sessions.forEach { session ->
                val appName = session["appName"]?.toString() ?: session["packageName"]?.toString()
                if (appName != null) {
                    appTime[appName] = (appTime[appName] ?: 0L) + ((session["durationMillis"] as? Long) ?: 0L)
                }
                (session["startTimeMillis"] as? Long)?.takeIf { it > 0L }?.let { timestamp ->
                    activeDays += Calendar.getInstance().apply { timeInMillis = timestamp }
                        .get(Calendar.DAY_OF_WEEK)
                }
            }
            val moodDistribution = mutableMapOf<String, Int>()
            var moodLogCount = 0
            runCatching {
                db.getReference("mood_logs").child(uid).get().await().children.forEach { day ->
                    val timestamp = day.child("timestamp").getValue(Long::class.java) ?: 0L
                    if (timestamp in weekStart..weekEnd) {
                        moodLogCount++
                        val label = day.child("labelEn").getValue(String::class.java)
                            ?: day.child("mood").getValue(String::class.java)
                        if (label != null) moodDistribution[label] = (moodDistribution[label] ?: 0) + 1
                    }
                }
            }
            val journalCount = RealtimeDBService.getJournalEntries(uid).getOrDefault(emptyList()).count {
                ((it["timestamp"] as? Long) ?: 0L) in weekStart..weekEnd
            }
            var chatMsgCount = 0
            runCatching {
                db.getReference("user_chats").get().await().children
                    .filter { it.key.orEmpty().startsWith(uid) }
                    .forEach { session ->
                        session.child("messages").children.forEach { message ->
                            val timestamp = message.child("timestamp").getValue(Long::class.java) ?: 0L
                            if (timestamp in weekStart..weekEnd) chatMsgCount++
                        }
                    }
            }
            val createdAt = RealtimeDBService.getUserCreatedAt(uid).takeIf { it > 0L }
                ?: (Firebase.auth.currentUser?.metadata?.creationTimestamp ?: 0L)
            val memberDays = if (createdAt > 0L) {
                ((System.currentTimeMillis() - createdAt) / 86_400_000L).toInt()
            } else 0
            val emailVerified = runCatching {
                db.getReference("users").child(uid).child("emailVerified").get().await()
                    .getValue(Boolean::class.java) ?: false
            }.getOrDefault(false)
            val flags = buildList {
                if (dastScore > 10) add(if (isEnglish) "High DAST-10 risk score ($dastScore)" else "DAST-10 score zyada hai ($dastScore)")
                if (dastScore in 6..10) add(if (isEnglish) "Moderate risk score ($dastScore)" else "Darmiyani risk score ($dastScore)")
                if (moodLogCount == 0) add(if (isEnglish) "No mood logs this week" else "Is hafte koi mood log nahi")
                if (chatMsgCount == 0) add(if (isEnglish) "No counselor contact this week" else "Is hafte counselor se rabta nahi")
                val hours = totalScreenMs / 3_600_000f
                if (hours > 20f) add(if (isEnglish) "Excessive screen time tracked (${hours.toInt()}h)" else "Zyada screen time (${hours.toInt()} ghante)")
                if (!emailVerified) add(if (isEnglish) "Email not verified" else "Email verify nahi ki gayi hai")
            }
            report = WeeklyReport(
                dast10Score = dastScore,
                assessmentTs = dastTs,
                totalSessions = sessions.size,
                totalScreenMs = totalScreenMs,
                topApps = appTime.entries.sortedByDescending { it.value }.take(5).map { it.key to it.value },
                activeDays = activeDays,
                moodLogCount = moodLogCount,
                moodDistribution = moodDistribution,
                journalCount = journalCount,
                chatMsgCount = chatMsgCount,
                memberSinceDays = memberDays,
                emailVerified = emailVerified,
                flags = flags
            )
            isLoading = false
        }
    }
}
