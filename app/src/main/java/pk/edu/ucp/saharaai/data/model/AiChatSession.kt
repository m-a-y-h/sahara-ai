package pk.edu.ucp.saharaai.data.model

import com.google.firebase.Timestamp

data class AiChatSession(
    val sessionId: String = "",
    val userId: String = "",
    val title: String = "Untitled",
    val titleStatus: String = TITLE_PENDING,
    val createdAt: Timestamp? = null,
    val clientCreatedAt: Timestamp = Timestamp.now(),
    val clientCreatedAtMillis: Long = 0L,
    val updatedAt: Timestamp? = null,
    val lastMessage: String = "",
    val messageCount: Int = 0,
    val isDeleted: Boolean = false,
) {
    companion object {
        const val TYPE_AI = "ai"
        const val TITLE_PENDING = "pending_server_timestamp"
        const val TITLE_READY = "server_timestamped"
    }
}
