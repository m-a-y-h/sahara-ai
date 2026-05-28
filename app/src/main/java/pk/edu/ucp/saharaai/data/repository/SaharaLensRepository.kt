package pk.edu.ucp.saharaai.data.repository

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import pk.edu.ucp.saharaai.BuildConfig
import pk.edu.ucp.saharaai.data.model.LensLevel
import pk.edu.ucp.saharaai.data.model.LensScanResponse
import pk.edu.ucp.saharaai.data.remote.SaharaLensClient


object SaharaLensRepository {

    private val firestore by lazy { Firebase.firestore }
    private val auth by lazy { Firebase.auth }

    suspend fun scan(imageBytes: ByteArray): Result<LensScanResponse> {
        val endpoint = BuildConfig.SAHARA_LENS_SCAN_URL
        return SaharaLensClient.scan(endpoint, imageBytes)
    }

    
    suspend fun recordCheckIn(scan: LensScanResponse): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(
            IllegalStateException("not signed in")
        )
        val screening = scan.screening ?: return Result.failure(
            IllegalStateException("scan has no screening payload")
        )
        val level = LensLevel.fromWire(screening.level)

        val payload = mapOf(
            "level"            to level.name.lowercase(),
            "distress_score"   to (screening.distressScore ?: 0.0),
            "top_class"        to (screening.topScreeningClass ?: "neutral"),
            "top_class_prob"   to (screening.topScreeningProb ?: 0.0),
            "screening_probs"  to (screening.screeningProbs ?: emptyMap<String, Double>()),
            "raw_probs"        to (screening.rawProbs ?: emptyMap<String, Double>()),
            "reasons"          to (screening.reasons ?: emptyList<String>()),
            "model_version"    to (scan.modelVersion ?: "unknown"),
            "quality_passed"   to (scan.qualityGate?.passed ?: false),
            "createdAt"        to FieldValue.serverTimestamp(),
        )

        return runCatching {
            val ref = firestore
                .collection("users").document(uid)
                .collection("sahara_lens_checkins")
                .add(payload)
                .await()
            ref.id
        }
    }
}
