import { abilityAccessCtrl, common, Permissions } from '@kit.AbilityKit'
import { customScan, scanBarcode, scanCore } from '@kit.ScanKit'
import type { BusinessError } from '@ohos.base'
import { QrCodeParser } from '../common/QrCodeParser'
import { ScannerSessionCoordinator } from '../common/ScannerSessionCoordinator'

const MAX_SCANNER_SIZE: number = 300
const SCANNER_SESSION_COORDINATOR = new ScannerSessionCoordinator()

@Component
export struct DeviceScanner {
  onScanned: (snCode: string) => Promise<boolean> = async () => false

  @State cameraGranted: boolean = false
  @State statusText: string = '正在准备相机...'
  @State statusIsError: boolean = false
  @State canRetry: boolean = false
  @State scannerSize: number = 0
  @State requestingPermission: boolean = false
  @State permissionNeedsSettings: boolean = false
  @State flashLightReady: boolean = false
  @State flashLightOn: boolean = false
  private scannerController: XComponentController = new XComponentController()
  private surfaceReady: boolean = false
  private initialized: boolean = false
  private started: boolean = false
  private handled: boolean = false
  private pageVisible: boolean = true
  private lifecycleGeneration: number = 0
  private areaGeneration: number = 0
  private sessionToken: number = 0
  private startTask: Promise<void> | null = null

  aboutToAppear(): void {
    this.pageVisible = true
    this.lifecycleGeneration++
    this.requestCameraPermission()
  }

  aboutToDisappear(): void {
    this.pageVisible = false
    this.lifecycleGeneration++
    this.handled = true
    this.stopAndRelease()
  }

  private async requestCameraPermission(): Promise<void> {
    if (this.requestingPermission || !this.pageVisible) {
      return
    }
    this.requestingPermission = true
    const generation = this.lifecycleGeneration
    try {
      const context = this.getUIContext().getHostContext() as common.UIAbilityContext
      const manager = abilityAccessCtrl.createAtManager()
      const permissions: Permissions[] = ['ohos.permission.CAMERA']
      let granted = false
      if (this.permissionNeedsSettings) {
        const settingsResult = await manager.requestPermissionOnSetting(context, permissions)
        granted = settingsResult.length > 0 &&
          settingsResult[0] === abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED
      } else {
        const result = await manager.requestPermissionsFromUser(context, permissions)
        granted = result.authResults.length > 0 &&
          result.authResults[0] === abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED
      }
      if (!this.pageVisible || generation !== this.lifecycleGeneration) {
        return
      }
      this.cameraGranted = granted
      if (!granted) {
        this.permissionNeedsSettings = true
        this.statusText = '需要相机权限才能扫描设备二维码'
        this.statusIsError = true
        this.canRetry = false
        return
      }
      this.permissionNeedsSettings = false
      this.statusText = '将二维码放入取景框内，识别后会自动回填'
      this.statusIsError = false
      this.canRetry = false
      this.handled = false
      this.startScannerIfReady()
    } catch (error) {
      if (this.pageVisible && generation === this.lifecycleGeneration) {
        this.cameraGranted = false
        this.statusText = '相机权限请求失败，请稍后重试'
        this.statusIsError = true
        this.canRetry = false
      }
    } finally {
      this.requestingPermission = false
    }
  }

  private async startScannerIfReady(): Promise<void> {
    if (!this.cameraGranted || !this.pageVisible || !this.surfaceReady ||
      this.scannerSize <= 0 || this.started || this.handled || this.startTask) {
      return
    }
    const generation = ++this.lifecycleGeneration
    const token = SCANNER_SESSION_COORDINATOR.createToken()
    this.sessionToken = token
    const startTask = this.performStart(generation, token)
    this.startTask = startTask
    await startTask
  }

