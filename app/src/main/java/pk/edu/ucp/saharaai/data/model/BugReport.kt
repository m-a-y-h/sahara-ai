package pk.edu.ucp.saharaai.data.model

data class BugReport(
    val reportId: String = "",
    val userId: String = "",
    val maskedEmail: String = "",
    val deviceModel: String = "",
    val screenshotUrl: String = "",
    val description: String = "",
    val status: String = "OPEN",
    val createdAt: Long = 0L,
    val resolvedAt: Long = 0L,
    val resolvedBy: String = "",
)
