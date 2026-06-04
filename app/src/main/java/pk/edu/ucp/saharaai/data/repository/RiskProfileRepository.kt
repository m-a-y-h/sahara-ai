package pk.edu.ucp.saharaai.data.repository

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import pk.edu.ucp.saharaai.data.model.MonitoringPeriod
import pk.edu.ucp.saharaai.data.model.MonitoringStartNotice
import pk.edu.ucp.saharaai.data.model.AssessmentCycleStatus
import pk.edu.ucp.saharaai.data.model.CumulativeRiskReport
import pk.edu.ucp.saharaai.data.model.RiskProfile
import pk.edu.ucp.saharaai.data.model.WeeklyRiskHistoryRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Read access for the per-user risk model state.
 *
 * Android bootstraps a new monitoring cycle when the assessment is saved.
 * The Modal cron (services/sahara_risk/modal_deploy.py) owns weekly score
 * updates, cumulative reports, and end-of-cycle reset.
 */
object RiskProfileRepository {

    private val firestore by lazy { Firebase.firestore }
    private val auth by lazy { Firebase.auth }
    private const val WEEK_MS = 7L * 24L * 60L * 60L * 1000L
    private const val MONITORING_WEEKS = 26L
    private val riskFromDast = mapOf(
        0 to 0.05,
        1 to 0.15,
        2 to 0.22,
        3 to 0.35,
        4 to 0.42,
        5 to 0.50,
        6 to 0.62,
        7 to 0.70,
        8 to 0.78,
        9 to 0.88,
        10 to 0.95,
    )

    private fun requireUid(): String? = auth.currentUser?.uid

