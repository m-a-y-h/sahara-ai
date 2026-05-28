package pk.edu.ucp.saharaai.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.remote.FirestoreService
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import pk.edu.ucp.saharaai.data.repository.AutomaticSleepLogRepository
import pk.edu.ucp.saharaai.data.repository.HealthConnectAvailability
import pk.edu.ucp.saharaai.data.repository.SleepActigraphyRepository
import pk.edu.ucp.saharaai.data.repository.SleepHealthConnectRepository
import pk.edu.ucp.saharaai.service.SleepActigraphyService
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

const val SLEEP_SOURCE_SELF_REPORTED = "self_reported"
const val SLEEP_SOURCE_HEALTH_CONNECT = "health_connect"

data class SleepLog(
    val date: String,
    val bedtime: String,
    val waketime: String,
    val hours: Float,
    val qualityType: String,
    val source: String = SLEEP_SOURCE_SELF_REPORTED,
    val timeZoneId: String = TimeZone.getDefault().id,
    val automatic: Boolean = false,
    val confidence: Float? = null,
    val sourceReason: String = ""
)

enum class HealthSleepImportState {
    CHECKING,
    READY,
    PERMISSION_REQUIRED,
    UPDATE_REQUIRED,
    UNAVAILABLE
}

class SleepTrackerViewModel : ViewModel() {
    private val _logs = MutableStateFlow<Map<String, SleepLog>>(emptyMap())
    val logs: StateFlow<Map<String, SleepLog>> = _logs.asStateFlow()

    private val _sleepGoal = MutableStateFlow(8f)
    val sleepGoal: StateFlow<Float> = _sleepGoal.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _healthImportState = MutableStateFlow(HealthSleepImportState.CHECKING)
    val healthImportState: StateFlow<HealthSleepImportState> = _healthImportState.asStateFlow()

    private val _automaticTrackingEnabled = MutableStateFlow(false)
    val automaticTrackingEnabled: StateFlow<Boolean> = _automaticTrackingEnabled.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val weekDates: List<String>
    val todayDate: String
    val todayIndex: Int
    val dayLabelsEn: List<String> = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val dayLabelsUr: List<String> = listOf("Pir", "Mgal", "Budh", "Jmrt", "Juma", "Hfta", "Itwl")

    private var uid = ""
    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val mirroredToRiskStore = mutableSetOf<String>()

    init {
        val cal = Calendar.getInstance()
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val daysFromMon = (dow - Calendar.MONDAY + 7) % 7
        cal.add(Calendar.DAY_OF_MONTH, -daysFromMon)

        val dates = (0..6).map {
            val date = fmt.format(cal.time)
            cal.add(Calendar.DAY_OF_MONTH, 1)
            date
        }
        weekDates = dates
        todayDate = fmt.format(Date())
        todayIndex = dates.indexOf(todayDate).let { if (it < 0) 6 else it }
    }

    fun initCurrentUser() = initUser(Firebase.auth.currentUser?.uid.orEmpty())

    fun initUser(userId: String) {
        if (userId.isBlank() || uid == userId) return
        uid = userId
        viewModelScope.launch {
            _sleepGoal.value = RealtimeDBService.loadSleepGoal(uid)
            RealtimeDBService
                .listenToWeekSleepLogs(uid, weekDates.first(), weekDates.last())
                .collect { raw ->
                    val mapped = raw.mapValues { (date, fields) ->
                        SleepLog(
                            date = date,
                            bedtime = fields["bedtime"] as? String ?: "",
                            waketime = fields["waketime"] as? String ?: "",
                            hours = (fields["hours"] as? Float) ?: 0f,
                            qualityType = fields["qualityType"] as? String ?: "none",
                            source = fields["source"] as? String ?: SLEEP_SOURCE_SELF_REPORTED,
                            timeZoneId = fields["timeZoneId"] as? String ?: TimeZone.getDefault().id,
                            automatic = fields["automatic"] as? Boolean ?: false,
                            confidence = fields["confidence"] as? Float,
                            sourceReason = fields["sourceReason"] as? String ?: ""
                        )
                    }
                    _logs.value = mapped
                    mapped.values
                        .filterNot { it.date in mirroredToRiskStore }
                        .forEach { mirrorToRiskStore(it) }
                }
        }
    }

    fun checkHealthConnect(context: Context) {
        val appContext = context.applicationContext
        when (SleepHealthConnectRepository.availability(appContext)) {
            HealthConnectAvailability.UNAVAILABLE -> {
                _healthImportState.value = HealthSleepImportState.UNAVAILABLE
            }
            HealthConnectAvailability.UPDATE_REQUIRED -> {
                _healthImportState.value = HealthSleepImportState.UPDATE_REQUIRED
            }
            HealthConnectAvailability.AVAILABLE -> viewModelScope.launch {
                _healthImportState.value =
                    if (SleepHealthConnectRepository.hasReadPermission(appContext)) {
                        HealthSleepImportState.READY
                    } else {
                        HealthSleepImportState.PERMISSION_REQUIRED
                    }
            }
        }
    }

