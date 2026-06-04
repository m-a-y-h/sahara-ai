package pk.edu.ucp.saharaai.util

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * On-device biometric login vault.
 *
 * Sits behind the system biometric prompt: when the user has fingerprint /
 * face unlock enrolled, we save their (email, password) AES-GCM-encrypted with
 * an Android Keystore-backed master key. Next time they hit "Login with
 * fingerprint" we decrypt those values back out and hand them to
 * Firebase.auth.signInWithEmailAndPassword. No Firebase Cloud Function, no
 * Blaze plan, no WebAuthn extension — the whole thing is local crypto.
 *
 * Threat model: a thief with physical access to an unlocked phone *and* the
 * user's biometric can sign back in. Same security model as the device lock
 * itself; that's the trade-off the user opted into when they enabled biometric
 * login. Encrypted at rest with the Keystore master key so a stolen-and-rooted
 * device can't trivially extract the password from disk.
 *
 * IMPORTANT: every successful manual sign-in updates the vault, and every
 * sign-out clears it. The biometric login flow only succeeds if BOTH the
 * device biometric authenticates AND a saved credential exists.
 */
object BiometricCredentialVault {

    private const val TAG = "BiometricVault"
    private const val FILE_NAME = "sahara_bio_vault"
    private const val K_EMAIL    = "v1_email"
    private const val K_PASSWORD = "v1_password"
    private const val K_ENABLED  = "v1_enabled"

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

    /** Persist after a successful regular sign-in. The vault is now "armed". */
    fun save(context: Context, email: String, password: String) {
        if (email.isBlank() || password.isBlank()) return
        val p = prefs(context) ?: return
        runCatching {
            p.edit()
                .putString(K_EMAIL, email)
                .putString(K_PASSWORD, password)
                .putBoolean(K_ENABLED, true)
                .apply()
        }.onFailure { Log.w(TAG, "save() failed", it) }
    }

    /** Returns the saved (email, password) pair if the vault has been armed,
     *  or null if the user has never enabled biometric login. */
    fun load(context: Context): Pair<String, String>? {
        val p = prefs(context) ?: return null
        return runCatching {
            if (!p.getBoolean(K_ENABLED, false)) return null
            val email = p.getString(K_EMAIL, "").orEmpty()
            val pwd   = p.getString(K_PASSWORD, "").orEmpty()
            if (email.isBlank() || pwd.isBlank()) null else email to pwd
        }.onFailure { Log.w(TAG, "load() failed", it) }.getOrNull()
    }

    /** Cheap "is biometric login set up?" check without decrypting anything. */
    fun isArmed(context: Context): Boolean {
        val p = prefs(context) ?: return false
        return runCatching { p.getBoolean(K_ENABLED, false) }
            .onFailure { Log.w(TAG, "isArmed() failed", it) }
            .getOrDefault(false)
    }

    /** Wipe on sign-out. */
    fun clear(context: Context) {
        val p = prefs(context) ?: return
        runCatching { p.edit().clear().apply() }
            .onFailure { Log.w(TAG, "clear() failed", it) }
    }
}