    private fun isoUtc(ms: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date(ms))
    }

    suspend fun registerAssessmentCycle(dastScore: Int, completedAtMs: Long): Result<Unit> {
        val uid = requireUid() ?: return Result.failure(IllegalStateException("not signed in"))
        return runCatching {
            val cleanScore = dastScore.coerceIn(0, 10)
            val initialRisk = riskFromDast[cleanScore] ?: 0.05
            val completedAt = completedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()
            val startsAt = completedAt + WEEK_MS
            val endsAt = startsAt + (MONITORING_WEEKS * WEEK_MS)
            val completedIso = isoUtc(completedAt)
            val startsIso = isoUtc(startsAt)
            val endsIso = isoUtc(endsAt)

            // Document IDs must avoid characters that have tripped "invalid
            // token in path" on some Firestore SDK paths — notably `:` and
            // `+` from the ISO-8601 timezone suffix (`2026-06-04T01:11:32+00:00`).
            // Pure epoch-millis strings are guaranteed to be safe.
            val historyDocId = completedAt.toString()
            val cycleDocId = startsAt.toString()

            val userRef = firestore.collection("users").document(uid)
            val batch = firestore.batch()
            batch.set(
                userRef.collection("monitoring").document("active"),
                mapOf(
                    "user_id" to uid,
                    "dast_completed_at" to completedIso,
                    "monitoring_starts_at" to startsIso,
                    "monitoring_ends_at" to endsIso,
                    "duration_weeks" to MONITORING_WEEKS,
                ),
                SetOptions.merge(),
            )
            batch.set(
                userRef.collection("monitoring").document("start_notice"),
                mapOf(
                    "shown" to false,
                    "monitoring_starts_at" to startsIso,
                    "monitoring_ends_at" to endsIso,
                    "duration_weeks" to MONITORING_WEEKS,
                    "notice_text_en" to (
                        "Sahara's 6-month behaviour-cycle monitoring has begun. " +
                            "Use the app honestly so your score stays useful."
                        ),
                    "notice_text_ur" to (
                        "Sahara ka 6-month behaviour-cycle monitoring shuru ho gaya hai. " +
                            "App imandari se use karein taake score madadgar rahe."
                        ),
                ),
                SetOptions.merge(),
            )
            batch.set(
                userRef.collection("risk_profile").document("current"),
                mapOf(
                    "user_id" to uid,
                    "initial_dast_score" to cleanScore,
                    "initial_risk_score" to initialRisk,
                    "current_risk_score" to initialRisk,
                    "monitoring_starts_at" to startsIso,
                    "week_index" to 0,
                    "running_stats" to emptyMap<String, Any>(),
                    "last_updated_at" to null,
                ),
                SetOptions.merge(),
            )
            batch.set(
                userRef.collection("risk_history").document(historyDocId),
                mapOf(
                    "user_id" to uid,
                    "week_index" to 0,
                    "week_iso" to completedIso,
                    "previous_risk_score" to initialRisk,
                    "observation" to initialRisk,
                    "recovery_credit" to 0.0,
                    "raw_delta" to 0.0,
                    "applied_delta" to 0.0,
                    "new_risk_score" to initialRisk,
                    "alpha" to 1.0,
                    "feature_contributions" to emptyMap<String, Any>(),
                    "feature_anomalies" to emptyMap<String, Any>(),
                    "feature_vector" to emptyMap<String, Any>(),
                    "reasons" to listOf("bootstrap from DAST score; monitoring window opens 1 week later"),
                ),
                SetOptions.merge(),
            )
            batch.set(
                userRef.collection("lifecycle").document("current"),
                mapOf(
                    "assessment_required" to false,
                    "active_cycle_id" to cycleDocId,
                    "active_cycle_iso" to startsIso,
                    "monitoring_starts_at" to startsIso,
                    "monitoring_ends_at" to endsIso,
                    "updated_at" to isoUtc(System.currentTimeMillis()),
                ),
                SetOptions.merge(),
            )
            batch.commit().await()
            Unit
        }.onFailure { android.util.Log.e("RiskProfileRepo", "registerAssessmentCycle failed", it) }
    }

    suspend fun loadProfile(): Result<RiskProfile?> {
        val uid = requireUid() ?: return Result.failure(IllegalStateException("not signed in"))
        return runCatching {
            val snap = firestore
                .collection("users").document(uid)
                .collection("risk_profile").document("current")
                .get().await()
            snap.data?.let { RiskProfile.fromFirestore(uid, it) }
        }
    }

    suspend fun loadMonitoringPeriod(): Result<MonitoringPeriod?> {
        val uid = requireUid() ?: return Result.failure(IllegalStateException("not signed in"))
        return runCatching {
            val snap = firestore
                .collection("users").document(uid)
                .collection("monitoring").document("active")
                .get().await()
            snap.data?.let { MonitoringPeriod.fromFirestore(uid, it) }
        }
    }

    suspend fun loadAssessmentCycleStatus(): Result<AssessmentCycleStatus?> {
        val uid = requireUid() ?: return Result.failure(IllegalStateException("not signed in"))
        return runCatching {
            val snap = firestore
                .collection("users").document(uid)
                .collection("lifecycle").document("current")
                .get().await()
            snap.data?.let { AssessmentCycleStatus.fromFirestore(it) }
        }
    }

    suspend fun loadPendingCumulativeReport(): Result<CumulativeRiskReport?> {
        val uid = requireUid() ?: return Result.failure(IllegalStateException("not signed in"))
        return runCatching {
            val snap = firestore
                .collection("users").document(uid)
                .collection("cumulative_reports")
                .orderBy("generated_at", Query.Direction.DESCENDING)
                .limit(1)
                .get().await()
            val doc = snap.documents.firstOrNull() ?: return@runCatching null
            val data = doc.data ?: return@runCatching null
            val report = CumulativeRiskReport.fromFirestore(uid, data)
            if (report.acknowledged) null else report
        }
    }

    suspend fun markCumulativeReportAcknowledged(cycleId: String): Result<Unit> {
        val uid = requireUid() ?: return Result.failure(IllegalStateException("not signed in"))
        return runCatching {
            if (cycleId.isBlank()) return@runCatching
            firestore
                .collection("users").document(uid)
                .collection("cumulative_reports").document(cycleId)
                .update("acknowledged", true)
                .await()
            firestore
                .collection("users").document(uid)
                .collection("lifecycle").document("current")
                .set(
                    mapOf(
                        "report_acknowledged" to true,
                        "updated_at" to com.google.firebase.Timestamp.now(),
                    ),
                    SetOptions.merge(),
                )
                .await()
            Unit
        }
    }

    /** Reads the one-shot start notice — null when none pending. */
    suspend fun loadPendingStartNotice(): Result<MonitoringStartNotice?> {
        val uid = requireUid() ?: return Result.failure(IllegalStateException("not signed in"))
        return runCatching {
            val snap = firestore
                .collection("users").document(uid)
                .collection("monitoring").document("start_notice")
                .get().await()
            val data = snap.data ?: return@runCatching null
            val notice = MonitoringStartNotice.fromFirestore(data)
            if (notice.shown) null else notice
        }
    }

    /** Mark the start notice as shown so the dashboard doesn't re-fire the popup. */
    suspend fun markStartNoticeShown(): Result<Unit> {
        val uid = requireUid() ?: return Result.failure(IllegalStateException("not signed in"))
        return runCatching {
            firestore
                .collection("users").document(uid)
                .collection("monitoring").document("start_notice")
                .update(mapOf("shown" to true))
                .await()
            Unit
        }
    }

    /** Weekly history rows in reverse chronological order. */
    suspend fun loadHistory(limit: Long = 26): Result<List<WeeklyRiskHistoryRow>> {
        val uid = requireUid() ?: return Result.failure(IllegalStateException("not signed in"))
        return runCatching {
            firestore
                .collection("users").document(uid)
                .collection("risk_history")
                .orderBy("week_iso", Query.Direction.DESCENDING)
                .limit(limit)
                .get().await()
                .documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    WeeklyRiskHistoryRow.fromFirestore(data)
                }
        }
    }
}
