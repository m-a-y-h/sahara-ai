package pk.edu.ucp.saharaai.data.model

import com.google.firebase.Timestamp

data class Assessment(
    val assessmentId: String = "",
    val userId: String = "",
    val type: String = "DAST10",
    val answers: Map<Int, Boolean> = emptyMap(),
    val score: Int = 0,
    val riskLevel: String = "LOW",
    val completedAt: Timestamp = Timestamp.now(),
    val recommendations: List<String> = emptyList()
)
