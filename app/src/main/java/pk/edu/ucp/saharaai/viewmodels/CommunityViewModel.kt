package pk.edu.ucp.saharaai.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService

sealed class CommunityUiState {
    object Idle    : CommunityUiState()
    object Loading : CommunityUiState()
    object Success : CommunityUiState()
    data class Error(val message: String) : CommunityUiState()
}

class CommunityViewModel : ViewModel() {

    val uid: String get() = Firebase.auth.currentUser?.uid ?: ""

    
    private val _posts = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val posts: StateFlow<List<Map<String, Any>>> = _posts.asStateFlow()

    private val _uiState = MutableStateFlow<CommunityUiState>(CommunityUiState.Idle)
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    
    private val _likedPostIds = MutableStateFlow<Set<String>>(emptySet())
    val likedPostIds: StateFlow<Set<String>> = _likedPostIds.asStateFlow()

    
    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    
    
    private val _repliesMap = MutableStateFlow<Map<String, List<Map<String, Any>>>>(emptyMap())
    val repliesMap: StateFlow<Map<String, List<Map<String, Any>>>> = _repliesMap.asStateFlow()

    
    private val _expandedReplies = MutableStateFlow<Set<String>>(emptySet())
    val expandedReplies: StateFlow<Set<String>> = _expandedReplies.asStateFlow()

    
    private val replyJobs = mutableMapOf<String, Job>()

    
    init {
        val currentUid = uid
        if (currentUid.isNotBlank()) {
            loadUserName(currentUid)
            loadLikedPostIds(currentUid)
        }
    }

    private fun loadUserName(currentUid: String) {
        
        Firebase.auth.currentUser?.displayName
            ?.takeIf { it.isNotBlank() }
            ?.let { _userName.value = it }

        
        viewModelScope.launch {
            RealtimeDBService.getUser(currentUid).onSuccess { map ->
                map?.get("name")?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { _userName.value = it }
            }
        }
    }

    private fun loadLikedPostIds(currentUid: String) {
        viewModelScope.launch {
            RealtimeDBService.getLikedPostIds(currentUid).onSuccess { ids ->
                _likedPostIds.value = ids
            }
        }
    }

    

    
    fun listenToPosts() {
        _uiState.value = CommunityUiState.Loading
        viewModelScope.launch {
            try {
                RealtimeDBService.listenToCommunityPosts().collect { list ->
                    _posts.value = list
                    _uiState.value = CommunityUiState.Success
                }
            } catch (e: Exception) {
                _uiState.value = CommunityUiState.Error(e.message ?: "Failed to load posts.")
            }
        }
    }

    
    fun createPost(
        content: String,
        isAnonymous: Boolean,
        category: String
    ) {
        val currentUid = uid
        if (content.isBlank() || currentUid.isBlank()) return

        viewModelScope.launch {
            val resolvedName: String = if (isAnonymous) {
                "Anonymous"
            } else {
                
                val freshName = RealtimeDBService.getUser(currentUid)
                    .getOrNull()?.get("name")?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?: Firebase.auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
                    ?: _userName.value.takeIf { it.isNotBlank() }
                    ?: "User"
                _userName.value = freshName
                freshName
            }

            RealtimeDBService.saveCommunityPost(
                authorId    = currentUid,
                authorName  = resolvedName,
                isAnonymous = isAnonymous,
                content     = content,
                category    = category
            ).onFailure {
                _uiState.value = CommunityUiState.Error(it.message ?: "Failed to post.")
            }
        }
    }

    
    fun deletePost(postId: String) {
        viewModelScope.launch { RealtimeDBService.deleteCommunityPost(postId) }
    }

    
    fun flagPost(postId: String) {
        viewModelScope.launch { RealtimeDBService.flagCommunityPost(postId) }
    }

    

    
    fun toggleLike(postId: String) {
        val currentUid = uid
        if (currentUid.isBlank()) return

        val liked = postId in _likedPostIds.value
        
        _likedPostIds.value = if (liked) _likedPostIds.value - postId
                              else       _likedPostIds.value + postId

        viewModelScope.launch {
            if (liked) {
                RealtimeDBService.removePostLike(currentUid, postId)
                RealtimeDBService.decrementPostLike(postId)
            } else {
                RealtimeDBService.setPostLike(currentUid, postId)
                RealtimeDBService.incrementPostLike(postId)
            }
        }
    }

    

    
    fun toggleReplies(postId: String) {
        val isCurrentlyExpanded = postId in _expandedReplies.value
        if (isCurrentlyExpanded) {
            
            replyJobs[postId]?.cancel()
            replyJobs.remove(postId)
            _expandedReplies.value = _expandedReplies.value - postId
        } else {
            
            _expandedReplies.value = _expandedReplies.value + postId
            replyJobs[postId]?.cancel() 
            replyJobs[postId] = viewModelScope.launch {
                try {
                    RealtimeDBService.listenToReplies(postId).collect { replies ->
                        _repliesMap.value = _repliesMap.value + (postId to replies)
                    }
                } catch (_: Exception) {  }
            }
        }
    }

    
    fun addReply(postId: String, content: String, isAnonymous: Boolean) {
        val currentUid = uid
        if (content.isBlank() || currentUid.isBlank()) return

        viewModelScope.launch {
            val resolvedName: String = if (isAnonymous) {
                "Anonymous"
            } else {
                RealtimeDBService.getUser(currentUid)
                    .getOrNull()?.get("name")?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?: Firebase.auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
                    ?: _userName.value.takeIf { it.isNotBlank() }
                    ?: "User"
            }

            val result = RealtimeDBService.saveCommunityReply(
                postId      = postId,
                authorId    = currentUid,
                authorName  = resolvedName,
                isAnonymous = isAnonymous,
                content     = content
            )

            
            result.onSuccess {
                val post     = _posts.value.find { it["postId"] as? String == postId }
                val authorId = post?.get("authorId") as? String ?: ""
                if (authorId.isNotBlank() && authorId != currentUid) {
                    val sender = if (isAnonymous) "Someone"
                                 else resolvedName.takeIf { it.isNotBlank() && it != "User" } ?: "Someone"
                    RealtimeDBService.saveUserNotification(
                        uid         = authorId,
                        titleEn     = "New Reply on Your Post",
                        titleUr     = "Aapki Post Par Naya Jawab",
                        bodyEn      = "$sender replied to your community post.",
                        bodyUr      = "$sender ne aapki community post par jawab diya.",
                        type        = "COMMUNITY_REPLY",
                        actionRoute = "community"
                    )
                }
            }
        }
    }

    fun clearError() { _uiState.value = CommunityUiState.Idle }

    override fun onCleared() {
        super.onCleared()
        replyJobs.values.forEach { it.cancel() }
        replyJobs.clear()
    }
}
