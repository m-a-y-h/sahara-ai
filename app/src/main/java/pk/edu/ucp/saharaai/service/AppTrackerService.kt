package pk.edu.ucp.saharaai.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService


class AppTrackerService : AccessibilityService() {

    private val tag = "AppTrackerService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentPackage: String? = null
    private var currentStartTime: Long = 0L

    private val excludedPackages = setOf(
        "android",
        "com.android.systemui",
        "com.android.launcher",
        "com.android.launcher2",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.miui.home",
        "com.samsung.android.app.launcher",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.vivo.launcher",
        "pk.edu.ucp.saharaai"
    )

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(tag, "AppTrackerService created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100L
        }
        Log.d(tag, "AppTrackerService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        if (excludedPackages.any { packageName == it || packageName.startsWith("$it.") }) return
        if (packageName == currentPackage) return

        val timestamp = System.currentTimeMillis()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val previousPackage = currentPackage
        val previousStart = currentStartTime
        if (previousPackage != null && previousStart > 0L) {
            serviceScope.launch {
                val appName = resolveAppName(previousPackage)
                RealtimeDBService.logAppUsageEvent(
                    uid,
                    previousPackage,
                    appName,
                    previousStart,
                    timestamp
                )

                // Look up the global reputation for the package. Only BAD /
                // BRAINROT apps generate a row in the user's
                // `screen_time_log` — normal/unknown apps are silently
                // ignored, mirroring the spec.
                val reputation = pk.edu.ucp.saharaai.data.repository.AppReputationRepository
                    .lookup(previousPackage, appName)
                if (reputation != null && reputation.isBad()) {
                    val minutes = ((timestamp - previousStart) / 60_000.0)
                        .coerceAtLeast(0.0)
                    pk.edu.ucp.saharaai.data.repository.ScreenTimeLogRepository
                        .logBadAppUsage(
                            uid = uid,
                            packageHash = reputation.packageHash,
                            packageName = reputation.packageName ?: previousPackage,
                            appName = reputation.appName ?: appName,
                            category = reputation.category.wire,
                            severity = reputation.severity,
                            minutes = minutes,
                        )
                }
            }
        }

        currentPackage = packageName
        currentStartTime = timestamp
        Log.d(tag, "Tracking $packageName")
    }

    override fun onInterrupt() {
        Log.e(tag, "AppTrackerService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.d(tag, "AppTrackerService destroyed")
    }

    private fun resolveAppName(packageName: String): String = try {
        val info = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        packageManager.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }
}
