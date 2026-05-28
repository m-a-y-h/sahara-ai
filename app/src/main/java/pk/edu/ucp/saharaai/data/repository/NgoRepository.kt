package pk.edu.ucp.saharaai.data.repository

import kotlinx.coroutines.flow.Flow
import pk.edu.ucp.saharaai.data.model.EmergencyAlert
import pk.edu.ucp.saharaai.data.model.NgoStats
import pk.edu.ucp.saharaai.data.remote.FirestoreService

object NgoRepository {

    
    suspend fun getStats(ngoId: String): Result<NgoStats?> =
        FirestoreService.getNgoStats(ngoId)

    
    suspend fun saveStats(stats: NgoStats): Result<Unit> =
        FirestoreService.saveNgoStats(stats)

    
    fun getStatsFlow(ngoId: String): Flow<NgoStats?> =
        FirestoreService.getNgoStatsFlow(ngoId)

    
    suspend fun getOpenAlerts(ngoId: String): Result<List<EmergencyAlert>> =
        FirestoreService.getOpenAlerts(ngoId)

    
    fun getAlertsFlow(ngoId: String): Flow<List<EmergencyAlert>> =
        FirestoreService.getAlertsFlow(ngoId)

    
    suspend fun acknowledgeAlert(alertId: String, counselorId: String): Result<Unit> =
        FirestoreService.updateAlertStatus(alertId, "ACKNOWLEDGED", counselorId)

    
    suspend fun resolveAlert(alertId: String, counselorId: String): Result<Unit> =
        FirestoreService.updateAlertStatus(alertId, "RESOLVED", counselorId)
}
