package pk.edu.ucp.saharaai.data.model

data class AvatarRequest(
    val requestId: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val fileUrl: String = "",
    val fileName: String = "",
    val mimeType: String = "",
    val sizeBytes: Long = 0L,
    val status: String = "PENDING_REVIEW",
    val adminComment: String = "",
    val reviewedBy: String = "",
    val createdAt: Long = 0L,
    val reviewedAt: Long = 0L,
)
