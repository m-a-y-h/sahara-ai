package pk.edu.ucp.saharaai.utils

import android.content.Context
import android.content.SharedPreferences
import pk.edu.ucp.saharaai.ASSESSMENT_VALIDITY_MS
import pk.edu.ucp.saharaai.KEY_ASSESSMENT_EVER_COMPLETED
import pk.edu.ucp.saharaai.KEY_ASSESSMENT_SCORE
import pk.edu.ucp.saharaai.KEY_ASSESSMENT_TIMESTAMP
import pk.edu.ucp.saharaai.ui.screens.GlobalAppState

data class CachedAssessment(
    val score: Int,
    val timestampMs: Long,
    val everCompleted: Boolean,
) {
    val isVerifiable: Boolean get() = score >= 0 && timestampMs > 0L
    val isCurrent: Boolean get() = isVerifiable && AssessmentCache.isCurrent(timestampMs)
}

object AssessmentCache {
    private const val PREFS_NAME = "sahara_prefs"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(base: String, uid: String): String = "${base}_$uid"

    fun isCurrent(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean =
        timestampMs > 0L && nowMs - timestampMs <= ASSESSMENT_VALIDITY_MS

    fun fromCloudMap(entry: Map<String, Any>?): CachedAssessment? {
        if (entry == null) return null
        val score = when (val raw = entry["score"]) {
            is Int -> raw
            is Long -> raw.toInt()
            is Number -> raw.toInt()
            else -> -1
        }
        val timestamp = when (val raw = entry["timestamp"]) {
            is Long -> raw
            is Int -> raw.toLong()
            is Number -> raw.toLong()
            else -> 0L
        }
        return if (score >= 0 && timestamp > 0L) {
            CachedAssessment(score, timestamp, everCompleted = true)
        } else {
            null
        }
    }

    fun load(context: Context, uid: String?): CachedAssessment? {
        val prefs = prefs(context)
        val cleanUid = uid.orEmpty()
        if (cleanUid.isNotBlank()) {
            val userScore = prefs.getInt(key(KEY_ASSESSMENT_SCORE, cleanUid), -1)
            val userTs = prefs.getLong(key(KEY_ASSESSMENT_TIMESTAMP, cleanUid), 0L)
            val userEver = prefs.getBoolean(key(KEY_ASSESSMENT_EVER_COMPLETED, cleanUid), false)
            if (userScore >= 0 || userEver) {
                return CachedAssessment(userScore, userTs, userEver || userScore >= 0)
            }
        }

        val legacyScore = prefs.getInt(KEY_ASSESSMENT_SCORE, -1)
        val legacyTs = prefs.getLong(KEY_ASSESSMENT_TIMESTAMP, 0L)
        val legacyEver = prefs.getBoolean(KEY_ASSESSMENT_EVER_COMPLETED, false)
        return if (legacyScore >= 0 || legacyEver) {
            CachedAssessment(legacyScore, legacyTs, legacyEver || legacyScore >= 0)
        } else {
            null
        }
    }

    fun save(context: Context, uid: String?, score: Int, timestampMs: Long) {
        val editor = prefs(context).edit()
            .putInt(KEY_ASSESSMENT_SCORE, score)
            .putLong(KEY_ASSESSMENT_TIMESTAMP, timestampMs)
            .putBoolean(KEY_ASSESSMENT_EVER_COMPLETED, true)

        val cleanUid = uid.orEmpty()
        if (cleanUid.isNotBlank()) {
            editor
                .putInt(key(KEY_ASSESSMENT_SCORE, cleanUid), score)
                .putLong(key(KEY_ASSESSMENT_TIMESTAMP, cleanUid), timestampMs)
                .putBoolean(key(KEY_ASSESSMENT_EVER_COMPLETED, cleanUid), true)
        }
        editor.apply()
    }

    fun clearActiveSession(context: Context) {
        prefs(context).edit()
            .remove(KEY_ASSESSMENT_SCORE)
            .remove(KEY_ASSESSMENT_TIMESTAMP)
            .remove(KEY_ASSESSMENT_EVER_COMPLETED)
            .apply()
        applyToGlobal(null)
    }

    fun applyToGlobal(snapshot: CachedAssessment?) {
        if (snapshot == null || snapshot.score < 0) {
            GlobalAppState.dast10Score = 0
            GlobalAppState.lastAssessmentTimestamp = 0L
            GlobalAppState.hasEverCompletedAssessment = snapshot?.everCompleted == true
            GlobalAppState.hasCompletedInitialAssessment = false
            return
        }

        GlobalAppState.dast10Score = snapshot.score
        GlobalAppState.lastAssessmentTimestamp = snapshot.timestampMs
        GlobalAppState.hasEverCompletedAssessment = true
        GlobalAppState.hasCompletedInitialAssessment = snapshot.isCurrent
    }

    fun restoreToGlobal(context: Context, uid: String?): CachedAssessment? =
        load(context, uid).also { applyToGlobal(it) }
}
