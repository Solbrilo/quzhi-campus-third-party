import common from '@ohos.app.ability.common'
import picker from '@ohos.file.picker'
import fs from '@ohos.file.fs'
import window from '@ohos.window'
import bundleManager from '@ohos.bundle.bundleManager'
import { Want } from '@kit.AbilityKit'
import { buffer } from '@kit.ArkTS'
import { SymbolGlyphModifier } from '@kit.ArkUI'
import { vibrator } from '@kit.SensorServiceKit'
import { BlurStrategy, DividerMode, HdsTabs, hdsMaterial } from '@kit.UIDesignKit'
import {
  ConsumptionRecord,
  DashboardSnapshot,
  HotWaterConfig,
  RuntimeDeviceInfo
} from '../common/HotWaterModels'
import { HotWaterRepository } from '../common/HotWaterRepository'
import { HotWaterStore } from '../common/HotWaterStore'
import { HotWaterPayload } from '../common/HotWaterPayload'
import {
  BillEntry,
  BillGrouping,
  BillGroup,
  BillScope,
  BillSummary,
  buildBillEntries,
  filterBillEntries,
  formatBillAmount,
  formatBillDuration,
  getBillGroupRenderKey,
  getBillMonths as getBillMonthsForYear,
  getBillYears as getAvailableBillYears,
  groupBillEntries,
  summarizeBillEntries,
  toggleBillGroupCollapsed
} from '../common/BillPresentation'
import { LaunchActionBridge } from '../common/LaunchActionBridge'
import { ConfigFlowBridge } from '../common/ConfigFlowBridge'
import { HotWaterConfigTransfer } from '../common/HotWaterConfigTransfer'
import { RuntimeDeviceInfoResolver } from '../common/RuntimeDeviceInfoResolver'
import { GiteeUpdateRepository, UpdateCheckState } from '../common/AppUpdate'
import {
  AppearanceMaterialLevel,
  AppearanceMaterialType
} from '../common/AppAppearance'

const LOW_BALANCE_WARNING_YUAN: number = 5
const ALIPAY_RECHARGE_URI: string =
  'alipays://platformapi/startapp?appId=2018090661238647&page=pages%2Findex%2Findex'
const MAX_CONFIG_FILE_BYTES: number = 128 * 1024
const POST_CLOSE_RECONCILE_DELAY_MS: number = 2200
const POST_CLOSE_RECONCILE_ATTEMPTS: number = 2
const FOREGROUND_VALIDATION_MIN_INTERVAL_MS: number = 3000

enum HomePage {
  OPERATIONS = 0,
  RECORDS = 1,
  CONFIG = 2
}

interface FieldInputParams {
  label: string
  description: string
  text: string
  placeholder: string
  inputType?: InputType
  isSensitive?: boolean
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
  @State liveStatus: string = '登录趣智校园以自动填充账号字段，再补充设备码。'

  @State balanceYuan: number = 0
  @State balanceLabel: string = '--'
  @State userLabel: string = '未导入账号'
  @State deviceName: string = '热水设备待刷新'
  @State locationLabel: string = '先完成配置再刷新'
  @State statusLabel: string = '当前配置还不完整。'
  @State hasCachedOrder: boolean = false
  @State deviceOnline: boolean | null = null
  @State usageStartedAtMillis: number = 0
  @State usageElapsedMillis: number = 0
  @State consumptionRecords: ConsumptionRecord[] = []
  @State billScope: BillScope = BillScope.MONTH
  @State billSelectedYear: number = 0
  @State billSelectedMonth: number = 0
  @State billGrouping: BillGrouping = BillGrouping.MONTH
  @State collapsedBillKeys: string[] = []
  private usageTimerGeneration: number = 0
  private billCollapseInitialized: boolean = false
  private consumingLaunchAction: boolean = false
  private pageVisible: boolean = false
  private initializationComplete: boolean = false
  private credentialExpiryPromptVisible: boolean = false
  private appliedDataClearGeneration: number = 0
  private postCloseRefreshGeneration: number = 0
  private lastCredentialValidationAtMillis: number = 0
  @State currentVersionLabel: string = '2.2.1'

  @State systemInfoLabel: string = 'HarmonyOS 设备'
  @State macAddressLabel: string = '系统已隐藏'

  @State projectIdText: string = ''
  @State userIdText: string = ''
  @State accountIdText: string = ''
  @State loginCodeText: string = ''
  @State telephoneText: string = ''
  @State snCodeText: string = ''
  @State configReloading: boolean = false
  @State configTransferBusy: boolean = false
  private snCodeFormDirty: boolean = false
  private snCodeEditGeneration: number = 0
  private configReloadGeneration: number = 0
  @State configSummary: string = '登录趣智校园会自动填充账号字段；首次使用仍需补充 snCode。'

  @State ringAngle: number = 0
  @State ringOpacity: number = 0.15
  @State refreshIconAngle: number = 0
  private ringAnimationRunning: boolean = false
  private ringBreathingUp: boolean = true
  @StorageProp('statusBarHeight') statusBarHeight: number = 0
  @StorageProp('bottomSafeHeight') bottomSafeHeight: number = 0
  @StorageProp('colorMode') colorMode: ColorMode = ColorMode.LIGHT
  @StorageProp('networkAvailable') networkAvailable: boolean = false
  @StorageProp('networkStatusKnown') networkStatusKnown: boolean = false
  @StorageProp('appearanceMaterialType') appearanceMaterialType: string =
    AppearanceMaterialType.ADAPTIVE
  @StorageProp('appearanceMaterialLevel') appearanceMaterialLevel: string =
    AppearanceMaterialLevel.ADAPTIVE
  @State immersiveMaterialSupported: boolean = false

  aboutToAppear(): void {
    this.pageVisible = true
    this.hostContext = this.getUIContext().getHostContext() as common.Context
    this.store = new HotWaterStore(this.hostContext)
    this.repository = new HotWaterRepository(this.store)
    this.immersiveMaterialSupported = this.supportsImmersiveMaterial()
    const completedDataClearGeneration = ConfigFlowBridge.getCompletedDataClearGeneration()
    if (completedDataClearGeneration > 0 &&
      completedDataClearGeneration !== this.appliedDataClearGeneration) {
      this.resetAfterDataCleared(completedDataClearGeneration)
    }
    this.initialize()
    this.setupImmersiveWindow()
  }

  onPageShow(): void {
    this.pageVisible = true
    const dataCleared = ConfigFlowBridge.consumeDataCleared()
    const completedDataClearGeneration = ConfigFlowBridge.getCompletedDataClearGeneration()
    if (dataCleared || (completedDataClearGeneration > 0 &&
      completedDataClearGeneration !== this.appliedDataClearGeneration)) {
      this.resetAfterDataCleared(completedDataClearGeneration)
      this.setupImmersiveWindow()
      return
    }
    this.consumePendingLaunchAction()
    const loginSkipped = ConfigFlowBridge.consumeLoginSkipped()
    if (loginSkipped) {
      this.credentialExpiryPromptVisible = false
      this.liveStatus = '已跳过登录，可在配置页导入或补充配置。'
      this.switchPage(HomePage.CONFIG)
    }
    const pendingSnCode = ConfigFlowBridge.consumePendingSnCode()
    if (pendingSnCode.length > 0) {
      this.snCodeText = pendingSnCode
      this.snCodeFormDirty = true
      this.snCodeEditGeneration++
    }
    const loginConfigChanged = ConfigFlowBridge.consumeLoginConfigChanged()
    if (loginConfigChanged) {
      this.reloadConfigAfterLogin()
    } else if (this.initializationComplete && !loginSkipped) {
      this.refreshAfterForeground()
    }
    this.setupImmersiveWindow()
    if (this.busy) {
      this.startRingAnimation()
    }
  }

