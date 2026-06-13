package pk.edu.ucp.saharaai.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await
import pk.edu.ucp.saharaai.BuildConfig
import pk.edu.ucp.saharaai.data.remote.BiometricAuthClient
import pk.edu.ucp.saharaai.data.remote.BiometricAuthHttpException
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import pk.edu.ucp.saharaai.utils.BiometricSessionVault

data class BiometricSessionRestore(
    val email: String,
    val displayName: String,
)

class BiometricSessionRepository {
    suspend fun enrollCurrentUser(context: Context): Result<Unit> = runCatching {
        val appContext = context.applicationContext
        val user = Firebase.auth.currentUser
            ?: error("Sign in first to enable biometric login.")
        check(!user.isAnonymous) { "Sign in first to enable biometric login." }

        val idToken = user.getIdToken(false).await().token.orEmpty()
        check(idToken.isNotBlank()) { "Could not verify your account session." }

        val session = BiometricSessionVault.createSession(
            emailHint = user.email.orEmpty(),
            nameHint = user.displayName.orEmpty(),
        )
        BiometricAuthClient.enroll(
            endpoint = BuildConfig.SAHARA_BIOMETRIC_ENROLL_URL,
            idToken = idToken,
            deviceId = session.deviceId,
            deviceSecret = session.deviceSecret,
            emailHint = session.emailHint,
            displayNameHint = session.nameHint,
        ).getOrThrow()

        BiometricSessionVault.save(appContext, session)
        setLocalEnabled(appContext, true)
        runCatching { RealtimeDBService.setBiometricEnabled(user.uid, true).getOrThrow() }
            .onFailure { Log.w(TAG, "Could not set remote biometric flag", it) }
    }

    suspend fun disableCurrentDevice(context: Context): Result<Unit> = runCatching {
        val appContext = context.applicationContext
        val session = BiometricSessionVault.load(appContext)
        val user = Firebase.auth.currentUser

        if (session != null && user != null && !user.isAnonymous) {
            runCatching {
                val idToken = user.getIdToken(false).await().token.orEmpty()
                if (idToken.isNotBlank() && BuildConfig.SAHARA_BIOMETRIC_DISABLE_URL.isNotBlank()) {
                    BiometricAuthClient.disable(
                        endpoint = BuildConfig.SAHARA_BIOMETRIC_DISABLE_URL,
                        idToken = idToken,
                        deviceId = session.deviceId,
                    ).getOrThrow()
                }
            }.onFailure { Log.w(TAG, "Could not disable backend biometric device", it) }

            runCatching { RealtimeDBService.setBiometricEnabled(user.uid, false).getOrThrow() }
                .onFailure { Log.w(TAG, "Could not clear remote biometric flag", it) }
        }

        BiometricSessionVault.clear(appContext)
        setLocalEnabled(appContext, false)
    }

    suspend fun signInWithStoredSession(context: Context): Result<BiometricSessionRestore> {
        val appContext = context.applicationContext
        val session = BiometricSessionVault.load(appContext)
            ?: return Result.failure(IllegalStateException("Fingerprint login is not set up on this device."))

        return runCatching {
            val response = BiometricAuthClient.login(
                endpoint = BuildConfig.SAHARA_BIOMETRIC_LOGIN_URL,
                deviceId = session.deviceId,
                deviceSecret = session.deviceSecret,
            ).getOrThrow()

            val authResult = Firebase.auth.signInWithCustomToken(response.customToken).await()
            val user = authResult.user ?: Firebase.auth.currentUser
                ?: error("Firebase did not return a signed-in user.")
            setLocalEnabled(appContext, true)
            runCatching { RealtimeDBService.setBiometricEnabled(user.uid, true) }

            BiometricSessionRestore(
                email = user.email.orEmpty().ifBlank { response.email }.ifBlank { session.emailHint },
                displayName = user.displayName.orEmpty().ifBlank { response.displayName }.ifBlank { session.nameHint },
            )
        }.onFailure { error ->
            if ((error as? BiometricAuthHttpException)?.isTerminalAuthFailure == true) {
                BiometricSessionVault.clear(appContext)
                setLocalEnabled(appContext, false)
            }
        }
    }

    private fun setLocalEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
            .apply()
    }

    companion object {
        private const val TAG = "BiometricSessionRepo"
        private const val PREFS_NAME = "sahara_prefs"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    }
}
