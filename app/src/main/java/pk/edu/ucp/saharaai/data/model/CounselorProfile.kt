package pk.edu.ucp.saharaai.data.model

import com.google.firebase.Timestamp

data class CounselorProfile(
    val counselorId: String = "",
    val userId: String = "",
    val name: String = "",
    val specialization: String = "",
    val bio: String = "",
    val rating: Float = 0f,
    val totalRatings: Int = 0,
    val isAvailable: Boolean = true,
    val isVerified: Boolean = true,
    val sessionCount: Int = 0,
    val languagesSpoken: List<String> = listOf("English", "Urdu"),
    val profileImageUrl: String = "",
    val feePkr: Int = 0,
    val attributeIds: List<String> = emptyList(),
    val callEnabled: Boolean = false,
    val ngoId: String = "",
    val ngoName: String = "",
    val region: String = "",
    val createdAt: Timestamp = Timestamp.now()
) {
    constructor() : this(
        "", "", "", "", "", 0f, 0, true, true, 0,
        listOf(), "", 0, emptyList(), false, "", "", "", Timestamp.now()
    )
}
