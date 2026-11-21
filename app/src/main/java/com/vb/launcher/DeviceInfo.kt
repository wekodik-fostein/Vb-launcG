package com.vb.launcher

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build

enum class GpuFamily(val label: String) {
    XCLIPSE("Samsung Xclipse (AMD RDNA)"),
    ADRENO("Qualcomm Adreno"),
    MALI("ARM Mali"),
    UNKNOWN("Неизвестно")
}

data class DeviceSnapshot(
    val model: String,
    val soc: String,
    val glRenderer: String,
    val gpu: GpuFamily,
    val androidVersion: String,
    val totalRamMb: Long
)

data class RamUsage(val usedMb: Long, val totalMb: Long) {
    val fraction: Float get() = if (totalMb <= 0) 0f else (usedMb.toFloat() / totalMb).coerceIn(0f, 1f)
}

object DeviceInfo {

    fun read(ctx: Context): DeviceSnapshot {
        val renderer = queryGlRenderer().orEmpty()
        val soc = buildString {
            if (Build.VERSION.SDK_INT >= 31 && Build.SOC_MODEL.isNotBlank() && Build.SOC_MODEL != "unknown") {
                append(Build.SOC_MANUFACTURER).append(' ').append(Build.SOC_MODEL)
            } else {
                append(Build.HARDWARE)
            }
        }
        return DeviceSnapshot(
            model = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            soc = soc,
            glRenderer = renderer.ifBlank { "н/д" },
            gpu = detectFamily(renderer, soc),
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            totalRamMb = ram(ctx).totalMb
        )
    }

    fun ram(ctx: Context): RamUsage {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val total = mi.totalMem / (1024 * 1024)
        val used = (mi.totalMem - mi.availMem) / (1024 * 1024)
        return RamUsage(used, total)
    }

    private fun detectFamily(renderer: String, soc: String): GpuFamily {
        val r = renderer.lowercase()
        return when {
            "xclipse" in r -> GpuFamily.XCLIPSE
            "adreno" in r -> GpuFamily.ADRENO
            "mali" in r -> GpuFamily.MALI
            else -> {
                val s = soc.lowercase()
                when {
                    "snapdragon" in s || "qualcomm" in s || "qcom" in s -> GpuFamily.ADRENO
                    "exynos" in s || "s5e" in s -> GpuFamily.UNKNOWN
                    "mediatek" in s || "dimensity" in s || "tensor" in s -> GpuFamily.MALI
                    else -> GpuFamily.UNKNOWN
                }
            }
        }
    }

    /** Читаем GL_RENDERER через временный EGL-контекст. */
    private fun queryGlRenderer(): String? = try {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val ver = IntArray(2)
        EGL14.eglInitialize(display, ver, 0, ver, 1)
        val attrs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val n = IntArray(1)
        EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, n, 0)
        val ctx = EGL14.eglCreateContext(
            display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        val surf = EGL14.eglCreatePbufferSurface(
            display, configs[0],
            intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
        )
        EGL14.eglMakeCurrent(display, surf, surf, ctx)
        val result = GLES20.glGetString(GLES20.GL_RENDERER)
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, surf)
        EGL14.eglDestroyContext(display, ctx)
        EGL14.eglTerminate(display)
        result
    } catch (t: Throwable) {
        null
    }
}
