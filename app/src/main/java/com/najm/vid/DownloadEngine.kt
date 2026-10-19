package com.najm.vid

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest

object DownloadEngine {
    private var ready = false
    private var attemptedUpdate = false

    @Synchronized
    fun prepare(context: Context) {
        if (!ready) {
            YoutubeDL.getInstance().init(context.applicationContext)
            FFmpeg.getInstance().init(context.applicationContext)
            ready = true
        }
        val prefs = context.getSharedPreferences("engine", Context.MODE_PRIVATE)
        if (!attemptedUpdate && System.currentTimeMillis() - prefs.getLong("updated", 0) > 86_400_000) {
            attemptedUpdate = true
            try {
                YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext, YoutubeDL.UpdateChannel.STABLE)
                prefs.edit().putLong("updated", System.currentTimeMillis()).apply()
            } catch (e: Exception) {
                // The bundled engine remains usable when the update endpoint is unavailable.
                Log.w("NajmVid", "Engine update unavailable", e)
            }
        }
    }

    fun request(url: String): YoutubeDLRequest = YoutubeDLRequest(url).apply {
        addOption("--no-playlist")
        addOption("--socket-timeout", 20)
        addOption("--retries", 3)
        addOption("--fragment-retries", 3)
    }
}
