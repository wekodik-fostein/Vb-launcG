package com.vb.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settings = SettingsState(applicationContext)
        val controls = ControlsState(applicationContext)
        val device = DeviceInfo.read(this)

        setContent {
            VbTheme(settings.themeMode) {
                LaunchedEffect(Unit) {
                    snapshotFlow { settings.snapshot() }.collect { settings.save() }
                }
                LaunchedEffect(Unit) {
                    snapshotFlow { controls.items.toList() }.collect { controls.save() }
                }
                App(settings, controls, device)
            }
        }
    }
}

private val DarkGray = darkColorScheme(
    primary = Color(0xFFFF5C8A),
    onPrimary = Color(0xFF1A0008),
    secondary = Color(0xFF7DD3FC),
    background = Color(0xFF1B1C1F),
    surface = Color(0xFF26272C),
    surfaceVariant = Color(0xFF303137),
    onBackground = Color(0xFFECEDF0),
    onSurface = Color(0xFFECEDF0)
)

private val White = lightColorScheme(
    primary = Color(0xFFD81B60),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF0277BD),
    background = Color(0xFFF6F6F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEBEBF0),
    onBackground = Color(0xFF1B1C1F),
    onSurface = Color(0xFF1B1C1F)
)

@Composable
fun VbTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window
        if (window != null) {
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = if (dark) DarkGray else White, content = content)
}

private enum class Tab(val label: String, val icon: String) {
    HOME("Главная", "🏠"),
    GAMES("Игры", "🎮"),
    DOWNLOADS("Загрузки", "⬇️"),
    SETTINGS("Настройки", "⚙️"),
    CONTROLS("Управление", "👆")
}

@Composable
fun App(settings: SettingsState, controls: ControlsState, device: DeviceSnapshot) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME.name) }
    var playing by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }

    val game = playing
    when {
        game != null -> GameScreen(game, settings, controls, device) { playing = null }
        editing -> ControlsEditorScreen(controls) { editing = false }
        else -> Scaffold(
            bottomBar = {
                NavigationBar {
                    Tab.values().forEach { t ->
                        NavigationBarItem(
                            selected = tab == t.name,
                            onClick = { tab = t.name },
                            icon = { Text(t.icon) },
                            label = { Text(t.label) }
                        )
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (Tab.valueOf(tab)) {
                    Tab.HOME -> HomeScreen(settings, device) { playing = it }
                    Tab.GAMES -> GamesScreen(settings) { tab = Tab.DOWNLOADS.name }
                    Tab.DOWNLOADS -> DownloadsScreen()
                    Tab.SETTINGS -> SettingsScreen(settings, device)
                    Tab.CONTROLS -> ControlsTabScreen(controls) { editing = true }
                }
            }
        }
    }
}
