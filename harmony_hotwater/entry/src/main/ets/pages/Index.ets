import common from '@ohos.app.ability.common'
import picker from '@ohos.file.picker'
import fs from '@ohos.file.fs'
import window from '@ohos.window'
import bundleManager from '@ohos.bundle.bundleManager'
import router from '@ohos.router'
import { Want } from '@kit.AbilityKit'
import { CaptureParser } from '../common/CaptureParser'
import { ConsumptionRecord, DashboardSnapshot, HotWaterConfig, RuntimeDeviceInfo } from '../common/HotWaterModels'
import { HotWaterRepository } from '../common/HotWaterRepository'
import { HotWaterStore } from '../common/HotWaterStore'
import { LaunchActionBridge } from '../common/LaunchActionBridge'
import { RuntimeDeviceInfoResolver } from '../common/RuntimeDeviceInfoResolver'
import { GiteeUpdateRepository, UpdateCheckState } from '../common/AppUpdate'

const LOW_BALANCE_WARNING_YUAN: number = 5
const ALIPAY_RECHARGE_URI: string = 'alipays://platformapi/startapp?appId=20000001'

enum HomePage {
  OPERATIONS = 0,
  RECORDS = 1,
  CONFIG = 2
}

interface FieldInputParams {
  text: string
  placeholder: string
  onChange: (value: string) => void
}

Environment.envProp('colorMode', ColorMode.LIGHT)

@Entry
@Component
struct Index {
  private hostContext?: common.Context
  private store?: HotWaterStore
  private repository?: HotWaterRepository
  private updateRepository: GiteeUpdateRepository = new GiteeUpdateRepository()

  @State activePage: number = HomePage.OPERATIONS
  @State busy: boolean = false
  @State refreshingBalance: boolean = false
  @State liveStatus: string = '先导入 .chls 或补全配置。'

  @State balanceYuan: number = 0
  @State balanceLabel: string = '--'
  @State userLabel: string = '未导入账号'
  @State deviceName: string = '热水设备待刷新'
  @State locationLabel: string = '先完成配置再刷新'
  @State statusLabel: string = '当前配置还不完整。'
  @State hasCachedOrder: boolean = false
  @State usageStartedAtMillis: number = 0
  @State usageElapsedMillis: number = 0
  @State consumptionRecords: ConsumptionRecord[] = []
  private usageTimerGeneration: number = 0
  @State aboutMenuVisible: boolean = false
  @State currentVersionLabel: string = '1.3.0'

  @State systemInfoLabel: string = 'HarmonyOS 设备'
  @State macAddressLabel: string = '系统已隐藏'

  @State projectIdText: string = ''
  @State userIdText: string = ''
  @State accountIdText: string = ''
  @State loginCodeText: string = ''
  @State telephoneText: string = ''
  @State snCodeText: string = ''
  @State configSummary: string = '还没完成配置。导入 .chls 后会自动填充关键字段。'

  @State ringAngle: number = 0
  @State refreshIconAngle: number = 0
  @StorageProp('statusBarHeight') statusBarHeight: number = 0
  @StorageProp('colorMode') colorMode: ColorMode = ColorMode.LIGHT

  aboutToAppear(): void {
    this.hostContext = this.getUIContext().getHostContext() as common.Context
    this.store = new HotWaterStore(this.hostContext)
    this.repository = new HotWaterRepository(this.store)
    this.initialize()
    this.setupImmersiveWindow()
  }

  onPageShow(): void {
    this.consumePendingLaunchAction()
    this.setupImmersiveWindow()
    if (this.busy) {
      this.runRingStep()
    }
  }

  private async setupImmersiveWindow(): Promise<void> {
    try {
      const ctx = this.getUIContext().getHostContext() as common.UIAbilityContext
      const win = await window.getLastWindow(ctx)
      const isDarkMode = this.colorMode === ColorMode.DARK
      await win.setWindowSystemBarProperties({
        statusBarColor: '#00FFFFFF',
        statusBarContentColor: isDarkMode ? '#F2F7FF' : '#11213B',
        navigationBarColor: isDarkMode ? '#08111F' : '#EAF2FF',
        navigationBarContentColor: isDarkMode ? '#F2F7FF' : '#11213B'
      })
    } catch (err) {
      // 降级处理
    }
  }

  private async initialize(): Promise<void> {
    await this.loadCurrentVersion()
    const runtimeInfo = RuntimeDeviceInfoResolver.resolve()
    this.applyRuntimeInfo(runtimeInfo)

    if (!this.store) {
      return
    }

    const config = RuntimeDeviceInfoResolver.enrichConfig(await this.store.loadConfig())
    await this.store.saveConfig(config)
    this.applyConfigToForm(config)
    this.applyConfigSummary(config)
    this.applySnapshot(this.placeholderSnapshot(config))
    await this.loadConsumptionRecords()

    if (config.isComplete()) {
      await this.refreshBalance(false)
    }
    await this.consumePendingLaunchAction()
    this.checkForUpdates(true)
  }

