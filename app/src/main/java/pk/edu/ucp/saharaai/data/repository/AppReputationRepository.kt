package pk.edu.ucp.saharaai.data.repository

import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import pk.edu.ucp.saharaai.data.model.AppReputation
import pk.edu.ucp.saharaai.data.model.AppReputationCategory
import java.util.concurrent.ConcurrentHashMap

/**
 * Read + write access for the global `app_reputation/{hash}` collection.
 *
 * Read-through, write-once-on-discovery semantics:
 *
 *   1. The accessibility service or screen-time aggregator hits
 *      [lookup] for every distinct package the user opens this session.
 *   2. If the doc exists in Firestore → cached locally for the remainder
 *      of the process; the screen-time logger uses its severity.
 *   3. If the doc does *not* exist → the client writes a placeholder
 *      `category = unknown` doc and **enqueues** the package id for the
 *      backend reverse-search worker (Modal cron / Cloud Function). The
 *      backend fills in the real category once the lookup completes.
 *
 * The local cache keeps the network round-trips off the hot path so a
 * user who flips between five apps in 30s pays one Firestore read per app
 * per process boot.
 */
object AppReputationRepository {

    private val firestore by lazy { Firebase.firestore }
    private val cache: MutableMap<String, AppReputation> = ConcurrentHashMap()

    /**
     * Look up the reputation for `packageName`. Returns `null` if the
     * package is genuinely unknown to the global registry *and* couldn't
     * be auto-classified. Hits the local cache first.
     */
    suspend fun lookup(packageName: String, appName: String? = null): AppReputation? {
        val hash = AppReputation.hashPackage(packageName)
        cache[hash]?.let { return it }
        val snap = firestore.collection("app_reputation").document(hash).get().await()
        if (snap.exists()) {
            val data = snap.data ?: return null
            val rep = AppReputation.fromFirestore(hash, data)
            cache[hash] = rep
            return rep
        }
        // Unknown package — enqueue for the backend reverse-search worker.
        // The worker writes the final row; we leave a *placeholder* doc so
        // concurrent users don't keep enqueueing the same one.
        enqueueForClassification(hash, packageName, appName)
        return null
    }

    /**
     * Fast synchronous version for callers that already have a cached
     * reputation — returns null if we haven't looked up this package yet.
     */
    fun cached(packageName: String): AppReputation? =
        cache[AppReputation.hashPackage(packageName)]

    /**
     * Backend worker hook — the Modal reverse-search function writes the
     * final classified row here once the package has been categorised.
     * Exposed so an admin tool / a Cloud Function trigger can call it,
     * not used directly from the user-facing client today.
     */
    suspend fun writeClassification(reputation: AppReputation): Result<Unit> = runCatching {
        firestore.collection("app_reputation").document(reputation.packageHash)
            .set(
                mapOf(
                    "package_name"     to reputation.packageName,
                    "app_name"         to reputation.appName,
                    "category"         to reputation.category.wire,
                    "severity"         to reputation.severity,
                    "bad_per_hour"     to reputation.badPerHour,
                    "classified_at"    to FieldValue.serverTimestamp(),
                    "classifier_version" to (reputation.classifierVersion ?: "v1"),
                )
            )
            .await()
        cache[reputation.packageHash] = reputation
        Unit
    }

    /** Drop the local cache — used by signed-out / signed-in transitions. */
    fun clearCache() { cache.clear() }


    private suspend fun enqueueForClassification(
        hash: String,
        packageName: String,
        appName: String?,
    ) {
        runCatching {
            // Placeholder row keyed by the hash itself. The backend reverse-search
            // worker (Cloud Function / Modal) overwrites this with the final
            // category once its lookup completes. Only the hash + minimal
            // metadata is stored here — no per-user identifiers.
            firestore.collection("app_reputation").document(hash)
                .set(
                    mapOf(
                        "package_name"     to packageName,
                        "app_name"         to appName,
                        "category"         to AppReputationCategory.UNKNOWN.wire,
                        "severity"         to 0.0,
                        "bad_per_hour"     to 0.0,
                        "classified_at"    to null,
                        "classifier_version" to null,
                        "queued_at"        to FieldValue.serverTimestamp(),
                    ),
                    com.google.firebase.firestore.SetOptions.merge(),
                )
                .await()
        }.getOrElse { /* swallow — the screen-time logger keeps working */ }
    }
}
