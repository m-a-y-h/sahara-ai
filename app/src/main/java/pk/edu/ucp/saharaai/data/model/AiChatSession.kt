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
    /**
     * Running summaries of each 16-message (8 user + 8 AI) batch we've
     * already collapsed out of live history, in order of generation. The
     * Qalb prompt prepends them as "Earlier context" so the model still
     * has continuity past the live-history window.
     */
    val batchSummaries: List<String> = emptyList(),
    /**
     * Server-timestamp (ms) of the latest message already folded into
     * [batchSummaries]. Messages with timestamp <= this are represented
     * by a summary; anything newer is live history and gets sent to Qalb
     * verbatim. Zero = nothing summarised yet.
     */
    val summarizedThroughMs: Long = 0L,
) {
    companion object {
        const val TYPE_AI = "ai"
        const val TITLE_PENDING = "pending_server_timestamp"
        const val TITLE_READY = "server_timestamped"
    }
}