  private applyRuntimeInfo(runtimeInfo: RuntimeDeviceInfo): void {
    this.systemInfoLabel = runtimeInfo.systemLabel
    this.macAddressLabel = runtimeInfo.macAddressLabel
  }

  private applySnapshot(snapshot: DashboardSnapshot): void {
    this.balanceYuan = snapshot.balanceYuan
    this.balanceLabel = snapshot.balanceLabel
    this.userLabel = snapshot.userLabel
    this.deviceName = snapshot.deviceName
    this.locationLabel = snapshot.locationLabel
    this.hasCachedOrder = snapshot.hasCachedOrder
    this.statusLabel = snapshot.statusLabel
    this.syncUsageTimer()
  }

  private applyConfigToForm(config: HotWaterConfig): void {
    this.projectIdText = config.projectId > 0 ? config.projectId.toString() : ''
    this.userIdText = config.userId > 0 ? config.userId.toString() : ''
    this.accountIdText = config.accountId > 0 ? config.accountId.toString() : ''
    this.loginCodeText = config.loginCode
    this.telephoneText = config.telephone
    this.snCodeText = config.snCode
  }

  private applyConfigSummary(config: HotWaterConfig): void {
    if (config.isComplete()) {
      this.configSummary = `配置已完成\n账号: ${HotWaterConfig.maskMiddle(config.telephone, 3, 4)}\nsnCode: ${config.snCode}`
    } else {
      this.configSummary = '还没完成配置。导入 .chls 后会自动填充关键字段。'
    }
  }

  private placeholderSnapshot(config: HotWaterConfig): DashboardSnapshot {
    const snapshot = new DashboardSnapshot()
    snapshot.balanceLabel = '--'
    snapshot.userLabel = config.telephone.length > 0 ? HotWaterConfig.maskMiddle(config.telephone, 3, 4) : '未导入账号'
    snapshot.deviceName = '热水设备待刷新'
    snapshot.locationLabel = config.isComplete() ? '点刷新余额后自动识别宿舍位置' : '先完成配置后再自动识别'
    snapshot.statusLabel = config.isComplete() ? '当前没有缓存订单号，点击主按钮将尝试开热水。' : '当前配置还不完整。'
    snapshot.orderNo = ''
    snapshot.hasCachedOrder = false
    return snapshot
  }

  private async loadConsumptionRecords(): Promise<void> {
    if (!this.store) {
      return
    }
    this.consumptionRecords = await this.store.loadConsumptionRecords()
  }

  private syncUsageTimer(): void {
    const generation = ++this.usageTimerGeneration
    if (!this.hasCachedOrder || !this.store) {
      this.usageStartedAtMillis = 0
      this.usageElapsedMillis = 0
      return
    }
    this.store.loadUsageSession().then((session) => {
      if (generation !== this.usageTimerGeneration || !session || !this.hasCachedOrder) {
        if (generation !== this.usageTimerGeneration) {
          return
        }
        this.usageStartedAtMillis = 0
        this.usageElapsedMillis = 0
        return
      }
      this.usageStartedAtMillis = session.startedAtMillis
      this.usageElapsedMillis = Math.max(0, Date.now() - session.startedAtMillis)
      this.runUsageTimer(generation)
    })
  }

  private runUsageTimer(generation: number): void {
    if (generation !== this.usageTimerGeneration || !this.hasCachedOrder || this.usageStartedAtMillis <= 0) {
      return
    }
    setTimeout(() => {
      if (generation !== this.usageTimerGeneration || !this.hasCachedOrder || this.usageStartedAtMillis <= 0) {
        return
      }
      this.usageElapsedMillis = Math.max(0, Date.now() - this.usageStartedAtMillis)
      this.runUsageTimer(generation)
    }, 1000)
  }

  private formatUsageDuration(durationMillis: number): string {
    return ConsumptionRecord.formatDuration(durationMillis)
  }

  private async collectConfig(): Promise<HotWaterConfig> {
    const loaded = this.store ? await this.store.loadConfig() : new HotWaterConfig()
    const config = new HotWaterConfig()
    config.projectId = Number(this.projectIdText || '0')
    config.userId = Number(this.userIdText || '0')
    config.accountId = Number(this.accountIdText || '0')
    config.loginCode = this.loginCodeText.trim()
    config.telephone = this.telephoneText.trim()
    config.snCode = this.snCodeText.trim().replace(/:/g, '').toUpperCase()
    config.macAddress = loaded.macAddress
    config.phoneSystem = loaded.phoneSystem
    config.version = loaded.version
    return RuntimeDeviceInfoResolver.enrichConfig(config)
  }

