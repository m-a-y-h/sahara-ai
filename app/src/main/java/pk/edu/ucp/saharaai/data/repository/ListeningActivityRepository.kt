package pk.edu.ucp.saharaai.data.repository

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import pk.edu.ucp.saharaai.data.model.FlaggedTrack
import pk.edu.ucp.saharaai.data.model.WeeklyListeningReport

/**
 * Firestore access for the listening-risk feature.
 *
 * The Modal cron (`services/sahara_listening/modal_deploy.py`) writes:
 *   * `users/{uid}/activity_log_flags/{auto-id}`   — one row per flagged track
 *   * `users/{uid}/weekly_reports/{week_start_iso}` — one digest per week
 *   * `users/{uid}/weekly_report_dismissals/{week_start_iso}` — popup closures
 *
 * The Android client reads those and never the raw listening history. All
 * methods return `Result` so the UI can surface errors without hiding them.
 */
object ListeningActivityRepository {

    private const val COLLECTION_FLAGS = "activity_log_flags"
    private const val COLLECTION_REPORTS = "weekly_reports"
    private const val COLLECTION_DISMISSALS = "weekly_report_dismissals"

    private val firestore by lazy { Firebase.firestore }
    private val auth by lazy { Firebase.auth }

    private fun requireUid(): Result<String> {
        val uid = auth.currentUser?.uid
        return if (uid.isNullOrBlank()) {
            Result.failure(IllegalStateException("not signed in"))
        } else {
            Result.success(uid)
        }
    }

    /**
     * Newest-first list of flagged tracks across all weeks. The activity log
     * screen consumes this; pagination is left as future work — the typical
     * volume is ~10–30 rows per week so the eager fetch is fine for now.
     */
    suspend fun listFlaggedTracks(limit: Long = 200): Result<List<FlaggedTrack>> {
        val uid = requireUid().getOrElse { return Result.failure(it) }
        return runCatching {
            firestore
                .collection("users").document(uid)
                .collection(COLLECTION_FLAGS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()
                .documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    FlaggedTrack.fromFirestore(doc.id, data)
                }
        }
    }

    /** Newest-first list of past weekly reports. */
    suspend fun listWeeklyReports(limit: Long = 26): Result<List<WeeklyListeningReport>> {
        val uid = requireUid().getOrElse { return Result.failure(it) }
        return runCatching {
            firestore
                .collection("users").document(uid)
                .collection(COLLECTION_REPORTS)
                .orderBy("week_start", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()
                .documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    WeeklyListeningReport.fromFirestore(data)
                }
        }
    }

    /**
     * Return the most recent weekly report whose popup the user hasn't yet
     * dismissed. Used by the dashboard to decide whether to show the modal.
     */
    suspend fun unseenLatestReport(): Result<WeeklyListeningReport?> {
        val uid = requireUid().getOrElse { return Result.failure(it) }
        return runCatching {
            val reports = firestore
                .collection("users").document(uid)
                .collection(COLLECTION_REPORTS)
                .orderBy("week_start", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()
                .documents
            val latest = reports.firstOrNull() ?: return@runCatching null
            val data = latest.data ?: return@runCatching null
            val report = WeeklyListeningReport.fromFirestore(data)
            // Has the user already closed this week's popup?
            val dismissed = firestore
                .collection("users").document(uid)
                .collection(COLLECTION_DISMISSALS)
                .document(report.weekStartIso)
                .get()
                .await()
                .exists()
            if (dismissed) null else report
        }
    }

    /** Mark the popup as closed for this week (doesn't delete the report). */
    suspend fun dismissReport(weekStartIso: String): Result<Unit> {
        val uid = requireUid().getOrElse { return Result.failure(it) }
        return runCatching {
            firestore
                .collection("users").document(uid)
                .collection(COLLECTION_DISMISSALS)
                .document(weekStartIso)
                .set(mapOf("dismissedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
                .await()
            Unit
        }
    }

    /** Permanently delete a weekly report (user-requested). */
    suspend fun deleteReport(weekStartIso: String): Result<Unit> {
        val uid = requireUid().getOrElse { return Result.failure(it) }
        return runCatching {
            firestore
                .collection("users").document(uid)
                .collection(COLLECTION_REPORTS)
                .document(weekStartIso)
                .delete()
                .await()
            Unit
        }
    }

    /** Delete a single flagged track row from the activity log. */
    suspend fun deleteFlaggedTrack(docId: String): Result<Unit> {
        val uid = requireUid().getOrElse { return Result.failure(it) }
        return runCatching {
            firestore
                .collection("users").document(uid)
                .collection(COLLECTION_FLAGS)
                .document(docId)
                .delete()
                .await()
            Unit
        }
    }
}
