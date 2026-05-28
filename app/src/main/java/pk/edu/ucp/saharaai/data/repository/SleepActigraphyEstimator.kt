package pk.edu.ucp.saharaai.data.repository

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class SleepMotionEpoch(
    val startedAtMillis: Long = 0L,
    val motionScore: Float = 0f,
    val sampleCount: Int = 0
)

data class SleepActigraphyEstimate(
    val date: String,
    val bedtime: String,
    val waketime: String,
    val hours: Float,
    val confidence: Float,
    val observedEpochs: Int
)

/**
 * Conservative phone-motion estimate. A stationary phone alone is not
 * evidence of sleep: a candidate must have sampled coverage and nearby
 * movement indicating that the phone was handled before or after the block.
 */
object SleepActigraphyEstimator {
    const val EPOCH_MINUTES = 5L
    const val EPOCH_DURATION_MILLIS = EPOCH_MINUTES * 60L * 1000L
    private const val MIN_OBSERVED_EPOCHS = 72
    private const val MAX_EXPECTED_EPOCHS = 216f
    private const val STILL_MOTION_SCORE = 0.12f
    private const val ACTIVE_MOTION_SCORE = 0.20f
    private const val MIN_SLEEP_HOURS = 3f
    private const val MAX_SLEEP_HOURS = 14f
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun estimate(
        epochs: List<SleepMotionEpoch>,
        wakeDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): SleepActigraphyEstimate? {
        val windowStart = wakeDate.minusDays(1).atTime(LocalTime.of(18, 0))
            .atZone(zoneId).toInstant().toEpochMilli()
        val windowEnd = wakeDate.atTime(LocalTime.NOON)
            .atZone(zoneId).toInstant().toEpochMilli()
        val observed = epochs.asSequence()
            .filter { it.sampleCount > 0 && it.startedAtMillis in windowStart until windowEnd }
            .sortedBy { it.startedAtMillis }
            .distinctBy { it.startedAtMillis / EPOCH_DURATION_MILLIS }
            .toList()
        if (observed.size < MIN_OBSERVED_EPOCHS) return null

        val runs = mutableListOf<List<SleepMotionEpoch>>()
        var current = mutableListOf<SleepMotionEpoch>()
        var interruptions = 0
        var previousStart = 0L

        fun finishRun() {
            if (current.isNotEmpty()) runs += current.toList()
            current = mutableListOf()
            interruptions = 0
        }

        observed.forEachIndexed { index, epoch ->
            if (previousStart > 0L &&
                epoch.startedAtMillis - previousStart > EPOCH_DURATION_MILLIS * 2
            ) {
                finishRun()
            }
            if (epoch.motionScore <= STILL_MOTION_SCORE) {
                current += epoch
            } else if (
                current.isNotEmpty() &&
                interruptions == 0 &&
                observed.getOrNull(index + 1)?.motionScore?.let { it <= STILL_MOTION_SCORE } == true
            ) {
                current += epoch
                interruptions += 1
            } else {
                finishRun()
            }
            previousStart = epoch.startedAtMillis
        }
        finishRun()

        val candidate = runs
            .map { run ->
                val millis = run.last().startedAtMillis + EPOCH_DURATION_MILLIS -
                    run.first().startedAtMillis
                run to millis.toFloat() / (60f * 60f * 1000f)
            }
            .filter { (_, hours) -> hours in MIN_SLEEP_HOURS..MAX_SLEEP_HOURS }
            .maxByOrNull { (_, hours) -> hours }
            ?: return null

        val run = candidate.first
        val sleepHours = candidate.second
        val start = run.first().startedAtMillis
        val end = run.last().startedAtMillis + EPOCH_DURATION_MILLIS
        val boundaryWindow = 2L * 60L * 60L * 1000L
        val movedBefore = observed.any {
            it.startedAtMillis in (start - boundaryWindow) until start &&
                it.motionScore >= ACTIVE_MOTION_SCORE
        }
        val movedAfter = observed.any {
            it.startedAtMillis in end until (end + boundaryWindow) &&
                it.motionScore >= ACTIVE_MOTION_SCORE
        }
        if (!movedBefore && !movedAfter) return null

        val coverage = (observed.size / MAX_EXPECTED_EPOCHS).coerceIn(0f, 1f)
        val boundaryConfidence = if (movedBefore && movedAfter) 0.25f else 0.10f
        val confidence = (0.45f + 0.35f * coverage + boundaryConfidence).coerceAtMost(1f)
        val bedtime = Instant.ofEpochMilli(start).atZone(zoneId).toLocalTime().format(timeFormatter)
        val waketime = Instant.ofEpochMilli(end).atZone(zoneId).toLocalTime().format(timeFormatter)
        return SleepActigraphyEstimate(
            date = wakeDate.toString(),
            bedtime = bedtime,
            waketime = waketime,
            hours = sleepHours,
            confidence = confidence,
            observedEpochs = observed.size
        )
    }
}
