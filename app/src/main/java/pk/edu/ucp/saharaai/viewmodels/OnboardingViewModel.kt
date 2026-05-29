package pk.edu.ucp.saharaai.viewmodels

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import pk.edu.ucp.saharaai.ui.screens.GlobalAppState
import pk.edu.ucp.saharaai.utils.NotificationManager

class OnboardingViewModel : ViewModel() {
    var step by mutableIntStateOf(1)
    var ageGroup by mutableStateOf("")
    var currentSituation by mutableStateOf("")
    var selectedHelps by mutableStateOf(setOf<String>())
    var notificationsAllowed by mutableStateOf(false)
    var locationAllowed by mutableStateOf(false)
    var actigraphyAllowed by mutableStateOf(false)
    var selectedAvatarId by mutableStateOf("avatar_01")
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
            5 -> if (isEnglish) "Pick a preset. Custom photos can be requested later." else "Preset chunein. Custom photo baad mein request ho sakti hai."
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
}