  private async saveConfig(): Promise<void> {
    if (!this.store) {
      return
    }
    const config = await this.collectConfig()
    await this.store.saveConfig(config)
    this.applyConfigSummary(config)
    this.liveStatus = '配置已保存。'
    this.switchPage(HomePage.OPERATIONS)
    if (config.isComplete()) {
      this.applySnapshot(this.placeholderSnapshot(config))
      await this.refreshBalance(false)
    }
  }

  private switchPage(target: number): void {
    this.getUIContext()?.animateTo({
      duration: 350,
      curve: Curve.EaseInOut
    }, () => {
      this.activePage = target
    })
  }

  private async refreshBalance(showStatus: boolean = true): Promise<void> {
    if (!this.repository || !this.store) {
      return
    }
    const config = await this.collectConfig()
    if (!config.isComplete()) {
      this.switchPage(HomePage.CONFIG)
      this.liveStatus = '配置还不完整，先导入 .chls 或补全字段。'
      return
    }
    this.refreshingBalance = true
    this.runRefreshIconAnimation()
    if (showStatus) {
      this.liveStatus = '正在刷新余额…'
    }
    try {
      const result = await this.repository.check(config)
      this.applySnapshot(result.snapshot ?? this.placeholderSnapshot(config))
      this.liveStatus = result.summary.split('\n')[0]
    } catch (error) {
      this.liveStatus = `刷新失败：${String(error)}`
    } finally {
      this.refreshingBalance = false
    }
  }

  private async toggleHotWater(skipLowBalanceWarning: boolean = false): Promise<void> {
    if (!this.repository) {
      return
    }
    const config = await this.collectConfig()
    if (!config.isComplete()) {
      this.switchPage(HomePage.CONFIG)
      this.liveStatus = '配置还不完整，先导入 .chls 或补全字段。'
      return
    }
    if (!skipLowBalanceWarning && this.shouldShowLowBalanceOpenWarning()) {
      this.showLowBalanceDialog()
      return
    }
    this.busy = true
    this.liveStatus = this.hasCachedOrder ? '正在关闭热水…' : '正在开启热水…'
    this.runRingStep()
    try {
      const result = this.hasCachedOrder ? await this.repository.close(config) : await this.repository.open(config)
      this.applySnapshot(result.snapshot ?? this.placeholderSnapshot(config))
      if (result.usageDurationMillis >= 0 && this.store) {
        const record = new ConsumptionRecord()
        record.id = Date.now()
        record.endedAtMillis = Date.now()
        record.usageDurationMillis = result.usageDurationMillis
        record.startedAtMillis = record.endedAtMillis - result.usageDurationMillis
        record.consumptionYuan = result.consumptionYuan
        await this.store.saveConsumptionRecord(record)
        await this.loadConsumptionRecords()
      }
      this.liveStatus = result.summary.split('\n')[0]
    } catch (error) {
      this.liveStatus = `操作失败：${String(error)}`
    } finally {
      this.busy = false
    }
  }

  private shouldShowLowBalanceOpenWarning(): boolean {
    return !this.hasCachedOrder &&
      this.balanceLabel !== '--' &&
      this.balanceYuan < LOW_BALANCE_WARNING_YUAN
  }

  private showLowBalanceDialog(): void {
    AlertDialog.show({
      title: '余额不足5元',
      message: '余额不足5元，建议通过支付宝进行充值。',
      primaryButton: {
        value: '继续开启热水',
        action: () => {
          this.toggleHotWater(true)
        }
      },
      secondaryButton: {
        value: '前往充值',
        action: () => {
          this.openAlipayForRecharge()
        }
      }
    })
  }

  private async openAlipayForRecharge(): Promise<void> {
    try {
      const ctx = this.getUIContext().getHostContext() as common.UIAbilityContext
      const want: Want = {
        action: 'ohos.want.action.viewData',
        uri: ALIPAY_RECHARGE_URI
      }
      await ctx.startAbility(want)
    } catch (error) {
      this.liveStatus = '未能打开支付宝，请手动通过支付宝进行充值。'
    }
  }

  private runRingStep(): void {
    if (!this.busy) {
      return
    }
    this.getUIContext()?.animateTo({
      duration: 60,
      curve: Curve.Linear,
      onFinish: () => {
        this.runRingStep()
      }
    }, () => {
      this.ringAngle += 18
    })
  }

