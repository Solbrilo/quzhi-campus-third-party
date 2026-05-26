import common from '@ohos.app.ability.common'
import picker from '@ohos.file.picker'
import fs from '@ohos.file.fs'
import window from '@ohos.window'
import { CaptureParser } from '../common/CaptureParser'
import { DashboardSnapshot, HotWaterConfig, RuntimeDeviceInfo } from '../common/HotWaterModels'
import { HotWaterRepository } from '../common/HotWaterRepository'
import { HotWaterStore } from '../common/HotWaterStore'
import { LaunchActionBridge } from '../common/LaunchActionBridge'
import { RuntimeDeviceInfoResolver } from '../common/RuntimeDeviceInfoResolver'

enum HomePage {
  OPERATIONS = 0,
  CONFIG = 1
}

interface FieldInputParams {
  text: string
  placeholder: string
  onChange: (value: string) => void
}

@Entry
@Component
struct Index {
  private hostContext?: common.Context
  private store?: HotWaterStore
  private repository?: HotWaterRepository

  @State activePage: number = HomePage.OPERATIONS
  @State busy: boolean = false
  @State refreshingBalance: boolean = false
  @State liveStatus: string = '先导入 .chls 或补全配置。'

  @State balanceLabel: string = '--'
  @State userLabel: string = '未导入账号'
  @State deviceName: string = '热水设备待刷新'
  @State locationLabel: string = '先完成配置再刷新'
  @State statusLabel: string = '当前配置还不完整。'
  @State hasCachedOrder: boolean = false

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

  aboutToAppear(): void {
    this.hostContext = this.getUIContext().getHostContext() as common.Context
    this.store = new HotWaterStore(this.hostContext)
    this.repository = new HotWaterRepository(this.store)
    this.initialize()
    this.setupImmersiveWindow()
  }

  onPageShow(): void {
    this.consumePendingLaunchAction()
    if (this.busy) {
      this.runRingStep()
    }
  }

  private async setupImmersiveWindow(): Promise<void> {
    try {
      const ctx = this.getUIContext().getHostContext() as common.UIAbilityContext
      const win = await window.getLastWindow(ctx)
      await win.setWindowSystemBarProperties({
        statusBarColor: '#00FFFFFF',
        statusBarContentColor: '#11213B',
        navigationBarColor: '#EAF2FF',
        navigationBarContentColor: '#11213B'
      })
    } catch (err) {
      // 降级处理
    }
  }

  private async initialize(): Promise<void> {
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

    if (config.isComplete()) {
      await this.refreshBalance(false)
    }
    await this.consumePendingLaunchAction()
  }

  private applyRuntimeInfo(runtimeInfo: RuntimeDeviceInfo): void {
    this.systemInfoLabel = runtimeInfo.systemLabel
    this.macAddressLabel = runtimeInfo.macAddressLabel
  }

  private applySnapshot(snapshot: DashboardSnapshot): void {
    this.balanceLabel = snapshot.balanceLabel
    this.userLabel = snapshot.userLabel
    this.deviceName = snapshot.deviceName
    this.locationLabel = snapshot.locationLabel
    this.hasCachedOrder = snapshot.hasCachedOrder
    this.statusLabel = snapshot.statusLabel
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

  private async toggleHotWater(): Promise<void> {
    if (!this.repository) {
      return
    }
    const config = await this.collectConfig()
    if (!config.isComplete()) {
      this.switchPage(HomePage.CONFIG)
      this.liveStatus = '配置还不完整，先导入 .chls 或补全字段。'
      return
    }
    this.busy = true
    this.liveStatus = this.hasCachedOrder ? '正在关闭热水…' : '正在开启热水…'
    this.runRingStep()
    try {
      const result = this.hasCachedOrder ? await this.repository.close(config) : await this.repository.open(config)
      this.applySnapshot(result.snapshot ?? this.placeholderSnapshot(config))
      this.liveStatus = result.summary.split('\n')[0]
    } catch (error) {
      this.liveStatus = `操作失败：${String(error)}`
    } finally {
      this.busy = false
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
            ['rgba(255,255,255,0.55)', 0.0],
            ['rgba(255,255,255,0.35)', 0.5],
            ['rgba(255,255,255,0.50)', 1.0]
          ]
        })
        .backgroundBlurStyle(BlurStyle.BACKGROUND_REGULAR)
        .backdropBlur(28)
        .border({
          width: 0.5,
          color: '#40FFFFFF',
          style: BorderStyle.Solid
        })
        .shadow({
          radius: 16,
          color: '#1A000000',
          offsetX: 0,
          offsetY: 4
        })
        .shadow({
          radius: 8,
          color: '#0D000000',
          offsetX: 0,
          offsetY: 1
        })

