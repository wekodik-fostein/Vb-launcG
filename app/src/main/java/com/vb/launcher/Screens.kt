package com.vb.launcher

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ───────── общие кусочки ─────────

@Composable
internal fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) { content() }
}

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        Text(v, fontSize = 13.sp)
    }
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (sub != null) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ───────── Главная ─────────

@Composable
fun HomeScreen(settings: SettingsState, device: DeviceSnapshot, onPlay: (String) -> Unit) {
    val ctx = LocalContext.current
    var ram by remember { mutableStateOf(DeviceInfo.ram(ctx)) }
    LaunchedEffect(Unit) {
        while (true) { ram = DeviceInfo.ram(ctx); delay(1000) }
    }
    val (rw, rh) = settings.renderSize(ctx)
    val (ow, oh) = settings.outputSize(ctx)

    Page {
        Text("VB Launcher", style = MaterialTheme.typography.headlineMedium)
        Text("бета · Xclipse", color = MaterialTheme.colorScheme.primary)

        Section("Устройство") {
            InfoRow("Модель", device.model)
            InfoRow("Чип", device.soc)
            InfoRow("GPU", device.gpu.label)
            InfoRow("Рендерер", device.glRenderer)
            InfoRow("Система", device.androidVersion)
        }

        Section("Оперативная память") {
            LinearProgressIndicator(progress = { ram.fraction }, modifier = Modifier.fillMaxWidth())
            Text(
                "Занято ${"%.1f".format(ram.usedMb / 1024f)} ГБ из ${"%.1f".format(ram.totalMb / 1024f)} ГБ",
                fontSize = 13.sp
            )
        }

        Section("Текущий профиль") {
            InfoRow("Экран", "${ow}×$oh")
            InfoRow("Рендер", "${rw}×$rh" + if (settings.fsrEnabled) "  (FSR ${settings.fsrScale}%)" else "")
            InfoRow("Лимит FPS", if (settings.fpsLimit > 0) "${settings.fpsLimit}" else "нет")
            InfoRow("CPU-пресет", settings.cpuPreset.label)
            Text(
                when (device.gpu) {
                    GpuFamily.XCLIPSE -> "Xclipse (RDNA): Vulkan есть, но драйвер Samsung закрытый. Начни с 720p, лимит 30 FPS и FSR."
                    GpuFamily.ADRENO -> "Adreno: лучший вариант для Windows-игр на Android, есть драйвер Turnip."
                    GpuFamily.MALI -> "Mali: Vulkan-драйверы слабее, выставляй низкое разрешение."
                    GpuFamily.UNKNOWN -> "GPU не определён. Начни с 720p и 30 FPS."
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        Section("Запуск") {
            Text(
                "Движок (Wine/Box64/DXVK) пока не подключён. Сейчас запуск открывает превью: " +
                    "полный экран, выбранное разрешение, FPS-лимит, HUD и твоё тач-управление.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onPlay("GTA IV") }, enabled = settings.gta4Uri != null) { Text("▶ GTA IV") }
                Button(onClick = { onPlay("GTA V") }, enabled = settings.gta5Uri != null) { Text("▶ GTA V") }
            }
            if (settings.gta4Uri == null && settings.gta5Uri == null) {
                Text("Сначала укажи папку игры во вкладке «Игры».", fontSize = 12.sp)
            }
        }
    }
}

// ───────── Игры (выбор файлов) ─────────

internal const val TG_URL = "https://t.me/vblauncher"

internal fun openUrl(ctx: android.content.Context, url: String) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun persistTree(ctx: android.content.Context, uri: Uri) {
    runCatching {
        ctx.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }
}

@Composable
fun GamesScreen(settings: SettingsState, onOpenDownloads: () -> Unit) {
    val ctx = LocalContext.current
    val pick4 = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { persistTree(ctx, uri); settings.gta4Uri = uri.toString() }
    }
    val pick5 = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { persistTree(ctx, uri); settings.gta5Uri = uri.toString() }
    }

    Page {
        Text("Игры", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Укажи папку со своей легально купленной копией. Лаунчер сам игры не содержит и не скачивает.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        GameCard("GTA IV", settings.gta4Uri, "https://store.steampowered.com/app/12210/", { pick4.launch(null) }, onOpenDownloads) {
            settings.gta4Uri = null
        }
        GameCard("GTA V", settings.gta5Uri, "https://store.steampowered.com/app/271590/", { pick5.launch(null) }, onOpenDownloads) {
            settings.gta5Uri = null
        }
    }
}

@Composable
private fun GameCard(
    title: String,
    uri: String?,
    storeUrl: String,
    onPick: () -> Unit,
    onOpenDownloads: () -> Unit,
    onClear: () -> Unit
) {
    val ctx = LocalContext.current
    var help by remember { mutableStateOf(false) }

    Section(title) {
        Text(
            uri?.let { Uri.parse(it).lastPathSegment ?: it } ?: "Папка не выбрана",
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onPick) { Text(if (uri == null) "Выбрать папку" else "Сменить") }
            if (uri != null) TextButton(onClick = onClear) { Text("Убрать") }
            else TextButton(onClick = { help = true }) { Text("Нет игры/файла?") }
        }
    }

    if (help) {
        AlertDialog(
            onDismissRequest = { help = false },
            title = { Text("Нет $title?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Лаунчер игры не раздаёт и не содержит. Купи свою копию (например, в Steam) " +
                            "и укажи её папку. Если у тебя есть прямая ссылка на твои собственные файлы, " +
                            "их можно скачать во вкладке «Загрузки» — с прогрессом, скоростью и в фоне."
                    )
                    Text("Телеграм-канал: здесь можно найти нужное.")
                    TextButton(onClick = { openUrl(ctx, TG_URL) }) { Text("Открыть канал t.me/vblauncher") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    help = false
                    runCatching {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(storeUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) { Text("Открыть магазин") }
            },
            dismissButton = {
                TextButton(onClick = { help = false; onOpenDownloads() }) { Text("Загрузки") }
            }
        )
    }
}

// ───────── Настройки ─────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(settings: SettingsState, device: DeviceSnapshot) {
    val ctx = LocalContext.current
    val (rw, rh) = settings.renderSize(ctx)
    val (ow, oh) = settings.outputSize(ctx)

    Page {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium)

        Section("Разрешение экрана") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RES_PRESETS.forEachIndexed { i, p ->
                    FilterChip(
                        selected = !settings.useCustom && settings.resIndex == i,
                        onClick = { settings.useCustom = false; settings.resIndex = i },
                        label = { Text(p.label) }
                    )
                }
                FilterChip(
                    selected = settings.useCustom,
                    onClick = { settings.useCustom = true },
                    label = { Text("Своё") }
                )
            }
            if (settings.useCustom) {
                var wText by remember { mutableStateOf(settings.customW.toString()) }
                var hText by remember { mutableStateOf(settings.customH.toString()) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = wText,
                        onValueChange = { t ->
                            wText = t.filter { it.isDigit() }.take(4)
                            wText.toIntOrNull()?.let { settings.customW = it }
                        },
                        label = { Text("Ширина") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Text("×")
                    OutlinedTextField(
                        value = hText,
                        onValueChange = { t ->
                            hText = t.filter { it.isDigit() }.take(4)
                            hText.toIntOrNull()?.let { settings.customH = it }
                        },
                        label = { Text("Высота") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text(
                "Экран: ${ow}×$oh · рендер: ${rw}×$rh",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        Section("FSR (апскейл, костыльный)") {
            SwitchRow(
                "Включить FSR",
                "Рисуем в меньшем разрешении и растягиваем с резкостью. Разгружает GPU.",
                settings.fsrEnabled
            ) { settings.fsrEnabled = it }
            if (settings.fsrEnabled) {
                Text("Масштаб рендера: ${settings.fsrScale}%", fontSize = 13.sp)
                Slider(
                    value = settings.fsrScale.toFloat(),
                    onValueChange = { settings.fsrScale = it.toInt() },
                    valueRange = 40f..100f
                )
                Text("Резкость: ${settings.fsrSharpness}%", fontSize = 13.sp)
                Slider(
                    value = settings.fsrSharpness.toFloat(),
                    onValueChange = { settings.fsrSharpness = it.toInt() },
                    valueRange = 0f..100f
                )
            }
        }

        Section("Лимит FPS") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FPS_OPTIONS.forEach { f ->
                    FilterChip(
                        selected = settings.fpsLimit == f,
                        onClick = { settings.fpsLimit = f },
                        label = { Text(if (f == 0) "Без лимита" else "$f") }
                    )
                }
            }
        }

        Section("Помощь процессору (Box64)") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CpuPreset.values().forEach { p ->
                    FilterChip(
                        selected = settings.cpuPreset == p,
                        onClick = { settings.cpuPreset = p },
                        label = { Text(p.label) }
                    )
                }
            }
            Text(settings.cpuPreset.hint, fontSize = 12.sp)
            SwitchRow(
                "Асинхронные шейдеры (DXVK async)",
                "Меньше подлагов при компиляции шейдеров.",
                settings.dxvkAsync
            ) { settings.dxvkAsync = it }
        }

        Section("Тема приложения") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.values().forEach { m ->
                    FilterChip(
                        selected = settings.themeMode == m,
                        onClick = { settings.themeMode = m },
                        label = { Text(m.label) }
                    )
                }
            }
        }

        Section("Интерфейс") {
            SwitchRow("HUD в игре", "Устройство, FPS, разрешение и занятая ОЗУ.", settings.showHud) { settings.showHud = it }
        }

        Section("Конфиг для движка") {
            val cfg = EngineConfig.build(ctx, settings, device)
            Text(cfg, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            OutlinedButton(onClick = {
                val f = EngineConfig.write(ctx, cfg)
                Toast.makeText(ctx, "Сохранено: ${f.name}", Toast.LENGTH_SHORT).show()
            }) { Text("Сохранить конфиг") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ───────── Управление ─────────

@Composable
fun ControlsTabScreen(controls: ControlsState, onOpenEditor: () -> Unit) {
    Page {
        Text("Управление", style = MaterialTheme.typography.headlineMedium)
        Section("Тач-раскладка") {
            Text(
                "Кнопок и стиков сейчас: ${controls.items.size}. Редактор открывается на весь экран " +
                    "в альбомной ориентации: двигай кнопки пальцем, меняй размер, добавляй и удаляй.",
                fontSize = 13.sp
            )
            Button(onClick = onOpenEditor) { Text("Открыть редактор") }
            OutlinedButton(onClick = { controls.reset() }) { Text("Сбросить по умолчанию") }
        }
        Section("Что есть") {
            controls.items.forEach {
                Text("• ${it.label}  (${if (it.kind == CtrlKind.STICK) "стик" else "кнопка"})", fontSize = 13.sp)
            }
        }
    }
}
