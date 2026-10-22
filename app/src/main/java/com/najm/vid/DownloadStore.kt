package com.najm.vid

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

 data class DownloadItem(
    val id: String = UUID.randomUUID().toString(), val url: String, val title: String,
    val platform: String = VideoRules.platform(url), val status: String = "queued",
    val progress: Int = 0, val height: Int = 0, val audio: Boolean = false,
    val thumbnail: String = "", val uri: String = "", val mime: String = "",
    val error: String = "", val bytes: Long = 0, val created: Long = System.currentTimeMillis()
) {
    val active get() = status in setOf("queued", "loading", "saving")
    val quality get() = if (audio) "MP3" else if (height > 0) "${height}p" else "أفضل جودة"
}

object DownloadStore {
    private var prefs: android.content.SharedPreferences? = null
    private val mutable = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items = mutable.asStateFlow()

    @Synchronized fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("downloads_v2", Context.MODE_PRIVATE)
        val data = runCatching { JSONArray(prefs!!.getString("items", "[]")) }.getOrDefault(JSONArray())
        val loaded = (0 until data.length()).mapNotNull { index ->
            runCatching {
                val o = data.getJSONObject(index)
                DownloadItem(id=o.getString("id"), url=o.getString("url"), title=o.getString("title"),
                    platform=o.optString("platform", "General"), status=o.optString("status"), progress=o.optInt("progress"),
                    height=o.optInt("height"), audio=o.optBoolean("audio"), thumbnail=o.optString("thumbnail"),
                    uri=o.optString("uri"), mime=o.optString("mime"), error=o.optString("error"),
                    bytes=o.optLong("bytes"), created=o.optLong("created"))
            }.getOrNull()
        }.map { if (it.active) it.copy(status="interrupted", error="توقف التحميل قبل اكتماله") else it }
        mutable.value = loaded
    }

    @Synchronized fun add(item: DownloadItem) { mutable.value = (listOf(item) + mutable.value.filter { it.id != item.id }).take(200); persist() }
    @Synchronized fun update(id: String, change: (DownloadItem) -> DownloadItem) {
        mutable.value = mutable.value.map { if (it.id == id) change(it) else it }; persist()
    }
    fun get(id: String) = mutable.value.find { it.id == id }
    @Synchronized fun clearFinished() { mutable.value = mutable.value.filter { it.active }; persist() }
    @Synchronized fun remove(id: String) { mutable.value = mutable.value.filter { it.id != id }; persist() }
    private fun persist() {
        val a = JSONArray()
        mutable.value.forEach { i -> a.put(JSONObject().apply {
            put("id",i.id);put("url",i.url);put("title",i.title);put("platform",i.platform);put("status",i.status)
            put("progress",i.progress);put("height",i.height);put("audio",i.audio);put("thumbnail",i.thumbnail)
            put("uri",i.uri);put("mime",i.mime);put("error",i.error);put("bytes",i.bytes);put("created",i.created)
        }) }
        prefs?.edit()?.putString("items", a.toString())?.apply()
    }
}

object AppPreferences {
    private fun prefs(c: Context) = c.getSharedPreferences("preferences_v2", Context.MODE_PRIVATE)
    fun quality(c: Context) = prefs(c).getString("quality", "ask") ?: "ask"
    fun setQuality(c: Context, value: String) { prefs(c).edit().putString("quality", value).apply() }
    fun label(key: String) = when(key) { "best" -> "أفضل جودة"; "1080" -> "حتى 1080p"; "720" -> "حتى 720p"; "audio" -> "صوت MP3"; else -> "اسألني دائمًا" }
}

object DownloadActions {
    fun enqueue(c: Context, url: String, height: Int = 0, audio: Boolean = false, title: String = "", thumbnail: String = "") {
        ContextCompat.startForegroundService(c, Intent(c, DownloadService::class.java)
            .putExtra("url",url).putExtra("height",height).putExtra("audio",audio)
            .putExtra("title",title).putExtra("thumbnail",thumbnail))
    }
    fun retry(c: Context, item: DownloadItem) = enqueue(c,item.url,item.height,item.audio,item.title,item.thumbnail)
    fun cancel(c: Context, id: String) { c.startService(Intent(c,DownloadService::class.java).setAction("cancel").putExtra("id",id)) }
    fun open(c: Context, item: DownloadItem) { c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(item.uri),item.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
    fun share(c: Context, item: DownloadItem) { c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(item.mime).putExtra(Intent.EXTRA_STREAM,Uri.parse(item.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"مشاركة المقطع")) }
}
