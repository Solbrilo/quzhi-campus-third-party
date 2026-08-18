package cn.qzxy.hotwater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackHistoryStoreTest {
    @Test
    fun codecRoundTripsConfirmedFeedback() {
        val entry = FeedbackHistoryEntry(
            submittedAtMillis = 1_750_000_000_000L,
            feedbackId = "fb_test_123",
            category = FeedbackCategory.Suggestion,
            email = "feedback@example.com",
            message = "希望增加余额刷新提示",
        )

        val restored = FeedbackHistoryCodec.deserialize(
            FeedbackHistoryCodec.serialize(listOf(entry)),
        )

        assertEquals(listOf(entry), restored)
    }

    @Test
    fun newestEntryReplacesDuplicateAndCapsHistory() {
        val existing = (0 until FeedbackHistoryCodec.MAX_ENTRIES).map { index ->
            FeedbackHistoryEntry(
                submittedAtMillis = index.toLong() + 1L,
                feedbackId = "fb_$index",
                category = FeedbackCategory.Bug,
                email = "user$index@example.com",
                message = "反馈 $index",
            )
        }
        val replacement = existing.last().copy(message = "已更新的反馈")

        val entries = FeedbackHistoryCodec.addEntry(existing, replacement)

        assertEquals(FeedbackHistoryCodec.MAX_ENTRIES, entries.size)
        assertEquals(replacement, entries.first())
        assertEquals(1, entries.count { it.feedbackId == replacement.feedbackId })
    }

    @Test
    fun emailIsMaskedForHistoryDisplay() {
        assertEquals("f***@example.com", FeedbackHistoryCodec.maskEmail("feedback@example.com"))
        assertEquals("***", FeedbackHistoryCodec.maskEmail("invalid"))
        assertTrue(FeedbackHistoryCodec.deserialize("not-json").isEmpty())
    }
}