      // 顶部高光反射
      Row()
        .width('88%')
        .height(0.5)
        .backgroundColor('#60FFFFFF')
        .borderRadius(28)
        .position({ x: '6%', y: 6 })

      // 底部微光
      Row()
        .width('60%')
        .height(0.5)
        .backgroundColor('#20FFFFFF')
        .borderRadius(14)
        .position({ x: '20%', y: 43 })

      // 滑块 - 带光晕渐变
      Row()
        .width('46%')
        .height(36)
        .borderRadius(22)
        .linearGradient({
          angle: 135,
          colors: [
            ['rgba(111,151,255,0.95)', 0.0],
            ['rgba(143,180,255,0.85)', 1.0]
          ]
        })
        .shadow({
          radius: 14,
          color: '#506F97FF',
          offsetX: 0,
          offsetY: 4
        })
        .shadow({
          radius: 4,
          color: '#206F97FF',
          offsetX: 0,
          offsetY: 1
        })
        .offset({
          x: this.activePage === HomePage.OPERATIONS ? '-23%' : '23%'
        })
        .animation({
          duration: 350,
          curve: Curve.EaseInOut
        })

      // 滑块顶部高光
      Row()
        .width('30%')
        .height(0.5)
        .backgroundColor('#60FFFFFF')
        .borderRadius(10)
        .position({
          x: this.activePage === HomePage.OPERATIONS ? '12%' : '58%',
          y: 10
        })
        .animation({
          duration: 350,
          curve: Curve.EaseInOut
        })

