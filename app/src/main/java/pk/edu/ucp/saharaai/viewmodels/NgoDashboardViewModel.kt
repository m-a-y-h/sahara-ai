package pk.edu.ucp.saharaai.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.model.CounselorProfile
import pk.edu.ucp.saharaai.data.model.RegionalRiskSummary
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService


class NgoDashboardViewModel : ViewModel() {

    private val _counselors  = MutableStateFlow<List<CounselorProfile>>(emptyList())
    val counselors: StateFlow<List<CounselorProfile>> = _counselors.asStateFlow()

    private val _totalUsers  = MutableStateFlow(0)
    val totalUsers: StateFlow<Int> = _totalUsers.asStateFlow()

    private val _totalChats  = MutableStateFlow(0)
    val totalChats: StateFlow<Int> = _totalChats.asStateFlow()

    private val _isLoading   = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _ngoRegion = MutableStateFlow("")
    val ngoRegion: StateFlow<String> = _ngoRegion.asStateFlow()

    private val _ngoName = MutableStateFlow("")
    val ngoName: StateFlow<String> = _ngoName.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var lastNgoKey: String = ""

    private val _regionalRisk = MutableStateFlow<List<RegionalRiskSummary>>(emptyList())
    val regionalRisk: StateFlow<List<RegionalRiskSummary>> = _regionalRisk.asStateFlow()

    private var rawCounselors = emptyList<CounselorProfile>()
    private var rawRegionalRisk = emptyList<RegionalRiskSummary>()
    private var started = false

    
    fun loadDashboard(ngoKey: String = "") {
        if (started) return
        started = true
        lastNgoKey = ngoKey
        if (ngoKey.isNotBlank()) {
            viewModelScope.launch {
                val ngoData = RealtimeDBService.getNgoKey(ngoKey).getOrNull()
                _ngoRegion.value = ngoData?.get("region")?.toString().orEmpty()
                _ngoName.value =
                    (ngoData?.get("name") as? String)?.takeIf { it.isNotBlank() }
                        ?: (ngoData?.get("displayName") as? String)?.takeIf { it.isNotBlank() }
                        ?: (ngoData?.get("orgName") as? String)?.takeIf { it.isNotBlank() }
                        ?: ""
                applyRegionFilter()
            }
        }
        
        viewModelScope.launch {
            RealtimeDBService.listenToAllCounselorKeys().collect { rawList ->
                rawCounselors = rawList.map { data -> data.toCounselorProfile() }
                applyRegionFilter()
                _isLoading.value = false
            }
        }
        
        viewModelScope.launch {
            RealtimeDBService.listenToUserCount().collect { count ->
                _totalUsers.value = count
            }
        }
        
        viewModelScope.launch {
            RealtimeDBService.listenToChatSessionCount().collect { count ->
                _totalChats.value = count
            }
        }
        viewModelScope.launch {
            RealtimeDBService.listenToRegionalRiskSummaries().collect { summaries ->
                rawRegionalRisk = summaries
                applyRegionFilter()
            }
        }
    }

    private fun applyRegionFilter() {
        val region = _ngoRegion.value.trim()
        _counselors.value = if (region.isBlank()) rawCounselors else {
            rawCounselors.filter { it.region.equals(region, ignoreCase = true) }
        }
        _regionalRisk.value = if (region.isBlank()) rawRegionalRisk else {
            rawRegionalRisk.filter { it.region.equals(region, ignoreCase = true) }
        }
    }

    

    
    /** Pull-to-refresh hook. The live RTDB listeners already keep the dashboard
     *  current; this method just re-fetches the NGO metadata (name, region in
     *  case it was edited) and flips a brief "refreshing" state so the UI
     *  shows feedback. Safe to call repeatedly. */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            if (lastNgoKey.isNotBlank()) {
                val ngoData = RealtimeDBService.getNgoKey(lastNgoKey).getOrNull()
                _ngoRegion.value = ngoData?.get("region")?.toString().orEmpty()
                _ngoName.value =
                    (ngoData?.get("name") as? String)?.takeIf { it.isNotBlank() }
                        ?: (ngoData?.get("displayName") as? String)?.takeIf { it.isNotBlank() }
                        ?: (ngoData?.get("orgName") as? String)?.takeIf { it.isNotBlank() }
                        ?: _ngoName.value
                applyRegionFilter()
            }
            // Hold the indicator briefly so the user actually sees it spin.
            kotlinx.coroutines.delay(450)
            _isRefreshing.value = false
        }
    }

    fun onlineCounselorCount(): Int = _counselors.value.count { it.isAvailable }

    
    fun offlineCounselorCount(): Int = _counselors.value.count { !it.isAvailable }

    
    fun onlinePercent(): Float {
        val total = _counselors.value.size.toFloat().coerceAtLeast(1f)
        return (onlineCounselorCount() / total) * 100f
    }

    
    fun offlinePercent(): Float = (100f - onlinePercent()).coerceAtLeast(0f)
}


private fun Map<String, Any>.toCounselorProfile(): CounselorProfile = CounselorProfile(
    counselorId    = this["key"]?.toString()            ?: "",
    userId         = this["uid"]?.toString()            ?: "",
    name           = this["assignedName"]?.toString()   ?: "",
    specialization = this["specialization"]?.toString() ?: "",
    bio            = this["bio"]?.toString()            ?: "",
    isAvailable    = this["isOnline"]  as? Boolean      ?: false,
    isVerified     = this["isActive"]  as? Boolean      ?: true,
    ngoName        = this["ngoName"]?.toString()        ?: "",
    region         = this["region"]?.toString()         ?: "",
    rating         = (this["rating"]       as? Double)?.toFloat() ?: 0f,
    totalRatings   = (this["totalRatings"] as? Long)?.toInt()    ?: 0,
    sessionCount   = (this["sessionCount"] as? Long)?.toInt()    ?: 0,
)
