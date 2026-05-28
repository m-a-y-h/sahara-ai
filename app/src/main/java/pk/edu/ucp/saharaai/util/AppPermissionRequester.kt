package pk.edu.ucp.saharaai.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private const val PERMISSION_PREFS = "sahara_permission_state"

data class PermissionCopy(
    val deniedEn: String,
    val deniedUr: String,
    val settingsEn: String,
    val settingsUr: String,
    val grantedEn: String = "",
    val grantedUr: String = "",
)

class AppPermissionRequester internal constructor(
    val hasPermission: () -> Boolean,
    val request: () -> Unit,
    val openSettings: () -> Unit,
)

fun Context.showLocalizedToast(
    isEnglish: Boolean,
    english: String,
    romanUrdu: String,
    length: Int = Toast.LENGTH_SHORT,
) {
    Toast.makeText(this, if (isEnglish) english else romanUrdu, length).show()
}

fun showAssessmentRequiredToast(context: Context, isEnglish: Boolean) {
    context.showLocalizedToast(
        isEnglish = isEnglish,
        english = "Please complete your assessment.",
        romanUrdu = "Please apna assessment complete karein.",
    )
}

@Composable
fun rememberAppPermissionRequester(
    permission: String,
    isEnglish: Boolean,
    copy: PermissionCopy,
    onGranted: () -> Unit,
    onDenied: () -> Unit = {},
): AppPermissionRequester {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val prefs = remember(context) {
        context.getSharedPreferences(PERMISSION_PREFS, Context.MODE_PRIVATE)
    }
    val latestOnGranted = rememberUpdatedState(onGranted)
    val latestOnDenied = rememberUpdatedState(onDenied)
    val latestCopy = rememberUpdatedState(copy)

    fun hasPermission(): Boolean = hasRuntimePermission(context, permission)
    fun resetDenials() {
        prefs.edit().remove(denialKey(permission)).apply()
    }
    fun shouldOpenSettings(): Boolean {
        val denials = prefs.getInt(denialKey(permission), 0)
        if (denials < 2) return false
        return activity == null || !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
    fun openSettingsWithToast() {
        context.showLocalizedToast(
            isEnglish,
            latestCopy.value.settingsEn,
            latestCopy.value.settingsUr,
            Toast.LENGTH_LONG,
        )
        context.openAppSettings()
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted || hasPermission()) {
            resetDenials()
            if (latestCopy.value.grantedEn.isNotBlank() || latestCopy.value.grantedUr.isNotBlank()) {
                context.showLocalizedToast(
                    isEnglish,
                    latestCopy.value.grantedEn,
                    latestCopy.value.grantedUr,
                )
            }
            latestOnGranted.value()
        } else {
            val systemWillNotAskAgain = activity == null ||
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            val previous = prefs.getInt(denialKey(permission), 0)
            val updated = if (systemWillNotAskAgain) maxOf(previous + 1, 2) else previous + 1
            prefs.edit().putInt(denialKey(permission), updated).apply()
            latestOnDenied.value()
            if (systemWillNotAskAgain && updated >= 2) {
                openSettingsWithToast()
            } else {
                context.showLocalizedToast(
                    isEnglish,
                    latestCopy.value.deniedEn,
                    latestCopy.value.deniedUr,
                    Toast.LENGTH_LONG,
                )
            }
        }
    }

    return AppPermissionRequester(
        hasPermission = ::hasPermission,
        request = {
            when {
                hasPermission() -> {
                    resetDenials()
                    latestOnGranted.value()
                }
                shouldOpenSettings() -> openSettingsWithToast()
                else -> launcher.launch(permission)
            }
        },
        openSettings = ::openSettingsWithToast,
    )
}

@Composable
fun rememberAppPermissionsRequester(
    permissions: Array<String>,
    isEnglish: Boolean,
    copy: PermissionCopy,
    onGranted: () -> Unit,
    onDenied: () -> Unit = {},
): AppPermissionRequester {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val prefs = remember(context) {
        context.getSharedPreferences(PERMISSION_PREFS, Context.MODE_PRIVATE)
    }
    val latestOnGranted = rememberUpdatedState(onGranted)
    val latestOnDenied = rememberUpdatedState(onDenied)
    val latestCopy = rememberUpdatedState(copy)
    val runtimePermissions = remember(permissions.toList()) {
        permissions.filter { isRuntimePermissionRequired(it) }.distinct().toTypedArray()
    }

    fun missingPermissions(): Array<String> =
        runtimePermissions.filterNot { hasRuntimePermission(context, it) }.toTypedArray()

    fun allGranted(): Boolean = missingPermissions().isEmpty()

    fun resetDenials() {
        prefs.edit().apply {
            runtimePermissions.forEach { remove(denialKey(it)) }
        }.apply()
    }

    fun permissionShouldOpenSettings(permission: String): Boolean {
        val denials = prefs.getInt(denialKey(permission), 0)
        if (denials < 2) return false
        return activity == null || !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    fun openSettingsWithToast() {
        context.showLocalizedToast(
            isEnglish,
            latestCopy.value.settingsEn,
            latestCopy.value.settingsUr,
            Toast.LENGTH_LONG,
        )
        context.openAppSettings()
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val denied = runtimePermissions.filter { permission ->
            grants[permission] != true && !hasRuntimePermission(context, permission)
        }
        if (denied.isEmpty()) {
            resetDenials()
            if (latestCopy.value.grantedEn.isNotBlank() || latestCopy.value.grantedUr.isNotBlank()) {
                context.showLocalizedToast(
                    isEnglish,
                    latestCopy.value.grantedEn,
                    latestCopy.value.grantedUr,
                )
            }
            latestOnGranted.value()
        } else {
            val edit = prefs.edit()
            val blocked = denied.any { permission ->
                val systemWillNotAskAgain = activity == null ||
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
                val previous = prefs.getInt(denialKey(permission), 0)
                val updated = if (systemWillNotAskAgain) maxOf(previous + 1, 2) else previous + 1
                edit.putInt(denialKey(permission), updated)
                systemWillNotAskAgain && updated >= 2
            }
            edit.apply()
            latestOnDenied.value()
            if (blocked) {
                openSettingsWithToast()
            } else {
                context.showLocalizedToast(
                    isEnglish,
                    latestCopy.value.deniedEn,
                    latestCopy.value.deniedUr,
                    Toast.LENGTH_LONG,
                )
            }
        }
    }

    return AppPermissionRequester(
        hasPermission = ::allGranted,
        request = {
            val missing = missingPermissions()
            when {
                missing.isEmpty() -> {
                    resetDenials()
                    latestOnGranted.value()
                }
                missing.any(::permissionShouldOpenSettings) -> openSettingsWithToast()
                else -> launcher.launch(missing)
            }
        },
        openSettings = ::openSettingsWithToast,
    )
}

private fun hasRuntimePermission(context: Context, permission: String): Boolean {
    if (!isRuntimePermissionRequired(permission)) return true
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun isRuntimePermissionRequired(permission: String): Boolean = when (permission) {
    Manifest.permission.POST_NOTIFICATIONS -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    Manifest.permission.ACTIVITY_RECOGNITION -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    else -> true
}

private fun denialKey(permission: String): String =
    "denials_${permission.replace('.', '_')}"

private fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
