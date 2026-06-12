package pk.edu.ucp.saharaai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.edu.ucp.saharaai.activities.MainActivity
import pk.edu.ucp.saharaai.utils.MeditationAudioCache
import pk.edu.ucp.saharaai.utils.MeditationAudioEngine


class MeditationMusicService : Service() {


    companion object {
        const val CHANNEL_ID = "sahara_meditation_music"
        const val NOTIF_ID   = 7001

        const val ACTION_PLAY    = "pk.edu.ucp.saharaai.MED_PLAY"
        const val ACTION_PAUSE   = "pk.edu.ucp.saharaai.MED_PAUSE"
        const val ACTION_RESUME  = "pk.edu.ucp.saharaai.MED_RESUME"
        const val ACTION_STOP    = "pk.edu.ucp.saharaai.MED_STOP"
        const val ACTION_NEXT    = "pk.edu.ucp.saharaai.MED_NEXT"
        const val ACTION_PREV    = "pk.edu.ucp.saharaai.MED_PREV"

        const val EXTRA_FILE            = "song_file"
        const val EXTRA_TITLE           = "song_title"
        const val EXTRA_PLAYLIST_FILES  = "playlist_files"
        const val EXTRA_PLAYLIST_TITLES = "playlist_titles"

        fun playIntent(
            ctx: Context,
            file: String,
            title: String,
            playlistFiles: Array<String>,
            playlistTitles: Array<String>
        ) = Intent(ctx, MeditationMusicService::class.java).also { i ->
            i.action = ACTION_PLAY
            i.putExtra(EXTRA_FILE,  file)
            i.putExtra(EXTRA_TITLE, title)
            i.putExtra(EXTRA_PLAYLIST_FILES,  playlistFiles)
            i.putExtra(EXTRA_PLAYLIST_TITLES, playlistTitles)
        }

        fun actionIntent(ctx: Context, action: String) =
            Intent(ctx, MeditationMusicService::class.java).also { it.action = action }
    }


    inner class LocalBinder : Binder() {
        val service: MeditationMusicService get() = this@MeditationMusicService
    }
    private val binder = LocalBinder()
    override fun onBind(intent: Intent?): IBinder = binder


    var currentTitle: String   = ""
        private set
    var isPlaying: Boolean     = false
        private set
    var isLoading: Boolean     = false
        private set
    var currentIndex: Int      = 0
        private set

    private var playlistFiles:  Array<String> = emptyArray()
    private var playlistTitles: Array<String> = emptyArray()
    private var mediaPlayer: MediaPlayer?     = null
    private var generatedEngine: MeditationAudioEngine? = null
    private var generatedFrequency: Double? = null
    private var usingGeneratedAudio = false

    // Each play() downloads off the main thread; keep the job so a fast
    // skip/stop cancels the in-flight fetch instead of racing it.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var prepareJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())