      Row({ space: 0 }) {
        Text('操作页')
          .fontSize(14)
          .fontWeight(this.activePage === HomePage.OPERATIONS ? FontWeight.Medium : FontWeight.Normal)
          .fontColor(this.activePage === HomePage.OPERATIONS ? Color.White : '#8090A8')
          .width('50%')
          .height(48)
          .textAlign(TextAlign.Center)
          .onClick(() => {
            this.switchPage(HomePage.OPERATIONS)
          })

        Text('配置页')
          .fontSize(14)
          .fontWeight(this.activePage === HomePage.CONFIG ? FontWeight.Medium : FontWeight.Normal)
          .fontColor(this.activePage === HomePage.CONFIG ? Color.White : '#8090A8')
          .width('50%')
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
      .backgroundColor('#18FFFFFF')
      .backgroundBlurStyle(BlurStyle.COMPONENT_REGULAR)
      .borderRadius(14)
      .padding({ left: 16, right: 16, top: 12, bottom: 12 })
      .border({
        width: 1,
        color: '#18FFFFFF',
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
          .fontColor('#63708A')
          .width('100%')
          .textAlign(TextAlign.Start)

        Row({ space: 10 }) {
          Text(`¥ ${this.balanceLabel}`)
            .fontSize(40)
            .fontWeight(FontWeight.Bold)
            .fontColor('#11213B')
            .layoutWeight(1)

          Stack() {
            Button('刷新')
              .type(ButtonType.Capsule)
              .fontSize(13)
              .backgroundColor('#D9E9FF')
              .fontColor(this.refreshingBalance ? '#D9E9FF00' : '#23457D')
              .enabled(!this.refreshingBalance && !this.busy)
              .onClick(() => {
                this.refreshBalance(true)
              })

            if (this.refreshingBalance) {
              LoadingProgress()
                .width(24)
                .height(24)
                .color('#6F97FF')
            }
          }
        }
        .width('100%')

        Row() {
          Text(this.statusLabel)
            .fontSize(12)
            .fontColor('#314765')
            .backgroundColor('#EEF5FF')
            .padding({ left: 12, right: 12, top: 7, bottom: 7 })
            .borderRadius(14)
            .border({
              width: 1,
              color: '#18FFFFFF',
              style: BorderStyle.Solid
            })
        }
        .margin({ top: 2 })

        Text(`宿舍位置：${this.locationLabel}`)
          .fontSize(15)
          .fontWeight(FontWeight.Medium)
          .fontColor('#11213B')
          .width('100%')

        Text(`设备：${this.deviceName}`)
          .fontSize(13)
          .fontColor('#63708A')
          .width('100%')

        Text(`账号：${this.userLabel}`)
          .fontSize(13)
          .fontColor('#63708A')
          .width('100%')
      }
      .padding(18)
      .borderRadius(24)
      .backgroundColor('#50FFFFFF')
      .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
      .border({
        width: 1,
        color: '#28FFFFFF',
        style: BorderStyle.Solid
      })
      .shadow({
        radius: 16,
        color: '#18000000',
        offsetX: 0,
        offsetY: 2
      })
      .margin({ left: 2, right: 2, top: 2, bottom: 2 })

      Stack() {
        Circle()
          .width(180)
          .height(180)
          .fill(this.hasCachedOrder ? '#FF9D84' : '#6F97FF')
          .opacity(0.08)
          .blendMode(BlendMode.PLUS)

        Circle()
          .width(280)
          .height(280)
          .fill(this.hasCachedOrder ? '#FF9D84' : '#6F97FF')
          .opacity(0.05)
          .blendMode(BlendMode.PLUS)

        Stack() {
          Circle()
            .width(272)
            .height(272)
            .fill('#22FFFFFF')
            .backdropBlur(4)

          Circle()
            .width(242)
            .height(242)
            .fill('#80FFFFFF')
            .opacity(0.55)

          if (this.busy) {
            Circle()
              .width(226)
              .height(226)
              .fill('rgba(0,0,0,0)')
              .stroke(this.hasCachedOrder ? '#FF9D84' : '#6F97FF')
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
            .backgroundColor(this.hasCachedOrder ? '#FFF1EB' : '#EDF6FF')
            .fontColor('#23457D')
            .enabled(!this.busy && !this.refreshingBalance)
            .shadow({
              radius: 24,
              color: this.hasCachedOrder ? '#60FF9D84' : '#606F97FF',
              offsetX: 0,
              offsetY: 6
            })
            .border({
              width: 2,
              color: this.hasCachedOrder ? '#30FF9D84' : '#30FFFFFF',
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
  private ConfigPage(): void {
    Column({ space: 16 }) {
      Column({ space: 12 }) {
        Text('导入与自动识别')
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor('#11213B')

        Text('继续保留 .chls 导入能力。系统信息和本机 Wi-Fi MAC 由程序自动识别。')
          .fontSize(13)
          .fontColor('#63708A')

        Row({ space: 12 }) {
          Button('导入 .chls')
            .layoutWeight(1)
            .type(ButtonType.Capsule)
            .backgroundColor('#D9E9FF')
            .fontColor('#23457D')
            .enabled(!this.busy)
            .onClick(() => {
              this.importCaptureFile()
            })

          Button('保存配置')
            .layoutWeight(1)
            .type(ButtonType.Capsule)
            .backgroundColor('#6F97FF')
            .fontColor(Color.White)
            .enabled(!this.busy)
            .onClick(() => {
              this.saveConfig()
            })
        }
      }
      .padding(18)
      .borderRadius(24)
      .backgroundColor('#50FFFFFF')
      .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
      .border({
        width: 1,
        color: '#28FFFFFF',
        style: BorderStyle.Solid
      })
      .shadow({
        radius: 16,
        color: '#18000000',
        offsetX: 0,
        offsetY: 2
      })
      .margin({ left: 2, right: 2, top: 2, bottom: 2 })

      Column({ space: 8 }) {
        Text('系统信息')
          .fontSize(12)
          .fontColor('#63708A')
        Text(this.systemInfoLabel)
          .fontSize(16)
          .fontWeight(FontWeight.Medium)
          .fontColor('#11213B')
        Text('本机 Wi-Fi MAC')
          .fontSize(12)
          .fontColor('#63708A')
          .margin({ top: 8 })
        Text(this.macAddressLabel)
          .fontSize(16)
          .fontWeight(FontWeight.Medium)
          .fontColor('#11213B')
      }
      .padding(18)
      .borderRadius(24)
      .backgroundColor('#50FFFFFF')
      .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
      .border({
        width: 1,
        color: '#28FFFFFF',
        style: BorderStyle.Solid
      })
      .shadow({
        radius: 16,
        color: '#18000000',
        offsetX: 0,
        offsetY: 2
      })
      .margin({ left: 2, right: 2, top: 2, bottom: 2 })

      Column({ space: 12 }) {
        Text('关键字段')
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor('#11213B')

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
          .fontColor('#63708A')
      }
      .padding(18)
      .borderRadius(24)
      .backgroundColor('#50FFFFFF')
      .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
      .border({
        width: 1,
        color: '#28FFFFFF',
        style: BorderStyle.Solid
      })
      .shadow({
        radius: 16,
        color: '#18000000',
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
        .fill('#60B7D6FF')
        .opacity(0.22)
        .align(Alignment.TopEnd)
        .offset({ x: 90, y: -100 })

      Circle()
        .width(200)
        .height(200)
        .fill('#50A5CCFF')
        .opacity(0.16)
        .align(Alignment.TopStart)
        .offset({ x: -60, y: 80 })

      Circle()
        .width(280)
        .height(280)
        .fill('#60FFD8C4')
        .opacity(0.15)
        .align(Alignment.BottomEnd)
        .offset({ x: 40, y: 80 })

      Circle()
        .width(160)
        .height(160)
        .fill('#50FFC8A8')
        .opacity(0.12)
        .align(Alignment.BottomStart)
        .offset({ x: -30, y: -20 })

      Column({ space: 18 }) {
        Column({ space: 4 }) {
          Text('趣智校园热水')
            .fontSize(30)
            .fontWeight(FontWeight.Bold)
            .fontColor('#11213B')
          Text('HarmonyOS 迁移版 · 导入抓包后可直接刷新余额和开关热水')
            .fontSize(14)
            .fontColor('#63708A')
        }
        .alignItems(HorizontalAlign.Start)
        .width('100%')
        .padding({ top: this.statusBarHeight > 0 ? this.statusBarHeight : 0 })

        Stack({ alignContent: Alignment.Bottom }) {
          Scroll() {
            Column({ space: 16 }) {
              Column() {
                if (this.activePage === HomePage.OPERATIONS) {
                  this.OperationsPage()
                } else {
                  this.ConfigPage()
                }
              }
              .width('100%')
              .transition(
                TransitionEffect.asymmetric(
                  TransitionEffect.OPACITY
                    .combine(TransitionEffect.translate({ x: 30 }))
                    .animation({ duration: 350, curve: Curve.EaseInOut }),
                  TransitionEffect.OPACITY
                    .combine(TransitionEffect.translate({ x: -30 }))
                    .animation({ duration: 350, curve: Curve.EaseInOut })
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
    .backgroundColor('#EAF2FF')
  }
}