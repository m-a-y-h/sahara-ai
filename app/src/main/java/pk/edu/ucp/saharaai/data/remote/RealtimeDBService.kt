package pk.edu.ucp.saharaai.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import pk.edu.ucp.saharaai.ASSESSMENT_VALIDITY_MS
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

    // Characters Firebase Realtime Database forbids inside a path segment.
    // When validation fails the SDK throws "Invalid Firebase database
    // path: ... Firebase Database paths must not contain '.', '#', '$',
    // '[', or ']'" — the source of the cryptic "invalid token in path"
    // crash the assessment save was hitting.
    private val CHARS_INVALID_IN_RTDB_KEY = setOf('.', '#', '$', '[', ']', '/')

    private fun hasInvalidRtdbKeyChars(value: String): Boolean =
        value.any { it in CHARS_INVALID_IN_RTDB_KEY }

    private fun accessKeyPathSegment(key: String): String {
        val cleanKey = key.trim()
        require(cleanKey.isNotBlank()) { "Missing access key." }
        return if (hasInvalidRtdbKeyChars(cleanKey)) {
            "encoded_${sha256Hex(cleanKey)}"
        } else {
            cleanKey
        }
    }

    private fun accessKeyFromSnapshot(child: DataSnapshot, data: Map<String, Any>): String =
        data["issuedKey"]?.toString()?.takeIf { it.isNotBlank() }
            ?: data["key"]?.toString()?.takeIf { it.isNotBlank() }
            ?: child.key.orEmpty()

    private fun keyedAccessData(child: DataSnapshot, data: Map<String, Any>): Map<String, Any> {
        val issuedKey = accessKeyFromSnapshot(child, data)
        return data + mapOf(
            "key" to issuedKey,
            "pathKey" to child.key.orEmpty(),
        )
    }

    private const val TAG = "RealtimeDBService"

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    // One body for the hand-rolled callbackFlow+ValueEventListener blocks this
    // file used to repeat. `ref` is a lambda so path building still runs at
    // collect time (inside the flow), exactly like the inlined originals.
    private fun <T> rtdbFlow(
        logLabel: String = "RTDB listener",
        closeWithError: Boolean = false,
        ref: () -> Query,
        map: (DataSnapshot) -> T,
    ): Flow<T> = callbackFlow {
        val query = ref()
        val listener = query.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) { trySend(map(snap)) }
            override fun onCancelled(error: DatabaseError) {
                val exception = error.toException()
                Log.w(TAG, "$logLabel cancelled", exception)
                if (closeWithError) close(exception) else close()
            }
        })
        awaitClose { query.removeEventListener(listener) }
    }

    private enum class FT { STR, LNG, INT, BOOL, FLT }

    // Copies the listed fields into `entry` when present, preserving the
    // original per-field order and coercions (INT = Long→Int, FLT = Double→Float).
    private fun DataSnapshot.fieldsInto(entry: MutableMap<String, Any>, vararg fields: Pair<String, FT>) {
        for ((name, type) in fields) when (type) {
            FT.STR  -> child(name).getValue(String::class.java)?.let  { entry[name] = it }
            FT.LNG  -> child(name).getValue(Long::class.java)?.let    { entry[name] = it }
            FT.INT  -> child(name).getValue(Long::class.java)?.let    { entry[name] = it.toInt() }
            FT.BOOL -> child(name).getValue(Boolean::class.java)?.let { entry[name] = it }
            FT.FLT  -> child(name).getValue(Double::class.java)?.let  { entry[name] = it.toFloat() }
        }
    }

    private fun DataSnapshot.strOrNull(name: String): String? = child(name).getValue(String::class.java)
    private fun DataSnapshot.str(name: String): String = strOrNull(name).orEmpty()
    private fun DataSnapshot.lng(name: String, default: Long = 0L): Long = child(name).getValue(Long::class.java) ?: default
    private fun DataSnapshot.bool(name: String): Boolean = child(name).getValue(Boolean::class.java) ?: false
    private fun DataSnapshot.childMaps(): List<Map<String, Any>> = children.mapNotNull { it.value as? Map<String, Any> }

    private fun userRef(id: String) = db.getReference("users").child(id)
    private fun counselorKeyRef(k: String) = db.getReference("counselor_keys").child(accessKeyPathSegment(k))
    private fun chatSessionRef(userId: String, ck: String) =
        db.getReference("user_chats").child(chatSessionPath(userId, ck))

    // Shared shape of the three hand-rolled RTDB transactions. A null
    // `abortError` treats an uncommitted transaction as success (release path).
    private suspend fun DatabaseReference.awaitTransaction(
        abortError: (() -> Throwable)? = null,
        transform: (MutableData) -> Transaction.Result,
    ) {
        suspendCancellableCoroutine<Unit> { continuation ->
            runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result = transform(currentData)
                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                    when {
                        error != null -> continuation.resumeWithException(error.toException())
                        !committed && abortError != null -> continuation.resumeWithException(abortError())
                        else -> continuation.resume(Unit)
                    }
                }
            })
        }
    }

    data class PostAuthUserState(
        val isBlocked: Boolean,
        val blockReason: String = "",
        val onboardingCompleted: Boolean = false,
        val userData: Map<String, Any> = emptyMap(),
    )

    suspend fun saveUser(uid: String, name: String, email: String): Result<Unit> =
        ensureUserRecord(uid, name, email).map { }

    /**
     * Public, world-readable registry of "this email has a password account
     * on this project." Used by [GoogleCredentialAuth.signIn] to detect a
     * collision before consuming the Google credential, because Firebase's
     * Email Enumeration Protection (the post-2023 default) makes
     * `fetchSignInMethodsForEmail` return empty and silently lets the
     * Google sign-in replace the password provider on an unverified email.
     *
     * Stored as a hash of the email (SHA-256 via [emailKey]) so the
     * registry doesn't leak the raw addresses to anyone snooping the DB.
     * The recorded value is the UID, so a single read tells us both
     * "this email has a password" and "which Firebase user it belongs to."
     */
    suspend fun recordEmailHasPassword(uid: String, email: String): Result<Unit> = runCatching {
        require(uid.isNotBlank()) { "Missing user id." }
        val key = emailKey(email)
        if (key.isBlank()) return@runCatching
        db.getReference("email_password_index").child(key).setValue(uid).await()
    }.onFailure { Log.w(TAG, "recordEmailHasPassword($email) failed", it) }

    /** @return the UID associated with the email's password account, or null
     *  if no record exists. Readable without auth (the rule sets `.read=true`),
     *  so the Google sign-in path can call this BEFORE a user is signed in. */
    suspend fun lookupEmailHasPassword(email: String): String? = runCatching {
        val key = emailKey(email)
        if (key.isBlank()) return@runCatching null
        val snap = db.getReference("email_password_index").child(key).get().await()
        snap.getValue(String::class.java)?.takeIf { it.isNotBlank() }
    }.onFailure { Log.w(TAG, "lookupEmailHasPassword($email) failed", it) }
        .getOrNull()

    suspend fun ensureUserRecord(uid: String, name: String, email: String): Result<Map<String, Any>> = runCatching {
        require(uid.isNotBlank()) { "Missing user id." }
        val ref = userRef(uid)
        val snap = ref.get().await()
        val now = System.currentTimeMillis()
        val existing = snap.value as? Map<String, Any>
        val updates = mutableMapOf<String, Any>(
            "uid" to uid,
            "updatedAt" to now,
        )
        // Prefer the value already on record. A later Google sign-in passes the
        // Google display name / email, which previously clobbered what the user
        // registered with. Once a field is set we keep it; the incoming values
        // only fill blanks (i.e. brand-new records). Name edits go through
        // updateUserName, not here.
        val existingName = existing?.get("name")?.toString().orEmpty()
        val existingEmail = existing?.get("email")?.toString().orEmpty()
        val cleanName = existingName.ifBlank { name.trim() }.ifBlank { "Sahara User" }
        val cleanEmail = existingEmail.ifBlank { email.trim().lowercase() }
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
        userRef(uid).updateChildren(
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
        userRef(uid).get().await().value as? Map<String, Any>
    }

    suspend fun getUserDisplayName(uid: String): String = runCatching {
        userRef(uid).child("name")
            .get().await().getValue(String::class.java) ?: ""
    }.getOrDefault("")

    suspend fun updateUserName(uid: String, newName: String): Result<Unit> = runCatching {
        userRef(uid)
            .updateChildren(mapOf("name" to newName)).await()
    }

    suspend fun updateUserRegion(uid: String, region: String): Result<Unit> = runCatching {
        userRef(uid)
            .updateChildren(mapOf("region" to region.trim())).await()
    }

    suspend fun updateUserAvatarId(uid: String, avatarId: String): Result<Unit> = runCatching {
        userRef(uid).updateChildren(
            mapOf(
                "avatarId" to avatarId,
                "customAvatarStatus" to "",
                "customAvatarUrl" to ""
            )
        ).await()
    }

    suspend fun getUserRegion(uid: String): String = runCatching {
        userRef(uid).child("region")
            .get().await().getValue(String::class.java) ?: ""
    }.getOrDefault("")

    suspend fun getUserCreatedAt(uid: String): Long = runCatching {
        userRef(uid).child("createdAt")
            .get().await().getValue(Long::class.java) ?: 0L
    }.getOrDefault(0L)

    suspend fun logFaceLogin(uid: String): Result<Unit> = runCatching {
        db.getReference("user_activity").child(uid).child("faceLogins")
            .push().setValue(System.currentTimeMillis()).await()
    }

    suspend fun setBiometricEnabled(uid: String, enabled: Boolean): Result<Unit> = runCatching {
        userRef(uid)
            .updateChildren(mapOf("biometricEnabled" to enabled)).await()
    }

    suspend fun getBiometricEnabled(uid: String): Boolean = runCatching {
        userRef(uid).child("biometricEnabled")
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
        val cleanKey = key.trim()
        val ref = counselorKeyRef(cleanKey)
        val snap = ref.get().await()
        val data = snap.value as? Map<String, Any> ?: return@runCatching null
        keyedAccessData(snap, data)
    }

    suspend fun getCounselorKeyByUid(uid: String): Result<Pair<String, Map<String, Any>>?> = runCatching {
        val snap = db.getReference("counselor_keys").get().await()
        for (child in snap.children) {
            val data = child.value as? Map<String, Any> ?: continue
            val key = accessKeyFromSnapshot(child, data)
            if (data["uid"]?.toString() == uid) return@runCatching Pair(key, keyedAccessData(child, data))
        }
        null
    }

    suspend fun saveCounselorSetup(
        key: String, uid: String, name: String,
        feePkr: Int,
        ngoName: String, region: String, bio: String
    ): Result<Unit> = runCatching {
        require(feePkr in 0..5000) { "Counselor fee must be between PKR 0 and PKR 5000." }
        counselorKeyRef(key).updateChildren(mapOf(
            "issuedKey"       to key.trim(),
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

    /** Manual "appear offline" override. Counselors are auto-online via the
     *  Firebase presence helper below; flipping this on makes them appear
     *  offline to users even though their app is connected. */
    suspend fun setCounselorInvisible(key: String, invisible: Boolean): Result<Unit> = runCatching {
        counselorKeyRef(key).child("isInvisible").setValue(invisible).await()
    }

    /** Firebase presence wiring for a counselor's dashboard session.
     *
     *  Watches `.info/connected`; when this client is connected, registers an
     *  `onDisconnect` that flips `isOnline` to `false`, then writes `true`.
     *  The returned closer cancels the listener and force-marks the counselor
     *  offline — call it from the screen's `DisposableEffect.onDispose`.
     *
     *  Effective visibility to users is `isOnline && !isInvisible` — see
     *  [listenToAllActiveCounselors] below. */
    fun setupCounselorPresence(key: String): () -> Unit {
        if (key.isBlank()) return {}
        val onlineRef    = counselorKeyRef(key).child("isOnline")
        val connectedRef = db.getReference(".info/connected")
        val listener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val connected = snap.value as? Boolean ?: false
                if (connected) {
                    onlineRef.onDisconnect().setValue(false)
                    onlineRef.setValue(true)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "counselor presence listener cancelled", error.toException())
            }
        }
        connectedRef.addValueEventListener(listener)
        return {
            connectedRef.removeEventListener(listener)
            runCatching { onlineRef.onDisconnect().cancel() }
            runCatching { onlineRef.setValue(false) }
        }
    }

    /** All counselors with a complete profile and active status, REGARDLESS of
     *  online state. Each entry carries `key`, `isOnline`, `isInvisible`, and
     *  the derived `effectiveOnline = isOnline && !isInvisible` so callers
     *  can sort and badge without duplicating the rule. */
    fun listenToAllActiveCounselors(): Flow<List<Map<String, Any>>> =
        rtdbFlow(ref = { db.getReference("counselor_keys") }) { snap ->
            val list = mutableListOf<Map<String, Any>>()
            for (child in snap.children) {
                val data = child.value as? Map<String, Any> ?: continue
                val isActive   = data["isActive"] as? Boolean ?: false
                val hasProfile = data["profileComplete"] as? Boolean ?: false
                if (!(isActive && hasProfile)) continue
                val isOnline    = data["isOnline"]    as? Boolean ?: false
                val isInvisible = data["isInvisible"] as? Boolean ?: false
                list.add(
                    keyedAccessData(child, data) + mapOf(
                        "effectiveOnline" to (isOnline && !isInvisible),
                    )
                )
            }
            list
        }

    suspend fun setCounselorCallAvailability(key: String, enabled: Boolean): Result<Unit> = runCatching {
        require(key.isNotBlank()) { "Missing counselor key." }
        counselorKeyRef(key).updateChildren(
            mapOf(
                "callEnabled" to enabled,
                "callAvailabilityUpdatedAt" to System.currentTimeMillis(),
            )
        ).await()
    }

    fun listenToOnlineCounselors(): Flow<List<Map<String, Any>>> =
        rtdbFlow(
            logLabel = "online counselors listener",
            closeWithError = true,
            ref = { db.getReference("counselor_keys") },
        ) { snap ->
            val list = mutableListOf<Map<String, Any>>()
            for (child in snap.children) {
                val data = child.value as? Map<String, Any> ?: continue
                val isOnline  = data["isOnline"]  as? Boolean ?: false
                val isActive  = data["isActive"]  as? Boolean ?: false
                val hasProfile = data["profileComplete"] as? Boolean ?: false
                if (isOnline && isActive && hasProfile) {
                    list.add(keyedAccessData(child, data))
                }
            }
            list
        }

    fun listenToCounselorData(key: String): Flow<Map<String, Any>?> =
        rtdbFlow(
            logLabel = "counselor data listener",
            closeWithError = true,
            ref = { counselorKeyRef(key) },
        ) { snap -> (snap.value as? Map<String, Any>)?.let { keyedAccessData(snap, it) } }

    suspend fun updateRating(key: String, newRating: Float): Result<Unit> = runCatching {
        val ref  = counselorKeyRef(key)
        val snap = ref.get().await()
        val data = snap.value as? Map<String, Any> ?: return@runCatching
        val total = (data["totalRatings"] as? Long)?.toInt() ?: 0
        val cur   = (data["rating"] as? Double) ?: 0.0
        val avg   = if (total == 0) newRating.toDouble() else ((cur * total) + newRating) / (total + 1)
        ref.updateChildren(mapOf("rating" to avg, "totalRatings" to total + 1)).await()
    }

    fun chatSessionPath(uid: String, counselorKey: String) = "${uid}_${accessKeyPathSegment(counselorKey)}"

    private suspend fun writeChatMessage(
        sessionUid: String, counselorKey: String,
        senderId: String, senderType: String, text: String,
    ) {
        val msgId = UUID.randomUUID().toString()
        chatSessionRef(sessionUid, counselorKey)
            .child("messages")
            .child(msgId)
            .setValue(mapOf(
                "messageId"    to msgId,
                "text"         to text,
                "senderId"     to senderId,
                "senderType"   to senderType,
                "counselorKey" to counselorKey,
                "timestamp"    to System.currentTimeMillis()
            )).await()
    }

    suspend fun sendChatMessage(
        uid: String, counselorKey: String,
        text: String, senderType: String
    ): Result<Unit> = runCatching {
        writeChatMessage(uid, counselorKey, senderId = uid, senderType = senderType, text = text)
    }

    suspend fun sendChatMessageFromCounselor(
        userUid: String, counselorKey: String,
        counselorUid: String, text: String
    ): Result<Unit> = runCatching {
        writeChatMessage(userUid, counselorKey, senderId = counselorUid, senderType = "counselor", text = text)
    }

    fun listenToChatMessages(uid: String, counselorKey: String): Flow<List<Map<String, Any>>> =
        rtdbFlow(ref = { chatSessionRef(uid, counselorKey).child("messages") }) { snap ->
            snap.childMaps().sortedBy { (it["timestamp"] as? Long) ?: 0L }
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
        val counselorPathKey = accessKeyPathSegment(counselorKey)
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val sessions = mutableListOf<Map<String, Any>>()
                for (child in snap.children) {
                    val sessionId = child.key ?: continue
                    if (!sessionId.endsWith("_$counselorPathKey")) continue
                    val uid       = sessionId.removeSuffix("_$counselorPathKey")
                    val messages  = child.child("messages").children
                        .mapNotNull { it.value as? Map<String, Any> }
                        .sortedBy { (it["timestamp"] as? Long) ?: 0L }
                    val lastMsg   = messages.lastOrNull()?.get("text")?.toString() ?: ""
                    val rawCounselorKey = child.child("counselorKey").getValue(String::class.java)
                        ?: counselorKey
                    sessions.add(mapOf(
                        "sessionId"    to sessionId,
                        "uid"          to uid,
                        "lastMessage"  to lastMsg,
                        "counselorKey" to rawCounselorKey,
                        "expiresAt"    to (child.child("expiresAt").getValue(Long::class.java) ?: 0L),
                        "blocked"      to child.child("blocked").exists(),
                    ))
                }
                trySend(sessions)
            }
            override fun onCancelled(error: DatabaseError) { Log.w(TAG, "RTDB listener cancelled", error.toException()); close() }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    fun listenToUserCounselorChats(uid: String): Flow<List<Map<String, Any>>> =
        rtdbFlow(ref = { db.getReference("user_chats") }) { snap ->
            val sessions = mutableListOf<Map<String, Any>>()
            for (child in snap.children) {
                val sessionId = child.key ?: continue
                if (!sessionId.startsWith("${uid}_")) continue
                val counselorPathKey = sessionId.removePrefix("${uid}_")
                val counselorKey = child.strOrNull("counselorKey") ?: counselorPathKey
                val messages = child.child("messages").childMaps()
                    .sortedBy { (it["timestamp"] as? Long) ?: 0L }
                val last = messages.lastOrNull()
                val counselorName = child.strOrNull("counselorName")
                    ?: child.strOrNull("assignedName")
                    ?: counselorKey
                sessions.add(
                    mapOf(
                        "sessionId" to sessionId,
                        "uid" to uid,
                        "lastMessage" to (last?.get("text")?.toString() ?: ""),
                        "lastTimestamp" to ((last?.get("timestamp") as? Long) ?: 0L),
                        "counselorKey" to counselorKey,
                        "counselorName" to counselorName,
                        "expiresAt" to child.lng("expiresAt"),
                        "blocked" to child.child("blocked").exists(),
                    )
                )
            }
            sessions.sortedByDescending { it["lastTimestamp"] as? Long ?: 0L }
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

    fun listenToCommunityPosts(): Flow<List<Map<String, Any>>> =
        rtdbFlow(ref = { db.getReference("community_posts") }) { snap ->
            snap.childMaps()
                .filter { it["isFlagged"] as? Boolean != true }
                .sortedByDescending { (it["timestamp"] as? Long) ?: 0L }
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

    fun listenToReplies(postId: String): Flow<List<Map<String, Any>>> =
        rtdbFlow(ref = { db.getReference("community_replies").child(postId) }) { snap ->
            snap.childMaps().sortedBy { (it["timestamp"] as? Long) ?: 0L }
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

    fun listenToUserNotifications(uid: String): Flow<List<Map<String, Any>>> =
        rtdbFlow(ref = { db.getReference("user_notifications").child(uid) }) { snap ->
            snap.childMaps().sortedByDescending { (it["timestamp"] as? Long) ?: 0L }
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

    fun listenChatIdentityVisible(userUid: String, counselorKey: String): Flow<Boolean> {
        if (userUid.isBlank() || counselorKey.isBlank()) return flowOf(false)
        return rtdbFlow(ref = {
            chatSessionRef(userUid, counselorKey).child("privacy").child("identityVisible")
        }) { it.getValue(Boolean::class.java) ?: false }
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
        userRef(userUid)
            .child("isIdentityVisibleToCounselors")
            .setValue(visible).await()
    }

    fun listenChatSessionMeta(userUid: String, counselorKey: String): Flow<Map<String, Any>> {
        if (userUid.isBlank() || counselorKey.isBlank()) return flowOf(emptyMap())
        return rtdbFlow(ref = { chatSessionRef(userUid, counselorKey) }) { snapshot ->
            val meta = mutableMapOf<String, Any>()
            snapshot.fieldsInto(meta, "sessionStartedAt" to FT.LNG, "expiresAt" to FT.LNG)
            snapshot.child("blocked").value?.let { meta["blocked"] = it }
            meta
        }
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

    fun listenToAvatarRequests(): Flow<List<AvatarRequest>> =
        rtdbFlow(ref = { db.getReference("avatar_requests") }) { snapshot ->
            snapshot.children.mapNotNull(::avatarRequestFrom).sortedByDescending { it.createdAt }
        }

    private suspend fun reviewAvatarRequest(
        request: AvatarRequest, reviewedBy: String, comment: String,
        status: String, extraUserUpdates: Map<String, Any>,
        titleEn: String, titleUr: String, defaultBodyEn: String, defaultBodyUr: String,
    ) {
        val now = System.currentTimeMillis()
        db.getReference("avatar_requests").child(request.requestId).updateChildren(
            mapOf(
                "status" to status,
                "adminComment" to comment.trim(),
                "reviewedBy" to reviewedBy,
                "reviewedAt" to now,
            )
        ).await()
        userRef(request.userId).updateChildren(
            extraUserUpdates + mapOf(
                "customAvatarStatus" to status,
                "customAvatarReviewedAt" to now,
            )
        ).await()
        saveUserNotification(
            uid = request.userId,
            titleEn = titleEn,
            titleUr = titleUr,
            bodyEn = comment.ifBlank { defaultBodyEn },
            bodyUr = comment.ifBlank { defaultBodyUr },
            type = "SYSTEM",
            actionRoute = "profile",
        ).getOrThrow()
    }

    suspend fun approveAvatarRequest(request: AvatarRequest, reviewedBy: String, comment: String): Result<Unit> = runCatching {
        reviewAvatarRequest(
            request, reviewedBy, comment,
            status = "APPROVED",
            extraUserUpdates = mapOf("customAvatarUrl" to request.fileUrl),
            titleEn = "Avatar approved",
            titleUr = "Avatar approve ho gaya",
            defaultBodyEn = "Your custom avatar is now visible on your profile.",
            defaultBodyUr = "Aapka custom avatar ab profile par nazar aayega.",
        )
    }

    suspend fun rejectAvatarRequest(request: AvatarRequest, reviewedBy: String, comment: String): Result<Unit> = runCatching {
        reviewAvatarRequest(
            request, reviewedBy, comment,
            status = "REJECTED",
            extraUserUpdates = emptyMap(),
            titleEn = "Avatar not approved",
            titleUr = "Avatar approve nahi hua",
            defaultBodyEn = "Your custom avatar request was not approved.",
            defaultBodyUr = "Aapki custom avatar request approve nahi hui.",
        )
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
            userRef(uid).updateChildren(
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

    /** Persist the rule-based classifier's output for the user's subscriptions
     *  list. Written to a sibling node `social_connections/{uid}/youtube_flags`
     *  so the raw subscription store stays clean and so the risk aggregator can
     *  read just the summary without scanning the full list. The schema mirrors
     *  the per-channel record produced by `YouTubeSubscriptionClassifier`. */
    suspend fun saveYouTubeFlaggedSubscriptions(
        uid: String,
        totalSubscriptions: Int,
        flagged: List<Map<String, Any>>,
        overallSeverity: String,
        recoveryChannelCount: Int,
    ): Result<Unit> = runCatching {
        require(uid.isNotBlank()) { "Missing user id." }
        val now = System.currentTimeMillis()
        db.getReference("social_connections").child(uid).child("youtube_flags")
            .setValue(mapOf(
                "evaluatedAt"          to now,
                "totalSubscriptions"   to totalSubscriptions,
                "flaggedCount"         to flagged.size,
                "overallSeverity"      to overallSeverity,
                "recoveryChannelCount" to recoveryChannelCount,
                "flagged"              to flagged,
            )).await()
    }

    suspend fun removeSocialConnection(uid: String, platform: String): Result<Unit> = runCatching {
        db.getReference("social_connections").child(uid).child(platform).removeValue().await()
    }

    fun listenToSocialConnections(uid: String): Flow<Map<String, SocialPlatformConnection>> =
        rtdbFlow(ref = { db.getReference("social_connections").child(uid) }) { snap ->
            val map = mutableMapOf<String, SocialPlatformConnection>()
            for (child in snap.children) {
                val platform = child.key ?: continue
                val username = child.strOrNull("username") ?: continue
                val dataExpiresAt = child.lng("dataExpiresAt")
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
                    did = child.str("did"),
                    externalId = child.str("externalId"),
                    authMethod = child.str("authMethod"),
                    dataAccess = child.str("dataAccess"),
                    verified = child.bool("verified"),
                    consentVersion = child.str("consentVersion"),
                    consentedAt = child.lng("consentedAt"),
                    connectedAt = child.lng("connectedAt"),
                    dataExpiresAt = dataExpiresAt,
                    subscriptionCount = child.lng("subscriptionCount").toInt(),
                    analysisEnabled = child.bool("analysisEnabled")
                )
            }
            map
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
    ): Flow<Map<String, Map<String, Any>>> =
        rtdbFlow(ref = {
            db.getReference("sleep").child(uid).orderByKey().startAt(mondayDate).endAt(sundayDate)
        }) { snap ->
            val map = mutableMapOf<String, Map<String, Any>>()
            for (child in snap.children) {
                val date = child.key ?: continue
                val entry = mutableMapOf<String, Any>()
                child.fieldsInto(
                    entry,
                    "bedtime" to FT.STR, "waketime" to FT.STR, "hours" to FT.FLT,
                    "qualityType" to FT.STR, "source" to FT.STR, "timeZoneId" to FT.STR,
                    "automatic" to FT.BOOL, "confidence" to FT.FLT, "sourceReason" to FT.STR,
                )
                if (entry.isNotEmpty()) map[date] = entry
            }
            map
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

    fun listenToMoodLogs(uid: String, limit: Int = 50): Flow<List<Map<String, Any>>> =
        rtdbFlow(ref = { db.getReference("mood_logs").child(uid).orderByKey().limitToLast(limit) }) { snap ->
            val list = mutableListOf<Map<String, Any>>()
            for (child in snap.children) {
                val date = child.key ?: continue
                val entry = mutableMapOf<String, Any>()
                entry["id"]   = date
                entry["date"] = date
                child.fieldsInto(entry, "mood" to FT.STR, "emoji" to FT.STR, "labelEn" to FT.STR, "timestamp" to FT.LNG)
                if (entry.size > 2) list.add(entry)
            }
            list.reversed()
        }

    fun listenToAllCounselorKeys(): Flow<List<Map<String, Any>>> =
        rtdbFlow(ref = { db.getReference("counselor_keys") }) { snap ->
            snap.children.mapNotNull { child ->
                val data = child.value as? Map<String, Any> ?: return@mapNotNull null
                val complete = data["profileComplete"] as? Boolean ?: false
                if (!complete) return@mapNotNull null
                keyedAccessData(child, data)
            }
        }

    fun listenToUserCount(): Flow<Int> =
        rtdbFlow(ref = { db.getReference("users") }) { it.childrenCount.toInt() }

    fun listenToChatSessionCount(): Flow<Int> =
        rtdbFlow(ref = { db.getReference("user_chats") }) { it.childrenCount.toInt() }

    private suspend fun requireRealtimeConnection(
        message: String = "Internet connection is required to save the assessment."
    ) {
        // Best-effort online check. Keep it non-fatal: RTDB has its own offline
        // queue so a transient null/false reading from .info/connected
        // shouldn't block a save that would otherwise succeed once the
        // socket reconnects. We only throw when the check itself raised a
        // real exception (e.g. project misconfigured).
        val ok = runCatching {
            db.getReference(".info/connected")
                .get()
                .await()
                .getValue(Boolean::class.java) == true
        }.onFailure {
            Log.w(TAG, ".info/connected probe threw", it)
        }.getOrNull()
        if (ok == false) {
            // Probe returned a definitive "not connected". Surface that with a
            // clear message; the snackbar will read it back as-is.
            throw IllegalStateException(message)
        }
    }

    private fun parseAssessmentEntry(child: DataSnapshot): Map<String, Any>? {
        val entry = mutableMapOf<String, Any>()
        entry["id"] = child.key ?: ""
        child.fieldsInto(
            entry,
            "score" to FT.INT, "quizType" to FT.STR, "riskLevel" to FT.STR,
            "date" to FT.STR, "timestamp" to FT.LNG,
        )
        return if (entry.containsKey("score")) entry else null
    }

    private fun formatAssessmentUnlockDate(timestampMs: Long): String {
        val unlockAt = timestampMs + ASSESSMENT_VALIDITY_MS
        return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(unlockAt))
    }

    suspend fun saveAssessment(
        uid: String,
        quizType: String,
        score: Int,
        riskLevel: String,
        date: String,
        answers: Map<Int, Triple<String, String, Boolean>>
    ): Result<Long> = runCatching {
        require(uid.isNotBlank()) { "Missing user id." }
        // Reject UIDs containing characters RTDB forbids in path keys.
        // Real Firebase UIDs are alphanumeric-only, but a corrupted auth
        // state could otherwise surface as the cryptic "invalid token in
        // path" Firebase error which gave us no clue where to look.
        require(uid.none { it in CHARS_INVALID_IN_RTDB_KEY }) {
            "Sign-in returned an invalid user id; please sign out and back in."
        }
        requireRealtimeConnection()
        // Best-effort 6-month-lock pre-check. If the read itself fails for
        // any reason, just skip the gate and let the write attempt proceed —
        // we'd rather permit an extra save than block the user behind a
        // network hiccup.
        val latest = loadLatestAssessmentResult(uid).getOrNull()
        val latestTimestamp = latest?.get("timestamp") as? Long ?: 0L
        if (latestTimestamp > 0L && System.currentTimeMillis() - latestTimestamp <= ASSESSMENT_VALIDITY_MS) {
            throw IllegalStateException(
                "Assessment already exists. Next reassessment is available from ${formatAssessmentUnlockDate(latestTimestamp)}."
            )
        }

        val ref = db.getReference("assessment").child(uid).push()

        val answersMap = answers.entries.associate { (idx, triple) ->
            idx.toString() to mapOf(
                "questionEn" to triple.first,
                "questionUr" to triple.second,
                "answer"     to triple.third   
            )
        }

        ref.setValue(
            mapOf<String, Any>(
                "quizType"       to quizType,
                "score"          to score,
                "totalQuestions" to answers.size,
                "riskLevel"      to riskLevel,
                "date"           to date,
                "timestamp"      to ServerValue.TIMESTAMP,
                "answers"        to answersMap
            )
        ).await()
        val savedTimestamp = ref.child("timestamp").get().await().getValue(Long::class.java) ?: 0L
        check(savedTimestamp > 0L) { "Server timestamp could not be verified." }
        savedTimestamp
    }.onFailure {
        // Log the full path + exception so the next "invalid token in path"
        // (or whatever Firebase throws) is diagnosable from logcat instead
        // of just the snackbar's truncated string.
        Log.e(TAG, "saveAssessment failed (uid='$uid', quizType='$quizType')", it)
    }

    suspend fun loadLatestAssessmentResult(uid: String): Result<Map<String, Any>?> = runCatching {
        val snap = db.getReference("assessment").child(uid)
            .orderByChild("timestamp").limitToLast(1).get().await()
        snap.children.lastOrNull()?.let(::parseAssessmentEntry)
    }

    suspend fun loadLatestAssessment(uid: String): Map<String, Any>? =
        loadLatestAssessmentResult(uid).getOrNull()

    fun listenToAssessments(uid: String): Flow<List<Map<String, Any>>> =
        rtdbFlow(ref = { db.getReference("assessment").child(uid).orderByChild("timestamp") }) { snap ->
            val list = mutableListOf<Map<String, Any>>()
            for (child in snap.children) {
                val entry = mutableMapOf<String, Any>()
                entry["id"] = child.key ?: continue
                child.fieldsInto(
                    entry,
                    "quizType" to FT.STR, "score" to FT.INT, "totalQuestions" to FT.INT,
                    "riskLevel" to FT.STR, "date" to FT.STR, "timestamp" to FT.LNG,
                )
                val answersMap = mutableMapOf<String, Map<String, Any>>()
                for (aq in child.child("answers").children) {
                    val aIdx = aq.key ?: continue
                    val aEntry = mutableMapOf<String, Any>()
                    aq.fieldsInto(aEntry, "questionEn" to FT.STR, "questionUr" to FT.STR, "answer" to FT.BOOL)
                    if (aEntry.isNotEmpty()) answersMap[aIdx] = aEntry
                }
                if (answersMap.isNotEmpty()) entry["answers"] = answersMap
                if (entry.containsKey("quizType")) list.add(entry)
            }
            list.reversed()
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

    fun listenToScreenTime(uid: String, limit: Int = 30): Flow<List<Map<String, Any>>> =
        rtdbFlow(ref = { db.getReference("screen_time").child(uid).orderByKey().limitToLast(limit) }) { snap ->
            val list = mutableListOf<Map<String, Any>>()
            for (child in snap.children) {
                val date  = child.key ?: continue
                val entry = mutableMapOf<String, Any>()
                entry["date"] = date
                child.fieldsInto(entry, "totalMs" to FT.LNG, "savedAt" to FT.LNG)
                val appsMap = mutableMapOf<String, Map<String, Any>>()
                for (app in child.child("apps").children) {
                    val pkg   = app.key ?: continue
                    val aData = mutableMapOf<String, Any>()
                    app.fieldsInto(aData, "packageName" to FT.STR, "appName" to FT.STR, "timeMs" to FT.LNG)
                    if (aData.isNotEmpty()) appsMap[pkg] = aData
                }
                if (appsMap.isNotEmpty()) entry["apps"] = appsMap
                if (entry.containsKey("totalMs")) list.add(entry)
            }
            list.reversed()
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
        snap.fieldsInto(
            result,
            "streak" to FT.LNG, "longestStreak" to FT.LNG,
            "totalDays" to FT.LNG, "lastCheckIn" to FT.STR,
        )

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
            child.fieldsInto(
                entry,
                "date" to FT.STR, "score" to FT.INT, "riskLevel" to FT.STR,
                "quizType" to FT.STR, "timestamp" to FT.LNG,
            )
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

    private suspend fun loadLeaderboardOrdered(field: String, limit: Int): List<Map<String, Any>> = runCatching {
        val snap = db.getReference("leaderboard")
            .orderByChild(field)
            .limitToLast(limit)
            .get().await()

        val list = mutableListOf<Map<String, Any>>()
        for (child in snap.children) {
            val entry = mutableMapOf<String, Any>()
            entry["uid"] = child.key ?: continue
            child.fieldsInto(
                entry,
                "displayName" to FT.STR, "streak" to FT.LNG, "longestStreak" to FT.LNG,
                "totalDays" to FT.LNG, "lastUpdated" to FT.LNG,
            )
            if (entry.containsKey("displayName")) list.add(entry)
        }
        list.reversed()
    }.getOrDefault(emptyList())

    suspend fun loadLeaderboard(limit: Int = 50): List<Map<String, Any>> =
        loadLeaderboardOrdered("streak", limit)

    suspend fun loadLeaderboardByDays(limit: Int = 50): List<Map<String, Any>> =
        loadLeaderboardOrdered("totalDays", limit)

    suspend fun startGameRecovery(uid: String, alias: String, location: String): Result<Map<String, Any>> = runCatching {
        require(uid.isNotBlank()) { "Missing user id." }
        val cleanAlias = alias.trim()
        require(cleanAlias.isNotBlank()) { "Choose an anonymous name first." }
        require(location.trim().isNotBlank()) { "Could not detect your location yet." }
        requireRealtimeConnection("Internet connection is required to start Game Recovery.")

        val ref  = db.getReference("game_recovery").child(uid)
        val snap = ref.get().await()
        val existingAlias = snap.child("alias").getValue(String::class.java).orEmpty()
        if (snap.exists() && existingAlias.isNotBlank()) {
            if (!existingAlias.equals(cleanAlias, ignoreCase = true)) {
                throw IllegalStateException("Your anonymous name is already locked and cannot be changed.")
            }
            val existingLocation = snap.child("location").getValue(String::class.java).orEmpty()
            if (location.isNotBlank() && existingLocation.isBlank()) {
                ref.child("location").setValue(location).await()
            }
            val updated = ref.get().await()
            syncGameLeaderboard(uid, updated)
            return@runCatching mapOf(
                "alias" to existingAlias,
                "location" to (updated.child("location").getValue(String::class.java) ?: location),
                "totalXp" to (updated.child("totalXp").getValue(Long::class.java) ?: 0L),
                "dailyXp" to (updated.child("dailyXp").getValue(Long::class.java) ?: 0L),
                "weeklyXp" to (updated.child("weeklyXp").getValue(Long::class.java) ?: 0L),
                "level" to (updated.child("level").getValue(Long::class.java) ?: 1L),
                "completedToday" to emptySet<String>(),
            )
        }

        reserveGameAlias(uid, cleanAlias)
        val now = System.currentTimeMillis()
        if (!snap.exists()) {
            ref.setValue(mapOf(
                "alias"          to cleanAlias,
                "location"       to location,
                "totalXp"        to 0L,
                "dailyXp"        to 0L,
                "weeklyXp"       to 0L,
                "level"          to 1L,
                "lastDailyDate"  to "",
                "lastWeeklyDate" to "",
                "aliasLocked"    to true,
                "aliasLockedAt"  to now,
            )).await()
        } else {
            ref.updateChildren(mapOf(
                "alias" to cleanAlias,
                "location" to location,
                "aliasLocked" to true,
                "aliasLockedAt" to now,
            )).await()
        }
        val updated = ref.get().await()
        syncGameLeaderboard(uid, updated)
        mapOf(
            "alias" to cleanAlias,
            "location" to location,
            "totalXp" to (updated.child("totalXp").getValue(Long::class.java) ?: 0L),
            "dailyXp" to (updated.child("dailyXp").getValue(Long::class.java) ?: 0L),
            "weeklyXp" to (updated.child("weeklyXp").getValue(Long::class.java) ?: 0L),
            "level" to (updated.child("level").getValue(Long::class.java) ?: 1L),
            "completedToday" to emptySet<String>(),
        )
    }.onFailure {
        Log.w(TAG, "startGameRecovery failed", it)
    }

    suspend fun saveGameAlias(uid: String, alias: String, location: String): Result<Unit> = runCatching {
        startGameRecovery(uid, alias, location).getOrThrow()
    }

    private suspend fun reserveGameAlias(uid: String, alias: String) {
        val aliasKey = gameAliasKey(alias)
        val existingAliasSnap = db.getReference("game_leaderboard")
            .orderByChild("alias")
            .equalTo(alias)
            .limitToFirst(1)
            .get()
            .await()
        for (child in existingAliasSnap.children) {
            val existingUid = child.key.orEmpty()
            if (existingUid.isNotBlank() && existingUid != uid) {
                throw IllegalStateException("This anonymous name is already taken. Please choose another.")
            }
        }

        db.getReference("game_alias_index").child(aliasKey).awaitTransaction(
            abortError = { IllegalStateException("This anonymous name is already taken. Please choose another.") },
        ) { currentData ->
            val currentUid = currentData.child("uid").getValue(String::class.java).orEmpty()
            if (currentUid.isNotBlank() && currentUid != uid) return@awaitTransaction Transaction.abort()
            currentData.value = mapOf(
                "uid" to uid,
                "alias" to alias,
                "aliasKey" to aliasKey,
                "createdAt" to System.currentTimeMillis(),
            )
            Transaction.success(currentData)
        }
    }

    private suspend fun syncGameLeaderboard(uid: String, snap: DataSnapshot) {
        val alias = snap.child("alias").getValue(String::class.java).orEmpty()
        if (alias.isBlank()) return
        db.getReference("game_leaderboard").child(uid).updateChildren(mapOf(
            "alias" to alias,
            "location" to snap.child("location").getValue(String::class.java).orEmpty(),
            "totalXp" to (snap.child("totalXp").getValue(Long::class.java) ?: 0L),
            "dailyXp" to (snap.child("dailyXp").getValue(Long::class.java) ?: 0L),
            "weeklyXp" to (snap.child("weeklyXp").getValue(Long::class.java) ?: 0L),
            "lastDailyDate" to snap.child("lastDailyDate").getValue(String::class.java).orEmpty(),
            "lastWeeklyDate" to snap.child("lastWeeklyDate").getValue(String::class.java).orEmpty(),
            "lastUpdated" to System.currentTimeMillis()
        )).await()
    }

    private fun gameAliasKey(alias: String): String = sha256Hex(alias.trim().lowercase(Locale.US))

    private fun completedTaskIds(snap: DataSnapshot, today: String): MutableSet<String> {
        val done = mutableSetOf<String>()
        for (c in snap.child("completedTasks").child(today).children) c.key?.let { done.add(it) }
        return done
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

        val completedToday = completedTaskIds(snap, today)

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
            val done = completedTaskIds(snap, today)
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

        val done = completedTaskIds(snap, today)
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
                    child.fieldsInto(
                        entry,
                        "packageName" to FT.STR, "appName" to FT.STR, "startTimeMillis" to FT.LNG,
                        "endTimeMillis" to FT.LNG, "durationMillis" to FT.LNG, "date" to FT.STR,
                    )
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
            child.fieldsInto(
                entry,
                "alias" to FT.STR, "location" to FT.STR, "totalXp" to FT.LNG,
                "dailyXp" to FT.LNG, "weeklyXp" to FT.LNG,
            )
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
        requiredDocumentKeys: List<String>,
        // Captured at submit time. Stored verbatim so the admin's later
        // approval can push the issued key back to this specific device via
        // sahara_push, without forcing the applicant to monitor email.
        applicantFcmToken: String = "",
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
                "applicantFcmToken" to applicantFcmToken,
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
                "ackEmailStatus" to "PENDING",
                "ackEmailAttempts" to 0,
                "createdAt" to System.currentTimeMillis()
            )
        ).await()
        requestId
    }

    fun listenToRegistrationRequests(): Flow<List<RegistrationRequest>> =
        rtdbFlow(ref = { db.getReference("registration_requests") }) { snapshot ->
            snapshot.children.mapNotNull(::registrationRequestFrom).sortedByDescending { it.createdAt }
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
        val cleanIssuedKey = issuedKey.trim()
        require(cleanIssuedKey.isNotBlank()) { "An issued access key is required." }
        val timestamp = System.currentTimeMillis()
        val cleanAttributes = approvedAttributeIds.distinct().filter { id ->
            CounselorAttributeCatalog.all.any { it.id == id }
        }
        val requestUpdates = mapOf(
            "status" to "APPROVED",
            "issuedKey" to cleanIssuedKey,
            "approvedAttributeIds" to cleanAttributes,
            "reviewedBy" to reviewedBy,
            "reviewNotes" to reviewNotes.trim(),
            "reviewedAt" to timestamp
        )
        db.getReference("registration_requests").child(request.requestId)
            .updateChildren(requestUpdates).await()

        // Queue the issued-key delivery for sahara_push. It picks up entries
        // here every minute, pushes an FCM notification to the applicant's
        // stored device token (if any) AND emails the key to their address.
        // Marked status=PENDING; sahara_push flips it to SENT after delivery.
        db.getReference("key_deliveries").child(request.requestId).setValue(
            mapOf(
                "requestId"       to request.requestId,
                "applicantType"   to request.applicantType,
                "applicantName"   to request.applicantName,
                "applicantEmail"  to request.email,
                "applicantToken"  to request.applicantFcmToken,
                "issuedKey"       to cleanIssuedKey,
                "reviewNotes"     to reviewNotes.trim(),
                "status"          to "PENDING",
                "createdAt"       to timestamp,
            )
        ).await()

        if (request.applicantType == "COUNSELOR") {
            val attributeLabelsEn = cleanAttributes.map(CounselorAttributeCatalog::labelEn)
            val attributeLabelsUr = cleanAttributes.map(CounselorAttributeCatalog::labelUr)
            counselorKeyRef(cleanIssuedKey).setValue(
                mapOf(
                    "issuedKey" to cleanIssuedKey,
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
            db.getReference("ngo_keys").child(accessKeyPathSegment(cleanIssuedKey)).setValue(
                mapOf(
                    "issuedKey" to cleanIssuedKey,
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
        val timestamp = System.currentTimeMillis()
        val requestRef = db.getReference("registration_requests").child(requestId)
        requestRef.updateChildren(
            mapOf(
                "status" to "REJECTED",
                "reviewedBy" to reviewedBy,
                "reviewNotes" to reviewNotes.trim(),
                "reviewedAt" to timestamp
            )
        ).await()

        val snapshot = requestRef.get().await()
        db.getReference("registration_rejection_deliveries").child(requestId).setValue(
            mapOf(
                "requestId" to requestId,
                "applicantType" to snapshot.child("applicantType").getValue(String::class.java).orEmpty(),
                "applicantName" to snapshot.child("applicantName").getValue(String::class.java).orEmpty(),
                "organizationName" to snapshot.child("organizationName").getValue(String::class.java).orEmpty(),
                "applicantEmail" to snapshot.child("email").getValue(String::class.java).orEmpty(),
                "applicantToken" to snapshot.child("applicantFcmToken").getValue(String::class.java).orEmpty(),
                "reviewNotes" to reviewNotes.trim(),
                "status" to "PENDING",
                "createdAt" to timestamp,
            )
        ).await()
    }

    suspend fun getNgoKey(key: String): Result<Map<String, Any>?> = runCatching {
        val snap = db.getReference("ngo_keys").child(accessKeyPathSegment(key)).get().await()
        val data = snap.value as? Map<String, Any> ?: return@runCatching null
        keyedAccessData(snap, data)
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
        counselorKeyRef(request.counselorKey).child("sessionCount")
            .setValue(ServerValue.increment(1)).await()

        saveUserNotification(
            uid = request.userId,
            titleEn = "Payment approved",
            titleUr = "Payment approve ho gayi",
            bodyEn = "Your payment was confirmed. Your 48-hour counselor session is active.",
            bodyUr = "Aapki payment confirm ho gayi. 48 ghante ka counselor session active hai.",
            type = "COUNSELOR",
            actionRoute = "counselor-chat/${Uri.encode(request.counselorKey)}/${Uri.encode(request.counselorName)}"
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

    fun listenToRegionalRiskSummaries(): Flow<List<RegionalRiskSummary>> =
        rtdbFlow(ref = { db.reference }) { snapshot ->
            val userRegions = snapshot.child("users").children.associate { user ->
                val region = user.strOrNull("region")?.trim().orEmpty()
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
            regions.map { region ->
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
        }

    private fun avatarRequestFrom(snapshot: DataSnapshot): AvatarRequest? {
        if (!snapshot.exists()) return null
        return AvatarRequest(
            requestId = snapshot.strOrNull("requestId") ?: snapshot.key.orEmpty(),
            userId = snapshot.str("userId"),
            userEmail = snapshot.str("userEmail"),
            userName = snapshot.str("userName"),
            fileUrl = snapshot.str("fileUrl"),
            fileName = snapshot.str("fileName"),
            mimeType = snapshot.str("mimeType"),
            sizeBytes = snapshot.lng("sizeBytes"),
            status = snapshot.strOrNull("status") ?: "PENDING_REVIEW",
            adminComment = snapshot.str("adminComment"),
            reviewedBy = snapshot.str("reviewedBy"),
            createdAt = snapshot.lng("createdAt"),
            reviewedAt = snapshot.lng("reviewedAt"),
        )
    }

    private fun emailKey(email: String): String {
        val normalized = email.trim().lowercase()
        return if (normalized.isBlank()) "" else sha256Hex(normalized)
    }

    private fun safeTokenKey(token: String): String = sha256Hex(token)

    /**
     * Inlines an upload as a `data:image/jpeg;base64,...` URI stored
     * directly in the surrounding RTDB record.
     *
     * Why not Firebase Storage: Storage was moved behind the Blaze
     * pricing plan in 2024, and this is a free-tier (Spark) FYP. We
     * still want the same call sites (`uploadEvidence(path, uri)`) to
     * return a single string that downstream readers (admin dashboard
     * AsyncImage, etc.) can render as an image, so we keep the
     * signature and swap the implementation.
     *
     * Anatomy:
     *  - Decode the user-selected image, downscale to <= 1280px on the
     *    longest edge, JPEG-compress at quality 60. A typical phone
     *    photo lands at ~300-600 KB raw and ~400-800 KB base64 —
     *    comfortably under RTDB's 10 MB per-value limit and well below
     *    the per-request 16 MB write cap even when several documents
     *    are attached to the same record.
     *  - Coil's `AsyncImage` natively understands `data:` URIs, so the
     *    admin dashboard / profile screens don't need any rendering
     *    changes — they were already loading whatever-string the
     *    Storage version returned.
     *  - PDFs and other non-image MIME types are NOT supported on this
     *    path. Callers should validate input at pick-time; if a non-
     *    image slips through, BitmapFactory will return null and we
     *    surface an IOException so the form-level error UX kicks in.
     *
     * The [path] argument is kept for source-compat with old call
     * sites and is currently ignored (records live inline now, not
     * keyed by path). Drop the argument once all callers are updated.
     */
    private suspend fun uploadEvidence(path: String, uri: Uri): String {
        return encodeImageAsDataUri(uri)
    }

    private suspend fun encodeImageAsDataUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val ctx = FirebaseApp.getInstance().applicationContext
        val raw = ctx.contentResolver.openInputStream(uri)
            ?.use { it.readBytes() }
            ?: throw IOException("Could not open upload stream.")
        val source = BitmapFactory.decodeByteArray(raw, 0, raw.size)
            ?: throw IOException("Unsupported file type — please upload an image (JPEG/PNG).")
        val maxDim = 1280
        val scaled = if (source.width <= maxDim && source.height <= maxDim) {
            source
        } else {
            val ratio = maxDim.toFloat() / maxOf(source.width, source.height)
            val w = (source.width * ratio).toInt().coerceAtLeast(1)
            val h = (source.height * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(source, w, h, true).also {
                if (it !== source) source.recycle()
            }
        }
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 60, baos)
        if (scaled !== source) scaled.recycle()
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        "data:image/jpeg;base64,$b64"
    }

    private fun paymentRequestsFlow(
        predicate: (PaymentRequest) -> Boolean = { true }
    ): Flow<List<PaymentRequest>> =
        rtdbFlow(ref = { db.getReference("payment_requests") }) { snapshot ->
            snapshot.children.mapNotNull(::paymentRequestFrom)
                .filter(predicate)
                .sortedByDescending { it.createdAt }
        }

    private fun registrationRequestFrom(snapshot: DataSnapshot): RegistrationRequest? {
        if (!snapshot.exists()) return null
        return RegistrationRequest(
            requestId = snapshot.strOrNull("requestId") ?: snapshot.key.orEmpty(),
            applicantType = snapshot.str("applicantType"),
            applicantName = snapshot.str("applicantName"),
            organizationName = snapshot.str("organizationName"),
            email = snapshot.str("email"),
            phone = snapshot.str("phone"),
            applicantFcmToken = snapshot.str("applicantFcmToken"),
            region = snapshot.str("region"),
            city = snapshot.str("city"),
            district = snapshot.str("district"),
            locationAccuracyMeters = snapshot.child("locationAccuracyMeters").getValue(Float::class.java) ?: 0f,
            verificationBody = snapshot.str("verificationBody"),
            registrationNumber = snapshot.str("registrationNumber"),
            qualificationSummary = snapshot.str("qualificationSummary"),
            details = snapshot.str("details"),
            documentUrl = snapshot.str("documentUrl"),
            documentUrls = snapshot.child("documentUrls").children.associate { child ->
                child.key.orEmpty() to child.getValue(String::class.java).orEmpty()
            }.filterKeys { it.isNotBlank() }.filterValues { it.isNotBlank() },
            requiredDocumentKeys = snapshot.child("requiredDocumentKeys").children.mapNotNull {
                it.getValue(String::class.java)
            },
            status = snapshot.strOrNull("status") ?: "PENDING_REVIEW",
            issuedKey = snapshot.str("issuedKey"),
            approvedAttributeIds = snapshot.child("approvedAttributeIds").children.mapNotNull {
                it.getValue(String::class.java)
            },
            reviewedBy = snapshot.str("reviewedBy"),
            reviewNotes = snapshot.str("reviewNotes"),
            createdAt = snapshot.lng("createdAt"),
            reviewedAt = snapshot.lng("reviewedAt")
        )
    }

    private fun paymentRequestFrom(snapshot: DataSnapshot): PaymentRequest? {
        if (!snapshot.exists()) return null
        return PaymentRequest(
            requestId = snapshot.strOrNull("requestId") ?: snapshot.key.orEmpty(),
            userId = snapshot.str("userId"),
            counselorKey = snapshot.str("counselorKey"),
            counselorName = snapshot.str("counselorName"),
            amountPkr = snapshot.str("amountPkr"),
            accountTitle = snapshot.str("accountTitle"),
            transactionReference = snapshot.str("transactionReference"),
            proofUrl = snapshot.str("proofUrl"),
            status = snapshot.strOrNull("status") ?: "PENDING_REVIEW",
            reviewedBy = snapshot.str("reviewedBy"),
            reviewNotes = snapshot.str("reviewNotes"),
            reviewAttachmentUrl = snapshot.str("reviewAttachmentUrl"),
            createdAt = snapshot.lng("createdAt"),
            reviewedAt = snapshot.lng("reviewedAt")
        )
    }

    private fun bugReportsFlow(
        predicate: (BugReport) -> Boolean = { true },
    ): Flow<List<BugReport>> =
        rtdbFlow(ref = { db.getReference("bug_reports") }) { snapshot ->
            snapshot.children.mapNotNull(::bugReportFrom)
                .filter(predicate)
                .sortedByDescending { it.createdAt }
        }

    private fun bugReportFrom(snapshot: DataSnapshot): BugReport? {
        if (!snapshot.exists()) return null
        return BugReport(
            reportId = snapshot.strOrNull("reportId") ?: snapshot.key.orEmpty(),
            userId = snapshot.str("userId"),
            maskedEmail = snapshot.str("maskedEmail"),
            deviceModel = snapshot.str("deviceModel"),
            screenshotUrl = snapshot.str("screenshotUrl"),
            description = snapshot.str("description"),
            status = snapshot.strOrNull("status") ?: "OPEN",
            createdAt = snapshot.lng("createdAt"),
            resolvedAt = snapshot.lng("resolvedAt"),
            resolvedBy = snapshot.str("resolvedBy"),
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
        counterRef.awaitTransaction(
            abortError = { IllegalStateException("You can submit at most 2 bug reports in one day.") },
        ) { currentData ->
            val count = currentData.getValue(Long::class.java) ?: 0L
            if (count >= 2L) return@awaitTransaction Transaction.abort()
            currentData.value = count + 1L
            Transaction.success(currentData)
        }
    }

    private suspend fun releaseBugReportSlot(counterRef: DatabaseReference) {
        counterRef.awaitTransaction { currentData ->
            val count = currentData.getValue(Long::class.java) ?: 0L
            currentData.value = (count - 1L).coerceAtLeast(0L)
            Transaction.success(currentData)
        }
    }
}
