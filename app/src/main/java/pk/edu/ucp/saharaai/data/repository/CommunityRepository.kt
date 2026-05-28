package pk.edu.ucp.saharaai.data.repository

import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import pk.edu.ucp.saharaai.data.model.CommunityPost
import pk.edu.ucp.saharaai.data.remote.FirestoreService

object CommunityRepository {

    
    suspend fun createPost(
        authorId: String,
        authorName: String,
        contentEn: String,
        contentUr: String,
        isAnonymous: Boolean,
        category: String,
        tags: List<String>
    ): Result<String> {
        val post = CommunityPost(
            authorId    = authorId,
            authorName  = if (isAnonymous) "Anonymous" else authorName,
            isAnonymous = isAnonymous,
            contentEn   = contentEn,
            contentUr   = contentUr,
            category    = category,
            tags        = tags,
            createdAt   = Timestamp.now()
        )
        return FirestoreService.createCommunityPost(post)
    }

    
    suspend fun getPosts(): Result<List<CommunityPost>> =
        FirestoreService.getCommunityPosts()

    
    fun getPostsFlow(): Flow<List<CommunityPost>> =
        FirestoreService.getCommunityPostsFlow()

    
    suspend fun likePost(postId: String): Result<Unit> =
        FirestoreService.toggleLikePost(postId, increment = true)

    
    suspend fun unlikePost(postId: String): Result<Unit> =
        FirestoreService.toggleLikePost(postId, increment = false)

    
    suspend fun deletePost(postId: String): Result<Unit> =
        FirestoreService.deleteCommunityPost(postId)

    
    suspend fun flagPost(postId: String, reportedBy: String): Result<Unit> {
        
        val flagResult = FirestoreService.flagPost(postId)
        
        if (flagResult.isSuccess) {
            ReportRepository.submitReport(
                reportedBy = reportedBy,
                targetId   = postId,
                targetType = "POST",
                reason     = "HARMFUL_CONTENT",
                description = "User flagged community post."
            )
        }
        return flagResult
    }

    
    fun getCategories(isEnglish: Boolean): List<Pair<String, String>> = listOf(
        "GENERAL"    to if (isEnglish) "General"     else "عمومی",
        "RECOVERY"   to if (isEnglish) "Recovery"    else "بحالی",
        "SUPPORT"    to if (isEnglish) "Support"     else "حمایت",
        "MOTIVATION" to if (isEnglish) "Motivation"  else "حوصلہ افزائی"
    )
}
