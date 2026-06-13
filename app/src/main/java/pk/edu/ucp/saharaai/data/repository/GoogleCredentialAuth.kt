package pk.edu.ucp.saharaai.data.repository

import android.content.Context
import android.util.Log
import androidx.credentials.CustomCredential
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import pk.edu.ucp.saharaai.R
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService

/**
 * Possible outcomes of a Google sign-in attempt.
 *
 * [NeedsPasswordLink] is only a fallback for projects where Firebase refuses
 * automatic one-account-per-email linking and throws a collision. The normal
 * path signs in with Google directly so Firebase can link Google to the
 * existing email/password UID once and keep it linked afterwards.
 */
sealed class GoogleSignInOutcome {
    /** Google sign-in completed and we're now authenticated. */
    data class Signed(val authResult: AuthResult) : GoogleSignInOutcome()

    /** Firebase refused automatic provider linking. The caller can ask for the
     *  password once, then call [GoogleCredentialAuth.completeLinkWithPassword]. */
    data class NeedsPasswordLink(
        val email: String,
        val pendingGoogleCredential: AuthCredential,
    ) : GoogleSignInOutcome()
}

object GoogleCredentialAuth {

    private const val TAG = "GoogleCredentialAuth"

    /** Runs Credential Manager, then signs in with Google directly. */
    suspend fun signIn(context: Context): Result<GoogleSignInOutcome> = runCatching {
        val webClientId = context.getString(R.string.default_web_client_id)
        check(webClientId.isNotBlank()) {
            "Google authentication is missing the Web OAuth client ID."
        }

        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val result = CredentialManager.create(context).getCredential(context, request)
        val credential = result.credential

        check(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            "Google returned an unsupported credential type."
        }

        val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
        val idToken = googleCred.idToken
        val email = googleCred.id // Google sets this to the user's email address
        val firebaseCred = GoogleAuthProvider.getCredential(idToken, null)
        val auth = FirebaseAuth.getInstance()

        // Login/register surfaces should not link a chosen Google account to
        // whatever Firebase user happens to be cached. This especially matters
        // after WelcomeSettings signs in anonymously to verify access keys.
        val current = auth.currentUser
        if (current != null) {
            Log.i(TAG, "Replacing cached Firebase session before Google sign-in. anonymous=${current.isAnonymous}")
            auth.signOut()
        }

        return@runCatching try {
            val authResult = auth.signInWithCredential(firebaseCred).await()
            val signedUser = authResult.user
            if (
                signedUser != null &&
                signedUser.providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID }
            ) {
                runCatching {
                    RealtimeDBService.recordEmailHasPassword(
                        uid = signedUser.uid,
                        email = signedUser.email.orEmpty().ifBlank { email },
                    )
                }
            }
            GoogleSignInOutcome.Signed(authResult)
        } catch (e: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
            Log.i(TAG, "Firebase refused automatic Google/email account linking.", e)
            GoogleSignInOutcome.NeedsPasswordLink(
                email = email,
                pendingGoogleCredential = firebaseCred,
            )
        }
    }

    /**
     * Completes the [GoogleSignInOutcome.NeedsPasswordLink] flow by signing
     * the user in with their existing password and attaching the previously
     * obtained Google credential. Both providers end up on the same UID.
     */
    suspend fun completeLinkWithPassword(
        email: String,
        password: String,
        pendingGoogleCredential: AuthCredential,
    ): Result<AuthResult> = runCatching {
        val auth = FirebaseAuth.getInstance()
        val authResult = auth.signInWithEmailAndPassword(email, password).await()
        val user = authResult.user
            ?: error("signInWithEmailAndPassword returned no user.")
        // Linking can fail if Google credential is already attached to this
        // user (e.g., second pass through the dialog). Treat that as success.
        try {
            user.linkWithCredential(pendingGoogleCredential).await()
        } catch (e: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
            Log.i(TAG, "Google provider already linked to this account; continuing.", e)
        }
        runCatching { RealtimeDBService.recordEmailHasPassword(user.uid, email) }
        authResult
    }.onFailure { Log.w(TAG, "completeLinkWithPassword failed for $email", it) }

    fun userMessage(error: Throwable, isEnglish: Boolean): String = when (error) {
        is GetCredentialCancellationException -> {
            if (isEnglish) "Google sign-in was cancelled." else "Google sign-in cancel ho gaya hai."
        }
        is NoCredentialException -> {
            if (isEnglish) {
                "No Google account is available on this device. Add an account and try again."
            } else {
                "Is device par Google account mojood nahi. Account add karke dobara koshish karein."
            }
        }
        else -> {
            val detail = error.localizedMessage ?: error.javaClass.simpleName
            if (isEnglish) "Google sign-in failed: $detail" else "Google sign-in nakam ho gayi hai: $detail"
        }
    }
}
