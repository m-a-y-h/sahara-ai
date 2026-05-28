package pk.edu.ucp.saharaai.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import java.text.SimpleDateFormat
import java.util.*

data class MoodEntry(
    val id: String,
    val mood: String,
    val emoji: String,
    val labelEn: String,
    val date: String,           
    val timestamp: Long
)

class MoodPatternsViewModel : ViewModel() {

    private val _entries    = MutableStateFlow<List<MoodEntry>>(emptyList())
    val entries: StateFlow<List<MoodEntry>> = _entries

    private val _isSaving   = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _error      = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _todayMood  = MutableStateFlow<String?>(null)
    val todayMood: StateFlow<String?> = _todayMood

    private var userId: String = ""
    private var listenJob: Job? = null

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val todayDate: String get() = dateFmt.format(Date())

    

    fun initCurrentUser() = initUser(Firebase.auth.currentUser?.uid.orEmpty())

    fun initUser(uid: String) {
        if (uid.isBlank() || uid == userId) return
        userId = uid
        startListening()
    }

    fun logMood(mood: String, emoji: String, labelEn: String) {
        if (userId.isBlank()) { _error.value = "Not logged in"; return }
        viewModelScope.launch {
            _isSaving.value = true
            RealtimeDBService.saveMoodLog(userId, mood, emoji, labelEn, todayDate)
                .onFailure { _error.value = it.message ?: "Failed to save mood" }
            _isSaving.value = false
        }
    }

    fun clearError() { _error.value = null }

    

    
    val weekDates: List<String> get() {
        val cal = Calendar.getInstance()
        val dow = cal.get(Calendar.DAY_OF_WEEK)           
        val offset = if (dow == Calendar.SUNDAY) -6 else -(dow - Calendar.MONDAY)
        return (0..6).map { i ->
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, offset + i)
            dateFmt.format(c.time)
        }
    }

    
    val weekMoods: List<MoodEntry?>
        get() {
            val byDate = _entries.value.groupBy { it.date }
            return weekDates.map { date -> byDate[date]?.firstOrNull() }
        }

    

    private fun startListening() {
        listenJob?.cancel()
        listenJob = viewModelScope.launch {
            RealtimeDBService.listenToMoodLogs(userId, 50).collect { raw ->
                val parsed = raw.mapNotNull { map ->
                    MoodEntry(
                        id        = map["id"]        as? String ?: return@mapNotNull null,
                        mood      = map["mood"]      as? String ?: return@mapNotNull null,
                        emoji     = map["emoji"]     as? String ?: return@mapNotNull null,
                        labelEn   = map["labelEn"]   as? String ?: return@mapNotNull null,
                        date      = map["date"]      as? String ?: return@mapNotNull null,
                        timestamp = map["timestamp"] as? Long   ?: 0L
                    )
                }
                _entries.value = parsed
                _todayMood.value = parsed.firstOrNull { it.date == todayDate }?.mood
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenJob?.cancel()
    }
}
