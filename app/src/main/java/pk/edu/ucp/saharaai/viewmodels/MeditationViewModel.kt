package pk.edu.ucp.saharaai.viewmodels

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*

class MeditationViewModel : ViewModel() {
    var selectedMeditation by mutableStateOf("4-7-8 Breathing")
    var playingMeditation by mutableStateOf<String?>(null)
    var breathTextEn by mutableStateOf("Tap to Start")
    var breathTextUr by mutableStateOf("Shuru Karein")
    var elapsedSeconds by mutableIntStateOf(0)
        private set

    private var meditationJob: Job? = null

    fun selectMeditation(meditationName: String) {
        selectedMeditation = meditationName
    }

    fun toggleMeditation(
        meditationName: String,
        inhale: Long,
        hold: Long,
        exhale: Long,
        secondHold: Long,
    ) {
        if (playingMeditation == meditationName) {
            stopMeditation()
        } else {
            startMeditation(meditationName, inhale, hold, exhale, secondHold)
        }
    }

    private fun startMeditation(
        meditationName: String,
        inhale: Long,
        hold: Long,
        exhale: Long,
        secondHold: Long,
    ) {
        stopMeditation()
        selectedMeditation = meditationName
        playingMeditation = meditationName
        elapsedSeconds = 0
        
        meditationJob = viewModelScope.launch {
            while (playingMeditation != null) {
                breathTextEn = "Breathe In..."; breathTextUr = "Saans andar lein..."
                delay(inhale)

                if (playingMeditation == null) break
                if (hold > 0L) {
                    breathTextEn = "Hold..."; breathTextUr = "Rokein..."
                    delay(hold)
                }

                if (playingMeditation == null) break
                breathTextEn = "Breathe Out..."; breathTextUr = "Saans bahar chhorein..."
                delay(exhale)

                if (secondHold > 0L) {
                    if (playingMeditation == null) break
                    breathTextEn = "Hold..."; breathTextUr = "Rokein..."
                    delay(secondHold)
                }
            }
        }
    }

    fun stopMeditation() {
        playingMeditation = null
        meditationJob?.cancel()
        meditationJob = null
        breathTextEn = "Tap to Start"
        breathTextUr = "Shuru Karein"
        elapsedSeconds = 0
    }
}
