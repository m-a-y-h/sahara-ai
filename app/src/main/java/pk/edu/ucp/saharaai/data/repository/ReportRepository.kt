package pk.edu.ucp.saharaai.data.repository

import com.google.firebase.Timestamp
import pk.edu.ucp.saharaai.data.model.ModerationReport
import pk.edu.ucp.saharaai.data.model.ReportStatus
import pk.edu.ucp.saharaai.data.remote.FirestoreService

object ReportRepository {

    
    suspend fun submitReport(
        reportedBy: String,
        targetId: String,
        targetType: String,
        reason: String,
        description: String
    ): Result<String> {
        val report = ModerationReport(
            reportedBy  = reportedBy,
            targetId    = targetId,
            targetType  = targetType,
            reason      = reason,
            description = description,
            status      = ReportStatus.OPEN.name,
            createdAt   = Timestamp.now()
        )
        return FirestoreService.submitReport(report)
    }

    
    suspend fun getOpenReports(): Result<List<ModerationReport>> =
        FirestoreService.getOpenReports()

    
    suspend fun resolveReport(
        reportId: String,
        reviewerId: String,
        reviewNote: String
    ): Result<Unit> = FirestoreService.updateReportStatus(
        reportId   = reportId,
        status     = ReportStatus.RESOLVED.name,
        reviewerId = reviewerId,
        reviewNote = reviewNote
    )

    
    suspend fun dismissReport(
        reportId: String,
        reviewerId: String,
        reviewNote: String
    ): Result<Unit> = FirestoreService.updateReportStatus(
        reportId   = reportId,
        status     = ReportStatus.DISMISSED.name,
        reviewerId = reviewerId,
        reviewNote = reviewNote
    )

    
    suspend fun startReview(
        reportId: String,
        reviewerId: String
    ): Result<Unit> = FirestoreService.updateReportStatus(
        reportId   = reportId,
        status     = ReportStatus.UNDER_REVIEW.name,
        reviewerId = reviewerId,
        reviewNote = "Under review"
    )
}
