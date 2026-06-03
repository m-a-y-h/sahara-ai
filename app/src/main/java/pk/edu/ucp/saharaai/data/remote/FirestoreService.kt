package pk.edu.ucp.saharaai.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import pk.edu.ucp.saharaai.data.model.AppNotification
import pk.edu.ucp.saharaai.data.model.CommunityPost
import pk.edu.ucp.saharaai.data.model.ConnectionStatus
import pk.edu.ucp.saharaai.data.model.CounselorProfile
import pk.edu.ucp.saharaai.data.model.EmergencyAlert
import pk.edu.ucp.saharaai.data.model.FirestoreMessage
import pk.edu.ucp.saharaai.data.model.ModerationReport
import pk.edu.ucp.saharaai.data.model.NgoStats
import pk.edu.ucp.saharaai.data.model.UserConnection

object FirestoreService {

    private val db: FirebaseFirestore get() = Firebase.firestore

    private const val COL_MESSAGES         = "messages"
    private const val COL_COUNSELORS       = "counselors"
    private const val COL_NGOS             = "ngos"
    private const val COL_NOTIFICATIONS    = "notifications"
    private const val COL_EMERGENCY_ALERTS = "emergency_alerts"
    private const val COL_COMMUNITY_POSTS  = "community_posts"
    private const val COL_CONNECTIONS      = "user_connections"
    private const val COL_REPORTS          = "reports"
    private const val COL_CHAT_SESSIONS    = "chat_sessions"

    private fun hoursToSleepQuality(hours: Float): String =
        when {
            hours >= 8f -> "excellent"
            hours >= 7f -> "good"
            hours >= 6f -> "okay"
            else -> "poor"
        }

    suspend fun saveSleepLog(
        uid: String,
        date: String,
        bedtime: String,
        waketime: String,
        hours: Float,
        source: String,
        timeZoneId: String,
        automatic: Boolean = false,
        confidence: Float? = null,
        sourceReason: String = ""
    ): Result<Unit> = runCatching {
        val values = mutableMapOf<String, Any?>(
            "date" to date,
            "bedtime" to bedtime,
            "waketime" to waketime,
            "hours" to hours,
            "qualityType" to hoursToSleepQuality(hours),
            "source" to source,
            "timeZoneId" to timeZoneId,
            "automatic" to automatic,
            "sourceReason" to sourceReason,
            "recordedAt" to Timestamp.now()
        )
        confidence?.let { values["confidence"] = it }
        db.collection("users").document(uid).collection("sleep_logs").document(date)
            .set(values).await()
    }

    suspend fun sendMessage(message: FirestoreMessage): Result<String> = runCatching {
        val ref = db.collection(COL_MESSAGES).document()
        val withId = message.copy(messageId = ref.id)
        ref.set(withId).await()
        ref.id
    }