  private runRefreshIconAnimation(): void {
    if (!this.refreshingBalance) {
      return
    }
    this.getUIContext()?.animateTo({
      duration: 60,
      curve: Curve.Linear,
      onFinish: () => {
        this.runRefreshIconAnimation()
      }
    }, () => {
      this.refreshIconAngle += 18
    })
  }

  private async importCaptureFile(): Promise<void> {
    if (!this.hostContext || !this.store) {
      return
    }
    try {
      const options = new picker.DocumentSelectOptions()
      options.fileSuffixFilters = ['.chls']
      options.maxSelectNumber = 1
      const documentPicker = new picker.DocumentViewPicker(this.hostContext)
      const uris = await documentPicker.select(options)
      if (!uris || uris.length === 0) {
        return
      }
      const captureText = this.readTextFromUri(uris[0])
      const parsedConfig = RuntimeDeviceInfoResolver.enrichConfig(CaptureParser.parse(captureText))
      await this.store.saveConfig(parsedConfig)
      this.applyConfigToForm(parsedConfig)
      this.applyConfigSummary(parsedConfig)
      this.switchPage(HomePage.OPERATIONS)
      this.liveStatus = '已导入 .chls，正在同步关键数据…'
      await this.refreshBalance(false)
    } catch (error) {
      this.liveStatus = `导入失败：${JSON.stringify(error)}`
    }
  }

  private async consumePendingLaunchAction(): Promise<void> {
    const action = LaunchActionBridge.consumePendingAction()
    if (action.length === 0) {
      return
    }
    if (action === 'config') {
      this.switchPage(HomePage.CONFIG)
      return
    }
    if (action === 'open') {
      this.switchPage(HomePage.OPERATIONS)
      this.hasCachedOrder = false
      await this.toggleHotWater()
      return
    }
    if (action === 'close') {
      this.switchPage(HomePage.OPERATIONS)
      this.hasCachedOrder = true
      await this.toggleHotWater()
    }
  }

  private readTextFromUri(uri: string): string {
    const file = fs.openSync(uri, fs.OpenMode.READ_ONLY)
    const size = 1024 * 128
    const buffer = new ArrayBuffer(size)
    const readSize = fs.readSync(file.fd, buffer)
    fs.closeSync(file.fd)
    const bytes = new Uint8Array(buffer)
    let text = ''
    for (let index = 0; index < readSize; index++) {
      text += String.fromCharCode(bytes[index])
    }
    return text
  }

  private async loadCurrentVersion(): Promise<void> {
    try {
      const bundleInfo = await bundleManager.getBundleInfoForSelf(
        bundleManager.BundleFlag.GET_BUNDLE_INFO_DEFAULT
      )
      if (bundleInfo.versionName.length > 0) {
        this.currentVersionLabel = bundleInfo.versionName
      }
    } catch (error) {
      this.currentVersionLabel = '1.3.0'
    }
  }

  private openAbout(): void {
    this.aboutMenuVisible = false
    router.pushUrl({ url: 'pages/About' })
  }

  private async checkForUpdates(showDialogWhenAvailable: boolean = false): Promise<void> {
    try {
      const result = await this.updateRepository.check(this.currentVersionLabel)
      if (showDialogWhenAvailable && result.state === UpdateCheckState.UPDATE_AVAILABLE && result.update) {
        this.showUpdateAvailableDialog(result.update.versionName, result.update.releaseNotes)
      }
    } catch (error) {
      // 启动检查失败时保持静默，用户可在关于页手动重试。
    }
  }

  private showUpdateAvailableDialog(versionName: string, releaseNotes: string): void {
    const updateNotice = '已发布 HarmonyOS 更新，请下载 HAP 手动安装。'
    AlertDialog.show({
      title: `已发布新版本 v${versionName}`,
      message: releaseNotes.length > 0 ? `${updateNotice}\n\n${releaseNotes}` : updateNotice,
      primaryButton: {
        value: '查看详情',
        action: () => {
          this.openAbout()
        }
      },
      secondaryButton: {
        value: '稍后',
        action: () => {
        }
      }
    })
  }

