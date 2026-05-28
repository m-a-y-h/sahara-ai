package pk.edu.ucp.saharaai.data.model

import com.google.firebase.Timestamp

data class JournalEntry(
    val entryId: String = "",
    val userId: String = "",
    val mood: String = "CALM",             
    val moodScore: Int = 5,                
    val content: String = "",
    val sentimentScore: Float = 0f,        
    val triggers: List<String> = emptyList(),
    val createdAt: Timestamp = Timestamp.now()
)
