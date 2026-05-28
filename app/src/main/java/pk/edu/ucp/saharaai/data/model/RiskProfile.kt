package pk.edu.ucp.saharaai.data.model

import com.google.firebase.Timestamp

/**
 * Mirror of the Python ``UserRiskProfile`` doc Sahara Risk writes to
 * ``users/{uid}/risk_profile/current``. The Android client reads it
 * read-only — only the Modal cron and the bootstrap function ever write.
 *
 * Field names mirror the snake_case Firestore payload from
 * ``services/sahara_risk/model.py::UserRiskProfile.to_firestore_dict``.
 */
data class RiskProfile(
    val userId: String,
    val initialDastScore: Int,
    val initialRiskScore: Double,
    val currentRiskScore: Double,
    val monitoringStartsAtIso: String,
    val weekIndex: Int,
    val lastUpdatedAtIso: String?,
) {
    /** Human-friendly bucket label used by the dashboard ring colour. */
    val severityLabel: String
        get() = when {
            currentRiskScore < 0.20 -> "low"
            currentRiskScore < 0.45 -> "moderate"
            currentRiskScore < 0.70 -> "substantial"
            else                    -> "severe"
        }

    /** Percentage value, 0..100, for tile / progress UI. */
    val riskPercent: Int get() = (currentRiskScore.coerceIn(0.0, 1.0) * 100).toInt()

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromFirestore(uid: String, data: Map<String, Any?>): RiskProfile {
            return RiskProfile(
                userId               = uid,
                initialDastScore     = (data["initial_dast_score"] as? Number)?.toInt() ?: 0,
                initialRiskScore     = (data["initial_risk_score"] as? Number)?.toDouble() ?: 0.0,
                currentRiskScore     = (data["current_risk_score"] as? Number)?.toDouble() ?: 0.0,
                monitoringStartsAtIso = (data["monitoring_starts_at"] as? String).orEmpty(),
                weekIndex            = (data["week_index"] as? Number)?.toInt() ?: 0,
                lastUpdatedAtIso     = data["last_updated_at"] as? String,
            )
        }
    }
}

/**
 * One row from ``users/{uid}/risk_history/{week_iso}`` — the audit trail of
 * the weekly EWMA update, useful for the trend graph and the "why did my
 * score change" detail card.
 */
data class WeeklyRiskHistoryRow(
    val weekIso: String,
    val weekIndex: Int,
    val previousRiskScore: Double,
    val newRiskScore: Double,
    val observation: Double,
    val recoveryCredit: Double,
    val alpha: Double,
    val appliedDelta: Double,
    val featureContributions: Map<String, Double>,
    val reasons: List<String>,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromFirestore(data: Map<String, Any?>): WeeklyRiskHistoryRow {
            return WeeklyRiskHistoryRow(
                weekIso              = data["week_iso"] as? String ?: "",
                weekIndex            = (data["week_index"] as? Number)?.toInt() ?: 0,
                previousRiskScore    = (data["previous_risk_score"] as? Number)?.toDouble() ?: 0.0,
                newRiskScore         = (data["new_risk_score"] as? Number)?.toDouble() ?: 0.0,
                observation          = (data["observation"] as? Number)?.toDouble() ?: 0.0,
                recoveryCredit       = (data["recovery_credit"] as? Number)?.toDouble() ?: 0.0,
                alpha                = (data["alpha"] as? Number)?.toDouble() ?: 0.0,
                appliedDelta         = (data["applied_delta"] as? Number)?.toDouble() ?: 0.0,
                featureContributions = (data["feature_contributions"] as? Map<String, Number>)
                    .orEmpty().mapValues { it.value.toDouble() },
                reasons              = (data["reasons"] as? List<String>).orEmpty(),
            )
        }
    }
}
