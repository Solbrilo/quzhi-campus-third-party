import { bundleManager, common } from '@kit.AbilityKit'
import { accessibility } from '@kit.AccessibilityKit'
import { BusinessError } from '@kit.BasicServicesKit'
import { vibrator } from '@kit.SensorServiceKit'
import window from '@ohos.window'
import { DeviceScanner } from '../components/DeviceScanner'
import { HotWaterStore } from '../common/HotWaterStore'
import { OnboardingQuoteMotion } from '../common/OnboardingQuoteMotion'
import { OnboardingResumeGate, OnboardingResumeTarget } from '../common/OnboardingResumeGate'
import { OnboardingStore } from '../common/OnboardingStore'
import { QrCodeParser } from '../common/QrCodeParser'
import { QzxyLoginClient, QZXY_LOGIN_CLIENT_VERSION } from '../common/QzxyLoginClient'
import { RuntimeDeviceInfoResolver } from '../common/RuntimeDeviceInfoResolver'

Environment.envProp('colorMode', ColorMode.LIGHT)

enum OnboardingStage {
  QUOTE = 0,
  LOGIN = 1,
  SCANNER = 2,
  COMPLETE = 3
}

class BuildIdentity {
  versionCode: number = 0
  installTime: number = 0
}

@Entry
@Component
struct Onboarding {
  private store?: HotWaterStore
  private onboardingStore?: OnboardingStore
  private loginClient: QzxyLoginClient = new QzxyLoginClient()
  private currentVersionCode: number = 0
  private currentInstallTime: number = 0
  private resumeTarget: OnboardingResumeTarget = OnboardingResumeTarget.LOGIN
  private initialStateLoadGeneration: number = 0
  private flowGeneration: number = 0
  private quoteAnimationGeneration: number = 0
  private quoteTimers: number[] = []
  private resumeQuoteOnShow: boolean = false
  private animationReduceSubscribed: boolean = false
  private animationReduceCallback: (enabled: boolean) => void = (enabled: boolean): void => {
    this.handleAnimationReduceStateChange(enabled)
  }
  private quoteOneText: string = '删繁就简三秋树'
  private quoteTwoText: string = '领异标新二月花'

  @StorageProp('statusBarHeight') statusBarHeight: number = 0
  @StorageProp('bottomSafeHeight') bottomSafeHeight: number = 0
  @StorageProp('colorMode') colorMode: ColorMode = ColorMode.LIGHT
  @State stage: number = OnboardingStage.QUOTE
  @State pageVisible: boolean = true
  @State telephone: string = ''
  @State password: string = ''
  @State busy: boolean = false
  @State binding: boolean = false
  @State finishing: boolean = false
  @State initialStateLoaded: boolean = false
  @State loginStatus: string = ''
  @State finishStatus: string = ''
  @State manualSnCode: string = ''
  @State manualSnCodeError: string = ''
  @State animationReduceEnabled: boolean = false
  @State quoteOneOpacity: number = 0
  @State quoteOneTranslateY: number = OnboardingQuoteMotion.ENTRY_TRANSLATE_Y
  @State quoteTwoOpacity: number = 0
  @State quoteTwoTranslateY: number = OnboardingQuoteMotion.ENTRY_TRANSLATE_Y
  @State authorOpacity: number = 0
  @State authorTranslateY: number = OnboardingQuoteMotion.ENTRY_TRANSLATE_Y
  @State completeOpacity: number = 0
  @State completeScale: number = 0.9

  aboutToAppear(): void {
    this.pageVisible = true
    this.initialStateLoaded = false
    this.resumeTarget = OnboardingResumeTarget.LOGIN
    this.flowGeneration++
    const context = this.getUIContext().getHostContext() as common.Context
    this.store = new HotWaterStore(context)
    this.onboardingStore = new OnboardingStore(context)
    this.loadInitialState()
    this.prepareMotion()
    this.setupImmersiveWindow()
  }

  aboutToDisappear(): void {
    this.pageVisible = false
    this.initialStateLoadGeneration++
    this.flowGeneration++
    this.resumeQuoteOnShow = false
    this.password = ''
    this.busy = false
    this.binding = false
    this.finishing = false
    this.clearQuoteTimers()
    this.unsubscribeAnimationReduceStateChange()
  }