  private resetAfterDataCleared(completedGeneration: number =
    ConfigFlowBridge.getCompletedDataClearGeneration()): void {
    if (completedGeneration > 0) {
      this.appliedDataClearGeneration = completedGeneration
    }
    this.postCloseRefreshGeneration++
    this.configReloadGeneration++
    this.usageTimerGeneration++
    this.busy = false
    this.refreshingBalance = false
    this.configReloading = false
    this.configTransferBusy = false
    this.initializationComplete = false
    this.credentialExpiryPromptVisible = false
    this.lastCredentialValidationAtMillis = 0
    this.ringAnimationRunning = false
    this.ringOpacity = 0.15
    this.applyConfigToForm(new HotWaterConfig())
    this.snCodeFormDirty = false
    this.configSummary = '登录趣智校园会自动填充账号字段；首次使用仍需补充 snCode。'
    this.applySnapshot(this.placeholderSnapshot(new HotWaterConfig()))
    this.consumptionRecords = []
    this.billScope = BillScope.MONTH
    this.billSelectedYear = 0
    this.billSelectedMonth = 0
    this.billGrouping = BillGrouping.MONTH
    this.collapsedBillKeys = []
    this.billCollapseInitialized = false
    this.activePage = HomePage.CONFIG
    this.liveStatus = '应用内信息已清空，请重新登录或导入配置。'
  }

  onPageHide(): void {
    this.pageVisible = false
    this.postCloseRefreshGeneration++
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
    const dataClearGeneration = ConfigFlowBridge.getDataClearGeneration()
    await this.loadCurrentVersion()
    if (!ConfigFlowBridge.isDataClearGenerationCurrent(dataClearGeneration)) {
      return
    }
    const runtimeInfo = RuntimeDeviceInfoResolver.resolve()
    this.applyRuntimeInfo(runtimeInfo)

    if (!this.store) {
      return
    }

    const config = RuntimeDeviceInfoResolver.enrichConfig(await this.store.loadConfig())
    if (!ConfigFlowBridge.isDataClearGenerationCurrent(dataClearGeneration)) {
      return
    }
    const configSaved = await this.store.saveConfig(
      config,
      () => ConfigFlowBridge.isDataClearGenerationCurrent(dataClearGeneration)
    )
    if (!configSaved || !ConfigFlowBridge.isDataClearGenerationCurrent(dataClearGeneration)) {
      return
    }
    this.applyConfigToForm(config)
    this.snCodeFormDirty = false
    this.applyConfigSummary(config)
    this.applySnapshot(this.placeholderSnapshot(config))
    await this.loadConsumptionRecords()
    if (!ConfigFlowBridge.isDataClearGenerationCurrent(dataClearGeneration)) {
      return
    }

    if (await this.store.isLoginCredentialExpired()) {
      if (!ConfigFlowBridge.isDataClearGenerationCurrent(dataClearGeneration)) {
        return
      }
      this.initializationComplete = true
      await this.handleCredentialExpired()
      this.checkForUpdates(true)
      return
    }

    const launchActionHandled = await this.consumePendingLaunchAction()
    if (!ConfigFlowBridge.isDataClearGenerationCurrent(dataClearGeneration)) {
      return
    }
    if (config.isComplete() && !launchActionHandled) {
      await this.refreshBalance(false)
      if (!ConfigFlowBridge.isDataClearGenerationCurrent(dataClearGeneration)) {
        return
      }
    }
    this.initializationComplete = true
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
    this.deviceOnline = snapshot.isDeviceOnline
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
      this.configSummary = `配置已完成\n账号: ${HotWaterConfig.maskMiddle(config.telephone, 3, 4)}\nsnCode：已隐藏`
    } else {
      this.configSummary = '登录趣智校园会自动填充账号字段；首次使用仍需补充 snCode。'
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
    this.consumptionRecords = (await this.store.loadConsumptionRecords()).slice()
    this.initializeBillCollapseDefaults()
  }

  private initializeBillCollapseDefaults(): void {
    if (this.billCollapseInitialized || this.consumptionRecords.length === 0) {
      return
    }
    this.billCollapseInitialized = true
    this.resetBillCollapseDefaults()
  }

  private resetBillCollapseDefaults(): void {
    const groups = this.getBillGroups()
    const collapsedKeys: string[] = []
    for (let index = 1; index < groups.length; index++) {
      collapsedKeys.push(groups[index].key)
    }
    this.collapsedBillKeys = collapsedKeys
  }

  private getCurrentBillEntries(): BillEntry[] {
    return buildBillEntries(this.consumptionRecords)
  }

  private getBillYears(): number[] {
    return getAvailableBillYears(this.getCurrentBillEntries())
  }

  private getActiveBillYear(): number {
    const years = this.getBillYears()
    if (years.indexOf(this.billSelectedYear) >= 0) {
      return this.billSelectedYear
    }
    if (years.length > 0) {
      return years[0]
    }
    return new Date().getFullYear()
  }

  private getActiveBillMonth(): number {
    if (this.billSelectedMonth >= 1 && this.billSelectedMonth <= 12) {
      return this.billSelectedMonth
    }
    const months = getBillMonthsForYear(this.getCurrentBillEntries(), this.getActiveBillYear())
    if (months.length > 0) {
      return months[0]
    }
    return new Date().getMonth() + 1
  }

  private getFilteredBillEntries(): BillEntry[] {
    const entries = this.getCurrentBillEntries()
    const year = this.getActiveBillYear()
    if (this.billScope === BillScope.YEAR) {
      // 年度账期不应继承月度筛选状态。
      return filterBillEntries(entries, BillScope.YEAR, year)
    }
    return filterBillEntries(entries, BillScope.MONTH, year, this.getActiveBillMonth())
  }

  private getBillSummary(): BillSummary {
    return summarizeBillEntries(this.getFilteredBillEntries())
  }

  private getBillGroups(): BillGroup[] {
    return groupBillEntries(
      this.getFilteredBillEntries(),
      this.billGrouping,
      this.collapsedBillKeys
    )
  }

  private getBillYearOptions(): SelectOption[] {
    const options: SelectOption[] = []
    const years = this.getBillYears()
    for (let index = 0; index < years.length; index++) {
      options.push({ value: years[index].toString() + '年' })
    }
    return options
  }

  private getBillMonthOptions(): SelectOption[] {
    const options: SelectOption[] = []
    for (let month = 1; month <= 12; month++) {
      options.push({ value: month.toString() + '月' })
    }
    return options
  }

  private getBillYearIndex(): number {
    return Math.max(0, this.getBillYears().indexOf(this.getActiveBillYear()))
  }

  private getBillMonthIndex(): number {
    return this.getActiveBillMonth() - 1
  }

  private setBillScope(scope: BillScope): void {
    if (this.billScope === scope) {
      return
    }
    this.billScope = scope
    if (scope === BillScope.YEAR) {
      this.billSelectedMonth = 0
    }
    this.resetBillCollapseDefaults()
  }

