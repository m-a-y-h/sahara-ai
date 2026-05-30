package pk.edu.ucp.saharaai.util

import pk.edu.ucp.saharaai.data.remote.YouTubeSubscription

/**
 * Lightweight keyword classifier for a user's YouTube subscriptions list.
 *
 * Runs in the app right after [pk.edu.ucp.saharaai.data.remote.YouTubeChannelClient.loadAuthorizedData]
 * returns, scans channel titles against a deny-list of substance- and recovery-
 * related terms (English + Roman Urdu), and produces a list of [FlaggedChannel]
 * entries that feed into the SAHARA screening pipeline alongside the Lens and
 * Voice signals.
 *
 * The list is intentionally **rule-based and auditable** — same design principle
 * as [sahara_listening]'s music classifier — so a counselor or compliance reviewer
 * can read the matched keyword from the saved record and judge for themselves
 * whether the flag is reasonable. No ML inference, no external API calls.
 *
 * "Reverse search" framing: rather than asking YouTube "show me drug content",
 * we walk the user's already-subscribed channels and surface which ones cluster
 * around substance-use cues. The output drives both an immediate "we found N
 * channels worth a closer look" badge in the Connections panel and the weekly
 * risk aggregation.
 *
 * Severity tiers map to the rest of the SAHARA risk vocabulary
 * (NONE / LOW / MEDIUM / HIGH) so downstream code can colour or weight uniformly.
 */
object YouTubeSubscriptionClassifier {

    enum class FlagSeverity { NONE, LOW, MEDIUM, HIGH }

    /** Each flagged channel records WHY it was flagged so the result is
     *  auditable. `matches` is the set of unique keywords that hit. */
    data class FlaggedChannel(
        val channelId: String,
        val channelTitle: String,
        val matches: List<String>,
        val severity: FlagSeverity,
    )

    /** Aggregate report over the whole subscriptions list. */
    data class ClassificationReport(
        val totalSubscriptions: Int,
        val flagged: List<FlaggedChannel>,
        val overallSeverity: FlagSeverity,
    ) {
        val flaggedCount: Int get() = flagged.size
    }

    /**
     * High-severity terms — direct substance references, harm-reduction or
     * dealer-adjacent vocabulary. A single hit puts the channel at MEDIUM;
     * two or more push it to HIGH.
     */
    private val HIGH_TERMS: List<String> = listOf(
        // direct substances
        "cocaine", "heroin", "meth", "methamphetamine", "crystal meth",
        "fentanyl", "xanax", "oxycontin", "oxycodone", "opioid", "opioids",
        "lsd", "mdma", "ecstasy", "molly", "ketamine", "shrooms", "psilocybin",
        "crack", "ice ", "speed ", "krokodil",
        // local roman-urdu street vocab
        "charas", "ganja", "afyoon", "afioon", "chitta", " ice ", "nasha",
        // delivery / sale signals
        "drug dealer", "drug deal", "plug ", "drop shipper", "darknet", "dark net",
    )

    /**
     * Medium-severity terms — adjacent topics (cannabis legalisation channels,
     * trip-reports, lifestyle drug culture). One hit = LOW, multiple = MEDIUM.
     */
    private val MEDIUM_TERMS: List<String> = listOf(
        "weed", "marijuana", "cannabis", "kush", "stoner", "stoned", "high life",
        "high times", "420", "blunt", "joint roll", "rolling paper", "rolling papers",
        "vape ", "vaping", "bong", "dab rig", "dabs",
        "trip report", "trip sit", "tripsit", "psychedelic",
        "drug trip", "drug review", "drug education", "harm reduction",
        "alcoholic", "alcoholism", "binge drink", "rave",
        "tablay", "goli ki dukan",
    )

    /**
     * Recovery / awareness channels — these are NOT a risk on their own, but a
     * heavy concentration suggests the user is already engaged with recovery
     * content and the SAHARA flow should be supportive rather than alarming.
     * Reported separately so the UI can soften its tone.
     */
    private val RECOVERY_TERMS: List<String> = listOf(
        "sober", "sobriety", "alcoholics anonymous", "narcotics anonymous",
        " aa meeting", " na meeting", "rehab", "addiction recovery",
        "12 step", "twelve step", "clean and sober", "drug free",
    )

    /** Returns the unique set of deny-list terms that appear in the title. */
    private fun matchesIn(title: String, dictionary: List<String>): List<String> {
        val lower = title.lowercase().replace(Regex("\\s+"), " ")
        return dictionary.filter { it in lower }.distinct()
    }

    /** Walk the deny-list against a single channel title and return a flag
     *  (or null if nothing matched). */
    fun classifyOne(channelId: String, channelTitle: String): FlaggedChannel? {
        val high   = matchesIn(channelTitle, HIGH_TERMS)
        val medium = matchesIn(channelTitle, MEDIUM_TERMS)

        val severity = when {
            high.size >= 2 -> FlagSeverity.HIGH
            high.size == 1 -> FlagSeverity.MEDIUM
            medium.size >= 2 -> FlagSeverity.MEDIUM
            medium.size == 1 -> FlagSeverity.LOW
            else -> FlagSeverity.NONE
        }
        if (severity == FlagSeverity.NONE) return null

        return FlaggedChannel(
            channelId = channelId,
            channelTitle = channelTitle,
            matches = (high + medium).distinct(),
            severity = severity,
        )
    }

    /** Classify a whole subscriptions list. */
    fun classify(subscriptions: List<YouTubeSubscription>): ClassificationReport {
        val flagged = subscriptions.mapNotNull { classifyOne(it.channelId, it.channelTitle) }
        val overall = when {
            flagged.any { it.severity == FlagSeverity.HIGH } -> FlagSeverity.HIGH
            flagged.count { it.severity == FlagSeverity.MEDIUM } >= 2 -> FlagSeverity.HIGH
            flagged.any { it.severity == FlagSeverity.MEDIUM } -> FlagSeverity.MEDIUM
            flagged.count { it.severity == FlagSeverity.LOW } >= 3 -> FlagSeverity.MEDIUM
            flagged.any { it.severity == FlagSeverity.LOW } -> FlagSeverity.LOW
            else -> FlagSeverity.NONE
        }
        return ClassificationReport(
            totalSubscriptions = subscriptions.size,
            flagged = flagged.sortedByDescending { it.severity.ordinal },
            overallSeverity = overall,
        )
    }

    /** Recovery-content overlay — separate from the deny-list flagging because
     *  recovery subscriptions are a positive signal we want to surface
     *  differently to the user. Returns the number of recovery-related
     *  channels in the list. */
    fun recoveryChannelCount(subscriptions: List<YouTubeSubscription>): Int =
        subscriptions.count { sub -> matchesIn(sub.channelTitle, RECOVERY_TERMS).isNotEmpty() }
}
