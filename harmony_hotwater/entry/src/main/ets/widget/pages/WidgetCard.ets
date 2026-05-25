class WidgetStorageInit {
  statusText: string = '准备就绪'
  statusTone: string = 'idle'
}

let widgetStorage: LocalStorage = new LocalStorage(new WidgetStorageInit())

@Entry(widgetStorage)
@Component
struct WidgetCard {
  private readonly ACTION_TYPE: string = 'message'
  @LocalStorageProp('statusText') statusText: string = '准备就绪'
  @LocalStorageProp('statusTone') statusTone: string = 'idle'

  build() {
    Stack() {
      Circle()
        .width(160)
        .height(160)
        .fill('#80B8D9FF')
        .opacity(0.22)
        .align(Alignment.TopEnd)
        .offset({ x: 42, y: -32 })

      Circle()
        .width(132)
        .height(132)
        .fill('#7CFFD4C2')
        .opacity(0.18)
        .align(Alignment.BottomStart)
        .offset({ x: -30, y: 36 })

      Column({ space: 10 }) {
        Text('趣智校园热水')
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
          .fontColor('#11213B')

        Row({ space: 6 }) {
          if (this.isBusy()) {
            LoadingProgress()
              .width(16)
              .height(16)
              .color(this.statusTone === 'close' ? '#FF9D84' : '#6F97FF')
          }

          Text(this.statusText)
            .fontSize(12)
            .fontColor(this.feedbackColor())
        }
        .width('100%')

        Column({ space: 8 }) {
          this.WidgetLink('开热水', '#DFF1FF', 'open')
          this.WidgetLink('关热水', '#FFE7E0', 'close')
        }
        .width('100%')
      }
      .width('100%')
    }
    .width('100%')
    .height('100%')
    .padding(16)
    .backgroundColor('#88FFFFFF')
    .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
    .borderRadius(24)
  }

  @Builder
  private WidgetLink(title: string, background: string, action: string): void {
    FormLink({
      action: this.ACTION_TYPE,
      params: {
        widgetAction: action
      }
    }) {
      Text(title)
        .width('100%')
        .padding({ top: 12, bottom: 12 })
        .textAlign(TextAlign.Center)
        .fontSize(14)
        .fontWeight(FontWeight.Medium)
        .fontColor('#23457D')
        .backgroundColor(background)
        .borderRadius(18)
    }
  }

  private feedbackColor(): string {
    if (this.statusTone === 'error') {
      return '#B84B36'
    }
    if (this.statusTone === 'close' || this.statusTone === 'close_success') {
      return '#A9553A'
    }
    if (this.statusTone === 'open' || this.statusTone === 'open_success') {
      return '#2A6C57'
    }
    return '#63708A'
  }

  private isBusy(): boolean {
    return this.statusTone === 'open' || this.statusTone === 'close'
  }
}
