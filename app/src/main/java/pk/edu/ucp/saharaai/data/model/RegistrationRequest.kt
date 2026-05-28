package pk.edu.ucp.saharaai.data.model

data class RegistrationRequest(
    val requestId: String = "",
    val applicantType: String = "", 
    val applicantName: String = "",
    val organizationName: String = "",
    val email: String = "",
    val phone: String = "",
    val region: String = "",
    val city: String = "",
    val district: String = "",
    val locationAccuracyMeters: Float = 0f,
    val verificationBody: String = "",
    val registrationNumber: String = "",
    val qualificationSummary: String = "",
    val details: String = "",
    val documentUrl: String = "",
    val documentUrls: Map<String, String> = emptyMap(),
    val requiredDocumentKeys: List<String> = emptyList(),
    val status: String = "PENDING_REVIEW",
    val issuedKey: String = "",
    val approvedAttributeIds: List<String> = emptyList(),
    val reviewedBy: String = "",
    val reviewNotes: String = "",
    val createdAt: Long = 0L,
    val reviewedAt: Long = 0L
)
