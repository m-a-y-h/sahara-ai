package pk.edu.ucp.saharaai.data.repository

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class ImportedSleepDay(
    val date: String,
    val hours: Float,
    val timeZoneId: String
)

enum class HealthConnectAvailability {
    AVAILABLE,
    UPDATE_REQUIRED,
    UNAVAILABLE
}

/**
 * Reads sleep duration already recorded by Health Connect or a connected
 * wearable app. It deliberately does not infer sleep from phone inactivity.
 */
object SleepHealthConnectRepository {
    val readSleepPermission: String =
        HealthPermission.getReadPermission(SleepSessionRecord::class)
    val requiredPermissions: Set<String> = setOf(readSleepPermission)

    fun availability(context: Context): HealthConnectAvailability =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.UPDATE_REQUIRED
            else -> HealthConnectAvailability.UNAVAILABLE
        }

    suspend fun hasReadPermission(context: Context): Boolean {
        val granted = HealthConnectClient.getOrCreate(context)
            .permissionController
            .getGrantedPermissions()
        return readSleepPermission in granted
    }

    suspend fun readWeek(
        context: Context,
        dates: List<LocalDate>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Result<List<ImportedSleepDay>> = runCatching {
        val client = HealthConnectClient.getOrCreate(context)
        dates.mapNotNull { wakeDate ->
            // A noon-to-noon window attaches an overnight sleep session to
            // its wake date without cutting it at midnight.
            val windowStart = wakeDate.minusDays(1).atTime(LocalTime.NOON)
            val windowEnd = wakeDate.atTime(LocalTime.NOON)
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(windowStart, windowEnd)
                )
            )
            val duration = response[SleepSessionRecord.SLEEP_DURATION_TOTAL]
                ?: return@mapNotNull null
            val minutes = duration.toMinutes()
            if (minutes <= 0L) return@mapNotNull null
            ImportedSleepDay(
                date = wakeDate.toString(),
                hours = minutes.toFloat() / 60f,
                timeZoneId = zoneId.id
            )
        }
    }
}
