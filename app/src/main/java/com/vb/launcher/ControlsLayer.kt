package com.vb.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Слой тач-управления.
 * editable = true  → кнопки можно таскать (режим редактора).
 * editable = false → кнопки нажимаются и шлют события (режим игры).
 */
@Composable
fun ControlsLayer(
    state: ControlsState,
    editable: Boolean,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onButton: (String, Boolean) -> Unit,
    onStick: (String, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        state.items.toList().forEach { item ->
            key(item.id) {
                val sizePx = with(density) { item.sizeDp.dp.toPx() }
                val selected = editable && item.id == selectedId
                val accent = MaterialTheme.colorScheme.primary

                val gesture: Modifier = when {
                    editable -> Modifier.pointerInput(item.id, w, h) {
                        detectDragGestures(
                            onDragStart = { onSelect(item.id) }
                        ) { change, drag ->
                            change.consume()
                            state.move(item.id, drag.x / w, drag.y / h)
                        }
                    }.pointerInput(item.id) {
                        detectTapGestures(onTap = { onSelect(item.id) })
                    }
                    item.kind == CtrlKind.BUTTON -> Modifier.pointerInput(item.id) {
                        detectTapGestures(onPress = {
                            onButton(item.id, true)
                            tryAwaitRelease()
                            onButton(item.id, false)
                        })
                    }
                    else -> Modifier
                }

                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                (item.x * w - sizePx / 2).roundToInt(),
                                (item.y * h - sizePx / 2).roundToInt()
                            )
                        }
                        .size(item.sizeDp.dp)
                        .background(Color.White.copy(alpha = 0.10f), CircleShape)
                        .border(
                            width = if (selected) 3.dp else 1.5.dp,
                            color = if (selected) accent else Color.White.copy(alpha = 0.45f),
                            shape = CircleShape
                        )
                        .then(gesture),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.kind == CtrlKind.STICK) {
                        StickKnob(item, editable, sizePx, onStick)
                    } else {
                        Text(
                            item.label,
                            color = Color.White,
                            fontSize = (item.sizeDp / 5.2f).coerceIn(9f, 16f).sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StickKnob(
    item: CtrlItem,
    editable: Boolean,
    sizePx: Float,
    onStick: (String, Float, Float) -> Unit
) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    val radius = sizePx / 2f
    val knobDp = (item.sizeDp * 0.42f).dp

    val gesture = if (editable) Modifier else Modifier.pointerInput(item.id, sizePx) {
        detectDragGestures(
            onDragEnd = { knob = Offset.Zero; onStick(item.id, 0f, 0f) },
            onDragCancel = { knob = Offset.Zero; onStick(item.id, 0f, 0f) }
        ) { change, drag ->
            change.consume()
            var n = knob + drag
            val len = hypot(n.x, n.y)
            if (len > radius * 0.6f) n = n * (radius * 0.6f / len)
            knob = n
            onStick(item.id, n.x / (radius * 0.6f), n.y / (radius * 0.6f))
        }
    }

    Box(
        Modifier
            .size(item.sizeDp.dp)
            .then(gesture),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .offset { IntOffset(knob.x.roundToInt(), knob.y.roundToInt()) }
                .size(knobDp)
                .background(Color.White.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (editable) Text(item.label, color = Color.White, fontSize = 10.sp)
        }
    }
}
