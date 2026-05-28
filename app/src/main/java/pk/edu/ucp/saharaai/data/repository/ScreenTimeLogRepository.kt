package pk.edu.ucp.saharaai.data.repository

import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

/**
 * Writes one row per bad-app session into
 * `users/{uid}/screen_time_log/{auto-id}`. The Sahara Risk weekly
 * aggregator reads these to compute the screen_time feature.
 *
 * **Only BAD / BRAINROT apps end up here.** Normal and unknown apps are
 * silently dropped by the caller (`AppTrackerService`) — they never
 * leave a trace in Firestore.
 */
object ScreenTimeLogRepository {

    private val firestore by lazy { Firebase.firestore }

    suspend fun logBadAppUsage(
        uid: String,
        packageHash: String,
        packageName: String,
        appName: String?,
        category: String,
        severity: Double,
        minutes: Double,
    ): Result<String> = runCatching {
        val payload = mapOf(
            "package_hash"   to packageHash,
            "package_name"   to packageName,
            "app_name"       to appName,
            "category"       to category,
            // The Python aggregator multiplies minutes by app_severity to
            // get weighted_bad_minutes; persisting both keeps the audit
            // trail readable.
            "app_severity"   to severity,
            "minutes"        to minutes,
            "recordedAt"     to FieldValue.serverTimestamp(),
        )
        val ref = firestore
            .collection("users").document(uid)
            .collection("screen_time_log")
            .add(payload)
            .await()
        ref.id
    }
}
