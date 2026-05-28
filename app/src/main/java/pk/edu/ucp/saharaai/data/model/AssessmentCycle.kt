package pk.edu.ucp.saharaai.data.model

/**
 * Server-side lifecycle marker written by the Sahara Risk cron at
 * ``users/{uid}/lifecycle/current``.
 */
data class AssessmentCycleStatus(
    val assessmentRequired: Boolean = false,
    val completedAtIso: String = "",
    val completedAtEpochMs: Long = 0L,
    val latestReportId: String = "",
    val currentCycleId: String = "",
    val reportAcknowledged: Boolean = true,
) {
    companion object {
        fun fromFirestore(data: Map<String, Any?>): AssessmentCycleStatus {
            return AssessmentCycleStatus(
                assessmentRequired = data["assessment_required"] as? Boolean ?: false,
                completedAtIso = data["completed_at"] as? String ?: "",
                completedAtEpochMs = (data["completed_at_epoch_ms"] as? Number)?.toLong() ?: 0L,
                latestReportId = data["latest_report_id"] as? String ?: "",
                currentCycleId = data["current_cycle_id"] as? String ?: "",
                reportAcknowledged = data["report_acknowledged"] as? Boolean ?: true,
            )
        }
    }
}

/**
 * Six-month digest written to ``users/{uid}/cumulative_reports/{cycle_id}``.
 */
data class CumulativeRiskReport(
    val userId: String = "",
    val cycleId: String = "",
    val monitoringStartsAtIso: String = "",
    val monitoringEndsAtIso: String = "",
    val initialDastScore: Int = 0,
    val initialRiskScore: Double = 0.0,
    val finalRiskScore: Double = 0.0,
    val overallDelta: Double = 0.0,
    val riskTrajectory: List<Double> = emptyList(),
    val weeksInSeverity: Map<String, Int> = emptyMap(),
    val featureSourceSummary: Map<String, Map<String, Double>> = emptyMap(),
    val totalRecoveryCredit: Double = 0.0,
    val completedWeeks: Int = 0,
    val weeksWithNoEvidence: Int = 0,
    val generatedAtIso: String = "",
    val modelVersion: String = "",
    val acknowledged: Boolean = false,
) {
    val finalRiskPercent: Int get() = (finalRiskScore.coerceIn(0.0, 1.0) * 100).toInt()

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromFirestore(uid: String, data: Map<String, Any?>): CumulativeRiskReport {
            return CumulativeRiskReport(
                userId = data["user_id"] as? String ?: uid,
                cycleId = data["cycle_id"] as? String ?: "",
                monitoringStartsAtIso = data["monitoring_starts_at"] as? String ?: "",
                monitoringEndsAtIso = data["monitoring_ends_at"] as? String ?: "",
                initialDastScore = (data["initial_dast_score"] as? Number)?.toInt() ?: 0,
                initialRiskScore = (data["initial_risk_score"] as? Number)?.toDouble() ?: 0.0,
                finalRiskScore = (data["final_risk_score"] as? Number)?.toDouble() ?: 0.0,
                overallDelta = (data["overall_delta"] as? Number)?.toDouble() ?: 0.0,
                riskTrajectory = (data["risk_trajectory"] as? List<Number>)
                    .orEmpty().map { it.toDouble() },
                weeksInSeverity = (data["weeks_in_severity"] as? Map<String, Number>)
                    .orEmpty().mapValues { it.value.toInt() },
                featureSourceSummary = (data["feature_source_summary"] as? Map<String, Map<String, Number>>)
                    .orEmpty().mapValues { (_, summary) -> summary.mapValues { it.value.toDouble() } },
                totalRecoveryCredit = (data["total_recovery_credit"] as? Number)?.toDouble() ?: 0.0,
                completedWeeks = (data["completed_weeks"] as? Number)?.toInt() ?: 0,
                weeksWithNoEvidence = (data["weeks_with_no_evidence"] as? Number)?.toInt() ?: 0,
                generatedAtIso = data["generated_at"] as? String ?: "",
                modelVersion = data["model_version"] as? String ?: "",
                acknowledged = data["acknowledged"] as? Boolean ?: false,
            )
        }
    }
}
