package pk.edu.ucp.saharaai.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import pk.edu.ucp.saharaai.data.model.AiChatSession
import pk.edu.ucp.saharaai.data.model.AppNotification
import pk.edu.ucp.saharaai.data.model.CommunityPost
import pk.edu.ucp.saharaai.data.model.ConnectionStatus
import pk.edu.ucp.saharaai.data.model.CounselorProfile
import pk.edu.ucp.saharaai.data.model.EmergencyAlert
import pk.edu.ucp.saharaai.data.model.FirestoreMessage
import pk.edu.ucp.saharaai.data.model.ModerationReport
import pk.edu.ucp.saharaai.data.model.NgoStats
import pk.edu.ucp.saharaai.data.model.UserConnection
import java.util.Date

object FirestoreService {

    private val db: FirebaseFirestore by lazy {
        Firebase.firestore.apply {
            runCatching {
                firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .build()
            }
        }
    }

    private const val COL_MESSAGES         = "messages"
    private const val COL_COUNSELORS       = "counselors"
    private const val COL_NGOS             = "ngos"
    private const val COL_NOTIFICATIONS    = "notifications"
    private const val COL_EMERGENCY_ALERTS = "emergency_alerts"
    private const val COL_COMMUNITY_POSTS  = "community_posts"
    private const val COL_CONNECTIONS      = "user_connections"
    private const val COL_REPORTS          = "reports"
    private const val COL_CHAT_SESSIONS    = "chat_sessions"

    private const val TAG = "FirestoreService"

    // Shared body of the snapshot-listener callbackFlow blocks below; the
    // query lambda runs at collect time, matching the inlined originals.
    private fun <T> queryFlow(
        logLabel: String = "Firestore listener error",
        query: () -> Query,
        map: (QuerySnapshot?) -> T,
    ): Flow<T> = callbackFlow {
        val listener = query().addSnapshotListener { snap, err ->
            if (err != null) {
                Log.w(TAG, logLabel, err)
                close()
                return@addSnapshotListener
            }
            trySend(map(snap))
        }
        awaitClose { listener.remove() }
    }