  onPageShow(): void {
    this.pageVisible = true
    this.flowGeneration++
    this.setupImmersiveWindow()
    const shouldResumeQuote = this.resumeQuoteOnShow && this.stage === OnboardingStage.QUOTE
    this.resumeQuoteOnShow = false
    if (shouldResumeQuote) {
      this.animateQuote()
    }
  }

  onPageHide(): void {
    this.resumeQuoteOnShow = this.stage === OnboardingStage.QUOTE
    this.pageVisible = false
    this.flowGeneration++
    this.busy = false
    this.binding = false
    this.finishing = false
    this.password = ''
    this.clearQuoteTimers()
  }

  onBackPress(): boolean {
    if (this.busy || this.binding || this.finishing) {
      return true
    }
    if (this.stage === OnboardingStage.LOGIN) {
      this.goToStage(OnboardingStage.QUOTE)
      return true
    }
    if (this.stage === OnboardingStage.SCANNER) {
      this.goToStage(OnboardingStage.LOGIN)
      return true
    }
    if (this.stage === OnboardingStage.COMPLETE) {
      this.goToStage(OnboardingStage.SCANNER)
      return true
    }
    return false
  }

  private async setupImmersiveWindow(): Promise<void> {
    try {
      const context = this.getUIContext().getHostContext() as common.UIAbilityContext
      const win = await window.getLastWindow(context)
      const dark = this.colorMode === ColorMode.DARK
      await win.setWindowSystemBarProperties({
        statusBarColor: '#00FFFFFF',
        statusBarContentColor: dark ? '#F2F7FF' : '#11213B',
        navigationBarColor: dark ? '#08111F' : '#EAF2FF',
        navigationBarContentColor: dark ? '#F2F7FF' : '#11213B'
      })
    } catch (error) {
    }
  }

  private async loadInitialState(): Promise<void> {
    const loadGeneration = ++this.initialStateLoadGeneration
    try {
      const identity = await this.readCurrentBuildIdentity()
      if (loadGeneration !== this.initialStateLoadGeneration) {
        return
      }
      this.currentVersionCode = identity.versionCode
      this.currentInstallTime = identity.installTime
    } catch (error) {
      if (loadGeneration !== this.initialStateLoadGeneration) {
        return
      }
      this.currentVersionCode = 0
      this.currentInstallTime = 0
    }
    if (!this.store) {
      this.initialStateLoaded = true
      return
    }
    try {
      const config = await this.store.loadConfig()
      if (loadGeneration !== this.initialStateLoadGeneration) {
        return
      }
      this.resumeTarget = OnboardingResumeGate.resolve(config)
      this.telephone = config.telephone
    } catch (error) {
      if (loadGeneration === this.initialStateLoadGeneration) {
        this.resumeTarget = OnboardingResumeTarget.LOGIN
      }
    } finally {
      if (loadGeneration === this.initialStateLoadGeneration) {
        this.initialStateLoaded = true
      }
    }
  }

  private startOnboarding(): void {
    if (!this.initialStateLoaded || this.busy || this.finishing || !this.pageVisible ||
      this.stage !== OnboardingStage.QUOTE) {
      return
    }
    if (this.resumeTarget === OnboardingResumeTarget.COMPLETE) {
      this.finishOnboarding()
      return
    }
    if (this.resumeTarget === OnboardingResumeTarget.SCANNER) {
      this.goToStage(OnboardingStage.SCANNER)
      return
    }
    this.goToStage(OnboardingStage.LOGIN)
  }

  private async readCurrentBuildIdentity(): Promise<BuildIdentity> {
    const bundleInfo = await bundleManager.getBundleInfoForSelf(
      bundleManager.BundleFlag.GET_BUNDLE_INFO_DEFAULT
    )
    const identity = new BuildIdentity()
    const versionCode = Number(bundleInfo.versionCode)
    const installTime = Number(bundleInfo.installTime)
    identity.versionCode = Number.isFinite(versionCode) && versionCode > 0 ? versionCode : 0
    identity.installTime = Number.isFinite(installTime) && installTime > 0 ? installTime : 0
    return identity
  }

  private prepareMotion(): void {
    try {
      this.animationReduceEnabled = accessibility.isAnimationReduceEnabledSync()
    } catch (error) {
      this.animationReduceEnabled = false
    }
    this.subscribeAnimationReduceStateChange()
    if (this.pageVisible && this.stage === OnboardingStage.QUOTE) {
      this.animateQuote()
    }
  }