  @Builder
  private TopHeader(): void {
    Column({ space: 6 }) {
      Row() {
        Column({ space: 4 }) {
          Text('趣智校园热水')
            .fontSize(30)
            .fontWeight(FontWeight.Bold)
            .fontColor($r('app.color.text_primary'))
          Text('HarmonyOS 迁移版 · 导入抓包后可直接刷新余额和开关热水')
            .fontSize(14)
            .fontColor($r('app.color.text_secondary'))
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Start)

        Text('⋮')
          .fontSize(28)
          .fontColor($r('app.color.text_primary'))
          .width(40)
          .height(40)
          .textAlign(TextAlign.Center)
          .onClick(() => {
            this.aboutMenuVisible = !this.aboutMenuVisible
          })
      }
      .width('100%')
      .alignItems(VerticalAlign.Top)

      if (this.aboutMenuVisible) {
        Row() {
          Row().layoutWeight(1)
          Text('关于')
            .fontSize(14)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_primary'))
            .padding({ left: 18, right: 18, top: 12, bottom: 12 })
            .backgroundColor($r('app.color.card_background'))
            .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
            .borderRadius(14)
            .border({ width: 1, color: $r('app.color.card_border'), style: BorderStyle.Solid })
            .onClick(() => {
              this.openAbout()
            })
        }
        .width('100%')
      }
    }
    .width('100%')
    .padding({ top: this.statusBarHeight > 0 ? this.statusBarHeight : 0 })
  }

  @Builder
  private PageSwitcher(): void {
    Stack() {
      // 背景玻璃容器
      Row()
        .width('100%')
        .height(48)
        .borderRadius(28)
        .linearGradient({
          angle: 180,
          colors: [
            [$r('app.color.glass_panel_start'), 0.0],
            [$r('app.color.glass_panel_middle'), 0.5],
            [$r('app.color.glass_panel_end'), 1.0]
          ]
        })
        .backgroundBlurStyle(BlurStyle.BACKGROUND_REGULAR)
        .backdropBlur(28)
        .border({
          width: 0.5,
          color: $r('app.color.glass_border'),
          style: BorderStyle.Solid
        })
        .shadow({
          radius: 16,
          color: $r('app.color.glass_shadow_primary'),
          offsetX: 0,
          offsetY: 4
        })
        .shadow({
          radius: 8,
          color: $r('app.color.glass_shadow_secondary'),
          offsetX: 0,
          offsetY: 1
        })

      // 滑块 - 带光晕渐变
      Row()
        .width('30%')
        .height(36)
        .borderRadius(22)
        .linearGradient({
          angle: 135,
          colors: [
            [$r('app.color.accent_gradient_start'), 0.0],
            [$r('app.color.accent_gradient_end'), 1.0]
          ]
        })
        .shadow({
          radius: 14,
          color: $r('app.color.accent_shadow_primary'),
          offsetX: 0,
          offsetY: 4
        })
        .shadow({
          radius: 4,
          color: $r('app.color.accent_shadow_secondary'),
          offsetX: 0,
          offsetY: 1
        })
        .offset({
          x: this.activePage === HomePage.OPERATIONS
            ? '-33%'
            : (this.activePage === HomePage.RECORDS ? '0%' : '33%')
        })
        .animation({
          duration: 350,
          curve: Curve.EaseInOut
        })

      Row({ space: 0 }) {
        Text('操作页')
          .fontSize(14)
          .fontWeight(this.activePage === HomePage.OPERATIONS ? FontWeight.Medium : FontWeight.Normal)
          .fontColor(this.activePage === HomePage.OPERATIONS ? Color.White : $r('app.color.switch_inactive_text'))
          .width('33.33%')
          .height(48)
          .textAlign(TextAlign.Center)
          .onClick(() => {
            this.switchPage(HomePage.OPERATIONS)
          })

        Text('记录页')
          .fontSize(14)
          .fontWeight(this.activePage === HomePage.RECORDS ? FontWeight.Medium : FontWeight.Normal)
          .fontColor(this.activePage === HomePage.RECORDS ? Color.White : $r('app.color.switch_inactive_text'))
          .width('33.33%')
          .height(48)
          .textAlign(TextAlign.Center)
          .onClick(() => {
            this.loadConsumptionRecords()
            this.switchPage(HomePage.RECORDS)
          })

        Text('配置页')
          .fontSize(14)
          .fontWeight(this.activePage === HomePage.CONFIG ? FontWeight.Medium : FontWeight.Normal)
          .fontColor(this.activePage === HomePage.CONFIG ? Color.White : $r('app.color.switch_inactive_text'))
          .width('33.33%')
          .height(48)
          .textAlign(TextAlign.Center)
          .onClick(() => {
            this.switchPage(HomePage.CONFIG)
          })
      }
      .width('100%')
    }
    .width('100%')
    .height(52)
    .backdropBlur(14)
  }

  @Builder
  private FieldInput(params: FieldInputParams): void {
    TextInput({ text: params.text, placeholder: params.placeholder })
      .fontSize(16)
      .fontColor($r('app.color.text_primary'))
      .placeholderColor($r('app.color.text_secondary'))
      .backgroundColor($r('app.color.input_background'))
      .backgroundBlurStyle(BlurStyle.COMPONENT_REGULAR)
      .borderRadius(14)
      .padding({ left: 16, right: 16, top: 12, bottom: 12 })
      .border({
        width: 1,
        color: $r('app.color.input_border'),
        style: BorderStyle.Solid
      })
      .onChange(params.onChange)
  }

