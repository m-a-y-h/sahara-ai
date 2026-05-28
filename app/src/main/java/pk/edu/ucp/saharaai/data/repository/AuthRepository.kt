package pk.edu.ucp.saharaai.data.repository

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException

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
                onResult(if (task.isSuccessful) null else mapFailure(task.exception))
            }
    }

    fun register(email: String, password: String, onResult: (FirebaseAuthFailure?) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                onResult(if (task.isSuccessful) null else mapFailure(task.exception))
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