    fun getMessagesFlow(sessionId: String): Flow<List<FirestoreMessage>> = callbackFlow {
        val listener = db.collection(COL_MESSAGES)
            .whereEqualTo("sessionId", sessionId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("FirestoreService", "messages listener error", error)
                    close()
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents?.mapNotNull {
                    it.toObject(FirestoreMessage::class.java)
                } ?: emptyList()
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    suspend fun markMessageRead(messageId: String): Result<Unit> = runCatching {
        db.collection(COL_MESSAGES)
            .document(messageId)
            .update("isRead", true)
            .await()
    }

    suspend fun updateMessageContent(messageId: String, newContent: String): Result<Unit> = runCatching {
        db.collection(COL_MESSAGES)
            .document(messageId)
            .update("content", newContent)
            .await()
    }

    suspend fun deleteAllMessages(sessionId: String): Result<Unit> = runCatching {
        val snap = db.collection(COL_MESSAGES)
            .whereEqualTo("sessionId", sessionId)
            .get()
            .await()
        if (snap.isEmpty) return@runCatching
        // Firestore caps batches at 500 writes; chunk to stay well under it.
        snap.documents.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    suspend fun getMessagesOnce(sessionId: String): Result<List<FirestoreMessage>> = runCatching {
        db.collection(COL_MESSAGES)
            .whereEqualTo("sessionId", sessionId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .await()
            .toObjects(FirestoreMessage::class.java)
    }

    suspend fun getCounselors(): Result<List<CounselorProfile>> = runCatching {
        db.collection(COL_COUNSELORS)
            .whereEqualTo("isVerified", true)
            .orderBy("rating", Query.Direction.DESCENDING)
            .get()
            .await()
            .toObjects(CounselorProfile::class.java)
    }

    suspend fun getCounselor(counselorId: String): Result<CounselorProfile?> = runCatching {
        db.collection(COL_COUNSELORS)
            .document(counselorId)
            .get()
            .await()
            .toObject(CounselorProfile::class.java)
    }

    suspend fun saveCounselor(profile: CounselorProfile): Result<Unit> = runCatching {
        val id = profile.counselorId.ifBlank {
            db.collection(COL_COUNSELORS).document().id
        }
        db.collection(COL_COUNSELORS)
            .document(id)
            .set(profile.copy(counselorId = id))
            .await()
    }

    suspend fun updateCounselorAvailability(counselorId: String, available: Boolean): Result<Unit> = runCatching {
        db.collection(COL_COUNSELORS)
            .document(counselorId)
            .update("isAvailable", available)
            .await()
    }

    suspend fun incrementCounselorSessions(counselorId: String): Result<Unit> = runCatching {
        val ref = db.collection(COL_COUNSELORS).document(counselorId)
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val current = snap.getLong("sessionCount") ?: 0L
            tx.update(ref, "sessionCount", current + 1)
        }.await()
    }

    suspend fun rateCounselor(counselorId: String, newRating: Float): Result<Unit> = runCatching {
        val ref = db.collection(COL_COUNSELORS).document(counselorId)
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val total = snap.getLong("totalRatings") ?: 0L
            val currentAvg = snap.getDouble("rating") ?: 0.0
            val newAvg = ((currentAvg * total) + newRating) / (total + 1)
            tx.update(ref, mapOf("rating" to newAvg, "totalRatings" to total + 1))
        }.await()
    }

    suspend fun getCounselorsByNgo(ngoId: String): Result<List<CounselorProfile>> = runCatching {
        db.collection(COL_COUNSELORS)
            .whereEqualTo("ngoId", ngoId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()
            .toObjects(CounselorProfile::class.java)
    }

    fun getCounselorsByNgoFlow(ngoId: String): Flow<List<CounselorProfile>> = callbackFlow {
        val listener = db.collection(COL_COUNSELORS)
            .whereEqualTo("ngoId", ngoId)
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w("FirestoreService", "Firestore listener error", err); close(); return@addSnapshotListener }
                trySend(snap?.toObjects(CounselorProfile::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun getCounselorByUserId(userId: String): Result<CounselorProfile?> = runCatching {
        val snap = db.collection(COL_COUNSELORS)
            .whereEqualTo("userId", userId)
            .limit(1)
            .get()
            .await()
        snap.toObjects(CounselorProfile::class.java).firstOrNull()
    }

    suspend fun getOrCreateChatSession(userId: String, counselorId: String): Result<String> = runCatching {
        val sessionId = listOf(userId, counselorId).sorted().joinToString("_")
        val ref = db.collection(COL_CHAT_SESSIONS).document(sessionId)
        val snap = ref.get().await()
        if (!snap.exists()) {
            ref.set(mapOf(
                "sessionId"   to sessionId,
                "userId"      to userId,
                "counselorId" to counselorId,
                "createdAt"   to Timestamp.now(),
                "lastMessage" to "",
                "lastMessageAt" to Timestamp.now()
            )).await()
        }
        sessionId
    }

    suspend fun updateChatSessionLastMessage(sessionId: String, preview: String): Result<Unit> = runCatching {
        db.collection(COL_CHAT_SESSIONS).document(sessionId).update(mapOf(
            "lastMessage"   to preview,
            "lastMessageAt" to Timestamp.now()
        )).await()
    }

    fun getCounselorChatsFlow(counselorId: String): Flow<List<Map<String, Any>>> = callbackFlow {
        val listener = db.collection(COL_CHAT_SESSIONS)
            .whereEqualTo("counselorId", counselorId)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w("FirestoreService", "Firestore listener error", err); close(); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull { it.data } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun getNgoStats(ngoId: String): Result<NgoStats?> = runCatching {
        db.collection(COL_NGOS)
            .document(ngoId)
            .get()
            .await()
            .toObject(NgoStats::class.java)
    }

    suspend fun saveNgoStats(stats: NgoStats): Result<Unit> = runCatching {
        db.collection(COL_NGOS)
            .document(stats.ngoId)
            .set(stats)
            .await()
    }

    fun getNgoStatsFlow(ngoId: String): Flow<NgoStats?> = callbackFlow {
        val listener = db.collection(COL_NGOS)
            .document(ngoId)
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w("FirestoreService", "Firestore listener error", err); close(); return@addSnapshotListener }
                trySend(snap?.toObject(NgoStats::class.java))
            }
        awaitClose { listener.remove() }
    }

    suspend fun saveNotification(notif: AppNotification): Result<String> = runCatching {
        val ref = db.collection(COL_NOTIFICATIONS).document()
        val withId = notif.copy(notificationId = ref.id)
        ref.set(withId).await()
        ref.id
    }

    suspend fun getNotifications(userId: String): Result<List<AppNotification>> = runCatching {
        db.collection(COL_NOTIFICATIONS)
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .await()
            .toObjects(AppNotification::class.java)
    }

    fun getNotificationsFlow(userId: String): Flow<List<AppNotification>> = callbackFlow {
        val listener = db.collection(COL_NOTIFICATIONS)
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w("FirestoreService", "Firestore listener error", err); close(); return@addSnapshotListener }
                trySend(snap?.toObjects(AppNotification::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun markNotificationRead(notifId: String): Result<Unit> = runCatching {
        db.collection(COL_NOTIFICATIONS)
            .document(notifId)
            .update("isRead", true)
            .await()
    }

    suspend fun markAllNotificationsRead(userId: String): Result<Unit> = runCatching {
        val batch = db.batch()
        val unread = db.collection(COL_NOTIFICATIONS)
            .whereEqualTo("userId", userId)
            .whereEqualTo("isRead", false)
            .get()
            .await()
        unread.documents.forEach { doc ->
            batch.update(doc.reference, "isRead", true)
        }
        batch.commit().await()
    }

    suspend fun createEmergencyAlert(alert: EmergencyAlert): Result<String> = runCatching {
        val ref = db.collection(COL_EMERGENCY_ALERTS).document()
        val withId = alert.copy(alertId = ref.id)
        ref.set(withId).await()
        ref.id
    }

    suspend fun getOpenAlerts(ngoId: String): Result<List<EmergencyAlert>> = runCatching {
        db.collection(COL_EMERGENCY_ALERTS)
            .whereEqualTo("ngoId", ngoId)
            .whereEqualTo("status", "PENDING")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()
            .toObjects(EmergencyAlert::class.java)
    }

    fun getAlertsFlow(ngoId: String): Flow<List<EmergencyAlert>> = callbackFlow {
        val listener = db.collection(COL_EMERGENCY_ALERTS)
            .whereEqualTo("ngoId", ngoId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w("FirestoreService", "Firestore listener error", err); close(); return@addSnapshotListener }
                trySend(snap?.toObjects(EmergencyAlert::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun updateAlertStatus(
        alertId: String,
        status: String,
        counselorId: String = ""
    ): Result<Unit> = runCatching {
        val updates = mutableMapOf<String, Any>("status" to status)
        if (counselorId.isNotBlank()) updates["assignedCounselorId"] = counselorId
        if (status == "RESOLVED") updates["resolvedAt"] = Timestamp.now()
        db.collection(COL_EMERGENCY_ALERTS)
            .document(alertId)
            .update(updates)
            .await()
    }

    suspend fun createCommunityPost(post: CommunityPost): Result<String> = runCatching {
        val ref = db.collection(COL_COMMUNITY_POSTS).document()
        val withId = post.copy(postId = ref.id)
        ref.set(withId).await()
        ref.id
    }

    suspend fun getCommunityPosts(): Result<List<CommunityPost>> = runCatching {
        db.collection(COL_COMMUNITY_POSTS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .await()
            .toObjects(CommunityPost::class.java)
            .filter { !it.isFlagged }
    }

    fun getCommunityPostsFlow(): Flow<List<CommunityPost>> = callbackFlow {
        val listener = db.collection(COL_COMMUNITY_POSTS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w("FirestoreService", "Firestore listener error", err); close(); return@addSnapshotListener }
                val posts = snap?.toObjects(CommunityPost::class.java)
                    ?.filter { !it.isFlagged }
                    ?: emptyList()
                trySend(posts)
            }
        awaitClose { listener.remove() }
    }

    suspend fun toggleLikePost(postId: String, increment: Boolean): Result<Unit> = runCatching {
        val ref = db.collection(COL_COMMUNITY_POSTS).document(postId)
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val count = snap.getLong("likesCount") ?: 0L
            tx.update(ref, "likesCount", if (increment) count + 1 else maxOf(0L, count - 1))
        }.await()
    }

    suspend fun deleteCommunityPost(postId: String): Result<Unit> = runCatching {
        db.collection(COL_COMMUNITY_POSTS).document(postId).delete().await()
    }

    suspend fun flagPost(postId: String): Result<Unit> = runCatching {
        db.collection(COL_COMMUNITY_POSTS)
            .document(postId)
            .update("isFlagged", true)
            .await()
    }

    suspend fun sendConnectionRequest(connection: UserConnection): Result<String> = runCatching {
        val ref = db.collection(COL_CONNECTIONS).document()
        val withId = connection.copy(connectionId = ref.id)
        ref.set(withId).await()
        ref.id
    }

    suspend fun getAcceptedConnections(userId: String): Result<List<UserConnection>> = runCatching {
        val asSender = db.collection(COL_CONNECTIONS)
            .whereEqualTo("senderId", userId)
            .whereEqualTo("status", ConnectionStatus.ACCEPTED.name)
            .get().await().toObjects(UserConnection::class.java)

        val asReceiver = db.collection(COL_CONNECTIONS)
            .whereEqualTo("receiverId", userId)
            .whereEqualTo("status", ConnectionStatus.ACCEPTED.name)
            .get().await().toObjects(UserConnection::class.java)

        (asSender + asReceiver).distinctBy { it.connectionId }
    }

    suspend fun getPendingRequests(userId: String): Result<List<UserConnection>> = runCatching {
        db.collection(COL_CONNECTIONS)
            .whereEqualTo("receiverId", userId)
            .whereEqualTo("status", ConnectionStatus.PENDING.name)
            .get()
            .await()
            .toObjects(UserConnection::class.java)
    }

    suspend fun updateConnectionStatus(connectionId: String, status: String): Result<Unit> = runCatching {
        db.collection(COL_CONNECTIONS)
            .document(connectionId)
            .update(mapOf("status" to status, "updatedAt" to Timestamp.now()))
            .await()
    }

    suspend fun removeConnection(connectionId: String): Result<Unit> = runCatching {
        db.collection(COL_CONNECTIONS).document(connectionId).delete().await()
    }

    fun getConnectionsFlow(userId: String): Flow<List<UserConnection>> = callbackFlow {
        var list1 = emptyList<UserConnection>()
        var list2 = emptyList<UserConnection>()

        val l1 = db.collection(COL_CONNECTIONS)
            .whereEqualTo("senderId", userId)
            .addSnapshotListener { snap, _ ->
                list1 = snap?.toObjects(UserConnection::class.java) ?: emptyList()
                trySend((list1 + list2).distinctBy { it.connectionId })
            }
        val l2 = db.collection(COL_CONNECTIONS)
            .whereEqualTo("receiverId", userId)
            .addSnapshotListener { snap, _ ->
                list2 = snap?.toObjects(UserConnection::class.java) ?: emptyList()
                trySend((list1 + list2).distinctBy { it.connectionId })
            }
        awaitClose { l1.remove(); l2.remove() }
    }

    suspend fun submitReport(report: ModerationReport): Result<String> = runCatching {
        val ref = db.collection(COL_REPORTS).document()
        val withId = report.copy(reportId = ref.id)
        ref.set(withId).await()
        ref.id
    }

    suspend fun getOpenReports(): Result<List<ModerationReport>> = runCatching {
        db.collection(COL_REPORTS)
            .whereEqualTo("status", "OPEN")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .get()
            .await()
            .toObjects(ModerationReport::class.java)
    }

    suspend fun updateReportStatus(
        reportId: String,
        status: String,
        reviewerId: String,
        reviewNote: String
    ): Result<Unit> = runCatching {
        db.collection(COL_REPORTS)
            .document(reportId)
            .update(mapOf(
                "status"       to status,
                "reviewedBy"   to reviewerId,
                "reviewNote"   to reviewNote,
                "resolvedAt"   to Timestamp.now()
            ))
            .await()
    }
}
