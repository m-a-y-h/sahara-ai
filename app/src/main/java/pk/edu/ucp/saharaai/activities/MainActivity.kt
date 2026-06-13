package pk.edu.ucp.saharaai.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import eightbitlab.com.blurview.BlurTarget
import pk.edu.ucp.saharaai.SaharaApp
import pk.edu.ucp.saharaai.ui.theme.SaharaAiTheme
import pk.edu.ucp.saharaai.utils.AssessmentCache
import pk.edu.ucp.saharaai.utils.BlueskyOAuthCallbackStore
import pk.edu.ucp.saharaai.utils.NotificationHelper
import pk.edu.ucp.saharaai.utils.NotificationRouteStore
import pk.edu.ucp.saharaai.utils.SpotifyOAuthCallbackStore
import pk.edu.ucp.saharaai.utils.SteamOpenIdCallbackStore

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureWindowContent()
        BlueskyOAuthCallbackStore.capture(intent)
        SteamOpenIdCallbackStore.capture(intent)
        SpotifyOAuthCallbackStore.capture(intent)
        NotificationRouteStore.capture(intent)
        NotificationHelper.init(this)
        enableEdgeToEdge()

        AssessmentCache.restoreToGlobal(this, Firebase.auth.currentUser?.uid)

        setContent {
            SaharaAiTheme {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        BlurTarget(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            addView(ComposeView(context).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setContent {
                                    SaharaAiTheme {
                                        SaharaApp()
                                    }
                                }
                            })
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        BlueskyOAuthCallbackStore.capture(intent)
        SteamOpenIdCallbackStore.capture(intent)
        SpotifyOAuthCallbackStore.capture(intent)
        NotificationRouteStore.capture(intent)
    }

    override fun onResume() {
        super.onResume()
        secureWindowContent()
    }

    private fun secureWindowContent() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }
    }
}
