package pk.edu.ucp.saharaai.data.repository

import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import pk.edu.ucp.saharaai.data.model.ConnectionStatus
import pk.edu.ucp.saharaai.data.model.UserConnection
import pk.edu.ucp.saharaai.data.remote.FirestoreService

object ConnectionRepository {

    
    suspend fun sendRequest(
        senderId: String,
        senderName: String,
        receiverId: String,
        receiverName: String
    ): Result<String> {
        val conn = UserConnection(
            senderId     = senderId,
            senderName   = senderName,
            receiverId   = receiverId,
            receiverName = receiverName,
            status       = ConnectionStatus.PENDING.name,
            createdAt    = Timestamp.now(),
            updatedAt    = Timestamp.now()
        )
        return FirestoreService.sendConnectionRequest(conn)
    }

    
    suspend fun acceptRequest(connectionId: String): Result<Unit> =
        FirestoreService.updateConnectionStatus(connectionId, ConnectionStatus.ACCEPTED.name)

    
    suspend fun rejectRequest(connectionId: String): Result<Unit> =
        FirestoreService.updateConnectionStatus(connectionId, ConnectionStatus.REJECTED.name)

    
    suspend fun blockUser(connectionId: String): Result<Unit> =
        FirestoreService.updateConnectionStatus(connectionId, ConnectionStatus.BLOCKED.name)

    
    suspend fun removeConnection(connectionId: String): Result<Unit> =
        FirestoreService.removeConnection(connectionId)

    
    suspend fun getConnections(userId: String): Result<List<UserConnection>> =
        FirestoreService.getAcceptedConnections(userId)

    
    suspend fun getPendingRequests(userId: String): Result<List<UserConnection>> =
        FirestoreService.getPendingRequests(userId)

    
    fun getConnectionsFlow(userId: String): Flow<List<UserConnection>> =
        FirestoreService.getConnectionsFlow(userId)

    
    suspend fun getFriends(userId: String): Result<List<UserConnection>> {
        return getConnections(userId).map { list ->
            list.filter { it.status == ConnectionStatus.ACCEPTED.name }
        }
    }

    
    fun getOtherName(conn: UserConnection, myId: String): String =
        if (conn.senderId == myId) conn.receiverName else conn.senderName

    
    fun getOtherId(conn: UserConnection, myId: String): String =
        if (conn.senderId == myId) conn.receiverId else conn.senderId
}
