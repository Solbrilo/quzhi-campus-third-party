import bundleManager from '@ohos.bundle.bundleManager'
import { GiteeUpdateRepository, UpdateCheckState } from '../common/AppUpdate'
import {
  FeedbackCategory,
  FeedbackClient,
  FeedbackSubmitResult,
  FeedbackSubmitState
} from '../common/FeedbackClient'

@Entry
@Component
struct About {
  private updateRepository: GiteeUpdateRepository = new GiteeUpdateRepository()
  private feedbackClient: FeedbackClient = new FeedbackClient()
  private feedbackRequestGeneration: number = 0

  @State currentVersionLabel: string = '2.1.0'
  @State checkingUpdate: boolean = false
  @State updateCheckState: UpdateCheckState = UpdateCheckState.IDLE
  @State updateStatusLabel: string = '通过 Gitee Release 检查新版本。'
  @State updateReleaseNotes: string = ''
  @State updateVersionName: string = ''
  @State feedbackCategory: string = FeedbackCategory.BUG
  @State feedbackMessage: string = ''
  @State feedbackErrorLabel: string = ''
  @State feedbackStatusLabel: string = ''
  @State feedbackSucceeded: boolean = false
  @State submittingFeedback: boolean = false
  @StorageProp('statusBarHeight') statusBarHeight: number = 0

  aboutToAppear(): void {
    this.initialize()
  }

  aboutToDisappear(): void {
    this.feedbackRequestGeneration++
    this.feedbackClient.cancelActiveRequest()
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
      this.currentVersionLabel = '2.1.0'
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
    this.getUIContext().getRouter().back()
  }

  private async submitFeedback(): Promise<void> {
    if (this.submittingFeedback) {
      return
    }
    const normalizedMessage = this.feedbackMessage.trim()
    const validationMessage = FeedbackClient.validationError(normalizedMessage)
    if (validationMessage.length > 0) {
      this.feedbackErrorLabel = validationMessage
      this.feedbackStatusLabel = ''
      this.feedbackSucceeded = false
      return
    }
    const submittedCategory = this.feedbackCategory
    const requestGeneration = ++this.feedbackRequestGeneration
    this.submittingFeedback = true
    this.feedbackErrorLabel = ''
    this.feedbackStatusLabel = ''
    this.feedbackSucceeded = false
    try {
      const result: FeedbackSubmitResult = await this.feedbackClient.submit(
        this.currentVersionLabel,
        submittedCategory,
        normalizedMessage
      )
      if (requestGeneration !== this.feedbackRequestGeneration) {
        return
      }
      if (result.state === FeedbackSubmitState.SUCCESS) {
        if (this.feedbackMessage.trim() === normalizedMessage && this.feedbackCategory === submittedCategory) {
          this.feedbackMessage = ''
        }
        this.feedbackSucceeded = true
        this.feedbackStatusLabel = `提交成功，反馈编号：${result.feedbackId}`
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
  private feedbackCategoryButton(value: string, label: string) {
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
      Row() {
        Text('‹')
          .fontSize(32)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_primary'))
          .accessibilityText('返回')
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
              .fontColor($r('app.color.accent_text'))
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
                  ? $r('app.color.error_text')
                  : (this.updateCheckState === UpdateCheckState.UPDATE_AVAILABLE
                    ? $r('app.color.accent_text')
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
            Text('问题反馈')
              .fontSize(18)
              .fontWeight(FontWeight.Bold)
              .fontColor($r('app.color.text_primary'))
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
            Text('请勿填写账号、手机号、学号、校园卡号、密码或 Token。仅在点击提交后发送上述反馈内容。')
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
    }
    .width('100%')
    .height('100%')
    .padding(20)
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM])
    .backgroundColor($r('app.color.app_background'))
  }
}
