package com.vb.launcher

import android.content.Context
import java.io.File

/**
 * Генерирует конфиг для слоя Wine/Box64/DXVK (Winlator-совместимые переменные).
 * Пока сам движок не подключён, файл просто пишется в память приложения —
 * его подхватит будущий мост к движку.
 */
object EngineConfig {

    fun build(ctx: Context, s: SettingsState, device: DeviceSnapshot): String {
        val (rw, rh) = s.renderSize(ctx)
        val (ow, oh) = s.outputSize(ctx)
        val env = linkedMapOf<String, String>()

        // Box64 dynarec
        when (s.cpuPreset) {
            CpuPreset.SAFE -> {
                env["BOX64_DYNAREC_BIGBLOCK"] = "0"
                env["BOX64_DYNAREC_STRONGMEM"] = "2"
                env["BOX64_DYNAREC_FASTNAN"] = "0"
                env["BOX64_DYNAREC_FASTROUND"] = "0"
                env["BOX64_DYNAREC_SAFEFLAGS"] = "2"
                env["BOX64_DYNAREC_CALLRET"] = "0"
            }
            CpuPreset.BALANCED -> {
                env["BOX64_DYNAREC_BIGBLOCK"] = "1"
                env["BOX64_DYNAREC_STRONGMEM"] = "1"
                env["BOX64_DYNAREC_FASTNAN"] = "1"
                env["BOX64_DYNAREC_FASTROUND"] = "0"
                env["BOX64_DYNAREC_SAFEFLAGS"] = "2"
                env["BOX64_DYNAREC_CALLRET"] = "0"
            }
            CpuPreset.FAST -> {
                env["BOX64_DYNAREC_BIGBLOCK"] = "3"
                env["BOX64_DYNAREC_STRONGMEM"] = "0"
                env["BOX64_DYNAREC_FASTNAN"] = "1"
                env["BOX64_DYNAREC_FASTROUND"] = "1"
                env["BOX64_DYNAREC_SAFEFLAGS"] = "1"
                env["BOX64_DYNAREC_CALLRET"] = "1"
            }
        }

        // FSR (wine-fsr): strength 0 = резче, 5 = мягче
        if (s.fsrEnabled) {
            env["WINE_FULLSCREEN_FSR"] = "1"
            env["WINE_FULLSCREEN_FSR_STRENGTH"] = (5 - (s.fsrSharpness / 20)).coerceIn(0, 5).toString()
        }

        env["DXVK_STATE_CACHE"] = "1"
        env["VB_GPU_FAMILY"] = device.gpu.name
        env["VB_RENDER_SIZE"] = "${rw}x$rh"
        env["VB_OUTPUT_SIZE"] = "${ow}x$oh"

        val sb = StringBuilder()
        sb.appendLine("# --- environment ---")
        env.forEach { (k, v) -> sb.appendLine("$k=$v") }
        sb.appendLine()
        sb.appendLine("# --- dxvk.conf ---")
        sb.appendLine("dxvk.enableAsync = ${if (s.dxvkAsync) "True" else "False"}")
        if (s.fpsLimit > 0) {
            sb.appendLine("dxgi.maxFrameRate = ${s.fpsLimit}")
            sb.appendLine("d3d9.maxFrameRate = ${s.fpsLimit}")
        }
        if (device.gpu == GpuFamily.XCLIPSE) {
            sb.appendLine("# Xclipse (RDNA): экспериментально")
            sb.appendLine("dxvk.numCompilerThreads = 0")
        }
        return sb.toString()
    }

    fun write(ctx: Context, text: String): File {
        val f = File(ctx.filesDir, "engine.conf")
        f.writeText(text)
        return f
    }
}
