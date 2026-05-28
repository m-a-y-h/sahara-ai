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
import pk.edu.ucp.saharaai.ui.screens.JournalEntry
import java.util.Calendar
import java.util.TimeZone

class JournalViewModel : ViewModel() {
    companion object {
        const val MAX_DAILY_ENTRIES = 3
    }

    var entries by mutableStateOf<List<JournalEntry>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var isSaving by mutableStateOf(false)
        private set

    private val uid: String
        get() = Firebase.auth.currentUser?.uid.orEmpty()

    val todayEntryCount: Int
        get() = entries.count { isToday(it.timestamp) }

    fun loadEntries() {
        val userId = uid
        if (userId.isBlank()) {
            entries = emptyList()
            isLoading = false
            return
        }
        viewModelScope.launch {
            isLoading = true
            entries = RealtimeDBService.getJournalEntries(userId).getOrDefault(emptyList()).map { map ->
                JournalEntry(
                    id = map["id"]?.toString().orEmpty(),
                    mood = map["mood"]?.toString() ?: "Okay",
                    prompt = map["prompt"]?.toString().orEmpty(),
                    notes = map["notes"]?.toString().orEmpty(),
                    timestamp = (map["timestamp"] as? Long) ?: 0L
                )
            }
            isLoading = false
        }
    }

    fun saveEntry(
        mood: String,
        prompt: String,
        notes: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val userId = uid
        if (userId.isBlank() || isSaving) {
            onResult(false, "Please sign in and try again.")
            return
        }
        viewModelScope.launch {
            isSaving = true
            val latestEntries = RealtimeDBService.getJournalEntries(userId).getOrElse {
                isSaving = false
                onResult(false, "Could not verify today's journal limit. Try again.")
                return@launch
            }
            val entriesToday = latestEntries.count { map ->
                isToday((map["timestamp"] as? Long) ?: 0L)
            }
            if (entriesToday >= MAX_DAILY_ENTRIES) {
                isSaving = false
                onResult(false, "You can submit up to 3 journal entries per day.")
                return@launch
            }
            val saved = RealtimeDBService.saveJournalEntry(
                uid = userId,
                mood = mood,
                prompt = prompt.trim(),
                notes = notes.trim()
            ).isSuccess
            if (saved) loadEntries()
            isSaving = false
            onResult(saved, if (saved) null else "Failed to save. Try again.")
        }
    }

    fun deleteEntry(entryId: String, onResult: (Boolean) -> Unit) {
        val userId = uid
        if (userId.isBlank()) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val deleted = RealtimeDBService.deleteJournalEntry(userId, entryId).isSuccess
            if (deleted) loadEntries()
            onResult(deleted)
        }
    }

    private fun isToday(timestamp: Long): Boolean {
        if (timestamp <= 0L) return false
        val zone = TimeZone.getDefault()
        val date = Calendar.getInstance(zone).apply { timeInMillis = timestamp }
        val today = Calendar.getInstance(zone)
        return date.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            date.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    }
}
