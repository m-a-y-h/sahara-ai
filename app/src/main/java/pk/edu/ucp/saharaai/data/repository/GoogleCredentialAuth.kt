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

/**
 * Possible outcomes of a Google sign-in attempt.
 *
 * [NeedsPasswordLink] is the important one: when the email returned by
 * Google already has a password account on the project, Firebase's
 * "Link accounts that use the same email" setting will happily merge them —
 * but if the password account was never email-verified, Google's verified
 * email assertion is treated as more trustworthy and the password provider
 * gets dropped from the user. We catch that case ourselves and require the
 * user to enter their password first; once signed in via password we attach
 * the Google credential via [FirebaseAuth.currentUser.linkWithCredential],
 * so the resulting account keeps *both* providers.
 */
sealed class GoogleSignInOutcome {
    /** Google sign-in completed and we're now authenticated. */
    data class Signed(val authResult: AuthResult) : GoogleSignInOutcome()

    /** The chosen Google email already has a password account. The caller
     *  must prompt the user for that password and then call
     *  [GoogleCredentialAuth.completeLinkWithPassword] with both pieces. */
    data class NeedsPasswordLink(
        val email: String,
        val pendingGoogleCredential: AuthCredential,
    ) : GoogleSignInOutcome()
}

object GoogleCredentialAuth {

    private const val TAG = "GoogleCredentialAuth"

    /**
     * Runs the Credential Manager flow, then either signs the user in with
     * the Google ID token directly, or — if a password account already
     * exists for the same email — defers the sign-in and returns
     * [GoogleSignInOutcome.NeedsPasswordLink].
     */
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

        // Already signed in (e.g., user is enabling Google from Settings) ->
        // attach Google to the current account directly. No collision path.
        val current = auth.currentUser
        if (current != null) {
            current.linkWithCredential(firebaseCred).await()
            return@runCatching GoogleSignInOutcome.Signed(auth.signInWithCredential(firebaseCred).await())
        }

        // Not signed in. Probe whether the email already has a password
        // account on this project. If it does, defer — otherwise plain
        // Google sign-in is safe.
        val methods = runCatching {
            auth.fetchSignInMethodsForEmail(email).await().signInMethods.orEmpty()
        }.onFailure { Log.w(TAG, "fetchSignInMethodsForEmail($email) failed", it) }
            .getOrDefault(emptyList())

        if (EmailAuthProvider.EMAIL_PASSWORD_SIGN_IN_METHOD in methods) {
            return@runCatching GoogleSignInOutcome.NeedsPasswordLink(
                email = email,
                pendingGoogleCredential = firebaseCred,
            )
        }

        GoogleSignInOutcome.Signed(auth.signInWithCredential(firebaseCred).await())
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
