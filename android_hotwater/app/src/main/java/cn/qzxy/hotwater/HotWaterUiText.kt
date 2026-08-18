package cn.qzxy.hotwater

object HotWaterUiText {
    fun summarizeError(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            error is CredentialExpiredException -> CREDENTIAL_EXPIRED_MESSAGE

            raw.contains("No active orderNo found", ignoreCase = true) ->
                "没有找到可关闭的订单，先开一次热水再试。"

            raw.contains("Please complete all required config fields first", ignoreCase = true) ->
                "配置还不完整，请先到配置页补全参数。"

            raw.contains("HTTP", ignoreCase = true) ->
                raw.lineSequence().firstOrNull { it.isNotBlank() } ?: "请求失败，请稍后重试。"

            raw.isBlank() ->
                "请求失败，请稍后重试。"

            else ->
                raw.lineSequence().firstOrNull { it.isNotBlank() } ?: "请求失败，请稍后重试。"
        }
    }

    fun summarizeWidgetResult(action: WidgetHotWaterAction, result: OperationResult): String {
        val snapshot = result.snapshot
        return when (action) {
            WidgetHotWaterAction.OPEN -> {
                if (snapshot?.isDeviceBusyByOther == true) {
                    "已有他人正在使用这台设备，请稍后再试。"
                } else if (snapshot?.hasCachedOrder == true) {
                    "热水已开启，可以直接去试水。"
                } else {
                    "开热水请求已发送，等待设备确认。"
                }
            }

            WidgetHotWaterAction.CLOSE -> {
                if (snapshot?.isDeviceBusyByOther == true) {
                    "已有他人正在使用这台设备，请稍后再试。"
                } else if (result.outcome == OperationOutcome.PENDING) {
                    "关热水请求已受理，设备确认和账单正在后台同步。"
                } else if (snapshot?.hasCachedOrder == false) {
                    "热水已关闭。"
                } else {
                    "关热水请求已发送，等待设备确认。"
                }
            }

            WidgetHotWaterAction.REFRESH -> result.summary.replace('\n', ' ')
        }
    }

    fun firstMeaningfulLine(text: String): String {
        return text.lineSequence().firstOrNull { it.isNotBlank() } ?: text
    }
}