  @Builder
  private OperationsPage(): void {
    Column({ space: 18 }) {
      Column({ space: 8 }) {
        Text('可用余额')
          .fontSize(13)
          .fontColor($r('app.color.text_secondary'))
          .width('100%')
          .textAlign(TextAlign.Start)

        Row({ space: 10 }) {
          Text(`¥ ${this.balanceLabel}`)
            .fontSize(40)
            .fontWeight(FontWeight.Bold)
            .fontColor($r('app.color.text_primary'))
            .layoutWeight(1)

          Stack() {
            Button('刷新')
              .type(ButtonType.Capsule)
              .fontSize(13)
              .backgroundColor($r('app.color.button_soft_background'))
              .fontColor(this.refreshingBalance ? '#00FFFFFF' : $r('app.color.button_soft_text'))
              .enabled(!this.refreshingBalance && !this.busy)
              .onClick(() => {
                this.refreshBalance(true)
              })

            if (this.refreshingBalance) {
              LoadingProgress()
                .width(24)
                .height(24)
                .color($r('app.color.accent'))
            }
          }
        }
        .width('100%')

        if (this.hasCachedOrder && this.usageStartedAtMillis > 0) {
          Text(`开启时长 ${this.formatUsageDuration(this.usageElapsedMillis)}`)
            .fontSize(13)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.accent'))
            .width('100%')
        }

        Row() {
          Text(this.statusLabel)
            .fontSize(12)
            .fontColor($r('app.color.text_chip'))
            .backgroundColor($r('app.color.status_chip_background'))
            .padding({ left: 12, right: 12, top: 7, bottom: 7 })
            .borderRadius(14)
            .border({
              width: 1,
              color: $r('app.color.input_border'),
              style: BorderStyle.Solid
            })
        }
        .margin({ top: 2 })

        Text(`宿舍位置：${this.locationLabel}`)
          .fontSize(15)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_primary'))
          .width('100%')

        Text(`设备：${this.deviceName}`)
          .fontSize(13)
          .fontColor($r('app.color.text_secondary'))
          .width('100%')

        Text(`账号：${this.userLabel}`)
          .fontSize(13)
          .fontColor($r('app.color.text_secondary'))
          .width('100%')
      }
      .padding(18)
      .borderRadius(24)
      .backgroundColor($r('app.color.card_background'))
      .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
      .border({
        width: 1,
        color: $r('app.color.card_border'),
        style: BorderStyle.Solid
      })
      .shadow({
        radius: 16,
        color: $r('app.color.card_shadow'),
        offsetX: 0,
        offsetY: 2
      })
      .margin({ left: 2, right: 2, top: 2, bottom: 2 })

      Stack() {
        Circle()
          .width(180)
          .height(180)
          .fill(this.hasCachedOrder ? $r('app.color.close_accent') : $r('app.color.accent'))
          .opacity(0.08)
          .blendMode(BlendMode.PLUS)

        Circle()
          .width(280)
          .height(280)
          .fill(this.hasCachedOrder ? $r('app.color.close_accent') : $r('app.color.accent'))
          .opacity(0.05)
          .blendMode(BlendMode.PLUS)

        Stack() {
          Circle()
            .width(272)
            .height(272)
            .fill($r('app.color.power_outer_background'))
            .backdropBlur(4)

          Circle()
            .width(242)
            .height(242)
            .fill($r('app.color.power_inner_background'))
            .opacity(0.55)

          if (this.busy) {
            Circle()
              .width(226)
              .height(226)
              .fill('rgba(0,0,0,0)')
              .stroke(this.hasCachedOrder ? $r('app.color.close_accent') : $r('app.color.accent'))
              .strokeWidth(3)
              .strokeDashArray([2, 12])
              .strokeLineCap(LineCapStyle.Round)
              .rotate({ angle: this.ringAngle })
          }

          Button(this.hasCachedOrder ? '关' : '开')
            .width(204)
            .height(204)
            .borderRadius(102)
            .fontSize(56)
            .fontWeight(FontWeight.Bold)
            .backgroundColor(this.hasCachedOrder ? $r('app.color.power_button_close_background') : $r('app.color.power_button_open_background'))
            .fontColor($r('app.color.power_button_text'))
            .enabled(!this.busy && !this.refreshingBalance)
            .shadow({
              radius: 24,
              color: this.hasCachedOrder ? $r('app.color.power_close_shadow') : $r('app.color.power_open_shadow'),
              offsetX: 0,
              offsetY: 6
            })
            .border({
              width: 2,
              color: this.hasCachedOrder ? $r('app.color.power_button_close_border') : $r('app.color.power_button_open_border'),
              style: BorderStyle.Solid
            })
            .onClick(() => {
              this.toggleHotWater()
            })
        }
        .width('100%')
        .height('100%')
      }
      .width('100%')
      .height(290)
    }
    .width('100%')
    .padding({ bottom: 70 })
  }

