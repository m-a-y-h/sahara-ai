package pk.edu.ucp.saharaai.data.model

import com.google.firebase.Timestamp

data class User(
    val userId: String = "",
    val email: String = "",
    val fullName: String = "",
    val role: String = "PATIENT",          
    val isAnonymous: Boolean = false,
    val languagePreference: String = "en", 
    val createdAt: Timestamp = Timestamp.now(),
    val riskScore: Float = 0f,
    val isVerified: Boolean = false
)
