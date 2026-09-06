package cn.qzxy.hotwater

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

internal object HotWaterPayload {
    fun findNestedValue(value: Any?, keyName: String): Any? {
        return when (value) {
            is JSONObject -> {
                if (value.has(keyName) && !value.isNull(keyName)) {
                    value.get(keyName)
                } else {
                    value.keys().asSequence()
                        .mapNotNull { childKey -> findNestedValue(value.opt(childKey), keyName) }
                        .firstOrNull()
                }
            }

            is JSONArray -> {
                (0 until value.length())
                    .mapNotNull { index -> findNestedValue(value.opt(index), keyName) }
                    .firstOrNull()
            }

            else -> null
        }
    }

    fun balanceYuan(payload: Any?): Double? {
        firstNumber(
            payload,
            "accountRealMoneyStr",
            "accountMoneyStr",
            "balanceMoneyStr",
            "realMoneyStr",
        )?.let { return normalizeYuan(it) }

        firstNumber(
            payload,
            "accountRealMoney",
            "accountMoney",
        )?.let { return fromMilliYuan(it) }

        return firstNumber(payload, "money", "balance")?.let(::normalizeYuan)
    }

    fun consumptionYuan(payload: Any?): Double? {
        return consumptionYuanIncludingZero(payload)?.takeIf(::isUsableAmount)
    }

    fun consumptionYuanIncludingZero(payload: Any?): Double? {
        val formattedAmount = firstNumber(
            payload,
            "consumeMoneyStr",
            "consumptionMoneyStr",
            "actualConsumeMoneyStr",
            "actualMoneyStr",
            "useMoneyStr",
            "costMoneyStr",
        )
            ?.let(::normalizeYuan)
            ?.takeIf(::isNonNegativeAmount)

        val milliAmount = firstNumber(
            payload,
            "consumeMoney",
            "consumptionMoney",
            "actualConsumeMoney",
            "actualMoney",
            "useMoney",
            "costMoney",
        )
            ?.let(::fromMilliYuan)
            ?.takeIf(::isNonNegativeAmount)

        return when {
            formattedAmount?.let(::isUsableAmount) == true -> formattedAmount
            milliAmount?.let(::isUsableAmount) == true -> milliAmount
            formattedAmount != null -> formattedAmount
            else -> milliAmount
        }
    }

    private fun firstNumber(payload: Any?, vararg keys: String): Double? {
        for (key in keys) {
            val parsed = findNestedValue(payload, key).toFiniteDoubleOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    private fun Any?.toFiniteDoubleOrNull(): Double? {
        val parsed = when (this) {
            is Number -> toDouble()
            null -> null
            else -> toString()
                .trim()
                .removePrefix("¥")
                .replace(",", "")
                .toDoubleOrNull()
        }
        return parsed?.takeIf { it.isFinite() }
    }

    fun normalizeYuan(value: Double): Double {
        return BigDecimal.valueOf(value)
            .setScale(YUAN_SCALE, RoundingMode.HALF_UP)
            .toDouble()
    }

    fun toMilliYuan(value: Double): Long? {
        if (!value.isFinite()) return null
        return try {
            BigDecimal.valueOf(value)
                .movePointRight(YUAN_SCALE)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (_: ArithmeticException) {
            null
        }
    }

    fun fromMilliYuan(value: Double): Double {
        return BigDecimal.valueOf(value)
            .movePointLeft(YUAN_SCALE)
            .setScale(YUAN_SCALE, RoundingMode.HALF_UP)
            .toDouble()
    }

    private fun isUsableAmount(value: Double): Boolean = (toMilliYuan(value) ?: 0L) > 0L

    private fun isNonNegativeAmount(value: Double): Boolean = (toMilliYuan(value) ?: -1L) >= 0L

    private const val YUAN_SCALE = 3
}

internal object HotWaterSettlement {
    fun canAutoCloseUsage(
        orderNo: String?,
        session: UsageSession?,
        remoteNoActiveOrder: Boolean,
        nowMillis: Long,
        minimumAgeMillis: Long,
    ): Boolean {
        if (!remoteNoActiveOrder || orderNo.isNullOrBlank() || session == null) return false
        if (session.startedAtMillis <= 0L || minimumAgeMillis < 0L) return false
        return nowMillis - session.startedAtMillis >= minimumAgeMillis
    }

    fun activeBalanceYuan(session: UsageSession): Double? {
        val storedBalanceYuan = session.startBalanceYuan
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?: return null
        return if (session.startBalanceIsBeforePreauthorization) {
            (storedBalanceYuan - HOT_WATER_PREAUTHORIZATION_YUAN).takeIf { it >= 0.0 }
        } else {
            storedBalanceYuan
        }
    }

    fun balanceBeforePreauthorization(session: UsageSession): Double? {
        val startBalanceYuan = session.startBalanceYuan
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?: return null
        return if (session.startBalanceIsBeforePreauthorization) {
            startBalanceYuan
        } else {
            startBalanceYuan + HOT_WATER_PREAUTHORIZATION_YUAN
        }
    }

    fun hasSettledBalanceChanged(
        activeBalanceYuan: Double?,
        candidateBalanceYuan: Double?,
    ): Boolean {
        val activeMilliYuan = activeBalanceYuan?.let(HotWaterPayload::toMilliYuan) ?: return false
        val candidateMilliYuan = candidateBalanceYuan?.let(HotWaterPayload::toMilliYuan) ?: return false
        return activeMilliYuan != candidateMilliYuan
    }

    fun balancesMatch(firstBalanceYuan: Double?, secondBalanceYuan: Double?): Boolean {
        val firstMilliYuan = firstBalanceYuan?.let(HotWaterPayload::toMilliYuan) ?: return false
        val secondMilliYuan = secondBalanceYuan?.let(HotWaterPayload::toMilliYuan) ?: return false
        return firstMilliYuan == secondMilliYuan
    }

    fun resolveConsumptionYuan(
        provisionalConsumptionYuan: Double?,
        startBalanceYuan: Double?,
        settledBalanceYuan: Double?,
    ): Double? {
        val balanceDelta = if (startBalanceYuan != null && settledBalanceYuan != null) {
            val startMilliYuan = HotWaterPayload.toMilliYuan(startBalanceYuan)
            val settledMilliYuan = HotWaterPayload.toMilliYuan(settledBalanceYuan)
            if (startMilliYuan != null && settledMilliYuan != null) {
                (startMilliYuan - settledMilliYuan)
                    .takeIf { it > 0L }
                    ?.let { HotWaterPayload.fromMilliYuan(it.toDouble()) }
            } else {
                null
            }
        } else {
            null
        }
        if (balanceDelta != null) return balanceDelta

        return provisionalConsumptionYuan
            ?.let(HotWaterPayload::normalizeYuan)
            ?.takeIf { (HotWaterPayload.toMilliYuan(it) ?: 0L) > 0L }
    }

    private const val HOT_WATER_PREAUTHORIZATION_YUAN = 5.0
}