    fun checkAutomaticTracking(context: Context) {
        val appContext = context.applicationContext
        val configured = SleepActigraphyRepository.isEnabled(appContext)
        val hasPermissions = SleepActigraphyRepository.hasRequiredPermissions(appContext)
        _automaticTrackingEnabled.value = configured && hasPermissions
        if (configured && !hasPermissions) {
            SleepActigraphyRepository.setEnabled(appContext, false)
            SleepActigraphyService.stop(appContext)
            viewModelScope.launch {
                AutomaticSleepLogRepository.fillMissingCompletedNights(
                    context = appContext,
                    useActigraphy = false,
                    unavailableReason = "Automatic motion permission or visible notification was removed"
                )
            }
        }
    }

    fun enableAutomaticTracking(
        context: Context,
        requestPermissions: (Array<String>) -> Unit
    ) {
        val appContext = context.applicationContext
        val missing = SleepActigraphyRepository.requiredPermissions()
            .filterNot { permission ->
                androidx.core.content.ContextCompat.checkSelfPermission(appContext, permission) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            .toTypedArray()
        if (missing.isNotEmpty()) {
            requestPermissions(missing)
        } else {
            tryStartAutomaticTracking(appContext)
        }
    }

    fun onAutomaticPermissionsResult(context: Context, granted: Map<String, Boolean>) {
        val appContext = context.applicationContext
        if (SleepActigraphyRepository.requiredPermissions().all { granted[it] == true ||
                androidx.core.content.ContextCompat.checkSelfPermission(appContext, it) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        ) {
            tryStartAutomaticTracking(appContext)
        } else {
            _error.value =
                "Automatic sleep estimates need activity recognition and a visible notification."
        }
    }

    fun disableAutomaticTracking(context: Context) {
        SleepActigraphyRepository.setEnabled(context.applicationContext, false)
        SleepActigraphyService.stop(context.applicationContext)
        _automaticTrackingEnabled.value = false
        _message.value = "Automatic sleep estimates stopped."
    }

    fun importFromHealthConnect(
        context: Context,
        requestPermission: (Set<String>) -> Unit
    ) {
        val appContext = context.applicationContext
        when (SleepHealthConnectRepository.availability(appContext)) {
            HealthConnectAvailability.UNAVAILABLE -> {
                _healthImportState.value = HealthSleepImportState.UNAVAILABLE
                _error.value = "Health Connect is not available on this device."
            }
            HealthConnectAvailability.UPDATE_REQUIRED -> {
                _healthImportState.value = HealthSleepImportState.UPDATE_REQUIRED
                _error.value = "Install or update Health Connect to import sleep records."
            }
            HealthConnectAvailability.AVAILABLE -> viewModelScope.launch {
                if (!SleepHealthConnectRepository.hasReadPermission(appContext)) {
                    _healthImportState.value = HealthSleepImportState.PERMISSION_REQUIRED
                    requestPermission(SleepHealthConnectRepository.requiredPermissions)
                } else {
                    _healthImportState.value = HealthSleepImportState.READY
                    importAuthorizedSleep(appContext)
                }
            }
        }
    }

    fun onHealthPermissionsResult(context: Context, granted: Set<String>) {
        if (SleepHealthConnectRepository.readSleepPermission !in granted) {
            _healthImportState.value = HealthSleepImportState.PERMISSION_REQUIRED
            _error.value = "Sleep permission was not granted. You can still log sleep manually."
            return
        }
        _healthImportState.value = HealthSleepImportState.READY
        viewModelScope.launch { importAuthorizedSleep(context.applicationContext) }
    }

    fun logSleep(date: String, bedtime: String, waketime: String, hours: Float) {
        if (uid.isBlank() || date !in weekDates || date > todayDate || hours !in 0.1f..24f) return
        val log = SleepLog(
            date = date,
            bedtime = bedtime,
            waketime = waketime,
            hours = hours,
            qualityType = hoursToQuality(hours),
            source = SLEEP_SOURCE_SELF_REPORTED,
            sourceReason = "Sleep times entered by the user."
        )
        viewModelScope.launch {
            _isSaving.value = true
            persistLog(log).onFailure { _error.value = it.message ?: "Failed to save sleep." }
            _isSaving.value = false
        }
    }

    fun updateGoal(goal: Float) {
        _sleepGoal.value = goal
        if (uid.isBlank()) return
        viewModelScope.launch {
            RealtimeDBService.saveSleepGoal(uid, goal)
                .onFailure { _error.value = it.message }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearMessage() {
        _message.value = null
    }

    fun dateLabel(weekIndex: Int): String = runCatching {
        val date = fmt.parse(weekDates[weekIndex]) ?: return@runCatching dayLabelsEn[weekIndex]
        SimpleDateFormat("EEE, d MMM", Locale.US).format(date)
    }.getOrDefault(dayLabelsEn[weekIndex])

    private suspend fun importAuthorizedSleep(context: Context) {
        if (uid.isBlank()) return
        _isImporting.value = true
        val daysToImport = weekDates
            .map(LocalDate::parse)
            .filterNot { it.isAfter(LocalDate.now()) }
        SleepHealthConnectRepository.readWeek(context, daysToImport).fold(
            onSuccess = { importedDays ->
                var saved = 0
                var manualPreserved = 0
                importedDays.forEach { imported ->
                    if (_logs.value[imported.date]?.source == SLEEP_SOURCE_SELF_REPORTED) {
                        manualPreserved += 1
                    } else {
                        val log = SleepLog(
                            date = imported.date,
                            bedtime = "",
                            waketime = "",
                            hours = imported.hours.coerceAtMost(24f),
                            qualityType = hoursToQuality(imported.hours),
                            source = SLEEP_SOURCE_HEALTH_CONNECT,
                            timeZoneId = imported.timeZoneId,
                            sourceReason = "Sleep duration imported from Health Connect."
                        )
                        persistLog(log).onSuccess { saved += 1 }
                            .onFailure { _error.value = it.message ?: "Failed to save imported sleep." }
                    }
                }
                _message.value = when {
                    saved == 0 && manualPreserved == 0 ->
                        "No sleep records were found in Health Connect for this week."
                    manualPreserved > 0 ->
                        "Imported $saved night(s). Kept $manualPreserved manually logged night(s)."
                    else -> "Imported $saved sleep night(s) from Health Connect."
                }
            },
            onFailure = { _error.value = it.message ?: "Unable to import Health Connect sleep." }
        )
        _isImporting.value = false
    }

    private suspend fun persistLog(log: SleepLog): Result<Unit> = runCatching {
        RealtimeDBService.saveSleepLog(
            uid = uid,
            date = log.date,
            bedtime = log.bedtime,
            waketime = log.waketime,
            hours = log.hours,
            source = log.source,
            timeZoneId = log.timeZoneId,
            automatic = log.automatic,
            confidence = log.confidence,
            sourceReason = log.sourceReason
        ).getOrThrow()
        FirestoreService.saveSleepLog(
            uid = uid,
            date = log.date,
            bedtime = log.bedtime,
            waketime = log.waketime,
            hours = log.hours,
            source = log.source,
            timeZoneId = log.timeZoneId,
            automatic = log.automatic,
            confidence = log.confidence,
            sourceReason = log.sourceReason
        ).getOrThrow()
        mirroredToRiskStore += log.date
    }

    private suspend fun mirrorToRiskStore(log: SleepLog) {
        FirestoreService.saveSleepLog(
            uid = uid,
            date = log.date,
            bedtime = log.bedtime,
            waketime = log.waketime,
            hours = log.hours,
            source = log.source,
            timeZoneId = log.timeZoneId,
            automatic = log.automatic,
            confidence = log.confidence,
            sourceReason = log.sourceReason
        ).onSuccess {
            mirroredToRiskStore += log.date
        }.onFailure {
            _error.value = it.message ?: "Could not sync sleep record for weekly analysis."
        }
    }

    private fun hoursToQuality(hours: Float): String =
        when {
            hours >= 8f -> "excellent"
            hours >= 7f -> "good"
            hours >= 6f -> "okay"
            else -> "poor"
        }

    private fun startAutomaticTracking(context: Context) {
        SleepActigraphyRepository.setEnabled(context, true)
        SleepActigraphyService.start(context)
        _automaticTrackingEnabled.value = true
        _message.value = "Automatic sleep estimates started. A persistent notification is shown."
        viewModelScope.launch {
            AutomaticSleepLogRepository.fillMissingCompletedNights(
                context = context,
                useActigraphy = true,
                unavailableReason = "Phone-motion data was insufficient for an actigraphy estimate"
            )
        }
    }

    private fun tryStartAutomaticTracking(context: Context) {
        if (SleepActigraphyRepository.hasRequiredPermissions(context)) {
            startAutomaticTracking(context)
        } else {
            _error.value =
                "Enable Sahara notifications so automatic sleep estimates remain visibly disclosed."
        }
    }
}
