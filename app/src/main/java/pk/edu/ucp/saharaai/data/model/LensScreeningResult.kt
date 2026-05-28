package pk.edu.ucp.saharaai.data.model

import com.google.gson.annotations.SerializedName


data class LensScanResponse(
    @SerializedName("passed")        val passed: Boolean? = null,
    @SerializedName("model_version") val modelVersion: String? = null,
    @SerializedName("quality_gate")  val qualityGate: LensQualityGate? = null,
    @SerializedName("screening")     val screening: LensScreening? = null,
)

data class LensQualityGate(
    @SerializedName("passed")         val passed: Boolean? = null,
    @SerializedName("reasons")        val reasons: List<String>? = null,
    @SerializedName("metrics")        val metrics: Map<String, Double>? = null,
    @SerializedName("face_box")       val faceBox: List<Int>? = null,
    @SerializedName("face_detector")  val faceDetector: String? = null,
)

data class LensScreening(
    @SerializedName("level")              val level: String? = null,
    @SerializedName("distress_score")     val distressScore: Double? = null,
    @SerializedName("screening_probs")    val screeningProbs: Map<String, Double>? = null,
    @SerializedName("raw_probs")          val rawProbs: Map<String, Double>? = null,
    @SerializedName("top_screening_class") val topScreeningClass: String? = null,
    @SerializedName("top_screening_prob")  val topScreeningProb: Double? = null,
    @SerializedName("reasons")            val reasons: List<String>? = null,
)


enum class LensLevel {
    NEUTRAL,
    ELEVATED,
    HIGH,
    UNCERTAIN,
    UNKNOWN;

    companion object {
        fun fromWire(raw: String?): LensLevel = when (raw?.lowercase()) {
            "neutral"   -> NEUTRAL
            "elevated"  -> ELEVATED
            "high"      -> HIGH
            "uncertain" -> UNCERTAIN
            else        -> UNKNOWN
        }
    }
}
