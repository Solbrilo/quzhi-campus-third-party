package cn.qzxy.hotwater

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HotWaterTileService : TileService() {
    // 使用 application 级别的作用域，避免 onStopListening 取消正在进行的请求
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 缓存实例，避免重复创建
    private val store by lazy { HotWaterConfigStore(applicationContext) }
    private val api by lazy { HotWaterApi() }
    private val repository by lazy { HotWaterRepository(api, store) }

    // 请求状态管理
    @Volatile
    private var isOperating = false

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    private fun updateTileState() {
        val config = store.loadConfig()
        val hasCachedOrder = !store.loadOrderNo().isNullOrBlank()

        qsTile?.apply {
            when {
                isOperating -> {
                    state = Tile.STATE_ACTIVE
                    label = "处理中…"
                    setSubtitleCompat("请稍候")
                }
                !config.isComplete() -> {
                    state = Tile.STATE_UNAVAILABLE
                    label = "热水"
                    setSubtitleCompat("未配置")
                }
                hasCachedOrder -> {
                    state = Tile.STATE_ACTIVE
                    label = "关热水"
                    setSubtitleCompat("点击关闭")
                }
                else -> {
                    state = Tile.STATE_INACTIVE
                    label = "开热水"
                    setSubtitleCompat("点击开启")
                }
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        // 防止重复点击
        if (isOperating) {
            Toast.makeText(this, "操作进行中，请稍候", Toast.LENGTH_SHORT).show()
            return
        }

        val stateGeneration = store.loadStateGeneration()
        val config = RuntimeDeviceInfoResolver.enrichConfig(applicationContext, store.loadConfig())

        if (!config.isComplete()) {
            Toast.makeText(this, "请先打开应用完成配置", Toast.LENGTH_SHORT).show()
            return
        }

        val shouldClose = !store.loadOrderNo().isNullOrBlank()

        // 立即更新 UI 状态
        isOperating = true
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = if (shouldClose) "关闭中…" else "开启中…"
            setSubtitleCompat("请稍候")
            updateTile()
        }

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (shouldClose) repository.close(config) else repository.open(config)
                }
                store.saveOperationResultHistory(
                    type = if (shouldClose) {
                        OperationHistory.OperationType.CLOSE
                    } else {
                        OperationHistory.OperationType.OPEN
                    },
                    result = result,
                    expectedStateGeneration = stateGeneration,
                )
                if (store.loadStateGeneration() != stateGeneration) return@launch
                if (shouldClose && result.historyId != null) {
                    HotWaterSettlementSyncScheduler.enqueue(applicationContext)
                }

                // 操作成功
                HotWaterWidgetUi.updateAllWidgets(applicationContext)
                if (!store.loadOrderNo().isNullOrBlank()) {
                    HotWaterNotificationService.show(applicationContext)
                } else {
                    HotWaterNotificationService.dismiss(applicationContext)
                }

                qsTile?.apply {
                    if (result.snapshot?.isDeviceBusyByOther == true) {
                        state = Tile.STATE_INACTIVE
                        label = "开热水"
                        setSubtitleCompat("设备占用")
                    } else {
                        state = if (shouldClose) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
                        label = if (shouldClose) "开热水" else "关热水"
                        setSubtitleCompat(
                            when {
                                shouldClose && result.outcome == OperationOutcome.PENDING -> "已受理，确认中"
                                shouldClose -> "已关闭"
                                else -> "已开启"
                            },
                        )
                    }
                    updateTile()
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@HotWaterTileService,
                        HotWaterUiText.firstMeaningfulLine(result.summary),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // 操作失败，恢复状态
                HotWaterWidgetUi.updateAllWidgets(applicationContext)
                qsTile?.apply {
                    state = Tile.STATE_ACTIVE
                    label = "热水"
                    setSubtitleCompat("操作失败")
                    updateTile()
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@HotWaterTileService,
                        HotWaterUiText.summarizeError(error),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } finally {
                isOperating = false
                // 延迟恢复正确状态
                withContext(Dispatchers.Main) {
                    updateTileState()
                }
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        // 不取消协程，让请求完成
        // 只是不再更新 UI
    }
}

private fun Tile.setSubtitleCompat(value: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        subtitle = value
    }
}