  private async performStart(generation: number, token: number): Promise<void> {
    try {
      const acquired = await SCANNER_SESSION_COORDINATOR.acquire(token, () => {
        if (!this.cameraGranted || !this.pageVisible || !this.surfaceReady ||
          this.scannerSize <= 0 || this.handled || generation !== this.lifecycleGeneration ||
          token !== this.sessionToken) {
          return false
        }
        if (!this.initialized) {
          const options: scanBarcode.ScanOptions = {
            scanTypes: [scanCore.ScanType.QR_CODE],
            enableMultiMode: false,
            enableAlbum: false
          }
          customScan.init(options)
          this.initialized = true
        }
        const surfaceId: string = this.scannerController.getXComponentSurfaceId()
        const viewControl: customScan.ViewControl = {
          width: this.scannerSize,
          height: this.scannerSize,
          surfaceId: surfaceId
        }
        this.started = true
        customScan.start(
          viewControl,
          (error: BusinessError, results: scanBarcode.ScanResult[]) => {
            this.handleScanCallback(generation, token, error, results)
          }
        )
        this.flashLightReady = true
        return true
      }, async () => {
        await this.performCleanup()
      })
      if (!acquired && this.sessionToken === token) {
        this.sessionToken = 0
      }
    } catch (error) {
      if (this.sessionToken === token) {
        this.sessionToken = 0
      }
      this.started = false
      this.initialized = false
      this.flashLightReady = false
      this.flashLightOn = false
      this.lifecycleGeneration++
      if (this.pageVisible) {
        this.statusText = '相机暂时不可用，请稍后重试'
        this.statusIsError = true
        this.canRetry = true
      }
    } finally {
      this.startTask = null
    }
  }

  private async handleScanCallback(
    generation: number,
    token: number,
    error: BusinessError,
    results: scanBarcode.ScanResult[]
  ): Promise<void> {
    if (!this.pageVisible || generation !== this.lifecycleGeneration || !this.started ||
      token !== this.sessionToken || !SCANNER_SESSION_COORDINATOR.owns(token)) {
      return
    }
    if (error) {
      this.lifecycleGeneration++
      await this.stopAndRelease()
      if (this.pageVisible) {
        this.statusText = '相机暂时不可用，请稍后重试'
        this.statusIsError = true
        this.canRetry = true
      }
      return
    }

    let snCode: string | null = null
    for (let index = 0; index < results.length; index++) {
      snCode = QrCodeParser.parseSnCode(results[index].originalValue)
      if (snCode !== null) {
        break
      }
    }
    if (snCode === null) {
      this.statusText = '这不是趣智校园热水设备二维码'
      this.statusIsError = true
      try {
        if (!SCANNER_SESSION_COORDINATOR.owns(token)) {
          return
        }
        customScan.rescan()
      } catch (rescanError) {
        this.lifecycleGeneration++
        await this.stopAndRelease()
        if (this.pageVisible) {
          this.statusText = '请移开二维码后重新对准取景框'
          this.canRetry = true
        }
      }
      return
    }

    if (this.handled) {
      return
    }
    this.handled = true
    this.statusText = '设备二维码识别成功'
    this.statusIsError = false
    await this.stopAndRelease()
    if (!this.pageVisible || generation !== this.lifecycleGeneration) {
      return
    }
    let accepted = false
    try {
      accepted = await this.onScanned(snCode)
    } catch (callbackError) {
      accepted = false
    }
    if (!accepted && this.pageVisible && generation === this.lifecycleGeneration) {
      this.handled = false
      this.statusText = '设备码保存失败，请重试'
      this.statusIsError = true
      this.canRetry = true
    }
  }

  private async stopAndRelease(): Promise<void> {
    const token = this.sessionToken
    this.sessionToken = 0
    this.started = false
    this.initialized = false
    this.flashLightReady = false
    this.flashLightOn = false
    if (token > 0) {
      await SCANNER_SESSION_COORDINATOR.release(token)
    }
  }

  private async performCleanup(): Promise<void> {
    try {
      if (customScan.getFlashLightStatus()) {
        customScan.closeFlashLight()
      }
    } catch (error) {
    }
    try {
      await customScan.stop()
    } catch (error) {
    }
    try {
      await customScan.release()
    } catch (error) {
    }
  }

  private async restartForAreaChange(areaGeneration: number): Promise<void> {
    this.lifecycleGeneration++
    await this.stopAndRelease()
    if (areaGeneration === this.areaGeneration && this.pageVisible) {
      this.startScannerIfReady()
    }
  }

  private onScannerAreaChange(newValue: Area): void {
    const width = Number(newValue.width)
    const height = Number(newValue.height)
    const nextSize = Math.floor(Math.min(width, height, MAX_SCANNER_SIZE))
    if (!Number.isFinite(nextSize) || nextSize <= 0 || nextSize === this.scannerSize) {
      return
    }
    this.scannerSize = nextSize
    this.areaGeneration++
    const currentAreaGeneration = this.areaGeneration
    if (this.started || this.initialized) {
      this.restartForAreaChange(currentAreaGeneration)
    } else {
      this.startScannerIfReady()
    }
  }

