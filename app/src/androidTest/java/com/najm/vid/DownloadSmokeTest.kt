package com.najm.vid

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class DownloadSmokeTest {
    @Test fun sharedVideoAndMp3AreSavedAndPlayable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val bytes = instrumentation.context.assets.open("najm_smoke.mp4").use { it.readBytes() }
        val marker = "najm_smoke_${System.currentTimeMillis()}"
        val server = ServerSocket(0)
        thread(isDaemon = true, name = "fixture-server") {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    socket.use {
                        it.soTimeout = 10_000
                        val reader = it.getInputStream().bufferedReader()
                        val first = reader.readLine() ?: return@use
                        val headers = mutableListOf<String>()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            headers += line
                        }
                        val range = headers.firstOrNull { h -> h.startsWith("Range:", true) }
                        val start = range?.substringAfter("bytes=")?.substringBefore('-')?.trim()?.toIntOrNull() ?: 0
                        val offset = start.coerceIn(0, bytes.lastIndex)
                        val output = it.getOutputStream()
                        val status = if (range != null) "206 Partial Content" else "200 OK"
                        val contentRange = if (range != null) "Content-Range: bytes $offset-${bytes.lastIndex}/${bytes.size}\r\n" else ""
                        output.write(("HTTP/1.1 $status\r\nContent-Type: video/mp4\r\nContent-Length: ${bytes.size - offset}\r\nAccept-Ranges: bytes\r\n${contentRange}Connection: close\r\n\r\n").toByteArray())
                        if (!first.startsWith("HEAD ")) output.write(bytes, offset, bytes.size - offset)
                        output.flush()
                    }
                }
            }
        }
        val url = "http://127.0.0.1:${server.localPort}/$marker.mp4"
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS").use { fd -> android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() } }
            }
            // Keep this integration test deterministic and offline after dependency installation.
            context.getSharedPreferences("engine", Context.MODE_PRIVATE).edit().putLong("updated", System.currentTimeMillis()).commit()
            resetStatus(context)
            val share = Intent(context, ShareActivity::class.java).setAction(Intent.ACTION_SEND)
                .setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Test video: $url")
            ActivityScenario.launch<ShareActivity>(share).use {
                awaitSave(context, "Movies/NV_General")
                verifyAndDelete(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, marker, "Movies/NV_General/")
            }
            resetStatus(context)
            // Starting from a visible activity exercises the Android foreground-service rule.
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
                it.onActivity { activity ->
                    activity.startForegroundService(Intent(activity, DownloadService::class.java)
                        .putExtra("url", url).putExtra("audio", true))
                }
                awaitSave(context, "Music/NV_General")
                verifyAndDelete(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, marker, "Music/NV_General/")
            }
        } finally {
            server.close()
        }
    }

    private fun resetStatus(context: Context) {
        context.getSharedPreferences("status", Context.MODE_PRIVATE).edit().remove("last").commit()
    }

    private fun awaitSave(context: Context, expected: String) {
        val end = SystemClock.elapsedRealtime() + 90_000
        while (SystemClock.elapsedRealtime() < end) {
            val status = context.getSharedPreferences("status", Context.MODE_PRIVATE).getString("last", "").orEmpty()
            if (status.startsWith("فشل")) fail(status)
            if (status.contains("تم الحفظ: $expected")) return
            SystemClock.sleep(250)
        }
        fail("Timed out waiting for $expected")
    }

    private fun verifyAndDelete(context: Context, collection: Uri, marker: String, directory: String) {
        val columns = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.IS_PENDING, MediaStore.MediaColumns.RELATIVE_PATH)
        val query = context.contentResolver.query(collection, columns, "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?", arrayOf("%$marker%"), null)
        assertNotNull(query)
        query!!.use { cursor ->
            assertTrue("Output missing from MediaStore", cursor.moveToFirst())
            assertEquals(0, cursor.getInt(1))
            assertEquals(directory, cursor.getString(2))
            val uri = ContentUris.withAppendedId(collection, cursor.getLong(0))
            try {
                assertTrue(context.contentResolver.openInputStream(uri)!!.use { it.readBytes().size > 0 })
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    assertTrue("Saved media is not playable", (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) > 0)
                } finally { retriever.release() }
            } finally { context.contentResolver.delete(uri, null, null) }
        }
    }
}

