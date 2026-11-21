package com.vb.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class CpuPreset(val label: String, val hint: String) {
    SAFE("Стабильно", "Меньше вылетов, медленнее"),
    BALANCED("Баланс", "Рекомендуется для старта"),
    FAST("Быстро", "Быстрее, возможны глюки")
}

enum class ThemeMode(val label: String) {
    DARK("Тёмно-серая"),
    LIGHT("Белая"),
    SYSTEM("Как в системе")
}

data class ResPreset(val label: String, val w: Int, val h: Int)

val RES_PRESETS = listOf(
    ResPreset("640×360", 640, 360),
    ResPreset("854×480", 854, 480),
    ResPreset("1280×720", 1280, 720),
    ResPreset("1600×900", 1600, 900),
    ResPreset("1920×1080", 1920, 1080),
    ResPreset("Родное", 0, 0)
)

val FPS_OPTIONS = listOf(30, 45, 60, 0) // 0 = без лимита

data class SettingsSnapshot(
    val resIndex: Int, val useCustom: Boolean, val customW: Int, val customH: Int,
    val fps: Int, val fsr: Boolean, val fsrScale: Int, val fsrSharp: Int,
    val cpu: CpuPreset, val dxvkAsync: Boolean, val hud: Boolean,
    val gta4: String?, val gta5: String?, val theme: ThemeMode
)

class SettingsState(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("vb_settings", Context.MODE_PRIVATE)

    var resIndex by mutableIntStateOf(2)
    var useCustom by mutableStateOf(false)
    var customW by mutableIntStateOf(1280)
    var customH by mutableIntStateOf(720)
    var fpsLimit by mutableIntStateOf(30)
    var fsrEnabled by mutableStateOf(false)
    var fsrScale by mutableIntStateOf(75)
    var fsrSharpness by mutableIntStateOf(50)
    var cpuPreset by mutableStateOf(CpuPreset.BALANCED)
    var dxvkAsync by mutableStateOf(true)
    var showHud by mutableStateOf(true)
    var gta4Uri by mutableStateOf<String?>(null)
    var gta5Uri by mutableStateOf<String?>(null)
    var gta4Exe by mutableStateOf<String?>(null)
    var gta5Exe by mutableStateOf<String?>(null)
    var themeMode by mutableStateOf(ThemeMode.DARK)

    init {
        resIndex = prefs.getInt("resIndex", 2).coerceIn(0, RES_PRESETS.lastIndex)
        useCustom = prefs.getBoolean("useCustom", false)
        customW = prefs.getInt("customW", 1280)
        customH = prefs.getInt("customH", 720)
        fpsLimit = prefs.getInt("fps", 30)
        fsrEnabled = prefs.getBoolean("fsr", false)
        fsrScale = prefs.getInt("fsrScale", 75)
        fsrSharpness = prefs.getInt("fsrSharp", 50)
        cpuPreset = runCatching { CpuPreset.valueOf(prefs.getString("cpu", "BALANCED")!!) }
            .getOrDefault(CpuPreset.BALANCED)
        dxvkAsync = prefs.getBoolean("dxvkAsync", true)
        showHud = prefs.getBoolean("hud", true)
        gta4Uri = prefs.getString("gta4", null)
        gta5Uri = prefs.getString("gta5", null)
        gta4Exe = prefs.getString("gta4exe", null)
        gta5Exe = prefs.getString("gta5exe", null)
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString("theme", "DARK")!!) }
            .getOrDefault(ThemeMode.DARK)
    }

    fun snapshot() = SettingsSnapshot(
        resIndex, useCustom, customW, customH, fpsLimit, fsrEnabled, fsrScale,
        fsrSharpness, cpuPreset, dxvkAsync, showHud, gta4Uri, gta5Uri, themeMode
    )

    fun save() {
        prefs.edit()
            .putInt("resIndex", resIndex)
            .putBoolean("useCustom", useCustom)
            .putInt("customW", customW)
            .putInt("customH", customH)
            .putInt("fps", fpsLimit)
            .putBoolean("fsr", fsrEnabled)
            .putInt("fsrScale", fsrScale)
            .putInt("fsrSharp", fsrSharpness)
            .putString("cpu", cpuPreset.name)
            .putBoolean("dxvkAsync", dxvkAsync)
            .putBoolean("hud", showHud)
            .putString("gta4", gta4Uri)
            .putString("gta5", gta5Uri)
            .putString("gta4exe", gta4Exe)
            .putString("gta5exe", gta5Exe)
            .putString("theme", themeMode.name)
            .apply()
    }

    /** Итоговое выходное разрешение (то, что видит экран). */
    fun outputSize(ctx: Context): Pair<Int, Int> {
        val dm = ctx.resources.displayMetrics
        val nativeW = maxOf(dm.widthPixels, dm.heightPixels)
        val nativeH = minOf(dm.widthPixels, dm.heightPixels)
        if (useCustom) {
            return customW.coerceIn(160, 4096) to customH.coerceIn(120, 4096)
        }
        val p = RES_PRESETS[resIndex]
        return if (p.w == 0) nativeW to nativeH else p.w to p.h
    }

    /** Внутреннее разрешение рендера: при FSR рисуем меньше и растягиваем. */
    fun renderSize(ctx: Context): Pair<Int, Int> {
        val (w, h) = outputSize(ctx)
        if (!fsrEnabled) return w to h
        val k = fsrScale.coerceIn(40, 100) / 100f
        return (w * k).toInt().coerceAtLeast(160) to (h * k).toInt().coerceAtLeast(120)
    }
}
