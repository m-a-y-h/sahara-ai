package pk.edu.ucp.saharaai.utils

import android.content.Context
import android.net.Uri
import pk.edu.ucp.saharaai.data.model.CounselorProfile
import pk.edu.ucp.saharaai.data.model.RegionalRiskSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * CSV export for the NGO dashboard. The Android Storage Access Framework hands
 * back a writable Uri the user picked (Downloads, Drive, etc.); this writer
 * formats the regional risk + counselor view into a single comma-separated
 * spreadsheet the NGO can open in Excel/Sheets.
 *
 * Layout:
 *   1. A header block — generation time, the NGO's region, top-line totals.
 *   2. Per-region risk rows from `regionalRisk`.
 *   3. Per-counselor rows from `counselors`, scoped to the same region.
 *
 * The three sections are separated by blank rows so a spreadsheet reader can
 * highlight each table independently.
 */
object NgoStatsExporter {

    fun buildDefaultFilename(region: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Karachi")
        }.format(Date())
        val tag = region.trim().lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").ifBlank { "all" }
        return "sahara-ngo-stats-$tag-$stamp.csv"
    }

    fun buildCsv(
        region: String,
        totalUsers: Int,
        totalChats: Int,
        counselors: List<CounselorProfile>,
        regionalRisk: List<RegionalRiskSummary>,
    ): String {
        val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm 'PKT'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Karachi")
        }.format(Date())

        val online = counselors.count { it.isAvailable }
        val regionLabel = region.ifBlank { "all regions" }

        return buildString {
            // Header block.
            appendCsvRow("SAHARA NGO regional statistics")
            appendCsvRow("Generated", generatedAt)
            appendCsvRow("Filter region", regionLabel)
            appendCsvRow("Total users", totalUsers.toString())
            appendCsvRow("Counselors (total / online)", "$online / ${counselors.size}")
            appendCsvRow("Chat sessions", totalChats.toString())
            appendLine()

            // Section 2: regional risk averages.
            appendCsvRow("Regional risk averages (latest assessment per user)")
            appendCsvRow(
                "Region",
                "Registered users",
                "Assessed users",
                "Total assessments",
                "Average latest DAST-10 (0-10)",
                "High risk users (>=6)",
                "Moderate risk users (3-5)",
            )
            if (regionalRisk.isEmpty()) {
                appendCsvRow("(no assessment data for this region)")
            } else {
                for (s in regionalRisk) {
                    appendCsvRow(
                        s.region.ifBlank { "Unspecified" },
                        s.registeredUsers.toString(),
                        s.assessedUsers.toString(),
                        s.totalAssessments.toString(),
                        "%.2f".format(s.averageLatestScore),
                        s.highRiskUsers.toString(),
                        s.moderateRiskUsers.toString(),
                    )
                }
            }
            appendLine()

            // Section 3: counselors.
            appendCsvRow("Counselors")
            appendCsvRow(
                "Name",
                "Region",
                "NGO",
                "Specialization",
                "Online now",
                "Verified",
                "Average rating (0-5)",
                "Total ratings",
                "Session count",
            )
            if (counselors.isEmpty()) {
                appendCsvRow("(no counselors registered for this region)")
            } else {
                for (c in counselors) {
                    appendCsvRow(
                        c.name.ifBlank { "Counselor" },
                        c.region.ifBlank { "Unspecified" },
                        c.ngoName,
                        c.specialization,
                        if (c.isAvailable) "yes" else "no",
                        if (c.isVerified) "yes" else "no",
                        "%.2f".format(c.rating),
                        c.totalRatings.toString(),
                        c.sessionCount.toString(),
                    )
                }
            }
        }
    }

    fun writeCsvToUri(context: Context, uri: Uri, csv: String): Boolean {
        return runCatching {
            context.contentResolver.openOutputStream(uri, "w")?.use { out ->
                out.write(csv.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: error("no output stream")
        }.isSuccess
    }

    private fun StringBuilder.appendCsvRow(vararg cells: String) {
        cells.joinTo(this, separator = ",") { escapeCell(it) }
        append("\r\n")
    }

    /** RFC 4180 cell escaping: quote when needed, double internal quotes. */
    private fun escapeCell(raw: String): String {
        val needs = raw.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needs) return raw
        return "\"" + raw.replace("\"", "\"\"") + "\""
    }
}
