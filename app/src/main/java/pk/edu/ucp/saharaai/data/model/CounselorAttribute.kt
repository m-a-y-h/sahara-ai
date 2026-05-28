package pk.edu.ucp.saharaai.data.model

data class CounselorAttribute(
    val id: String,
    val labelEn: String,
    val labelUr: String,
)

object CounselorAttributeCatalog {
    val all = listOf(
        CounselorAttribute("addiction_recovery", "Addiction Recovery", "Nashay se recovery"),
        CounselorAttribute("mental_health", "Mental Health", "Zehni sehat"),
        CounselorAttribute("youth_counseling", "Youth Counseling", "Nojawanon ki counseling"),
    )

    fun labelEn(id: String): String = all.firstOrNull { it.id == id }?.labelEn ?: id

    fun labelUr(id: String): String = all.firstOrNull { it.id == id }?.labelUr ?: labelEn(id)
}
