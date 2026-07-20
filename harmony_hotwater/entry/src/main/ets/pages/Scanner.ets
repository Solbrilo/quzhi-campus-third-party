import { ConfigFlowBridge } from '../common/ConfigFlowBridge'
import { DeviceScanner } from '../components/DeviceScanner'

@Entry
@Component
struct Scanner {
  @StorageProp('statusBarHeight') statusBarHeight: number = 0
  @StorageProp('bottomSafeHeight') bottomSafeHeight: number = 0
  @State pageVisible: boolean = true
  @State accepting: boolean = false

  aboutToAppear(): void {
    this.pageVisible = true
  }

  onPageShow(): void {
    this.pageVisible = true
  }

  onPageHide(): void {
    this.pageVisible = false
  }

  onBackPress(): boolean {
    return this.accepting
  }

  private goBack(): void {
    if (!this.accepting) {
      this.getUIContext().getRouter().back()
    }
  }

  private async acceptSnCode(snCode: string): Promise<boolean> {
    if (this.accepting) {
      return false
    }
    this.accepting = true
    ConfigFlowBridge.setPendingSnCode(snCode)
    try {
      this.getUIContext().getPromptAction().showToast({ message: '设备码已回填' })
      this.getUIContext().getRouter().back()
      return true
    } catch (error) {
      this.accepting = false
      this.getUIContext().getPromptAction().showToast({ message: '暂时无法返回配置页，请重试' })
      return false
    }
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
        .enabled(!this.accepting)
        .accessibilityText('返回')
        .onClick(() => this.goBack())

        Text('扫描设备二维码')
          .fontSize(24)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.text_primary'))
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)

      Blank()
      if (this.pageVisible) {
        DeviceScanner({
          onScanned: (snCode: string) => this.acceptSnCode(snCode)
        })
      }
      Blank()
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
