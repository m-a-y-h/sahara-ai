package pk.edu.ucp.saharaai.data.model

data class RegionalRiskSummary(
    val region: String,
    val registeredUsers: Int,
    val assessedUsers: Int,
    val totalAssessments: Int,
    val averageLatestScore: Double,
    val highRiskUsers: Int,
    val moderateRiskUsers: Int
)
