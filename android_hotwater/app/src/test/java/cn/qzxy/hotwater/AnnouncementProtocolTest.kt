package cn.qzxy.hotwater

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AnnouncementProtocolTest {
    private fun payload(revision: Int = 1, now: Long = 200) = JSONObject("""{"now":$now,"notices":[
      {"id":"abcdefabcdefabcdefabcdef","revision":$revision,"title":"演示","body":"<纯文本>",
       "starts":100,"pushes":210,"expires":300,"enabled":true,
       "options":[{"id":"1","label":"建议","input":true}]}]}""")
    @Test fun usesServerTimeAndPreservesPlainText() {
        val message = AnnouncementProtocol.parse(payload()).single()
        assertEquals("<纯文本>", message.contentHtml)
        assertTrue(message.systemNotice!!.options.single().input)
        assertEquals(210L, message.systemNotice!!.pushes)
        assertTrue(AnnouncementProtocol.parse(payload(now = 99)).isEmpty())
        assertTrue(AnnouncementProtocol.parse(payload(now = 300)).isEmpty())
    }
    @Test fun editsHaveDistinctReceiptsEvenWhenPublicationTimeIsUnchanged() {
        val old = AnnouncementProtocol.parse(payload()).single()
        val edited = AnnouncementProtocol.parse(payload(revision = 2)).single()
        assertNotEquals(old.revision, edited.revision)
        assertEquals(old.id, edited.id)
    }
}