    private val listeners = mutableListOf<() -> Unit>()
    fun addListener(cb: () -> Unit)    { listeners += cb }
    fun removeListener(cb: () -> Unit) { listeners -= cb }
    private fun notify2() = listeners.forEach { it() }



    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                playlistFiles  = intent.getStringArrayExtra(EXTRA_PLAYLIST_FILES)  ?: emptyArray()
                playlistTitles = intent.getStringArrayExtra(EXTRA_PLAYLIST_TITLES) ?: emptyArray()
                val file       = intent.getStringExtra(EXTRA_FILE).orEmpty()
                currentIndex   = playlistFiles.indexOf(file).coerceAtLeast(0)
                currentTitle   = playlistTitles.getOrNull(currentIndex) ?: intent.getStringExtra(EXTRA_TITLE).orEmpty()
                // Foreground immediately: the download below can outlast the
                // startForeground() deadline, so we can't wait for playback.
                isLoading = true; isPlaying = false
                startForegroundNow()
                notify2()
                playAt(currentIndex)
            }
            ACTION_PAUSE  -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP   -> { stopSelf(); return START_NOT_STICKY }
            ACTION_NEXT   -> skipNext()
            ACTION_PREV   -> skipPrev()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        prepareJob?.cancel()
        scope.cancel()
        releasePlayer()
        super.onDestroy()
    }



    private fun playAt(index: Int) {
        if (playlistFiles.isEmpty()) return
        currentIndex = index.coerceIn(0, playlistFiles.lastIndex)
        currentTitle = playlistTitles.getOrNull(currentIndex) ?: ""
        val fileName = playlistFiles[currentIndex]
        releasePlayer()
        isLoading = true; isPlaying = false
        updateNotif(); notify2()

        prepareJob?.cancel()
        prepareJob = scope.launch {
            val file = try {
                withContext(Dispatchers.IO) { MeditationAudioCache.resolve(this@MeditationMusicService, fileName) }
            } catch (_: Exception) {
                startGeneratedFallback(fileName)
                return@launch
            }
            try {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setDataSource(file.absolutePath)
                    isLooping = false
                    setOnCompletionListener { skipNext() }
                    setOnErrorListener { _, _, _ ->
                        runCatching { file.delete() }
                        startGeneratedFallback(fileName)
                        true
                    }
                    setOnPreparedListener { mp ->
                        // A fast skip can release this player while it was still
                        // preparing; if it's no longer the current one, drop it.
                        if (mediaPlayer !== mp) { runCatching { mp.release() }; return@setOnPreparedListener }
                        // Qualified: inside MediaPlayer.apply, bare isPlaying would
                        // bind to MediaPlayer.isPlaying (a read-only getter).
                        this@MeditationMusicService.isLoading = false
                        this@MeditationMusicService.isPlaying = true
                        mp.start()
                        updateNotif(); notify2()
                    }
                    prepareAsync()
                }
            } catch (_: Exception) {
                startGeneratedFallback(fileName)
            }
        }
    }

    private fun startGeneratedFallback(fileName: String) {
        val title = currentTitle.ifBlank { fileName }
        val frequency = MeditationAudioEngine.frequencyFor(title)
        releasePlayback(clearGeneratedState = false)
        generatedFrequency = frequency
        usingGeneratedAudio = true
        generatedEngine = MeditationAudioEngine().also { it.play(frequency, volumeFraction = 0.28f) }
        isLoading = false
        isPlaying = true
        mainHandler.post {
            Toast.makeText(this, "Meditation audio offline mode", Toast.LENGTH_SHORT).show()
        }
        updateNotif()
        notify2()
    }

    fun pause() {
        if (usingGeneratedAudio) {
            generatedEngine?.stop()
            generatedEngine = null
        } else {
            runCatching { mediaPlayer?.pause() }
        }
        isPlaying = false
        updateNotif()
        notify2()
    }

    fun resume() {
        if (usingGeneratedAudio) {
            generatedFrequency?.let { frequency ->
                generatedEngine = MeditationAudioEngine().also { it.play(frequency, volumeFraction = 0.28f) }
                isPlaying = true
            }
        } else {
            runCatching { mediaPlayer?.start() }
            isPlaying = mediaPlayer != null
        }
        updateNotif()
        notify2()
    }

    fun skipNext() {
        if (playlistFiles.isEmpty()) return
        playAt((currentIndex + 1) % playlistFiles.size)
    }

    fun skipPrev() {
        if (playlistFiles.isEmpty()) return
        playAt(if (currentIndex == 0) playlistFiles.lastIndex else currentIndex - 1)
    }

    val durationMs: Int get() = try { mediaPlayer?.duration ?: 0 } catch (_: Exception) { 0 }
    val positionMs: Int get() = try { mediaPlayer?.currentPosition ?: 0 } catch (_: Exception) { 0 }

    // True only when a track is loaded and paused (resumable). After an error or
    // stop the player is released, so a tap on that track must start fresh
    // rather than call resume() (which would silently no-op).
    val canResume: Boolean get() =
        (mediaPlayer != null || (usingGeneratedAudio && generatedFrequency != null)) && !isPlaying && !isLoading

    private fun releasePlayer() {
        releasePlayback()
    }

    private fun releasePlayback(clearGeneratedState: Boolean = true) {
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        runCatching { generatedEngine?.stop() }
        mediaPlayer = null
        generatedEngine = null
        if (clearGeneratedState) {
            generatedFrequency = null
            usingGeneratedAudio = false
        }
        isPlaying = false
    }



    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Meditation Music",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Background meditation music" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun startForegroundNow() {
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), serviceType)
    }

    private fun pi(action: String): PendingIntent =
        PendingIntent.getService(
            this, action.hashCode(),
            actionIntent(this, action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("🎵 Sahara Meditation")
            .setContentText(currentTitle)
            .setSubText(if (isLoading) "Loading…" else if (isPlaying) "Now Playing" else "Paused")
            .setContentIntent(openApp)
            .setSilent(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_previous, "Prev",  pi(ACTION_PREV))
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause
                else           android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                pi(if (isPlaying) ACTION_PAUSE else ACTION_RESUME)
            )
            .addAction(android.R.drawable.ic_media_next,   "Next",  pi(ACTION_NEXT))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pi(ACTION_STOP))
            .build()
    }

    private fun updateNotif() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }
}
