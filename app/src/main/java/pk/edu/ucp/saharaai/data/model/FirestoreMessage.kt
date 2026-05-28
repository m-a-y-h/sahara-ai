package pk.edu.ucp.saharaai.data.model

import com.google.firebase.Timestamp

data class FirestoreMessage(
    val messageId: String = "",
    val sessionId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val content: String = "",
    val isFromAI: Boolean = false,
    val isRead: Boolean = false,
    val messageType: String = "TEXT",
    val timestamp: Timestamp = Timestamp.now(),
    val senderType: String = ""
) {
    constructor() : this("", "", "", "", "", false, false, "TEXT", Timestamp.now(), "")
}
