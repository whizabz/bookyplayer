package xyz.saltedchips.bookyplayer

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import xyz.saltedchips.bookyplayer.tts.ConversionsRequests

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            ),
        )
        handleIntent(intent)
        setContent {
            BookyApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(ConversionsRequests.EXTRA_OPEN_CONVERSIONS, false) == true) {
            intent.removeExtra(ConversionsRequests.EXTRA_OPEN_CONVERSIONS)
            ConversionsRequests.request()
        }
    }
}
