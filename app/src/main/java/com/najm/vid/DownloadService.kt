package com.najm.vid

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.PowerManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Mutex()
    private var pending = 0
    @Volatile private var processId: String? = null
    private lateinit var notifications: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "تحميلات Najm Vid", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("url")?.let(VideoRules::extractUrl)
        if (url == null) {
            if (pending == 0) stopSelf(startId)
            return START_NOT_STICKY
        }
        val height = intent.getIntExtra("height", 0)
        val audio = intent.getBooleanExtra("audio", false)
        pending++
        ServiceCompat.startForeground(this, 1, notification("جارٍ تجهيز التحميل", -1, true), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        scope.launch {
            queue.withLock {
                download(url, height, audio)
            }
            withContext(Dispatchers.Main) {
                pending--
                if (pending == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun download(url: String, height: Int, audio: Boolean) {
        val id = UUID.randomUUID().toString()
        val platform = VideoRules.platform(url)
        val workDir = File(cacheDir, "downloads/$id")
        processId = id
        val wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NajmVid:download")
        try {
            wakeLock.acquire(3 * 60 * 60 * 1000L)
            check(workDir.mkdirs()) { "تعذر إنشاء مجلد التحميل المؤقت" }
            progress("جارٍ تهيئة محرك التحميل", -1)
            DownloadEngine.prepare(applicationContext)
            val request = DownloadEngine.request(url).apply {
                addOption("--no-mtime")
                addOption("--windows-filenames")
                addOption("-o", "${workDir.absolutePath}/NV_%(title).60B_%(id).40B.%(ext)s")
                if (audio) {
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                } else {
                    addOption("-f", VideoRules.format(height))
                    addOption("--merge-output-format", "mp4")
                    addOption("--remux-video", "mp4")
                }
            }
            var lastUpdate = 0L
            YoutubeDL.getInstance().execute(request, id, false) { value, _, _ ->
                val now = System.currentTimeMillis()
                if (now - lastUpdate >= 800) {
                    lastUpdate = now
                    progress("جارٍ التحميل من $platform", value.toInt().coerceIn(0, 100))
                }
            }
            val extensions = if (audio) setOf("mp3") else setOf("mp4", "mkv", "webm", "mov", "m4v")
            val file = workDir.listFiles()?.filter { it.isFile && it.extension.lowercase() in extensions }
                ?.maxByOrNull { it.length() } ?: error("لم ينتج المصدر ملف فيديو أو صوت صالحًا")
            progress("جارٍ الحفظ في NV_$platform", -1)
            val uri = MediaPublisher.publish(applicationContext, file, platform, audio)
            val directory = if (audio) "Music" else "Movies"
            saveStatus("تم الحفظ: $directory/NV_$platform/${file.name}")
            val open = Intent(Intent.ACTION_VIEW).setDataAndType(uri, if (audio) "audio/mpeg" else "video/mp4")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val contentIntent = PendingIntent.getActivity(this, id.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            complete(id, "تم الحفظ في NV_$platform", contentIntent)
        } catch (e: Exception) {
            Log.e("NajmVid", "Download failed", e)
            val message = "فشل التحميل: ${e.message ?: e.javaClass.simpleName}"
            saveStatus(message)
            complete(id, "فشل التحميل؛ افتح التطبيق لعرض السبب", appIntent())
        } finally {
            processId = null
            if (wakeLock.isHeld) wakeLock.release()
            workDir.deleteRecursively()
        }
    }

    private fun saveStatus(message: String) {
        getSharedPreferences("status", MODE_PRIVATE).edit().putString("last", message).apply()
    }

    private fun appIntent(): PendingIntent = PendingIntent.getActivity(this, 0,
        Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun notification(message: String, percent: Int, ongoing: Boolean): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Najm Vid")
            .setContentText(message)
            .setContentIntent(appIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
        if (ongoing) builder.setProgress(100, percent.coerceAtLeast(0), percent < 0)
        return builder.build()
    }

    @SuppressLint("MissingPermission")
    private fun progress(message: String, percent: Int) {
        notifications.notify(1, notification(message, percent, true))
    }

    @SuppressLint("MissingPermission")
    private fun complete(id: String, message: String, contentIntent: PendingIntent) {
        if (notifications.areNotificationsEnabled()) {
            notifications.notify(id.hashCode(), NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_download).setContentTitle("Najm Vid")
                .setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(contentIntent).setAutoCancel(true).build())
        }
    }

    override fun onDestroy() {
        processId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
        scope.cancel()
        super.onDestroy()
    }

    companion object { private const val CHANNEL = "downloads" }
}
