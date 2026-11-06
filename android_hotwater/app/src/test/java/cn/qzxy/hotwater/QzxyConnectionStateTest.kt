package cn.qzxy.hotwater
import org.junit.Assert.*
import org.junit.Test
class QzxyConnectionStateTest {
    @Test fun olderSuccessCannotOverrideNewFailureOrNetworkLoss() {
        QzxyConnectionState.disconnected()
        val old = QzxyConnectionState.beginRequest()
        val current = QzxyConnectionState.beginRequest()
        QzxyConnectionState.complete(current, false)
        QzxyConnectionState.complete(old, true)
        assertFalse(QzxyConnectionState.reachable.value)
        val pending = QzxyConnectionState.beginRequest()
        QzxyConnectionState.disconnected()
        QzxyConnectionState.complete(pending, true)
        assertFalse(QzxyConnectionState.reachable.value)
        QzxyConnectionState.complete(QzxyConnectionState.beginRequest(), true)
        assertTrue(QzxyConnectionState.reachable.value)
        QzxyConnectionState.disconnected()
    }
}