  private subscribeAnimationReduceStateChange(): void {
    if (this.animationReduceSubscribed) {
      return
    }
    try {
      accessibility.onAnimationReduceStateChange(this.animationReduceCallback)
      this.animationReduceSubscribed = true
    } catch (error) {
      this.animationReduceSubscribed = false
    }
  }

  private unsubscribeAnimationReduceStateChange(): void {
    if (!this.animationReduceSubscribed) {
      return
    }
    try {
      accessibility.offAnimationReduceStateChange(this.animationReduceCallback)
    } catch (error) {
    } finally {
      this.animationReduceSubscribed = false
    }
  }

  private handleAnimationReduceStateChange(enabled: boolean): void {
    if (enabled === this.animationReduceEnabled) {
      return
    }
    this.animationReduceEnabled = enabled
    if (this.pageVisible && this.stage === OnboardingStage.QUOTE) {
      this.animateQuote()
    }
  }

  private motionDuration(baseDuration: number): number {
    return this.animationReduceEnabled ? 0 : baseDuration
  }

  private clearQuoteTimers(): void {
    this.quoteAnimationGeneration++
    for (let index = 0; index < this.quoteTimers.length; index++) {
      clearTimeout(this.quoteTimers[index])
    }
    this.quoteTimers = []
  }

  private isQuoteAnimationCurrent(animationGeneration: number): boolean {
    return this.pageVisible &&
      this.stage === OnboardingStage.QUOTE &&
      animationGeneration === this.quoteAnimationGeneration
  }

  private revealQuoteSentence(quoteIndex: number, animationGeneration: number): void {
    if (!this.isQuoteAnimationCurrent(animationGeneration)) {
      return
    }
    this.getUIContext().animateTo({
      duration: this.motionDuration(OnboardingQuoteMotion.SENTENCE_DURATION_MILLIS),
      curve: Curve.EaseOut
    }, () => {
      if (quoteIndex === 1) {
        this.quoteOneOpacity = 1
        this.quoteOneTranslateY = 0
      } else {
        this.quoteTwoOpacity = 1
        this.quoteTwoTranslateY = 0
      }
    })
  }

  private revealQuoteAuthor(animationGeneration: number): void {
    if (!this.isQuoteAnimationCurrent(animationGeneration)) {
      return
    }
    this.getUIContext().animateTo({
      duration: this.motionDuration(OnboardingQuoteMotion.AUTHOR_DURATION_MILLIS),
      curve: Curve.EaseOut
    }, () => {
      this.authorOpacity = 1
      this.authorTranslateY = 0
    })
  }

  private triggerQuoteHaptic(animationGeneration: number): void {
    if (this.animationReduceEnabled || !this.isQuoteAnimationCurrent(animationGeneration)) {
      return
    }
    try {
      vibrator.startVibration({
        type: 'preset',
        effectId: 'haptic.effect.hard',
        count: 1
      }, {
        usage: 'touch'
      }, (error: BusinessError) => {
        if (error) {
          this.triggerQuoteHapticFallback(animationGeneration)
        }
      })
    } catch (error) {
      this.triggerQuoteHapticFallback(animationGeneration)
    }
  }

  private triggerQuoteHapticFallback(animationGeneration: number): void {
    if (this.animationReduceEnabled || !this.isQuoteAnimationCurrent(animationGeneration)) {
      return
    }
    try {
      vibrator.startVibration({
        type: 'time',
        duration: 30
      }, {
        usage: 'touch'
      }, (_error: BusinessError) => {
      })
    } catch (error) {
    }
  }

