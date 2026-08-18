package cn.qzxy.hotwater

data class HotWaterConfig(
    val projectId: Int? = null,
    val userId: Int? = null,
    val accountId: Int? = null,
    val loginCode: String = "",
    val telephone: String = "",
    val snCode: String = "",
    val macAddress: String = "",
    val phoneSystem: String = "",
    val version: String = "",
) {
    fun isComplete(): Boolean {
        return projectId != null &&
            userId != null &&
            accountId != null &&
            loginCode.isNotBlank() &&
            telephone.isNotBlank() &&
            snCode.isNotBlank()
    }

    fun maskedSummary(): String {
        return buildString {
            appendLine("projectId: ${projectId ?: "-"}")
            appendLine("userId: ${userId ?: "-"}")
            appendLine("accountId: ${accountId ?: "-"}")
            appendLine("telephone: ${maskMiddle(telephone, 3, 4)}")
            appendLine("loginCode: ${maskMiddle(loginCode, 6, 4)}")
            appendLine("snCode: ${maskMiddle(snCode, 0, 0)}")
            appendLine("macAddress: ${if (macAddress.isBlank()) "-" else macAddress}")
            appendLine("phoneSystem: ${if (phoneSystem.isBlank()) "-" else phoneSystem}")
            append("version: ${if (version.isBlank()) "-" else version}")
        }
    }

    companion object {
        fun maskMiddle(value: String, left: Int = 6, right: Int = 4): String {
            if (value.isBlank()) return "-"
            if (value.length <= left + right) return "*".repeat(value.length)
            return buildString {
                append(value.take(left))
                append("*".repeat(value.length - left - right))
                append(value.takeLast(right))
            }
        }
    }
}

internal fun HotWaterConfig.hasDifferentIdentityFrom(other: HotWaterConfig): Boolean {
    return projectId != other.projectId ||
        userId != other.userId ||
        accountId != other.accountId ||
        telephone != other.telephone ||
        snCode != other.snCode
}
