package cn.qzxy.hotwater

import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ServerMessagesTest {
    private val config = HotWaterConfig(1, 2, 3, "test-session", "test-phone")
    private fun response(vararg ids: Int) = JSONObject().put("success", true).put("data",
        JSONArray(ids.map { id -> JSONObject().put("pushId", id).put("pushTitle", "Notice $id")
            .put("pushTime", "2026-10-01 12:00:00").put("pushContent", "Summary")
            .put("fullContent", "<p>Full notice $id</p>") }))
    private class Receipts : MessageReceiptStore {
        val values = mutableMapOf<String, MessageReceipts>()
        override fun load(account: String) = values[account] ?: MessageReceipts()
        override fun save(account: String, receipts: MessageReceipts) { values[account] = receipts }
        override fun clear(): Boolean { values.clear(); return true }
    }

    @Test fun parsesFullDetailsAndRejectsMalformedResponses() {
        val message = ServerMessageParser.parse(response(1)).single()
        assertEquals("<p>Full notice 1</p>", message.contentHtml)
        assertTrue(runCatching { ServerMessageParser.parse(JSONObject("""{"success":true}""")) }.isFailure)
        assertTrue(runCatching { ServerMessageParser.parse(JSONObject("""{"success":true,"data":[{}]}""")) }.isFailure)
    }

    @Test fun firstSyncIsBaselineThenNewMessagesAreAnnouncedOnceAndReadPersists() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var payload = response(1)
        val remote = object : ServerMessageRemoteDataSource {
            override suspend fun messageList(config: HotWaterConfig) = payload
        }
        val receipts = Receipts()
        val controller = ServerMessagesController(scope, remote, receipts) {}
        try {
            controller.load(config, true)
            assertNull(controller.state.value.announcement)
            assertTrue(controller.state.value.hasUnread)
            payload = response(2, 1)
            controller.load(config, true)
            assertEquals("2", controller.state.value.announcement?.id)
            controller.dismissAnnouncement()
            controller.load(config, true)
            assertNull(controller.state.value.announcement)
            controller.state.value.messages.forEach(controller::markRead)
            assertFalse(controller.state.value.hasUnread)
            controller.clear()
            controller.load(config.copy(loginCode = "rotated-session", snCode = "different-device"), true)
            assertFalse(controller.state.value.hasUnread)
            controller.load(config.copy(userId = 99), true)
            assertTrue(controller.state.value.hasUnread)
            assertNull(controller.state.value.announcement)
        } finally { scope.cancel() }
    }

    @Test fun cancelledOldAccountCannotPublishOrWriteReceipts() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val release = CompletableDeferred<Unit>()
        val receipts = Receipts()
        val remote = object : ServerMessageRemoteDataSource {
            override suspend fun messageList(config: HotWaterConfig): JSONObject {
                if (config.userId == 2) withContext(NonCancellable) { release.await() }
                return response(config.userId!!)
            }
        }
        val controller = ServerMessagesController(scope, remote, receipts) {}
        try {
            controller.load(config, true)
            controller.load(config.copy(userId = 99), true)
            release.complete(Unit)
            yield()
            assertEquals("99", controller.state.value.messages.single().id)
            assertFalse(receipts.values.containsKey(messageAccountKey(config)))
        } finally { release.complete(Unit); scope.cancel() }
    }

    @Test fun markAllReadPersistsAcrossRefreshButKeepsNewArrivalsUnread() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val release = CompletableDeferred<Unit>()
        var refreshing = false
        val receipts = Receipts()
        val remote = object : ServerMessageRemoteDataSource {
            override suspend fun messageList(config: HotWaterConfig): JSONObject {
                if (refreshing) release.await()
                return if (refreshing) response(3, 2, 1) else response(2, 1)
            }
        }
        val controller = ServerMessagesController(scope, remote, receipts) {}
        try {
            controller.load(config, true)
            refreshing = true
            controller.load(config, true)
            controller.markAllRead()
            assertFalse(controller.state.value.hasUnread)
            assertNull(controller.state.value.announcement)
            release.complete(Unit)
            yield()
            assertEquals(listOf("3"), controller.state.value.messages.filter {
                it.revision !in controller.state.value.read
            }.map { it.id })
            controller.markAllRead()
            assertNull(controller.state.value.announcement)
            controller.clear()
            controller.load(config)
            assertFalse(controller.state.value.hasUnread)
        } finally { release.complete(Unit); scope.cancel() }
    }
}