    private suspend fun createWithId(collection: String, build: (String) -> Any): String {
        val ref = db.collection(collection).document()
        ref.set(build(ref.id)).await()
        return ref.id
    }

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
        ref.set(withId.toFirestoreMap()).await()
        ref.id
    }

    fun getMessagesFlow(sessionId: String): Flow<List<FirestoreMessage>> =
        queryFlow(logLabel = "messages listener error", query = {
            db.collection(COL_MESSAGES)
                .whereEqualTo("sessionId", sessionId)
                .orderBy("timestamp", Query.Direction.ASCENDING)
        }) { snapshot ->
            snapshot?.documents?.mapNotNull { it.toFirestoreMessage() } ?: emptyList()
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
            .documents
            .mapNotNull { it.toFirestoreMessage() }
    }

    fun createAiChatSession(
        userId: String,
        clientCreatedAtMillis: Long = System.currentTimeMillis(),
        sessionId: String = "",
    ): Result<AiChatSession> = runCatching {
        val ref = if (sessionId.isBlank()) {
            db.collection(COL_CHAT_SESSIONS).document()
        } else {
            db.collection(COL_CHAT_SESSIONS).document(sessionId)
        }
        val clientCreatedAt = Timestamp(Date(clientCreatedAtMillis))
        val session = AiChatSession(
            sessionId = ref.id,
            userId = userId,
            title = "Untitled",
            titleStatus = AiChatSession.TITLE_PENDING,
            clientCreatedAt = clientCreatedAt,
            clientCreatedAtMillis = clientCreatedAtMillis,
        )
        ref.set(
            mapOf(
                "sessionId" to session.sessionId,
                "type" to AiChatSession.TYPE_AI,
                "userId" to userId,
                "title" to session.title,
                "titleStatus" to session.titleStatus,
                "createdAt" to FieldValue.serverTimestamp(),
                "clientCreatedAt" to clientCreatedAt,
                "clientCreatedAtMillis" to clientCreatedAtMillis,
                "updatedAt" to FieldValue.serverTimestamp(),
                "lastMessage" to "",
                "messageCount" to 0,
                "isDeleted" to false,
            )
        )
        session
    }

    suspend fun getLatestAiChatSession(userId: String): Result<AiChatSession?> = runCatching {
        db.collection(COL_CHAT_SESSIONS)
            .whereEqualTo("type", AiChatSession.TYPE_AI)
            .whereEqualTo("userId", userId)
            .whereEqualTo("isDeleted", false)
            .get()
            .await()
            .documents
            .mapNotNull { it.toAiChatSession() }
            .maxByOrNull { it.clientCreatedAtMillis }
    }

    fun getAiChatSessionsFlow(userId: String): Flow<List<AiChatSession>> =
        queryFlow(logLabel = "AI chat sessions listener error", query = {
            db.collection(COL_CHAT_SESSIONS)
                .whereEqualTo("type", AiChatSession.TYPE_AI)
                .whereEqualTo("userId", userId)
                .whereEqualTo("isDeleted", false)
        }) { snapshot ->
            snapshot?.documents
                ?.mapNotNull { it.toAiChatSession() }
                ?.sortedByDescending { it.clientCreatedAtMillis }
                .orEmpty()
        }

    suspend fun getAiChatSessionsOnce(userId: String): Result<List<AiChatSession>> = runCatching {
        db.collection(COL_CHAT_SESSIONS)
            .whereEqualTo("type", AiChatSession.TYPE_AI)
            .whereEqualTo("userId", userId)
            .whereEqualTo("isDeleted", false)
            .get()
            .await()
            .documents
            .mapNotNull { it.toAiChatSession() }
            .sortedByDescending { it.clientCreatedAtMillis }
    }

    fun updateAiChatSessionAfterMessage(sessionId: String, preview: String) {
        db.collection(COL_CHAT_SESSIONS).document(sessionId).update(
            mapOf(
                "lastMessage" to preview.take(160),
                "updatedAt" to FieldValue.serverTimestamp(),
                "messageCount" to FieldValue.increment(1),
            )
        )
    }

    suspend fun renameAiChatSessionFromServerTime(sessionId: String, title: String): Result<Unit> = runCatching {
        db.collection(COL_CHAT_SESSIONS).document(sessionId).update(
            mapOf(
                "title" to title,
                "titleStatus" to AiChatSession.TITLE_READY,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    suspend fun deleteAiChatSession(sessionId: String): Result<Unit> = runCatching {
        deleteAllMessages(sessionId).getOrThrow()
        db.collection(COL_CHAT_SESSIONS)
            .document(sessionId)
            .delete()
            .await()
    }

    private fun FirestoreMessage.toFirestoreMap(): Map<String, Any?> =
        mapOf(
            "messageId" to messageId,
            "sessionId" to sessionId,
            "senderId" to senderId,
            "receiverId" to receiverId,
            "content" to content,
            "timestamp" to timestamp,
            "isFromAI" to isFromAI,
            "fromAI" to isFromAI,
            "isRead" to isRead,
            "messageType" to messageType,
            "senderType" to senderType,
            "riskLevel" to riskLevel,
            "triggerCounselor" to triggerCounselor,
            "substanceDetected" to substanceDetected,
            "substancesDetected" to substancesDetected,
            "actionDestination" to actionDestination,
            "quickReplies" to quickReplies,
            "safetyFlags" to safetyFlags,
            "detectedSymptoms" to detectedSymptoms,
            "userIntent" to userIntent,
        )

    private fun DocumentSnapshot.toFirestoreMessage(): FirestoreMessage? {
        val data = data ?: return null
        return FirestoreMessage(
            messageId = (data["messageId"] as? String).orEmpty().ifBlank { id },
            sessionId = (data["sessionId"] as? String).orEmpty(),
            senderId = (data["senderId"] as? String).orEmpty(),
            receiverId = (data["receiverId"] as? String).orEmpty(),
            content = (data["content"] as? String).orEmpty(),
            isFromAI = data["isFromAI"] as? Boolean
                ?: data["fromAI"] as? Boolean
                ?: ((data["senderType"] as? String) == "ai" || (data["senderId"] as? String) == "ai"),
            isRead = data["isRead"] as? Boolean ?: false,
            messageType = (data["messageType"] as? String).orEmpty().ifBlank { "TEXT" },
            timestamp = data["timestamp"] as? Timestamp ?: Timestamp.now(),
            senderType = (data["senderType"] as? String).orEmpty(),
            riskLevel = (data["riskLevel"] as? String).orEmpty(),
            triggerCounselor = data["triggerCounselor"] as? Boolean ?: false,
            substanceDetected = (data["substanceDetected"] as? String).orEmpty(),
            substancesDetected = data.stringList("substancesDetected"),
            actionDestination = (data["actionDestination"] as? String).orEmpty(),
            quickReplies = data.stringList("quickReplies"),
            safetyFlags = data.stringList("safetyFlags"),
            detectedSymptoms = data.stringList("detectedSymptoms"),
            userIntent = (data["userIntent"] as? String).orEmpty(),
        )
    }

    private fun Map<String, Any>.stringList(key: String): List<String> =
        (this[key] as? List<*>)?.mapNotNull { it as? String }.orEmpty()

    private fun DocumentSnapshot.toAiChatSession(): AiChatSession? {
        val data = data ?: return null
        return AiChatSession(
            sessionId = (data["sessionId"] as? String).orEmpty().ifBlank { id },
            userId = (data["userId"] as? String).orEmpty(),
            title = (data["title"] as? String).orEmpty().ifBlank { "Untitled" },
            titleStatus = (data["titleStatus"] as? String).orEmpty().ifBlank { AiChatSession.TITLE_PENDING },
            createdAt = data["createdAt"] as? Timestamp,
            clientCreatedAt = data["clientCreatedAt"] as? Timestamp ?: Timestamp.now(),
            clientCreatedAtMillis = data["clientCreatedAtMillis"] as? Long
                ?: (data["clientCreatedAtMillis"] as? Number)?.toLong()
                ?: 0L,
            updatedAt = data["updatedAt"] as? Timestamp,
            lastMessage = (data["lastMessage"] as? String).orEmpty(),
            messageCount = (data["messageCount"] as? Long)?.toInt()
                ?: (data["messageCount"] as? Number)?.toInt()
                ?: 0,
            isDeleted = data["isDeleted"] as? Boolean ?: false,
            batchSummaries = data.stringList("batchSummaries"),
            summarizedThroughMs = (data["summarizedThroughMs"] as? Long)
                ?: (data["summarizedThroughMs"] as? Number)?.toLong()
                ?: 0L,
        )
    }

    /**
     * Appends a new batch summary to the session and records the timestamp
     * boundary up to which live history has been collapsed into summaries.
     * Used by the AI chat 16-message (8-user-prompt) summarisation cycle.
     */
    suspend fun appendAiChatBatchSummary(
        sessionId: String,
        summary: String,
        summarizedThroughMs: Long,
    ): Result<Unit> = runCatching {
        db.collection(COL_CHAT_SESSIONS).document(sessionId).update(
            mapOf(
                "batchSummaries" to FieldValue.arrayUnion(summary),
                "summarizedThroughMs" to summarizedThroughMs,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
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

    fun getCounselorsByNgoFlow(ngoId: String): Flow<List<CounselorProfile>> =
        queryFlow(query = { db.collection(COL_COUNSELORS).whereEqualTo("ngoId", ngoId) }) { snap ->
            snap?.toObjects(CounselorProfile::class.java) ?: emptyList()
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

    fun getCounselorChatsFlow(counselorId: String): Flow<List<Map<String, Any>>> =
        queryFlow(query = {
            db.collection(COL_CHAT_SESSIONS)
                .whereEqualTo("counselorId", counselorId)
                .orderBy("lastMessageAt", Query.Direction.DESCENDING)
        }) { snap -> snap?.documents?.mapNotNull { it.data } ?: emptyList() }

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

    suspend fun saveNotification(notif: AppNotification): Result<String> = runCatching {
        createWithId(COL_NOTIFICATIONS) { notif.copy(notificationId = it) }
    }

    suspend fun getNotifications(userId: String): Result<List<AppNotification>> = runCatching {
        db.collection(COL_NOTIFICATIONS)
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .await()
            .toObjects(AppNotification::class.java)
    }

    fun getNotificationsFlow(userId: String): Flow<List<AppNotification>> =
        queryFlow(query = {
            db.collection(COL_NOTIFICATIONS)
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
        }) { snap -> snap?.toObjects(AppNotification::class.java) ?: emptyList() }

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
        createWithId(COL_EMERGENCY_ALERTS) { alert.copy(alertId = it) }
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

    fun getAlertsFlow(ngoId: String): Flow<List<EmergencyAlert>> =
        queryFlow(query = {
            db.collection(COL_EMERGENCY_ALERTS)
                .whereEqualTo("ngoId", ngoId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
        }) { snap -> snap?.toObjects(EmergencyAlert::class.java) ?: emptyList() }

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
        createWithId(COL_COMMUNITY_POSTS) { post.copy(postId = it) }
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

    fun getCommunityPostsFlow(): Flow<List<CommunityPost>> =
        queryFlow(query = {
            db.collection(COL_COMMUNITY_POSTS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
        }) { snap ->
            snap?.toObjects(CommunityPost::class.java)?.filter { !it.isFlagged } ?: emptyList()
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
        createWithId(COL_CONNECTIONS) { connection.copy(connectionId = it) }
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
        createWithId(COL_REPORTS) { report.copy(reportId = it) }
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
