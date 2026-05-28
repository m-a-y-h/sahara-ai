package pk.edu.ucp.saharaai.data.model

import com.google.firebase.Timestamp

data class CommunityPost(
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val isAnonymous: Boolean = true,
    val contentEn: String = "",
    val contentUr: String = "",
    val category: String = "GENERAL",
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val isModerated: Boolean = false,
    val isFlagged: Boolean = false,
    val tags: List<String> = emptyList(),
    val createdAt: Timestamp = Timestamp.now()
) {
    constructor() : this("", "", "", true, "", "", "GENERAL", 0, 0, false, false, emptyList(), Timestamp.now())
}
