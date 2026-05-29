package pk.edu.ucp.saharaai.util

// Honorifics, caste/clan titles, and religious prefixes that should never be used
// as a person's calling name in greetings. Kept lowercase for case-insensitive match.
private val honorificTitles = setOf(
    "syed", "sayyed", "sayyid", "muhammad", "mohammad", "mohd", "muhammed", "mohamed", "mohammed",
    "peer", "doctor", "pir", "qazi", "qaazi", "khan", "bin", "shaikh", "sheikh", "mian", "shah",
    "makhdoom", "hafiz", "raja", "malik", "sardar", "wadera", "mir", "haju", "haaji", "haji",
    "mirza", "ustad", "jan", "khawaja", "dewan", "rana", "chaudhari", "choudhury", "chowdhury",
    "chaudhry", "chohadry", "choudhry", "choudhary", "chohadri", "chouudry", "choudari", "chaudree",
    "chowdhrie", "chawdry", "chowdhary", "chowdhry", "chowdhri", "chowdhari", "chawdhury", "chaudery",
    "rai", "rao", "kanwar", "jam", "arbab", "zardar", "nawab", "qari", "mufti", "allama", "maulana",
    "pirzada", "ghulam", "zaildar", "pasha", "syeda", "bibi", "begum", "beghum", "moulvi", "faqir",
    "faqeer", "hashmi", "hazrat", "bano", "baano", "pirzadi", "sahibzadi", "khanum", "khatoon",
    "sultan", "malak",
)

/**
 * Returns the first name token that isn't an honorific/title (capitalised) so greetings
 * use the person's actual given name — e.g. "Muhammad Asfand" -> "Asfand". Falls back to
 * the first token if every token is a title. Single source of truth for the skip-list.
 */
fun callingName(fullName: String): String {
    val parts = fullName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (parts.isEmpty()) return ""
    val calling = parts.firstOrNull { it.lowercase().replace(".", "") !in honorificTitles }
        ?: parts.first()
    return calling.replaceFirstChar { it.uppercase() }
}
