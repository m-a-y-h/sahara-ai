package pk.edu.ucp.saharaai.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class FirestoreMessage(
    val messageId: String = "",
    val sessionId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val content: String = "",
    // Kotlin Boolean properties starting with `is` can expose a bean property
    // name without the `is` prefix. Without these annotations, some Firestore
    // SDK paths write `fromAI` and read `isFromAI`, silently dropping the value
    // to false and rendering AI replies on the user side of the chat.
    // Pinning the JSON key with @PropertyName on both the field and its
    // getter keeps the wire format consistent across versions.
    @get:PropertyName("isFromAI")
    @set:PropertyName("isFromAI")
    @PropertyName("isFromAI")
    var isFromAI: Boolean = false,
    @get:PropertyName("isRead")
    @set:PropertyName("isRead")
    @PropertyName("isRead")
    var isRead: Boolean = false,
    val messageType: String = "TEXT",
    val timestamp: Timestamp = Timestamp.now(),
    val senderType: String = "",
    val riskLevel: String = "",
    val triggerCounselor: Boolean = false,
    val substanceDetected: String = "",
    val substancesDetected: List<String> = emptyList(),
    val actionDestination: String = "",
    val quickReplies: List<String> = emptyList(),
    val safetyFlags: List<String> = emptyList(),
    val detectedSymptoms: List<String> = emptyList(),
    val userIntent: String = "",
) {
    constructor() : this("", "", "", "", "", false, false, "TEXT", Timestamp.now(), "")
}
