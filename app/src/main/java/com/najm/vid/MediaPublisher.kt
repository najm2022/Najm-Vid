package com.najm.vid

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File

object MediaPublisher {
    fun publish(context: Context, file: File, platform: String, audio: Boolean): Uri {
        require(file.isFile && file.length() > 0) { "لم ينتج التحميل ملفًا صالحًا" }
        val collection = if (audio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val directory = if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: if (audio) "audio/mpeg" else "video/mp4"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/NV_$platform")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: error("تعذر إنشاء الملف في التخزين")
        try {
            val output = resolver.openOutputStream(uri, "w") ?: error("تعذر فتح ملف الحفظ")
            output.use { target -> file.inputStream().use { it.copyTo(target) } }
            val updated = resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            check(updated == 1) { "تعذر إكمال حفظ الملف" }
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }
}
