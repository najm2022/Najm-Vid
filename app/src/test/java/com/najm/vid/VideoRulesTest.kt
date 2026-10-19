package com.najm.vid

import org.junit.Assert.*
import org.junit.Test

class VideoRulesTest {
    @Test fun extractsLinkFromSharedCaption() {
        assertEquals("https://vm.tiktok.com/abc/", VideoRules.extractUrl("شاهد المقطع (https://vm.tiktok.com/abc/)."))
        assertNull(VideoRules.extractUrl("مجرد نص"))
        assertNull(VideoRules.extractUrl("file:///sdcard/video.mp4"))
    }
    @Test fun matchesDomainsNotSubstrings() {
        assertEquals("X", VideoRules.platform("https://x.com/name/status/1"))
        assertEquals("General", VideoRules.platform("https://example.com/?next=x.com"))
        assertEquals("General", VideoRules.platform("https://notinstagram.com/video"))
        assertEquals("TikTok", VideoRules.platform("https://VM.TIKTOK.COM/abc"))
        assertEquals("YouTube", VideoRules.platform("https://youtu.be/abc"))
    }
    @Test fun capsEveryVideoFallbackAndPrefersSeparateHighQualityStreams() {
        val rule = VideoRules.format(720)
        assertTrue(rule.startsWith("bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]"))
        assertTrue(rule.split('/').all { it.contains("[height<=720]") })
        assertFalse(VideoRules.format(0).contains("height<="))
    }
}