  private setBillGrouping(grouping: BillGrouping): void {
    if (this.billGrouping === grouping) {
      return
    }
    this.billGrouping = grouping
    this.resetBillCollapseDefaults()
  }

  private toggleBillGroup(groupKey: string): void {
    const updateCollapsedState = (): void => {
      this.collapsedBillKeys = toggleBillGroupCollapsed(this.collapsedBillKeys, groupKey)
    }
    const uiContext = this.getUIContext()
    if (!uiContext) {
      updateCollapsedState()
      return
    }
    uiContext.animateTo({
      duration: 260,
      curve: Curve.EaseInOut
    }, updateCollapsedState)
  }

  private setBillYear(year: number): void {
    this.billSelectedYear = year
    this.billSelectedMonth = 0
    this.resetBillCollapseDefaults()
  }

  private setBillMonth(month: number): void {
    this.billSelectedMonth = month
    this.resetBillCollapseDefaults()
  }

  private setBillYearByIndex(index: number): void {
    const years = this.getBillYears()
    if (index >= 0 && index < years.length) {
      this.setBillYear(years[index])
    }
  }

  private async reloadConfigAfterLogin(): Promise<void> {
    if (!this.store) {
      return
    }
    const reloadGeneration = ++this.configReloadGeneration
    this.configReloading = true
    const pendingSnCode = this.snCodeText
    const pendingSnCodeDirty = this.snCodeFormDirty
    const pendingSnCodeGeneration = this.snCodeEditGeneration
    try {
      const config = RuntimeDeviceInfoResolver.enrichConfig(await this.store.loadConfig())
      if (reloadGeneration !== this.configReloadGeneration) {
        return
      }
      const snCodeWasEditedDuringReload = this.snCodeEditGeneration !== pendingSnCodeGeneration
      const snCodeToKeep = snCodeWasEditedDuringReload ? this.snCodeText : pendingSnCode
      const snCodeDirty = snCodeWasEditedDuringReload || pendingSnCodeDirty
      this.applyConfigToForm(config)
      this.snCodeText = ConfigFlowBridge.resolveSnCodeAfterLogin(
        snCodeToKeep,
        snCodeDirty,
        config.snCode
      )
      this.snCodeFormDirty = ConfigFlowBridge.hasPendingSnCodeAfterLogin(
        snCodeToKeep,
        snCodeDirty,
        config.snCode
      )
      config.snCode = this.snCodeText
      if (this.snCodeFormDirty) {
        this.configSummary = `设备码已回填，点击“保存配置”完成绑定。\n` +
          `账号: ${HotWaterConfig.maskMiddle(config.telephone, 3, 4)}\nsnCode：已隐藏`
      } else {
        this.applyConfigSummary(config)
      }

      const snapshot = this.placeholderSnapshot(config)
      const cachedOrderNo = await this.store.loadOrderNo()
      if (reloadGeneration !== this.configReloadGeneration) {
        return
      }
      if (cachedOrderNo.length > 0) {
        snapshot.orderNo = cachedOrderNo
        snapshot.hasCachedOrder = true
        snapshot.statusLabel = '已缓存活动订单，正在同步最新状态。'
      }
      this.applySnapshot(snapshot)
      if (config.isComplete() && !this.snCodeFormDirty) {
        await this.refreshBalance(false, true)
      }
    } catch (error) {
      if (reloadGeneration === this.configReloadGeneration) {
        this.liveStatus = '登录配置已保存，但暂时无法刷新配置表单，请稍后重试。'
      }
    } finally {
      if (reloadGeneration === this.configReloadGeneration) {
        this.configReloading = false
      }
    }
  }

  private async importConfigFile(): Promise<void> {
    if (!this.hostContext || !this.store || this.configTransferBusy) {
      return
    }
    this.configTransferBusy = true
    const dataClearGeneration = ConfigFlowBridge.getDataClearGeneration()
    try {
      const options = new picker.DocumentSelectOptions()
      options.fileSuffixFilters = ['JSON 配置文件|.json']
      options.maxSelectNumber = 1
      const documentPicker = new picker.DocumentViewPicker(this.hostContext)
      const uris = await documentPicker.select(options)
      if (!uris || uris.length === 0) {
        return
      }
      const parsedConfig = RuntimeDeviceInfoResolver.enrichConfig(
        HotWaterConfigTransfer.parse(this.readConfigTextFromUri(uris[0]))
      )
      const persisted = await this.store.saveConfig(
        parsedConfig,
        () => ConfigFlowBridge.isDataClearGenerationCurrent(dataClearGeneration)
      )
      if (!persisted || !ConfigFlowBridge.isDataClearGenerationCurrent(dataClearGeneration)) {
        return
      }
      this.applyConfigToForm(parsedConfig)
      this.snCodeFormDirty = false
      this.applyConfigSummary(parsedConfig)
      this.applySnapshot(this.placeholderSnapshot(parsedConfig))
      this.liveStatus = '配置已导入。'
    } catch (error) {
      this.liveStatus = `导入配置失败：${String(error)}`
    } finally {
      this.configTransferBusy = false
    }
  }

  private async exportConfigFile(): Promise<void> {
    if (!this.hostContext || !this.store || this.configTransferBusy) {
      return
    }
    this.configTransferBusy = true
    try {
      const config = await this.store.loadConfig()
      const validationError = HotWaterConfigTransfer.validate(config)
      if (validationError.length > 0) {
        this.liveStatus = `配置不完整，暂时无法导出：${validationError}`
        return
      }
      const options = new picker.DocumentSaveOptions()
      options.newFileNames = [HotWaterConfigTransfer.generateFileName()]
      const documentPicker = new picker.DocumentViewPicker(this.hostContext)
      const uris = await documentPicker.save(options)
      if (!uris || uris.length === 0) {
        return
      }
      this.writeConfigTextToUri(
        uris[0],
        HotWaterConfigTransfer.serialize(config, Date.now(), this.currentVersionLabel)
      )
      this.liveStatus = '配置已导出。'
    } catch (error) {
      this.liveStatus = `导出配置失败：${String(error)}`
    } finally {
      this.configTransferBusy = false
    }
  }

  private readConfigTextFromUri(uri: string): string {
    const file = fs.openSync(uri, fs.OpenMode.READ_ONLY)
    const bytesBuffer = new ArrayBuffer(16 * 1024)
    const chunks: Uint8Array[] = []
    let totalSize = 0
    try {
      while (true) {
        const readSize = fs.readSync(file.fd, bytesBuffer)
        if (readSize <= 0) {
          break
        }
        totalSize += readSize
        if (totalSize > MAX_CONFIG_FILE_BYTES) {
          throw new Error('配置文件过大，最多支持 128 KB')
        }
        const chunk = new Uint8Array(readSize)
        chunk.set(new Uint8Array(bytesBuffer, 0, readSize))
        chunks.push(chunk)
      }
      return buffer.concat(chunks, totalSize).toString('utf-8')
    } finally {
      fs.closeSync(file.fd)
    }
  }

