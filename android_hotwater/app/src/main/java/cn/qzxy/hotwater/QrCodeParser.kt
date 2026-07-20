package cn.qzxy.hotwater

import java.util.Locale

object QrCodeParser {
    private const val DEVICE_QR_PREFIX = "KLCXKJ-Water"

    fun normalizeSnCode(rawValue: String): String? {
        val normalized = rawValue
            .trim()
            .replace(":", "")
            .uppercase(Locale.ROOT)
        return normalized.takeIf(DEVICE_CODE::matches)
    }

    fun parseSnCode(rawValue: String): String? {
        val fields = rawValue
            .trim()
            .removePrefix("\uFEFF")
            .split(',')
            .map(String::trim)

        if (fields.size < 3 ||
            !fields.first().equals(DEVICE_QR_PREFIX, ignoreCase = true) ||
            fields[1].isBlank()
        ) {
            return null
        }

        return normalizeSnCode(fields.last())
    }

    private val DEVICE_CODE = Regex("^[0-9A-F]{12}$")
}
