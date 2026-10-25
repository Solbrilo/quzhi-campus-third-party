package cn.qzxy.hotwater

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

interface ServerBillRemoteDataSource {
    suspend fun billList(config: HotWaterConfig, month: String): JSONObject
}

/** Amount is in milli-yuan solely for exact display/summing. Source values are server yuan. */
data class ServerBill(
    val id: String,
    val orderId: String,
    val orderNo: String?,
    val consumeDate: String,
    val amountMilliYuan: Long?,
    val description: String,
    val timestamp: Long?,
    val period: YearMonth,
    val waterMilliLiters: Long? = null,
)

data class BillQuery(val year: Int, val month: Int? = null) {
    fun months(today: YearMonth = YearMonth.now()): List<YearMonth> {
        require(year in 2010..today.year) { "请选择有效年份" }
        require(month == null || month in 1..12) { "请选择有效月份" }
        return (month?.let { listOf(it) } ?: (1..12).toList())
            .map { YearMonth.of(year, it) }.filter { it <= today }
    }
}

data class ServerBillsState(
    val query: BillQuery = BillQuery(YearMonth.now().year, YearMonth.now().monthValue),
    val bills: List<ServerBill> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val message: String = "",
    val failedMonths: List<YearMonth> = emptyList(),
)

internal fun HotWaterConfig.accountRequestKey(): String =
    listOf(projectId, userId, accountId, telephone, loginCode).joinToString("\u0000")

internal fun HotWaterConfig.hasAccount(): Boolean = projectId != null && userId != null &&
    accountId != null && telephone.isNotBlank() && loginCode.isNotBlank()

/** Owns request cancellation and publication order, independent from the active heater. */
internal class ServerBillsController(
    private val scope: CoroutineScope,
    private val remote: ServerBillRemoteDataSource,
    private val onCredentialExpired: () -> Unit,
) {
    private val mutableState = MutableStateFlow(ServerBillsState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var accountKey: String? = null
    private var job: Job? = null

    fun bind(config: HotWaterConfig) {
        val key = config.accountRequestKey()
        if (accountKey != key) { clear(); accountKey = key }
    }

    fun clear() {
        generation++
        job?.cancel()
        job = null
        accountKey = null
        mutableState.value = ServerBillsState()
    }

    fun load(config: HotWaterConfig, query: BillQuery, force: Boolean = false) {
        bind(config)
        if (!config.hasAccount()) {
            mutableState.value = ServerBillsState(query = query, message = "登录趣智校园后即可查看服务器账单")
            return
        }
        val before = mutableState.value
        if (!force && before.query == query && (before.loading || before.loaded)) return
        val months = runCatching { query.months() }.getOrElse {
            mutableState.value = ServerBillsState(query = query, message = it.message.orEmpty())
            return
        }
        job?.cancel()
        val requestGeneration = ++generation
        val sameQuery = before.query == query
        mutableState.value = ServerBillsState(query, if (sameQuery) before.bills else emptyList(), loading = true)
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val results = coroutineScope {
                    val permits = Semaphore(3)
                    months.map { month -> async {
                        permits.withPermit {
                            try {
                                month to Result.success(ServerBillParser.parse(remote.billList(config, month.toString()), month))
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (expired: CredentialExpiredException) { throw expired }
                            catch (error: Exception) { month to Result.failure<List<ServerBill>>(error) }
                        }
                    } }.awaitAll()
                }
                if (requestGeneration != generation) return@launch
                val failed = results.filter { it.second.isFailure }.map { it.first }
                val successful = results.filter { it.second.isSuccess }.flatMap { it.second.getOrThrow() }
                // A failed refresh retains already visible records for that same account/period.
                val retained = if (sameQuery) before.bills.filter { it.period in failed } else emptyList()
                mutableState.value = ServerBillsState(
                    query = query,
                    bills = (successful + retained).distinctBy { it.id }
                        .sortedWith(compareByDescending<ServerBill> { it.period }.thenByDescending { it.timestamp ?: 0L }),
                    loaded = failed.isEmpty(),
                    message = if (failed.isEmpty()) "" else "部分账单加载失败，请重试；汇总可能不完整",
                    failedMonths = failed,
                )
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: CredentialExpiredException) {
                if (requestGeneration == generation) {
                    mutableState.value = ServerBillsState(query = query, message = CREDENTIAL_EXPIRED_MESSAGE)
                    onCredentialExpired()
                }
            } finally {
                if (requestGeneration == generation) {
                    mutableState.value = mutableState.value.copy(loading = false)
                    job = null
                }
            }
        }.also { it.start() }
    }
}

