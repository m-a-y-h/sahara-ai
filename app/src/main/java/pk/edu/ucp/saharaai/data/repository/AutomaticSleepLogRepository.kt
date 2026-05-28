package pk.edu.ucp.saharaai.data.repository

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import pk.edu.ucp.saharaai.data.remote.FirestoreService
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

const val SLEEP_SOURCE_ACTIGRAPHY = "actigraphy"
const val SLEEP_SOURCE_SIX_MONTH_AVERAGE = "six_month_average"
const val SLEEP_SOURCE_DEFAULT = "default"

data class AutomaticSleepCompletion(
    val generated: Int,
    val actigraphyEstimates: Int,
    val fallbackEntries: Int
)

/**
 * Produces at most one automatic entry per completed night in the current
 * week. Existing manual/imported entries always win.
 */
object AutomaticSleepLogRepository {
    suspend fun fillMissingCompletedNights(
        context: Context,
        useActigraphy: Boolean,
        unavailableReason: String
    ): Result<AutomaticSleepCompletion> = runCatching {
        val uid = Firebase.auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) return@runCatching AutomaticSleepCompletion(0, 0, 0)
        val zoneId = ZoneId.systemDefault()
        val now = java.time.ZonedDateTime.now(zoneId)
        val today = now.toLocalDate()
        val lastCompletedWakeDate =
            if (now.toLocalTime() >= LocalTime.NOON) today else today.minusDays(1)
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (lastCompletedWakeDate.isBefore(monday)) {
            return@runCatching AutomaticSleepCompletion(0, 0, 0)
        }

        var generated = 0
        var estimated = 0
        var fallback = 0
        var wakeDate = monday
        while (!wakeDate.isAfter(lastCompletedWakeDate)) {
            val date = wakeDate.toString()
            if (RealtimeDBService.loadSleepLog(uid, date) == null) {
                val actigraphy = if (useActigraphy) {
                    SleepActigraphyRepository.estimateNight(context, wakeDate, zoneId)
                } else {
                    null
                }
                if (actigraphy != null) {
                    writeAutomaticLog(
                        uid = uid,
                        date = date,
                        bedtime = actigraphy.bedtime,
                        waketime = actigraphy.waketime,
                        hours = actigraphy.hours,
                        source = SLEEP_SOURCE_ACTIGRAPHY,
                        timeZoneId = zoneId.id,
                        confidence = actigraphy.confidence,
                        sourceReason = "Phone-motion estimate from ${actigraphy.observedEpochs} five-minute epochs."
                    )
                    estimated += 1
                } else {
                    val pastAverage = RealtimeDBService.loadMeasuredSleepAverage(uid, date)
                    val hours = pastAverage ?: 6f
                    val source = if (pastAverage != null) {
                        SLEEP_SOURCE_SIX_MONTH_AVERAGE
                    } else {
                        SLEEP_SOURCE_DEFAULT
                    }
                    val reason = if (pastAverage != null) {
                        "$unavailableReason; used prior measured sleep average."
                    } else {
                        "$unavailableReason; no prior measured sleep, defaulted to 6 hours."
                    }
                    writeAutomaticLog(
                        uid = uid,
                        date = date,
                        bedtime = "",
                        waketime = "",
                        hours = hours,
                        source = source,
                        timeZoneId = zoneId.id,
                        confidence = 0f,
                        sourceReason = reason
                    )
                    fallback += 1
                }
                generated += 1
            }
            wakeDate = wakeDate.plusDays(1)
        }
        AutomaticSleepCompletion(generated, estimated, fallback)
    }

    private suspend fun writeAutomaticLog(
        uid: String,
        date: String,
        bedtime: String,
        waketime: String,
        hours: Float,
        source: String,
        timeZoneId: String,
        confidence: Float,
        sourceReason: String
    ) {
        FirestoreService.saveSleepLog(
            uid = uid,
            date = date,
            bedtime = bedtime,
            waketime = waketime,
            hours = hours,
            source = source,
            timeZoneId = timeZoneId,
            automatic = true,
            confidence = confidence,
            sourceReason = sourceReason
        ).getOrThrow()
        RealtimeDBService.saveSleepLog(
            uid = uid,
            date = date,
            bedtime = bedtime,
            waketime = waketime,
            hours = hours,
            source = source,
            timeZoneId = timeZoneId,
            automatic = true,
            confidence = confidence,
            sourceReason = sourceReason
        ).getOrThrow()
    }
}
