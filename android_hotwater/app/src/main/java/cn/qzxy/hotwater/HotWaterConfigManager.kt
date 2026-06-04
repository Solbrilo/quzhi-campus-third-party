package cn.qzxy.hotwater

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object HotWaterConfigManager {
    private const val CONFIG_FILE_EXTENSION = ".json"
    private const val CONFIG_MIME_TYPE = "application/json"

    /**
     * 导出配置到 JSON 文件
     */
    fun exportConfig(config: HotWaterConfig): JSONObject {
        return JSONObject().apply {
            put("projectId", config.projectId)
            put("userId", config.userId)
            put("accountId", config.accountId)
            put("loginCode", config.loginCode)
            put("telephone", config.telephone)
            put("snCode", config.snCode)
            put("macAddress", config.macAddress)
            put("phoneSystem", config.phoneSystem)
            put("version", config.version)
            put("exportedAt", System.currentTimeMillis())
            put("appVersion", "1.0.3")
        }
    }

    /**
     * 从 JSON 导入配置
     */
    fun importConfig(json: JSONObject): HotWaterConfig {
        return HotWaterConfig(
            projectId = json.optIntOrNull("projectId"),
            userId = json.optIntOrNull("userId"),
            accountId = json.optIntOrNull("accountId"),
            loginCode = json.optString("loginCode", ""),
            telephone = json.optString("telephone", ""),
            snCode = json.optString("snCode", ""),
            macAddress = json.optString("macAddress", ""),
            phoneSystem = json.optString("phoneSystem", ""),
            version = json.optString("version", ""),
        )
    }

    /**
     * 生成导出文件名（以手机号命名）
     */
    fun generateExportFileName(config: HotWaterConfig): String {
        val telephone = config.telephone.takeIf { it.isNotBlank() } ?: "unknown"
        val maskedTelephone = if (telephone.length > 7) {
            "${telephone.take(3)}****${telephone.takeLast(4)}"
        } else {
            telephone
        }
        return "hotwater_config_${maskedTelephone}$CONFIG_FILE_EXTENSION"
    }

    /**
     * 从 URI 读取配置
     */
    fun readConfigFromUri(context: Context, uri: Uri): Result<HotWaterConfig> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val jsonString = reader.readText()
                val json = JSONObject(jsonString)
                val config = importConfig(json)
                Result.success(config)
            } ?: Result.failure(Exception("无法读取文件"))
        } catch (e: Exception) {
            Result.failure(Exception("配置文件格式错误: ${e.message}"))
        }
    }

    /**
     * 将配置写入 URI
     */
    fun writeConfigToUri(context: Context, uri: Uri, config: HotWaterConfig): Result<Unit> {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val writer = OutputStreamWriter(outputStream)
                val json = exportConfig(config)
                writer.write(json.toString(2))
                writer.flush()
                Result.success(Unit)
            } ?: Result.failure(Exception("无法写入文件"))
        } catch (e: Exception) {
            Result.failure(Exception("导出配置失败: ${e.message}"))
        }
    }

    /**
     * 获取配置文件 MIME 类型
     */
    fun getConfigMimeType(): String = CONFIG_MIME_TYPE

    /**
     * 获取配置文件扩展名
     */
    fun getConfigFileExtension(): String = CONFIG_FILE_EXTENSION

    /**
     * 验证配置是否完整
     */
    fun validateConfig(config: HotWaterConfig): String? {
        return when {
            config.projectId == null -> "缺少 projectId"
            config.userId == null -> "缺少 userId"
            config.accountId == null -> "缺少 accountId"
            config.loginCode.isBlank() -> "缺少 loginCode"
            config.telephone.isBlank() -> "缺少 telephone"
            config.snCode.isBlank() -> "缺少 snCode"
            else -> null
        }
    }

    /**
     * 扩展函数：安全获取 Int 或 null
     */
    private fun JSONObject.optIntOrNull(key: String): Int? {
        return if (has(key) && !isNull(key)) optInt(key) else null
    }
}
