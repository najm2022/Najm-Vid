package com.najm.vid

import java.net.URI
import java.util.Locale

object VideoRules {
    fun extractUrl(text: String?): String? {
        val candidates = Regex("https?://[^\\s<>\\\"]+", RegexOption.IGNORE_CASE)
            .findAll(text.orEmpty())
        for (match in candidates) {
            val candidate = match.value.trimEnd('.', ',', ';', '!', '،', '؛', ')', ']', '}')
            val uri = runCatching { URI(candidate) }.getOrNull() ?: continue
            if (!uri.host.isNullOrBlank() && uri.userInfo == null) return candidate
        }
        return null
    }

    fun platform(url: String): String {
        val host = runCatching { URI(url).host?.lowercase(Locale.ROOT) }.getOrNull() ?: return "General"
        fun matches(domain: String) = host == domain || host.endsWith(".$domain")
        return when {
            matches("tiktok.com") -> "TikTok"
            matches("instagram.com") -> "Instagram"
            matches("x.com") || matches("twitter.com") || matches("t.co") -> "X"
            matches("youtube.com") || matches("youtu.be") -> "YouTube"
            matches("snapchat.com") -> "Snapchat"
            matches("facebook.com") || matches("fb.watch") || matches("fb.com") -> "Facebook"
            else -> "General"
        }
    }

    fun format(height: Int): String {
        val limit = if (height > 0) "[height<=$height]" else ""
        return "bestvideo$limit[ext=mp4]+bestaudio[ext=m4a]/best$limit[ext=mp4]/bestvideo$limit+bestaudio/best$limit"
    }
}
