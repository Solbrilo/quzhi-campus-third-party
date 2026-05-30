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
        .fill($r('app.color.decor_blue_large'))
        .opacity(0.22)
        .align(Alignment.TopEnd)
        .offset({ x: 42, y: -32 })

      Circle()
        .width(132)
        .height(132)
        .fill($r('app.color.decor_peach_large'))
        .opacity(0.18)
        .align(Alignment.BottomStart)
        .offset({ x: -30, y: 36 })

      Column({ space: 10 }) {
        Text('趣智校园热水')
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.text_primary'))

        Row({ space: 6 }) {
          if (this.isBusy()) {
            LoadingProgress()
              .width(16)
              .height(16)
              .color(this.statusTone === 'close' ? $r('app.color.close_accent') : $r('app.color.accent'))
          }

          Text(this.statusText)
            .fontSize(12)
            .fontColor(this.feedbackColor())
        }
        .width('100%')

        Column({ space: 8 }) {
          this.WidgetLink('开热水', $r('app.color.widget_button_open_background'), 'open')
          this.WidgetLink('关热水', $r('app.color.widget_button_close_background'), 'close')
        }
        .width('100%')
      }
      .width('100%')
    }
    .width('100%')
    .height('100%')
    .padding(16)
    .backgroundColor($r('app.color.widget_background'))
    .border({
      width: 1,
      color: $r('app.color.widget_border'),
      style: BorderStyle.Solid
    })
    .borderRadius(24)
  }

  @Builder
  private WidgetLink(title: string, background: ResourceColor, action: string): void {
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
        .fontColor($r('app.color.button_soft_text'))
        .backgroundColor(background)
        .borderRadius(18)
    }
  }

  private feedbackColor(): ResourceColor {
    if (this.statusTone === 'error') {
      return $r('app.color.widget_status_error')
    }
    if (this.statusTone === 'close' || this.statusTone === 'close_success') {
      return $r('app.color.widget_status_close')
    }
    if (this.statusTone === 'open' || this.statusTone === 'open_success') {
      return $r('app.color.widget_status_open')
    }
    return $r('app.color.text_secondary')
  }

  private isBusy(): boolean {
    return this.statusTone === 'open' || this.statusTone === 'close'
  }
}
