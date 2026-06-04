package pk.edu.ucp.saharaai.data.repository

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService

enum class FirebaseAuthFailure {
    EMAIL_ALREADY_IN_USE,
    INVALID_CREDENTIALS,
    EMAIL_PASSWORD_DISABLED,
    CONFIGURATION_MISSING,
    WEAK_PASSWORD,
    NETWORK,
    TOO_MANY_REQUESTS,
    USER_DISABLED,
    UNKNOWN
}

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    fun signIn(email: String, password: String, onResult: (FirebaseAuthFailure?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Backfill the email->has_password registry that
                    // GoogleCredentialAuth consults to detect collisions
                    // (Firebase's Email Enumeration Protection blocks
                    // fetchSignInMethodsForEmail). Best-effort fire-and-forget.
                    val uid = auth.currentUser?.uid.orEmpty()
                    if (uid.isNotBlank()) recordPasswordIndexAsync(uid, email)
                }
                onResult(if (task.isSuccessful) null else mapFailure(task.exception))
            }
    }

    fun register(email: String, password: String, onResult: (FirebaseAuthFailure?) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid.orEmpty()
                    if (uid.isNotBlank()) recordPasswordIndexAsync(uid, email)
                }
                onResult(if (task.isSuccessful) null else mapFailure(task.exception))
            }
    }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    private fun recordPasswordIndexAsync(uid: String, email: String) {
        GlobalScope.launch {
            runCatching { RealtimeDBService.recordEmailHasPassword(uid, email) }
        }
    }

    fun sendPasswordResetLink(email: String, onResult: (FirebaseAuthFailure?) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                onResult(if (task.isSuccessful) null else mapFailure(task.exception))
            }
    }

    fun currentUserEmail(): String? = auth.currentUser?.email

    fun hasAuthenticatedSession(): Boolean = auth.currentUser != null

    /** True if the signed-in Firebase user has an email/password credential
     *  attached. Google-only users return false until they link a password
     *  via the biometric-enable flow. */
    fun hasPasswordProvider(): Boolean =
        auth.currentUser?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true

    /** Adds an email/password credential to the currently signed-in user
     *  (e.g. a Google account that has no password yet). After this returns
     *  successfully the same Firebase user can sign in with either provider.
     *  Maps Firebase failures into the existing [FirebaseAuthFailure] enum so
     *  callers don't need to deal with raw exception types. */
    suspend fun linkEmailPassword(email: String, password: String): FirebaseAuthFailure? {
        val user = auth.currentUser ?: return FirebaseAuthFailure.UNKNOWN
        return try {
            val cred = EmailAuthProvider.getCredential(email, password)
            user.linkWithCredential(cred).await()
            // The user just added a password — record it in our registry so a
            // future Google sign-in correctly hits the collision branch.
            runCatching { RealtimeDBService.recordEmailHasPassword(user.uid, email) }
            null
        } catch (e: Exception) {
            mapFailure(e)
        }
    }

    /** Re-verifies the user's password without doing a full sign-out/in. Used
     *  when re-arming the biometric vault for a user who already has a
     *  password provider but cleared the vault earlier. */
    suspend fun reauthenticateWithPassword(email: String, password: String): FirebaseAuthFailure? {
        val user = auth.currentUser ?: return FirebaseAuthFailure.UNKNOWN
        return try {
            val cred = EmailAuthProvider.getCredential(email, password)
            user.reauthenticate(cred).await()
            null
        } catch (e: Exception) {
            mapFailure(e)
        }
    }

    private fun mapFailure(error: Exception?): FirebaseAuthFailure {
        val message = error?.message.orEmpty()
        if (message.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true)) {
            return FirebaseAuthFailure.CONFIGURATION_MISSING
        }
        if (error is FirebaseNetworkException) {
            return FirebaseAuthFailure.NETWORK
        }

        return when ((error as? FirebaseAuthException)?.errorCode) {
            "ERROR_EMAIL_ALREADY_IN_USE",
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> FirebaseAuthFailure.EMAIL_ALREADY_IN_USE
            "ERROR_INVALID_CREDENTIAL",
            "ERROR_INVALID_EMAIL",
            "ERROR_USER_NOT_FOUND",
            "ERROR_WRONG_PASSWORD" -> FirebaseAuthFailure.INVALID_CREDENTIALS
            "ERROR_OPERATION_NOT_ALLOWED" -> FirebaseAuthFailure.EMAIL_PASSWORD_DISABLED
            "ERROR_WEAK_PASSWORD" -> FirebaseAuthFailure.WEAK_PASSWORD
            "ERROR_TOO_MANY_REQUESTS" -> FirebaseAuthFailure.TOO_MANY_REQUESTS
            "ERROR_USER_DISABLED" -> FirebaseAuthFailure.USER_DISABLED
            "ERROR_NETWORK_REQUEST_FAILED" -> FirebaseAuthFailure.NETWORK
            else -> FirebaseAuthFailure.UNKNOWN
        }
    }
}