internal object ServerBillParser {
    private val dateFormatters = listOf("uuuu-MM-dd HH:mm:ss", "uuuu-MM-dd HH:mm", "uuuu/MM/dd HH:mm:ss", "uuuuMMddHHmmss")
        .map { DateTimeFormatter.ofPattern(it, Locale.ROOT).withResolverStyle(java.time.format.ResolverStyle.STRICT) }

    fun parse(payload: JSONObject, requestedMonth: YearMonth, zoneId: ZoneId = ZoneId.systemDefault()): List<ServerBill> {
        QzxyLoginProtocol.requireSuccessfulResponse(payload)
        if (!payload.has("data")) throw IOException("账单响应缺少数据")
        if (payload.isNull("data")) return emptyList()
        val data = payload.opt("data")
        val array = when (data) {
            is JSONArray -> data
            is JSONObject -> data.optJSONArray("list") ?: data.optJSONArray("records")
            else -> null
        } ?: throw IOException("无法识别服务器账单，请重试")
        return (0 until array.length()).map { index ->
            val item = array.optJSONObject(index) ?: throw IOException("账单条目格式错误")
            val bill = item.optJSONObject("consumeBillDTO") ?: item
            val date = text(bill, "consumeDate")
            val dateTime = dateFormatters.firstNotNullOfOrNull { runCatching { LocalDateTime.parse(date, it) }.getOrNull() }
            val id = text(bill, "orderId")
            val orderNo = text(bill, "orderNo").takeIf(String::isNotBlank)
            val description = text(bill, "description").ifBlank { "热水消费" }
            val waterMilliLiters = parseWaterMilliLiters(bill)
                ?: if (bill !== item) parseWaterMilliLiters(item) else null
            ServerBill(
                id = if (id.isNotBlank() || orderNo != null) "${orderNo ?: id}|$date" else "$requestedMonth|$date|$description|$index",
                orderId = id,
                orderNo = orderNo,
                consumeDate = date,
                amountMilliYuan = parseAmount(text(bill, "consumeMoney")),
                description = description,
                timestamp = dateTime?.atZone(zoneId)?.toInstant()?.toEpochMilli(),
                period = dateTime?.let { YearMonth.from(it) } ?: requestedMonth,
                waterMilliLiters = waterMilliLiters,
            )
        }.distinctBy { it.id }
    }

    private val waterFields = listOf(
        "consumeWaterStr", "waterConsumptionStr", "waterVolumeStr", "waterStr",
        "consumeWater", "consumeWaterNum", "consumeWaterCount", "waterConsumption",
        "waterVolume", "consumeVolumeStr", "consumeVolume", "waterAmount", "waterNum",
        "waterUsed", "water", "useWater",
    )

    private fun parseWaterMilliLiters(json: JSONObject): Long? = waterFields
        .firstNotNullOfOrNull { key ->
            if (!json.has(key) || json.isNull(key)) null
            else parseWaterValue(json.optString(key).trim())
        }

    private fun parseWaterValue(raw: String): Long? = runCatching {
        val normalized = raw.replace(" ", "").replace("升", "L")
        val unit = when {
            normalized.endsWith("mL", ignoreCase = true) -> "ml"
            normalized.endsWith("L", ignoreCase = true) -> "l"
            normalized.endsWith("m³", ignoreCase = true) || normalized.endsWith("m3", ignoreCase = true) -> "m3"
            else -> "l"
        }
        val numeric = when (unit) {
            "ml" -> normalized.dropLast(2)
            "m3" -> normalized.dropLast(if (normalized.endsWith("m³", true)) 2 else 2)
            else -> normalized.removeSuffix("L").removeSuffix("l")
        }
        val liters = BigDecimal(numeric).let {
            when (unit) {
                "ml" -> it.movePointLeft(3)
                "m3" -> it.movePointRight(3)
                else -> it
            }
        }
        liters.takeIf { it.signum() >= 0 }
            ?.movePointRight(3)
            ?.setScale(0, RoundingMode.HALF_UP)
            ?.longValueExact()
    }.getOrNull()

    private fun text(json: JSONObject, key: String): String =
        if (json.isNull(key)) "" else json.optString(key).trim()

    private fun parseAmount(value: String): Long? = runCatching {
        BigDecimal(value).takeIf { it.signum() >= 0 }?.movePointRight(3)
            ?.setScale(0, RoundingMode.UNNECESSARY)?.longValueExact()
    }.getOrNull()
}
