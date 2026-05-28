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
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import pk.edu.ucp.saharaai.MainActivity


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

        const val EXTRA_RES_ID          = "res_id"
        const val EXTRA_TITLE           = "song_title"
        const val EXTRA_PLAYLIST_IDS    = "playlist_ids"
        const val EXTRA_PLAYLIST_TITLES = "playlist_titles"

        fun playIntent(
            ctx: Context,
            resId: Int,
            title: String,
            playlistIds: IntArray,
            playlistTitles: Array<String>
        ) = Intent(ctx, MeditationMusicService::class.java).also { i ->
            i.action = ACTION_PLAY
            i.putExtra(EXTRA_RES_ID, resId)
            i.putExtra(EXTRA_TITLE,  title)
            i.putExtra(EXTRA_PLAYLIST_IDS,    playlistIds)
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
    var currentIndex: Int      = 0
        private set

    private var playlistIds:    IntArray      = intArrayOf()
    private var playlistTitles: Array<String> = emptyArray()
    private var mediaPlayer: MediaPlayer?     = null

    
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
                playlistIds    = intent.getIntArrayExtra(EXTRA_PLAYLIST_IDS)    ?: intArrayOf()
                playlistTitles = intent.getStringArrayExtra(EXTRA_PLAYLIST_TITLES) ?: emptyArray()
                val resId      = intent.getIntExtra(EXTRA_RES_ID, -1)
                currentIndex   = playlistIds.indexOf(resId).coerceAtLeast(0)
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
        releasePlayer()
        super.onDestroy()
    }

    

    private fun playAt(index: Int) {
        if (playlistIds.isEmpty()) return
        currentIndex = index.coerceIn(0, playlistIds.lastIndex)
        currentTitle = playlistTitles.getOrNull(currentIndex) ?: ""
        releasePlayer()

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            resources.openRawResourceFd(playlistIds[currentIndex])
                .also { fd -> setDataSource(fd.fileDescriptor, fd.startOffset, fd.length); fd.close() }
            isLooping = false
            setOnCompletionListener { skipNext() }
            prepare()
            start()
        }
        isPlaying = true
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(),
            serviceType
        )
        notify2()
    }

    fun pause() {
        mediaPlayer?.pause()
        isPlaying = false
        updateNotif()
        notify2()
    }

    fun resume() {
        mediaPlayer?.start()
        isPlaying = true
        updateNotif()
        notify2()
    }

    fun skipNext() {
        if (playlistIds.isEmpty()) return
        playAt((currentIndex + 1) % playlistIds.size)
    }

    fun skipPrev() {
        if (playlistIds.isEmpty()) return
        playAt(if (currentIndex == 0) playlistIds.lastIndex else currentIndex - 1)
    }

    val durationMs: Int get() = try { mediaPlayer?.duration ?: 0 } catch (_: Exception) { 0 }
    val positionMs: Int get() = try { mediaPlayer?.currentPosition ?: 0 } catch (_: Exception) { 0 }

    private fun releasePlayer() {
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        isPlaying   = false
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
            .setSubText(if (isPlaying) "Now Playing" else "Paused")
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
