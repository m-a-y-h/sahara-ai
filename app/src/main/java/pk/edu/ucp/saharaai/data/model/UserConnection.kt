package pk.edu.ucp.saharaai.data.model

import com.google.firebase.Timestamp

enum class ConnectionStatus { PENDING, ACCEPTED, REJECTED, BLOCKED }

data class UserConnection(
    val connectionId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val receiverId: String = "",
    val receiverName: String = "",
    val status: String = ConnectionStatus.PENDING.name,
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
) {
    constructor() : this("", "", "", "", "", ConnectionStatus.PENDING.name, Timestamp.now(), Timestamp.now())
}
