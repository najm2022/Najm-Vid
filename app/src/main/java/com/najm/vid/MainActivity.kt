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

    private var progressDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            YoutubeDL.getInstance().init(applicationContext)
            FFmpeg.getInstance().init(applicationContext)
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

    private fun showLoading(message: String) {
        progressDialog?.dismiss()
        progressDialog = AlertDialog.Builder(this)
            .setTitle("Najm Vid ⚡")
            .setMessage(message)
            .setCancelable(false)
            .create()
        progressDialog?.show()
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
            startDownload(url, platform, "best")
            return
        }

        showLoading("🔍 جاري فحص الدقات المتاحة من $platform...")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val info = YoutubeDL.getInstance().getInfo(url)
                val formats = info.formats
                    ?.filter { it.ext == "mp4" && it.height != null && it.height > 0 }
                    ?.distinctBy { it.height }
                    ?.sortedByDescending { it.height }

                withContext(Dispatchers.Main) {
                    progressDialog?.dismiss()
                    if (formats == null || formats.size <= 1) {
                        startDownload(url, platform, "best")
                    } else {
                        showResolutionPicker(url, platform, formats.map { it.height })
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog?.dismiss()
                    startDownload(url, platform, "best")
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
                    startDownload(url, platform, "bestvideo[height<=$selectedHeight][ext=mp4]+bestaudio[ext=m4a]/best[height<=$selectedHeight]/best")
                }
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun startDownload(url: String, platform: String, formatRule: String) {
        showLoading("⏳ جاري تحميل الفيديو من $platform...\nسيتم الحفظ في مجلد NV_$platform")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // تحديث محرك التحميل تلقائياً لضمان عمل كافة المنصات
                try { YoutubeDL.getInstance().updateYoutubeDL(applicationContext) } catch (_: Exception) {}

                val folderName = "NV_$platform"
                val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = File(baseDir, folderName)
                if (!targetDir.exists()) targetDir.mkdirs()

                val request = YoutubeDLRequest(url)
                if (formatRule == "audio") {
                    request.addOption("-x")
                    request.addOption("--audio-format", "mp3")
                    request.addOption("-o", "${targetDir.absolutePath}/NV_%(id)s.%(ext)s")
                } else {
                    request.addOption("-f", formatRule)
                    request.addOption("--merge-output-format", "mp4")
                    request.addOption("-o", "${targetDir.absolutePath}/NV_%(id)s.%(ext)s")
                }

                YoutubeDL.getInstance().execute(request) { _, _, _ -> }

                // فحص الملفات الجديدة لإظهارها فوراً في الاستوديو
                val files = targetDir.listFiles()?.map { it.absolutePath }?.toTypedArray() ?: arrayOf(targetDir.absolutePath)
                MediaScannerConnection.scanFile(applicationContext, files, null, null)

                withContext(Dispatchers.Main) {
                    progressDialog?.dismiss()
                    Toast.makeText(
                        applicationContext,
                        "✅ تم الحفظ بنجاح في: Downloads/$folderName",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog?.dismiss()
                    Toast.makeText(
                        applicationContext,
                        "❌ تعذر التحميل: ${e.localizedMessage ?: "تأكد من الرابط"}",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
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
                "Downloads / NV_[اسم المنصة]"
            )
            .setPositiveButton("تم") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
