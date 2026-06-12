package pk.edu.ucp.saharaai.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat


class MeditationMusicController(private val appContext: Context) {


    var isPlaying    by mutableStateOf(false)    ; private set
    var isLoading    by mutableStateOf(false)    ; private set
    var currentTitle by mutableStateOf("")       ; private set
    var currentIndex by mutableStateOf(-1)       ; private set


    private var playlistFiles:  Array<String> = emptyArray()
    private var playlistTitles: Array<String> = emptyArray()


    private var service: MeditationMusicService? = null
    private var bound   = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val svc  = (binder as MeditationMusicService.LocalBinder).service
            service  = svc
            bound    = true
            svc.addListener(::syncState)
            syncState()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            bound   = false
            service = null
        }
    }


    fun bind() {
        if (!bound) {
            val intent = Intent(appContext, MeditationMusicService::class.java)
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }


    fun unbind() {
        if (bound) {
            service?.removeListener(::syncState)
            appContext.unbindService(connection)
            bound   = false
            service = null
        }
    }


    /**
     * @param file     the track's remote/cached file name, e.g. "meditation_relaxing.mp3"
     * @param playlist (file, title) pairs for prev/next
     */
    fun play(
        file: String,
        title: String,
        playlist: List<Pair<String, String>>
    ) {
        playlistFiles  = playlist.map { it.first  }.toTypedArray()
        playlistTitles = playlist.map { it.second }.toTypedArray()

        val idx = playlistFiles.indexOf(file)

        if (currentIndex == idx && isPlaying) {
            pause(); return
        }
        if (currentIndex == idx && !isPlaying && !isLoading) {
            // Only resume a genuinely paused track; if the player was released
            // (a prior error, or a host that never loaded) start it fresh so the
            // button always responds instead of dead-ending on a no-op resume().
            if (service?.canResume == true) service?.resume() else startPlay(file, title)
            return
        }
        startPlay(file, title)
    }

    fun pause() {
        service?.pause()
            ?: ContextCompat.startForegroundService(
                appContext, MeditationMusicService.actionIntent(appContext, MeditationMusicService.ACTION_PAUSE)
            )
    }

    fun resume() {
        service?.resume()
            ?: ContextCompat.startForegroundService(
                appContext, MeditationMusicService.actionIntent(appContext, MeditationMusicService.ACTION_RESUME)
            )
    }

    fun next() { service?.skipNext() }
    fun prev() { service?.skipPrev() }


    fun stop() {
        unbind()
        ContextCompat.startForegroundService(
            appContext, MeditationMusicService.actionIntent(appContext, MeditationMusicService.ACTION_STOP)
        )
        isPlaying    = false
        isLoading    = false
        currentTitle = ""
        currentIndex = -1
    }

    val durationMs: Int get() = service?.durationMs ?: 0
    val positionMs: Int get() = service?.positionMs ?: 0


    private fun startPlay(file: String, title: String) {
        currentIndex = playlistFiles.indexOf(file)
        currentTitle = title
        isLoading = true
        isPlaying = false

        val intent = MeditationMusicService.playIntent(
            appContext, file, title, playlistFiles, playlistTitles
        )
        ContextCompat.startForegroundService(appContext, intent)
        if (!bound) bind()
    }

    private fun syncState() {
        val svc = service ?: return
        isPlaying    = svc.isPlaying
        isLoading    = svc.isLoading
        currentTitle = svc.currentTitle
        currentIndex = svc.currentIndex
    }
}




@Composable
fun rememberMeditationMusicController(): MeditationMusicController {
    val context = androidx.compose.ui.platform.LocalContext.current
    val ctrl = remember { MeditationMusicController(context.applicationContext) }
    DisposableEffect(Unit) {
        ctrl.bind()
        onDispose { ctrl.unbind() }
    }
    return ctrl
}
