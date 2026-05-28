package pk.edu.ucp.saharaai.data.model

import com.google.firebase.Timestamp

data class SleepEntry(
    val entryId: String = "",
    val userId: String = "",
    val bedTime: String = "",              
    val wakeTime: String = "",
    val durationHours: Float = 0f,
    val quality: String = "FAIR",          
    val notes: String = "",
    val createdAt: Timestamp = Timestamp.now()
)
