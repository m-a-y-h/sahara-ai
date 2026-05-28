package pk.edu.ucp.saharaai.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenTimeViewModel : ViewModel() {
    var liveEvents by mutableStateOf<List<Map<String, Any>>>(emptyList())
        private set

    private var eventJob: Job? = null

    fun listenToTodayEvents() {
        val uid = Firebase.auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) return
        eventJob?.cancel()
        eventJob = viewModelScope.launch {
            RealtimeDBService.getTodayAppUsageEvents(uid).collect { liveEvents = it }
        }
    }

    fun saveTodaySnapshot(apps: List<Triple<String, String, Long>>) {
        val uid = Firebase.auth.currentUser?.uid.orEmpty()
        if (uid.isBlank() || apps.isEmpty()) return
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        viewModelScope.launch {
            RealtimeDBService.saveScreenTime(uid, date, apps)
        }
    }
}
