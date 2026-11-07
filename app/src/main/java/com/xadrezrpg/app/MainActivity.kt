package com.xadrezrpg.app

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.xadrezrpg.app.net.Session
import com.xadrezrpg.app.ui.JoinScreen
import com.xadrezrpg.app.ui.MatchScreen
import com.xadrezrpg.app.ui.MenuScreen
import com.xadrezrpg.app.ui.Palette
import com.xadrezrpg.app.ui.RulesScreen

private sealed class Screen {
    data object Menu : Screen()
    data object Join : Screen()
    data object Rules : Screen()
    data class Match(val session: Session) : Screen()
}

class MainActivity : ComponentActivity() {

    private var multicastLock: WifiManager.MulticastLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Necessário em muitos aparelhos para receber os anúncios (broadcast) de partidas.
        runCatching {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("xadrezrpg").apply {
                setReferenceCounted(false)
                acquire()
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.fillMaxSize().background(Palette.bg).safeDrawingPadding()) {
                    App()
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching { multicastLock?.release() }
        super.onDestroy()
    }
}

@Composable
private fun App() {
    var screen by remember { mutableStateOf<Screen>(Screen.Menu) }

    fun exitMatch() {
        (screen as? Screen.Match)?.session?.close()
        screen = Screen.Menu
    }

    BackHandler(enabled = screen != Screen.Menu) { exitMatch() }

    when (val s = screen) {
        Screen.Menu -> MenuScreen(
            onLocal = { screen = Screen.Match(Session.local()) },
            onHost = { screen = Screen.Match(Session.host()) },
            onJoin = { screen = Screen.Join },
            onRules = { screen = Screen.Rules },
        )
        Screen.Join -> JoinScreen(
            onConnect = { ip -> screen = Screen.Match(Session.join(ip)) },
            onBack = { screen = Screen.Menu },
        )
        Screen.Rules -> RulesScreen(onBack = { screen = Screen.Menu })
        is Screen.Match -> MatchScreen(s.session, onExit = ::exitMatch)
    }
}
