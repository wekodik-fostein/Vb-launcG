package com.vb.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Полноэкранный редактор: таскаешь кнопки пальцем, как они будут в игре. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlsEditorScreen(controls: ControlsState, onDone: () -> Unit) {
    ImmersiveEffect()
    BackHandler(onBack = onDone)

    var selected by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    val current = controls.items.firstOrNull { it.id == selected }

    Box(Modifier.fillMaxSize().background(Color(0xFF0A0B10))) {
        Text(
            "Перетаскивай кнопки. Тап — выбрать.",
            color = Color.White.copy(alpha = 0.35f),
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.Center)
        )

        ControlsLayer(
            state = controls,
            editable = true,
            selectedId = selected,
            onSelect = { selected = it },
            onButton = { _, _ -> },
            onStick = { _, _, _ -> },
            modifier = Modifier.fillMaxSize()
        )

        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onDone) { Text("Готово") }
            OutlinedButton(onClick = { showAdd = true }) { Text("+ Кнопка") }
            OutlinedButton(onClick = { controls.reset(); selected = null }) { Text("Сброс") }

            if (current != null) {
                Text(current.label, color = Color.White, fontSize = 12.sp)
                Slider(
                    value = current.sizeDp,
                    onValueChange = { controls.resize(current.id, it) },
                    valueRange = 36f..200f,
                    modifier = Modifier.width(160.dp)
                )
                TextButton(onClick = { controls.remove(current.id); selected = null }) {
                    Text("Удалить", color = Color(0xFFFF8A8A))
                }
            }
        }
    }

    LaunchedEffect(controls.items.size) {
        controls.selectedHint?.let { selected = it }
        controls.selectedHint = null
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            confirmButton = { TextButton(onClick = { showAdd = false }) { Text("Закрыть") } },
            title = { Text("Добавить кнопку") },
            text = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BUTTON_LIBRARY.forEach { (label, code) ->
                        AssistChip(
                            onClick = { controls.add(label, code); showAdd = false },
                            label = { Text(label) }
                        )
                    }
                }
            }
        )
    }
}