  private animateQuote(): void {
    this.clearQuoteTimers()
    const animationGeneration = this.quoteAnimationGeneration
    this.quoteOneOpacity = 0
    this.quoteOneTranslateY = OnboardingQuoteMotion.ENTRY_TRANSLATE_Y
    this.quoteTwoOpacity = 0
    this.quoteTwoTranslateY = OnboardingQuoteMotion.ENTRY_TRANSLATE_Y
    this.authorOpacity = 0
    this.authorTranslateY = OnboardingQuoteMotion.ENTRY_TRANSLATE_Y
    if (this.animationReduceEnabled) {
      this.quoteOneOpacity = 1
      this.quoteOneTranslateY = 0
      this.quoteTwoOpacity = 1
      this.quoteTwoTranslateY = 0
      this.authorOpacity = 1
      this.authorTranslateY = 0
      return
    }

    this.quoteTimers.push(setTimeout(() => {
      this.revealQuoteSentence(1, animationGeneration)
    }, this.motionDuration(OnboardingQuoteMotion.INITIAL_DELAY_MILLIS)))

    const firstQuoteComplete = OnboardingQuoteMotion.firstSentenceCompleteAtMillis()
    this.quoteTimers.push(setTimeout(() => {
      this.triggerQuoteHaptic(animationGeneration)
    }, this.motionDuration(firstQuoteComplete)))

    const secondQuoteStart = OnboardingQuoteMotion.secondSentenceStartAtMillis()
    this.quoteTimers.push(setTimeout(() => {
      this.revealQuoteSentence(2, animationGeneration)
    }, this.motionDuration(secondQuoteStart)))

    const secondQuoteComplete = OnboardingQuoteMotion.secondSentenceCompleteAtMillis()
    this.quoteTimers.push(setTimeout(() => {
      this.triggerQuoteHaptic(animationGeneration)
    }, this.motionDuration(secondQuoteComplete)))
    this.quoteTimers.push(setTimeout(() => {
      this.revealQuoteAuthor(animationGeneration)
    }, this.motionDuration(OnboardingQuoteMotion.authorStartAtMillis())))
  }

  private animateComplete(): void {
    this.completeOpacity = 0
    this.completeScale = 0.9
    if (this.animationReduceEnabled) {
      this.completeOpacity = 1
      this.completeScale = 1
      return
    }
    const timer = setTimeout(() => {
      if (!this.pageVisible || this.stage !== OnboardingStage.COMPLETE) {
        return
      }
      this.getUIContext().animateTo({ duration: this.motionDuration(520), curve: Curve.EaseOut }, () => {
        this.completeOpacity = 1
        this.completeScale = 1
      })
    }, this.motionDuration(80))
    this.quoteTimers.push(timer)
  }

  private goToStage(target: OnboardingStage): void {
    if (this.stage === OnboardingStage.LOGIN && target !== OnboardingStage.LOGIN) {
      this.password = ''
      this.loginStatus = ''
    }
    this.clearQuoteTimers()
    this.getUIContext().animateTo({
      duration: this.motionDuration(300),
      curve: Curve.EaseInOut
    }, () => {
      this.stage = target
      if (target === OnboardingStage.QUOTE) {
        this.animateQuote()
      } else if (target === OnboardingStage.COMPLETE) {
        this.animateComplete()
      }
    })
  }

  private isFlowCurrent(generation: number, expectedStage: OnboardingStage): boolean {
    return this.pageVisible && generation === this.flowGeneration && this.stage === expectedStage
  }

  private async login(): Promise<void> {
    if (!this.store || this.busy || !this.pageVisible || this.stage !== OnboardingStage.LOGIN) {
      return
    }
    if (this.telephone.trim().length === 0 || this.password.length === 0) {
      this.loginStatus = '请输入趣智校园手机号和密码'
      return
    }
    this.busy = true
    this.loginStatus = ''
    const generation = this.flowGeneration
    try {
      const result = await this.loginClient.login(
        this.telephone.trim(),
        this.password,
        'harmonyos',
        QZXY_LOGIN_CLIENT_VERSION
      )
      if (!this.isFlowCurrent(generation, OnboardingStage.LOGIN)) {
        return
      }
      const existing = await this.store.loadConfig()
      if (!this.isFlowCurrent(generation, OnboardingStage.LOGIN)) {
        return
      }
      const config = RuntimeDeviceInfoResolver.enrichConfig(result.mergeInto(existing))
      await this.store.applyQzxyLogin(config)
      if (this.isFlowCurrent(generation, OnboardingStage.LOGIN)) {
        this.goToStage(OnboardingStage.SCANNER)
      }
    } catch (error) {
      if (this.isFlowCurrent(generation, OnboardingStage.LOGIN)) {
        this.loginStatus = QzxyLoginClient.displayError(error) || '登录请求未完成，请稍后重试'
      }
    } finally {
      if (this.pageVisible && generation === this.flowGeneration) {
        this.password = ''
        this.busy = false
      }
    }
  }

