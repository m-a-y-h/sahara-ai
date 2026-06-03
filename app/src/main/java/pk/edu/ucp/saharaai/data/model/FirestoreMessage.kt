package pk.edu.ucp.saharaai.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class FirestoreMessage(
    val messageId: String = "",
    val sessionId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val content: String = "",
    // Kotlin val isFromAI generates a Java-bean getter `isFromAI()` whose
    // bean property name is `fromAI` (the SDK strips the `is` prefix). That
    // means without these annotations Firestore would write the key as
    // `fromAI` and read it back into the field name `isFromAI` — a mismatch
    // that silently drops to the default `false` on some SDK paths and
    // causes AI replies to render on the user side of the chat.
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
    val senderType: String = ""
) {
    constructor() : this("", "", "", "", "", false, false, "TEXT", Timestamp.now(), "")
}
