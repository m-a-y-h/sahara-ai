package pk.edu.ucp.saharaai.utils

import pk.edu.ucp.saharaai.utils.Constants.MESSAGE_MAX_LENGTH
import pk.edu.ucp.saharaai.utils.Constants.MESSAGE_MIN_LENGTH
import pk.edu.ucp.saharaai.utils.Constants.PASSWORD_MAX_LENGTH
import pk.edu.ucp.saharaai.utils.Constants.PASSWORD_MIN_LENGTH
import pk.edu.ucp.saharaai.utils.Constants.VOICE_MAX_DURATION_SECONDS
import pk.edu.ucp.saharaai.utils.Constants.VOICE_MIN_DURATION_SECONDS


sealed class PasswordValidationResult {
    object Valid : PasswordValidationResult()
    data class Invalid(val reasons: List<String>) : PasswordValidationResult()
}

object ValidationUtils {

    private val EMAIL_REGEX = Regex(
        "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$"
    )

    

    
    fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && EMAIL_REGEX.matches(email.trim())
    }

    

    
    fun isValidPassword(password: String): PasswordValidationResult {
        val reasons = mutableListOf<String>()

        if (password.length < PASSWORD_MIN_LENGTH || password.length > PASSWORD_MAX_LENGTH) {
            reasons.add("Password must be $PASSWORD_MIN_LENGTH–$PASSWORD_MAX_LENGTH characters long.")
        }
        if (!password.any { it.isUpperCase() }) {
            reasons.add("Password must contain at least one uppercase letter.")
        }
        if (!password.any { it.isDigit() }) {
            reasons.add("Password must contain at least one number.")
        }
        if (!password.any { !it.isLetterOrDigit() }) {
            reasons.add("Password must contain at least one special character.")
        }
        if (password != password.trim()) {
            reasons.add("Password must not have leading or trailing whitespace.")
        }

        return if (reasons.isEmpty()) PasswordValidationResult.Valid
        else PasswordValidationResult.Invalid(reasons)
    }

    

    
    fun isValidVoiceDuration(seconds: Float): Boolean {
        return seconds in VOICE_MIN_DURATION_SECONDS..VOICE_MAX_DURATION_SECONDS
    }

    

    
    fun isValidMessageLength(msg: String): Boolean {
        return msg.length in MESSAGE_MIN_LENGTH..MESSAGE_MAX_LENGTH
    }
}
