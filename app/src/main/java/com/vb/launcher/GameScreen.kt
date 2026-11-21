package com.vb.launcher

import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sin

/**
 * Экран «игры». Движок Wine/Box64/DXVK пока не подключён, поэтому тут живое
 * превью: поверхность реально создаётся в выбранном разрешении и растягивается
 * на весь экран, считается FPS с учётом лимита, поверх — тач-управление и HUD.
 */
@Composable
fun GameScreen(
    title: String,
    settings: SettingsState,
    controls: ControlsState,
    device: DeviceSnapshot,
    onExit: () -> Unit
) {
    val ctx = LocalContext.current
    ImmersiveEffect()
    BackHandler(onBack = onExit)

    val (rw, rh) = settings.renderSize(ctx)
    val (ow, oh) = settings.outputSize(ctx)

    val fpsCounter = remember { AtomicInteger(0) }
    var fps by remember { mutableIntStateOf(0) }
    var ram by remember { mutableStateOf(DeviceInfo.ram(ctx)) }
    var lastInput by remember { mutableStateOf("—") }
    val renderer = remember {
        PreviewRenderer(title, fpsCounter) { settings.fpsLimit }
    }

    LaunchedEffect(Unit) {
        while (true) {
            fps = fpsCounter.get()
            ram = DeviceInfo.ram(ctx)
            delay(500)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                SurfaceView(c).apply {
                    holder.setFixedSize(rw, rh)
                    holder.addCallback(renderer)
                }
            },
            update = { it.holder.setFixedSize(rw, rh) }
        )

        ControlsLayer(
            state = controls,
            editable = false,
            selectedId = null,
            onSelect = {},
            onButton = { id, down -> lastInput = "$id ${if (down) "↓" else "↑"}" },
            onStick = { id, x, y ->
                lastInput = "$id (%.2f, %.2f)".format(x, y)
            },
            modifier = Modifier.fillMaxSize()
        )

        if (settings.showHud) {
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(8.dp)
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                val hud = 11.sp
                Text("$title · ${device.model}", color = Color.White, fontSize = hud)
                Text("GPU: ${device.gpu.label}", color = Color.White, fontSize = hud)
                Text(
                    "Рендер ${rw}×$rh → экран ${ow}×$oh" + if (settings.fsrEnabled) " (FSR)" else "",
                    color = Color.White, fontSize = hud
                )
                Text(
                    "FPS $fps" + if (settings.fpsLimit > 0) " / ${settings.fpsLimit}" else "",
                    color = Color.White, fontSize = hud
                )
                Text(
                    "ОЗУ ${"%.1f".format(ram.usedMb / 1024f)} / ${"%.1f".format(ram.totalMb / 1024f)} ГБ",
                    color = Color.White, fontSize = hud
                )
                Text("Ввод: $lastInput", color = Color(0xFFFFB3C6), fontSize = hud)
            }
        }

        TextButton(
            onClick = onExit,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.displayCutout)
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
        ) { Text("✕ Выход", color = Color.White) }
    }
}

private class PreviewRenderer(
    private val title: String,
    private val fpsOut: AtomicInteger,
    private val fpsLimit: () -> Int
) : SurfaceHolder.Callback {

    @Volatile private var running = false
    private var thread: Thread? = null

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        thread = Thread { loop(holder) }.also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        thread?.join()
        thread = null
    }

    private fun loop(holder: SurfaceHolder) {
        val bg = Paint()
        val line = Paint().apply { color = AColor.argb(40, 255, 255, 255); strokeWidth = 2f }
        val bar = Paint().apply { color = AColor.rgb(255, 92, 138) }
        val text = Paint().apply { color = AColor.WHITE; isAntiAlias = true }
        var phase = 0f
        var frames = 0
        var windowStart = System.nanoTime()

        while (running) {
            val frameStart = System.nanoTime()
            val canvas: Canvas? = try { holder.lockCanvas() } catch (e: Exception) { null }
            if (canvas == null) { Thread.sleep(5); continue }
            try {
                val w = canvas.width.toFloat()
                val h = canvas.height.toFloat()
                bg.color = AColor.rgb(
                    (20 + 15 * sin(phase)).toInt().coerceIn(0, 255),
                    (22 + 10 * sin(phase * 0.7f)).toInt().coerceIn(0, 255),
                    (40 + 25 * sin(phase * 0.5f)).toInt().coerceIn(0, 255)
                )
                canvas.drawPaint(bg)
                val step = (h / 9f).coerceAtLeast(16f)
                var gx = 0f
                while (gx < w) { canvas.drawLine(gx, 0f, gx, h, line); gx += step }
                var gy = 0f
                while (gy < h) { canvas.drawLine(0f, gy, w, gy, line); gy += step }
                val bx = ((phase * 0.35f) % 1f) * w
                canvas.drawRect(bx, 0f, bx + w * 0.02f, h, bar)
                text.textSize = h / 14f
                text.textAlign = Paint.Align.CENTER
                canvas.drawText(title, w / 2f, h * 0.48f, text)
                text.textSize = h / 26f
                canvas.drawText(
                    "превью рендера ${canvas.width}×${canvas.height} · движок не подключён",
                    w / 2f, h * 0.56f, text
                )
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
            phase += 0.05f
            frames++

            val now = System.nanoTime()
            if (now - windowStart >= 1_000_000_000L) {
                fpsOut.set(frames)
                frames = 0
                windowStart = now
            }

            val limit = fpsLimit()
            if (limit > 0) {
                val target = 1_000_000_000L / limit
                val spent = System.nanoTime() - frameStart
                if (spent < target) {
                    try { Thread.sleep((target - spent) / 1_000_000L) } catch (_: InterruptedException) {}
                }
            }
        }
    }
}
