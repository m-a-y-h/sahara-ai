package pk.edu.ucp.saharaai.data.repository

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate
import java.time.ZoneId

const val SLEEP_ACTIGRAPHY_NOTIFICATION_CHANNEL_ID = "sahara_sleep_actigraphy"

object SleepActigraphyRepository {
    private const val PREFS_NAME = "sahara_sleep_actigraphy"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_EPOCHS = "motion_epochs"
    private const val KEY_ENABLED_AT = "enabled_at"
    private const val KEEP_DAYS_MILLIS = 8L * 24L * 60L * 60L * 1000L
    private val gson = Gson()
    private val epochListType = object : TypeToken<List<SleepMotionEpoch>>() {}.type

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply {
                if (enabled) putLong(KEY_ENABLED_AT, System.currentTimeMillis())
            }
            .apply()
    }

    fun enabledAt(context: Context): Long = prefs(context).getLong(KEY_ENABLED_AT, 0L)

    fun requiredPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    fun hasRequiredPermissions(context: Context): Boolean =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        } && canShowDisclosureNotification(context)

    private fun canShowDisclosureNotification(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(SLEEP_ACTIGRAPHY_NOTIFICATION_CHANNEL_ID)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    fun appendEpoch(context: Context, epoch: SleepMotionEpoch) {
        val oldest = System.currentTimeMillis() - KEEP_DAYS_MILLIS
        val saved = readEpochs(context)
            .filter { it.startedAtMillis >= oldest }
            .filterNot {
                it.startedAtMillis / SleepActigraphyEstimator.EPOCH_DURATION_MILLIS ==
                    epoch.startedAtMillis / SleepActigraphyEstimator.EPOCH_DURATION_MILLIS
            }
            .plus(epoch)
            .sortedBy { it.startedAtMillis }
        prefs(context).edit().putString(KEY_EPOCHS, gson.toJson(saved)).apply()
    }

    fun estimateNight(
        context: Context,
        wakeDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): SleepActigraphyEstimate? =
        SleepActigraphyEstimator.estimate(readEpochs(context), wakeDate, zoneId)

    private fun readEpochs(context: Context): List<SleepMotionEpoch> {
        val raw = prefs(context).getString(KEY_EPOCHS, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<SleepMotionEpoch>>(raw, epochListType) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
