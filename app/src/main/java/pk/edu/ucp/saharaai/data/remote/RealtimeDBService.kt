package pk.edu.ucp.saharaai.data.remote

import android.net.Uri
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import pk.edu.ucp.saharaai.data.model.AvatarRequest
import pk.edu.ucp.saharaai.data.model.BugReport
import pk.edu.ucp.saharaai.data.model.CounselorAttributeCatalog
import pk.edu.ucp.saharaai.data.model.PaymentRequest
import pk.edu.ucp.saharaai.data.model.RegionalRiskSummary
import pk.edu.ucp.saharaai.data.model.RegistrationRequest
import pk.edu.ucp.saharaai.data.model.SocialPlatformConnection
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


object RealtimeDBService {

    private val db get() = FirebaseDatabase.getInstance()
    private const val YOUTUBE_API_DATA_RETENTION_MS = 30L * 24L * 60L * 60L * 1000L

    data class PostAuthUserState(
        val isBlocked: Boolean,
        val blockReason: String = "",
        val onboardingCompleted: Boolean = false,
        val userData: Map<String, Any> = emptyMap(),
    )

    
    
    

    
    suspend fun saveUser(uid: String, name: String, email: String): Result<Unit> =
        ensureUserRecord(uid, name, email).map { }

    suspend fun ensureUserRecord(uid: String, name: String, email: String): Result<Map<String, Any>> = runCatching {
        require(uid.isNotBlank()) { "Missing user id." }
        val ref = db.getReference("users").child(uid)
        val snap = ref.get().await()
        val now = System.currentTimeMillis()
        val existing = snap.value as? Map<String, Any>
        val updates = mutableMapOf<String, Any>(
            "uid" to uid,
            "updatedAt" to now,
        )
        val cleanName = name.trim().ifBlank { existing?.get("name")?.toString().orEmpty() }.ifBlank { "Sahara User" }
        val cleanEmail = email.trim().lowercase().ifBlank { existing?.get("email")?.toString().orEmpty() }
        updates["name"] = cleanName
        if (cleanEmail.isNotBlank()) {
            updates["email"] = cleanEmail
            updates["emailKey"] = emailKey(cleanEmail)
        }
        if (!snap.exists()) {
            updates["createdAt"] = now
            updates["onboardingCompleted"] = false
            updates["isIdentityVisibleToCounselors"] = false
            updates["avatarId"] = "avatar_01"
        }
        ref.updateChildren(updates).await()
        @Suppress("UNCHECKED_CAST")
        (ref.get().await().value as? Map<String, Any>) ?: updates
    }

    suspend fun postAuthUserState(uid: String, name: String, email: String): Result<PostAuthUserState> = runCatching {
        val cleanEmail = email.trim().lowercase()
        val uidBlock = if (uid.isNotBlank()) {
            db.getReference("blocked_users").child(uid).get().await()
        } else null
        val emailBlock = if (cleanEmail.isNotBlank()) {
            db.getReference("blocked_emails").child(emailKey(cleanEmail)).get().await()
        } else null
        val blockedSnapshot = listOfNotNull(uidBlock, emailBlock).firstOrNull { it.exists() }
        if (blockedSnapshot != null) {
            return@runCatching PostAuthUserState(
                isBlocked = true,
                blockReason = blockedSnapshot.child("reason").getValue(String::class.java).orEmpty()
                    .ifBlank { "This account is blocked." },
            )
        }
        val userData = ensureUserRecord(uid, name, cleanEmail).getOrThrow()
        PostAuthUserState(
            isBlocked = false,
            onboardingCompleted = userData["onboardingCompleted"] as? Boolean ?: false,
            userData = userData,
        )
    }

    suspend fun completeOnboarding(
        uid: String,
        ageGroup: String,
        currentSituation: String,
        selectedHelps: Set<String>,
        avatarId: String,
        notificationsAllowed: Boolean,
        locationAllowed: Boolean,
        actigraphyAllowed: Boolean,
    ): Result<Unit> = runCatching {
        require(uid.isNotBlank()) { "Missing user id." }
        db.getReference("users").child(uid).updateChildren(
            mapOf(
                "ageGroup" to ageGroup,
                "currentSituation" to currentSituation,
                "selectedHelps" to selectedHelps.toList(),
                "avatarId" to avatarId,
                "notificationsAllowed" to notificationsAllowed,
                "locationAllowed" to locationAllowed,
                "actigraphyAllowed" to actigraphyAllowed,
                "onboardingCompleted" to true,
                "onboardedAt" to System.currentTimeMillis(),
                "isIdentityVisibleToCounselors" to false,
            )
        ).await()
    }

    
    suspend fun getUser(uid: String): Result<Map<String, Any>?> = runCatching {
        db.getReference("users").child(uid).get().await().value as? Map<String, Any>
    }

    
    suspend fun getUserDisplayName(uid: String): String = runCatching {
        db.getReference("users").child(uid).child("name")
            .get().await().getValue(String::class.java) ?: ""
    }.getOrDefault("")

    
    suspend fun updateUserName(uid: String, newName: String): Result<Unit> = runCatching {
        db.getReference("users").child(uid)
            .updateChildren(mapOf("name" to newName)).await()
    }

    
    suspend fun updateUserRegion(uid: String, region: String): Result<Unit> = runCatching {
        db.getReference("users").child(uid)
            .updateChildren(mapOf("region" to region.trim())).await()
    }

    suspend fun updateUserAvatarId(uid: String, avatarId: String): Result<Unit> = runCatching {
        db.getReference("users").child(uid).updateChildren(
            mapOf(
                "avatarId" to avatarId,
                "customAvatarStatus" to "",
                "customAvatarUrl" to ""
            )
        ).await()
    }

    suspend fun getUserRegion(uid: String): String = runCatching {
        db.getReference("users").child(uid).child("region")
            .get().await().getValue(String::class.java) ?: ""
    }.getOrDefault("")

    
    suspend fun getUserCreatedAt(uid: String): Long = runCatching {
        db.getReference("users").child(uid).child("createdAt")
            .get().await().getValue(Long::class.java) ?: 0L
    }.getOrDefault(0L)

    
    suspend fun logFaceLogin(uid: String): Result<Unit> = runCatching {
        db.getReference("user_activity").child(uid).child("faceLogins")
            .push().setValue(System.currentTimeMillis()).await()
    }

    
    suspend fun setBiometricEnabled(uid: String, enabled: Boolean): Result<Unit> = runCatching {
        db.getReference("users").child(uid)
            .updateChildren(mapOf("biometricEnabled" to enabled)).await()
    }

    
    suspend fun getBiometricEnabled(uid: String): Boolean = runCatching {
        db.getReference("users").child(uid).child("biometricEnabled")
            .get().await().getValue(Boolean::class.java) ?: false
    }.getOrDefault(false)

    suspend fun resetCompletedCycleData(uid: String, cycleId: String): Result<Unit> = runCatching {
        require(uid.isNotBlank()) { "Missing user id." }
        val cleanCycleId = cycleId.ifBlank { "unknown" }
        val userRef = db.getReference("users").child(uid)
        val lastReset = userRef.child("lastCycleResetId").get().await()
            .getValue(String::class.java)
        if (lastReset == cleanCycleId) return@runCatching

        val now = System.currentTimeMillis()
        db.getReference().updateChildren(
            mapOf(
                "/journals/$uid" to null,
                "/sleep/$uid" to null,
                "/mood_logs/$uid" to null,
                "/screen_time/$uid" to null,
                "/app_usage_events/$uid" to null,
                "/users/$uid/lastCycleResetId" to cleanCycleId,
                "/users/$uid/lastCycleResetAt" to now,
            )
        ).await()
    }

    
    
    

    
    suspend fun getCounselorKey(key: String): Result<Map<String, Any>?> = runCatching {
        db.getReference("counselor_keys").child(key).get().await().value as? Map<String, Any>
    }

    
    suspend fun getCounselorKeyByUid(uid: String): Result<Pair<String, Map<String, Any>>?> = runCatching {
        val snap = db.getReference("counselor_keys").get().await()
        for (child in snap.children) {
            val data = child.value as? Map<String, Any> ?: continue
            if (data["uid"]?.toString() == uid) return@runCatching Pair(child.key ?: "", data)
        }
        null
    }

    
    suspend fun saveCounselorSetup(
        key: String, uid: String, name: String,
        feePkr: Int,
        ngoName: String, region: String, bio: String
    ): Result<Unit> = runCatching {
        require(feePkr in 0..5000) { "Counselor fee must be between PKR 0 and PKR 5000." }
        db.getReference("counselor_keys").child(key).updateChildren(mapOf(
            "uid"             to uid,
            "assignedName"    to name,
            "ngoName"         to ngoName,
            "region"          to region,
            "bio"             to bio,
            "feePkr"          to feePkr,
            "profileComplete" to true,
            "callEnabled"     to false,
            "rating"          to 0.0,
            "totalRatings"    to 0,
            "sessionCount"    to 0
        )).await()
    }

    
    suspend fun setOnlineStatus(key: String, isOnline: Boolean): Result<Unit> = runCatching {
        db.getReference("counselor_keys").child(key).child("isOnline").setValue(isOnline).await()
    }

