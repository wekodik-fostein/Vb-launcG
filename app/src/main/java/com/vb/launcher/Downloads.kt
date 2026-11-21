package com.vb.launcher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.Executors

enum class DlStatus(val label: String) {
    RUNNING("Качается"), PAUSED("Пауза"), DONE("Готово"), ERROR("Ошибка"), CANCELED("Отменено")
}

class DlTask(val id: Int, val url: String, val file: File) {
    var status by mutableStateOf(DlStatus.RUNNING)
    var total by mutableLongStateOf(-1L)
    var downloaded by mutableLongStateOf(0L)
    var speedBps by mutableLongStateOf(0L)
    var error by mutableStateOf<String?>(null)

    /** 0 — работаем, 1 — пауза, 2 — отмена. */
    @Volatile var stopFlag = 0

    val fraction: Float get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
    val etaSec: Long get() = if (speedBps > 0 && total > 0) (total - downloaded) / speedBps else -1L
}

object Downloads {
    val tasks = mutableStateListOf<DlTask>()
    private var nextId = 1

    fun dir(ctx: Context): File =
        (ctx.getExternalFilesDir("downloads") ?: File(ctx.filesDir, "downloads")).also { it.mkdirs() }

    /** Возвращает текст ошибки или null, если загрузка запущена. */
    fun add(ctx: Context, url: String): String? {
        val u = url.trim()
        if (!(u.startsWith("https://") || u.startsWith("http://"))) {
            return "Ссылка должна начинаться с http:// или https://"
        }
        val task = DlTask(nextId++, u, uniqueFile(dir(ctx), guessName(u)))
        tasks.add(0, task)
        startService(ctx, task.id)
        return null
    }

    fun resume(ctx: Context, t: DlTask) {
        if (t.status == DlStatus.PAUSED || t.status == DlStatus.ERROR) {
            t.stopFlag = 0
            t.error = null
            t.status = DlStatus.RUNNING
            startService(ctx, t.id)
        }
    }

    fun pause(t: DlTask) {
        t.stopFlag = 1
    }

    fun cancel(t: DlTask) {
        t.stopFlag = 2
        if (t.status != DlStatus.RUNNING) {
            t.status = DlStatus.CANCELED
            t.file.delete()
        }
    }

    fun remove(t: DlTask) {
        tasks.remove(t)
    }

    private fun startService(ctx: Context, id: Int) {
        ContextCompat.startForegroundService(
            ctx, Intent(ctx, DownloadService::class.java).putExtra("id", id)
        )
    }

    private fun guessName(url: String): String {
        val path = runCatching { java.net.URI(url).path }.getOrNull().orEmpty().substringAfterLast('/')
        val decoded = runCatching { URLDecoder.decode(path, "UTF-8") }.getOrDefault(path)
        return decoded.replace(Regex("[^\\w.\\- ()]"), "_").ifBlank { "download.bin" }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        var n = 1
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        while (f.exists()) {
            f = File(dir, if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext")
            n++
        }
        return f
    }

    private fun fail(t: DlTask, msg: String) {
        t.speedBps = 0
        t.error = msg
        t.status = DlStatus.ERROR
    }

    /** Блокирующая загрузка с докачкой. Вызывается из потока сервиса. */
    fun run(t: DlTask) {
        try {
            val existing = if (t.file.exists()) t.file.length() else 0L
            val c = URL(t.url).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) VBLauncher")
            if (existing > 0) c.setRequestProperty("Range", "bytes=$existing-")

            val code = c.responseCode
            if (code == 416 && existing > 0) { // файл уже докачан
                t.downloaded = existing
                t.total = existing
                t.status = DlStatus.DONE
                return
            }
            if (c.contentType.orEmpty().lowercase().startsWith("text/html")) {
                fail(t, "Это веб-страница, а не прямая ссылка на файл")
                return
            }
            val append: Boolean
            when (code) {
                206 -> {
                    append = true
                    t.downloaded = existing
                    t.total = existing + c.contentLengthLong
                }
                200 -> {
                    append = false
                    t.downloaded = 0
                    t.total = c.contentLengthLong
                }
                else -> {
                    fail(t, "Сервер ответил кодом $code")
                    return
                }
            }

            RandomAccessFile(t.file, "rw").use { raf ->
                if (append) raf.seek(existing) else raf.setLength(0)
                c.inputStream.use { ins ->
                    val buf = ByteArray(64 * 1024)
                    var lastT = System.nanoTime()
                    var lastB = t.downloaded
                    while (t.stopFlag == 0) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        t.downloaded += n
                        val now = System.nanoTime()
                        if (now - lastT >= 500_000_000L) {
                            val inst = (t.downloaded - lastB) * 1_000_000_000L / (now - lastT)
                            t.speedBps = if (t.speedBps == 0L) inst else (t.speedBps * 0.6 + inst * 0.4).toLong()
                            lastT = now
                            lastB = t.downloaded
                        }
                    }
                }
            }
            t.speedBps = 0
            when {
                t.stopFlag == 2 -> { t.status = DlStatus.CANCELED; t.file.delete() }
                t.stopFlag == 1 -> t.status = DlStatus.PAUSED
                t.total > 0 && t.downloaded < t.total -> fail(t, "Соединение оборвалось, можно продолжить")
                else -> t.status = DlStatus.DONE
            }
        } catch (e: Exception) {
            fail(t, e.message ?: e.javaClass.simpleName)
        }
    }
}

// ───────── форматирование ─────────

fun fmtBytes(b: Long): String = when {
    b < 0 -> "?"
    b >= 1L shl 30 -> "%.2f ГБ".format(b / (1024.0 * 1024 * 1024))
    b >= 1L shl 20 -> "%.1f МБ".format(b / (1024.0 * 1024))
    else -> "%d КБ".format(b / 1024)
}

fun fmtMBps(bps: Long): String = "%.2f МБ/с".format(bps / (1024.0 * 1024))
fun fmtMbit(bps: Long): String = "%.1f Мбит/с".format(bps * 8 / 1_000_000.0)

fun fmtEta(sec: Long): String = when {
    sec < 0 -> "—"
    sec >= 3600 -> "%d ч %02d мин".format(sec / 3600, (sec % 3600) / 60)
    else -> "%d:%02d".format(sec / 60, sec % 60)
}

// ───────── фоновый сервис ─────────

class DownloadService : Service() {
    private val pool = Executors.newFixedThreadPool(2)
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel(CHANNEL, "Загрузки", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        handler.postDelayed(ticker, 1000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground(buildNotification("Загрузка…", 0, true))
        val id = intent?.getIntExtra("id", -1) ?: -1
        Downloads.tasks.firstOrNull { it.id == id }?.let { t -> pool.execute { Downloads.run(t) } }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        pool.shutdown()
        super.onDestroy()
    }

    private fun tick() {
        val active = Downloads.tasks.filter { it.status == DlStatus.RUNNING }
        if (active.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val speed = active.sumOf { it.speedBps }
        val total = active.sumOf { it.total.coerceAtLeast(0L) }
        val done = active.sumOf { it.downloaded }
        val pct = if (total > 0) (done * 100 / total).toInt() else 0
        val text = "${active.size} шт · ${fmtMBps(speed)} · ${fmtMbit(speed)} · $pct%"
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(text, pct, total <= 0))
    }

    private fun buildNotification(text: String, pct: Int, indeterminate: Boolean) =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("VB Launcher")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, pct, indeterminate)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

    private fun goForeground(n: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIF_ID = 1001
    }
}
