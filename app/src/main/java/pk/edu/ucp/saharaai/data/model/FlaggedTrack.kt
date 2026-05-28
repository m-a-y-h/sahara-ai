package pk.edu.ucp.saharaai.data.model

import com.google.firebase.Timestamp

/**
 * Severity bucket the listening classifier assigns to a track / week.
 *
 * Mirrors `services/sahara_listening/classifier.py::FlagSeverity` so the same
 * string values round-trip cleanly through Firestore.
 */
enum class ListeningSeverity(val wire: String) {
    NONE("none"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    UNKNOWN("unknown");

    companion object {
        fun fromWire(raw: String?): ListeningSeverity = entries.firstOrNull {
            it.wire.equals(raw, ignoreCase = true)
        } ?: UNKNOWN
    }
}

/**
 * One row under `users/{uid}/activity_log_flags/{auto-id}` — a single track
 * the weekly classifier flagged. Field names match the Firestore payload
 * written by ``services/sahara_listening/firestore_writer.write_flagged_tracks``.
 */
data class FlaggedTrack(
    val docId: String,
    val trackId: String?,
    val name: String,
    val artist: String,
    val album: String?,
    val playedAtIso: String?,
    val genres: List<String>,
    val flagReasons: List<String>,
    val severity: ListeningSeverity,
    val score: Double,
    val source: String,
    val spotifyUri: String?,
    val externalUrl: String?,
    val createdAt: Timestamp?,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromFirestore(docId: String, data: Map<String, Any?>): FlaggedTrack {
            return FlaggedTrack(
                docId        = docId,
                trackId      = data["track_id"] as? String,
                name         = data["name"] as? String ?: "Unknown track",
                artist       = data["artist"] as? String ?: "Unknown artist",
                album        = data["album"] as? String,
                playedAtIso  = data["played_at"] as? String,
                genres       = (data["genres"] as? List<String>) ?: emptyList(),
                flagReasons  = (data["flag_reasons"] as? List<String>) ?: emptyList(),
                severity     = ListeningSeverity.fromWire(data["severity"] as? String),
                score        = (data["score"] as? Number)?.toDouble() ?: 0.0,
                source       = data["source"] as? String ?: "unknown",
                spotifyUri   = data["spotify_uri"] as? String,
                externalUrl  = data["external_url"] as? String,
                createdAt    = data["createdAt"] as? Timestamp,
            )
        }
    }
}
