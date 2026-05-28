package pk.edu.ucp.saharaai.data.model

import com.google.firebase.Timestamp

enum class AlertStatus { PENDING, ACKNOWLEDGED, RESOLVED }

data class EmergencyAlert(
    val alertId: String = "",
    val userId: String = "",
    val userName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val locationName: String = "",
    val riskScore: Float = 0f,
    val message: String = "",
    val status: String = AlertStatus.PENDING.name,
    val assignedCounselorId: String = "",
    val ngoId: String = "",
    val resolvedAt: Timestamp? = null,
    val createdAt: Timestamp = Timestamp.now()
) {
    constructor() : this("", "", "", 0.0, 0.0, "", 0f, "", AlertStatus.PENDING.name, "", "", null, Timestamp.now())
}