    suspend fun setCounselorCallAvailability(key: String, enabled: Boolean): Result<Unit> = runCatching {
        require(key.isNotBlank()) { "Missing counselor key." }
        db.getReference("counselor_keys").child(key).updateChildren(
            mapOf(
                "callEnabled" to enabled,
                "callAvailabilityUpdatedAt" to System.currentTimeMillis(),
            )
        ).await()
    }

    
    fun listenToOnlineCounselors(): Flow<List<Map<String, Any>>> = callbackFlow {
        val ref = db.getReference("counselor_keys")
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val list = mutableListOf<Map<String, Any>>()
                for (child in snap.children) {
                    val data = child.value as? Map<String, Any> ?: continue
                    val isOnline  = data["isOnline"]  as? Boolean ?: false
                    val isActive  = data["isActive"]  as? Boolean ?: false
                    val hasProfile = data["profileComplete"] as? Boolean ?: false
                    if (isOnline && isActive && hasProfile) {
                        list.add(data + mapOf("key" to (child.key ?: "")))
                    }
                }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) { Log.w("RealtimeDBService", "RTDB listener cancelled", error.toException()); close() }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    
    fun listenToCounselorData(key: String): Flow<Map<String, Any>?> = callbackFlow {
        val ref = db.getReference("counselor_keys").child(key)
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) = trySend(snap.value as? Map<String, Any>).let {}
            override fun onCancelled(error: DatabaseError) { Log.w("RealtimeDBService", "RTDB listener cancelled", error.toException()); close() }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    
    suspend fun updateRating(key: String, newRating: Float): Result<Unit> = runCatching {
        val ref  = db.getReference("counselor_keys").child(key)
        val snap = ref.get().await()
        val data = snap.value as? Map<String, Any> ?: return@runCatching
        val total = (data["totalRatings"] as? Long)?.toInt() ?: 0
        val cur   = (data["rating"] as? Double) ?: 0.0
        val avg   = if (total == 0) newRating.toDouble() else ((cur * total) + newRating) / (total + 1)
        ref.updateChildren(mapOf("rating" to avg, "totalRatings" to total + 1)).await()
    }

    
    
    

    
    fun chatSessionPath(uid: String, counselorKey: String) = "${uid}_${counselorKey}"

    
    suspend fun sendChatMessage(
        uid: String, counselorKey: String,
        text: String, senderType: String
    ): Result<Unit> = runCatching {
        val msgId = UUID.randomUUID().toString()
        db.getReference("user_chats")
            .child(chatSessionPath(uid, counselorKey))
            .child("messages")
            .child(msgId)
            .setValue(mapOf(
                "messageId"    to msgId,
                "text"         to text,
                "senderId"     to uid,
                "senderType"   to senderType,
                "counselorKey" to counselorKey,
                "timestamp"    to System.currentTimeMillis()
            )).await()
    }

    
    suspend fun sendChatMessageFromCounselor(
        userUid: String, counselorKey: String,
        counselorUid: String, text: String
    ): Result<Unit> = runCatching {
        val msgId = UUID.randomUUID().toString()
        db.getReference("user_chats")
            .child(chatSessionPath(userUid, counselorKey))   
            .child("messages")
            .child(msgId)
            .setValue(mapOf(
                "messageId"    to msgId,
                "text"         to text,
                "senderId"     to counselorUid,
                "senderType"   to "counselor",
                "counselorKey" to counselorKey,
                "timestamp"    to System.currentTimeMillis()
            )).await()
    }

    
    fun listenToChatMessages(uid: String, counselorKey: String): Flow<List<Map<String, Any>>> = callbackFlow {
        val ref = db.getReference("user_chats")
            .child(chatSessionPath(uid, counselorKey))
            .child("messages")
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val msgs = snap.children
                    .mapNotNull { it.value as? Map<String, Any> }
                    .sortedBy { (it["timestamp"] as? Long) ?: 0L }
                trySend(msgs)
            }
            override fun onCancelled(error: DatabaseError) { Log.w("RealtimeDBService", "RTDB listener cancelled", error.toException()); close() }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    
    
    

    
    suspend fun saveJournalEntry(
        uid: String, mood: String, prompt: String, notes: String
    ): Result<Unit> = runCatching {
        val entryId = UUID.randomUUID().toString()
        db.getReference("journals").child(uid).child(entryId)
            .setValue(mapOf(
                "id"        to entryId,
                "mood"      to mood,
                "prompt"    to prompt,
                "notes"     to notes,
                "timestamp" to System.currentTimeMillis()
            )).await()
    }

    
    suspend fun getJournalEntries(uid: String): Result<List<Map<String, Any>>> = runCatching {
        val snap = db.getReference("journals").child(uid).get().await()
        snap.children
            .mapNotNull { it.value as? Map<String, Any> }
            .sortedByDescending { (it["timestamp"] as? Long) ?: 0L }
    }

    
    suspend fun deleteJournalEntry(uid: String, entryId: String): Result<Unit> = runCatching {
        db.getReference("journals").child(uid).child(entryId).removeValue().await()
    }

    
    
    

    
    suspend fun setPostLike(uid: String, postId: String): Result<Unit> = runCatching {
        db.getReference("community_likes").child(uid).child(postId).setValue(true).await()
    }

    
    suspend fun removePostLike(uid: String, postId: String): Result<Unit> = runCatching {
        db.getReference("community_likes").child(uid).child(postId).removeValue().await()
    }

    
    suspend fun getLikedPostIds(uid: String): Result<Set<String>> = runCatching {
        val snap = db.getReference("community_likes").child(uid).get().await()
        snap.children.mapNotNull { it.key }.toSet()
    }

    
    fun listenToCounselorChats(counselorKey: String): Flow<List<Map<String, Any>>> = callbackFlow {
        val ref = db.getReference("user_chats")
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val sessions = mutableListOf<Map<String, Any>>()
                for (child in snap.children) {
                    val sessionId = child.key ?: continue
                    if (!sessionId.endsWith("_$counselorKey")) continue
                    val uid       = sessionId.removeSuffix("_$counselorKey")
                    val messages  = child.child("messages").children
                        .mapNotNull { it.value as? Map<String, Any> }
                        .sortedBy { (it["timestamp"] as? Long) ?: 0L }
                    val lastMsg   = messages.lastOrNull()?.get("text")?.toString() ?: ""
                    sessions.add(mapOf(
                        "sessionId"    to sessionId,
                        "uid"          to uid,
                        "lastMessage"  to lastMsg,
                        "counselorKey" to counselorKey,
                        "expiresAt"    to (child.child("expiresAt").getValue(Long::class.java) ?: 0L),
                        "blocked"      to child.child("blocked").exists(),
                    ))
                }
                trySend(sessions)
            }
            override fun onCancelled(error: DatabaseError) { Log.w("RealtimeDBService", "RTDB listener cancelled", error.toException()); close() }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    fun listenToUserCounselorChats(uid: String): Flow<List<Map<String, Any>>> = callbackFlow {
        val ref = db.getReference("user_chats")
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val sessions = mutableListOf<Map<String, Any>>()
                for (child in snap.children) {
                    val sessionId = child.key ?: continue
                    if (!sessionId.startsWith("${uid}_")) continue
                    val counselorKey = sessionId.removePrefix("${uid}_")
                    val messages = child.child("messages").children
                        .mapNotNull { it.value as? Map<String, Any> }
                        .sortedBy { (it["timestamp"] as? Long) ?: 0L }
                    val last = messages.lastOrNull()
                    val counselorName = child.child("counselorName").getValue(String::class.java)
                        ?: child.child("assignedName").getValue(String::class.java)
                        ?: counselorKey
                    sessions.add(
                        mapOf(
                            "sessionId" to sessionId,
                            "uid" to uid,
                            "lastMessage" to (last?.get("text")?.toString() ?: ""),
                            "lastTimestamp" to ((last?.get("timestamp") as? Long) ?: 0L),
                            "counselorKey" to counselorKey,
                            "counselorName" to counselorName,
                            "expiresAt" to (child.child("expiresAt").getValue(Long::class.java) ?: 0L),
                            "blocked" to child.child("blocked").exists(),
                        )
                    )
                }
                trySend(sessions.sortedByDescending { it["lastTimestamp"] as? Long ?: 0L })
            }

            override fun onCancelled(error: DatabaseError) { Log.w("RealtimeDBService", "RTDB listener cancelled", error.toException()); close() }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    
    
    

    
    suspend fun saveCommunityPost(
        authorId: String,
        authorName: String,
        isAnonymous: Boolean,
        content: String,
        category: String
    ): Result<String> = runCatching {
        val postId = UUID.randomUUID().toString()
        db.getReference("community_posts").child(postId)
            .setValue(mapOf(
                "postId"        to postId,
                "authorId"      to authorId,
                "authorName"    to authorName,
                "isAnonymous"   to isAnonymous,
                "content"       to content,
                "category"      to category,
                "likesCount"    to 0,
                "repliesCount"  to 0,
                "isFlagged"     to false,
                "timestamp"     to System.currentTimeMillis()
            )).await()
        postId
    }

    
    fun listenToCommunityPosts(): Flow<List<Map<String, Any>>> = callbackFlow {
        val ref = db.getReference("community_posts")
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val posts = snap.children
                    .mapNotNull { it.value as? Map<String, Any> }
                    .filter { it["isFlagged"] as? Boolean != true }
                    .sortedByDescending { (it["timestamp"] as? Long) ?: 0L }
                trySend(posts)
            }
            override fun onCancelled(error: DatabaseError) { Log.w("RealtimeDBService", "RTDB listener cancelled", error.toException()); close() }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    
    suspend fun deleteCommunityPost(postId: String): Result<Unit> = runCatching {
        db.getReference("community_posts").child(postId).removeValue().await()
    }

    
    suspend fun flagCommunityPost(postId: String): Result<Unit> = runCatching {
        db.getReference("community_posts").child(postId).child("isFlagged").setValue(true).await()
    }

    
    suspend fun incrementPostLike(postId: String): Result<Unit> = runCatching {
        db.getReference("community_posts").child(postId)
            .updateChildren(mapOf("likesCount" to ServerValue.increment(1))).await()
    }

    
    suspend fun decrementPostLike(postId: String): Result<Unit> = runCatching {
        
        val ref = db.getReference("community_posts").child(postId).child("likesCount")
        val current = (ref.get().await().value as? Long)?.toInt() ?: 0
        if (current > 0) ref.setValue(current - 1).await()
    }

    
    
    

    
    suspend fun saveCommunityReply(
        postId: String,
        authorId: String,
        authorName: String,
        isAnonymous: Boolean,
        content: String
    ): Result<String> = runCatching {
        val replyId = UUID.randomUUID().toString()
        db.getReference("community_replies").child(postId).child(replyId)
            .setValue(mapOf(
                "replyId"     to replyId,
                "postId"      to postId,
                "authorId"    to authorId,
                "authorName"  to authorName,
                "isAnonymous" to isAnonymous,
                "content"     to content,
                "timestamp"   to System.currentTimeMillis()
            )).await()
        
        db.getReference("community_posts").child(postId)
            .updateChildren(mapOf("repliesCount" to ServerValue.increment(1))).await()
        replyId
    }

    
    fun listenToReplies(postId: String): Flow<List<Map<String, Any>>> = callbackFlow {
        val ref = db.getReference("community_replies").child(postId)
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val replies = snap.children
                    .mapNotNull { it.value as? Map<String, Any> }
                    .sortedBy { (it["timestamp"] as? Long) ?: 0L }
                trySend(replies)
            }
            override fun onCancelled(error: DatabaseError) { Log.w("RealtimeDBService", "RTDB listener cancelled", error.toException()); close() }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    
    
    
    
    
    
    
    

    
    suspend fun saveUserNotification(
        uid: String,
        titleEn: String, titleUr: String,
        bodyEn: String, bodyUr: String,
        type: String,
        actionRoute: String = ""
    ): Result<String> = runCatching {
        val notifId = UUID.randomUUID().toString()
        db.getReference("user_notifications").child(uid).child(notifId)
            .setValue(mapOf(
                "notifId"     to notifId,
                "titleEn"     to titleEn,
                "titleUr"     to titleUr,
                "bodyEn"      to bodyEn,
                "bodyUr"      to bodyUr,
                "type"        to type,
                "actionRoute" to actionRoute,
                "isRead"      to false,
                "timestamp"   to System.currentTimeMillis()
            )).await()
        notifId
    }

    
    fun listenToUserNotifications(uid: String): Flow<List<Map<String, Any>>> = callbackFlow {
        val ref = db.getReference("user_notifications").child(uid)
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val notifs = snap.children
                    .mapNotNull { it.value as? Map<String, Any> }
                    .sortedByDescending { (it["timestamp"] as? Long) ?: 0L }
                trySend(notifs)
            }
            override fun onCancelled(error: DatabaseError) { Log.w("RealtimeDBService", "RTDB listener cancelled", error.toException()); close() }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    
    suspend fun markNotificationRead(uid: String, notifId: String): Result<Unit> = runCatching {
        db.getReference("user_notifications").child(uid).child(notifId)
            .child("isRead").setValue(true).await()
    }

    
    suspend fun markAllNotificationsRead(uid: String): Result<Unit> = runCatching {
        val ref  = db.getReference("user_notifications").child(uid)
        val snap = ref.get().await()
        val updates = mutableMapOf<String, Any>()
        for (child in snap.children) {
            updates["${child.key}/isRead"] = true
        }
        if (updates.isNotEmpty()) ref.updateChildren(updates).await()
    }

