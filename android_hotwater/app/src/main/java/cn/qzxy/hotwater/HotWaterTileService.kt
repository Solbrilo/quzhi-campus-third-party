package cn.qzxy.hotwater

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HotWaterTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        val store = HotWaterConfigStore(applicationContext)
        val config = store.loadConfig()
        val hasCachedOrder = !store.loadOrderNo().isNullOrBlank()

        qsTile?.apply {
            if (!config.isComplete()) {
                state = Tile.STATE_UNAVAILABLE
                label = "热水"
                subtitle = "未配置"
            } else if (hasCachedOrder) {
                state = Tile.STATE_ACTIVE
                label = "关热水"
                subtitle = "点击关闭"
            } else {
                state = Tile.STATE_INACTIVE
                label = "开热水"
                subtitle = "点击开启"
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        val store = HotWaterConfigStore(applicationContext)
        val config = RuntimeDeviceInfoResolver.enrichConfig(applicationContext, store.loadConfig())

        if (!config.isComplete()) {
            Toast.makeText(this, "请先打开应用完成配置", Toast.LENGTH_SHORT).show()
            return
        }

        val shouldClose = !store.loadOrderNo().isNullOrBlank()

        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = if (shouldClose) "关闭中…" else "开启中…"
            subtitle = "请稍候"
            updateTile()
        }

        scope.launch {
            try {
                val repository = HotWaterRepository(HotWaterApi(), store)
                val result = withContext(Dispatchers.IO) {
                    if (shouldClose) repository.close(config) else repository.open(config)
                }
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
                        subtitle = "设备占用"
                    } else {
                        state = if (shouldClose) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
                        label = if (shouldClose) "开热水" else "关热水"
                        subtitle = if (shouldClose) "已关闭" else "已开启"
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
            } catch (error: Exception) {
                HotWaterWidgetUi.updateAllWidgets(applicationContext)
                qsTile?.apply {
                    state = Tile.STATE_ACTIVE
                    label = "热水"
                    subtitle = "操作失败"
                    updateTile()
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@HotWaterTileService,
                        HotWaterUiText.summarizeError(error),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                onStartListening()
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        scope.cancel()
    }
}
