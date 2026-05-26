package cn.qzxy.hotwater

import android.animation.ObjectAnimator
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import cn.qzxy.hotwater.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private enum class AppPage {
        OPERATIONS,
        CONFIG,
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: HotWaterConfigStore
    private lateinit var repository: HotWaterRepository

    private var currentPage: AppPage = AppPage.OPERATIONS
    private var currentSnapshot: DashboardSnapshot? = null
    private var busy = false
    private var loadingRingAnimator: ObjectAnimator? = null

    private val openDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                importCapture(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        store = HotWaterConfigStore(this)
        repository = HotWaterRepository(HotWaterApi(), store)

        bindActions()

        val preparedConfig = persistRuntimeInfoIfNeeded(store.loadConfig())
        renderRuntimeDeviceInfo()
        renderConfig(preparedConfig)
        currentSnapshot = buildPlaceholderSnapshot(preparedConfig)
        renderOperationSnapshot(currentSnapshot ?: buildPlaceholderSnapshot(preparedConfig))
        switchPage(AppPage.OPERATIONS, animated = false)

        if (preparedConfig.isComplete()) {
            executeRefreshBalance(announce = false)
        } else {
            renderLiveStatus("先到配置页导入 .chls 或填写参数。")
        }
    }

    private fun bindActions() {
        binding.operationsTab.setOnClickListener { switchPage(AppPage.OPERATIONS) }
        binding.configTab.setOnClickListener { switchPage(AppPage.CONFIG) }

        binding.importButton.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("*/*"))
        }

        binding.saveButton.setOnClickListener {
            val config = collectAndSaveConfig()
            currentSnapshot = buildPlaceholderSnapshot(config)
            renderOperationSnapshot(currentSnapshot ?: buildPlaceholderSnapshot(config))
            switchPage(AppPage.OPERATIONS)
            renderLiveStatus("配置已保存，正在同步关键数据…")
            if (config.isComplete()) {
                executeRefreshBalance(announce = false)
            }
            toast("配置已保存")
        }

        binding.refreshBalanceButton.setOnClickListener {
            executeRefreshBalance(announce = true)
        }

        binding.toggleHotWaterButton.setOnClickListener {
            executeToggle()
        }
    }

    private fun executeRefreshBalance(announce: Boolean) {
        runOperation(
            busyMessage = if (announce) "正在刷新余额…" else "正在同步余额…",
        ) { config ->
            repository.check(config)
        }
    }

    private fun executeToggle() {
        val shouldClose = currentSnapshot?.hasCachedOrder == true || !store.loadOrderNo().isNullOrBlank()
        runOperation(
            busyMessage = if (shouldClose) "正在关闭热水…" else "正在开启热水…",
        ) { config ->
            if (shouldClose) repository.close(config) else repository.open(config)
        }
    }

    private fun runOperation(
        busyMessage: String,
        block: suspend (HotWaterConfig) -> OperationResult,
    ) {
        val preparedConfig = persistRuntimeInfoIfNeeded(store.loadConfig())
        if (!preparedConfig.isComplete()) {
            switchPage(AppPage.CONFIG)
            renderLiveStatus("配置还不完整，先导入 .chls 或补全字段。")
            toast("请先完成配置")
            return
        }

        setBusy(true)
        renderLiveStatus(busyMessage)

        lifecycleScope.launch {
            try {
                val result = block(preparedConfig)
                currentSnapshot = result.snapshot ?: buildPlaceholderSnapshot(preparedConfig)
                renderOperationSnapshot(currentSnapshot ?: buildPlaceholderSnapshot(preparedConfig))
                renderLiveStatus(HotWaterUiText.firstMeaningfulLine(result.summary))
                syncWidgetStatus(result.title, result.summary)
            } catch (error: Exception) {
                currentSnapshot = buildPlaceholderSnapshot(preparedConfig)
                renderOperationSnapshot(currentSnapshot ?: buildPlaceholderSnapshot(preparedConfig))
                val message = HotWaterUiText.summarizeError(error)
                renderLiveStatus(message)
                syncWidgetStatus("操作失败", message)
            } finally {
                setBusy(false)
            }
        }
    }

    private fun collectAndSaveConfig(): HotWaterConfig {
        val existing = store.loadConfig()
        val rawConfig = HotWaterConfig(
            projectId = binding.projectIdInput.text.toString().trim().toIntOrNull(),
            userId = binding.userIdInput.text.toString().trim().toIntOrNull(),
            accountId = binding.accountIdInput.text.toString().trim().toIntOrNull(),
            loginCode = binding.loginCodeInput.text.toString().trim(),
            telephone = binding.telephoneInput.text.toString().trim(),
            snCode = binding.snCodeInput.text.toString().trim().replace(":", "").uppercase(),
            macAddress = existing.macAddress,
            phoneSystem = existing.phoneSystem,
            version = existing.version,
        )

        val prepared = persistRuntimeInfoIfNeeded(rawConfig)
        store.saveConfig(prepared)
        renderRuntimeDeviceInfo()
        renderConfig(prepared)
        store.saveWidgetState(
            title = "配置已就绪",
            message = "桌面小组件和主按钮现在都可以直接使用。",
            isBusy = false,
        )
        HotWaterWidgetUi.updateAllWidgets(this)
        return prepared
    }

    private fun renderConfig(config: HotWaterConfig) {
        binding.projectIdInput.setText(config.projectId?.toString().orEmpty())
        binding.userIdInput.setText(config.userId?.toString().orEmpty())
        binding.accountIdInput.setText(config.accountId?.toString().orEmpty())
        binding.loginCodeInput.setText(config.loginCode)
        binding.telephoneInput.setText(config.telephone)
        binding.snCodeInput.setText(config.snCode)
        binding.configStateText.text = buildConfigSummary(config)
    }

    private fun renderRuntimeDeviceInfo() {
        val runtimeInfo = RuntimeDeviceInfoResolver.resolve(this)
        binding.systemInfoValue.text = runtimeInfo.systemLabel
        binding.macAddressValue.text = runtimeInfo.macAddressLabel
    }

    private fun buildPlaceholderSnapshot(config: HotWaterConfig): DashboardSnapshot {
        val cachedOrderNo = store.loadOrderNo()
        return DashboardSnapshot(
            balanceYuan = 0.0,
            balanceLabel = "--",
            userLabel = config.telephone.takeIf { it.isNotBlank() }
                ?.let { HotWaterConfig.maskMiddle(it, 3, 4) }
                ?: "未导入账号",
            deviceName = "热水设备待刷新",
            locationLabel = if (config.isComplete()) "点刷新余额后自动识别宿舍位置" else "先完成配置后再自动识别",
            statusLabel = when {
                !config.isComplete() -> "当前配置还不完整。"
                !cachedOrderNo.isNullOrBlank() -> "已缓存订单号，主按钮现在会显示为关。"
                else -> "当前没有缓存订单号，主按钮现在会显示为开。"
            },
            orderNo = cachedOrderNo,
            hasCachedOrder = !cachedOrderNo.isNullOrBlank(),
        )
    }

    private fun renderOperationSnapshot(snapshot: DashboardSnapshot) {
        val preparedConfig = store.loadConfig()
        binding.balanceAmountText.text = if (snapshot.balanceLabel == "--") "¥ --" else "¥ ${snapshot.balanceLabel}"
        binding.accountText.text = "账号：${snapshot.userLabel}"
        binding.locationText.text = "宿舍位置：${snapshot.locationLabel}"
        binding.deviceText.text = "设备：${snapshot.deviceName}"
        binding.statusPill.text = snapshot.statusLabel
        binding.toggleHotWaterButton.text = if (snapshot.hasCachedOrder) "关" else "开"
        binding.toggleHotWaterButton.setBackgroundResource(
            if (snapshot.hasCachedOrder) {
                R.drawable.glass_circle_button_close_background
            } else {
                R.drawable.glass_circle_button_open_background
            },
        )
        binding.actionOuterCircle.setBackgroundResource(
            if (snapshot.hasCachedOrder) {
                R.drawable.glass_halo_close
            } else {
                R.drawable.glass_halo_open
            },
        )
        binding.actionLoadingRing.setBackgroundResource(
            if (snapshot.hasCachedOrder) {
                R.drawable.loading_ring_close
            } else {
                R.drawable.loading_ring_open
            },
        )
        binding.toggleModeText.text = if (snapshot.hasCachedOrder) "当前模式 · 关闭热水" else "当前模式 · 开启热水"
        binding.actionHintText.text = if (snapshot.hasCachedOrder) {
            "检测到缓存订单号，点击大按钮将尝试关热水。"
        } else {
            "当前没有缓存订单号，点击大按钮将尝试开热水。"
        }

        val canOperate = preparedConfig.isComplete()
        binding.toggleHotWaterButton.isEnabled = canOperate && !busy
        binding.refreshBalanceButton.isEnabled = canOperate && !busy
        binding.toggleHotWaterButton.alpha = if (canOperate) 1f else 0.55f
        binding.refreshBalanceButton.alpha = if (canOperate) 1f else 0.55f
    }

    private fun renderLiveStatus(message: String) {
        binding.statusPill.text = message
    }

    private fun switchPage(page: AppPage, animated: Boolean = true) {
        val previousPage = currentPage
        currentPage = page

        val activeTextColor = ContextCompat.getColor(this, R.color.text_primary)
        val inactiveTextColor = ContextCompat.getColor(this, R.color.text_secondary)
        binding.operationsTab.setTextColor(if (page == AppPage.OPERATIONS) android.graphics.Color.WHITE else inactiveTextColor)
        binding.configTab.setTextColor(if (page == AppPage.CONFIG) android.graphics.Color.WHITE else inactiveTextColor)
        binding.operationsTab.setBackgroundResource(
            if (page == AppPage.OPERATIONS) R.drawable.tab_active_background else R.drawable.tab_inactive_background
        )
        binding.configTab.setBackgroundResource(
            if (page == AppPage.CONFIG) R.drawable.tab_active_background else R.drawable.tab_inactive_background
        )

        val incoming = if (page == AppPage.OPERATIONS) binding.operationsPage else binding.configPage
        val outgoing = if (page == AppPage.OPERATIONS) binding.configPage else binding.operationsPage

        if (!animated || previousPage == page) {
            incoming.visibility = View.VISIBLE
            incoming.translationX = 0f
            incoming.alpha = 1f
            outgoing.visibility = if (previousPage == page) outgoing.visibility else View.GONE
            outgoing.translationX = 0f
            outgoing.alpha = 1f
            return
        }

        val width = binding.root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val direction = if (page == AppPage.OPERATIONS) -1 else 1

        incoming.visibility = View.VISIBLE
        incoming.alpha = 0f
        incoming.translationX = (width * direction).toFloat()

        outgoing.animate()
            .translationX((-width * direction * 0.16f).toFloat())
            .alpha(0f)
            .setDuration(220L)
            .withEndAction {
                outgoing.visibility = View.GONE
                outgoing.translationX = 0f
                outgoing.alpha = 1f
            }
            .start()

        incoming.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(280L)
            .start()
    }

    private fun setBusy(value: Boolean) {
        busy = value
        updateActionLoadingState(value)
        binding.importButton.isEnabled = !value
        binding.saveButton.isEnabled = !value
        renderOperationSnapshot(currentSnapshot ?: buildPlaceholderSnapshot(store.loadConfig()))
    }

    private fun importCapture(uri: Uri) {
        lifecycleScope.launch {
            try {
                val imported = contentResolver.openInputStream(uri)?.use(CaptureParser::parse)
                    ?: error("无法读取所选文件")
                val prepared = persistRuntimeInfoIfNeeded(imported)
                store.saveConfig(prepared)
                renderConfig(prepared)
                renderRuntimeDeviceInfo()
                currentSnapshot = buildPlaceholderSnapshot(prepared)
                renderOperationSnapshot(currentSnapshot ?: buildPlaceholderSnapshot(prepared))
                store.saveWidgetState(
                    title = "配置已导入",
                    message = "主按钮和桌面小组件都可以直接使用。",
                    isBusy = false,
                )
                HotWaterWidgetUi.updateAllWidgets(this@MainActivity)
                switchPage(AppPage.OPERATIONS)
                renderLiveStatus("配置已导入，正在同步关键数据…")
                if (prepared.isComplete()) {
                    executeRefreshBalance(announce = false)
                }
            } catch (error: Exception) {
                renderLiveStatus("导入失败，请重新选择 .chls 文件。")
                toast(error.message ?: "导入失败")
            }
        }
    }

    private fun persistRuntimeInfoIfNeeded(config: HotWaterConfig): HotWaterConfig {
        val prepared = RuntimeDeviceInfoResolver.enrichConfig(this, config)
        if (prepared != config) {
            store.saveConfig(prepared)
        }
        return prepared
    }

    private fun syncWidgetStatus(title: String, summary: String) {
        store.saveWidgetState(
            title = title,
            message = HotWaterUiText.firstMeaningfulLine(summary).ifBlank { "请求已执行。" },
            isBusy = false,
        )
        HotWaterWidgetUi.updateAllWidgets(this)
    }

    private fun buildConfigSummary(config: HotWaterConfig): String {
        return if (config.isComplete()) {
            buildString {
                appendLine("配置已完成")
                appendLine("账号: ${HotWaterConfig.maskMiddle(config.telephone, 3, 4)}")
                append("设备 snCode: ${config.snCode}")
            }
        } else {
            "还没完成配置。导入 .chls 后会自动填充大部分关键字段。"
        }
    }

    private fun updateActionLoadingState(loading: Boolean) {
        if (loading) {
            binding.actionLoadingRing.visibility = View.VISIBLE
            if (loadingRingAnimator == null) {
                loadingRingAnimator = ObjectAnimator.ofFloat(binding.actionLoadingRing, View.ROTATION, 0f, 360f).apply {
                    duration = 1200L
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                }
            }
            loadingRingAnimator?.start()
        } else {
            loadingRingAnimator?.cancel()
            binding.actionLoadingRing.rotation = 0f
            binding.actionLoadingRing.visibility = View.GONE
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
