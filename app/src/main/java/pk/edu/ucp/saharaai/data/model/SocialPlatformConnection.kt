package pk.edu.ucp.saharaai.data.model


data class SocialPlatformConnection(
    val platform: String = "",
    val username: String = "",
    val did: String = "",
    val externalId: String = "",
    val authMethod: String = "",
    val dataAccess: String = "",
    val verified: Boolean = false,
    val consentVersion: String = "",
    val consentedAt: Long = 0L,
    val connectedAt: Long = 0L,
    val dataExpiresAt: Long = 0L,
    val subscriptionCount: Int = 0,
    val analysisEnabled: Boolean = false
) {
    val displayIdentifier: String
        get() = username.ifBlank { did.ifBlank { externalId } }
}
