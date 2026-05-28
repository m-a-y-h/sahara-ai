package pk.edu.ucp.saharaai.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.data.model.AppNotification
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService

class NotificationsViewModel : ViewModel() {

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    
    private var currentUid: String = ""

    

    
    fun listenToCurrentUserNotifications() =
        listenToNotifications(Firebase.auth.currentUser?.uid.orEmpty())

    fun listenToNotifications(userId: String) {
        if (userId.isBlank()) return
        currentUid = userId
        viewModelScope.launch {
            RealtimeDBService.listenToUserNotifications(userId).collect { maps ->
                _notifications.value = maps.map { it.toAppNotification(userId) }
                _unreadCount.value   = maps.count { it["isRead"] as? Boolean != true }
            }
        }
    }

    

    
    fun markRead(notifId: String) {
        val uid = currentUid.ifBlank { return }
        viewModelScope.launch {
            RealtimeDBService.markNotificationRead(uid, notifId)
        }
        
        _notifications.value = _notifications.value.map { n ->
            if (n.notificationId == notifId) n.copy(isRead = true) else n
        }
        _unreadCount.value = _notifications.value.count { !it.isRead }
    }

    
    fun markAllRead() {
        val uid = currentUid.ifBlank { return }
        viewModelScope.launch {
            RealtimeDBService.markAllNotificationsRead(uid)
        }
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
        _unreadCount.value   = 0
    }

    

    
    fun loadNotifications(userId: String) = listenToNotifications(userId)

    
    fun markAllRead(userId: String) = markAllRead()

    

    private fun Map<String, Any>.toAppNotification(uid: String): AppNotification {
        val tsMillis = (this["timestamp"] as? Long) ?: System.currentTimeMillis()
        return AppNotification(
            notificationId = this["notifId"]     as? String  ?: "",
            userId         = uid,
            titleEn        = this["titleEn"]     as? String  ?: "",
            titleUr        = this["titleUr"]     as? String  ?: "",
            bodyEn         = this["bodyEn"]      as? String  ?: "",
            bodyUr         = this["bodyUr"]      as? String  ?: "",
            type           = this["type"]        as? String  ?: "GENERAL",
            isRead         = this["isRead"]      as? Boolean ?: false,
            actionRoute    = this["actionRoute"] as? String  ?: "",
            timestamp      = Timestamp(
                tsMillis / 1000,
                ((tsMillis % 1000) * 1_000_000).toInt()
            )
        )
    }
}
