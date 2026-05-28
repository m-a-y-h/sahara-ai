package pk.edu.ucp.saharaai.data.model

import com.google.gson.annotations.SerializedName


data class VoiceAnalyzeResponse(
    @SerializedName("passed")        val passed: Boolean? = null,
    @SerializedName("model_version") val modelVersion: String? = null,
    @SerializedName("reasons")       val reasons: List<String>? = null,
    @SerializedName("audio")         val audio: VoiceAudio? = null,
    @SerializedName("screening")     val screening: VoiceScreening? = null,
    @SerializedName("raw_probs")     val rawProbs: Map<String, Double>? = null,
)

data class VoiceAudio(
    @SerializedName("duration_s")  val durationS: Double? = null,
    @SerializedName("sample_rate") val sampleRate: Int? = null,
)

data class VoiceScreening(
    @SerializedName("level")               val level: String? = null,
    @SerializedName("distress_score")      val distressScore: Double? = null,
    @SerializedName("screening_probs")     val screeningProbs: Map<String, Double>? = null,
    @SerializedName("raw_probs")           val rawProbs: Map<String, Double>? = null,
    @SerializedName("top_screening_class") val topScreeningClass: String? = null,
    @SerializedName("top_screening_prob")  val topScreeningProb: Double? = null,
    @SerializedName("reasons")             val reasons: List<String>? = null,
)


enum class VoiceLevel {
    NEUTRAL,
    ELEVATED,
    HIGH,
    UNCERTAIN,
    UNKNOWN;

    companion object {
        fun fromWire(raw: String?): VoiceLevel = when (raw?.lowercase()) {
            "neutral"   -> NEUTRAL
            "elevated"  -> ELEVATED
            "high"      -> HIGH
            "uncertain" -> UNCERTAIN
            else        -> UNKNOWN
        }
    }
}
