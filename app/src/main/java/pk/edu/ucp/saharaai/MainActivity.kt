package pk.edu.ucp.saharaai

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import eightbitlab.com.blurview.BlurTarget
import pk.edu.ucp.saharaai.ui.screens.GlobalAppState
import pk.edu.ucp.saharaai.ui.theme.SaharaAiTheme
import pk.edu.ucp.saharaai.util.BlueskyOAuthCallbackStore
import pk.edu.ucp.saharaai.util.NotificationHelper
import pk.edu.ucp.saharaai.util.NotificationRouteStore
import pk.edu.ucp.saharaai.util.SpotifyOAuthCallbackStore
import pk.edu.ucp.saharaai.util.SteamOpenIdCallbackStore

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BlueskyOAuthCallbackStore.capture(intent)
        SteamOpenIdCallbackStore.capture(intent)
        SpotifyOAuthCallbackStore.capture(intent)
        NotificationRouteStore.capture(intent)
        NotificationHelper.init(this)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("sahara_prefs", android.content.Context.MODE_PRIVATE)
        val savedScore = prefs.getInt(KEY_ASSESSMENT_SCORE, -1)
        val savedTs    = prefs.getLong(KEY_ASSESSMENT_TIMESTAMP, 0L)
        GlobalAppState.hasEverCompletedAssessment =
            prefs.getBoolean(KEY_ASSESSMENT_EVER_COMPLETED, false) || savedScore >= 0
        if (savedScore >= 0) {
            val ageMs = if (savedTs > 0) System.currentTimeMillis() - savedTs else 0L
            GlobalAppState.dast10Score = savedScore
            GlobalAppState.lastAssessmentTimestamp = savedTs
            if (savedTs == 0L || ageMs <= ASSESSMENT_VALIDITY_MS) {
                GlobalAppState.hasCompletedInitialAssessment = true
            }
        }

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
}
