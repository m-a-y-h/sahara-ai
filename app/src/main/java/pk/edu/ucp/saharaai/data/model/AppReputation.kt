package pk.edu.ucp.saharaai.data.model

import com.google.firebase.Timestamp
import java.security.MessageDigest

/**
 * A shared reputation row stored once globally at
 * ``app_reputation/{package_hash}``. The first user to hit an unknown
 * package writes the row after the backend reverse-search completes; every
 * other user reads it.
 *
 * Severity is on a [0, 1] scale and is also stored as a `bad_per_hour`
 * raw rate so the screen-time aggregator can blend the global rating with
 * the user's own usage minutes:
 *
 *     weighted_bad_minutes = minutes_today * severity
 *
 * Examples (rough, tuneable):
 *
 *     coursera        → NORMAL  → no row written
 *     youtube         → BAD     → severity ≈ 0.30 (mild doomscroll potential)
 *     instagram       → BAD     → severity ≈ 0.55
 *     tiktok          → BAD     → severity ≈ 0.85
 *     "chaato"        → BRAINROT→ severity ≈ 0.95
 *     unknown new app → UNKNOWN → no row written, ignored by the model
 *
 * Categorisation is driven by the backend reverse-search (looks up the
 * Play Store / AppBrain metadata of the package). Only BAD / BRAINROT
 * gets a hash row at all — UNKNOWN and NORMAL apps deliberately don't
 * leave a trace.
 */
data class AppReputation(
    val packageHash: String,
    val packageName: String?,
    val appName: String?,
    val category: AppReputationCategory,
    val severity: Double,
    val badPerHour: Double,
    val classifiedAt: Timestamp?,
    val classifierVersion: String?,
) {
    fun isBad(): Boolean = category == AppReputationCategory.BAD ||
                           category == AppReputationCategory.BRAINROT

    companion object {
        fun fromFirestore(packageHash: String, data: Map<String, Any?>): AppReputation {
            return AppReputation(
                packageHash       = packageHash,
                packageName       = data["package_name"] as? String,
                appName           = data["app_name"] as? String,
                category          = AppReputationCategory.fromWire(data["category"] as? String),
                severity          = (data["severity"] as? Number)?.toDouble() ?: 0.0,
                badPerHour        = (data["bad_per_hour"] as? Number)?.toDouble() ?: 0.0,
                classifiedAt      = data["classified_at"] as? Timestamp,
                classifierVersion = data["classifier_version"] as? String,
            )
        }

        /**
         * SHA-256 of the Android package id, lower-case hex. Used as both
         * the Firestore document id and as the Android client's local
         * cache key.
         *
         * Hashing rather than storing the package name directly keeps the
         * collection cheap to enumerate without leaking which apps the
         * user has ever opened — only opted-in (BAD) apps get their
         * package_name field populated, and even then it's at the
         * reputation row level, not on a per-user activity row.
         */
        fun hashPackage(packageName: String): String {
            val bytes = MessageDigest.getInstance("SHA-256")
                .digest(packageName.lowercase().trim().toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

enum class AppReputationCategory(val wire: String) {
    NORMAL("normal"),
    UNKNOWN("unknown"),
    BAD("bad"),
    BRAINROT("brainrot");

    companion object {
        fun fromWire(raw: String?): AppReputationCategory = entries.firstOrNull {
            it.wire.equals(raw, ignoreCase = true)
        } ?: UNKNOWN
    }
}