  private async retryCamera(): Promise<void> {
    if (this.requestingPermission) {
      return
    }
    this.canRetry = false
    this.statusIsError = false
    this.statusText = '正在重新准备相机...'
    this.handled = false
    await this.stopAndRelease()
    if (this.cameraGranted) {
      this.startScannerIfReady()
    } else {
      this.requestCameraPermission()
    }
  }

  private toggleFlashLight(): void {
    const token = this.sessionToken
    if (!this.flashLightReady || !this.started || this.handled ||
      !SCANNER_SESSION_COORDINATOR.owns(token)) {
      return
    }
    try {
      const currentlyOn = customScan.getFlashLightStatus()
      if (currentlyOn) {
        customScan.closeFlashLight()
      } else {
        customScan.openFlashLight()
      }
      this.flashLightOn = !currentlyOn
      this.statusText = '将二维码放入取景框内，识别后会自动回填'
      this.statusIsError = false
    } catch (error) {
      this.flashLightReady = this.started && SCANNER_SESSION_COORDINATOR.owns(token)
      this.flashLightOn = false
      this.statusText = '手电筒暂时不可用，请稍后重试'
      this.statusIsError = true
    }
  }

  build() {
    Column({ space: 14 }) {
      Stack({ alignContent: Alignment.Center }) {
        if (this.cameraGranted) {
          XComponent({
            id: 'water_device_qr_scanner',
            type: XComponentType.SURFACE,
            controller: this.scannerController
          })
            .width('100%')
            .height('100%')
            .onLoad(() => {
              this.surfaceReady = true
              this.startScannerIfReady()
            })
            .onDestroy(() => {
              this.surfaceReady = false
              this.lifecycleGeneration++
              this.stopAndRelease()
            })
        } else {
          Column({ space: 10 }) {
            SymbolGlyph($r('sys.symbol.camera'))
              .fontSize(38)
              .fontColor([$r('app.color.text_secondary')])
            Text('请先允许使用相机')
              .fontSize(15)
              .fontColor($r('app.color.text_secondary'))
          }
          .alignItems(HorizontalAlign.Center)
        }
      }
      .width('100%')
      .constraintSize({ maxWidth: MAX_SCANNER_SIZE, maxHeight: MAX_SCANNER_SIZE })
      .aspectRatio(1)
      .onAreaChange((oldValue: Area, newValue: Area) => this.onScannerAreaChange(newValue))
      .borderRadius(24)
      .clip(true)
      .backgroundColor($r('app.color.input_background'))
      .border({ width: 1, color: $r('app.color.card_border'), style: BorderStyle.Solid })

      Row({ space: 10 }) {
        SymbolGlyph($r('sys.symbol.qrcode'))
          .fontSize(20)
          .fontColor([$r('app.color.accent_text')])
        Text(this.statusText)
          .fontSize(13)
          .fontColor(this.statusIsError ? $r('app.color.error_text') : $r('app.color.text_secondary'))
          .layoutWeight(1)

        if (this.flashLightReady) {
          Button({ type: ButtonType.Circle }) {
            if (this.flashLightOn) {
              SymbolGlyph($r('sys.symbol.flashlight_off'))
                .fontSize(21)
                .fontColor([$r('app.color.text_primary')])
            } else {
              SymbolGlyph($r('sys.symbol.flashlight_on'))
                .fontSize(21)
                .fontColor([$r('app.color.text_primary')])
            }
          }
          .width(40)
          .height(40)
          .backgroundColor($r('app.color.button_soft_background'))
          .accessibilityText(this.flashLightOn ? '关闭手电筒' : '打开手电筒')
          .onClick(() => this.toggleFlashLight())
        }
      }
      .width('100%')
      .constraintSize({ maxWidth: MAX_SCANNER_SIZE })
      .padding(12)
      .borderRadius(16)
      .backgroundColor($r('app.color.input_background'))

      if (!this.cameraGranted || this.canRetry) {
        Button(this.cameraGranted && this.canRetry ? '重试相机' :
          (this.permissionNeedsSettings ? '前往设置授权' : '允许相机权限'))
          .type(ButtonType.Capsule)
          .width('100%')
          .constraintSize({ maxWidth: MAX_SCANNER_SIZE })
          .backgroundColor($r('app.color.accent_button_background'))
          .fontColor($r('app.color.accent_button_text'))
          .enabled(!this.requestingPermission)
          .accessibilityText(this.permissionNeedsSettings ? '前往设置授权相机' : '允许相机权限')
          .onClick(() => this.retryCamera())
      }
    }
    .alignItems(HorizontalAlign.Center)
    .width('100%')
  }
}
