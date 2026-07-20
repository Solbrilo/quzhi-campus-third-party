package cn.qzxy.hotwater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrCodeParserTest {
    @Test
    fun `extracts the last field from a school water qr payload`() {
        assertEquals(
            "C47F0EDA85D8",
            QrCodeParser.parseSnCode("KLCXKJ-Water,M,C47F0EDA85D8"),
        )
    }

    @Test
    fun `trims payload fields and normalizes the device code`() {
        assertEquals(
            "C47F0EDA85D8",
            QrCodeParser.parseSnCode("  klcxkj-water, M, c4:7f0eda85d8\n"),
        )
    }

    @Test
    fun `normalizes a manually entered device code`() {
        assertEquals("C47F0EDA85D8", QrCodeParser.normalizeSnCode(" c4:7f:0e:da:85:d8 "))
        assertNull(QrCodeParser.normalizeSnCode("not-a-device"))
    }

    @Test
    fun `uses the last nonempty protocol position when more fields are present`() {
        assertEquals(
            "C47F0EDA85D8",
            QrCodeParser.parseSnCode("KLCXKJ-Water,M,ROOM-3,C47F0EDA85D8"),
        )
    }

    @Test
    fun `rejects unrelated or incomplete qr content`() {
        assertNull(QrCodeParser.parseSnCode("https://example.invalid/device"))
        assertNull(QrCodeParser.parseSnCode("OTHER,M,C47F0EDA85D8"))
        assertNull(QrCodeParser.parseSnCode("KLCXKJ-Water,C47F0EDA85D8"))
        assertNull(QrCodeParser.parseSnCode("KLCXKJ-Water,,C47F0EDA85D8"))
        assertNull(QrCodeParser.parseSnCode("KLCXKJ-Water,M,"))
        assertNull(QrCodeParser.parseSnCode("KLCXKJ-Water,M,not-a-device"))
        assertNull(QrCodeParser.parseSnCode("KLCXKJ-Water,M,C47F0EDA85"))
        assertNull(QrCodeParser.parseSnCode(""))
    }
}
