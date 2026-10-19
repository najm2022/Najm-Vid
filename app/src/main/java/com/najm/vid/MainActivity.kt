package com.najm.vid

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private var dialog: Dialog? = null
    private var analysis: Job? = null
    private var pendingUrl: String? = null
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingUrl?.let { url -> pendingUrl = null; analyze(url) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        analysis?.cancel()
        dialog?.dismiss()
        handleIntent(intent)
    }

    private fun handleIntent(source: Intent) {
        if (source.action == Intent.ACTION_SEND) {
            val url = VideoRules.extractUrl(source.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString())
            if (url == null) showError("لم يتم العثور على رابط صالح في النص المُشارك")
            else prepare(url)
        } else showWelcome()
    }

    private fun prepare(url: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !getPreferences(MODE_PRIVATE).getBoolean("notificationAsked", false)) {
            pendingUrl = url
            getPreferences(MODE_PRIVATE).edit().putBoolean("notificationAsked", true).apply()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else analyze(url)
    }

    private fun analyze(url: String) {
        dialog?.dismiss()
        dialog = AlertDialog.Builder(this).setTitle("Najm Vid")
            .setMessage("جارٍ تهيئة المحرك وفحص الدقات المتاحة…")
            .setNegativeButton("إلغاء") { _, _ -> analysis?.cancel(); finish() }
            .setOnCancelListener { analysis?.cancel(); finish() }.show()
        analysis = lifecycleScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    DownloadEngine.prepare(applicationContext)
                    YoutubeDL.getInstance().getInfo(DownloadEngine.request(url))
                }
                val formats = info.formats.orEmpty().filter { it.height > 0 && it.vcodec != "none" }
                val heights = formats.map { it.height }.distinct().sortedDescending()
                dialog?.dismiss()
                if (heights.size <= 1) startDownload(url, 0, false)
                else {
                    val estimates = heights.associateWith { height ->
                        val video = formats.filter { it.height == height }.maxOfOrNull { maxOf(it.fileSize, it.fileSizeApproximate) } ?: 0L
                        val audio = info.formats.orEmpty().filter { it.vcodec == "none" }.maxOfOrNull { maxOf(it.fileSize, it.fileSizeApproximate) } ?: 0L
                        val combined = formats.any { it.height == height && it.acodec != null && it.acodec != "none" }
                        if (video <= 0) null else if (combined) video else (video + audio)
                    }
                    showPicker(url, info.title ?: "اختر الدقة", heights, estimates)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dialog?.dismiss()
                // Preserve the real failure; never report every error as a private video.
                showError(e.message ?: "تعذر فحص الرابط")
            }
        }
    }

    private fun showPicker(url: String, title: String, heights: List<Int>, estimates: Map<Int, Long?>) {
        val sheet = BottomSheetDialog(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        layout.addView(TextView(this).apply { text = title; textSize = 18f })
        heights.forEach { height ->
            layout.addView(Button(this).apply {
                val size = estimates[height]?.let { " · نحو ${String.format(Locale.US, "%.1f", it / 1048576.0)} MB" }.orEmpty()
                text = "${height}p$size"
                setOnClickListener { sheet.dismiss(); startDownload(url, height, false) }
            })
        }
        layout.addView(Button(this).apply {
            text = "صوت فقط MP3"
            setOnClickListener { sheet.dismiss(); startDownload(url, 0, true) }
        })
        val scroll = android.widget.ScrollView(this).apply { addView(layout) }
        sheet.setContentView(scroll)
        sheet.setOnCancelListener { finish() }
        dialog = sheet
        sheet.show()
    }

    private fun startDownload(url: String, height: Int, audio: Boolean) {
        try {
            ContextCompat.startForegroundService(this, Intent(this, DownloadService::class.java)
                .putExtra("url", url).putExtra("height", height).putExtra("audio", audio))
            Toast.makeText(this, "بدأ التحميل في الخلفية", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            showError(e.message ?: "تعذر بدء خدمة التحميل")
        }
    }

    private fun showWelcome() {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, 0, pad, 0) }
        layout.addView(TextView(this).apply {
            text = "شارك رابط الفيديو إلى Najm Vid أو ألصقه هنا.\nالفيديو: Movies/NV_[المنصة]\nالصوت: Music/NV_[المنصة]\n\nترتيب التطبيق في قائمة المشاركة يحدده النظام؛ يمكن تثبيته إذا كان خيار التثبيت متاحًا."
        })
        val input = EditText(this).apply {
            hint = "https://…"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            maxLines = 3
        }
        layout.addView(input, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val welcome = AlertDialog.Builder(this).setTitle("Najm Vid")
            .setView(layout).setPositiveButton("تحميل", null)
            .setNeutralButton("آخر نتيجة") { _, _ ->
                showError(getSharedPreferences("status", MODE_PRIVATE).getString("last", "لا توجد تحميلات سابقة").orEmpty())
            }
            .setNegativeButton("إغلاق") { _, _ -> finish() }
            .setOnCancelListener { finish() }.create()
        dialog = welcome
        welcome.setOnShowListener {
            welcome.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val url = VideoRules.extractUrl(input.text.toString())
                if (url == null) input.error = "ألصق رابطًا يبدأ بـ https://"
                else { welcome.dismiss(); prepare(url) }
            }
        }
        welcome.show()
    }

    private fun showError(message: String) {
        dialog?.dismiss()
        dialog = AlertDialog.Builder(this).setTitle("Najm Vid").setMessage(message.take(6000))
            .setPositiveButton("إغلاق") { _, _ -> finish() }
            .setOnCancelListener { finish() }.show()
    }

    override fun onDestroy() {
        dialog?.dismiss()
        super.onDestroy()
    }
}