  @Builder
  private RecordsPage(): void {
    Column({ space: 14 }) {
      Column({ space: 4 }) {
        Text('消费记录')
          .fontSize(22)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.text_primary'))
        Text('记录已结束的热水使用，金额按余额变化计算。')
          .fontSize(13)
          .fontColor($r('app.color.text_secondary'))
      }
      .width('100%')

      if (this.consumptionRecords.length === 0) {
        Column() {
          Text('暂无消费记录')
            .fontSize(16)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_primary'))
          Text('关闭一次热水后，消费金额和开启时长会显示在这里。')
            .fontSize(13)
            .fontColor($r('app.color.text_secondary'))
            .margin({ top: 8 })
        }
        .width('100%')
        .padding(20)
        .borderRadius(24)
        .backgroundColor($r('app.color.card_background'))
        .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
        .border({ width: 1, color: $r('app.color.card_border'), style: BorderStyle.Solid })
      } else {
        ForEach(this.consumptionRecords, (record: ConsumptionRecord) => {
          Column({ space: 10 }) {
            Row() {
              Column({ space: 4 }) {
                Text('热水消费')
                  .fontSize(16)
                  .fontWeight(FontWeight.Medium)
                  .fontColor($r('app.color.text_primary'))
                Text(`开始于 ${record.getStartedAtLabel()}`)
                  .fontSize(12)
                  .fontColor($r('app.color.text_secondary'))
              }
              .layoutWeight(1)

              Text(record.consumptionYuan >= 0 ? `¥ ${record.consumptionYuan.toFixed(2)}` : '待同步')
                .fontSize(18)
                .fontWeight(FontWeight.Bold)
                .fontColor($r('app.color.close_accent'))
            }
            .width('100%')

            Row() {
              Text('开启时长')
                .fontSize(13)
                .fontColor($r('app.color.text_secondary'))
                .layoutWeight(1)
              Text(record.getDurationLabel())
                .fontSize(13)
                .fontWeight(FontWeight.Medium)
                .fontColor($r('app.color.text_primary'))
            }
            .width('100%')
          }
          .width('100%')
          .padding(18)
          .borderRadius(24)
          .backgroundColor($r('app.color.card_background'))
          .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
          .border({ width: 1, color: $r('app.color.card_border'), style: BorderStyle.Solid })
          .shadow({ radius: 12, color: $r('app.color.card_shadow'), offsetX: 0, offsetY: 2 })
        }, (record: ConsumptionRecord) => record.id.toString())
      }
    }
    .width('100%')
    .padding({ bottom: 70 })
  }

