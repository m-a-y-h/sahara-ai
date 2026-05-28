package pk.edu.ucp.saharaai.data.model

import com.google.firebase.Timestamp

/**
 * One row under `users/{uid}/weekly_reports/{week_start_iso}` — the
 * end-of-week digest the dashboard popup and the WeeklyReportScreen render.
 * Field names match the Firestore payload produced by
 * ``services/sahara_listening/weekly_report.WeeklyListeningReport.to_firestore_dict``.
 *
 * The document is keyed on its `week_start_iso` so re-running the cron for
 * a given week is idempotent.
 */
data class WeeklyListeningReport(
    val weekStartIso: String,
    val weekEndIso: String,
    val totalTracks: Int,
    val flaggedCount: Int,
    val flaggedTracks: List<FlaggedTrack>,
    val severityBreakdown: Map<String, Int>,
    val topGenres: List<Pair<String, Int>>,
    val severity: ListeningSeverity,
    val aggregateScore: Double,
    val generatedAtIso: String?,
    val modelVersion: String?,
) {
    /** Cheap-to-display label, e.g. "5 flagged / 47 listened". */
    val summaryLabel: String
        get() = "$flaggedCount flagged / $totalTracks listened"

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromFirestore(data: Map<String, Any?>): WeeklyListeningReport {
            val weekStart = data["week_start"] as? String ?: ""
            val nested = (data["flagged_tracks"] as? List<Map<String, Any?>>).orEmpty()
            val tracks = nested.mapIndexed { i, m ->
                // Inline tracks don't have a Firestore doc id; synthesize one
                // from the week_start + index so the list-key is stable across
                // recompositions.
                FlaggedTrack.fromFirestore("inline-$weekStart-$i", m)
            }
            val topGenres = (data["top_genres"] as? List<Map<String, Any?>>)
                .orEmpty()
                .mapNotNull { m ->
                    val g = m["genre"] as? String ?: return@mapNotNull null
                    val c = (m["count"] as? Number)?.toInt() ?: 0
                    g to c
                }
            return WeeklyListeningReport(
                weekStartIso        = weekStart,
                weekEndIso          = data["week_end"] as? String ?: "",
                totalTracks         = (data["total_tracks"] as? Number)?.toInt() ?: 0,
                flaggedCount        = (data["flagged_count"] as? Number)?.toInt() ?: tracks.size,
                flaggedTracks       = tracks,
                severityBreakdown   = (data["severity_breakdown"] as? Map<String, Number>)
                    .orEmpty()
                    .mapValues { it.value.toInt() },
                topGenres           = topGenres,
                severity            = ListeningSeverity.fromWire(data["severity"] as? String),
                aggregateScore      = (data["aggregate_score"] as? Number)?.toDouble() ?: 0.0,
                generatedAtIso      = data["generated_at"] as? String,
                modelVersion        = data["model_version"] as? String,
            )
        }
    }
}
