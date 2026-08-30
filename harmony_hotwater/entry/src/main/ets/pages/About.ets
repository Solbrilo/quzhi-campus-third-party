import bundleManager from '@ohos.bundle.bundleManager'
import type common from '@ohos.app.ability.common'
import { ConfigurationConstant, Want } from '@kit.AbilityKit'
import { uiMaterial } from '@kit.ArkUI'
import { hdsMaterial } from '@kit.UIDesignKit'
import {
  GiteeUpdateRepository,
  HarmonyReleaseLog,
  ReleaseLogFetchResult,
  UpdateCheckState
} from '../common/AppUpdate'
import {
  AppAppearance,
  AppAppearanceStore,
  AppearanceMaterialLevel,
  AppearanceMaterialType,
  AppearanceThemeMode,
  publishAppearance
} from '../common/AppAppearance'
import { ConfigFlowBridge } from '../common/ConfigFlowBridge'
import {
  FeedbackCategory,
  FeedbackClient,
  FeedbackSubmitResult,
  FeedbackSubmitState
} from '../common/FeedbackClient'
import { FeedbackHistoryEntry, FeedbackHistoryStore } from '../common/FeedbackHistoryStore'
import { HotWaterStore } from '../common/HotWaterStore'
import { OnboardingStore } from '../common/OnboardingStore'

const PROJECT_REPOSITORY_URL: string = 'https://gitee.com/Solbrilo/quzhi-campus-third-party'
const SOLBRILO_URL: string = 'https://gitee.com/Solbrilo'
const NCYOIGG_URL: string = 'https://gitee.com/NCYOIGG'
const SOLBRILO_AVATAR_URL: string =
  'https://foruda.gitee.com/avatar/1746673105091619288/15502997_solbrilo_1746673105.png'
const NCYOIGG_AVATAR_URL: string =
  'https://foruda.gitee.com/avatar/1787055328183752892/14964913_lazyoigg_1787055328.png'

enum MoreScreen {
  HOME = 0,
  FEEDBACK = 1,
  PRIVACY = 2,
  CREDITS = 3
}

@CustomDialog
@Component
struct FeedbackHistoryDialog {
  controller?: CustomDialogController
  entries: FeedbackHistoryEntry[] = []
  onClose: () => void = () => {
  }

  private categoryLabel(category: string): string {
    switch (category) {
      case FeedbackCategory.BUG:
        return '功能问题'
      case FeedbackCategory.SUGGESTION:
        return '功能建议'
      case FeedbackCategory.EXPERIENCE:
        return '使用体验'
      default:
        return '其他'
    }
  }

  build() {
    Column({ space: 14 }) {
      Row() {
        Text('历史反馈')
          .fontSize(20)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.text_primary'))
          .layoutWeight(1)
        Button('关闭')
          .height(36)
          .type(ButtonType.Capsule)
          .backgroundColor($r('app.color.button_soft_background'))
          .fontColor($r('app.color.button_soft_text'))
          .onClick(() => {
            this.getDialogController()?.close()
            this.onClose()
          })
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)

      if (this.entries.length === 0) {
        Text('暂时没有已成功提交的反馈。')
          .fontSize(14)
          .fontColor($r('app.color.text_secondary'))
          .width('100%')
          .padding({ top: 24, bottom: 24 })
          .textAlign(TextAlign.Center)
      } else {
        Scroll() {
          Column({ space: 10 }) {
            ForEach(this.entries, (entry: FeedbackHistoryEntry) => {
              Column({ space: 6 }) {
                Text(FeedbackHistoryStore.formatTimestamp(entry.submittedAtMillis))
                  .fontSize(12)
                  .fontColor($r('app.color.text_secondary'))
                Text(`编号：${entry.feedbackId}`)
                  .fontSize(14)
                  .fontWeight(FontWeight.Medium)
                  .fontColor($r('app.color.text_primary'))
                Text(`类型：${this.categoryLabel(entry.category)}`)
                  .fontSize(13)
                  .fontColor($r('app.color.text_secondary'))
                Text(`邮箱：${FeedbackHistoryStore.maskEmail(entry.email)}`)
                  .fontSize(13)
                  .fontColor($r('app.color.text_secondary'))
                Text(`内容：${entry.message}`)
                  .fontSize(13)
                  .fontColor($r('app.color.text_primary'))
              }
              .width('100%')
              .padding(12)
              .borderRadius(14)
              .backgroundColor($r('app.color.input_background'))
              .border({ width: 0.5, color: $r('app.color.input_border'), style: BorderStyle.Solid })
            }, (entry: FeedbackHistoryEntry) => entry.feedbackId)
          }
          .width('100%')
        }
        .width('100%')
        .layoutWeight(1)
        .scrollBar(BarState.Off)
      }
    }
    .width('88%')
    .height('72%')
    .padding(20)
    .borderRadius(24)
    .backgroundColor($r('app.color.card_background'))
    .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
    .border({ width: 1, color: $r('app.color.card_border'), style: BorderStyle.Solid })
  }
}

@CustomDialog
@Component
struct UpdateLogDialog {
  controller?: CustomDialogController
  entries: HarmonyReleaseLog[] = []
  currentVersionName: string = ''
  errorLabel: string = ''
  onClose: () => void = () => {
  }
  onRetry: () => void = () => {
  }
  @State expandedVersionNames: string[] = []
  @State initialExpansionResolved: boolean = false

  aboutToAppear(): void {
    if (this.initialExpansionResolved) {
      return
    }
    this.initialExpansionResolved = true
    // The repository sorts entries newest first. Prefer that entry so a stale installed
    // package cannot hide a newer release behind its own expanded item.
    let preferredVersion = ''
    if (this.entries.length > 0) {
      preferredVersion = this.entries[0].versionName
    }
    if (preferredVersion.length > 0) {
      this.expandedVersionNames = [preferredVersion]
    }
  }

  private isExpanded(versionName: string): boolean {
    return this.expandedVersionNames.indexOf(versionName) >= 0
  }

  private toggleVersion(versionName: string): void {
    const next: string[] = []
    const wasExpanded = this.isExpanded(versionName)
    for (let index = 0; index < this.expandedVersionNames.length; index++) {
      const item = this.expandedVersionNames[index]
      if (item !== versionName) {
        next.push(item)
      }
    }
    if (!wasExpanded) {
      next.push(versionName)
    }
    this.expandedVersionNames = next
  }

  private formatReleasedAt(value: string): string {
    const normalized = value.trim()
    if (normalized.length < 10) {
      return ''
    }
    return normalized.substring(0, 10)
  }

