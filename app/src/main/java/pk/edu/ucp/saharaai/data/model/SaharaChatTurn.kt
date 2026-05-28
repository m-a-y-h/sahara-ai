package pk.edu.ucp.saharaai.data.model


enum class SaharaMessageType {
    TEXT,
    QUICK_REPLIES,
    EXERCISE_CARD,
    CRISIS_CARD,
    PRESCRIPTION_REDIRECT,
    VOICE_NOTE;

    companion object {
        
        fun fromWire(raw: String?, userIntent: String?): SaharaMessageType {
            
            
            if (userIntent == "prescription_inquiry_out_of_scope") return PRESCRIPTION_REDIRECT
            return when (raw?.uppercase()) {
                "CRISIS_CARD"   -> CRISIS_CARD
                "QUICK_REPLIES" -> QUICK_REPLIES
                "EXERCISE_CARD" -> EXERCISE_CARD
                "VOICE_NOTE"    -> VOICE_NOTE
                else            -> TEXT
            }
        }
    }
}

enum class SaharaRiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL, UNKNOWN;

    companion object {
        fun fromWire(raw: String?): SaharaRiskLevel = when (raw?.lowercase()) {
            "low"      -> LOW
            "medium"   -> MEDIUM
            "high"     -> HIGH
            "critical" -> CRITICAL
            else       -> UNKNOWN
        }
    }
}


data class SaharaChatTurnMetadata(
    val messageId: String,
    val messageType: SaharaMessageType = SaharaMessageType.TEXT,
    val riskLevel: SaharaRiskLevel = SaharaRiskLevel.UNKNOWN,
    val triggerCounselor: Boolean = false,
    val substanceDetected: String? = null,
    val substancesDetected: List<String> = emptyList(),
    val actionDestination: String? = null,
    val quickReplies: List<String> = emptyList(),
    val safetyFlags: List<String> = emptyList(),
    val detectedSymptoms: List<String> = emptyList(),
    val userIntent: String? = null,
) {
    val isCrisis: Boolean
        get() = riskLevel == SaharaRiskLevel.CRITICAL || riskLevel == SaharaRiskLevel.HIGH
    val isPrescriptionRedirect: Boolean
        get() = userIntent == "prescription_inquiry_out_of_scope"
}
