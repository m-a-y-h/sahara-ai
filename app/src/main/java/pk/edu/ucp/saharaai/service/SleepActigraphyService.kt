package pk.edu.ucp.saharaai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.activities.MainActivity
import pk.edu.ucp.saharaai.data.repository.AutomaticSleepLogRepository
import pk.edu.ucp.saharaai.data.repository.SLEEP_ACTIGRAPHY_NOTIFICATION_CHANNEL_ID
import pk.edu.ucp.saharaai.data.repository.SleepActigraphyEstimator
import pk.edu.ucp.saharaai.data.repository.SleepActigraphyRepository
import pk.edu.ucp.saharaai.data.repository.SleepMotionEpoch
import kotlin.math.abs
import kotlin.math.sqrt

class SleepActigraphyService : Service(), SensorEventListener {
    companion object {
        private const val NOTIFICATION_ID = 7102
        private const val ACTION_START = "pk.edu.ucp.saharaai.SLEEP_ACTIGRAPHY_START"
        private const val ACTION_STOP = "pk.edu.ucp.saharaai.SLEEP_ACTIGRAPHY_STOP"
        private const val ACTION_ACQUIRE_WAKE_LOCK = "pk.edu.ucp.saharaai.SLEEP_ACTIGRAPHY_ACQUIRE_WAKE_LOCK"
        private const val ACTION_RELEASE_WAKE_LOCK = "pk.edu.ucp.saharaai.SLEEP_ACTIGRAPHY_RELEASE_WAKE_LOCK"

        fun start(context: Context) {
            val intent = Intent(context, SleepActigraphyService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, SleepActigraphyService::class.java).setAction(ACTION_STOP)
            )
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var timerJob: Job? = null
    private var epochStartedAtMillis = 0L
    private var motionTotal = 0f
    private var sampleCount = 0
    private var previousMagnitude: Float? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                SleepActigraphyRepository.setEnabled(this, false)
                releaseWakeLock()
                stopCollection()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_ACQUIRE_WAKE_LOCK -> acquireWakeLock()
            ACTION_RELEASE_WAKE_LOCK -> releaseWakeLock()
        }
        if (!SleepActigraphyRepository.isEnabled(this) ||
            !SleepActigraphyRepository.hasRequiredPermissions(this)
        ) {
            stopSelf()
            return START_NOT_STICKY
        }

        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), serviceType)
        startCollection()
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val now = System.currentTimeMillis()
        flushCompletedEpoch(now)
        if (epochStartedAtMillis == 0L) {
            epochStartedAtMillis = alignedEpochStart(now)
        }
        val magnitude = sqrt(
            event.values[0] * event.values[0] +
                event.values[1] * event.values[1] +
                event.values[2] * event.values[2]
        )
        previousMagnitude?.let {
            motionTotal += abs(magnitude - it)
            sampleCount += 1
        }
        previousMagnitude = magnitude
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        flushEpoch()
        releaseWakeLock()
        stopCollection()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startCollection() {
        if (timerJob != null) return
        epochStartedAtMillis = alignedEpochStart(System.currentTimeMillis())
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(60_000L)
                if (!SleepActigraphyRepository.hasRequiredPermissions(this@SleepActigraphyService)) {
                    AutomaticSleepLogRepository.fillMissingCompletedNights(
                        context = this@SleepActigraphyService,
                        useActigraphy = false,
                        unavailableReason = "Automatic motion permission or notification access is unavailable"
                    )
                    SleepActigraphyRepository.setEnabled(this@SleepActigraphyService, false)
                    stopSelf()
                    return@launch
                }
                flushCompletedEpoch(System.currentTimeMillis())
                AutomaticSleepLogRepository.fillMissingCompletedNights(
                    context = this@SleepActigraphyService,
                    useActigraphy = true,
                    unavailableReason = "Phone-motion data was insufficient for an actigraphy estimate"
                )
            }
        }
    }

    private fun stopCollection() {
        sensorManager.unregisterListener(this)
        timerJob?.cancel()
        timerJob = null
    }

    private fun flushCompletedEpoch(now: Long) {
        if (epochStartedAtMillis > 0L &&
            now >= epochStartedAtMillis + SleepActigraphyEstimator.EPOCH_DURATION_MILLIS
        ) {
            flushEpoch()
            epochStartedAtMillis = alignedEpochStart(now)
        }
    }

    private fun flushEpoch() {
        if (epochStartedAtMillis > 0L && sampleCount > 0) {
            SleepActigraphyRepository.appendEpoch(
                this,
                SleepMotionEpoch(
                    startedAtMillis = epochStartedAtMillis,
                    motionScore = motionTotal / sampleCount,
                    sampleCount = sampleCount
                )
            )
        }
        motionTotal = 0f
        sampleCount = 0
        previousMagnitude = null
    }

    private fun alignedEpochStart(now: Long): Long =
        now - (now % SleepActigraphyEstimator.EPOCH_DURATION_MILLIS)

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SLEEP_ACTIGRAPHY_NOTIFICATION_CHANNEL_ID,
                "Automatic sleep estimates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown while Sahara collects motion summaries for automatic sleep estimates."
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopService = PendingIntent.getService(
            this,
            1,
            Intent(this, SleepActigraphyService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val hasWakeLock = wakeLock?.isHeld == true
        val wakeLockAction = PendingIntent.getService(
            this,
            2,
            Intent(this, SleepActigraphyService::class.java).setAction(
                if (hasWakeLock) ACTION_RELEASE_WAKE_LOCK else ACTION_ACQUIRE_WAKE_LOCK
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, SLEEP_ACTIGRAPHY_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Sahara automatic sleep estimate is active")
            .setContentText("Using phone motion summaries; tap Stop to turn this off.")
            .setContentIntent(openApp)
            .setSilent(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_lock_idle_lock,
                if (hasWakeLock) "Release WakeLock" else "Acquire WakeLock",
                wakeLockAction
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopService)
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SaharaAi:SleepActigraphyWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(8 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }
}