  private writeConfigTextToUri(uri: string, value: string): void {
    const file = fs.openSync(
      uri,
      fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC
    )
    try {
      fs.writeSync(file.fd, value)
    } finally {
      fs.closeSync(file.fd)
    }
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

  private async collectConfig(includeSnCodeDraft: boolean = false): Promise<HotWaterConfig> {
    const loaded = this.store ? await this.store.loadConfig() : new HotWaterConfig()
    const config = new HotWaterConfig()
    config.projectId = Number(this.projectIdText || '0')
    config.userId = Number(this.userIdText || '0')
    config.accountId = Number(this.accountIdText || '0')
    config.loginCode = this.loginCodeText.trim()
    config.telephone = this.telephoneText.trim()
    config.snCode = includeSnCodeDraft
      ? this.snCodeText.trim().replace(/:/g, '').toUpperCase()
      : loaded.snCode
    config.macAddress = loaded.macAddress
    config.phoneSystem = loaded.phoneSystem
    config.version = loaded.version
    return RuntimeDeviceInfoResolver.enrichConfig(config)
  }

  private async saveConfig(): Promise<void> {
    if (!this.store || this.configReloading) {
      return
    }
    const dataClearGeneration = ConfigFlowBridge.getDataClearGeneration()
    const config = await this.collectConfig(true)
    const persisted = await this.store.saveConfig(
      config,
      () => ConfigFlowBridge.isDataClearGenerationCurrent(dataClearGeneration)
    )
    if (!persisted || !ConfigFlowBridge.isDataClearGenerationCurrent(dataClearGeneration)) {
      return
    }
    this.snCodeFormDirty = false
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

  private triggerHaptic(duration: number = 35): void {
    vibrator.startVibration({ type: 'time', duration: duration }, { id: 0, usage: 'touch' })
      .catch(() => {
      })
  }

  private refreshAfterForeground(): void {
    const nowMillis = Date.now()
    if (!this.pageVisible || this.busy || this.refreshingBalance || this.configReloading ||
      this.credentialExpiryPromptVisible ||
      nowMillis - this.lastCredentialValidationAtMillis < FOREGROUND_VALIDATION_MIN_INTERVAL_MS) {
      return
    }
    this.refreshBalance(false)
  }

  private async handleCredentialExpired(): Promise<void> {
    if (this.credentialExpiryPromptVisible) {
      return
    }
    this.credentialExpiryPromptVisible = true
    this.postCloseRefreshGeneration++
    this.loginCodeText = ''
    this.liveStatus = '凭证已过期，请重新登录。'
    try {
      await this.store?.invalidateLoginCredential()
    } catch (error) {
    }

    try {
      AlertDialog.show({
        title: '凭证已过期',
        message: '登录凭证已失效，请重新登录趣智校园。',
        autoCancel: false,
        primaryButton: {
          value: '重新登录',
          action: () => {
            this.credentialExpiryPromptVisible = false
            this.openLogin()
          }
        }
      })
    } catch (error) {
      this.credentialExpiryPromptVisible = false
      await this.openLogin()
    }
  }

  private schedulePostCloseReconciliation(): void {
    const generation = ++this.postCloseRefreshGeneration
    this.runPostCloseReconciliation(generation, 0)
  }

  private runPostCloseReconciliation(generation: number, attempt: number): void {
    setTimeout(async () => {
      if (generation !== this.postCloseRefreshGeneration || !this.pageVisible || this.busy ||
        this.hasCachedOrder || this.credentialExpiryPromptVisible) {
        return
      }
      await this.refreshBalance(false)
      if (generation !== this.postCloseRefreshGeneration || !this.pageVisible || !this.store ||
        attempt + 1 >= POST_CLOSE_RECONCILE_ATTEMPTS) {
        return
      }
      const pendingSettlements = await this.store.loadPendingSettlements()
      if (pendingSettlements.length > 0) {
        this.runPostCloseReconciliation(generation, attempt + 1)
      }
    }, POST_CLOSE_RECONCILE_DELAY_MS)
  }

  private async refreshBalance(
    showStatus: boolean = true,
    allowDuringConfigReload: boolean = false
  ): Promise<void> {
    if (!this.repository || !this.store || this.busy || this.refreshingBalance ||
      (this.configReloading && !allowDuringConfigReload)) {
      return
    }
    const config = await this.collectConfig()
    if (!config.isComplete()) {
      this.switchPage(HomePage.CONFIG)
      this.liveStatus = '配置还不完整，请登录趣智校园或补全字段。'
      return
    }
    this.refreshingBalance = true
    this.lastCredentialValidationAtMillis = Date.now()
    this.runRefreshIconAnimation()
    if (showStatus) {
      this.liveStatus = '正在刷新余额…'
    }
    try {
      const result = await this.repository.check(config)
      this.applySnapshot(result.snapshot ?? this.placeholderSnapshot(config))
      await this.loadConsumptionRecords()
      this.liveStatus = result.summary.split('\n')[0]
    } catch (error) {
      if (HotWaterPayload.isCredentialExpiredError(error)) {
        await this.handleCredentialExpired()
        return
      }
      this.liveStatus = `刷新失败：${String(error)}`
    } finally {
      this.refreshingBalance = false
    }
  }

  private async toggleHotWater(skipLowBalanceWarning: boolean = false): Promise<void> {
    if (!this.repository || this.configReloading) {
      return
    }
    const config = await this.collectConfig()
    if (!config.isComplete()) {
      this.switchPage(HomePage.CONFIG)
      this.liveStatus = '配置还不完整，请登录趣智校园或补全字段。'
      return
    }
    if (!skipLowBalanceWarning && this.shouldShowLowBalanceOpenWarning()) {
      this.showLowBalanceDialog()
      return
    }
    const closingHotWater = this.hasCachedOrder
    this.postCloseRefreshGeneration++
    this.busy = true
    this.liveStatus = closingHotWater ? '正在关闭热水…' : '正在开启热水…'
    this.startRingAnimation()
    try {
      const result = closingHotWater ? await this.repository.close(config) : await this.repository.open(config)
      this.applySnapshot(result.snapshot ?? this.placeholderSnapshot(config))
      await this.loadConsumptionRecords()
      this.liveStatus = result.summary.split('\n')[0]
      if (closingHotWater && result.usageDurationMillis >= 0) {
        this.schedulePostCloseReconciliation()
      }
    } catch (error) {
      if (HotWaterPayload.isCredentialExpiredError(error)) {
        await this.handleCredentialExpired()
        return
      }
      this.liveStatus = `操作失败：${String(error)}`
    } finally {
      this.busy = false
      this.ringAnimationRunning = false
      this.ringOpacity = 0.15
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

  private startRingAnimation(): void {
    if (!this.busy || this.ringAnimationRunning) {
      return
    }
    this.ringAnimationRunning = true
    this.ringBreathingUp = true
    this.ringOpacity = 0.15
    this.runRingRotation()
    this.runRingBreath()
  }

  private runRingRotation(): void {
    if (!this.busy || !this.ringAnimationRunning) {
      return
    }
    this.getUIContext()?.animateTo({
      duration: 1500,
      curve: Curve.Ease,
      onFinish: () => {
        this.runRingRotation()
      }
    }, () => {
      this.ringAngle += 360
    })
  }

  private runRingBreath(): void {
    if (!this.busy || !this.ringAnimationRunning) {
      return
    }
    const targetOpacity = this.ringBreathingUp ? 0.35 : 0.15
    this.getUIContext()?.animateTo({
      duration: 750,
      curve: Curve.EaseInOut,
      onFinish: () => {
        this.ringBreathingUp = !this.ringBreathingUp
        this.runRingBreath()
      }
    }, () => {
      this.ringOpacity = targetOpacity
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

  private async consumePendingLaunchAction(): Promise<boolean> {
    if (this.consumingLaunchAction) {
      return false
    }
    this.consumingLaunchAction = true
    let handled = false
    try {
      let action = LaunchActionBridge.consumePendingAction()
      while (action.length > 0) {
        handled = true
        await this.handleLaunchAction(action)
        action = LaunchActionBridge.consumePendingAction()
      }
    } finally {
      this.consumingLaunchAction = false
    }
    return handled
  }

  private async handleLaunchAction(action: string): Promise<void> {
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

  private async loadCurrentVersion(): Promise<void> {
    try {
      const bundleInfo = await bundleManager.getBundleInfoForSelf(
        bundleManager.BundleFlag.GET_BUNDLE_INFO_DEFAULT
      )
      if (bundleInfo.versionName.length > 0) {
        this.currentVersionLabel = bundleInfo.versionName
      }
    } catch (error) {
      this.currentVersionLabel = '2.2.1'
    }
  }

  private async openAbout(): Promise<void> {
    try {
      await this.getUIContext().getRouter().pushUrl({ url: 'pages/About' })
    } catch (error) {
      this.liveStatus = '暂时无法打开更多页面。'
    }
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
            .fontSize(26)
            .fontWeight(FontWeight.Bold)
            .fontColor($r('app.color.text_primary'))
          if (this.shouldShowConnectionStatus()) {
            Row({ space: 6 }) {
              Text('●')
                .fontSize(12)
                .fontColor(!this.networkAvailable
                  ? $r('app.color.widget_status_error')
                  : (this.deviceOnline === true
                    ? $r('app.color.widget_status_open')
                    : $r('app.color.widget_status_close')))
              Text(this.getConnectionStatusLabel())
                .fontSize(13)
                .fontWeight(FontWeight.Medium)
                .fontColor(!this.networkAvailable
                  ? $r('app.color.widget_status_error')
                  : (this.deviceOnline === true
                    ? $r('app.color.widget_status_open')
                    : $r('app.color.widget_status_close')))
            }
            .alignItems(VerticalAlign.Center)
            .accessibilityText(this.getConnectionStatusLabel())
          }
          Text(this.activePage === HomePage.OPERATIONS
            ? '用水控制'
            : (this.activePage === HomePage.RECORDS ? '账单' : '设备与账号'))
            .fontSize(13)
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
          .accessibilityText('更多')
          .onClick(() => {
            this.openAbout()
          })
      }
      .width('100%')
      .alignItems(VerticalAlign.Top)
    }
    .width('100%')
    .padding({ top: this.statusBarHeight > 0 ? this.statusBarHeight : 0 })
  }

  private shouldShowConnectionStatus(): boolean {
    return this.networkStatusKnown && (!this.networkAvailable || this.deviceOnline !== null)
  }

  private getConnectionStatusLabel(): string {
    if (!this.networkAvailable) {
      return '用户未联网'
    }
    return this.deviceOnline === true ? '热水器在线' : '热水器不在线'
  }

  private supportsImmersiveMaterial(): boolean {
    try {
      const supportedTypes = hdsMaterial.getSystemMaterialTypes()
      return supportedTypes.indexOf(hdsMaterial.MaterialType.IMMERSIVE) >= 0
    } catch (error) {
      return false
    }
  }

  private resolveBottomMaterialType(): hdsMaterial.MaterialType {
    if (this.appearanceMaterialType === AppearanceMaterialType.NONE) {
      return hdsMaterial.MaterialType.NONE
    }
    if (this.appearanceMaterialType === AppearanceMaterialType.IMMERSIVE &&
      this.immersiveMaterialSupported) {
      return hdsMaterial.MaterialType.IMMERSIVE
    }
    return hdsMaterial.MaterialType.ADAPTIVE
  }

  private resolveBottomMaterialLevel(): hdsMaterial.MaterialLevel {
    switch (this.appearanceMaterialLevel) {
      case AppearanceMaterialLevel.EXQUISITE:
        return hdsMaterial.MaterialLevel.EXQUISITE
      case AppearanceMaterialLevel.GENTLE:
        return hdsMaterial.MaterialLevel.GENTLE
      case AppearanceMaterialLevel.SMOOTH:
        return hdsMaterial.MaterialLevel.SMOOTH
      default:
        return hdsMaterial.MaterialLevel.ADAPTIVE
    }
  }

  @Builder
  private FieldInput(params: FieldInputParams): void {
    Column({ space: 6 }) {
      Row() {
        Text(params.label)
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_primary'))
          .layoutWeight(1)
        Text(params.description)
          .fontSize(12)
          .fontColor($r('app.color.text_secondary'))
      }
      .width('100%')

      TextInput({ text: params.text, placeholder: params.placeholder })
        .type(params.inputType ?? (params.isSensitive ? InputType.Password : InputType.Normal))
        .showPasswordIcon(params.isSensitive === true)
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
    .width('100%')
  }

  private async openLogin(): Promise<void> {
    if (this.configReloading) {
      return
    }
    try {
      await this.getUIContext().getRouter().pushUrl({ url: 'pages/Login' })
    } catch (error) {
      this.liveStatus = '暂时无法打开登录页面。'
    }
  }

  private async openScanner(): Promise<void> {
    if (this.configReloading) {
      return
    }
    try {
      await this.getUIContext().getRouter().pushUrl({ url: 'pages/Scanner' })
    } catch (error) {
      this.liveStatus = '暂时无法打开扫码页面。'
    }
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

          Column({ space: 8 }) {
            Stack() {
              Button('刷新')
                .type(ButtonType.Capsule)
                .width('100%')
                .height(36)
                .fontSize(13)
                .backgroundColor($r('app.color.button_soft_background'))
                .fontColor(this.refreshingBalance ? '#00FFFFFF' : $r('app.color.button_soft_text'))
                .enabled(!this.refreshingBalance && !this.busy && !this.configReloading)
                .onClick(() => {
                  this.triggerHaptic()
                  this.refreshBalance(true)
                })

              if (this.refreshingBalance) {
                LoadingProgress()
                  .width(24)
                  .height(24)
                  .color($r('app.color.accent'))
              }
            }

            Button('充值')
              .type(ButtonType.Capsule)
              .width('100%')
              .height(36)
              .fontSize(13)
              .backgroundColor($r('app.color.accent_button_background'))
              .fontColor($r('app.color.accent_button_text'))
              .enabled(!this.busy && !this.configReloading)
              .onClick(() => {
                this.triggerHaptic()
                this.openAlipayForRecharge()
              })
          }
          .width(72)
        }
        .width('100%')

        if (this.hasCachedOrder && this.usageStartedAtMillis > 0) {
          Text(`开启时长 ${this.formatUsageDuration(this.usageElapsedMillis)}`)
            .fontSize(13)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.accent_text'))
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
              .width(260)
              .height(260)
              .fill('rgba(0,0,0,0)')
              .stroke(this.hasCachedOrder ? $r('app.color.close_accent') : $r('app.color.accent'))
              .strokeWidth(12)
              .strokeDashArray([42, 36])
              .strokeLineCap(LineCapStyle.Round)
              .opacity(this.ringOpacity)
              .rotate({ angle: this.ringAngle })
          }

          Button(this.hasCachedOrder ? '关' : '开')
            .type(ButtonType.Circle)
            .width(204)
            .height(204)
            .borderRadius(102)
            .fontSize(56)
            .fontWeight(FontWeight.Bold)
            .backgroundColor(this.hasCachedOrder ? $r('app.color.power_button_close_background') : $r('app.color.power_button_open_background'))
            .fontColor($r('app.color.power_button_text'))
            .enabled(!this.busy && !this.refreshingBalance && !this.configReloading)
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
              this.triggerHaptic(55)
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
    .padding({ bottom: 96 + this.bottomSafeHeight })
  }

  @Builder
  private RecordsPage(): void {
    Column({ space: 14 }) {
      Column({ space: 4 }) {
        Text('账单')
          .fontSize(22)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.text_primary'))
        Text('按结算时间查看月度或年度用水汇总。')
          .fontSize(13)
          .fontColor($r('app.color.text_secondary'))
      }
      .width('100%')

      if (this.getCurrentBillEntries().length === 0) {
        Column() {
          Text('暂无账单')
            .fontSize(16)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_primary'))
          Text('关闭一次热水后，消费金额和用水时长会显示在这里。')
            .fontSize(13)
            .fontColor($r('app.color.text_secondary'))
            .margin({ top: 8 })
        }
        .width('100%')
        .padding(20)
        .borderRadius(16)
        .backgroundColor($r('app.color.card_background'))
        .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
        .border({ width: 1, color: $r('app.color.card_border'), style: BorderStyle.Solid })
      } else {
        this.BillFilterPanel()
        this.BillSummaryPanel()
        if (this.getFilteredBillEntries().length === 0) {
          this.EmptyBillPeriodState()
        } else {
          ForEach(this.getBillGroups(), (group: BillGroup) => {
            this.BillGroupCard(group)
          }, (group: BillGroup) => getBillGroupRenderKey(
            group,
            this.billScope,
            this.getActiveBillYear(),
            this.billScope === BillScope.MONTH ? this.getActiveBillMonth() : 0,
            this.billGrouping
          ))
        }
      }
    }
    .width('100%')
    .padding({ bottom: 96 + this.bottomSafeHeight })
  }

  @Builder
  private BillFilterPanel(): void {
    Column({ space: 12 }) {
      Text('账期筛选')
        .fontSize(14)
        .fontWeight(FontWeight.Medium)
        .fontColor($r('app.color.text_primary'))

      Row({ space: 8 }) {
        Button('年度')
          .layoutWeight(1)
          .type(ButtonType.Capsule)
          .fontSize(13)
          .fontColor(this.billScope === BillScope.YEAR ? $r('app.color.accent_button_text') : $r('app.color.button_soft_text'))
          .backgroundColor(this.billScope === BillScope.YEAR ? $r('app.color.accent_button_background') : $r('app.color.button_soft_background'))
          .onClick(() => {
            this.setBillScope(BillScope.YEAR)
          })
        Button('月度')
          .layoutWeight(1)
          .type(ButtonType.Capsule)
          .fontSize(13)
          .fontColor(this.billScope === BillScope.MONTH ? $r('app.color.accent_button_text') : $r('app.color.button_soft_text'))
          .backgroundColor(this.billScope === BillScope.MONTH ? $r('app.color.accent_button_background') : $r('app.color.button_soft_background'))
          .onClick(() => {
            this.setBillScope(BillScope.MONTH)
          })
      }
      .width('100%')

      Row({ space: 8 }) {
        Select(this.getBillYearOptions())
          .selected(this.getBillYearIndex())
          .value(this.getActiveBillYear().toString() + '年')
          .fontColor($r('app.color.text_primary'))
          .backgroundColor($r('app.color.input_background'))
          .border({ width: 1, color: $r('app.color.input_border'), style: BorderStyle.Solid })
          .borderRadius(12)
          .layoutWeight(1)
          .onSelect((index: number) => {
            this.setBillYearByIndex(index)
          })
        if (this.billScope === BillScope.MONTH) {
          Select(this.getBillMonthOptions())
            .selected(this.getBillMonthIndex())
            .value(this.getActiveBillMonth().toString() + '月')
            .fontColor($r('app.color.text_primary'))
            .backgroundColor($r('app.color.input_background'))
            .border({ width: 1, color: $r('app.color.input_border'), style: BorderStyle.Solid })
            .borderRadius(12)
            .layoutWeight(1)
            .onSelect((index: number) => {
              this.setBillMonth(index + 1)
            })
        }
      }
      .width('100%')

      Text('折叠方式')
        .fontSize(12)
        .fontColor($r('app.color.text_secondary'))
      Row({ space: 8 }) {
        Button('按年')
          .layoutWeight(1)
          .type(ButtonType.Capsule)
          .fontSize(13)
          .fontColor(this.billGrouping === BillGrouping.YEAR ? $r('app.color.accent_button_text') : $r('app.color.button_soft_text'))
          .backgroundColor(this.billGrouping === BillGrouping.YEAR ? $r('app.color.accent_button_background') : $r('app.color.button_soft_background'))
          .onClick(() => {
            this.setBillGrouping(BillGrouping.YEAR)
          })
        Button('按月')
          .layoutWeight(1)
          .type(ButtonType.Capsule)
          .fontSize(13)
          .fontColor(this.billGrouping === BillGrouping.MONTH ? $r('app.color.accent_button_text') : $r('app.color.button_soft_text'))
          .backgroundColor(this.billGrouping === BillGrouping.MONTH ? $r('app.color.accent_button_background') : $r('app.color.button_soft_background'))
          .onClick(() => {
            this.setBillGrouping(BillGrouping.MONTH)
          })
      }
      .width('100%')
    }
    .width('100%')
    .padding(16)
    .borderRadius(16)
    .backgroundColor($r('app.color.card_background'))
    .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
    .border({ width: 1, color: $r('app.color.card_border'), style: BorderStyle.Solid })
  }

  @Builder
  private BillSummaryPanel(): void {
    Column({ space: 12 }) {
      Text(this.billScope === BillScope.YEAR
        ? `${this.getActiveBillYear()} 年度汇总`
        : `${this.getActiveBillYear()}年${this.getActiveBillMonth()}月汇总`)
        .fontSize(16)
        .fontWeight(FontWeight.Medium)
        .fontColor($r('app.color.text_primary'))

      Row({ space: 12 }) {
        Column({ space: 3 }) {
          Text('金额合计')
            .fontSize(12)
            .fontColor($r('app.color.text_secondary'))
          Text(formatBillAmount(this.getBillSummary().totalMilliYuan))
            .fontSize(22)
            .fontWeight(FontWeight.Bold)
            .fontColor($r('app.color.error_text'))
        }
        .layoutWeight(1)
        Column({ space: 3 }) {
          Text('用水时长')
            .fontSize(12)
            .fontColor($r('app.color.text_secondary'))
          Text(formatBillDuration(this.getBillSummary().totalDurationMillis))
            .fontSize(22)
            .fontWeight(FontWeight.Bold)
            .fontColor($r('app.color.text_primary'))
        }
        .layoutWeight(1)
      }
      .width('100%')

      Text(`${this.getBillSummary().recordCount} 笔用水，已结算 ${this.getBillSummary().settledCount} 笔`)
        .fontSize(12)
        .fontColor($r('app.color.text_secondary'))
      if (this.getBillSummary().pendingCount > 0) {
        Text(`${this.getBillSummary().pendingCount} 笔金额待同步，未计入金额合计`)
          .fontSize(12)
          .fontColor($r('app.color.close_accent'))
      }
    }
    .width('100%')
    .padding(16)
    .borderRadius(16)
    .backgroundColor($r('app.color.card_background'))
    .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
    .border({ width: 1, color: $r('app.color.card_border'), style: BorderStyle.Solid })
  }

  @Builder
  private EmptyBillPeriodState(): void {
    Column({ space: 6 }) {
      Text('该账期暂无用水记录')
        .fontSize(16)
        .fontWeight(FontWeight.Medium)
        .fontColor($r('app.color.text_primary'))
      Text('可切换年份或月份查看其他账期。')
        .fontSize(13)
        .fontColor($r('app.color.text_secondary'))
    }
    .width('100%')
    .padding(20)
    .borderRadius(16)
    .backgroundColor($r('app.color.card_background'))
    .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
    .border({ width: 1, color: $r('app.color.card_border'), style: BorderStyle.Solid })
  }

  @Builder
  private BillGroupCard(group: BillGroup): void {
    Column({ space: 0 }) {
      Button({ type: ButtonType.Normal }) {
        Row() {
          Column({ space: 3 }) {
            Text(group.label)
              .fontSize(16)
              .fontWeight(FontWeight.Medium)
              .fontColor($r('app.color.text_primary'))
            Text(`${group.summary.recordCount} 笔 · ${formatBillAmount(group.summary.totalMilliYuan)}`)
              .fontSize(12)
              .fontColor($r('app.color.text_secondary'))
          }
          .alignItems(HorizontalAlign.Start)
          .layoutWeight(1)
          Stack({ alignContent: Alignment.Center }) {
            SymbolGlyph($r('sys.symbol.chevron_backward'))
              .fontSize(18)
              .fontColor([$r('app.color.text_secondary')])
          }
          .width(24)
          .height(24)
          .rotate({
            angle: this.collapsedBillKeys.indexOf(group.key) >= 0 ? 180 : 270,
            centerX: '50%',
            centerY: '50%'
          })
          .animation({ duration: 260, curve: Curve.EaseInOut })
        }
        .width('100%')
        .padding({ left: 16, right: 16, top: 12, bottom: 12 })
      }
      .width('100%')
      .padding(0)
      .borderRadius(0)
      .backgroundColor(Color.Transparent)
      .stateEffect(true)
      .onClick(() => {
        this.toggleBillGroup(group.key)
      })

      if (this.collapsedBillKeys.indexOf(group.key) < 0) {
        Column({ space: 0 }) {
          Divider()
            .width('100%')
            .color($r('app.color.card_border'))
            .strokeWidth(1)
            .margin({ left: 16, right: 16 })
          ForEach(group.entries, (entry: BillEntry, index: number) => {
            if (index > 0) {
              Divider()
                .width('100%')
                .color($r('app.color.card_border'))
                .strokeWidth(1)
                .margin({ left: 16, right: 16 })
            }
            this.BillRecordCard(entry)
          }, (entry: BillEntry) => entry.record.id.toString())
        }
        .width('100%')
        .clip(true)
        .transition(
            TransitionEffect.asymmetric(
            TransitionEffect.OPACITY
              .combine(TransitionEffect.translate({ x: 0, y: -6 }))
              .animation({ duration: 240, curve: Curve.EaseOut }),
            TransitionEffect.OPACITY
              .combine(TransitionEffect.translate({ x: 0, y: -4 }))
              .animation({ duration: 180, curve: Curve.EaseIn })
            )
          )
      }
    }
    .width('100%')
    .clip(true)
    .borderRadius(16)
    .backgroundColor($r('app.color.card_background'))
    .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
    .border({ width: 1, color: $r('app.color.card_border'), style: BorderStyle.Solid })
    .shadow({
      radius: 10,
      color: $r('app.color.card_shadow'),
      offsetX: 0,
      offsetY: 3
    })
  }

  @Builder
  private BillRecordCard(entry: BillEntry): void {
    Column({ space: 6 }) {
      Row() {
        Column({ space: 3 }) {
          Text('热水消费')
            .fontSize(15)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_primary'))
          Text(`开始于 ${entry.record.getStartedAtLabel()}`)
            .fontSize(12)
            .fontColor($r('app.color.text_secondary'))
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)
        Text(entry.record.consumptionYuan >= 0 ? entry.record.getConsumptionLabel() : '待同步')
          .fontSize(17)
          .fontWeight(FontWeight.Bold)
          .fontColor(entry.record.consumptionYuan >= 0 ? $r('app.color.error_text') : $r('app.color.close_accent'))
      }
      .width('100%')
      Row() {
        Text('用水时长')
          .fontSize(12)
          .fontColor($r('app.color.text_secondary'))
          .layoutWeight(1)
        Text(entry.record.getDurationLabel())
          .fontSize(12)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_primary'))
      }
      .width('100%')
    }
    .width('100%')
    .padding({ left: 16, right: 16, top: 14, bottom: 14 })
  }

  @Builder
  private ConfigPage(): void {
    Column({ space: 16 }) {
      Column({ space: 12 }) {
        Row() {
          Column({ space: 4 }) {
            Text('账户配置')
              .fontSize(18)
              .fontWeight(FontWeight.Bold)
              .fontColor($r('app.color.text_primary'))
            Text('登录趣智校园可自动填充账号字段；也可手动补全以下字段。')
              .fontSize(13)
              .fontColor($r('app.color.text_secondary'))
          }
          .layoutWeight(1)

          Text('必填')
            .fontSize(12)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.accent_text'))
            .backgroundColor($r('app.color.status_chip_background'))
            .borderRadius(12)
            .padding({ left: 10, right: 10, top: 5, bottom: 5 })
        }
        .width('100%')

        Text('登录后自动填入项目、用户、账户和登录凭据；密码只用于本次请求。')
          .fontSize(13)
          .fontColor($r('app.color.text_secondary'))

        Button('登录趣智校园')
          .type(ButtonType.Capsule)
          .width('100%')
          .backgroundColor($r('app.color.accent_button_background'))
          .fontColor($r('app.color.accent_button_text'))
          .enabled(!this.busy && !this.configReloading)
          .onClick(() => {
            this.triggerHaptic()
            this.openLogin()
          })

        this.FieldInput({
          label: '项目 ID',
          description: 'projectId',
          text: this.projectIdText,
          placeholder: 'projectId',
          inputType: InputType.NUMBER_PASSWORD,
          isSensitive: true,
          onChange: (value: string) => {
            this.projectIdText = value
          }
        })
        this.FieldInput({
          label: '用户 ID',
          description: 'userId',
          text: this.userIdText,
          placeholder: 'userId',
          inputType: InputType.NUMBER_PASSWORD,
          isSensitive: true,
          onChange: (value: string) => {
            this.userIdText = value
          }
        })
        this.FieldInput({
          label: '账户 ID',
          description: 'accountId',
          text: this.accountIdText,
          placeholder: 'accountId',
          inputType: InputType.NUMBER_PASSWORD,
          isSensitive: true,
          onChange: (value: string) => {
            this.accountIdText = value
          }
        })
        this.FieldInput({
          label: '登录凭据',
          description: 'loginCode',
          text: this.loginCodeText,
          placeholder: 'loginCode',
          isSensitive: true,
          onChange: (value: string) => {
            this.loginCodeText = value
          }
        })
        this.FieldInput({
          label: '手机号',
          description: 'telephone',
          text: this.telephoneText,
          placeholder: 'telephone',
          inputType: InputType.NUMBER_PASSWORD,
          isSensitive: true,
          onChange: (value: string) => {
            this.telephoneText = value
          }
        })
        Row({ space: 10 }) {
          Column() {
            this.FieldInput({
              label: '设备序列号',
              description: 'snCode',
              text: this.snCodeText,
              placeholder: 'snCode',
              isSensitive: true,
              onChange: (value: string) => {
                this.snCodeText = value
                this.snCodeFormDirty = true
                this.snCodeEditGeneration++
              }
            })
          }
          .layoutWeight(1)
          Button({ type: ButtonType.Circle }) {
            SymbolGlyph($r('sys.symbol.qrcode'))
              .fontSize(23)
              .fontColor([$r('app.color.text_primary')])
          }
          .width(52)
          .height(52)
          .backgroundColor($r('app.color.input_background'))
          .enabled(!this.busy && !this.configReloading)
          .accessibilityText('扫描设备二维码')
          .onClick(() => this.openScanner())
        }
        .width('100%')
        .alignItems(VerticalAlign.Bottom)

        Column({ space: 4 }) {
          Text('配置状态')
            .fontSize(12)
            .fontColor($r('app.color.text_secondary'))
          Text(this.configSummary)
            .fontSize(13)
            .fontColor($r('app.color.text_secondary'))
        }
        .width('100%')
        .padding(14)
        .borderRadius(16)
        .backgroundColor($r('app.color.input_background'))

        Button('保存配置')
          .type(ButtonType.Capsule)
          .width('100%')
          .backgroundColor($r('app.color.accent_button_background'))
          .fontColor($r('app.color.accent_button_text'))
          .enabled(!this.busy && !this.configReloading && !this.configTransferBusy)
          .onClick(() => {
            this.triggerHaptic(40)
             this.saveConfig()
           })
        Row({ space: 12 }) {
          Button('导出配置')
            .layoutWeight(1)
            .type(ButtonType.Capsule)
            .backgroundColor($r('app.color.button_soft_background'))
            .fontColor($r('app.color.button_soft_text'))
            .enabled(!this.busy && !this.configReloading && !this.configTransferBusy)
            .onClick(() => {
              this.triggerHaptic()
              this.exportConfigFile()
            })

          Button('导入配置')
            .layoutWeight(1)
            .type(ButtonType.Capsule)
            .backgroundColor($r('app.color.button_soft_background'))
            .fontColor($r('app.color.button_soft_text'))
            .enabled(!this.busy && !this.configReloading && !this.configTransferBusy)
            .onClick(() => {
              this.triggerHaptic()
              this.importConfigFile()
            })
        }
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

      Column({ space: 8 }) {
        Text('设备信息')
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.text_primary'))

        Text('系统信息')
          .fontSize(12)
          .fontColor($r('app.color.text_secondary'))
          .margin({ top: 4 })
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
      .width('100%')
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
    .padding({ bottom: 96 + this.bottomSafeHeight })
  }

  private handlePageChange(index: number): void {
    if (index !== this.activePage) {
      this.triggerHaptic()
    }
    this.activePage = index
    if (index === HomePage.RECORDS) {
      this.loadConsumptionRecords()
    }
  }

  build() {
    HdsTabs({ index: this.activePage }) {
      TabContent() {
        Column({ space: 8 }) {
          this.TopHeader()
          Scroll() {
            this.OperationsPage()
          }
          .scrollBar(BarState.Off)
          .layoutWeight(1)
          .width('100%')
          .edgeEffect(EdgeEffect.Spring)
        }
        .width('100%')
        .height('100%')
        .padding({ left: 16, right: 16 })
      }
        .tabBar(
          BottomTabBarStyle.of({
            normal: new SymbolGlyphModifier($r('sys.symbol.drop'))
          }, '用水')
            .layoutMode(LayoutMode.VERTICAL)
            .verticalAlign(VerticalAlign.Center)
            .padding({ left: 8, right: 8, top: 4, bottom: 4 })
            .iconStyle({
              selectedColor: $r('app.color.accent_text'),
              unselectedColor: $r('app.color.text_secondary')
            })
            .labelStyle({
              selectedColor: $r('app.color.accent_text'),
              unselectedColor: $r('app.color.text_secondary')
            })
        )

      TabContent() {
        Column({ space: 8 }) {
          this.TopHeader()
          Scroll() {
            this.RecordsPage()
          }
          .scrollBar(BarState.Off)
          .layoutWeight(1)
          .width('100%')
          .edgeEffect(EdgeEffect.Spring)
        }
        .width('100%')
        .height('100%')
        .padding({ left: 16, right: 16 })
      }
        .tabBar(
          BottomTabBarStyle.of({
            normal: new SymbolGlyphModifier($r('sys.symbol.list_square_bill'))
          }, '账单')
            .layoutMode(LayoutMode.VERTICAL)
            .verticalAlign(VerticalAlign.Center)
            .padding({ left: 8, right: 8, top: 4, bottom: 4 })
            .iconStyle({
              selectedColor: $r('app.color.accent_text'),
              unselectedColor: $r('app.color.text_secondary')
            })
            .labelStyle({
              selectedColor: $r('app.color.accent_text'),
              unselectedColor: $r('app.color.text_secondary')
            })
        )

      TabContent() {
        Column({ space: 8 }) {
          this.TopHeader()
          Scroll() {
            this.ConfigPage()
          }
          .scrollBar(BarState.Off)
          .layoutWeight(1)
          .width('100%')
          .edgeEffect(EdgeEffect.Spring)
        }
        .width('100%')
        .height('100%')
        .padding({ left: 16, right: 16 })
      }
        .tabBar(
          BottomTabBarStyle.of({
            normal: new SymbolGlyphModifier($r('sys.symbol.person_badge_gearshape'))
          }, '配置')
            .layoutMode(LayoutMode.VERTICAL)
            .verticalAlign(VerticalAlign.Center)
            .padding({ left: 8, right: 8, top: 4, bottom: 4 })
            .iconStyle({
              selectedColor: $r('app.color.accent_text'),
              unselectedColor: $r('app.color.text_secondary')
            })
            .labelStyle({
              selectedColor: $r('app.color.accent_text'),
              unselectedColor: $r('app.color.text_secondary')
            })
        )
    }
    .vertical(false)
    .barPosition(BarPosition.End)
    .barMode(BarMode.Fixed)
    .barHeight(64)
    .barOverlap(true)
    .blurStrategy(BlurStrategy.ENABLE)
    .divider({ mode: DividerMode.NONE })
    .barFloatingStyle({
      barSideMargin: 10,
      barBottomMargin: 28,
      lightColor: $r('app.color.accent'),
      gradientMask: {
        maskColor: $r('app.color.app_background'),
        maskHeight: 92
      },
      systemMaterialEffect: {
        materialType: this.resolveBottomMaterialType(),
        materialLevel: this.resolveBottomMaterialLevel()
      },
      thermoCtrl: true,
      barOpacity: 0.96
    })
    .animationDuration(300)
    .onChange((index: number) => {
      this.handlePageChange(index)
    })
    .width('100%')
    .height('100%')
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM])
    .backgroundColor($r('app.color.app_background'))
  }
}
