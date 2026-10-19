package com.najm.vid

import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            val url = extractUrl(sharedText)
            if (url != null) {
                processVideoUrl(url)
            } else {
                Toast.makeText(this, "لم يتم العثور على رابط صالح!", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            showWelcomeDialog()
        }
    }

    private fun extractUrl(text: String?): String? {
        if (text == null) return null
        val regex = "(https?://\\S+)".toRegex()
        return regex.find(text)?.value
    }

    private fun detectPlatform(url: String): String {
        val u = url.lowercase()
        return when {
            u.contains("tiktok.com") -> "TikTok"
            u.contains("instagram.com") -> "Instagram"
            u.contains("twitter.com") || u.contains("x.com") -> "X"
            u.contains("youtube.com") || u.contains("youtu.be") -> "YouTube"
            u.contains("snapchat.com") -> "Snapchat"
            u.contains("facebook.com") || u.contains("fb.watch") -> "Facebook"
            else -> "General"
        }
    }

    private fun processVideoUrl(url: String) {
        val platform = detectPlatform(url)

        if (platform == "TikTok" || platform == "Instagram" || platform == "Snapchat") {
            Toast.makeText(this, "⚡ Najm Vid: جاري التحميل التلقائي من $platform...", Toast.LENGTH_SHORT).show()
            startDownload(url, platform, "best")
            finish()
            return
        }

        Toast.makeText(this, "🔍 Najm Vid: جاري فحص الدقات المتاحة...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val info = YoutubeDL.getInstance().getInfo(url)
                val formats = info.formats
                    ?.filter { it.ext == "mp4" && it.height != null && it.height > 0 }
                    ?.distinctBy { it.height }
                    ?.sortedByDescending { it.height }

                withContext(Dispatchers.Main) {
                    if (formats == null || formats.size <= 1) {
                        Toast.makeText(this@MainActivity, "⚡ جاري التحميل التلقائي...", Toast.LENGTH_SHORT).show()
                        startDownload(url, platform, "best")
                        finish()
                    } else {
                        showResolutionPicker(url, platform, formats.map { it.height })
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    startDownload(url, platform, "best")
                    finish()
                }
            }
        }
    }

    private fun showResolutionPicker(url: String, platform: String, heights: List<Int>) {
        val options = heights.map { "🎬 دقة ${it}p (MP4)" }.toMutableList()
        options.add("🎵 صوت فقط (MP3)")

        val adapter = ArrayAdapter(this, android.R.layout.select_dialog_item, options)

        AlertDialog.Builder(this)
            .setTitle("Najm Vid - اختر الدقة ($platform)")
            .setAdapter(adapter) { _, which ->
                if (which == options.lastIndex) {
                    startDownload(url, platform, "audio")
                } else {
                    val selectedHeight = heights[which]
                    startDownload(url, platform, "bestvideo[height<=$selectedHeight][ext=mp4]+bestaudio[ext=m4a]/best[height<=$selectedHeight]")
                }
                finish()
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun startDownload(url: String, platform: String, formatRule: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val folderName = "NV_$platform"
                val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                val targetDir = File(moviesDir, folderName)
                if (!targetDir.exists()) targetDir.mkdirs()

                val request = YoutubeDLRequest(url)
                if (formatRule == "audio") {
                    request.addOption("-x")
                    request.addOption("--audio-format", "mp3")
                    request.addOption("-o", "${targetDir.absolutePath}/NV_%(title).40s_%(id)s.%(ext)s")
                } else {
                    request.addOption("-f", formatRule)
                    request.addOption("--merge-output-format", "mp4")
                    request.addOption("-o", "${targetDir.absolutePath}/NV_%(title).40s_%(id)s.%(ext)s")
                }

                YoutubeDL.getInstance().execute(request) { _, _, _ -> }

                MediaScannerConnection.scanFile(
                    applicationContext,
                    arrayOf(targetDir.absolutePath),
                    null,
                    null
                )

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        "✅ تم الحفظ في فولدر: $folderName",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        "❌ فشل التحميل: تأكد من أن الحساب عام",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showWelcomeDialog() {
        AlertDialog.Builder(this)
            .setTitle("Najm Vid ⚡")
            .setMessage(
                "التطبيق جاهز للعمل!\n\n" +
                "طريقة الاستخدام:\n" +
                "1. افتح أي فيديو في (TikTok, Instagram, X, YouTube, Snapchat).\n" +
                "2. اضغط (مشاركة / Share) واختر Najm Vid.\n" +
                "3. لتثبيته كأول اقتراح دائماً: اضغط مطولاً على أيقونة Najm Vid في قائمة المشاركة واختر (تثبيت / Pin).\n\n" +
                "📁 مسار الحفظ التلقائي:\n" +
                "Movies / NV_[اسم المنصة]"
            )
            .setPositiveButton("تم") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
