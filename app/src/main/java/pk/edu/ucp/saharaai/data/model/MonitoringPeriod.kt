package pk.edu.ucp.saharaai.data.model

/**
 * Read-only mirror of ``users/{uid}/monitoring/active``. Lives in its own
 * file (rather than RiskProfile.kt) because the dashboard popup may need
 * the start/end timestamps even before the first weekly update has
 * happened and the risk profile doc is technically still seeded.
 */
data class MonitoringPeriod(
    val userId: String,
    val dastCompletedAtIso: String,
    val monitoringStartsAtIso: String,
    val monitoringEndsAtIso: String,
    val durationWeeks: Int,
) {
    companion object {
        fun fromFirestore(uid: String, data: Map<String, Any?>): MonitoringPeriod {
            return MonitoringPeriod(
                userId                = uid,
                dastCompletedAtIso    = data["dast_completed_at"] as? String ?: "",
                monitoringStartsAtIso = data["monitoring_starts_at"] as? String ?: "",
                monitoringEndsAtIso   = data["monitoring_ends_at"] as? String ?: "",
                durationWeeks         = (data["duration_weeks"] as? Number)?.toInt() ?: 26,
            )
        }
    }
}

/**
 * One-shot popup payload written at ``users/{uid}/monitoring/start_notice``
 * the first time the monitoring period opens. Cleared / marked-shown after
 * the user sees the popup so the dashboard doesn't re-show it.
 */
data class MonitoringStartNotice(
    val shown: Boolean,
    val monitoringStartsAtIso: String,
    val monitoringEndsAtIso: String,
    val durationWeeks: Int,
    val noticeTextEn: String,
    val noticeTextUr: String,
) {
    companion object {
        fun fromFirestore(data: Map<String, Any?>): MonitoringStartNotice {
            return MonitoringStartNotice(
                shown                 = data["shown"] as? Boolean ?: false,
                monitoringStartsAtIso = data["monitoring_starts_at"] as? String ?: "",
                monitoringEndsAtIso   = data["monitoring_ends_at"] as? String ?: "",
                durationWeeks         = (data["duration_weeks"] as? Number)?.toInt() ?: 26,
                noticeTextEn          = data["notice_text_en"] as? String ?: "",
                noticeTextUr          = data["notice_text_ur"] as? String ?: "",
            )
        }
    }
}
