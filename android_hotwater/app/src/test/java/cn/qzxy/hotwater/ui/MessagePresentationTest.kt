package cn.qzxy.hotwater.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MessagePresentationTest {
    @Test fun removesOnlyLeadingRepeatedTitleAndTimestamp() {
        val body = "停水通知\n\n发布时间：2026-11-06 12:30\n\n正文\n停水通知\n2026-11-06 12:30:00"
        assertEquals("正文\n停水通知\n2026-11-06 12:30:00",
            messageBodyWithoutRepeatedHeader(body, "停水通知", "2026-11-06 12:30:00"))
    }

    @Test fun preservesBodyWithoutEmbeddedMetadata() {
        val body = "请同学们提前储水。\n\n停水通知包含以下安排。"
        assertEquals(body, messageBodyWithoutRepeatedHeader(body, "停水通知", "2026-11-06 12:30:00"))
    }

    @Test fun handlesWhitespaceCombinedHeaderAndDateFormats() {
        assertEquals("正文", messageBodyWithoutRepeatedHeader(
            "\uFEFF停水 通知\u00a0发布时间：2026/11/06 12:30:00\n正文", "停水通知", "2026-11-06 12:30:00"))
        assertEquals("正文", messageBodyWithoutRepeatedHeader(
            "停水通知\n2026-11-06 12:00\n正文", "停水通知", "2026-11-06 12:00:00"))
    }
}
