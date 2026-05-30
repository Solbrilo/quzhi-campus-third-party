package cn.qzxy.hotwater

import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ScrollView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import cn.qzxy.hotwater.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private enum class AppPage {
        OPERATIONS,
        CONFIG,
    }

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var historyAdapter: HistoryAdapter

    private var currentPage: AppPage = AppPage.OPERATIONS

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }
    private var loadingRingAnimator: ObjectAnimator? = null

    private val exportConfigLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { exportConfigToUri(it) }
    }

    private val importConfigLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importConfigFromUri(it) }
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

        binding.configPage.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus != null) {
                newFocus.postDelayed({
                    binding.configPage.scrollTo(0, calculateScrollY(binding.configPage, newFocus))
                }, 200)
            }
        }

        setupHistoryRecyclerView()
        bindActions()
        observeState()

        renderRuntimeDeviceInfo()
        switchPage(AppPage.OPERATIONS, animated = false)

        viewModel.initialize()
        requestNotificationPermission()
    }

    private fun setupHistoryRecyclerView() {
        historyAdapter = HistoryAdapter()
        binding.historyRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = historyAdapter
        }
    }

    private fun bindActions() {
        binding.operationsTab.setOnClickListener { switchPage(AppPage.OPERATIONS) }
        binding.configTab.setOnClickListener { switchPage(AppPage.CONFIG) }

        binding.saveButton.setOnClickListener {
            val rawConfig = collectConfigInput()
            viewModel.saveConfig(rawConfig)
            switchPage(AppPage.OPERATIONS)
            FeedbackHelper.showSuccess(this, binding.root, "配置已保存")
        }

        binding.exportButton.setOnClickListener {
            exportConfig()
        }

        binding.importButton.setOnClickListener {
            importConfig()
        }

        binding.refreshBalanceButton.setOnClickListener {
            viewModel.refreshBalance()
        }

        binding.toggleHotWaterButton.setOnClickListener {
            viewModel.toggle()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.snapshot.collect { snapshot ->
                        val display = snapshot ?: return@collect
                        renderOperationSnapshot(display)
                    }
                }
                launch {
                    viewModel.busy.collect { busy ->
                        updateActionLoadingState(busy)
                        binding.saveButton.isEnabled = !busy
                        val config = viewModel.config.value
                        val canOperate = config.isComplete()
                        binding.toggleHotWaterButton.isEnabled = canOperate && !busy
                        binding.refreshBalanceButton.isEnabled = canOperate && !busy
                    }
                }
                launch {
                    viewModel.statusMessage.collect { message ->
                        if (message.isNotBlank()) {
                            renderLiveStatus(message)
                        }
                    }
                }
                launch {
                    viewModel.config.collect { config ->
                        renderConfig(config)
                        renderRuntimeDeviceInfo()
                    }
                }
                launch {
                    viewModel.history.collect { history ->
                        historyAdapter.submitList(history)
                        binding.historyEmptyText.visibility = if (history.isEmpty()) View.VISIBLE else View.GONE
                        binding.historyRecyclerView.visibility = if (history.isEmpty()) View.GONE else View.VISIBLE
                    }
                }
                launch {
                    viewModel.feedbackEvent.collect { event ->
                        event?.let { showFeedback(it) }
                    }
                }
            }
        }
    }

    private fun showFeedback(event: FeedbackEvent) {
        when (event.type) {
            FeedbackEvent.FeedbackType.ERROR -> {
                FeedbackHelper.showError(this, binding.root, event.message)
            }
            FeedbackEvent.FeedbackType.WARNING -> {
                FeedbackHelper.showWarning(this, binding.root, event.message)
            }
            else -> {} // SUCCESS 不再触发弹窗
        }
        viewModel.clearFeedbackEvent()
    }

    private fun collectConfigInput(): HotWaterConfig {
        val existing = viewModel.getConfig()
        return HotWaterConfig(
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
    }

    private fun renderOperationSnapshot(snapshot: DashboardSnapshot) {
        val config = viewModel.config.value
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
        binding.toggleModeText.text = when {
            snapshot.isDeviceBusyByOther -> "当前模式 · 等待设备空闲"
            snapshot.hasCachedOrder -> "当前模式 · 关闭热水"
            else -> "当前模式 · 开启热水"
        }
        binding.actionHintText.text = when {
            snapshot.isDeviceBusyByOther -> "已有他人正在使用这台设备，请稍后刷新或重试。"
            snapshot.hasCachedOrder -> "检测到缓存订单号，点击大按钮将尝试关热水。"
            else -> "当前没有缓存订单号，点击大按钮将尝试开热水。"
        }

        val canOperate = config.isComplete()
        val isBusy = viewModel.busy.value
        binding.toggleHotWaterButton.isEnabled = canOperate && !isBusy
        binding.refreshBalanceButton.isEnabled = canOperate && !isBusy
        binding.toggleHotWaterButton.alpha = if (canOperate) 1f else 0.55f
        binding.refreshBalanceButton.alpha = if (canOperate) 1f else 0.55f
    }

    private fun renderLiveStatus(message: String) {
        binding.statusPill.text = message
    }

    private fun switchPage(page: AppPage, animated: Boolean = true) {
        val previousPage = currentPage
        currentPage = page

        val inactiveTextColor = ContextCompat.getColor(this, R.color.text_secondary)
        val activeTextColor = ContextCompat.getColor(this, R.color.text_primary)
        binding.operationsTab.setTextColor(if (page == AppPage.OPERATIONS) activeTextColor else inactiveTextColor)
        binding.configTab.setTextColor(if (page == AppPage.CONFIG) activeTextColor else inactiveTextColor)
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

    private fun buildConfigSummary(config: HotWaterConfig): String {
        return if (config.isComplete()) {
            buildString {
                appendLine("配置已完成")
                appendLine("账号: ${HotWaterConfig.maskMiddle(config.telephone, 3, 4)}")
                append("设备 snCode: ${config.snCode}")
            }
        } else {
            "还没完成配置。请填写上方所有字段后点击保存。"
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

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun calculateScrollY(scrollView: ScrollView, target: View): Int {
        var offset = 0
        var child = target
        while (child.parent !== scrollView && child.parent is View) {
            offset += (child.parent as View).top
            child = child.parent as View
        }
        return (offset + target.top - scrollView.height / 3).coerceAtLeast(0)
    }

    private fun exportConfig() {
        val config = collectConfigInput()
        val validationError = HotWaterConfigManager.validateConfig(config)
        if (validationError != null) {
            FeedbackHelper.showError(this, binding.root, "导出失败：$validationError")
            return
        }

        val fileName = HotWaterConfigManager.generateExportFileName(config)
        exportConfigLauncher.launch(fileName)
    }

    private fun exportConfigToUri(uri: android.net.Uri) {
        val config = collectConfigInput()
        val result = HotWaterConfigManager.writeConfigToUri(this, uri, config)
        result.fold(
            onSuccess = {
                FeedbackHelper.showSuccess(this, binding.root, "配置已导出")
            },
            onFailure = { error ->
                FeedbackHelper.showError(this, binding.root, "导出失败：${error.message}")
            }
        )
    }

    private fun importConfig() {
        importConfigLauncher.launch(arrayOf("application/json"))
    }

    private fun importConfigFromUri(uri: android.net.Uri) {
        val result = HotWaterConfigManager.readConfigFromUri(this, uri)
        result.fold(
            onSuccess = { config ->
                val validationError = HotWaterConfigManager.validateConfig(config)
                if (validationError != null) {
                    FeedbackHelper.showError(this, binding.root, "导入失败：$validationError")
                    return
                }

                viewModel.saveConfig(config)
                renderConfig(config)
                FeedbackHelper.showSuccess(this, binding.root, "配置已导入")
            },
            onFailure = { error ->
                FeedbackHelper.showError(this, binding.root, "导入失败：${error.message}")
            }
        )
    }
}