  build() {
    Column({ space: 14 }) {
      Row() {
        Text('更新日志')
          .fontSize(20)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.text_primary'))
          .layoutWeight(1)
        Button('关闭')
          .height(36)
          .type(ButtonType.Capsule)
          .backgroundColor($r('app.color.button_soft_background'))
          .fontColor($r('app.color.button_soft_text'))
          .onClick(() => {
            this.getDialogController()?.close()
            this.onClose()
          })
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)

      if (this.errorLabel.length > 0) {
        Column({ space: 14 }) {
          Text(this.errorLabel)
            .fontSize(14)
            .lineHeight(21)
            .fontColor($r('app.color.error_text'))
            .width('100%')
          Button('重试')
            .width('100%')
            .type(ButtonType.Capsule)
            .backgroundColor($r('app.color.button_soft_background'))
            .fontColor($r('app.color.button_soft_text'))
            .onClick(() => {
              this.getDialogController()?.close()
              this.onClose()
              this.onRetry()
            })
        }
        .width('100%')
        .padding({ top: 24, bottom: 24 })
      } else if (this.entries.length === 0) {
        Text('暂时没有可显示的更新日志。')
          .fontSize(14)
          .fontColor($r('app.color.text_secondary'))
          .width('100%')
          .padding({ top: 24, bottom: 24 })
          .textAlign(TextAlign.Center)
      } else {
        Text('点击版本可展开查看详细更新内容。')
          .fontSize(12)
          .fontColor($r('app.color.text_secondary'))
          .width('100%')
        Scroll() {
          Column({ space: 10 }) {
            ForEach(this.entries, (entry: HarmonyReleaseLog) => {
              Column({ space: 0 }) {
                Button({ type: ButtonType.Normal }) {
                  Row({ space: 10 }) {
                    Column({ space: 3 }) {
                      Text(`v${entry.versionName}`)
                        .fontSize(15)
                        .fontWeight(FontWeight.Medium)
                        .fontColor($r('app.color.text_primary'))
                      if (this.formatReleasedAt(entry.releasedAt).length > 0) {
                        Text(this.formatReleasedAt(entry.releasedAt))
                          .fontSize(12)
                          .fontColor($r('app.color.text_secondary'))
                      }
                    }
                    .layoutWeight(1)
                    .alignItems(HorizontalAlign.Start)
                    SymbolGlyph($r('sys.symbol.chevron_backward'))
                      .fontSize(18)
                      .fontColor([$r('app.color.text_secondary')])
                      .rotate({
                        angle: this.isExpanded(entry.versionName) ? 270 : 180,
                        centerX: '50%',
                        centerY: '50%'
                      })
                      .animation({ duration: 180, curve: Curve.EaseInOut })
                  }
                  .width('100%')
                  .padding(12)
                }
                .width('100%')
                .padding(0)
                .backgroundColor(Color.Transparent)
                .borderRadius(0)
                .stateEffect(true)
                .onClick(() => {
                  this.toggleVersion(entry.versionName)
                })

                if (this.isExpanded(entry.versionName)) {
                  Column() {
                    Divider()
                      .width('100%')
                      .color($r('app.color.input_border'))
                      .strokeWidth(0.5)
                    Text(entry.releaseNotes.length > 0 ? entry.releaseNotes : '暂无更新说明。')
                      .fontSize(13)
                      .lineHeight(20)
                      .fontColor($r('app.color.text_secondary'))
                      .width('100%')
                      .padding(12)
                  }
                  .width('100%')
                  .transition(
                    TransitionEffect.OPACITY
                      .combine(TransitionEffect.translate({ x: 0, y: -6 }))
                      .animation({ duration: 180, curve: Curve.EaseOut })
                  )
                }
              }
              .width('100%')
              .clip(true)
              .borderRadius(14)
              .backgroundColor($r('app.color.input_background'))
              .border({ width: 0.5, color: $r('app.color.input_border'), style: BorderStyle.Solid })
            }, (entry: HarmonyReleaseLog) => entry.versionName)
          }
          .width('100%')
        }
        .width('100%')
        .layoutWeight(1)
        .scrollBar(BarState.Off)
      }
    }
    .width('88%')
    .height('72%')
    .padding(20)
    .borderRadius(24)
    .backgroundColor($r('app.color.card_background'))
    .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
    .border({ width: 1, color: $r('app.color.card_border'), style: BorderStyle.Solid })
  }
}

@Entry
@Component
struct About {
  private updateRepository: GiteeUpdateRepository = new GiteeUpdateRepository()
  private feedbackClient: FeedbackClient = new FeedbackClient()
  private appearanceStore?: AppAppearanceStore
  private hotWaterStore?: HotWaterStore
  private onboardingStore?: OnboardingStore
  private feedbackHistoryStore?: FeedbackHistoryStore
  private feedbackHistoryDialogController?: CustomDialogController
  private updateLogDialogController?: CustomDialogController
  private feedbackRequestGeneration: number = 0
  private updateLogRequestGeneration: number = 0
  private appearanceWriteGeneration: number = 0
  private appearanceSaveChain: Promise<void> = Promise.resolve()

  @State screen: MoreScreen = MoreScreen.HOME
  @State currentVersionLabel: string = '2.2.1'
  @State checkingUpdate: boolean = false
  @State loadingUpdateLogs: boolean = false
  @State updateCheckState: UpdateCheckState = UpdateCheckState.IDLE
  @State updateStatusLabel: string = '通过 Gitee 已发行版本检查新版本。'
  @State updateReleaseNotes: string = ''
  @State updateVersionName: string = ''
  @State themeMode: string = AppearanceThemeMode.SYSTEM
  @State materialType: string = AppearanceMaterialType.ADAPTIVE
  @State materialLevel: string = AppearanceMaterialLevel.ADAPTIVE
  @State immersiveMaterialSupported: boolean = false
  @State feedbackCategory: string = FeedbackCategory.BUG
  @State feedbackEmail: string = ''
  @State feedbackMessage: string = ''
  @State feedbackEmailErrorLabel: string = ''
  @State feedbackErrorLabel: string = ''
  @State feedbackStatusLabel: string = ''
  @State feedbackSucceeded: boolean = false
  @State submittingFeedback: boolean = false
  @State feedbackHistory: FeedbackHistoryEntry[] = []
  @StorageProp('statusBarHeight') statusBarHeight: number = 0
  @State clearingAllInformation: boolean = false

  aboutToAppear(): void {
    this.immersiveMaterialSupported = this.supportsImmersiveMaterial()
    const context = this.getUIContext().getHostContext() as common.Context
    this.hotWaterStore = new HotWaterStore(context)
    this.onboardingStore = new OnboardingStore(context)
    this.initialize()
    this.loadAppearance()
    this.loadFeedbackHistory()
  }

  aboutToDisappear(): void {
    this.feedbackRequestGeneration++
    this.updateLogRequestGeneration++
    this.feedbackClient.cancelActiveRequest()
    this.feedbackHistoryDialogController?.close()
    this.feedbackHistoryDialogController = undefined
    this.updateLogDialogController?.close()
    this.updateLogDialogController = undefined
  }

  onBackPress(): boolean {
    if (this.screen !== MoreScreen.HOME) {
      this.closeSecondaryPage()
      return true
    }
    return false
  }