    suspend fun saveDeviceToken(uid: String, token: String): Result<Unit> = runCatching {
        require(uid.isNotBlank()) { "Missing user id." }
        require(token.isNotBlank()) { "Missing device token." }
        db.getReference("device_tokens").child(uid).child(safeTokenKey(token)).setValue(
            mapOf(
                "token" to token,
                "platform" to "android",
                "updatedAt" to System.currentTimeMillis(),
            )
        ).await()
    }

    suspend fun getUserProfile(uid: String): Result<Map<String, Any>?> = getUser(uid)

    suspend fun getCounselorProfileByKey(key: String): Result<Map<String, Any>?> = getCounselorKey(key)

    fun listenChatIdentityVisible(userUid: String, counselorKey: String): Flow<Boolean> = callbackFlow {
        if (userUid.isBlank() || counselorKey.isBlank()) {
            trySend(false)
            close()
            return@callbackFlow
        }
        val ref = db.getReference("user_chats")
            .child(chatSessionPath(userUid, counselorKey))
            .child("privacy")
            .child("identityVisible")
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(Boolean::class.java) ?: false)
            }

            override fun onCancelled(error: DatabaseError) { Log.w("RealtimeDBService", "RTDB listener cancelled", error.toException()); close() }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun setChatIdentityVisible(userUid: String, counselorKey: String, visible: Boolean): Result<Unit> = runCatching {
        require(userUid.isNotBlank() && counselorKey.isNotBlank()) { "Missing chat identity." }
        val updates = mapOf(
            "identityVisible" to visible,
            "updatedAt" to System.currentTimeMillis(),
        )
        db.getReference("user_chats")
            .child(chatSessionPath(userUid, counselorKey))
            .child("privacy")
            .updateChildren(updates).await()
        db.getReference("users").child(userUid)
            .child("isIdentityVisibleToCounselors")
            .setValue(visible).await()
    }

    fun listenChatSessionMeta(userUid: String, counselorKey: String): Flow<Map<String, Any>> = callbackFlow {
        if (userUid.isBlank() || counselorKey.isBlank()) {
            trySend(emptyMap())
            close()
            return@callbackFlow
        }
        val ref = db.getReference("user_chats").child(chatSessionPath(userUid, counselorKey))
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val meta = mutableMapOf<String, Any>()
                snapshot.child("sessionStartedAt").getValue(Long::class.java)?.let { meta["sessionStartedAt"] = it }
                snapshot.child("expiresAt").getValue(Long::class.java)?.let { meta["expiresAt"] = it }
                snapshot.child("blocked").value?.let { meta["blocked"] = it }
                trySend(meta)
            }

            override fun onCancelled(error: DatabaseError) { Log.w("RealtimeDBService", "RTDB listener cancelled", error.toException()); close() }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun extendChatSession(
        userUid: String,
        counselorKey: String,
        hours: Long = 24L,
    ): Result<Long> = runCatching {
        require(userUid.isNotBlank() && counselorKey.isNotBlank()) { "Missing chat identity." }
        require(hours in 1L..168L) { "Extension must be between 1 and 168 hours." }
        val ref = db.getReference("user_chats").child(chatSessionPath(userUid, counselorKey))
        val now = System.currentTimeMillis()
        val currentExpiry = ref.child("expiresAt").get().await().getValue(Long::class.java) ?: now
        val newExpiry = maxOf(currentExpiry, now) + hours * 60L * 60L * 1000L
        ref.updateChildren(
            mapOf(
                "expiresAt" to newExpiry,
                "lastExtendedAt" to now,
            )
        ).await()
        newExpiry
    }

    suspend fun blockChatSession(
        blockedBy: String,
        userUid: String,
        counselorKey: String,
        reason: String = "Blocked from counselor chat",
    ): Result<Unit> = runCatching {
        require(blockedBy.isNotBlank() && userUid.isNotBlank() && counselorKey.isNotBlank()) {
            "Missing block target."
        }
        val now = System.currentTimeMillis()
        db.getReference("user_chats")
            .child(chatSessionPath(userUid, counselorKey))
            .child("blocked")
            .setValue(
                mapOf(
                    "blockedBy" to blockedBy,
                    "reason" to reason,
                    "blockedAt" to now,
                )
            ).await()
    }

    suspend fun submitAvatarRequest(
        userId: String,
        userEmail: String,
        userName: String,
        fileUri: Uri,
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
    ): Result<String> = runCatching {
        require(userId.isNotBlank()) { "Please sign in before uploading an avatar." }
        require(sizeBytes in 1..250_000L) { "Avatar must be under 250 KB." }
        require(mimeType in setOf("image/png", "image/jpeg", "image/webp")) {
            "Avatar must be PNG, JPEG, or WebP."
        }
        val requestId = UUID.randomUUID().toString()
        val fileUrl = uploadEvidence("avatar_requests/$userId/$requestId", fileUri)
        db.getReference("avatar_requests").child(requestId).setValue(
            mapOf(
                "requestId" to requestId,
                "userId" to userId,
                "userEmail" to userEmail.trim().lowercase(),
                "userName" to userName.trim(),
                "fileUrl" to fileUrl,
                "fileName" to fileName,
                "mimeType" to mimeType,
                "sizeBytes" to sizeBytes,
                "status" to "PENDING_REVIEW",
                "createdAt" to System.currentTimeMillis(),
            )
        ).await()
        requestId
    }

    fun listenToAvatarRequests(): Flow<List<AvatarRequest>> = callbackFlow {
        val ref = db.getReference("avatar_requests")
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.mapNotNull(::avatarRequestFrom).sortedByDescending { it.createdAt })
            }

            override fun onCancelled(error: DatabaseError) { Log.w("RealtimeDBService", "RTDB listener cancelled", error.toException()); close() }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun approveAvatarRequest(request: AvatarRequest, reviewedBy: String, comment: String): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        db.getReference("avatar_requests").child(request.requestId).updateChildren(
            mapOf(
                "status" to "APPROVED",
                "adminComment" to comment.trim(),
                "reviewedBy" to reviewedBy,
                "reviewedAt" to now,
            )
        ).await()
        db.getReference("users").child(request.userId).updateChildren(
            mapOf(
                "customAvatarUrl" to request.fileUrl,
                "customAvatarStatus" to "APPROVED",
                "customAvatarReviewedAt" to now,
            )
        ).await()
        saveUserNotification(
            uid = request.userId,
            titleEn = "Avatar approved",
            titleUr = "Avatar approve ho gaya",
            bodyEn = comment.ifBlank { "Your custom avatar is now visible on your profile." },
            bodyUr = comment.ifBlank { "Aapka custom avatar ab profile par nazar aayega." },
            type = "SYSTEM",
            actionRoute = "profile",
        ).getOrThrow()
    }

    suspend fun rejectAvatarRequest(request: AvatarRequest, reviewedBy: String, comment: String): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        db.getReference("avatar_requests").child(request.requestId).updateChildren(
            mapOf(
                "status" to "REJECTED",
                "adminComment" to comment.trim(),
                "reviewedBy" to reviewedBy,
                "reviewedAt" to now,
            )
        ).await()
        db.getReference("users").child(request.userId).updateChildren(
            mapOf(
                "customAvatarStatus" to "REJECTED",
                "customAvatarReviewedAt" to now,
            )
        ).await()
        saveUserNotification(
            uid = request.userId,
            titleEn = "Avatar not approved",
            titleUr = "Avatar approve nahi hua",
            bodyEn = comment.ifBlank { "Your custom avatar request was not approved." },
            bodyUr = comment.ifBlank { "Aapki custom avatar request approve nahi hui." },
            type = "SYSTEM",
            actionRoute = "profile",
        ).getOrThrow()
    }

    suspend fun blockUserIdentity(
        uid: String,
        email: String,
        reason: String,
        blockedBy: String,
    ): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        val payload = mapOf(
            "uid" to uid,
            "email" to email.trim().lowercase(),
            "reason" to reason.trim().ifBlank { "Blocked by admin." },
            "blockedBy" to blockedBy,
            "blockedAt" to now,
        )
        if (uid.isNotBlank()) {
            db.getReference("blocked_users").child(uid).setValue(payload).await()
            db.getReference("users").child(uid).updateChildren(
                mapOf("blocked" to true, "blockedReason" to payload["reason"].toString())
            ).await()
        }
        if (email.isNotBlank()) {
            db.getReference("blocked_emails").child(emailKey(email)).setValue(payload).await()
        }
    }

    suspend fun blockFromAvatarRequest(request: AvatarRequest, reviewedBy: String, comment: String): Result<Unit> = runCatching {
        rejectAvatarRequest(request, reviewedBy, comment.ifBlank { "Your account was blocked after avatar review." }).getOrThrow()
        blockUserIdentity(
            uid = request.userId,
            email = request.userEmail,
            reason = comment.ifBlank { "Blocked after avatar review." },
            blockedBy = reviewedBy,
        ).getOrThrow()
    }

    
    
    
    
    
    
    
    
    
    
    

    
    suspend fun saveSocialConnection(
        uid: String,
        platform: String,
        username: String
    ): Result<Unit> = runCatching {
        db.getReference("social_connections").child(uid).child(platform)
            .setValue(mapOf(
                "username"    to username,
                "connectedAt" to System.currentTimeMillis()
            )).await()
    }

    
    suspend fun saveVerifiedBlueskyConnection(
        uid: String,
        handle: String,
        did: String,
        consentVersion: String
    ): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        db.getReference("social_connections").child(uid).child("bluesky")
            .setValue(mapOf(
                "username"        to handle,
                "handle"          to handle,
                "did"             to did,
                "verified"        to true,
                "oauthScope"      to "atproto",
                "dataAccess"      to "public_posts_only",
                "consentVersion"  to consentVersion,
                "consentedAt"     to now,
                "analysisEnabled" to true,
                "connectedAt"     to now
            )).await()
    }

    
    suspend fun saveVerifiedSteamConnection(
        uid: String,
        steamId: String,
        displayName: String,
        consentVersion: String
    ): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        db.getReference("social_connections").child(uid).child("steam")
            .setValue(mapOf(
                "username"        to displayName.ifBlank { "Steam ID $steamId" },
                "steamId"         to steamId,
                "externalId"      to steamId,
                "verified"        to true,
                "authMethod"      to "openid_2_0",
                "dataAccess"      to "visible_profile_and_game_activity",
                "consentVersion"  to consentVersion,
                "consentedAt"     to now,
                "analysisEnabled" to true,
                "connectedAt"     to now
            )).await()
    }

    
    suspend fun saveVerifiedSpotifyConnection(
        uid: String,
        spotifyId: String,
        displayName: String,
        consentVersion: String
    ): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        db.getReference("social_connections").child(uid).child("spotify")
            .setValue(mapOf(
                "username"        to displayName.ifBlank { "Spotify ID $spotifyId" },
                "spotifyId"       to spotifyId,
                "externalId"      to spotifyId,
                "verified"        to true,
                "authMethod"      to "oauth2_pkce",
                "oauthScope"      to "user-read-private",
                "dataAccess"      to "identity_profile_only",
                "consentVersion"  to consentVersion,
                "consentedAt"     to now,
                "analysisEnabled" to false,
                "connectedAt"     to now
            )).await()
    }

    
    suspend fun saveVerifiedYouTubeConnection(
        uid: String,
        channelId: String,
        channelTitle: String,
        subscriptions: List<YouTubeSubscription>,
        subscriptionsTruncated: Boolean,
        consentVersion: String
    ): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        db.getReference("social_connections").child(uid).child("youtube")
            .setValue(mapOf(
                "username"        to channelTitle.ifBlank { "YouTube channel $channelId" },
                "channelId"       to channelId,
                "externalId"      to channelId,
                "verified"        to true,
                "authMethod"      to "oauth2_authorization_code",
                "oauthScope"      to "youtube.readonly",
                "dataAccess"      to "channel_identity_and_subscriptions",
                "authorizationRetained" to true,
                "consentVersion"  to consentVersion,
                "consentedAt"     to now,
                "analysisEnabled" to true,
                "connectedAt"     to now,
                "dataExpiresAt"   to now + YOUTUBE_API_DATA_RETENTION_MS,
                "subscriptionsRetrievedAt" to now,
                "subscriptionCount" to subscriptions.size,
                "subscriptionsTruncated" to subscriptionsTruncated,
                "subscriptions" to subscriptions.map { subscription ->
                    mapOf(
                        "channelId" to subscription.channelId,
                        "channelTitle" to subscription.channelTitle
                    )
                }
            )).await()
    }

    
    suspend fun removeSocialConnection(uid: String, platform: String): Result<Unit> = runCatching {
        db.getReference("social_connections").child(uid).child(platform).removeValue().await()
    }

    
    fun listenToSocialConnections(uid: String): Flow<Map<String, SocialPlatformConnection>> = callbackFlow {
        val ref = db.getReference("social_connections").child(uid)
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val map = mutableMapOf<String, SocialPlatformConnection>()
                for (child in snap.children) {
                    val platform = child.key ?: continue
                    val username = child.child("username").getValue(String::class.java) ?: continue
                    val dataExpiresAt = child.child("dataExpiresAt").getValue(Long::class.java) ?: 0L
                    if (
                        platform == "youtube" &&
                        dataExpiresAt > 0L &&
                        dataExpiresAt <= System.currentTimeMillis()
                    ) {
                        
                        
                        child.ref.removeValue()
                        continue
                    }
                    map[platform] = SocialPlatformConnection(
                        platform = platform,
                        username = username,
                        did = child.child("did").getValue(String::class.java).orEmpty(),
                        externalId = child.child("externalId").getValue(String::class.java).orEmpty(),
                        authMethod = child.child("authMethod").getValue(String::class.java).orEmpty(),
                        dataAccess = child.child("dataAccess").getValue(String::class.java).orEmpty(),
                        verified = child.child("verified").getValue(Boolean::class.java) ?: false,
                        consentVersion = child.child("consentVersion").getValue(String::class.java).orEmpty(),
                        consentedAt = child.child("consentedAt").getValue(Long::class.java) ?: 0L,
                        connectedAt = child.child("connectedAt").getValue(Long::class.java) ?: 0L,
                        dataExpiresAt = dataExpiresAt,
                        subscriptionCount = (child.child("subscriptionCount")
                            .getValue(Long::class.java) ?: 0L).toInt(),
                        analysisEnabled = child.child("analysisEnabled").getValue(Boolean::class.java) ?: false
                    )
                }
                trySend(map)
            }
            override fun onCancelled(error: DatabaseError) { Log.w("RealtimeDBService", "RTDB listener cancelled", error.toException()); close() }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    
    
    
    
    
    
    
    
    
    
    
    

    private fun hoursToQuality(h: Float) =
        when { h >= 8f -> "excellent"; h >= 7f -> "good"; h >= 6f -> "okay"; else -> "poor" }

    
    suspend fun saveSleepLog(
        uid: String,
        date: String,      
        bedtime: String,   
        waketime: String,  
        hours: Float,
        source: String = "self_reported",
        timeZoneId: String = java.util.TimeZone.getDefault().id,
        automatic: Boolean = false,
        confidence: Float? = null,
        sourceReason: String = ""
    ): Result<Unit> = runCatching {
        val values = mutableMapOf<String, Any>(
            "bedtime"     to bedtime,
            "waketime"    to waketime,
            "hours"       to hours,
            "qualityType" to hoursToQuality(hours),
            "source"      to source,
            "timeZoneId"  to timeZoneId,
            "automatic"   to automatic,
            "sourceReason" to sourceReason,
            "timestamp"   to System.currentTimeMillis()
        )
        confidence?.let { values["confidence"] = it }
        db.getReference("sleep").child(uid).child(date)
            .setValue(values).await()
    }

    suspend fun loadSleepLog(uid: String, date: String): Map<String, Any>? = runCatching {
        @Suppress("UNCHECKED_CAST")
        db.getReference("sleep").child(uid).child(date).get().await().value as? Map<String, Any>
    }.getOrNull()

    suspend fun loadMeasuredSleepAverage(uid: String, beforeDate: String): Float? = runCatching {
        val snapshot = db.getReference("sleep").child(uid)
            .orderByKey()
            .endBefore(beforeDate)
            .limitToLast(182)
            .get()
            .await()
        val measuredSources = setOf("self_reported", "health_connect", "actigraphy")
        val hours = snapshot.children.mapNotNull { child ->
            val source = child.child("source").getValue(String::class.java) ?: "self_reported"
            val value = child.child("hours").getValue(Double::class.java)?.toFloat()
            value?.takeIf { source in measuredSources && it in 0.1f..24f }
        }
        hours.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    }.getOrNull()

    
    fun listenToWeekSleepLogs(
        uid: String,
        mondayDate: String,
        sundayDate: String
    ): Flow<Map<String, Map<String, Any>>> = callbackFlow {
        val ref = db.getReference("sleep").child(uid)
            .orderByKey().startAt(mondayDate).endAt(sundayDate)
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val map = mutableMapOf<String, Map<String, Any>>()
                for (child in snap.children) {
                    val date = child.key ?: continue
                    val entry = mutableMapOf<String, Any>()
                    child.child("bedtime").getValue(String::class.java)?.let  { entry["bedtime"]     = it }
                    child.child("waketime").getValue(String::class.java)?.let { entry["waketime"]    = it }
                    child.child("hours").getValue(Double::class.java)?.let    { entry["hours"]       = it.toFloat() }
                    child.child("qualityType").getValue(String::class.java)?.let { entry["qualityType"] = it }
                    child.child("source").getValue(String::class.java)?.let { entry["source"] = it }
                    child.child("timeZoneId").getValue(String::class.java)?.let { entry["timeZoneId"] = it }
                    child.child("automatic").getValue(Boolean::class.java)?.let { entry["automatic"] = it }
                    child.child("confidence").getValue(Double::class.java)?.let { entry["confidence"] = it.toFloat() }
                    child.child("sourceReason").getValue(String::class.java)?.let { entry["sourceReason"] = it }
                    if (entry.isNotEmpty()) map[date] = entry
                }
                trySend(map)
            }
            override fun onCancelled(error: DatabaseError) { Log.w("RealtimeDBService", "RTDB listener cancelled", error.toException()); close() }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    
    suspend fun saveSleepGoal(uid: String, goal: Float): Result<Unit> = runCatching {
        db.getReference("sleep_goals").child(uid).child("goal").setValue(goal).await()
    }

    
    suspend fun loadSleepGoal(uid: String): Float = runCatching {
        (db.getReference("sleep_goals").child(uid).child("goal")
            .get().await().getValue(Double::class.java) ?: 8.0).toFloat()
    }.getOrDefault(8f)

    
    
    
    
    
    
    
    
    

    
    suspend fun saveMoodLog(
        uid: String,
        mood: String,
        emoji: String,
        labelEn: String,
        date: String        
    ): Result<Unit> = runCatching {
        db.getReference("mood_logs").child(uid).child(date)
            .setValue(mapOf(
                "mood"      to mood,
                "emoji"     to emoji,
                "labelEn"   to labelEn,
                "timestamp" to System.currentTimeMillis()
            )).await()
    }

    
    fun listenToMoodLogs(uid: String, limit: Int = 50): Flow<List<Map<String, Any>>> = callbackFlow {
        val ref = db.getReference("mood_logs").child(uid)
            .orderByKey().limitToLast(limit)
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val list = mutableListOf<Map<String, Any>>()
                for (child in snap.children) {
                    val date = child.key ?: continue
                    val entry = mutableMapOf<String, Any>()
                    entry["id"]   = date      
                    entry["date"] = date
                    child.child("mood").getValue(String::class.java)?.let      { entry["mood"]      = it }
                    child.child("emoji").getValue(String::class.java)?.let     { entry["emoji"]     = it }
                    child.child("labelEn").getValue(String::class.java)?.let   { entry["labelEn"]   = it }
                    child.child("timestamp").getValue(Long::class.java)?.let   { entry["timestamp"] = it }
                    if (entry.size > 2) list.add(entry)   
                }
                trySend(list.reversed())   
            }
            override fun onCancelled(error: DatabaseError) { Log.w("RealtimeDBService", "RTDB listener cancelled", error.toException()); close() }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    
    
    

    
    fun listenToAllCounselorKeys(): Flow<List<Map<String, Any>>> = callbackFlow {
        val ref = db.getReference("counselor_keys")
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val list = snap.children.mapNotNull { child ->
                    val data = child.value as? Map<String, Any> ?: return@mapNotNull null
                    val complete = data["profileComplete"] as? Boolean ?: false
                    if (!complete) return@mapNotNull null
                    data + mapOf("key" to (child.key ?: ""))
                }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) { Log.w("RealtimeDBService", "RTDB listener cancelled", error.toException()); close() }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    
    fun listenToUserCount(): Flow<Int> = callbackFlow {
        val ref = db.getReference("users")
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) = trySend(snap.childrenCount.toInt()).let {}
            override fun onCancelled(error: DatabaseError) { Log.w("RealtimeDBService", "RTDB listener cancelled", error.toException()); close() }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    
    fun listenToChatSessionCount(): Flow<Int> = callbackFlow {
        val ref = db.getReference("user_chats")
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) = trySend(snap.childrenCount.toInt()).let {}
            override fun onCancelled(error: DatabaseError) { Log.w("RealtimeDBService", "RTDB listener cancelled", error.toException()); close() }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    
    
    
    
    
    
    
    
    
    
    
    
    
    

    
    suspend fun saveAssessment(
        uid: String,
        quizType: String,
        score: Int,
        riskLevel: String,
        date: String,
        answers: Map<Int, Triple<String, String, Boolean>>   
    ): Result<Unit> = runCatching {
        val ref = db.getReference("assessment").child(uid).push()

        
        val answersMap = answers.entries.associate { (idx, triple) ->
            idx.toString() to mapOf(
                "questionEn" to triple.first,
                "questionUr" to triple.second,
                "answer"     to triple.third   
            )
        }

        ref.setValue(
            mapOf(
                "quizType"       to quizType,
                "score"          to score,
                "totalQuestions" to answers.size,
                "riskLevel"      to riskLevel,
                "date"           to date,
                "timestamp"      to System.currentTimeMillis(),
                "answers"        to answersMap
            )
        ).await()
    }

    
    suspend fun loadLatestAssessment(uid: String): Map<String, Any>? = runCatching {
        val snap = db.getReference("assessment").child(uid)
            .orderByChild("timestamp").limitToLast(1).get().await()
        snap.children.lastOrNull()?.let { child ->
            val entry = mutableMapOf<String, Any>()
            entry["id"] = child.key ?: ""
            child.child("score").getValue(Long::class.java)?.let        { entry["score"]     = it.toInt() }
            child.child("quizType").getValue(String::class.java)?.let   { entry["quizType"]  = it }
            child.child("riskLevel").getValue(String::class.java)?.let  { entry["riskLevel"] = it }
            child.child("date").getValue(String::class.java)?.let       { entry["date"]      = it }
            child.child("timestamp").getValue(Long::class.java)?.let    { entry["timestamp"] = it }
            
            if (entry.containsKey("score")) entry else null
        }
    }.getOrNull()

    
    fun listenToAssessments(uid: String): Flow<List<Map<String, Any>>> = callbackFlow {
        val ref = db.getReference("assessment").child(uid)
            .orderByChild("timestamp")
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val list = mutableListOf<Map<String, Any>>()
                for (child in snap.children) {
                    val entry = mutableMapOf<String, Any>()
                    entry["id"] = child.key ?: continue
                    child.child("quizType").getValue(String::class.java)?.let       { entry["quizType"]       = it }
                    child.child("score").getValue(Long::class.java)?.let            { entry["score"]          = it.toInt() }
                    child.child("totalQuestions").getValue(Long::class.java)?.let   { entry["totalQuestions"] = it.toInt() }
                    child.child("riskLevel").getValue(String::class.java)?.let      { entry["riskLevel"]      = it }
                    child.child("date").getValue(String::class.java)?.let           { entry["date"]           = it }
                    child.child("timestamp").getValue(Long::class.java)?.let        { entry["timestamp"]      = it }
                    
                    val answersMap = mutableMapOf<String, Map<String, Any>>()
                    for (aq in child.child("answers").children) {
                        val aIdx = aq.key ?: continue
                        val aEntry = mutableMapOf<String, Any>()
                        aq.child("questionEn").getValue(String::class.java)?.let { aEntry["questionEn"] = it }
                        aq.child("questionUr").getValue(String::class.java)?.let { aEntry["questionUr"] = it }
                        aq.child("answer").getValue(Boolean::class.java)?.let    { aEntry["answer"]     = it }
                        if (aEntry.isNotEmpty()) answersMap[aIdx] = aEntry
                    }
                    if (answersMap.isNotEmpty()) entry["answers"] = answersMap
                    if (entry.containsKey("quizType")) list.add(entry)
                }
                trySend(list.reversed())   
            }
            override fun onCancelled(error: DatabaseError) { Log.w("RealtimeDBService", "RTDB listener cancelled", error.toException()); close() }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    
    
    
    
    
    
    
    
    
    
    
    

    
    suspend fun saveScreenTime(
        uid: String,
        date: String,
        apps: List<Triple<String, String, Long>>   
    ): Result<Unit> = runCatching {
        val appsMap = apps.associate { (pkg, name, ms) ->
            
            pkg.replace(".", "_") to mapOf(
                "packageName" to pkg,
                "appName"     to name,
                "timeMs"      to ms
            )
        }
        val totalMs = apps.sumOf { it.third }

        db.getReference("screen_time").child(uid).child(date)
            .setValue(
                mapOf(
                    "totalMs" to totalMs,
                    "savedAt" to System.currentTimeMillis(),
                    "apps"    to appsMap
                )
            ).await()
    }

    
    suspend fun loadScreenTime(uid: String, date: String): Map<String, Any>? = runCatching {
        db.getReference("screen_time").child(uid).child(date)
            .get().await().value as? Map<String, Any>
    }.getOrNull()

    
    fun listenToScreenTime(uid: String, limit: Int = 30): Flow<List<Map<String, Any>>> = callbackFlow {
        val ref = db.getReference("screen_time").child(uid)
            .orderByKey().limitToLast(limit)
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val list = mutableListOf<Map<String, Any>>()
                for (child in snap.children) {
                    val date  = child.key ?: continue
                    val entry = mutableMapOf<String, Any>()
                    entry["date"] = date
                    child.child("totalMs").getValue(Long::class.java)?.let  { entry["totalMs"] = it }
                    child.child("savedAt").getValue(Long::class.java)?.let  { entry["savedAt"] = it }
                    
                    val appsMap = mutableMapOf<String, Map<String, Any>>()
                    for (app in child.child("apps").children) {
                        val pkg   = app.key ?: continue
                        val aData = mutableMapOf<String, Any>()
                        app.child("packageName").getValue(String::class.java)?.let { aData["packageName"] = it }
                        app.child("appName").getValue(String::class.java)?.let     { aData["appName"]     = it }
                        app.child("timeMs").getValue(Long::class.java)?.let        { aData["timeMs"]      = it }
                        if (aData.isNotEmpty()) appsMap[pkg] = aData
                    }
                    if (appsMap.isNotEmpty()) entry["apps"] = appsMap
                    if (entry.containsKey("totalMs")) list.add(entry)
                }
                trySend(list.reversed())   
            }
            override fun onCancelled(error: DatabaseError) { Log.w("RealtimeDBService", "RTDB listener cancelled", error.toException()); close() }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    
    
    
    
    
    
    
    
    
    
    
    

    
    suspend fun updateDailyCheckIn(uid: String, today: String): Result<Map<String, Any>> = runCatching {
        val ref  = db.getReference("progress").child(uid)
        val snap = ref.get().await()

        val lastCheckIn    = snap.child("lastCheckIn").getValue(String::class.java) ?: ""
        val currentStreak  = snap.child("streak").getValue(Long::class.java)         ?: 0L
        val longestStreak  = snap.child("longestStreak").getValue(Long::class.java)   ?: 0L
        val totalDays      = snap.child("totalDays").getValue(Long::class.java)       ?: 0L

        
        if (lastCheckIn == today) {
            return@runCatching mapOf(
                "streak"        to currentStreak,
                "longestStreak" to longestStreak,
                "totalDays"     to totalDays,
                "lastCheckIn"   to lastCheckIn
            )
        }

        
        val sdf       = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val todayDate = sdf.parse(today)!!
        val newStreak: Long = if (lastCheckIn.isNotBlank()) {
            val lastDate  = sdf.parse(lastCheckIn)!!
            val diffDays  = ((todayDate.time - lastDate.time) / 86_400_000L)
            when (diffDays) {
                1L   -> currentStreak + 1   
                else -> 1L                  
            }
        } else 1L   

        val newLongest   = maxOf(longestStreak, newStreak)
        val newTotalDays = totalDays + 1

        val update = mapOf(
            "streak"        to newStreak,
            "longestStreak" to newLongest,
            "totalDays"     to newTotalDays,
            "lastCheckIn"   to today
        )
        ref.updateChildren(update).await()
        update
    }

    
    suspend fun loadProgressData(uid: String): Map<String, Any>? = runCatching {
        val snap = db.getReference("progress").child(uid).get().await()
        if (!snap.exists()) return@runCatching null

        val result = mutableMapOf<String, Any>()
        snap.child("streak").getValue(Long::class.java)?.let        { result["streak"]        = it }
        snap.child("longestStreak").getValue(Long::class.java)?.let { result["longestStreak"] = it }
        snap.child("totalDays").getValue(Long::class.java)?.let     { result["totalDays"]     = it }
        snap.child("lastCheckIn").getValue(String::class.java)?.let { result["lastCheckIn"]   = it }

        
        val achMap = mutableMapOf<String, Long>()
        for (child in snap.child("unlockedAchievements").children) {
            val id = child.key ?: continue
            val ts = child.getValue(Long::class.java) ?: 0L
            achMap[id] = ts
        }
        if (achMap.isNotEmpty()) result["unlockedAchievements"] = achMap

        result
    }.getOrNull()

    
    suspend fun unlockAchievement(uid: String, achievementId: String): Result<Unit> = runCatching {
        val ref  = db.getReference("progress").child(uid)
            .child("unlockedAchievements").child(achievementId)
        
        val snap = ref.get().await()
        if (!snap.exists()) ref.setValue(System.currentTimeMillis()).await()
    }

    
    suspend fun loadAssessmentHistory(uid: String, limit: Int = 6): List<Map<String, Any>> = runCatching {
        val snap = db.getReference("assessment").child(uid)
            .orderByChild("timestamp").limitToLast(limit).get().await()
        val list = mutableListOf<Map<String, Any>>()
        for (child in snap.children) {
            val entry = mutableMapOf<String, Any>()
            child.child("date").getValue(String::class.java)?.let      { entry["date"]      = it }
            child.child("score").getValue(Long::class.java)?.let       { entry["score"]     = it.toInt() }
            child.child("riskLevel").getValue(String::class.java)?.let { entry["riskLevel"] = it }
            child.child("quizType").getValue(String::class.java)?.let  { entry["quizType"]  = it }
            child.child("timestamp").getValue(Long::class.java)?.let   { entry["timestamp"] = it }
            if (entry.containsKey("score")) list.add(entry)
        }
        list.reversed()   
    }.getOrDefault(emptyList())

    
    
    

    
    suspend fun updateLeaderboardEntry(
        uid          : String,
        displayName  : String,
        streak       : Int,
        longestStreak: Int,
        totalDays    : Int
    ): Result<Unit> = runCatching {
        db.getReference("leaderboard").child(uid).updateChildren(
            mapOf(
                "displayName"   to displayName,
                "streak"        to streak.toLong(),
                "longestStreak" to longestStreak.toLong(),
                "totalDays"     to totalDays.toLong(),
                "lastUpdated"   to System.currentTimeMillis()
            )
        ).await()
    }

    
    suspend fun loadLeaderboard(limit: Int = 50): List<Map<String, Any>> = runCatching {
        val snap = db.getReference("leaderboard")
            .orderByChild("streak")
            .limitToLast(limit)
            .get().await()

        val list = mutableListOf<Map<String, Any>>()
        for (child in snap.children) {
            val entry = mutableMapOf<String, Any>()
            entry["uid"]           = child.key ?: continue
            child.child("displayName").getValue(String::class.java)?.let  { entry["displayName"]   = it }
            child.child("streak").getValue(Long::class.java)?.let         { entry["streak"]        = it }
            child.child("longestStreak").getValue(Long::class.java)?.let  { entry["longestStreak"] = it }
            child.child("totalDays").getValue(Long::class.java)?.let      { entry["totalDays"]     = it }
            child.child("lastUpdated").getValue(Long::class.java)?.let    { entry["lastUpdated"]   = it }
            if (entry.containsKey("displayName")) list.add(entry)
        }
        list.reversed()   
    }.getOrDefault(emptyList())

    
    suspend fun loadLeaderboardByDays(limit: Int = 50): List<Map<String, Any>> = runCatching {
        val snap = db.getReference("leaderboard")
            .orderByChild("totalDays")
            .limitToLast(limit)
            .get().await()

        val list = mutableListOf<Map<String, Any>>()
        for (child in snap.children) {
            val entry = mutableMapOf<String, Any>()
            entry["uid"]           = child.key ?: continue
            child.child("displayName").getValue(String::class.java)?.let  { entry["displayName"]   = it }
            child.child("streak").getValue(Long::class.java)?.let         { entry["streak"]        = it }
            child.child("longestStreak").getValue(Long::class.java)?.let  { entry["longestStreak"] = it }
            child.child("totalDays").getValue(Long::class.java)?.let      { entry["totalDays"]     = it }
            child.child("lastUpdated").getValue(Long::class.java)?.let    { entry["lastUpdated"]   = it }
            if (entry.containsKey("displayName")) list.add(entry)
        }
        list.reversed()   
    }.getOrDefault(emptyList())

    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    

    
    suspend fun saveGameAlias(uid: String, alias: String, location: String): Result<Unit> = runCatching {
        val ref  = db.getReference("game_recovery").child(uid)
        val snap = ref.get().await()
        if (!snap.exists()) {
            ref.setValue(mapOf(
                "alias"          to alias,
                "location"       to location,
                "totalXp"        to 0L,
                "dailyXp"        to 0L,
                "weeklyXp"       to 0L,
                "level"          to 1L,
                "lastDailyDate"  to "",
                "lastWeeklyDate" to ""
            )).await()
        } else {
            ref.updateChildren(mapOf("alias" to alias, "location" to location)).await()
        }
        db.getReference("game_leaderboard").child(uid).updateChildren(mapOf(
            "alias"       to alias,
            "location"    to location,
            "totalXp"     to (snap.child("totalXp").getValue(Long::class.java)  ?: 0L),
            "dailyXp"     to (snap.child("dailyXp").getValue(Long::class.java)  ?: 0L),
            "weeklyXp"    to (snap.child("weeklyXp").getValue(Long::class.java) ?: 0L),
            "lastDailyDate" to snap.child("lastDailyDate").getValue(String::class.java).orEmpty(),
            "lastWeeklyDate" to snap.child("lastWeeklyDate").getValue(String::class.java).orEmpty(),
            "lastUpdated" to System.currentTimeMillis()
        )).await()
    }

    
    suspend fun loadGameProfile(uid: String, today: String, thisWeek: String): Map<String, Any>? = runCatching {
        val ref  = db.getReference("game_recovery").child(uid)
        val snap = ref.get().await()
        if (!snap.exists()) return@runCatching null

        val alias      = snap.child("alias").getValue(String::class.java)     ?: ""
        val location   = snap.child("location").getValue(String::class.java)  ?: ""
        var totalXp    = snap.child("totalXp").getValue(Long::class.java)     ?: 0L
        var dailyXp    = snap.child("dailyXp").getValue(Long::class.java)     ?: 0L
        var weeklyXp   = snap.child("weeklyXp").getValue(Long::class.java)    ?: 0L
        val level      = snap.child("level").getValue(Long::class.java)       ?: 1L
        val lastDaily  = snap.child("lastDailyDate").getValue(String::class.java)  ?: ""
        val lastWeekly = snap.child("lastWeeklyDate").getValue(String::class.java) ?: ""

        val resets = mutableMapOf<String, Any>()
        if (lastDaily != today) {
            dailyXp = 0L
            resets["dailyXp"]       = 0L
            resets["lastDailyDate"] = today
        }
        if (lastWeekly != thisWeek) {
            weeklyXp = 0L
            resets["weeklyXp"]       = 0L
            resets["lastWeeklyDate"] = thisWeek
        }
        if (resets.isNotEmpty()) {
            ref.updateChildren(resets).await()
            val lbResets = resets.filterKeys { it == "dailyXp" || it == "weeklyXp" }
            if (lbResets.isNotEmpty()) {
                db.getReference("game_leaderboard").child(uid).updateChildren(
                    lbResets + mapOf(
                        "lastDailyDate" to if (lastDaily != today) today else lastDaily,
                        "lastWeeklyDate" to if (lastWeekly != thisWeek) thisWeek else lastWeekly,
                    )
                ).await()
            }
        }

        val completedToday = mutableSetOf<String>()
        for (child in snap.child("completedTasks").child(today).children)
            child.key?.let { completedToday.add(it) }

        mapOf(
            "alias"          to alias,
            "location"       to location,
            "totalXp"        to totalXp,
            "dailyXp"        to dailyXp,
            "weeklyXp"       to weeklyXp,
            "level"          to level,
            "completedToday" to completedToday
        )
    }.getOrNull()

    
    suspend fun completeGameTask(
        uid      : String,
        taskId   : String,
        xpReward : Int,
        today    : String,
        thisWeek : String
    ): Result<Map<String, Any>> = runCatching {
        val ref  = db.getReference("game_recovery").child(uid)
        val snap = ref.get().await()

        
        if (snap.child("completedTasks").child(today).child(taskId).exists()) {
            val done = mutableSetOf<String>()
            for (c in snap.child("completedTasks").child(today).children) c.key?.let { done.add(it) }
            return@runCatching mapOf(
                "totalXp"        to (snap.child("totalXp").getValue(Long::class.java)  ?: 0L),
                "dailyXp"        to (snap.child("dailyXp").getValue(Long::class.java)  ?: 0L),
                "weeklyXp"       to (snap.child("weeklyXp").getValue(Long::class.java) ?: 0L),
                "level"          to (snap.child("level").getValue(Long::class.java)    ?: 1L),
                "completedToday" to done
            )
        }

        val newTotal  = (snap.child("totalXp").getValue(Long::class.java)  ?: 0L) + xpReward
        val newDaily  = (snap.child("dailyXp").getValue(Long::class.java)  ?: 0L) + xpReward
        val newWeekly = (snap.child("weeklyXp").getValue(Long::class.java) ?: 0L) + xpReward
        val newLevel  = (newTotal / 200L) + 1L
        val alias     = snap.child("alias").getValue(String::class.java)    ?: ""
        val location  = snap.child("location").getValue(String::class.java) ?: ""
        val now       = System.currentTimeMillis()

        ref.updateChildren(mapOf(
            "totalXp"                       to newTotal,
            "dailyXp"                       to newDaily,
            "weeklyXp"                      to newWeekly,
            "level"                         to newLevel,
            "lastDailyDate"                 to today,
            "lastWeeklyDate"                to thisWeek,
            "completedTasks/$today/$taskId" to now
        )).await()

        db.getReference("game_leaderboard").child(uid).updateChildren(mapOf(
            "alias"       to alias,
            "location"    to location,
            "totalXp"     to newTotal,
            "dailyXp"     to newDaily,
            "weeklyXp"    to newWeekly,
            "lastDailyDate" to today,
            "lastWeeklyDate" to thisWeek,
            "lastUpdated" to now
        )).await()

        val done = mutableSetOf<String>()
        for (c in snap.child("completedTasks").child(today).children) c.key?.let { done.add(it) }
        done.add(taskId)

        mapOf(
            "totalXp"        to newTotal,
            "dailyXp"        to newDaily,
            "weeklyXp"       to newWeekly,
            "level"          to newLevel,
            "completedToday" to done
        )
    }

    

    suspend fun logAppUsageEvent(
        uid: String,
        packageName: String,
        appName: String,
        startTimeMillis: Long,
        endTimeMillis: Long
    ): Result<Unit> = runCatching {
        val eventId = UUID.randomUUID().toString()
        val durationMs = if (endTimeMillis > startTimeMillis) {
            endTimeMillis - startTimeMillis
        } else {
            0L
        }
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date(startTimeMillis))

        db.getReference("app_usage_events").child(uid).child(eventId)
            .setValue(
                mapOf(
                    "packageName" to packageName,
                    "appName" to appName,
                    "startTimeMillis" to startTimeMillis,
                    "endTimeMillis" to endTimeMillis,
                    "durationMillis" to durationMs,
                    "date" to date
                )
            ).await()
    }

    fun getTodayAppUsageEvents(uid: String): Flow<List<Map<String, Any>>> = callbackFlow {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis

        val ref = db.getReference("app_usage_events").child(uid)
            .orderByChild("startTimeMillis").startAt(startOfDay.toDouble())

        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val events = mutableListOf<Map<String, Any>>()
                for (child in snapshot.children) {
                    val entry = mutableMapOf<String, Any>()
                    entry["id"] = child.key ?: continue
                    child.child("packageName").getValue(String::class.java)?.let {
                        entry["packageName"] = it
                    }
                    child.child("appName").getValue(String::class.java)?.let {
                        entry["appName"] = it
                    }
                    child.child("startTimeMillis").getValue(Long::class.java)?.let {
                        entry["startTimeMillis"] = it
                    }
                    child.child("endTimeMillis").getValue(Long::class.java)?.let {
                        entry["endTimeMillis"] = it
                    }
                    child.child("durationMillis").getValue(Long::class.java)?.let {
                        entry["durationMillis"] = it
                    }
                    child.child("date").getValue(String::class.java)?.let {
                        entry["date"] = it
                    }
                    if (entry.containsKey("packageName")) events.add(entry)
                }
                trySend(events.sortedByDescending { (it["startTimeMillis"] as? Long) ?: 0L })
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e(
                    "RealtimeDBService",
                    "getTodayAppUsageEvents cancelled",
                    error.toException()
                )
                trySend(emptyList())
            }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    
    suspend fun loadGameLeaderboard(
        sortBy: String = "allTime",
        limit: Int = 20,
        today: String = "",
        thisWeek: String = ""
    ): List<Map<String, Any>> = runCatching {
        val field = when (sortBy) {
            "daily"  -> "dailyXp"
            "weekly" -> "weeklyXp"
            else     -> "totalXp"
        }
        val snap = db.getReference("game_leaderboard")
            .orderByChild(field)
            .limitToLast(if (sortBy == "allTime") limit else maxOf(limit * 5, 100))
            .get().await()

        val list = mutableListOf<Map<String, Any>>()
        for (child in snap.children) {
            val lastDailyDate = child.child("lastDailyDate").getValue(String::class.java).orEmpty()
            val lastWeeklyDate = child.child("lastWeeklyDate").getValue(String::class.java).orEmpty()
            val isCurrentWindow = when (sortBy) {
                "daily" -> today.isNotBlank() && lastDailyDate == today
                "weekly" -> thisWeek.isNotBlank() && lastWeeklyDate == thisWeek
                else -> true
            }
            if (!isCurrentWindow) continue
            val entry = mutableMapOf<String, Any>()
            entry["uid"] = child.key ?: continue
            child.child("alias").getValue(String::class.java)?.let    { entry["alias"]    = it }
            child.child("location").getValue(String::class.java)?.let { entry["location"] = it }
            child.child("totalXp").getValue(Long::class.java)?.let    { entry["totalXp"]  = it }
            child.child("dailyXp").getValue(Long::class.java)?.let    { entry["dailyXp"]  = it }
            child.child("weeklyXp").getValue(Long::class.java)?.let   { entry["weeklyXp"] = it }
            entry["lastDailyDate"] = lastDailyDate
            entry["lastWeeklyDate"] = lastWeeklyDate
            if (entry.containsKey("alias")) list.add(entry)
        }
        list.reversed().take(limit)
    }.getOrDefault(emptyList())

    
    
    

    suspend fun submitRegistrationRequest(
        applicantType: String,
        applicantName: String,
        organizationName: String,
        email: String,
        phone: String,
        region: String,
        city: String,
        district: String,
        locationAccuracyMeters: Float,
        verificationBody: String,
        registrationNumber: String,
        qualificationSummary: String,
        details: String,
        documentUris: Map<String, Uri>,
        requiredDocumentKeys: List<String>
    ): Result<String> = runCatching {
        val requestId = UUID.randomUUID().toString()
        require(documentUris.isNotEmpty()) { "At least one verification document is required." }
        val documentUrls = documentUris.mapValues { (key, uri) ->
            uploadEvidence("application_documents/$requestId/$key", uri)
        }
        val documentUrl = documentUrls.values.firstOrNull().orEmpty()
        db.getReference("registration_requests").child(requestId).setValue(
            mapOf(
                "requestId" to requestId,
                "applicantType" to applicantType,
                "applicantName" to applicantName,
                "organizationName" to organizationName,
                "email" to email,
                "phone" to phone,
                "region" to region,
                "city" to city,
                "district" to district,
                "locationAccuracyMeters" to locationAccuracyMeters,
                "verificationBody" to verificationBody,
                "registrationNumber" to registrationNumber,
                "qualificationSummary" to qualificationSummary,
                "details" to details,
                "documentUrl" to documentUrl,
                "documentUrls" to documentUrls,
                "requiredDocumentKeys" to requiredDocumentKeys,
                "status" to "PENDING_REVIEW",
                "createdAt" to System.currentTimeMillis()
            )
        ).await()
        requestId
    }

    fun listenToRegistrationRequests(): Flow<List<RegistrationRequest>> = callbackFlow {
        val ref = db.getReference("registration_requests")
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.mapNotNull(::registrationRequestFrom).sortedByDescending { it.createdAt })
            }

            override fun onCancelled(error: DatabaseError) { Log.w("RealtimeDBService", "RTDB listener cancelled", error.toException()); close() }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun submitBugReport(
        userId: String,
        email: String,
        deviceModel: String,
        description: String,
        screenshotUri: Uri,
    ): Result<String> = runCatching {
        require(userId.isNotBlank()) { "Please sign in before reporting a bug." }
        require(deviceModel.isNotBlank()) { "Device model is required." }
        val dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val counterRef = db.getReference("bug_report_daily_counts").child(userId).child(dayKey)
        reserveBugReportSlot(counterRef)

        val reportId = UUID.randomUUID().toString()
        try {
            val screenshotUrl = uploadEvidence("bug_reports/$userId/$reportId", screenshotUri)
            db.getReference("bug_reports").child(reportId).setValue(
                mapOf(
                    "reportId" to reportId,
                    "userId" to userId,
                    "maskedEmail" to maskEmail(email),
                    "deviceModel" to deviceModel.trim(),
                    "screenshotUrl" to screenshotUrl,
                    "description" to description.trim(),
                    "status" to "OPEN",
                    "createdAt" to System.currentTimeMillis(),
                    "dayKey" to dayKey,
                )
            ).await()
        } catch (cause: Throwable) {
            releaseBugReportSlot(counterRef)
            throw cause
        }
        reportId
    }

    fun listenToUserBugReports(userId: String): Flow<List<BugReport>> =
        bugReportsFlow { it.userId == userId }

    fun listenToBugReports(): Flow<List<BugReport>> = bugReportsFlow()

    suspend fun resolveBugReport(reportId: String, resolvedBy: String): Result<Unit> = runCatching {
        db.getReference("bug_reports").child(reportId).updateChildren(
            mapOf(
                "status" to "RESOLVED",
                "resolvedAt" to System.currentTimeMillis(),
                "resolvedBy" to resolvedBy,
            )
        ).await()
    }

    
    suspend fun approveRegistrationRequest(
        request: RegistrationRequest,
        issuedKey: String,
        reviewedBy: String,
        reviewNotes: String,
        approvedAttributeIds: List<String> = emptyList(),
    ): Result<Unit> = runCatching {
        require(issuedKey.isNotBlank()) { "An issued access key is required." }
        val timestamp = System.currentTimeMillis()
        val cleanAttributes = approvedAttributeIds.distinct().filter { id ->
            CounselorAttributeCatalog.all.any { it.id == id }
        }
        val requestUpdates = mapOf(
            "status" to "APPROVED",
            "issuedKey" to issuedKey.trim(),
            "approvedAttributeIds" to cleanAttributes,
            "reviewedBy" to reviewedBy,
            "reviewNotes" to reviewNotes.trim(),
            "reviewedAt" to timestamp
        )
        db.getReference("registration_requests").child(request.requestId)
            .updateChildren(requestUpdates).await()

        if (request.applicantType == "COUNSELOR") {
            val attributeLabelsEn = cleanAttributes.map(CounselorAttributeCatalog::labelEn)
            val attributeLabelsUr = cleanAttributes.map(CounselorAttributeCatalog::labelUr)
            db.getReference("counselor_keys").child(issuedKey.trim()).setValue(
                mapOf(
                    "isActive" to true,
                    "isOnline" to false,
                    "callEnabled" to false,
                    "profileComplete" to false,
                    "assignedName" to request.applicantName,
                    "ngoName" to request.organizationName,
                    "region" to request.region,
                    "email" to request.email,
                    "attributeIds" to cleanAttributes,
                    "attributeLabelsEn" to attributeLabelsEn,
                    "attributeLabelsUr" to attributeLabelsUr,
                    "specialization" to attributeLabelsEn.firstOrNull().orEmpty().ifBlank { "Mental Health" },
                    "createdAt" to timestamp,
                    "createdFromRequestId" to request.requestId
                )
            ).await()
        } else {
            db.getReference("ngo_keys").child(issuedKey.trim()).setValue(
                mapOf(
                    "isActive" to true,
                    "name" to request.organizationName.ifBlank { request.applicantName },
                    "region" to request.region,
                    "email" to request.email,
                    "createdAt" to timestamp,
                    "createdFromRequestId" to request.requestId
                )
            ).await()
        }
    }

    suspend fun rejectRegistrationRequest(
        requestId: String,
        reviewedBy: String,
        reviewNotes: String
    ): Result<Unit> = runCatching {
        db.getReference("registration_requests").child(requestId).updateChildren(
            mapOf(
                "status" to "REJECTED",
                "reviewedBy" to reviewedBy,
                "reviewNotes" to reviewNotes.trim(),
                "reviewedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    suspend fun getNgoKey(key: String): Result<Map<String, Any>?> = runCatching {
        db.getReference("ngo_keys").child(key).get().await().value as? Map<String, Any>
    }

    suspend fun submitPaymentRequest(
        userId: String,
        counselorKey: String,
        counselorName: String,
        amountPkr: String,
        accountTitle: String,
        transactionReference: String,
        proofUri: Uri?
    ): Result<String> = runCatching {
        require(userId.isNotBlank()) { "Please sign in before submitting payment proof." }
        require(accountTitle.trim().isNotBlank()) { "Account title is required." }
        require(amountPkr.toIntOrNull() != null) { "A valid fee amount is required." }
        val requestId = UUID.randomUUID().toString()
        val proofUrl = proofUri?.let { uploadEvidence("payment_proofs/$userId/$requestId", it) }.orEmpty()
        db.getReference("payment_requests").child(requestId).setValue(
            mapOf(
                "requestId" to requestId,
                "userId" to userId,
                "counselorKey" to counselorKey,
                "counselorName" to counselorName,
                "amountPkr" to amountPkr,
                "accountTitle" to accountTitle.trim(),
                "transactionReference" to transactionReference,
                "proofUrl" to proofUrl,
                "status" to "PENDING_REVIEW",
                "createdAt" to System.currentTimeMillis()
            )
        ).await()
        requestId
    }

    fun listenToPaymentRequests(): Flow<List<PaymentRequest>> = paymentRequestsFlow()

    fun listenToUserPaymentRequests(userId: String): Flow<List<PaymentRequest>> =
        paymentRequestsFlow { it.userId == userId }

    
    suspend fun approvePaymentRequest(
        request: PaymentRequest,
        reviewedBy: String,
        reviewNotes: String
    ): Result<Unit> = runCatching {
        val timestamp = System.currentTimeMillis()
        val chatRef = db.getReference("user_chats")
            .child(chatSessionPath(request.userId, request.counselorKey))
        val existingExpiry = chatRef.child("expiresAt").get().await().getValue(Long::class.java) ?: timestamp
        val expiresAt = maxOf(existingExpiry, timestamp) + 48L * 60L * 60L * 1000L
        db.getReference("payment_requests").child(request.requestId).updateChildren(
            mapOf(
                "status" to "ASSIGNED",
                "reviewedBy" to reviewedBy,
                "reviewNotes" to reviewNotes.trim(),
                "reviewedAt" to timestamp,
                "sessionStartedAt" to timestamp,
                "sessionExpiresAt" to expiresAt,
            )
        ).await()

        val messageId = UUID.randomUUID().toString()
        chatRef.updateChildren(
            mapOf(
                "uid" to request.userId,
                "counselorKey" to request.counselorKey,
                "counselorName" to request.counselorName,
                "sessionStartedAt" to timestamp,
                "expiresAt" to expiresAt,
                "paymentRequestId" to request.requestId,
                "lastPaymentApprovedAt" to timestamp,
            )
        ).await()
        chatRef.child("messages").child(messageId)
            .setValue(
                mapOf(
                    "messageId" to messageId,
                    "text" to "A 48-hour consultation session has been assigned after manual review of the submitted payment.",
                    "senderId" to "admin",
                    "senderType" to "counselor",
                    "counselorKey" to request.counselorKey,
                    "timestamp" to timestamp
                )
            ).await()
        db.getReference("counselor_keys").child(request.counselorKey).child("sessionCount")
            .setValue(ServerValue.increment(1)).await()

        saveUserNotification(
            uid = request.userId,
            titleEn = "Payment approved",
            titleUr = "Payment approve ho gayi",
            bodyEn = "Your payment was confirmed. Your 48-hour counselor session is active.",
            bodyUr = "Aapki payment confirm ho gayi. 48 ghante ka counselor session active hai.",
            type = "COUNSELOR",
            actionRoute = "counselor-chat/${request.counselorKey}/${request.counselorName.replace(" ", "_")}"
        ).getOrThrow()
    }

    suspend fun rejectPaymentRequest(
        request: PaymentRequest,
        reviewedBy: String,
        reviewNotes: String,
        reviewAttachmentUri: Uri? = null,
    ): Result<Unit> = runCatching {
        require(reviewNotes.trim().isNotBlank()) { "A rejection note is required." }
        val timestamp = System.currentTimeMillis()
        val attachmentUrl = reviewAttachmentUri
            ?.let { uploadEvidence("payment_review_notes/${request.userId}/${request.requestId}", it) }
            .orEmpty()
        db.getReference("payment_requests").child(request.requestId).updateChildren(
            mapOf(
                "status" to "REJECTED",
                "reviewedBy" to reviewedBy,
                "reviewNotes" to reviewNotes.trim(),
                "reviewAttachmentUrl" to attachmentUrl,
                "reviewedAt" to timestamp
            )
        ).await()
        saveUserNotification(
            uid = request.userId,
            titleEn = "Payment rejected",
            titleUr = "Payment reject ho gayi",
            bodyEn = "Your payment could not be confirmed: ${reviewNotes.trim()}",
            bodyUr = "Aapki payment confirm nahi ho saki: ${reviewNotes.trim()}",
            type = "COUNSELOR",
            actionRoute = "counselors"
        ).getOrThrow()
    }

    
    fun listenToRegionalRiskSummaries(): Flow<List<RegionalRiskSummary>> = callbackFlow {
        val root = db.reference
        val listener = root.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userRegions = snapshot.child("users").children.associate { user ->
                    val region = user.child("region").getValue(String::class.java)?.trim().orEmpty()
                    (user.key ?: "") to region.ifBlank { "Unspecified" }
                }
                val registeredCounts = userRegions.values.groupingBy { it }.eachCount()
                val entries = mutableMapOf<String, MutableList<Pair<String, List<DataSnapshot>>>>()
                snapshot.child("assessment").children.forEach { userAssessments ->
                    val uid = userAssessments.key ?: return@forEach
                    val region = userRegions[uid] ?: "Unspecified"
                    entries.getOrPut(region) { mutableListOf() }
                        .add(uid to userAssessments.children.toList())
                }

                val regions = (registeredCounts.keys + entries.keys).toSortedSet()
                val results = regions.map { region ->
                    val usersWithScores = entries[region].orEmpty().mapNotNull { (_, assessments) ->
                        assessments.maxByOrNull {
                            it.child("timestamp").getValue(Long::class.java) ?: 0L
                        }?.child("score")?.getValue(Long::class.java)?.toInt()
                    }
                    RegionalRiskSummary(
                        region = region,
                        registeredUsers = registeredCounts[region] ?: 0,
                        assessedUsers = usersWithScores.size,
                        totalAssessments = entries[region].orEmpty().sumOf { it.second.size },
                        averageLatestScore = usersWithScores.average().takeUnless { it.isNaN() } ?: 0.0,
                        highRiskUsers = usersWithScores.count { it >= 6 },
                        moderateRiskUsers = usersWithScores.count { it in 3..5 }
                    )
                }
                trySend(results)
            }

            override fun onCancelled(error: DatabaseError) { Log.w("RealtimeDBService", "RTDB listener cancelled", error.toException()); close() }
        })
        awaitClose { root.removeEventListener(listener) }
    }

    private fun avatarRequestFrom(snapshot: DataSnapshot): AvatarRequest? {
        if (!snapshot.exists()) return null
        return AvatarRequest(
            requestId = snapshot.child("requestId").getValue(String::class.java) ?: snapshot.key.orEmpty(),
            userId = snapshot.child("userId").getValue(String::class.java).orEmpty(),
            userEmail = snapshot.child("userEmail").getValue(String::class.java).orEmpty(),
            userName = snapshot.child("userName").getValue(String::class.java).orEmpty(),
            fileUrl = snapshot.child("fileUrl").getValue(String::class.java).orEmpty(),
            fileName = snapshot.child("fileName").getValue(String::class.java).orEmpty(),
            mimeType = snapshot.child("mimeType").getValue(String::class.java).orEmpty(),
            sizeBytes = snapshot.child("sizeBytes").getValue(Long::class.java) ?: 0L,
            status = snapshot.child("status").getValue(String::class.java) ?: "PENDING_REVIEW",
            adminComment = snapshot.child("adminComment").getValue(String::class.java).orEmpty(),
            reviewedBy = snapshot.child("reviewedBy").getValue(String::class.java).orEmpty(),
            createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L,
            reviewedAt = snapshot.child("reviewedAt").getValue(Long::class.java) ?: 0L,
        )
    }

    private fun emailKey(email: String): String {
        val normalized = email.trim().lowercase()
        if (normalized.isBlank()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun safeTokenKey(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private suspend fun uploadEvidence(path: String, uri: Uri): String {
        val reference = FirebaseStorage.getInstance().reference.child("$path/upload")
        reference.putFile(uri).await()
        return reference.downloadUrl.await().toString()
    }

    private fun paymentRequestsFlow(
        predicate: (PaymentRequest) -> Boolean = { true }
    ): Flow<List<PaymentRequest>> = callbackFlow {
        val ref = db.getReference("payment_requests")
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(
                    snapshot.children.mapNotNull(::paymentRequestFrom)
                        .filter(predicate)
                        .sortedByDescending { it.createdAt }
                )
            }

            override fun onCancelled(error: DatabaseError) { Log.w("RealtimeDBService", "RTDB listener cancelled", error.toException()); close() }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    private fun registrationRequestFrom(snapshot: DataSnapshot): RegistrationRequest? {
        if (!snapshot.exists()) return null
        return RegistrationRequest(
            requestId = snapshot.child("requestId").getValue(String::class.java) ?: snapshot.key.orEmpty(),
            applicantType = snapshot.child("applicantType").getValue(String::class.java).orEmpty(),
            applicantName = snapshot.child("applicantName").getValue(String::class.java).orEmpty(),
            organizationName = snapshot.child("organizationName").getValue(String::class.java).orEmpty(),
            email = snapshot.child("email").getValue(String::class.java).orEmpty(),
            phone = snapshot.child("phone").getValue(String::class.java).orEmpty(),
            region = snapshot.child("region").getValue(String::class.java).orEmpty(),
            city = snapshot.child("city").getValue(String::class.java).orEmpty(),
            district = snapshot.child("district").getValue(String::class.java).orEmpty(),
            locationAccuracyMeters = snapshot.child("locationAccuracyMeters").getValue(Float::class.java) ?: 0f,
            verificationBody = snapshot.child("verificationBody").getValue(String::class.java).orEmpty(),
            registrationNumber = snapshot.child("registrationNumber").getValue(String::class.java).orEmpty(),
            qualificationSummary = snapshot.child("qualificationSummary").getValue(String::class.java).orEmpty(),
            details = snapshot.child("details").getValue(String::class.java).orEmpty(),
            documentUrl = snapshot.child("documentUrl").getValue(String::class.java).orEmpty(),
            documentUrls = snapshot.child("documentUrls").children.associate { child ->
                child.key.orEmpty() to child.getValue(String::class.java).orEmpty()
            }.filterKeys { it.isNotBlank() }.filterValues { it.isNotBlank() },
            requiredDocumentKeys = snapshot.child("requiredDocumentKeys").children.mapNotNull {
                it.getValue(String::class.java)
            },
            status = snapshot.child("status").getValue(String::class.java) ?: "PENDING_REVIEW",
            issuedKey = snapshot.child("issuedKey").getValue(String::class.java).orEmpty(),
            approvedAttributeIds = snapshot.child("approvedAttributeIds").children.mapNotNull {
                it.getValue(String::class.java)
            },
            reviewedBy = snapshot.child("reviewedBy").getValue(String::class.java).orEmpty(),
            reviewNotes = snapshot.child("reviewNotes").getValue(String::class.java).orEmpty(),
            createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L,
            reviewedAt = snapshot.child("reviewedAt").getValue(Long::class.java) ?: 0L
        )
    }

    private fun paymentRequestFrom(snapshot: DataSnapshot): PaymentRequest? {
        if (!snapshot.exists()) return null
        return PaymentRequest(
            requestId = snapshot.child("requestId").getValue(String::class.java) ?: snapshot.key.orEmpty(),
            userId = snapshot.child("userId").getValue(String::class.java).orEmpty(),
            counselorKey = snapshot.child("counselorKey").getValue(String::class.java).orEmpty(),
            counselorName = snapshot.child("counselorName").getValue(String::class.java).orEmpty(),
            amountPkr = snapshot.child("amountPkr").getValue(String::class.java).orEmpty(),
            accountTitle = snapshot.child("accountTitle").getValue(String::class.java).orEmpty(),
            transactionReference = snapshot.child("transactionReference").getValue(String::class.java).orEmpty(),
            proofUrl = snapshot.child("proofUrl").getValue(String::class.java).orEmpty(),
            status = snapshot.child("status").getValue(String::class.java) ?: "PENDING_REVIEW",
            reviewedBy = snapshot.child("reviewedBy").getValue(String::class.java).orEmpty(),
            reviewNotes = snapshot.child("reviewNotes").getValue(String::class.java).orEmpty(),
            reviewAttachmentUrl = snapshot.child("reviewAttachmentUrl").getValue(String::class.java).orEmpty(),
            createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L,
            reviewedAt = snapshot.child("reviewedAt").getValue(Long::class.java) ?: 0L
        )
    }

    private fun bugReportsFlow(
        predicate: (BugReport) -> Boolean = { true },
    ): Flow<List<BugReport>> = callbackFlow {
        val ref = db.getReference("bug_reports")
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(
                    snapshot.children.mapNotNull(::bugReportFrom)
                        .filter(predicate)
                        .sortedByDescending { it.createdAt }
                )
            }

            override fun onCancelled(error: DatabaseError) { Log.w("RealtimeDBService", "RTDB listener cancelled", error.toException()); close() }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    private fun bugReportFrom(snapshot: DataSnapshot): BugReport? {
        if (!snapshot.exists()) return null
        return BugReport(
            reportId = snapshot.child("reportId").getValue(String::class.java) ?: snapshot.key.orEmpty(),
            userId = snapshot.child("userId").getValue(String::class.java).orEmpty(),
            maskedEmail = snapshot.child("maskedEmail").getValue(String::class.java).orEmpty(),
            deviceModel = snapshot.child("deviceModel").getValue(String::class.java).orEmpty(),
            screenshotUrl = snapshot.child("screenshotUrl").getValue(String::class.java).orEmpty(),
            description = snapshot.child("description").getValue(String::class.java).orEmpty(),
            status = snapshot.child("status").getValue(String::class.java) ?: "OPEN",
            createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L,
            resolvedAt = snapshot.child("resolvedAt").getValue(Long::class.java) ?: 0L,
            resolvedBy = snapshot.child("resolvedBy").getValue(String::class.java).orEmpty(),
        )
    }

    private fun maskEmail(email: String): String {
        val trimmed = email.trim()
        val name = trimmed.substringBefore("@", missingDelimiterValue = "")
        val domain = trimmed.substringAfter("@", missingDelimiterValue = "")
        if (name.isBlank() || domain.isBlank()) return "anonymous@sahara.ai"
        return "${name.first()}***@$domain"
    }

    private suspend fun reserveBugReportSlot(counterRef: DatabaseReference) {
        suspendCancellableCoroutine<Unit> { continuation ->
            var rejectedForLimit = false
            counterRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val count = currentData.getValue(Long::class.java) ?: 0L
                    if (count >= 2L) {
                        rejectedForLimit = true
                        return Transaction.abort()
                    }
                    currentData.value = count + 1L
                    return Transaction.success(currentData)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                    when {
                        error != null -> continuation.resumeWithException(error.toException())
                        rejectedForLimit || !committed ->
                            continuation.resumeWithException(IllegalStateException("You can submit at most 2 bug reports in one day."))
                        else -> continuation.resume(Unit)
                    }
                }
            })
        }
    }

    private suspend fun releaseBugReportSlot(counterRef: DatabaseReference) {
        suspendCancellableCoroutine<Unit> { continuation ->
            counterRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val count = currentData.getValue(Long::class.java) ?: 0L
                    currentData.value = (count - 1L).coerceAtLeast(0L)
                    return Transaction.success(currentData)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                    if (error != null) continuation.resumeWithException(error.toException())
                    else continuation.resume(Unit)
                }
            })
        }
    }
}