  private async bindSnCode(snCode: string): Promise<boolean> {
    if (!this.store || this.binding || !this.pageVisible || this.stage !== OnboardingStage.SCANNER) {
      return false
    }
    this.binding = true
    const generation = this.flowGeneration
    try {
      const config = await this.store.loadConfig()
      if (!this.isFlowCurrent(generation, OnboardingStage.SCANNER)) {
        return false
      }
      config.snCode = snCode
      await this.store.saveConfig(config)
      if (this.isFlowCurrent(generation, OnboardingStage.SCANNER)) {
        this.goToStage(OnboardingStage.COMPLETE)
      }
      return true
    } catch (error) {
      if (this.isFlowCurrent(generation, OnboardingStage.SCANNER)) {
        this.getUIContext().getPromptAction().showToast({ message: '设备码保存失败，请重试' })
      }
      return false
    } finally {
      if (this.pageVisible && generation === this.flowGeneration) {
        this.binding = false
      }
    }
  }

  private submitManualSnCode(): void {
    if (this.binding || this.finishing || this.stage !== OnboardingStage.SCANNER) {
      return
    }
    const normalized = QrCodeParser.normalizeSnCode(this.manualSnCode)
    if (normalized === null) {
      this.manualSnCodeError = '请输入 12 位十六进制 snCode'
      return
    }
    this.manualSnCode = normalized
    this.manualSnCodeError = ''
    this.bindSnCode(normalized)
  }

  private async finishOnboarding(): Promise<void> {
    if (!this.onboardingStore || this.finishing || !this.pageVisible) {
      return
    }
    this.finishing = true
    this.finishStatus = ''
    const generation = this.flowGeneration
    try {
      let versionCode = this.currentVersionCode
      if (versionCode <= 0 || this.currentInstallTime <= 0) {
        const identity = await this.readCurrentBuildIdentity()
        versionCode = identity.versionCode
        this.currentInstallTime = identity.installTime
      }
      if (versionCode <= 0) {
        throw new Error('无法读取当前应用版本')
      }
      await this.onboardingStore.markCompleted(versionCode, this.currentInstallTime)
      this.currentVersionCode = versionCode
      if (!this.pageVisible || generation !== this.flowGeneration) {
        return
      }
      await this.getUIContext().getRouter().replaceUrl({ url: 'pages/Index' })
    } catch (error) {
      if (this.pageVisible && generation === this.flowGeneration) {
        this.finishStatus = '暂时无法完成引导，请重试'
        this.getUIContext().getPromptAction().showToast({ message: this.finishStatus })
      }
    } finally {
      if (this.pageVisible && generation === this.flowGeneration) {
        this.finishing = false
      }
    }
  }

  @Builder
  private BackButton(onClick: () => void): void {
    Button({ type: ButtonType.Circle }) {
      SymbolGlyph($r('sys.symbol.chevron_backward'))
        .fontSize(23)
        .fontColor([$r('app.color.text_primary')])
    }
    .width(48)
    .height(48)
    .backgroundColor($r('app.color.card_background'))
    .enabled(!this.busy && !this.binding && !this.finishing)
    .accessibilityText('返回')
    .onClick(onClick)
  }

  @Builder
  private VerticalText(text: string, fontSize: number): void {
    Column({ space: 4 }) {
      ForEach(text.split(''), (character: string, index: number) => {
        Text(character)
          .width(38)
          .height(40)
          .textAlign(TextAlign.Center)
          .fontSize(fontSize)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_primary'))
      }, (character: string, index: number) => `${index}-${character}`)
    }
    .width(38)
    .alignItems(HorizontalAlign.Center)
  }

  @Builder
  private AuthorMark(): void {
    Column({ space: 9 }) {
      Divider()
        .vertical(true)
        .height(25)
        .strokeWidth(1)
        .color($r('app.color.text_secondary'))

      Column({ space: 2 }) {
        ForEach('郑板桥'.split(''), (character: string) => {
          Text(character)
            .width(22)
            .height(22)
            .textAlign(TextAlign.Center)
            .fontSize(14)
            .fontWeight(FontWeight.Regular)
            .fontColor($r('app.color.text_secondary'))
        }, (character: string, index: number) => `${index}-${character}`)
      }
      .alignItems(HorizontalAlign.Center)
    }
    .width(22)
    .alignItems(HorizontalAlign.Center)
    .opacity(this.authorOpacity)
    .translate({ y: this.authorTranslateY })
  }

