import type common from '@ohos.app.ability.common'
import window from '@ohos.window'
import { ConfigFlowBridge } from '../common/ConfigFlowBridge'
import { HotWaterStore } from '../common/HotWaterStore'
import { QzxyLoginClient, QZXY_LOGIN_CLIENT_VERSION } from '../common/QzxyLoginClient'
import { RuntimeDeviceInfoResolver } from '../common/RuntimeDeviceInfoResolver'

Environment.envProp('colorMode', ColorMode.LIGHT)

@Entry
@Component
struct Login {
  private store?: HotWaterStore
  private client: QzxyLoginClient = new QzxyLoginClient()
  private pageVisible: boolean = false
  private loginGeneration: number = 0
  @StorageProp('statusBarHeight') statusBarHeight: number = 0
  @StorageProp('bottomSafeHeight') bottomSafeHeight: number = 0
  @StorageProp('colorMode') colorMode: ColorMode = ColorMode.LIGHT
  @State telephone: string = ''
  @State password: string = ''
  @State busy: boolean = false
  @State statusText: string = ''

  aboutToAppear(): void {
    this.pageVisible = true
    const context = this.getUIContext().getHostContext() as common.Context
    this.store = new HotWaterStore(context)
    const generation = ++this.loginGeneration
    this.loadTelephone(generation)
    this.setupImmersiveWindow()
  }

  onPageShow(): void {
    this.pageVisible = true
  }

  onPageHide(): void {
    this.pageVisible = false
    this.loginGeneration++
    this.password = ''
    this.busy = false
  }

  aboutToDisappear(): void {
    this.pageVisible = false
    this.loginGeneration++
    this.password = ''
    this.busy = false
  }

  onBackPress(): boolean {
    return this.busy
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

  private async loadTelephone(generation: number): Promise<void> {
    if (!this.store) {
      return
    }
    try {
      const config = await this.store.loadConfig()
      if (this.pageVisible && generation === this.loginGeneration) {
        this.telephone = config.telephone
      }
    } catch (error) {
    }
  }

  private async login(): Promise<void> {
    if (!this.store || this.busy || !this.pageVisible) {
      return
    }
    if (this.telephone.trim().length === 0 || this.password.length === 0) {
      this.statusText = '请输入趣智校园手机号和密码'
      return
    }
    this.busy = true
    this.statusText = ''
    const generation = ++this.loginGeneration
    const dataClearGeneration = ConfigFlowBridge.getDataClearGeneration()
    try {
      const result = await this.client.login(
        this.telephone.trim(),
        this.password,
        'harmonyos',
        QZXY_LOGIN_CLIENT_VERSION
      )
      if (!this.pageVisible || generation !== this.loginGeneration) {
        return
      }
      const existing = await this.store.loadConfig()
      if (!this.pageVisible || generation !== this.loginGeneration) {
        return
      }
      const config = RuntimeDeviceInfoResolver.enrichConfig(result.mergeInto(existing))
      const persisted = await this.store.applyQzxyLogin(config, dataClearGeneration)
      if (!persisted || !ConfigFlowBridge.isDataClearGenerationCurrent(dataClearGeneration)) {
        return
      }
      ConfigFlowBridge.markLoginConfigChanged()
      this.password = ''
      if (!this.pageVisible || generation !== this.loginGeneration) {
        return
      }
      try {
        this.getUIContext().getPromptAction().showToast({
          message: config.snCode.length > 0 ? '登录成功，账号和设备配置已保存' :
            '登录成功，请继续绑定热水器'
        })
        this.getUIContext().getRouter().back()
      } catch (navigationError) {
        this.statusText = '登录已保存，请返回配置页继续操作'
      }
    } catch (error) {
      if (this.pageVisible && generation === this.loginGeneration) {
        this.password = ''
        this.statusText = QzxyLoginClient.displayError(error) || '登录请求未完成，请稍后重试'
      }
    } finally {
      if (this.pageVisible && generation === this.loginGeneration) {
        this.password = ''
        this.busy = false
      }
    }
  }

  private skipLogin(): void {
    if (this.busy || !this.pageVisible) {
      return
    }
    ConfigFlowBridge.markLoginSkipped()
    this.password = ''
    this.statusText = ''
    try {
      this.getUIContext().getPromptAction().showToast({
        message: '已跳过登录，可在配置页导入或补充配置'
      })
      this.getUIContext().getRouter().back()
    } catch (error) {
      this.statusText = '已跳过登录，请返回配置页导入或补充配置'
    }
  }

  @Builder
  private InputField(label: string, value: string, placeholder: string,
    inputType: InputType, onChange: (value: string) => void): void {
    Column({ space: 6 }) {
      Text(label)
        .fontSize(12)
        .fontColor($r('app.color.text_secondary'))
      TextInput({ text: value, placeholder: placeholder })
        .type(inputType)
        .showPasswordIcon(inputType === InputType.Password)
        .height(52)
        .fontSize(15)
        .fontColor($r('app.color.text_primary'))
        .placeholderColor($r('app.color.text_secondary'))
        .backgroundColor($r('app.color.input_background'))
        .borderRadius(16)
        .padding({ left: 16, right: 16 })
        .onChange((text: string) => onChange(text))
    }
    .width('100%')
  }

  build() {
    Column() {
      Row({ space: 14 }) {
        Button({ type: ButtonType.Circle }) {
          SymbolGlyph($r('sys.symbol.chevron_backward'))
            .fontSize(23)
            .fontColor([$r('app.color.text_primary')])
        }
        .width(48)
        .height(48)
        .backgroundColor($r('app.color.card_background'))
        .enabled(!this.busy)
        .accessibilityText('返回')
        .onClick(() => this.getUIContext().getRouter().back())

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
          this.InputField('手机号', this.telephone, '趣智校园手机号', InputType.PhoneNumber,
            (value: string) => this.telephone = value)
          this.InputField('密码', this.password, '趣智校园密码', InputType.Password,
            (value: string) => this.password = value)

          if (this.statusText.length > 0) {
            Text(this.statusText)
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

          Button('跳过登录')
            .type(ButtonType.Capsule)
            .width('100%')
            .height(46)
            .backgroundColor($r('app.color.input_background'))
            .fontColor($r('app.color.text_secondary'))
            .enabled(!this.busy)
            .onClick(() => this.skipLogin())
        }
        .width('100%')
        .padding(20)
        .borderRadius(24)
        .backgroundColor($r('app.color.card_background'))
        .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
        .border({ width: 1, color: $r('app.color.card_border'), style: BorderStyle.Solid })
        .margin({ top: 28, bottom: 24 })
      }
      .layoutWeight(1)
      .width('100%')
      .scrollBar(BarState.Off)
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
