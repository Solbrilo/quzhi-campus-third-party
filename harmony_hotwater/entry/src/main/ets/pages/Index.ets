import common from '@ohos.app.ability.common'
import picker from '@ohos.file.picker'
import fs from '@ohos.file.fs'
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

@Entry
@Component
struct Index {
  private hostContext?: common.Context
  private store?: HotWaterStore
  private repository?: HotWaterRepository

  @State activePage: number = HomePage.OPERATIONS
  @State busy: boolean = false
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

  aboutToAppear(): void {
    this.hostContext = this.getUIContext().getHostContext() as common.Context
    this.store = new HotWaterStore(this.hostContext)
    this.repository = new HotWaterRepository(this.store)
    this.initialize()
  }

  onPageShow(): void {
    this.consumePendingLaunchAction()
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
    this.activePage = HomePage.OPERATIONS
    if (config.isComplete()) {
      this.applySnapshot(this.placeholderSnapshot(config))
      await this.refreshBalance(false)
    }
  }

  private async refreshBalance(showStatus: boolean = true): Promise<void> {
    if (!this.repository || !this.store) {
      return
    }
    const config = await this.collectConfig()
    if (!config.isComplete()) {
      this.activePage = HomePage.CONFIG
      this.liveStatus = '配置还不完整，先导入 .chls 或补全字段。'
      return
    }
    this.busy = true
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
      this.busy = false
    }
  }

  private async toggleHotWater(): Promise<void> {
    if (!this.repository) {
      return
    }
    const config = await this.collectConfig()
    if (!config.isComplete()) {
      this.activePage = HomePage.CONFIG
      this.liveStatus = '配置还不完整，先导入 .chls 或补全字段。'
      return
    }
    this.busy = true
    this.liveStatus = this.hasCachedOrder ? '正在关闭热水…' : '正在开启热水…'
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
      this.activePage = HomePage.OPERATIONS
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
      this.activePage = HomePage.CONFIG
      return
    }
    if (action === 'open') {
      this.activePage = HomePage.OPERATIONS
      this.hasCachedOrder = false
      await this.toggleHotWater()
      return
    }
    if (action === 'close') {
      this.activePage = HomePage.OPERATIONS
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
    Row({ space: 8 }) {
      Button('操作页')
        .layoutWeight(1)
        .type(ButtonType.Capsule)
        .backgroundColor(this.activePage === HomePage.OPERATIONS ? '#6F97FF' : '#00000000')
        .fontColor(this.activePage === HomePage.OPERATIONS ? Color.White : '#40506A')
        .onClick(() => {
          this.activePage = HomePage.OPERATIONS
        })

      Button('配置页')
        .layoutWeight(1)
        .type(ButtonType.Capsule)
        .backgroundColor(this.activePage === HomePage.CONFIG ? '#6F97FF' : '#00000000')
        .fontColor(this.activePage === HomePage.CONFIG ? Color.White : '#40506A')
        .onClick(() => {
          this.activePage = HomePage.CONFIG
        })
    }
    .width('100%')
    .padding(8)
    .backgroundColor('#66FFFFFF')
    .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
    .shadow(ShadowStyle.OUTER_FLOATING_SM)
    .borderRadius(28)
  }

  @Builder
  private OperationsPage(): void {
    Column({ space: 16 }) {
      Column({ space: 10 }) {
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

          Button('刷新')
            .type(ButtonType.Capsule)
            .fontSize(13)
            .backgroundColor('#D9E9FF')
            .fontColor('#23457D')
            .enabled(!this.busy)
            .onClick(() => {
              this.refreshBalance(true)
            })
        }
        .width('100%')

        Text(this.statusLabel)
          .fontSize(12)
          .fontColor('#314765')
          .backgroundColor('#EEF5FF')
          .padding({ left: 12, right: 12, top: 8, bottom: 8 })
          .borderRadius(16)

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
      .backgroundColor('#66FFFFFF')
      .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
      .shadow(ShadowStyle.OUTER_FLOATING_SM)

      Stack() {
        Circle()
          .width(268)
          .height(268)
          .fill(this.hasCachedOrder ? '#5DFFB29F' : '#66A5CCFF')
          .opacity(0.22)

        Circle()
          .width(236)
          .height(236)
          .fill('#90FFFFFF')
          .opacity(0.62)

        if (this.busy) {
          LoadingProgress()
            .width(240)
            .height(240)
            .color(this.hasCachedOrder ? '#FF9D84' : '#6F97FF')
        }

        Button(this.hasCachedOrder ? '关' : '开')
          .width(200)
          .height(200)
          .borderRadius(100)
          .fontSize(56)
          .fontWeight(FontWeight.Bold)
          .backgroundColor(this.hasCachedOrder ? '#FFF1EB' : '#EDF6FF')
          .fontColor('#23457D')
          .enabled(!this.busy)
          .shadow(ShadowStyle.OUTER_FLOATING_SM)
          .onClick(() => {
            this.toggleHotWater()
          })
      }
      .width('100%')
      .height(280)

      Column({ space: 8 }) {
        Text(this.hasCachedOrder ? '当前模式 · 关闭热水' : '当前模式 · 开启热水')
          .fontSize(13)
          .fontColor('#63708A')

        Text(this.hasCachedOrder ? '检测到缓存订单号，点击大按钮将尝试关热水。' : '当前没有缓存订单号，点击大按钮将尝试开热水。')
          .fontSize(13)
          .fontColor('#63708A')
          .textAlign(TextAlign.Center)
      }
    }
    .width('100%')
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
      .backgroundColor('#66FFFFFF')
      .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
      .shadow(ShadowStyle.OUTER_FLOATING_SM)

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
      .width('100%')
      .padding(18)
      .borderRadius(24)
      .backgroundColor('#66FFFFFF')
      .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
      .shadow(ShadowStyle.OUTER_FLOATING_SM)

      Column({ space: 12 }) {
        Text('关键字段')
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor('#11213B')

        TextInput({ text: this.projectIdText, placeholder: 'projectId' })
          .onChange((value: string) => {
            this.projectIdText = value
          })
        TextInput({ text: this.userIdText, placeholder: 'userId' })
          .onChange((value: string) => {
            this.userIdText = value
          })
        TextInput({ text: this.accountIdText, placeholder: 'accountId' })
          .onChange((value: string) => {
            this.accountIdText = value
          })
        TextInput({ text: this.loginCodeText, placeholder: 'loginCode' })
          .onChange((value: string) => {
            this.loginCodeText = value
          })
        TextInput({ text: this.telephoneText, placeholder: 'telephone' })
          .onChange((value: string) => {
            this.telephoneText = value
          })
        TextInput({ text: this.snCodeText, placeholder: 'snCode' })
          .onChange((value: string) => {
            this.snCodeText = value
          })

        Text(this.configSummary)
          .fontSize(13)
          .fontColor('#63708A')
      }
      .padding(18)
      .borderRadius(24)
      .backgroundColor('#66FFFFFF')
      .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
      .shadow(ShadowStyle.OUTER_FLOATING_SM)
    }
    .width('100%')
  }

  build() {
    Stack() {
      Circle()
        .width(300)
        .height(300)
        .fill('#80B7D6FF')
        .opacity(0.24)
        .align(Alignment.TopEnd)
        .offset({ x: 72, y: -80 })

      Circle()
        .width(240)
        .height(240)
        .fill('#80FFD8C4')
        .opacity(0.18)
        .align(Alignment.BottomStart)
        .offset({ x: -56, y: 48 })

      Column({ space: 18 }) {
        Column({ space: 6 }) {
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

        Scroll() {
          Column({ space: 16 }) {
            if (this.activePage === HomePage.OPERATIONS) {
              this.OperationsPage()
            } else {
              this.ConfigPage()
            }

            Text(this.liveStatus)
              .fontSize(13)
              .fontColor('#63708A')
              .width('100%')
              .padding({ bottom: 16 })
          }
          .width('100%')
        }
        .scrollBar(BarState.Off)
        .layoutWeight(1)

        this.PageSwitcher()
      }
      .width('100%')
      .height('100%')
      .padding(20)
    }
    .backgroundColor('#EAF2FF')
  }
}
