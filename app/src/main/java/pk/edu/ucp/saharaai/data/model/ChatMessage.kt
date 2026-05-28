package pk.edu.ucp.saharaai.data.model

import com.google.firebase.Timestamp

data class ChatMessage(
    val messageId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val content: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val isFromAI: Boolean = false,
    val isRead: Boolean = false,
    val sessionId: String = ""
)
