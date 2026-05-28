package pk.edu.ucp.saharaai.viewmodels

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel

class WelcomeViewModel : ViewModel() {
    var showPrivacyPolicy by mutableStateOf(false)

    fun togglePrivacyPolicy(show: Boolean) {
        showPrivacyPolicy = show
    }
}
