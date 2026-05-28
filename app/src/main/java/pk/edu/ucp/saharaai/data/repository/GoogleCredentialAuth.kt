package pk.edu.ucp.saharaai.data.repository

import android.content.Context
import androidx.credentials.CustomCredential
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import pk.edu.ucp.saharaai.R

object GoogleCredentialAuth {
    suspend fun signIn(context: Context): Result<AuthResult> = runCatching {
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

        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        FirebaseAuth.getInstance()
            .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
            .await()
    }

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
