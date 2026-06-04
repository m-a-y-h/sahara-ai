package pk.edu.ucp.saharaai.viewmodels

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.ASSESSMENT_VALIDITY_MS
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import pk.edu.ucp.saharaai.data.repository.ChatRepository
import pk.edu.ucp.saharaai.util.callingName
import pk.edu.ucp.saharaai.ui.screens.GlobalAppState
import pk.edu.ucp.saharaai.ui.theme.*
import java.util.Calendar
import java.util.TimeZone

data class DailyTip(
    val textEn: String,
    val textUr: String,
    val isExternalLink: Boolean,
    val actionDestination: String
)

data class RiskStatus(
    val percentage: Float,
    val title: String,
    val action: String,
    val color: Color
)

class DashboardViewModel : ViewModel() {
    var userId by mutableStateOf("")
        private set
    var resolvedName by mutableStateOf("User")
        private set
    var isAiCheckInLoaded by mutableStateOf(false)
        private set

    fun loadDashboard(defaultName: String) {
        resolvedName = callingName(defaultName).ifBlank { defaultName }
        userId = Firebase.auth.currentUser?.uid.orEmpty()
        isAiCheckInLoaded = false
        if (userId.isBlank()) {
            GlobalAppState.hasCheckedIn = false
            isAiCheckInLoaded = true
            return
        }
        val activeUserId = userId
        viewModelScope.launch {
            GlobalAppState.hasCheckedIn = ChatRepository.hasUserAiMessageToday(activeUserId)
            isAiCheckInLoaded = true
            val dbName = RealtimeDBService.getUserDisplayName(activeUserId)
            if (dbName.isNotBlank()) {
                resolvedName = callingName(dbName).ifBlank { dbName }
            }
            val latest = RealtimeDBService.loadLatestAssessment(activeUserId) ?: return@launch
            val savedScore = (latest["score"] as? Int) ?: (latest["score"] as? Long)?.toInt()
            val savedTs = (latest["timestamp"] as? Long) ?: 0L
            if (savedScore != null) {
                GlobalAppState.dast10Score = savedScore
                GlobalAppState.lastAssessmentTimestamp = savedTs
                GlobalAppState.hasEverCompletedAssessment = true
                val ageMs = if (savedTs > 0) System.currentTimeMillis() - savedTs else 0L
                if (savedTs > 0L && ageMs <= ASSESSMENT_VALIDITY_MS) {
                    GlobalAppState.hasCompletedInitialAssessment = true
                } else {
                    GlobalAppState.hasCompletedInitialAssessment = false
                }
            }
        }
    }

    val tipsList = listOf(
        DailyTip("Drinking a glass of water first thing in the morning boosts brain function.", "Subah uthte hi ek glass pani peena dimagh ke liye best hai.", true, "https://www.ncbi.nlm.nih.gov/pmc/articles/PMC2908954/"),
        DailyTip("Take a 5-minute stretch break. Exercise aids in addiction recovery.", "5 min ki break lein. Exercise recovery mein madad karti hai.", true, "https://www.ncbi.nlm.nih.gov/pmc/articles/PMC3224086/"),
        DailyTip("Write down three things you are grateful for today.", "Aaj ki 3 achi baatein apne journal mein note karein.", false, "journal")
    )

    var currentTipIndex by mutableIntStateOf(0)

    fun getGreeting(isEnglish: Boolean): String {
        val pktTimeZone = TimeZone.getTimeZone("Asia/Karachi")
        val calendar = Calendar.getInstance(pktTimeZone)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        return when (hour) {
            in 5..11 -> if (isEnglish) "Good morning," else "Subah Bakhair,"
            in 12..16 -> if (isEnglish) "Good afternoon," else "Dopeher Bakhair,"
            in 17..20 -> if (isEnglish) "Good evening," else "Shaam Bakhair,"
            else -> if (isEnglish) "Good night," else "Shab Bakhair,"
        }
    }

    fun getRiskStatus(): RiskStatus {
        return if (!GlobalAppState.hasCompletedInitialAssessment) {
            RiskStatus(0f, "Pending Assessment", "Complete evaluation", Color.Gray)
        } else {
            when (GlobalAppState.dast10Score) {
                0 -> RiskStatus(5f, "No Problems", "None at this time", SaharaStrongGreen)
                in 1..2 -> RiskStatus(25f, "Low Level", "Monitor, re-assess later", SaharaSky)
                in 3..5 -> RiskStatus(50f, "Moderate Level", "Further Investigation", SaharaWarning)
                in 6..8 -> RiskStatus(75f, "Substantial Level", "Intensive Assessment", SaharaCoral)
                else -> RiskStatus(100f, "Severe Level", "Intensive Assessment", Color.Red)
            }
        }
    }

    fun nextTip() {
        currentTipIndex = (currentTipIndex + 1) % tipsList.size
    }
}
