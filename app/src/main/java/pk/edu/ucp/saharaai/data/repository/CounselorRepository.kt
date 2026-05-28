package pk.edu.ucp.saharaai.data.repository

import kotlinx.coroutines.flow.Flow
import pk.edu.ucp.saharaai.data.model.CounselorProfile
import pk.edu.ucp.saharaai.data.remote.FirestoreService

object CounselorRepository {

    
    suspend fun getCounselors(): Result<List<CounselorProfile>> =
        FirestoreService.getCounselors()

    
    suspend fun getCounselorsByNgo(ngoId: String): Result<List<CounselorProfile>> =
        FirestoreService.getCounselorsByNgo(ngoId)

    
    fun getCounselorsByNgoFlow(ngoId: String): Flow<List<CounselorProfile>> =
        FirestoreService.getCounselorsByNgoFlow(ngoId)

    
    suspend fun getCounselor(counselorId: String): Result<CounselorProfile?> =
        FirestoreService.getCounselor(counselorId)

    
    suspend fun getCounselorByUserId(userId: String): Result<CounselorProfile?> =
        FirestoreService.getCounselorByUserId(userId)

    
    suspend fun saveCounselor(profile: CounselorProfile): Result<Unit> =
        FirestoreService.saveCounselor(profile)

    
    suspend fun setAvailability(counselorId: String, available: Boolean): Result<Unit> =
        FirestoreService.updateCounselorAvailability(counselorId, available)

    
    suspend fun completeSession(counselorId: String): Result<Unit> =
        FirestoreService.incrementCounselorSessions(counselorId)

    
    suspend fun rateCounselor(counselorId: String, rating: Float): Result<Unit> {
        if (rating < 1f || rating > 5f) return Result.failure(
            IllegalArgumentException("Rating must be between 1 and 5")
        )
        return FirestoreService.rateCounselor(counselorId, rating)
    }

    
    suspend fun getOrCreateChatSession(userId: String, counselorId: String): Result<String> =
        FirestoreService.getOrCreateChatSession(userId, counselorId)

    
    fun getCounselorChatsFlow(counselorId: String) =
        FirestoreService.getCounselorChatsFlow(counselorId)

    
    fun buildSessionId(userId: String, counselorId: String): String =
        ChatRepository.counselorSessionId(userId, counselorId)
}
