package cn.qzxy.hotwater

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class AnnouncementOption(val id: String, val label: String, val input: Boolean)
data class SystemNotice(val id: String, val revision: Int, val starts: Long, val pushes: Long,
    val expires: Long, val options: List<AnnouncementOption>)
data class AnnouncementResponseState(val revision: String? = null, val sending: Boolean = false,
    val error: String? = null, val saved: Set<String> = emptySet())

internal object AnnouncementProtocol {
    fun parse(payload: JSONObject): List<ServerMessage> {
        val rows = payload.getJSONArray("notices")
        val now = payload.getLong("now")
        require(rows.length() <= 100)
        return (0 until rows.length()).mapNotNull { i ->
            val row = rows.getJSONObject(i)
            val id = row.getString("id"); val revision = row.getInt("revision")
            require(id.matches(Regex("[a-f0-9]{24}")) && revision > 0)
            val starts = row.getLong("starts"); val expires = row.getLong("expires"); val pushes = row.getLong("pushes")
            if (starts > now || expires <= now || !row.optBoolean("enabled", true)) return@mapNotNull null
            val options = row.getJSONArray("options")
            require(options.length() <= 8)
            val choices = (0 until options.length()).map { index -> options.getJSONObject(index).let {
                AnnouncementOption(it.getString("id"), it.getString("label").take(80), it.getBoolean("input"))
            } }
            val body = row.getString("body").take(12000)
            ServerMessage("system:$id", row.getString("title").take(120),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai")).format(Instant.ofEpochSecond(starts)),
                body.take(150), body, SystemNotice(id, revision, starts, pushes, expires, choices))
        }.sortedByDescending { it.systemNotice!!.starts }
    }
}

internal class AnnouncementClient {
    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false).retryOnConnectionFailure(false).build()
    suspend fun load(): JSONObject = request(Request.Builder().url(URL).get().build())
    suspend fun respond(notice: SystemNotice, token: String, option: String, text: String): JSONObject = request(
        Request.Builder().url("$URL/response").post(JSONObject().put("id", notice.id)
            .put("revision", notice.revision).put("token", token).put("option", option).put("text", text)
            .toString().toRequestBody("application/json".toMediaType())).build())
    private suspend fun request(request: Request): JSONObject = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            // Bound the response before allocating an untrusted body.
            val bytes = response.body?.byteStream()?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 2_000_000)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: error("公告服务暂不可用")
            require(bytes.size <= 2_000_000)
            if (!response.isSuccessful) throw java.io.IOException(if (response.code == 400) "公告已更新或结束，请刷新消息后重试" else "连接失败，请稍后重试")
            JSONObject(bytes.toString(Charsets.UTF_8))
        }
    }
    companion object { private const val URL = "https://nebularive.top/api/hotwater-notices" }
}

/** Separate per-notice pseudonyms cannot be joined to activity tokens or another notice. */
internal object AnnouncementIdentity {
    private val lock = Any()
    fun token(context: Context, id: String): String = synchronized(lock) {
        val file = AtomicFile(File(context.noBackupFilesDir, "announcement_seed"))
        val seed = if (file.baseFile.exists()) file.readFully() else ByteArray(32).also {
            SecureRandom().nextBytes(it); val out = file.startWrite()
            try { out.write(it); file.finishWrite(out) } catch (e: Exception) { file.failWrite(out); throw e }
        }
        require(seed.size == 32)
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(seed, "HmacSHA256")); doFinal(("notice:" + id).toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
    fun clear(context: Context) = synchronized(lock) { AtomicFile(File(context.noBackupFilesDir, "announcement_seed")).delete() }
}

internal class AnnouncementsController(private val context: Context, private val scope: CoroutineScope) {
    private val remote = AnnouncementClient()
    private val receipts = MessagePreferences(context)
    private val preferences = context.getSharedPreferences("announcement_responses", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(ServerMessagesState(loggedIn = true))
    val state = mutableState.asStateFlow()
    private val mutableResponse = MutableStateFlow(AnnouncementResponseState(saved = preferences.getStringSet("saved", emptySet()).orEmpty().toSet()))
    val response = mutableResponse.asStateFlow()
    private var job: Job? = null
    private var responseJob: Job? = null
    private var generation = 0L
    private var serverTime = 0L
    private var receivedAt = 0L

    fun load(announce: Boolean = true) {
        if (job?.isActive == true) return
        val expected = generation
        mutableState.value = state.value.copy(loading = true, error = null)
        job = scope.launch {
            try {
                val payload = remote.load()
                val messages = AnnouncementProtocol.parse(payload)
                if (expected != generation) return@launch
                serverTime = payload.getLong("now"); receivedAt = android.os.SystemClock.elapsedRealtime()
                val saved = receipts.load("system_announcements")
                val announcement = if (announce) messages.firstOrNull {
                    it.systemNotice!!.pushes <= serverTime && it.revision !in saved.known && it.revision !in saved.read
                } else null
                mutableState.value = ServerMessagesState(messages, saved.read, loggedIn = true, announcement = announcement)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (expected == generation) {
                    val now = serverTime + (android.os.SystemClock.elapsedRealtime() - receivedAt) / 1000
                    mutableState.value = state.value.copy(loading = false, error = "系统公告暂时无法同步", announcement = null,
                        messages = state.value.messages.filter { it.systemNotice!!.expires > now })
                }
            }
        }
    }
    fun markRead(message: ServerMessage) {
        val saved = receipts.load("system_announcements")
        val next = saved.copy(initialized = true, known = saved.known + message.revision, read = saved.read + message.revision)
        receipts.save("system_announcements", next)
        mutableState.value = state.value.copy(read = next.read, announcement = null)
    }
    fun dismiss() {
        state.value.announcement?.let { notice ->
            val saved = receipts.load("system_announcements")
            receipts.save("system_announcements", saved.copy(known = saved.known + notice.revision))
        }
        mutableState.value = state.value.copy(announcement = null)
    }
    fun markAllRead() { state.value.messages.forEach(::markRead) }
    fun respond(message: ServerMessage, option: String, text: String) {
        if (responseJob?.isActive == true) return
        val notice = message.systemNotice ?: return
        val choice = notice.options.firstOrNull { it.id == option } ?: return
        if ((choice.input && text.isBlank()) || text.length > 1000) {
            mutableResponse.value = response.value.copy(revision = message.revision, error = "请填写 1–1000 字的回复"); return
        }
        val expected = generation
        mutableResponse.value = response.value.copy(revision = message.revision, sending = true, error = null)
        responseJob = scope.launch {
            try {
                val token = withContext(Dispatchers.IO) { AnnouncementIdentity.token(context, notice.id) }
                remote.respond(notice, token, option, if (choice.input) text.trim() else "")
                if (expected != generation) return@launch
                val saved = response.value.saved + message.revision
                preferences.edit().putStringSet("saved", saved).apply()
                mutableResponse.value = AnnouncementResponseState(message.revision, saved = saved)
                markRead(message)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (expected == generation) mutableResponse.value = response.value.copy(sending = false, error = "回复未确认，请刷新后重试；重复提交不会重复计数") }
        }
    }
    fun clear() {
        ++generation; job?.cancel(); responseJob?.cancel(); preferences.edit().clear().commit()
        AnnouncementIdentity.clear(context); mutableState.value = ServerMessagesState(loggedIn = true)
        mutableResponse.value = AnnouncementResponseState()
    }
}
