package pk.edu.ucp.saharaai.data.repository

import com.google.firebase.Timestamp
import pk.edu.ucp.saharaai.data.model.EmergencyAlert
import pk.edu.ucp.saharaai.data.remote.FirestoreService


data class EmergencyContact(
    val nameEn: String,
    val nameUr: String,
    val number: String,
    val descriptionEn: String,
    val descriptionUr: String
)

object EmergencyRepository {

    
    fun getEmergencyContacts(): List<EmergencyContact> = listOf(
        EmergencyContact(
            nameEn = "Umang Helpline",
            nameUr = "امنگ ہیلپ لائن",
            number = "0317-4288665",
            descriptionEn = "Mental health and crisis support helpline",
            descriptionUr = "ذہنی صحت اور بحران کی حمایت"
        ),
        EmergencyContact(
            nameEn = "Rozan Counseling",
            nameUr = "روزن کونسلنگ",
            number = "051-2890505",
            descriptionEn = "Psychosocial counseling & trauma support",
            descriptionUr = "نفسیاتی مشاورت اور صدمے کی مدد"
        ),
        EmergencyContact(
            nameEn = "Rescue / Police",
            nameUr = "ریسکیو / پولیس",
            number = "15",
            descriptionEn = "Emergency police services",
            descriptionUr = "ہنگامی پولیس سروسز"
        ),
        EmergencyContact(
            nameEn = "Edhi Foundation",
            nameUr = "ایدھی فاؤنڈیشن",
            number = "115",
            descriptionEn = "Ambulance and social welfare services",
            descriptionUr = "ایمبولینس اور سماجی فلاح"
        ),
        EmergencyContact(
            nameEn = "Hospital Emergency",
            nameUr = "اسپتال ایمرجنسی",
            number = "1122",
            descriptionEn = "Emergency medical services",
            descriptionUr = "ہنگامی طبی خدمات"
        ),
        EmergencyContact(
            nameEn = "Drug Abuse Helpline",
            nameUr = "منشیات کی لت ہیلپ لائن",
            number = "1166",
            descriptionEn = "Drug abuse and addiction support",
            descriptionUr = "منشیات اور لت کی مدد"
        )
    )

    
    suspend fun triggerSOS(
        userId: String,
        userName: String,
        latitude: Double,
        longitude: Double,
        locationName: String,
        riskScore: Float,
        ngoId: String = ""
    ): Result<String> {
        val alert = EmergencyAlert(
            userId       = userId,
            userName     = userName,
            latitude     = latitude,
            longitude    = longitude,
            locationName = locationName,
            riskScore    = riskScore,
            message      = "User triggered SOS from app.",
            ngoId        = ngoId,
            createdAt    = Timestamp.now()
        )
        val result = FirestoreService.createEmergencyAlert(alert)
        
        if (result.isSuccess) {
            NotificationRepository.sendCrisisAlert(userId)
        }
        return result
    }

    
    suspend fun getOpenAlerts(ngoId: String): Result<List<EmergencyAlert>> =
        FirestoreService.getOpenAlerts(ngoId)

    
    suspend fun acknowledge(alertId: String, counselorId: String): Result<Unit> =
        FirestoreService.updateAlertStatus(alertId, "ACKNOWLEDGED", counselorId)

    
    suspend fun resolve(alertId: String, counselorId: String): Result<Unit> =
        FirestoreService.updateAlertStatus(alertId, "RESOLVED", counselorId)
}