  @Builder
  private ConfigPage(): void {
    Column({ space: 16 }) {
      Column({ space: 12 }) {
        Text('导入与自动识别')
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.text_primary'))

        Text('继续保留 .chls 导入能力。系统信息和本机 Wi-Fi MAC 由程序自动识别。')
          .fontSize(13)
          .fontColor($r('app.color.text_secondary'))

        Row({ space: 12 }) {
          Button('导入 .chls')
            .layoutWeight(1)
            .type(ButtonType.Capsule)
            .backgroundColor($r('app.color.button_soft_background'))
            .fontColor($r('app.color.button_soft_text'))
            .enabled(!this.busy)
            .onClick(() => {
              this.importCaptureFile()
            })

          Button('保存配置')
            .layoutWeight(1)
            .type(ButtonType.Capsule)
            .backgroundColor($r('app.color.accent'))
            .fontColor(Color.White)
            .enabled(!this.busy)
            .onClick(() => {
              this.saveConfig()
            })
        }
      }
      .padding(18)
      .borderRadius(24)
      .backgroundColor($r('app.color.card_background'))
      .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
      .border({
        width: 1,
        color: $r('app.color.card_border'),
        style: BorderStyle.Solid
      })
      .shadow({
        radius: 16,
        color: $r('app.color.card_shadow'),
        offsetX: 0,
        offsetY: 2
      })
      .margin({ left: 2, right: 2, top: 2, bottom: 2 })

      Column({ space: 8 }) {
        Text('系统信息')
          .fontSize(12)
          .fontColor($r('app.color.text_secondary'))
        Text(this.systemInfoLabel)
          .fontSize(16)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_primary'))
        Text('本机 Wi-Fi MAC')
          .fontSize(12)
          .fontColor($r('app.color.text_secondary'))
          .margin({ top: 8 })
        Text(this.macAddressLabel)
          .fontSize(16)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_primary'))
      }
      .padding(18)
      .borderRadius(24)
      .backgroundColor($r('app.color.card_background'))
      .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
      .border({
        width: 1,
        color: $r('app.color.card_border'),
        style: BorderStyle.Solid
      })
      .shadow({
        radius: 16,
        color: $r('app.color.card_shadow'),
        offsetX: 0,
        offsetY: 2
      })
      .margin({ left: 2, right: 2, top: 2, bottom: 2 })

      Column({ space: 12 }) {
        Text('关键字段')
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.text_primary'))

        this.FieldInput({
          text: this.projectIdText,
          placeholder: 'projectId',
          onChange: (value: string) => {
            this.projectIdText = value
          }
        })
        this.FieldInput({
          text: this.userIdText,
          placeholder: 'userId',
          onChange: (value: string) => {
            this.userIdText = value
          }
        })
        this.FieldInput({
          text: this.accountIdText,
          placeholder: 'accountId',
          onChange: (value: string) => {
            this.accountIdText = value
          }
        })
        this.FieldInput({
          text: this.loginCodeText,
          placeholder: 'loginCode',
          onChange: (value: string) => {
            this.loginCodeText = value
          }
        })
        this.FieldInput({
          text: this.telephoneText,
          placeholder: 'telephone',
          onChange: (value: string) => {
            this.telephoneText = value
          }
        })
        this.FieldInput({
          text: this.snCodeText,
          placeholder: 'snCode',
          onChange: (value: string) => {
            this.snCodeText = value
          }
        })

        Text(this.configSummary)
          .fontSize(13)
          .fontColor($r('app.color.text_secondary'))
      }
      .padding(18)
      .borderRadius(24)
      .backgroundColor($r('app.color.card_background'))
      .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
      .border({
        width: 1,
        color: $r('app.color.card_border'),
        style: BorderStyle.Solid
      })
      .shadow({
        radius: 16,
        color: $r('app.color.card_shadow'),
        offsetX: 0,
        offsetY: 2
      })
      .margin({ left: 2, right: 2, top: 2, bottom: 2 })
    }
    .width('100%')
    .padding({ bottom: 70 })
  }

  build() {
    Stack() {
      Circle()
        .width(340)
        .height(340)
        .fill($r('app.color.decor_blue_large'))
        .opacity(0.22)
        .align(Alignment.TopEnd)
        .offset({ x: 90, y: -100 })

      Circle()
        .width(200)
        .height(200)
        .fill($r('app.color.decor_blue_small'))
        .opacity(0.16)
        .align(Alignment.TopStart)
        .offset({ x: -60, y: 80 })

      Circle()
        .width(280)
        .height(280)
        .fill($r('app.color.decor_peach_large'))
        .opacity(0.15)
        .align(Alignment.BottomEnd)
        .offset({ x: 40, y: 80 })

      Circle()
        .width(160)
        .height(160)
        .fill($r('app.color.decor_peach_small'))
        .opacity(0.12)
        .align(Alignment.BottomStart)
        .offset({ x: -30, y: -20 })

      Column({ space: 18 }) {
        this.TopHeader()

        Stack({ alignContent: Alignment.Bottom }) {
          Scroll() {
            Column({ space: 16 }) {
              Column() {
                if (this.activePage === HomePage.OPERATIONS) {
                  this.OperationsPage()
                } else if (this.activePage === HomePage.RECORDS) {
                  this.RecordsPage()
                } else {
                  this.ConfigPage()
                }
              }
              .width('100%')
              .transition(
                TransitionEffect.asymmetric(
                  TransitionEffect.OPACITY
                    .combine(TransitionEffect.translate({ x: -36 }))
                    .animation({ duration: 360, curve: Curve.EaseInOut }),
                  TransitionEffect.OPACITY
                    .combine(TransitionEffect.translate({ x: 24 }))
                    .animation({ duration: 240, curve: Curve.EaseInOut })
                )
              )
            }
            .width('100%')
            .padding({ left: 2, right: 2 })
          }
          .scrollBar(BarState.Off)
          .width('100%')
          .height('100%')

          Row() {
            this.PageSwitcher()
          }
          .width('100%')
          .padding({ left: 18, right: 18, bottom: 8 })
        }
        .layoutWeight(1)
        .width('100%')
      }
      .width('100%')
      .height('100%')
      .padding(20)
    }
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM])
    .backgroundColor($r('app.color.app_background'))
  }
}
