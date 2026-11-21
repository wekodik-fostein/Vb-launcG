package com.vb.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
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
            VbTheme {
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

@Composable
fun VbTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFFF5C8A),
            onPrimary = Color(0xFF1A0008),
            secondary = Color(0xFF7DD3FC),
            background = Color(0xFF0E0F14),
            surface = Color(0xFF151823),
            surfaceVariant = Color(0xFF1D2130),
            onBackground = Color(0xFFECEDF2),
            onSurface = Color(0xFFECEDF2)
        ),
        content = content
    )
}

private enum class Tab(val label: String, val icon: String) {
    HOME("Главная", "🏠"),
    GAMES("Игры", "🎮"),
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
                    Tab.GAMES -> GamesScreen(settings)
                    Tab.SETTINGS -> SettingsScreen(settings, device)
                    Tab.CONTROLS -> ControlsTabScreen(controls) { editing = true }
                }
            }
        }
    }
}
