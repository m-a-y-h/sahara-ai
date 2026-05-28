package pk.edu.ucp.saharaai.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.model.ConnectionStatus
import pk.edu.ucp.saharaai.data.model.UserConnection
import pk.edu.ucp.saharaai.data.repository.ConnectionRepository
import pk.edu.ucp.saharaai.data.repository.ReportRepository

sealed class ConnectionsUiState {
    object Idle      : ConnectionsUiState()
    object Loading   : ConnectionsUiState()
    object Success   : ConnectionsUiState()
    data class Error(val message: String) : ConnectionsUiState()
}

class ConnectionsViewModel : ViewModel() {

    private val _connections     = MutableStateFlow<List<UserConnection>>(emptyList())
    val connections: StateFlow<List<UserConnection>> = _connections.asStateFlow()

    private val _pendingRequests = MutableStateFlow<List<UserConnection>>(emptyList())
    val pendingRequests: StateFlow<List<UserConnection>> = _pendingRequests.asStateFlow()

    private val _uiState         = MutableStateFlow<ConnectionsUiState>(ConnectionsUiState.Idle)
    val uiState: StateFlow<ConnectionsUiState> = _uiState.asStateFlow()

    
    fun listenToConnections(userId: String) {
        viewModelScope.launch {
            ConnectionRepository.getConnectionsFlow(userId).collect { all ->
                _connections.value = all.filter {
                    it.status == ConnectionStatus.ACCEPTED.name
                }
                _pendingRequests.value = all.filter {
                    it.receiverId == userId && it.status == ConnectionStatus.PENDING.name
                }
            }
        }
    }

    
    fun loadConnections(userId: String) {
        viewModelScope.launch {
            _uiState.value = ConnectionsUiState.Loading
            val connResult = ConnectionRepository.getConnections(userId)
            val reqResult  = ConnectionRepository.getPendingRequests(userId)
            connResult.onSuccess { _connections.value = it }
            reqResult.onSuccess  { _pendingRequests.value = it }
            _uiState.value = ConnectionsUiState.Success
        }
    }

    
    fun sendRequest(
        senderId: String,
        senderName: String,
        receiverId: String,
        receiverName: String
    ) {
        viewModelScope.launch {
            val result = ConnectionRepository.sendRequest(senderId, senderName, receiverId, receiverName)
            _uiState.value = if (result.isSuccess)
                ConnectionsUiState.Success
            else
                ConnectionsUiState.Error(result.exceptionOrNull()?.message ?: "Request failed.")
        }
    }

    
    fun acceptRequest(connectionId: String) {
        viewModelScope.launch {
            ConnectionRepository.acceptRequest(connectionId)
            
            val accepted = _pendingRequests.value.find { it.connectionId == connectionId }
            if (accepted != null) {
                _pendingRequests.value = _pendingRequests.value - accepted
                _connections.value     = _connections.value + accepted.copy(
                    status = ConnectionStatus.ACCEPTED.name
                )
            }
        }
    }

    
    fun rejectRequest(connectionId: String) {
        viewModelScope.launch {
            ConnectionRepository.rejectRequest(connectionId)
            _pendingRequests.value = _pendingRequests.value.filter { it.connectionId != connectionId }
        }
    }

    
    fun removeConnection(connectionId: String) {
        viewModelScope.launch {
            ConnectionRepository.removeConnection(connectionId)
            _connections.value = _connections.value.filter { it.connectionId != connectionId }
        }
    }

    
    fun blockUser(connectionId: String) {
        viewModelScope.launch {
            ConnectionRepository.blockUser(connectionId)
            _connections.value = _connections.value.filter { it.connectionId != connectionId }
        }
    }

    
    fun reportUser(reportedBy: String, targetUserId: String, reason: String, description: String) {
        viewModelScope.launch {
            ReportRepository.submitReport(
                reportedBy  = reportedBy,
                targetId    = targetUserId,
                targetType  = "USER",
                reason      = reason,
                description = description
            )
        }
    }

    
    fun getOtherName(conn: UserConnection, myId: String): String =
        ConnectionRepository.getOtherName(conn, myId)

    fun getOtherId(conn: UserConnection, myId: String): String =
        ConnectionRepository.getOtherId(conn, myId)

    fun clearError() { _uiState.value = ConnectionsUiState.Idle }
}
