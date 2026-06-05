package pk.edu.ucp.saharaai.viewmodels

import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import pk.edu.ucp.saharaai.ui.screens.GlobalAppState
import pk.edu.ucp.saharaai.utils.NotificationManager
import pk.edu.ucp.saharaai.utils.avatarPresenceLine

class OnboardingViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val stepState = mutableIntStateOf(savedStateHandle.get<Int>(KEY_STEP) ?: 1)
    var step: Int
        get() = stepState.intValue
        private set(value) {
            val cleanValue = value.coerceIn(1, 5)
            stepState.intValue = cleanValue
            savedStateHandle[KEY_STEP] = cleanValue
        }

    private val ageGroupState = mutableStateOf(savedStateHandle.get<String>(KEY_AGE_GROUP).orEmpty())
    var ageGroup: String
        get() = ageGroupState.value
        set(value) {
            ageGroupState.value = value
            savedStateHandle[KEY_AGE_GROUP] = value
        }

    private val currentSituationState = mutableStateOf(savedStateHandle.get<String>(KEY_CURRENT_SITUATION).orEmpty())
    var currentSituation: String
        get() = currentSituationState.value
        set(value) {
            currentSituationState.value = value
            savedStateHandle[KEY_CURRENT_SITUATION] = value
        }

    private val selectedHelpsState = mutableStateOf(
        savedStateHandle.get<ArrayList<String>>(KEY_SELECTED_HELPS)?.toSet() ?: emptySet()
    )
    var selectedHelps: Set<String>
        get() = selectedHelpsState.value
        set(value) {
            selectedHelpsState.value = value
            savedStateHandle[KEY_SELECTED_HELPS] = ArrayList(value)
        }

    private val notificationsAllowedState = mutableStateOf(savedStateHandle.get<Boolean>(KEY_NOTIFICATIONS_ALLOWED) ?: false)
    var notificationsAllowed: Boolean
        get() = notificationsAllowedState.value
        set(value) {
            notificationsAllowedState.value = value
            savedStateHandle[KEY_NOTIFICATIONS_ALLOWED] = value
        }

    private val locationAllowedState = mutableStateOf(savedStateHandle.get<Boolean>(KEY_LOCATION_ALLOWED) ?: false)
    var locationAllowed: Boolean
        get() = locationAllowedState.value
        set(value) {
            locationAllowedState.value = value
            savedStateHandle[KEY_LOCATION_ALLOWED] = value
        }

    private val actigraphyAllowedState = mutableStateOf(savedStateHandle.get<Boolean>(KEY_ACTIGRAPHY_ALLOWED) ?: false)
    var actigraphyAllowed: Boolean
        get() = actigraphyAllowedState.value
        set(value) {
            actigraphyAllowedState.value = value
            savedStateHandle[KEY_ACTIGRAPHY_ALLOWED] = value
        }

    private val selectedAvatarIdState = mutableStateOf(savedStateHandle.get<String>(KEY_SELECTED_AVATAR_ID) ?: "avatar_01")
    var selectedAvatarId: String
        get() = selectedAvatarIdState.value
        set(value) {
            selectedAvatarIdState.value = value
            savedStateHandle[KEY_SELECTED_AVATAR_ID] = value
        }

    var isSaving by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf("")
        private set

    val isNextEnabled: Boolean
        get() = when (step) {
            1 -> ageGroup.isNotEmpty()
            2 -> currentSituation.isNotEmpty()
            3 -> selectedHelps.size >= 2
            5 -> selectedAvatarId.isNotBlank() && !isSaving
            else -> true
        }

    fun getHeaderTitle(isEnglish: Boolean): String {
        return when (step) {
            1 -> if (isEnglish) "Your Age Group?" else "Aapka Age Group?"
            2 -> if (isEnglish) "Current Situation" else "Halaat"
            3 -> if (isEnglish) "What Help Do You Need?" else "Kaunsi Help Chahiye?"
            4 -> if (isEnglish) "Permissions" else "Ijazat"
            5 -> if (isEnglish) "Choose Your Avatar" else "Apna Avatar Chunein"
            else -> ""
        }
    }

    fun getHeaderSubtitle(isEnglish: Boolean): String {
        return when (step) {
            1 -> if (isEnglish) "Customizes your journey." else "Behtar madad ke liye age batayein."
            2 -> if (isEnglish) "Tell us your focus." else "Yeh completely anonymous hai."
            3 -> if (isEnglish) "Select at least 2 categories." else "Kam az kam 2 options select karein."
            4 -> if (isEnglish) "Enable only the background safety features." else "Sirf background safety features enable karein."
            5 -> avatarPresenceLine(selectedAvatarId, isEnglish)
            else -> ""
        }
    }

    fun nextStep(onComplete: () -> Unit) {
        if (step < 5) {
            step++
        } else {
            val uid = Firebase.auth.currentUser?.uid.orEmpty()
            if (uid.isBlank()) {
                errorMessage = "Please sign in again before finishing onboarding."
                return
            }
            viewModelScope.launch {
                isSaving = true
                errorMessage = ""
                RealtimeDBService.completeOnboarding(
                    uid = uid,
                    ageGroup = ageGroup,
                    currentSituation = currentSituation,
                    selectedHelps = selectedHelps,
                    avatarId = selectedAvatarId,
                    notificationsAllowed = notificationsAllowed,
                    locationAllowed = locationAllowed,
                    actigraphyAllowed = actigraphyAllowed,
                ).onSuccess {
                    GlobalAppState.isMinor = (ageGroup == "18 se Kam")
                    NotificationManager.logWelcome()
                    onComplete()
                }.onFailure {
                    errorMessage = it.message ?: "Could not finish onboarding."
                }
                isSaving = false
            }
        }
    }

    fun previousStep() {
        if (step > 1) {
            step--
        }
    }

    fun toggleHelp(help: String) {
        selectedHelps = if (selectedHelps.contains(help)) selectedHelps - help else selectedHelps + help
    }

    private companion object {
        const val KEY_STEP = "onboarding_step"
        const val KEY_AGE_GROUP = "onboarding_age_group"
        const val KEY_CURRENT_SITUATION = "onboarding_current_situation"
        const val KEY_SELECTED_HELPS = "onboarding_selected_helps"
        const val KEY_NOTIFICATIONS_ALLOWED = "onboarding_notifications_allowed"
        const val KEY_LOCATION_ALLOWED = "onboarding_location_allowed"
        const val KEY_ACTIGRAPHY_ALLOWED = "onboarding_actigraphy_allowed"
        const val KEY_SELECTED_AVATAR_ID = "onboarding_selected_avatar_id"
    }
}
