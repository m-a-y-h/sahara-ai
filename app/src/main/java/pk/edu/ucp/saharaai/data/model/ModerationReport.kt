package pk.edu.ucp.saharaai.data.model

import com.google.firebase.Timestamp

enum class ReportStatus { OPEN, UNDER_REVIEW, RESOLVED, DISMISSED }
enum class ReportReason { SPAM, HARASSMENT, MISINFORMATION, HARMFUL_CONTENT, OTHER }

data class ModerationReport(
    val reportId: String = "",
    val reportedBy: String = "",
    val targetId: String = "",
    val targetType: String = "POST",
    val reason: String = ReportReason.OTHER.name,
    val description: String = "",
    val status: String = ReportStatus.OPEN.name,
    val reviewedBy: String = "",
    val reviewNote: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val resolvedAt: Timestamp? = null
) {
    constructor() : this("", "", "", "POST", ReportReason.OTHER.name, "", ReportStatus.OPEN.name, "", "", Timestamp.now(), null)
}
