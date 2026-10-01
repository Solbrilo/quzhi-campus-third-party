package cn.qzxy.hotwater

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest

internal fun messageAccountKey(config: HotWaterConfig): String =
    if (!config.hasAccount()) "" else MessageDigest.getInstance("SHA-256")
        .digest(listOf(config.projectId, config.userId, config.accountId, config.telephone)
            .joinToString(":").toByteArray())
        .joinToString("") { "%02x".format(it) }

data class ServerMessage(
    val id: String,
    val title: String,
    val publishedAt: String,
    val summaryHtml: String,
    val contentHtml: String,
) {
    val revision: String get() = "$id|$publishedAt"
}

interface ServerMessageRemoteDataSource {
    suspend fun messageList(config: HotWaterConfig): JSONObject
}

internal object ServerMessageParser {
    fun parse(payload: JSONObject): List<ServerMessage> {
        QzxyLoginProtocol.requireSuccessfulResponse(payload)
        if (!payload.has("data")) throw IOException("消息响应缺少数据")
        if (payload.isNull("data")) return emptyList()
        val rows = payload.optJSONArray("data") ?: throw IOException("无法识别服务器消息")
        return (0 until rows.length()).map { index ->
            val row = rows.optJSONObject(index) ?: throw IOException("消息条目格式错误")
            fun text(key: String) = if (row.isNull(key)) "" else row.optString(key).trim()
            val id = text("pushId")
            if (id.isBlank()) throw IOException("消息缺少编号")
            val summary = text("pushContent")
            ServerMessage(id, text("pushTitle").ifBlank { "校园通知" }, text("pushTime"),
                summary, text("fullContent").ifBlank { summary })
        }.distinctBy { it.revision }.sortedByDescending { it.publishedAt }
    }
}

internal data class MessageReceipts(
    val initialized: Boolean = false,
    val known: Set<String> = emptySet(),
    val read: Set<String> = emptySet(),
)

internal interface MessageReceiptStore {
    fun load(account: String): MessageReceipts
    fun save(account: String, receipts: MessageReceipts)
    fun clear(): Boolean
}

/** Local receipt keys only; credentials and message bodies are never persisted here. */
internal class MessagePreferences(context: Context) : MessageReceiptStore {
    private val prefs = context.getSharedPreferences("message_receipts", Context.MODE_PRIVATE)
    override fun load(account: String) = MessageReceipts(
        prefs.getBoolean("$account:initialized", false),
        prefs.getStringSet("$account:known", emptySet()).orEmpty().toSet(),
        prefs.getStringSet("$account:read", emptySet()).orEmpty().toSet(),
    )
    override fun save(account: String, receipts: MessageReceipts) {
        prefs.edit().putBoolean("$account:initialized", receipts.initialized)
            .putStringSet("$account:known", receipts.known)
            .putStringSet("$account:read", receipts.read).apply()
    }
    override fun clear() = prefs.edit().clear().commit()
}

data class ServerMessagesState(
    val messages: List<ServerMessage> = emptyList(),
    val read: Set<String> = emptySet(),
    val loading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
    val announcement: ServerMessage? = null,
) {
    val hasUnread: Boolean get() = messages.any { it.revision !in read }
}

internal class ServerMessagesController(
    private val scope: CoroutineScope,
    private val remote: ServerMessageRemoteDataSource,
    private val receipts: MessageReceiptStore,
    private val onCredentialsExpired: () -> Unit,
) {
    private val mutableState = MutableStateFlow(ServerMessagesState())
    val state = mutableState.asStateFlow()
    private var account = ""
    private var requestKey = ""
    private var generation = 0L
    private var job: Job? = null

    fun bind(config: HotWaterConfig) {
        val nextAccount = messageAccountKey(config)
        val nextRequest = if (nextAccount.isBlank()) "" else config.accountRequestKey()
        if (nextRequest == requestKey) return
        ++generation
        job?.cancel()
        job = null
        val changedAccount = account != nextAccount
        account = nextAccount
        requestKey = nextRequest
        mutableState.value = if (changedAccount) ServerMessagesState(loggedIn = account.isNotBlank())
            else mutableState.value.copy(loading = false, error = null)
    }

    fun load(config: HotWaterConfig, announce: Boolean = false) {
        bind(config)
        if (account.isBlank() || job?.isActive == true) return
        val expected = ++generation
        val owner = account
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        job = scope.launch {
            try {
                val messages = ServerMessageParser.parse(remote.messageList(config))
                if (expected != generation || owner != account) return@launch
                val saved = receipts.load(owner)
                // First sync establishes a baseline, without announcing historical notices.
                val newMessage = if (announce && saved.initialized) messages.firstOrNull {
                    it.revision !in saved.known && it.revision !in saved.read
                } else null
                val updated = saved.copy(initialized = true,
                    known = saved.known + messages.map { it.revision })
                receipts.save(owner, updated)
                mutableState.value = ServerMessagesState(messages, updated.read, loggedIn = true,
                    announcement = newMessage ?: mutableState.value.announcement)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (expected != generation) return@launch
                mutableState.value = mutableState.value.copy(loading = false, error = "消息同步失败，请稍后重试")
                if (e is CredentialExpiredException) onCredentialsExpired()
            }
        }
    }

    fun markRead(message: ServerMessage) {
        if (account.isBlank() || state.value.messages.none { it.revision == message.revision }) return
        val saved = receipts.load(account)
        val read = saved.read + message.revision
        receipts.save(account, saved.copy(read = read))
        mutableState.value = state.value.copy(read = read, announcement = null)
    }

    fun dismissAnnouncement() { mutableState.value = state.value.copy(announcement = null) }

    fun markAllRead() {
        if (account.isBlank() || !state.value.hasUnread) return
        val saved = receipts.load(account)
        val read = saved.read + state.value.messages.map { it.revision }
        receipts.save(account, saved.copy(read = read))
        mutableState.value = state.value.copy(read = read, announcement = null)
    }

    fun clear() {
        ++generation
        job?.cancel()
        job = null
        account = ""
        requestKey = ""
        mutableState.value = ServerMessagesState()
    }
}