  @Builder
  private QuotePage(): void {
    Column() {
      Stack({ alignContent: Alignment.Center }) {
        Row({ space: 26 }) {
          Column() {
            this.AuthorMark()
          }
          .translate({ y: 74 })

          Column() {
            Column() {
              this.VerticalText(this.quoteTwoText, 28)
            }
            .opacity(this.quoteTwoOpacity)
            .translate({ y: this.quoteTwoTranslateY })
          }
          .translate({ y: 20 })

          Column() {
            Column() {
              this.VerticalText(this.quoteOneText, 28)
            }
            .opacity(this.quoteOneOpacity)
            .translate({ y: this.quoteOneTranslateY })
          }
          .translate({ y: -18 })
        }
        .alignItems(VerticalAlign.Center)
        .translate({ y: -8 })
      }
      .width('100%')
      .layoutWeight(1)

      Button('快速开始')
        .type(ButtonType.Capsule)
        .width('100%')
        .height(52)
        .backgroundColor($r('app.color.accent_button_background'))
        .fontColor($r('app.color.accent_button_text'))
        .margin({ top: 12 })
        .enabled(this.initialStateLoaded && !this.busy && !this.finishing)
        .onClick(() => this.startOnboarding())
    }
    .width('100%')
    .height('100%')
    .transition(TransitionEffect.OPACITY.animation({
      duration: this.motionDuration(300),
      curve: Curve.EaseOut
    }))
  }

  @Builder
  private LoginPage(): void {
    Column() {
      Row({ space: 14 }) {
        this.BackButton(() => this.goToStage(OnboardingStage.QUOTE))
        Column({ space: 3 }) {
          Text('登录趣智校园')
            .fontSize(28)
            .fontWeight(FontWeight.Bold)
            .fontColor($r('app.color.text_primary'))
          Text('连接你的校园账号')
            .fontSize(14)
            .fontColor($r('app.color.text_secondary'))
        }
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)

      Scroll() {
        Column({ space: 14 }) {
          Column({ space: 6 }) {
            Text('手机号')
              .fontSize(12)
              .fontColor($r('app.color.text_secondary'))
            TextInput({ text: this.telephone, placeholder: '趣智校园手机号' })
              .type(InputType.PhoneNumber)
              .height(52)
              .fontColor($r('app.color.text_primary'))
              .placeholderColor($r('app.color.text_secondary'))
              .backgroundColor($r('app.color.input_background'))
              .borderRadius(16)
              .padding({ left: 16, right: 16 })
              .onChange((value: string) => this.telephone = value)
          }
          Column({ space: 6 }) {
            Text('密码')
              .fontSize(12)
              .fontColor($r('app.color.text_secondary'))
            TextInput({ text: this.password, placeholder: '趣智校园密码' })
              .type(InputType.Password)
              .showPasswordIcon(true)
              .height(52)
              .fontColor($r('app.color.text_primary'))
              .placeholderColor($r('app.color.text_secondary'))
              .backgroundColor($r('app.color.input_background'))
              .borderRadius(16)
              .padding({ left: 16, right: 16 })
              .onChange((value: string) => this.password = value)
          }

          if (this.loginStatus.length > 0) {
            Text(this.loginStatus)
              .fontSize(13)
              .fontColor($r('app.color.error_text'))
              .width('100%')
          }

          Button(this.busy ? '正在登录' : '登录')
            .type(ButtonType.Capsule)
            .width('100%')
            .height(52)
            .backgroundColor($r('app.color.accent_button_background'))
            .fontColor($r('app.color.accent_button_text'))
            .enabled(!this.busy && this.telephone.trim().length > 0 && this.password.length > 0)
            .onClick(() => this.login())
        }
        .width('100%')
        .padding(20)
        .borderRadius(24)
        .backgroundColor($r('app.color.card_background'))
        .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
        .border({ width: 1, color: $r('app.color.card_border'), style: BorderStyle.Solid })
        .margin({ top: 28, bottom: 20 })
      }
      .layoutWeight(1)
      .width('100%')
      .scrollBar(BarState.Off)

      Button('跳过登录')
        .type(ButtonType.Capsule)
        .height(48)
        .backgroundColor($r('app.color.input_background'))
        .fontColor($r('app.color.text_secondary'))
        .enabled(!this.busy && !this.finishing)
        .onClick(() => this.goToStage(OnboardingStage.SCANNER))
    }
    .width('100%')
    .height('100%')
    .transition(TransitionEffect.OPACITY.animation({
      duration: this.motionDuration(300),
      curve: Curve.EaseOut
    }))
  }

