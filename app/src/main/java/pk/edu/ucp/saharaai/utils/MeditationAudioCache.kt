package pk.edu.ucp.saharaai.utils

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import okhttp3.OkHttpClient
import okhttp3.Request
import pk.edu.ucp.saharaai.BuildConfig
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * On-demand download + on-disk cache for the guided-meditation tracks.
 *
 * The audio used to ship inside the APK (~57 MB of res/raw MP3s). It now lives
 * on a remote host ([BuildConfig.SAHARA_MEDITATION_BASE_URL]); the first time a
 * signed-in user plays a track it's fetched once and kept in the app's private
 * storage, so every later play is offline and instant.
 *
 * [resolve] blocks on the network — call it off the main thread.
 */
object MeditationAudioCache {

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // Total cap on the whole download. Without it a large track on a slow
            // connection spins the loader for minutes; on timeout the service
            // falls back to the generated tone instead of appearing stuck.
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    /** The cache directory; survives cache eviction so downloads stay offline. */
    private fun dir(context: Context): File =
        File(context.filesDir, "meditation").apply { mkdirs() }

    fun cachedFile(context: Context, fileName: String): File = File(dir(context), fileName)

    fun isCached(context: Context, fileName: String): Boolean =
        cachedFile(context, fileName).let { it.isFile && it.length() > 0 }

    /**
     * Returns a playable local [File] for [fileName], downloading it on first
     * use. Blocking. Throws [IllegalStateException] when there's no signed-in
     * session or no configured base URL, and [IOException] on network failure,
     * so the caller can show a clear "couldn't load" state instead of silently
     * playing nothing.
     */
    @Throws(IOException::class)
    fun resolve(context: Context, fileName: String): File {
        val cached = cachedFile(context, fileName)
        if (cached.isFile && cached.length() > 0) return cached

        checkNotNull(FirebaseAuth.getInstance().currentUser) {
            "Sign in to download meditation audio"
        }
        val base = BuildConfig.SAHARA_MEDITATION_BASE_URL.trim().trimEnd('/')
        check(base.isNotEmpty()) { "Meditation audio host is not configured" }

        val url = "$base/$fileName"
        val part = File(cached.parentFile, "$fileName.part")
        part.delete()

        http.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("download failed: HTTP ${resp.code} for $fileName")
            val body = resp.body ?: throw IOException("empty response for $fileName")
            part.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        if (part.length() <= 0) {
            part.delete()
            throw IOException("downloaded 0 bytes for $fileName")
        }
        if (!part.renameTo(cached)) {
            part.copyTo(cached, overwrite = true)
            part.delete()
        }
        return cached
    }
}
