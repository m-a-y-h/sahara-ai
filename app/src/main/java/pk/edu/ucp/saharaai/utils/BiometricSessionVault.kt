package pk.edu.ucp.saharaai.utils

import android.content.Context
import android.util.Log
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import java.util.UUID

/**
 * On-device biometric session credential.
 *
 * The vault stores only a random device id + secret behind Android
 * Keystore-backed encrypted prefs. After the system biometric prompt succeeds,
 * the app sends that device credential to the backend, which validates it and
 * returns a Firebase custom token for the original UID. User passwords and
 * Firebase refresh tokens are never stored here.
 */
object BiometricSessionVault {

    private const val TAG = "BiometricSessionVault"
    private const val FILE_NAME = "sahara_bio_session_vault"
    private const val K_DEVICE_ID = "v2_device_id"
    private const val K_DEVICE_SECRET = "v2_device_secret"
    private const val K_EMAIL_HINT = "v2_email_hint"
    private const val K_NAME_HINT = "v2_name_hint"
    private const val K_ENABLED = "v2_enabled"

    data class StoredSession(
        val deviceId: String,
        val deviceSecret: String,
        val emailHint: String = "",
        val nameHint: String = "",
    )

    /**
     * Builds the encrypted prefs handle. Wrapped in runCatching because
     * EncryptedSharedPreferences.create() can throw on a corrupted /
     * invalidated keystore — e.g. after a screen-lock policy change, an OEM
     * keystore reset, or just an SDK regression. A throw here used to kill
     * LoginScreen during composition (the `showBiometric` remember call ran
     * it on every screen entry). Returning null lets callers degrade to
     * "vault not armed" instead of crashing the app.
     *
     * If the underlying file looks broken we delete it and retry once — the
     * vault stays usable on subsequent launches without the user having to
     * reinstall.
     */
    private fun prefs(context: Context): android.content.SharedPreferences? {
        val build = {
            EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
        return runCatching { build() }
            .recoverCatching { firstFail ->
                Log.w(TAG, "EncryptedSharedPreferences.create failed; resetting vault", firstFail)
                runCatching { context.deleteSharedPreferences(FILE_NAME) }
                build()
            }
            .onFailure { Log.w(TAG, "Biometric vault unavailable", it) }
            .getOrNull()
    }

    fun createSession(emailHint: String = "", nameHint: String = ""): StoredSession =
        StoredSession(
            deviceId = UUID.randomUUID().toString(),
            deviceSecret = randomSecret(),
            emailHint = emailHint,
            nameHint = nameHint,
        )

    fun save(context: Context, session: StoredSession) {
        if (session.deviceId.isBlank() || session.deviceSecret.isBlank()) return
        val p = prefs(context) ?: return
        runCatching {
            p.edit()
                .putString(K_DEVICE_ID, session.deviceId)
                .putString(K_DEVICE_SECRET, session.deviceSecret)
                .putString(K_EMAIL_HINT, session.emailHint)
                .putString(K_NAME_HINT, session.nameHint)
                .putBoolean(K_ENABLED, true)
                .apply()
        }.onFailure { Log.w(TAG, "save() failed", it) }
    }

    fun load(context: Context): StoredSession? {
        val p = prefs(context) ?: return null
        return runCatching {
            if (!p.getBoolean(K_ENABLED, false)) return null
            val deviceId = p.getString(K_DEVICE_ID, "").orEmpty()
            val deviceSecret = p.getString(K_DEVICE_SECRET, "").orEmpty()
            if (deviceId.isBlank() || deviceSecret.isBlank()) {
                null
            } else {
                StoredSession(
                    deviceId = deviceId,
                    deviceSecret = deviceSecret,
                    emailHint = p.getString(K_EMAIL_HINT, "").orEmpty(),
                    nameHint = p.getString(K_NAME_HINT, "").orEmpty(),
                )
            }
        }.onFailure { Log.w(TAG, "load() failed", it) }.getOrNull()
    }

    fun isArmed(context: Context): Boolean {
        val p = prefs(context) ?: return false
        return runCatching { p.getBoolean(K_ENABLED, false) }
            .onFailure { Log.w(TAG, "isArmed() failed", it) }
            .getOrDefault(false)
    }

    fun clear(context: Context) {
        val p = prefs(context) ?: return
        runCatching { p.edit().clear().apply() }
            .onFailure { Log.w(TAG, "clear() failed", it) }
    }

    private fun randomSecret(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
