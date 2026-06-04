package pk.edu.ucp.saharaai.util

import android.content.Context
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

    private const val FILE_NAME = "sahara_bio_vault"
    private const val K_EMAIL    = "v1_email"
    private const val K_PASSWORD = "v1_password"
    private const val K_ENABLED  = "v1_enabled"

    private fun prefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    /** Persist after a successful regular sign-in. The vault is now "armed". */
    fun save(context: Context, email: String, password: String) {
        if (email.isBlank() || password.isBlank()) return
        prefs(context).edit()
            .putString(K_EMAIL, email)
            .putString(K_PASSWORD, password)
            .putBoolean(K_ENABLED, true)
            .apply()
    }

    /** Returns the saved (email, password) pair if the vault has been armed,
     *  or null if the user has never enabled biometric login. */
    fun load(context: Context): Pair<String, String>? {
        val p = prefs(context)
        if (!p.getBoolean(K_ENABLED, false)) return null
        val email = p.getString(K_EMAIL, "").orEmpty()
        val pwd   = p.getString(K_PASSWORD, "").orEmpty()
        if (email.isBlank() || pwd.isBlank()) return null
        return email to pwd
    }

    /** Cheap "is biometric login set up?" check without decrypting anything. */
    fun isArmed(context: Context): Boolean =
        prefs(context).getBoolean(K_ENABLED, false)

    /** Wipe on sign-out. */
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
