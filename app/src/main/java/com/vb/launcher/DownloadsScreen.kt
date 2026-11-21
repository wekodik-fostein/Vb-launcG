package com.vb.launcher

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DownloadsScreen() {
    val ctx = LocalContext.current
    var url by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val active = Downloads.tasks.filter { it.status == DlStatus.RUNNING }
    val speed = active.sumOf { it.speedBps }

    Page {
        Text("Загрузки", style = MaterialTheme.typography.headlineMedium)

        // «Фон загрузки»: общая скорость сверху
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.40f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.30f)
                        )
                    ),
                    RoundedCornerShape(16.dp)
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (active.isEmpty()) "Нет активных загрузок" else "⬇ ${fmtMBps(speed)}",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    if (active.isEmpty()) "Вставь прямую ссылку ниже" else "${fmtMbit(speed)} · активных: ${active.size}",
                    fontSize = 13.sp
                )
                Text("Качается в фоне, даже если свернуть приложение.", fontSize = 12.sp)
            }
        }

        Section("Новая загрузка") {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; err = null },
                label = { Text("Прямая ссылка на файл") },
                isError = err != null,
                supportingText = { Text(err ?: "Нужна именно прямая ссылка, не страница облачного хранилища.") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                val e = Downloads.add(ctx, url)
                if (e == null) url = "" else err = e
            }) { Text("Скачать") }
            Text(
                "Файлы: Android/data/com.vb.launcher/files/downloads",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Section("Помощь") {
            Text("Не получается скачать или запустить? Загляни в наш Телеграм-канал.", fontSize = 13.sp)
            OutlinedButton(onClick = { openUrl(ctx, TG_URL) }) { Text("Телеграм-канал помощи") }
        }

        Downloads.tasks.toList().forEach { DownloadCard(it) }
    }
}

@Composable
private fun DownloadCard(t: DlTask) {
    val ctx = LocalContext.current
    Section(t.file.name) {
        if (t.status == DlStatus.RUNNING && t.total <= 0) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { t.fraction }, modifier = Modifier.fillMaxWidth())
        }
        Text(
            "${fmtBytes(t.downloaded)} / ${fmtBytes(t.total)}" +
                if (t.total > 0) " · ${(t.fraction * 100).toInt()}%" else "",
            fontSize = 13.sp
        )
        if (t.status == DlStatus.RUNNING) {
            Text(
                "${fmtMBps(t.speedBps)} · ${fmtMbit(t.speedBps)} · осталось ${fmtEta(t.etaSec)}",
                fontSize = 13.sp
            )
        } else {
            Text(
                t.status.label + (t.error?.let { ": $it" } ?: ""),
                fontSize = 13.sp,
                color = if (t.status == DlStatus.ERROR) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (t.status) {
                DlStatus.RUNNING -> {
                    OutlinedButton(onClick = { Downloads.pause(t) }) { Text("Пауза") }
                    TextButton(onClick = { Downloads.cancel(t) }) { Text("Отмена") }
                }
                DlStatus.PAUSED, DlStatus.ERROR -> {
                    Button(onClick = { Downloads.resume(ctx, t) }) { Text("Продолжить") }
                    TextButton(onClick = { Downloads.cancel(t) }) { Text("Отмена") }
                }
                else -> TextButton(onClick = { Downloads.remove(t) }) { Text("Убрать из списка") }
            }
        }
    }
}