  private async initialize(): Promise<void> {
    await this.loadCurrentVersion()
    this.checkForUpdates()
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

  private async checkForUpdates(): Promise<void> {
    this.checkingUpdate = true
    this.updateCheckState = UpdateCheckState.IDLE
    this.updateReleaseNotes = ''
    this.updateVersionName = ''
    try {
      const result = await this.updateRepository.check(this.currentVersionLabel)
      this.updateCheckState = result.state
      this.updateStatusLabel = result.message
      if (result.update) {
        this.updateReleaseNotes = result.update.releaseNotes
        this.updateVersionName = result.update.versionName
      }
    } catch (error) {
      this.updateCheckState = UpdateCheckState.FAILED
      this.updateStatusLabel = '检查更新失败，请检查网络后重试。'
    } finally {
      this.checkingUpdate = false
    }
  }

  private async loadAppearance(): Promise<void> {
    const requestedGeneration = this.appearanceWriteGeneration
    try {
      const context = this.getUIContext().getHostContext() as common.Context
      this.appearanceStore = new AppAppearanceStore(context)
      const appearance = await this.appearanceStore.load()
      if (requestedGeneration !== this.appearanceWriteGeneration) {
        return
      }
      this.applyAppearanceState(appearance)
      publishAppearance(appearance)
    } catch (error) {
      // The app remains usable with the in-memory defaults when Preferences is unavailable.
    }
  }

  private applyAppearanceState(appearance: AppAppearance): void {
    this.themeMode = appearance.themeMode
    this.materialType = appearance.materialType === AppearanceMaterialType.IMMERSIVE &&
      !this.immersiveMaterialSupported
      ? AppearanceMaterialType.ADAPTIVE
      : appearance.materialType
    this.materialLevel = appearance.materialLevel
  }

  private persistAppearance(): void {
    const appearance = new AppAppearance()
    appearance.themeMode = this.themeMode
    appearance.materialType = this.materialType
    appearance.materialLevel = this.materialLevel
    const writeGeneration = ++this.appearanceWriteGeneration
    publishAppearance(appearance)
    this.applyApplicationColorMode(appearance.themeMode)
    this.appearanceSaveChain = this.appearanceSaveChain.then(async (): Promise<void> => {
      try {
        const context = this.getUIContext().getHostContext() as common.Context
        if (!this.appearanceStore) {
          this.appearanceStore = new AppAppearanceStore(context)
        }
        const savedAppearance = await this.appearanceStore.save(appearance)
        if (writeGeneration === this.appearanceWriteGeneration) {
          this.applyAppearanceState(savedAppearance)
          publishAppearance(savedAppearance)
        }
      } catch (error) {
        // Preferences failures should not undo an appearance change already applied for this session.
      }
    })
  }

  private updateThemeMode(themeMode: string): void {
    if (this.themeMode === themeMode) {
      return
    }
    this.themeMode = themeMode
    this.persistAppearance()
  }

  private updateMaterialType(materialType: string): void {
    if (materialType === AppearanceMaterialType.IMMERSIVE && !this.immersiveMaterialSupported) {
      return
    }
    if (this.materialType === materialType) {
      return
    }
    this.materialType = materialType
    this.persistAppearance()
  }

  private updateMaterialLevel(materialLevel: string): void {
    if (this.materialLevel === materialLevel) {
      return
    }
    this.materialLevel = materialLevel
    this.persistAppearance()
  }

  private selectThemeMode(index: number): void {
    const values = this.themeModeValues()
    if (index >= 0 && index < values.length) {
      this.updateThemeMode(values[index])
    }
  }

  private selectMaterialType(index: number): void {
    const values = this.materialTypeValues()
    if (index >= 0 && index < values.length) {
      this.updateMaterialType(values[index])
    }
  }

  private selectMaterialLevel(index: number): void {
    const values = this.materialLevelValues()
    if (index >= 0 && index < values.length) {
      this.updateMaterialLevel(values[index])
    }
  }

  private applyApplicationColorMode(themeMode: string): void {
    try {
      const context = this.getUIContext().getHostContext() as common.UIAbilityContext
      context.getApplicationContext().setColorMode(this.resolveColorMode(themeMode))
    } catch (error) {
      // Applying the system color mode is best-effort; AppStorage still updates the page state.
    }
  }

  private resolveColorMode(themeMode: string): ConfigurationConstant.ColorMode {
    if (themeMode === AppearanceThemeMode.DARK) {
      return ConfigurationConstant.ColorMode.COLOR_MODE_DARK
    }
    if (themeMode === AppearanceThemeMode.LIGHT) {
      return ConfigurationConstant.ColorMode.COLOR_MODE_LIGHT
    }
    return ConfigurationConstant.ColorMode.COLOR_MODE_NOT_SET
  }

  private supportsImmersiveMaterial(): boolean {
    try {
      const supportedTypes = hdsMaterial.getSystemMaterialTypes()
      return supportedTypes.indexOf(hdsMaterial.MaterialType.IMMERSIVE) >= 0
    } catch (error) {
      return false
    }
  }

  // Select does not expose HDS systemMaterialEffect. Keep its control and popup
  // on the same material scale through the native blur APIs it provides.
  private resolveSelectBlurStyle(): BlurStyle {
    if (this.materialType === AppearanceMaterialType.NONE) {
      return BlurStyle.NONE
    }
    switch (this.materialLevel) {
      case AppearanceMaterialLevel.EXQUISITE:
        return BlurStyle.COMPONENT_ULTRA_THICK
      case AppearanceMaterialLevel.GENTLE:
        return BlurStyle.COMPONENT_THICK
      case AppearanceMaterialLevel.SMOOTH:
        return BlurStyle.COMPONENT_ULTRA_THIN
      default:
        return this.materialType === AppearanceMaterialType.IMMERSIVE
          ? BlurStyle.COMPONENT_THICK
          : BlurStyle.COMPONENT_REGULAR
    }
  }

  private goBack(): void {
    this.getUIContext().getRouter().back()
  }

  private closeSecondaryPage(): void {
    if (this.screen === MoreScreen.HOME) {
      return
    }
    this.getUIContext().animateTo({ duration: 240, curve: Curve.EaseInOut }, () => {
      this.screen = MoreScreen.HOME
    })
  }

  private openSecondaryPage(targetScreen: MoreScreen): void {
    if (this.screen === targetScreen) {
      return
    }
    this.getUIContext().animateTo({ duration: 260, curve: Curve.EaseOut }, () => {
      this.screen = targetScreen
    })
  }

  private screenTitle(): string {
    switch (this.screen) {
      case MoreScreen.FEEDBACK:
        return '问题反馈'
      case MoreScreen.PRIVACY:
        return '隐私政策'
      case MoreScreen.CREDITS:
        return '制作者'
      default:
        return '更多'
    }
  }

  private async openExternalLink(uri: string): Promise<void> {
    try {
      const context = this.getUIContext().getHostContext() as common.UIAbilityContext
      const want: Want = {
        action: 'ohos.want.action.viewData',
        uri: uri
      }
      await context.startAbility(want)
    } catch (error) {
      this.updateStatusLabel = '暂时无法打开链接，请稍后再试。'
    }
  }

  private async loadFeedbackHistory(): Promise<void> {
    try {
      if (!this.feedbackHistoryStore) {
        const context = this.getUIContext().getHostContext() as common.Context
        this.feedbackHistoryStore = new FeedbackHistoryStore(context)
      }
      this.feedbackHistory = await this.feedbackHistoryStore.load()
    } catch (error) {
      this.feedbackHistory = []
    }
  }

  private async saveFeedbackHistory(feedbackId: string, category: string, email: string,
    message: string): Promise<void> {
    if (!this.feedbackHistoryStore) {
      const context = this.getUIContext().getHostContext() as common.Context
      this.feedbackHistoryStore = new FeedbackHistoryStore(context)
    }
    const entry = new FeedbackHistoryEntry()
    entry.submittedAtMillis = Date.now()
    entry.feedbackId = feedbackId
    entry.category = category
    entry.email = email
    entry.message = message
    this.feedbackHistory = await this.feedbackHistoryStore.append(entry)
  }

  private confirmClearAllInformation(): void {
    if (this.submittingFeedback || this.clearingAllInformation) {
      return
    }
    AlertDialog.show({
      title: '清空所有信息',
      message: '将删除应用内保存的账号与设备配置、账单和操作记录、运行状态、反馈表单与反馈历史、外观设置及引导状态，且无法恢复。已提交到反馈服务的内容不会被撤回。',
      primaryButton: {
        value: '清空',
        action: () => {
          this.clearAllInformation()
        }
      },
      secondaryButton: {
        value: '取消',
        action: () => {
        }
      }
    })
  }

  private async clearAllInformation(): Promise<void> {
    if (this.clearingAllInformation) {
      return
    }
    this.clearingAllInformation = true
    this.feedbackRequestGeneration++
    this.updateLogRequestGeneration++
    this.feedbackClient.cancelActiveRequest()
    try {
      const context = this.getUIContext().getHostContext() as common.Context
      if (!this.hotWaterStore) {
        this.hotWaterStore = new HotWaterStore(context)
      }
      if (!this.onboardingStore) {
        this.onboardingStore = new OnboardingStore(context)
      }
      if (!this.feedbackHistoryStore) {
        this.feedbackHistoryStore = new FeedbackHistoryStore(context)
      }
      if (!this.appearanceStore) {
        this.appearanceStore = new AppAppearanceStore(context)
      }
      // Invalidate stale store writers before waiting for unrelated appearance
      // persistence. The same generation is passed to clearAll below.
      const clearGeneration = ConfigFlowBridge.beginDataClear()
      // Wait for a pending appearance write before deleting the store.
      await this.appearanceSaveChain
      await this.hotWaterStore.clearAll(clearGeneration)
      await this.feedbackHistoryStore.clear()
      await this.appearanceStore.clear()
      await this.onboardingStore.clear()

      const defaultAppearance = new AppAppearance()
      this.applyAppearanceState(defaultAppearance)
      publishAppearance(defaultAppearance)
      this.applyApplicationColorMode(AppearanceThemeMode.SYSTEM)
      ConfigFlowBridge.markDataCleared(clearGeneration)

      this.feedbackHistory = []
      this.feedbackEmail = ''
      this.feedbackMessage = ''
      this.feedbackCategory = FeedbackCategory.BUG
      this.feedbackEmailErrorLabel = ''
      this.feedbackErrorLabel = ''
      this.feedbackStatusLabel = ''
      this.feedbackSucceeded = false
      await this.getUIContext().getRouter().replaceUrl({ url: 'pages/Onboarding' })
    } catch (error) {
      this.feedbackStatusLabel = '清空失败，请稍后重试。'
      this.feedbackSucceeded = false
    } finally {
      this.clearingAllInformation = false
    }
  }

  private showFeedbackHistory(): void {
    this.feedbackHistoryDialogController?.close()
    const controller = new CustomDialogController({
      builder: FeedbackHistoryDialog({
        entries: this.feedbackHistory,
        onClose: () => {
          this.feedbackHistoryDialogController = undefined
        }
      }),
      cancel: () => {
        this.feedbackHistoryDialogController = undefined
      },
      autoCancel: true,
      alignment: DialogAlignment.Center,
      customStyle: true,
      maskColor: '#66000000'
    })
    this.feedbackHistoryDialogController = controller
    controller.open()
  }

  private async showUpdateLogs(): Promise<void> {
    if (this.loadingUpdateLogs) {
      return
    }
    const requestGeneration = ++this.updateLogRequestGeneration
    this.loadingUpdateLogs = true
    try {
      const result: ReleaseLogFetchResult = await this.updateRepository.listReleaseLogs()
      if (requestGeneration !== this.updateLogRequestGeneration) {
        return
      }
      this.openUpdateLogDialog(result.releases, result.success ? '' : result.message)
    } finally {
      if (requestGeneration === this.updateLogRequestGeneration) {
        this.loadingUpdateLogs = false
      }
    }
  }

  private openUpdateLogDialog(entries: HarmonyReleaseLog[], errorLabel: string): void {
    this.updateLogDialogController?.close()
    const controller = new CustomDialogController({
      builder: UpdateLogDialog({
        entries: entries,
        currentVersionName: this.currentVersionLabel,
        errorLabel: errorLabel,
        onClose: () => {
          this.updateLogDialogController = undefined
        },
        onRetry: () => {
          this.showUpdateLogs()
        }
      }),
      cancel: () => {
        this.updateLogDialogController = undefined
      },
      autoCancel: true,
      alignment: DialogAlignment.Center,
      customStyle: true,
      maskColor: '#66000000'
    })
    this.updateLogDialogController = controller
    controller.open()
  }

  private async submitFeedback(): Promise<void> {
    if (this.submittingFeedback) {
      return
    }
    const normalizedEmail = this.feedbackEmail.trim()
    const normalizedMessage = this.feedbackMessage.trim()
    const emailValidationMessage = FeedbackClient.emailValidationError(normalizedEmail)
    if (emailValidationMessage.length > 0) {
      this.feedbackEmailErrorLabel = emailValidationMessage
      this.feedbackErrorLabel = ''
      this.feedbackStatusLabel = ''
      this.feedbackSucceeded = false
      return
    }
    const validationMessage = FeedbackClient.validationError(normalizedMessage)
    if (validationMessage.length > 0) {
      this.feedbackErrorLabel = validationMessage
      this.feedbackEmailErrorLabel = ''
      this.feedbackStatusLabel = ''
      this.feedbackSucceeded = false
      return
    }
    const submittedCategory = this.feedbackCategory
    const requestGeneration = ++this.feedbackRequestGeneration
    this.submittingFeedback = true
    this.feedbackEmailErrorLabel = ''
    this.feedbackErrorLabel = ''
    this.feedbackStatusLabel = ''
    this.feedbackSucceeded = false
    try {
      const result: FeedbackSubmitResult = await this.feedbackClient.submit(
        this.currentVersionLabel,
        submittedCategory,
        normalizedEmail,
        normalizedMessage
      )
      if (requestGeneration !== this.feedbackRequestGeneration) {
        return
      }
      if (result.state === FeedbackSubmitState.SUCCESS) {
        let historySaved = true
        try {
          await this.saveFeedbackHistory(
            result.feedbackId,
            submittedCategory,
            normalizedEmail,
            normalizedMessage
          )
        } catch (error) {
          historySaved = false
        }
        if (this.feedbackMessage.trim() === normalizedMessage && this.feedbackCategory === submittedCategory &&
          this.feedbackEmail.trim() === normalizedEmail) {
          this.feedbackMessage = ''
        }
        this.feedbackSucceeded = true
        this.feedbackStatusLabel = historySaved
          ? `提交成功，已保存到历史反馈。反馈编号：${result.feedbackId}`
          : `提交成功，反馈编号：${result.feedbackId}。本地历史保存失败，请稍后查看。`
      } else if (result.state === FeedbackSubmitState.RATE_LIMITED) {
        const waitHint = result.retryAfterSeconds >= 0
          ? `请等待 ${result.retryAfterSeconds} 秒后`
          : '请稍后'
        this.feedbackStatusLabel = `提交过于频繁，${waitHint}手动重试。反馈内容已保留。`
      } else if (result.state === FeedbackSubmitState.REJECTED) {
        this.feedbackErrorLabel = result.message
      } else {
        this.feedbackStatusLabel = result.message
      }
    } finally {
      if (requestGeneration === this.feedbackRequestGeneration) {
        this.submittingFeedback = false
      }
    }
  }

  @Builder
  private PageHeader(title: string, rootPage: boolean): void {
    Row() {
      Text('‹')
        .fontSize(32)
        .fontWeight(FontWeight.Medium)
        .fontColor($r('app.color.text_primary'))
        .accessibilityText(rootPage ? '返回' : '返回更多')
        .width(40)
        .height(40)
        .textAlign(TextAlign.Center)
        .onClick(() => {
          if (rootPage) {
            this.goBack()
          } else {
            this.closeSecondaryPage()
          }
        })
      Text(title)
        .fontSize(30)
        .fontWeight(FontWeight.Bold)
        .fontColor($r('app.color.text_primary'))
        .layoutWeight(1)
    }
    .width('100%')
    .alignItems(VerticalAlign.Center)
    .padding({ top: this.statusBarHeight > 0 ? this.statusBarHeight : 0 })
  }

  private themeModeValues(): string[] {
    return [
      AppearanceThemeMode.SYSTEM,
      AppearanceThemeMode.LIGHT,
      AppearanceThemeMode.DARK
    ]
  }

  private themeModeOptions(): SelectOption[] {
    return [
      { value: '跟随系统' },
      { value: '浅色' },
      { value: '深色' }
    ]
  }

  private themeModeIndex(): number {
    const index = this.themeModeValues().indexOf(this.themeMode)
    return index >= 0 ? index : 0
  }

  private themeModeLabel(): string {
    const labels: string[] = ['跟随系统', '浅色', '深色']
    return labels[this.themeModeIndex()]
  }

  private materialTypeValues(): string[] {
    const values: string[] = [AppearanceMaterialType.ADAPTIVE]
    if (this.immersiveMaterialSupported) {
      values.push(AppearanceMaterialType.IMMERSIVE)
    }
    values.push(AppearanceMaterialType.NONE)
    return values
  }

  private materialTypeLabel(value: string): string {
    switch (value) {
      case AppearanceMaterialType.IMMERSIVE:
        return '沉浸'
      case AppearanceMaterialType.NONE:
        return '关闭'
      default:
        return '自适应'
    }
  }

  private materialTypeOptions(): SelectOption[] {
    const options: SelectOption[] = []
    const values = this.materialTypeValues()
    for (let index = 0; index < values.length; index++) {
      options.push({ value: this.materialTypeLabel(values[index]) })
    }
    return options
  }

  private materialTypeIndex(): number {
    const index = this.materialTypeValues().indexOf(this.materialType)
    return index >= 0 ? index : 0
  }

  private materialLevelValues(): string[] {
    return [
      AppearanceMaterialLevel.ADAPTIVE,
      AppearanceMaterialLevel.EXQUISITE,
      AppearanceMaterialLevel.GENTLE,
      AppearanceMaterialLevel.SMOOTH
    ]
  }

  private materialLevelOptions(): SelectOption[] {
    return [
      { value: '自适应' },
      { value: '精美' },
      { value: '轻柔' },
      { value: '流畅' }
    ]
  }

  private materialLevelLabel(): string {
    const labels: string[] = ['自适应', '精美', '轻柔', '流畅']
    const index = this.materialLevelValues().indexOf(this.materialLevel)
    return labels[index >= 0 ? index : 0]
  }

  private materialLevelIndex(): number {
    const index = this.materialLevelValues().indexOf(this.materialLevel)
    return index >= 0 ? index : 0
  }

  @Builder
  private navigationRow(icon: Resource, title: string, description: string, targetScreen: MoreScreen): void {
    Row({ space: 12 }) {
      Stack({ alignContent: Alignment.Center }) {
        SymbolGlyph(icon)
          .fontSize(20)
          .fontColor([$r('app.color.accent_text')])
      }
      .width(38)
      .height(38)
      .borderRadius(13)
      .backgroundColor($r('app.color.input_background'))
      Column({ space: 4 }) {
        Text(title)
          .fontSize(16)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_primary'))
        Text(description)
          .fontSize(12)
          .fontColor($r('app.color.text_secondary'))
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      SymbolGlyph($r('sys.symbol.chevron_backward'))
        .fontSize(18)
        .fontColor([$r('app.color.text_secondary')])
        .rotate({ angle: 180, centerX: '50%', centerY: '50%' })
    }
    .width('100%')
    .padding({ top: 4, bottom: 4 })
    .onClick(() => {
      this.openSecondaryPage(targetScreen)
    })
  }

  @Builder
  private externalNavigationRow(icon: Resource, title: string, description: string, uri: string): void {
    Row({ space: 12 }) {
      Stack({ alignContent: Alignment.Center }) {
        SymbolGlyph(icon)
          .fontSize(20)
          .fontColor([$r('app.color.accent_text')])
      }
      .width(38)
      .height(38)
      .borderRadius(13)
      .backgroundColor($r('app.color.input_background'))
      Column({ space: 4 }) {
        Text(title)
          .fontSize(16)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_primary'))
        Text(description)
          .fontSize(12)
          .fontColor($r('app.color.text_secondary'))
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      SymbolGlyph($r('sys.symbol.chevron_backward'))
        .fontSize(18)
        .fontColor([$r('app.color.text_secondary')])
        .rotate({ angle: 180, centerX: '50%', centerY: '50%' })
    }
    .width('100%')
    .padding({ top: 4, bottom: 4 })
    .onClick(() => {
      this.openExternalLink(uri)
    })
  }

  @Builder
  private HomeContent(): void {
    Scroll() {
      Column({ space: 16 }) {
        Row({ space: 14 }) {
          Image($r('app.media.app_icon'))
            .width(62)
            .height(62)
            .borderRadius(18)
          Column({ space: 5 }) {
            Text('趣智校园热水')
              .fontSize(20)
              .fontWeight(FontWeight.Bold)
              .fontColor($r('app.color.text_primary'))
            Text('第三方热水控制工具')
              .fontSize(13)
              .fontColor($r('app.color.text_secondary'))
          }
          .layoutWeight(1)
          .alignItems(HorizontalAlign.Start)
        }
        .width('100%')
        .alignItems(VerticalAlign.Center)

        Column({ space: 14 }) {
          Text(`当前版本 v${this.currentVersionLabel}`)
            .fontSize(14)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.accent_text'))
          Row({ space: 12 }) {
            Text(this.updateStatusLabel)
              .fontSize(13)
              .fontColor(
                this.updateCheckState === UpdateCheckState.FAILED
                  ? $r('app.color.error_text')
                  : (this.updateCheckState === UpdateCheckState.UPDATE_AVAILABLE
                    ? $r('app.color.accent_text')
                    : $r('app.color.text_secondary'))
              )
              .layoutWeight(1)
            Button(this.checkingUpdate ? '正在检查...' : '检查更新')
              .height(38)
              .type(ButtonType.Capsule)
              .backgroundColor($r('app.color.button_soft_background'))
              .fontColor($r('app.color.button_soft_text'))
              .enabled(!this.checkingUpdate)
              .onClick(() => {
                this.checkForUpdates()
              })
          }
          .width('100%')
          .alignItems(VerticalAlign.Center)

          Button({ type: ButtonType.Normal }) {
            Row({ space: 12 }) {
              Stack({ alignContent: Alignment.Center }) {
                SymbolGlyph($r('sys.symbol.list_square_bill'))
                  .fontSize(19)
                  .fontColor([$r('app.color.accent_text')])
              }
              .width(34)
              .height(34)
              .borderRadius(12)
              .backgroundColor($r('app.color.input_background'))
              Column({ space: 2 }) {
                Text(this.loadingUpdateLogs ? '正在加载更新日志...' : '更新日志')
                  .fontSize(14)
                  .fontWeight(FontWeight.Medium)
                  .fontColor($r('app.color.text_primary'))
                Text('查看每个版本的更新内容')
                  .fontSize(12)
                  .fontColor($r('app.color.text_secondary'))
              }
              .layoutWeight(1)
              .alignItems(HorizontalAlign.Start)
              SymbolGlyph($r('sys.symbol.chevron_backward'))
                .fontSize(18)
                .fontColor([$r('app.color.text_secondary')])
                .rotate({ angle: 180, centerX: '50%', centerY: '50%' })
            }
            .width('100%')
            .padding({ left: 10, right: 10, top: 8, bottom: 8 })
          }
          .width('100%')
          .padding(0)
          .borderRadius(16)
          .backgroundColor($r('app.color.input_background'))
          .enabled(!this.loadingUpdateLogs)
          .stateEffect(true)
          .onClick(() => {
            this.showUpdateLogs()
          })

          if (this.updateCheckState === UpdateCheckState.UPDATE_AVAILABLE) {
            Text(`发现新版本 v${this.updateVersionName}`)
              .fontSize(14)
              .fontWeight(FontWeight.Bold)
              .fontColor($r('app.color.accent_text'))
            Text(this.updateReleaseNotes.length > 0 ? this.updateReleaseNotes : '暂无更新说明。')
              .fontSize(13)
              .fontColor($r('app.color.text_secondary'))
          }
        }
        .width('100%')
        .padding(18)
        .borderRadius(24)
        .backgroundColor($r('app.color.card_background'))
        .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
        .border({ width: 1, color: $r('app.color.card_border'), style: BorderStyle.Solid })

        Column({ space: 12 }) {
          Text('显示与沉浸光感')
            .fontSize(18)
            .fontWeight(FontWeight.Bold)
            .fontColor($r('app.color.text_primary'))
          Row({ space: 12 }) {
            Text('深浅色模式')
              .fontSize(14)
              .fontColor($r('app.color.text_primary'))
              .layoutWeight(1)
            Select(this.themeModeOptions())
              .selected(this.themeModeIndex())
              .value(this.themeModeLabel())
              .font({ size: 14, weight: FontWeight.Medium })
              .fontColor($r('app.color.text_primary'))
              .selectedOptionFont({ size: 14, weight: FontWeight.Medium })
              .optionFont({ size: 14, weight: FontWeight.Regular })
              .backgroundColor(
                this.materialType === AppearanceMaterialType.NONE
                  ? $r('app.color.input_background')
                  : Color.Transparent
              )
              .backgroundBlurStyle(this.resolveSelectBlurStyle())
              .border({ width: 1, color: $r('app.color.input_border'), style: BorderStyle.Solid })
              .borderRadius(12)
              .menuBackgroundColor($r('app.color.select_menu_background'))
              .menuBackgroundBlurStyle(BlurStyle.COMPONENT_THICK)
              .optionBgColor($r('app.color.select_menu_background'))
              .menuSystemMaterial(uiMaterial.Material.empty)
              .menuOutline({ width: 1, color: $r('app.color.input_border') })
              .selectedOptionBgColor($r('app.color.status_chip_background'))
              .showDefaultSelectedIcon(true)
              .width(190)
              .height(46)
              .onSelect((index: number) => {
                this.selectThemeMode(index)
              })
          }
          .width('100%')

          Text('沉浸光感')
            .fontSize(13)
            .fontColor($r('app.color.text_secondary'))
            .margin({ top: 4 })
          Row({ space: 12 }) {
            Text('材质类型')
              .fontSize(14)
              .fontColor($r('app.color.text_primary'))
              .layoutWeight(1)
            Select(this.materialTypeOptions())
              .selected(this.materialTypeIndex())
              .value(this.materialTypeLabel(this.materialTypeValues()[this.materialTypeIndex()]))
              .font({ size: 14, weight: FontWeight.Medium })
              .fontColor($r('app.color.text_primary'))
              .selectedOptionFont({ size: 14, weight: FontWeight.Medium })
              .optionFont({ size: 14, weight: FontWeight.Regular })
              .backgroundColor(
                this.materialType === AppearanceMaterialType.NONE
                  ? $r('app.color.input_background')
                  : Color.Transparent
              )
              .backgroundBlurStyle(this.resolveSelectBlurStyle())
              .border({ width: 1, color: $r('app.color.input_border'), style: BorderStyle.Solid })
              .borderRadius(12)
              .menuBackgroundColor($r('app.color.select_menu_background'))
              .menuBackgroundBlurStyle(BlurStyle.COMPONENT_THICK)
              .optionBgColor($r('app.color.select_menu_background'))
              .menuSystemMaterial(uiMaterial.Material.empty)
              .menuOutline({ width: 1, color: $r('app.color.input_border') })
              .selectedOptionBgColor($r('app.color.status_chip_background'))
              .showDefaultSelectedIcon(true)
              .width(190)
              .height(46)
              .onSelect((index: number) => {
                this.selectMaterialType(index)
              })
          }
          .width('100%')
          if (!this.immersiveMaterialSupported) {
            Text('当前设备不支持沉浸材质，仍可使用自适应效果。')
              .fontSize(12)
              .fontColor($r('app.color.text_secondary'))
          }

          Row({ space: 12 }) {
            Text('材质等级')
              .fontSize(14)
              .fontColor($r('app.color.text_primary'))
              .layoutWeight(1)
            Select(this.materialLevelOptions())
              .selected(this.materialLevelIndex())
              .value(this.materialLevelLabel())
              .font({ size: 14, weight: FontWeight.Medium })
              .fontColor($r('app.color.text_primary'))
              .selectedOptionFont({ size: 14, weight: FontWeight.Medium })
              .optionFont({ size: 14, weight: FontWeight.Regular })
              .backgroundColor(
                this.materialType === AppearanceMaterialType.NONE
                  ? $r('app.color.input_background')
                  : Color.Transparent
              )
              .backgroundBlurStyle(this.resolveSelectBlurStyle())
              .border({ width: 1, color: $r('app.color.input_border'), style: BorderStyle.Solid })
              .borderRadius(12)
              .menuBackgroundColor($r('app.color.select_menu_background'))
              .menuBackgroundBlurStyle(BlurStyle.COMPONENT_THICK)
              .optionBgColor($r('app.color.select_menu_background'))
              .menuSystemMaterial(uiMaterial.Material.empty)
              .menuOutline({ width: 1, color: $r('app.color.input_border') })
              .selectedOptionBgColor($r('app.color.status_chip_background'))
              .showDefaultSelectedIcon(true)
              .width(190)
              .height(46)
              .onSelect((index: number) => {
                this.selectMaterialLevel(index)
              })
          }
          .width('100%')
        }
        .width('100%')
        .padding(18)
        .borderRadius(24)
        .backgroundColor($r('app.color.card_background'))
        .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
        .border({ width: 1, color: $r('app.color.card_border'), style: BorderStyle.Solid })

        Column({ space: 16 }) {
          this.navigationRow(
            $r('sys.symbol.list_square_bill'),
            '问题反馈',
            '提交建议、查看自己的历史反馈',
            MoreScreen.FEEDBACK
          )
          this.navigationRow(
            $r('sys.symbol.lock_shield'),
            '隐私政策',
            '了解本应用如何处理本地与反馈信息',
            MoreScreen.PRIVACY
          )
          Button(this.clearingAllInformation ? '正在清空...' : '清空所有信息')
            .width('100%')
            .height(44)
            .type(ButtonType.Capsule)
            .backgroundColor($r('app.color.input_background'))
            .fontColor($r('app.color.error_text'))
            .enabled(!this.submittingFeedback && !this.clearingAllInformation)
            .onClick(() => {
              this.confirmClearAllInformation()
            })
        }
        .width('100%')
        .padding(18)
        .borderRadius(24)
        .backgroundColor($r('app.color.card_background'))
        .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
        .border({ width: 1, color: $r('app.color.card_border'), style: BorderStyle.Solid })

        Column({ space: 16 }) {
          this.navigationRow(
            $r('sys.symbol.person_2'),
            '制作者',
            '参与开发与维护的成员',
            MoreScreen.CREDITS
          )
          this.externalNavigationRow(
            $r('sys.symbol.link'),
            '项目仓库',
            '在 Gitee 查看源码与发布信息',
            PROJECT_REPOSITORY_URL
          )
        }
        .width('100%')
        .padding(18)
        .borderRadius(24)
        .backgroundColor($r('app.color.card_background'))
        .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
        .border({ width: 1, color: $r('app.color.card_border'), style: BorderStyle.Solid })
      }
      .width('100%')
      .padding({ bottom: 20 })
    }
    .scrollBar(BarState.Off)
    .layoutWeight(1)
    .width('100%')
    .edgeEffect(EdgeEffect.Spring)
    .transition(
      TransitionEffect.asymmetric(
        TransitionEffect.OPACITY
          .combine(TransitionEffect.scale({ x: 0.98, y: 0.98 }))
          .animation({ duration: 260, curve: Curve.EaseOut }),
        TransitionEffect.OPACITY
          .combine(TransitionEffect.scale({ x: 0.98, y: 0.98 }))
          .animation({ duration: 180, curve: Curve.EaseIn })
      )
    )
  }

  @Builder
  private FeedbackContent(): void {
    Scroll() {
      Column({ space: 16 }) {
        Column({ space: 12 }) {
          Row() {
            Text('向我们反馈')
              .fontSize(18)
              .fontWeight(FontWeight.Bold)
              .fontColor($r('app.color.text_primary'))
              .layoutWeight(1)
            Button('查看历史反馈')
              .height(36)
              .type(ButtonType.Capsule)
              .backgroundColor($r('app.color.button_soft_background'))
              .fontColor($r('app.color.button_soft_text'))
              .enabled(!this.submittingFeedback)
              .onClick(() => {
                this.showFeedbackHistory()
              })
          }
          .width('100%')
          .alignItems(VerticalAlign.Center)
          Text('请选择类型')
            .fontSize(12)
            .fontColor($r('app.color.text_secondary'))
          Row({ space: 8 }) {
            this.feedbackCategoryButton(FeedbackCategory.BUG, '功能问题')
            this.feedbackCategoryButton(FeedbackCategory.SUGGESTION, '功能建议')
          }
          .width('100%')
          Row({ space: 8 }) {
            this.feedbackCategoryButton(FeedbackCategory.EXPERIENCE, '使用体验')
            this.feedbackCategoryButton(FeedbackCategory.OTHER, '其他')
          }
          .width('100%')
          Text('联系邮箱（必填）')
            .fontSize(12)
            .fontColor(
              this.feedbackEmailErrorLabel.length > 0
                ? $r('app.color.error_text')
                : $r('app.color.text_secondary')
            )
          TextInput({
            text: this.feedbackEmail,
            placeholder: 'name@example.com'
          })
            .width('100%')
            .height(48)
            .maxLength(254)
            .type(InputType.Email)
            .enabled(!this.submittingFeedback)
            .fontSize(15)
            .fontColor($r('app.color.text_primary'))
            .placeholderColor($r('app.color.text_secondary'))
            .backgroundColor($r('app.color.input_background'))
            .borderRadius(16)
            .border({
              width: this.feedbackEmailErrorLabel.length > 0 ? 1 : 0.5,
              color: this.feedbackEmailErrorLabel.length > 0
                ? $r('app.color.close_accent')
                : $r('app.color.input_border'),
              style: BorderStyle.Solid
            })
            .onChange((value: string) => {
              this.feedbackEmail = value
              this.feedbackEmailErrorLabel = ''
              this.feedbackStatusLabel = ''
              this.feedbackSucceeded = false
            })
          if (this.feedbackEmailErrorLabel.length > 0) {
            Text(this.feedbackEmailErrorLabel)
              .fontSize(12)
              .fontColor($r('app.color.error_text'))
          }
          Text('反馈内容')
            .fontSize(12)
            .fontColor(
              this.feedbackErrorLabel.length > 0
                ? $r('app.color.error_text')
                : $r('app.color.text_secondary')
            )
          TextArea({
            text: this.feedbackMessage,
            placeholder: '请描述遇到的问题或建议（2-2000 字）'
          })
            .width('100%')
            .height(140)
            .maxLength(2000)
            .enabled(!this.submittingFeedback)
            .fontSize(15)
            .fontColor($r('app.color.text_primary'))
            .placeholderColor($r('app.color.text_secondary'))
            .backgroundColor($r('app.color.input_background'))
            .borderRadius(16)
            .border({
              width: this.feedbackErrorLabel.length > 0 ? 1 : 0.5,
              color: this.feedbackErrorLabel.length > 0
                ? $r('app.color.close_accent')
                : $r('app.color.input_border'),
              style: BorderStyle.Solid
            })
            .onChange((value: string) => {
              if (FeedbackClient.countUnicodeCharacters(value) <= 2000) {
                this.feedbackMessage = value
                this.feedbackErrorLabel = ''
                this.feedbackStatusLabel = ''
                this.feedbackSucceeded = false
              }
            })
          Row() {
            if (this.feedbackErrorLabel.length > 0) {
              Text(this.feedbackErrorLabel)
                .fontSize(12)
                .fontColor($r('app.color.error_text'))
                .layoutWeight(1)
            } else {
              Blank()
            }
            Text(`${FeedbackClient.countUnicodeCharacters(this.feedbackMessage)}/2000`)
              .fontSize(12)
              .fontColor($r('app.color.text_secondary'))
          }
          .width('100%')
          Text('联系邮箱仅用于跟进本次反馈。请勿填写账号、手机号、学号、校园卡号、密码或 Token。')
            .fontSize(12)
            .fontColor($r('app.color.text_secondary'))
          if (this.feedbackStatusLabel.length > 0) {
            Text(this.feedbackStatusLabel)
              .fontSize(13)
              .fontColor(
                this.feedbackSucceeded
                  ? $r('app.color.widget_status_open')
                  : $r('app.color.error_text')
              )
          }
          Button(this.submittingFeedback ? '正在提交...' : '提交反馈')
            .width('100%')
            .type(ButtonType.Capsule)
            .backgroundColor($r('app.color.button_soft_background'))
            .fontColor($r('app.color.button_soft_text'))
            .enabled(!this.submittingFeedback)
            .onClick(() => {
              this.submitFeedback()
            })
        }
        .width('100%')
        .padding(18)
        .borderRadius(24)
        .backgroundColor($r('app.color.card_background'))
        .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
        .border({ width: 1, color: $r('app.color.card_border'), style: BorderStyle.Solid })

      }
      .width('100%')
      .padding({ bottom: 20 })
    }
    .scrollBar(BarState.Off)
    .layoutWeight(1)
    .width('100%')
    .edgeEffect(EdgeEffect.Spring)
    .transition(
      TransitionEffect.asymmetric(
        TransitionEffect.OPACITY
          .combine(TransitionEffect.scale({ x: 0.98, y: 0.98 }))
          .animation({ duration: 260, curve: Curve.EaseOut }),
        TransitionEffect.OPACITY
          .combine(TransitionEffect.scale({ x: 0.98, y: 0.98 }))
          .animation({ duration: 180, curve: Curve.EaseIn })
      )
    )
  }

  @Builder
  private PrivacyContent(): void {
    Scroll() {
      Column({ space: 16 }) {
        Column({ space: 12 }) {
          Text('隐私政策')
            .fontSize(20)
            .fontWeight(FontWeight.Bold)
            .fontColor($r('app.color.text_primary'))
          Text('最后更新：2026 年 8 月')
            .fontSize(12)
            .fontColor($r('app.color.text_secondary'))

          Text('适用范围')
            .fontSize(16)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_primary'))
          Text('本政策适用于趣智校园热水第三方控制工具。我们希望以清晰、必要的方式说明应用在提供热水设备查询、操作和配置辅助功能时如何处理相关信息。')
            .fontSize(14)
            .lineHeight(21)
            .fontColor($r('app.color.text_secondary'))

          Text('信息收集与使用')
            .fontSize(16)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_primary'))
          Text('应用仅在您主动登录、导入配置、绑定设备或使用相关功能时处理必要信息，例如账号标识、设备配置、余额和账单数据。上述信息用于完成请求、恢复应用状态和展示结果，不用于与功能无关的画像或广告。')
            .fontSize(14)
            .lineHeight(21)
            .fontColor($r('app.color.text_secondary'))

          Text('本地保存')
            .fontSize(16)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_primary'))
          Text('为便于下次使用，账号配置、设备信息、账单记录以及您提交成功的反馈摘要可能保存在本机。您可以在更多页面的“清空所有信息”入口删除应用内保存的这些数据；删除应用或清除应用数据也会移除本机保存的信息。')
            .fontSize(14)
            .lineHeight(21)
            .fontColor($r('app.color.text_secondary'))

          Text('反馈与权限')
            .fontSize(16)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_primary'))
          Text('当您主动提交反馈时，应用会发送您填写的联系邮箱、反馈类型、反馈内容和应用版本，用于处理该次反馈。请勿填写密码、学号、校园卡号、Token 或其他敏感信息。网络权限用于联网功能，网络状态和 Wi-Fi 信息权限用于判断连接情况，相机权限仅在扫描设备二维码时使用，振动权限仅用于操作反馈；您可以在系统设置中管理可选权限。')
            .fontSize(14)
            .lineHeight(21)
            .fontColor($r('app.color.text_secondary'))

          Text('安全与用户选择')
            .fontSize(16)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_primary'))
          Text('应用会尽量减少收集范围，并在传输过程中使用系统提供的安全机制。网络服务或第三方内容的可用性可能变化，应用不会超出上述用途处理信息。您可以选择不登录、不提交反馈或拒绝可选权限，但相应功能可能无法使用；如需删除应用内保存的账号、设备、账单、运行和反馈信息，可使用更多页面的“清空所有信息”入口。')
            .fontSize(14)
            .lineHeight(21)
            .fontColor($r('app.color.text_secondary'))
        }
        .width('100%')
        .padding(18)
        .borderRadius(24)
        .backgroundColor($r('app.color.card_background'))
        .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
        .border({ width: 1, color: $r('app.color.card_border'), style: BorderStyle.Solid })
      }
      .width('100%')
      .padding({ bottom: 20 })
    }
    .scrollBar(BarState.Off)
    .layoutWeight(1)
    .width('100%')
    .edgeEffect(EdgeEffect.Spring)
    .transition(
      TransitionEffect.asymmetric(
        TransitionEffect.OPACITY
          .combine(TransitionEffect.scale({ x: 0.98, y: 0.98 }))
          .animation({ duration: 260, curve: Curve.EaseOut }),
        TransitionEffect.OPACITY
          .combine(TransitionEffect.scale({ x: 0.98, y: 0.98 }))
          .animation({ duration: 180, curve: Curve.EaseIn })
      )
    )
  }

  @Builder
  private creatorRow(name: string, uri: string, avatar: string): void {
    Row({ space: 14 }) {
      Image(avatar)
        .width(52)
        .height(52)
        .borderRadius(26)
      Column({ space: 4 }) {
        Text(name)
          .fontSize(16)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_primary'))
        Text(uri)
          .fontSize(12)
          .fontColor($r('app.color.accent_text'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      Text('›')
        .fontSize(26)
        .fontColor($r('app.color.text_secondary'))
    }
    .width('100%')
    .padding(12)
    .borderRadius(16)
    .backgroundColor($r('app.color.input_background'))
    .onClick(() => {
      this.openExternalLink(uri)
    })
  }

  @Builder
  private CreditsContent(): void {
    Scroll() {
      Column({ space: 16 }) {
        Column({ space: 14 }) {
          Text('制作者')
            .fontSize(20)
            .fontWeight(FontWeight.Bold)
            .fontColor($r('app.color.text_primary'))
          Text('感谢每一位参与应用开发与维护的人。')
            .fontSize(14)
            .fontColor($r('app.color.text_secondary'))
          this.creatorRow('Solbrilo', SOLBRILO_URL, SOLBRILO_AVATAR_URL)
          this.creatorRow('NCYOIGG', NCYOIGG_URL, NCYOIGG_AVATAR_URL)
        }
        .width('100%')
        .padding(18)
        .borderRadius(24)
        .backgroundColor($r('app.color.card_background'))
        .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
        .border({ width: 1, color: $r('app.color.card_border'), style: BorderStyle.Solid })
      }
      .width('100%')
      .padding({ bottom: 20 })
    }
    .scrollBar(BarState.Off)
    .layoutWeight(1)
    .width('100%')
    .edgeEffect(EdgeEffect.Spring)
    .transition(
      TransitionEffect.asymmetric(
        TransitionEffect.OPACITY
          .combine(TransitionEffect.scale({ x: 0.98, y: 0.98 }))
          .animation({ duration: 260, curve: Curve.EaseOut }),
        TransitionEffect.OPACITY
          .combine(TransitionEffect.scale({ x: 0.98, y: 0.98 }))
          .animation({ duration: 180, curve: Curve.EaseIn })
      )
    )
  }

  @Builder
  private feedbackCategoryButton(value: string, label: string): void {
    Button(label)
      .layoutWeight(1)
      .height(42)
      .type(ButtonType.Capsule)
      .backgroundColor(
        this.feedbackCategory === value
          ? $r('app.color.button_soft_background')
          : $r('app.color.card_background')
      )
      .fontColor(
        this.feedbackCategory === value
          ? $r('app.color.button_soft_text')
          : $r('app.color.text_secondary')
      )
      .border({
        width: this.feedbackCategory === value ? 1 : 0,
        color: $r('app.color.accent'),
        style: BorderStyle.Solid
      })
      .enabled(!this.submittingFeedback)
      .onClick(() => {
        this.feedbackCategory = value
        this.feedbackStatusLabel = ''
        this.feedbackSucceeded = false
      })
  }

  pageTransition() {
    PageTransitionEnter({ duration: 300, curve: Curve.EaseOut })
      .slide(SlideEffect.Right)
      .opacity(0)
    PageTransitionExit({ duration: 220, curve: Curve.EaseIn })
      .slide(SlideEffect.Right)
      .opacity(0)
  }

  build() {
    Column({ space: 18 }) {
      this.PageHeader(this.screenTitle(), this.screen === MoreScreen.HOME)
      if (this.screen === MoreScreen.HOME) {
        this.HomeContent()
      } else if (this.screen === MoreScreen.FEEDBACK) {
        this.FeedbackContent()
      } else if (this.screen === MoreScreen.PRIVACY) {
        this.PrivacyContent()
      } else {
        this.CreditsContent()
      }
    }
    .width('100%')
    .height('100%')
    .padding({ left: 20, right: 20, bottom: 20 })
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM])
    .backgroundColor($r('app.color.app_background'))
  }
}
