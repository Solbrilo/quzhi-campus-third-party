package cn.qzxy.hotwater

import java.io.InputStream
import java.nio.charset.Charset

object CaptureParser {
    private val latin1 = Charset.forName("ISO-8859-1")

    fun parse(inputStream: InputStream): HotWaterConfig {
        val text = inputStream.readBytes().toString(latin1)

        val projectId = extractFirst(text, listOf("""projectId=(\d+)"""), "projectId").toInt()
        val userId = extractFirst(text, listOf("""userId=(\d+)"""), "userId").toInt()
        val accountId = extractFirst(text, listOf("""accountId=(\d+)"""), "accountId").toInt()
        val loginCode = extractFirst(text, listOf("""loginCode=([0-9a-f]{32})"""), "loginCode")
        val telephone = extractFirst(
            text,
            listOf("""telephone=(\d+)""", """telPhone=(\d+)"""),
            "telephone",
        )
        val snCode = extractFirst(
            text,
            listOf("""snCode=([0-9A-Fa-f]+)""", """macAddress=([0-9A-Fa-f:]+)"""),
            "snCode/macAddress",
        ).replace(":", "").uppercase()

        val macAddress = Regex("""macAddress=([0-9A-Fa-f:]+)""")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.uppercase()
            .orEmpty()

        val phoneSystem = Regex("""phoneSystem=([A-Za-z0-9_]+)""")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?: "Harmony"

        val version = Regex("""version=([0-9.]+)""")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?: "0.0.1"

        return HotWaterConfig(
            projectId = projectId,
            userId = userId,
            accountId = accountId,
            loginCode = loginCode,
            telephone = telephone,
            snCode = snCode,
            macAddress = macAddress,
            phoneSystem = phoneSystem,
            version = version,
        )
    }

    private fun extractFirst(text: String, patterns: List<String>, fieldName: String): String {
        for (pattern in patterns) {
            val match = Regex(pattern).find(text)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        error("Unable to extract $fieldName from capture file")
    }
}
