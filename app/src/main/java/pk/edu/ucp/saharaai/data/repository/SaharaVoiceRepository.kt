package pk.edu.ucp.saharaai.data.repository

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import pk.edu.ucp.saharaai.BuildConfig
import pk.edu.ucp.saharaai.data.model.VoiceAnalyzeResponse
import pk.edu.ucp.saharaai.data.model.VoiceLevel
import pk.edu.ucp.saharaai.data.remote.SaharaVoiceClient


object SaharaVoiceRepository {

    private val firestore by lazy { Firebase.firestore }
    private val auth by lazy { Firebase.auth }

    suspend fun analyze(
        audioBytes: ByteArray,
        mimeType: String = "audio/m4a",
    ): Result<VoiceAnalyzeResponse> {
        val endpoint = BuildConfig.SAHARA_VOICE_ANALYZE_URL
        return SaharaVoiceClient.analyze(endpoint, audioBytes, mimeType = mimeType)
    }

    suspend fun recordCheckIn(response: VoiceAnalyzeResponse): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(
            IllegalStateException("not signed in")
        )
        val screening = response.screening ?: return Result.failure(
            IllegalStateException("response has no screening payload")
        )
        val level = VoiceLevel.fromWire(screening.level)
        val payload = mapOf(
            "level"           to level.name.lowercase(),
            "distress_score"  to (screening.distressScore ?: 0.0),
            "top_class"       to (screening.topScreeningClass ?: "neutral"),
            "top_class_prob"  to (screening.topScreeningProb ?: 0.0),
            "screening_probs" to (screening.screeningProbs ?: emptyMap<String, Double>()),
            "raw_probs"       to (response.rawProbs ?: emptyMap<String, Double>()),
            "reasons"         to (screening.reasons ?: emptyList<String>()),
            "model_version"   to (response.modelVersion ?: "unknown"),
            "duration_s"      to (response.audio?.durationS ?: 0.0),
            "createdAt"       to FieldValue.serverTimestamp(),
        )
        return runCatching {
            firestore
                .collection("users").document(uid)
                .collection("sahara_voice_checkins")
                .add(payload)
                .await()
                .id
        }
    }
}