  @Builder
  private ScannerPage(): void {
    Column() {
      Row({ space: 14 }) {
        this.BackButton(() => this.goToStage(OnboardingStage.LOGIN))
        Text('绑定你的热水器')
          .fontSize(24)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.text_primary'))
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)

      Blank()
      if (this.pageVisible) {
        DeviceScanner({
          onScanned: (snCode: string) => this.bindSnCode(snCode)
        })
      }

      Column({ space: 6 }) {
        Row({ space: 10 }) {
          TextInput({ text: this.manualSnCode, placeholder: '手动输入 12 位 snCode' })
            .layoutWeight(1)
            .height(48)
            .maxLength(32)
            .type(InputType.Normal)
            .fontSize(15)
            .fontColor($r('app.color.text_primary'))
            .placeholderColor($r('app.color.text_secondary'))
            .backgroundColor($r('app.color.input_background'))
            .borderRadius(16)
            .enabled(!this.binding && !this.finishing)
            .onChange((value: string) => {
              this.manualSnCode = value
              this.manualSnCodeError = ''
            })

          Button({ type: ButtonType.Circle }) {
            SymbolGlyph($r('sys.symbol.checkmark'))
              .fontSize(22)
              .fontColor([$r('app.color.text_primary')])
          }
          .width(48)
          .height(48)
          .backgroundColor($r('app.color.button_soft_background'))
          .enabled(!this.binding && !this.finishing && this.manualSnCode.trim().length > 0)
          .accessibilityText('确认设备码')
          .onClick(() => this.submitManualSnCode())
        }
        .width('100%')

        if (this.manualSnCodeError.length > 0) {
          Text(this.manualSnCodeError)
            .fontSize(12)
            .fontColor($r('app.color.error_text'))
            .width('100%')
        }
      }
      .width('100%')
      .constraintSize({ maxWidth: 300 })
      .margin({ top: 10 })

      Blank()
      Button(this.finishing ? '正在进入' : '跳过绑定')
        .type(ButtonType.Capsule)
        .height(48)
        .backgroundColor($r('app.color.input_background'))
        .fontColor($r('app.color.text_secondary'))
        .enabled(!this.binding && !this.finishing)
        .onClick(() => this.finishOnboarding())
    }
    .width('100%')
    .height('100%')
    .transition(TransitionEffect.OPACITY.animation({
      duration: this.motionDuration(300),
      curve: Curve.EaseOut
    }))
  }

  @Builder
  private CompletePage(): void {
    Column({ space: 24 }) {
      Button({ type: ButtonType.Circle }) {
        SymbolGlyph($r('sys.symbol.checkmark'))
          .fontSize(36)
          .fontColor([$r('app.color.accent_text')])
      }
      .width(72)
      .height(72)
      .enabled(false)
      .backgroundColor($r('app.color.card_background'))

      Text('恭喜你，完成配置！')
        .fontSize(26)
        .fontWeight(FontWeight.Bold)
        .fontColor($r('app.color.text_primary'))

      if (this.finishStatus.length > 0) {
        Text(this.finishStatus)
          .fontSize(13)
          .fontColor($r('app.color.error_text'))
      }

      Button(this.finishing ? '正在进入' : '立即开始')
        .type(ButtonType.Capsule)
        .width('100%')
        .height(52)
        .backgroundColor($r('app.color.accent_button_background'))
        .fontColor($r('app.color.accent_button_text'))
        .enabled(!this.finishing)
        .margin({ top: 10 })
        .onClick(() => this.finishOnboarding())
    }
    .alignItems(HorizontalAlign.Center)
    .width('100%')
    .opacity(this.completeOpacity)
    .scale({ x: this.completeScale, y: this.completeScale })
    .transition(TransitionEffect.OPACITY.animation({
      duration: this.motionDuration(300),
      curve: Curve.EaseOut
    }))
  }

  build() {
    Stack({ alignContent: Alignment.Center }) {
      if (this.stage === OnboardingStage.QUOTE) {
        this.QuotePage()
      } else if (this.stage === OnboardingStage.LOGIN) {
        this.LoginPage()
      } else if (this.stage === OnboardingStage.SCANNER) {
        this.ScannerPage()
      } else {
        this.CompletePage()
      }
    }
    .width('100%')
    .height('100%')
    .padding({
      left: 20,
      right: 20,
      top: 18 + this.statusBarHeight,
      bottom: 18 + this.bottomSafeHeight
    })
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM])
    .backgroundColor($r('app.color.app_background'))
  }
}
