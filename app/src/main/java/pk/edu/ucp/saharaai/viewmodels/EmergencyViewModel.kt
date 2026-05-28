package pk.edu.ucp.saharaai.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.repository.EmergencyContact
import pk.edu.ucp.saharaai.data.repository.EmergencyRepository

sealed class EmergencyUiState {
    object Idle        : EmergencyUiState()
    object Sending     : EmergencyUiState()
    object AlertSent   : EmergencyUiState()
    data class Error(val message: String) : EmergencyUiState()
}

class EmergencyViewModel : ViewModel() {

    private val _contacts  = MutableStateFlow<List<EmergencyContact>>(emptyList())
    val contacts: StateFlow<List<EmergencyContact>> = _contacts.asStateFlow()

    private val _uiState   = MutableStateFlow<EmergencyUiState>(EmergencyUiState.Idle)
    val uiState: StateFlow<EmergencyUiState> = _uiState.asStateFlow()

    init {
        
        _contacts.value = EmergencyRepository.getEmergencyContacts()
    }

    
    fun triggerSOSForSignedInUser(
        latitude: Double,
        longitude: Double,
        locationName: String,
        riskScore: Float = 100f,
        ngoId: String = ""
    ) {
        val user = Firebase.auth.currentUser ?: return
        triggerSOS(
            userId = user.uid,
            userName = user.displayName ?: "Anonymous",
            latitude = latitude,
            longitude = longitude,
            locationName = locationName,
            riskScore = riskScore,
            ngoId = ngoId
        )
    }

    fun triggerSOS(
        userId: String,
        userName: String,
        latitude: Double,
        longitude: Double,
        locationName: String,
        riskScore: Float = 100f,
        ngoId: String = ""
    ) {
        viewModelScope.launch {
            _uiState.value = EmergencyUiState.Sending
            val result = EmergencyRepository.triggerSOS(
                userId       = userId,
                userName     = userName,
                latitude     = latitude,
                longitude    = longitude,
                locationName = locationName,
                riskScore    = riskScore,
                ngoId        = ngoId
            )
            _uiState.value = if (result.isSuccess)
                EmergencyUiState.AlertSent
            else
                EmergencyUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to send SOS."
                )
        }
    }

    fun resetState() { _uiState.value = EmergencyUiState.Idle }
}
