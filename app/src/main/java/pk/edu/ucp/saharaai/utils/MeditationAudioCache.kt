package pk.edu.ucp.saharaai.utils

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * Provides a playable File for a bundled guided-meditation track.
 *
 * The tracks ship inside the APK at `assets/meditation/` — no download, no
 * login, no encryption, no network. On first use a track is extracted once to
 * the app's private storage (MediaPlayer wants a file path), then reused.
 */
object MeditationAudioCache {

    private fun dir(context: Context): File =
        File(context.filesDir, "meditation").apply { mkdirs() }

    fun cachedFile(context: Context, fileName: String): File = File(dir(context), fileName)

    fun isCached(context: Context, fileName: String): Boolean =
        cachedFile(context, fileName).let { it.isFile && it.length() > 0 }

    /**
     * Returns a playable [File] for [fileName] by extracting it from
     * `assets/meditation/` once. Blocking on local I/O only — call off the main
     * thread. Throws [IOException] only if the bundled asset is missing/empty.
     */
    @Throws(IOException::class)
    fun resolve(context: Context, fileName: String): File {
        val cached = cachedFile(context, fileName)
        if (cached.isFile && cached.length() > 0) return cached

        val part = File(cached.parentFile, "$fileName.part")
        part.delete()
        context.assets.open("meditation/$fileName").use { input ->
            part.outputStream().use { out -> input.copyTo(out) }
        }
        if (part.length() <= 0) {
            part.delete()
            throw IOException("bundled meditation track $fileName is missing or empty")
        }
        if (!part.renameTo(cached)) {
            part.copyTo(cached, overwrite = true); part.delete()
        }
        return cached
    }
}
