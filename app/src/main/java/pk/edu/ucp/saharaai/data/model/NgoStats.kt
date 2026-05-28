package pk.edu.ucp.saharaai.data.model

import com.google.firebase.Timestamp

data class NgoStats(
    val ngoId: String = "",
    val ngoName: String = "",
    val region: String = "",
    val totalUsers: Int = 0,
    val activeUsers: Int = 0,
    val highRiskCount: Int = 0,
    val mediumRiskCount: Int = 0,
    val lowRiskCount: Int = 0,
    val totalAssessments: Int = 0,
    val totalAlerts: Int = 0,
    val resolvedAlerts: Int = 0,
    val weeklyActiveUsers: Map<String, Int> = emptyMap(),   
    val riskTrend: List<Float> = emptyList(),               
    val lastUpdated: Timestamp = Timestamp.now()
) {
    constructor() : this("", "", "", 0, 0, 0, 0, 0, 0, 0, 0, emptyMap(), emptyList(), Timestamp.now())
}
