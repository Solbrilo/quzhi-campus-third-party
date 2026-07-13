import bundleManager from '@ohos.bundle.bundleManager'
import router from '@ohos.router'
import { GiteeUpdateRepository, UpdateCheckState } from '../common/AppUpdate'

@Entry
@Component
struct About {
  private updateRepository: GiteeUpdateRepository = new GiteeUpdateRepository()

  @State currentVersionLabel: string = '1.3.0'
  @State checkingUpdate: boolean = false
  @State updateCheckState: UpdateCheckState = UpdateCheckState.IDLE
  @State updateStatusLabel: string = '通过 Gitee Release 检查新版本。'
  @State updateReleaseNotes: string = ''
  @State updateVersionName: string = ''
  @StorageProp('statusBarHeight') statusBarHeight: number = 0

  aboutToAppear(): void {
    this.initialize()
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
      this.currentVersionLabel = '1.3.0'
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

  private goBack(): void {
    router.back()
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
      Row() {
        Text('‹')
          .fontSize(32)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_primary'))
          .width(40)
          .height(40)
          .textAlign(TextAlign.Center)
          .onClick(() => {
            this.goBack()
          })
        Text('关于')
          .fontSize(30)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.text_primary'))
          .layoutWeight(1)
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)
      .padding({ top: this.statusBarHeight > 0 ? this.statusBarHeight : 0 })

      Scroll() {
        Column({ space: 16 }) {
          Column({ space: 8 }) {
            Text('趣智校园热水')
              .fontSize(22)
              .fontWeight(FontWeight.Bold)
              .fontColor($r('app.color.text_primary'))
            Text('第三方热水控制工具')
              .fontSize(14)
              .fontColor($r('app.color.text_secondary'))
            Text(`当前版本 v${this.currentVersionLabel}`)
              .fontSize(14)
              .fontWeight(FontWeight.Medium)
              .fontColor($r('app.color.accent'))
          }
          .width('100%')
          .padding(20)
          .borderRadius(24)
          .backgroundColor($r('app.color.card_background'))
          .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
          .border({ width: 1, color: $r('app.color.card_border'), style: BorderStyle.Solid })

          Column({ space: 12 }) {
            Text('检查更新')
              .fontSize(18)
              .fontWeight(FontWeight.Bold)
              .fontColor($r('app.color.text_primary'))
            Text(this.updateStatusLabel)
              .fontSize(13)
              .fontColor(
                this.updateCheckState === UpdateCheckState.FAILED
                  ? $r('app.color.close_accent')
                  : (this.updateCheckState === UpdateCheckState.UPDATE_AVAILABLE
                    ? $r('app.color.accent')
                    : $r('app.color.text_secondary'))
              )
            Button(this.checkingUpdate ? '正在检查...' : '检查更新')
              .width('100%')
              .type(ButtonType.Capsule)
              .backgroundColor($r('app.color.button_soft_background'))
              .fontColor($r('app.color.button_soft_text'))
              .enabled(!this.checkingUpdate)
              .onClick(() => {
                this.checkForUpdates()
              })

            if (this.updateCheckState === UpdateCheckState.UPDATE_AVAILABLE) {
              Text(`发现新版本 v${this.updateVersionName}`)
                .fontSize(15)
                .fontWeight(FontWeight.Bold)
                .fontColor($r('app.color.accent'))
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
        }
        .width('100%')
        .padding({ bottom: 20 })
      }
      .scrollBar(BarState.Off)
      .layoutWeight(1)
      .width('100%')
    }
    .width('100%')
    .height('100%')
    .padding(20)
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM])
    .backgroundColor($r('app.color.app_background'))
  }
}
