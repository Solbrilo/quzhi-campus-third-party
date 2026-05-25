@Entry
@Component
struct Index {
  build() {
    RelativeContainer() {
      Text('请预览 pages/Index')
        .fontSize(14)
        .fontColor('#40506A')
        .alignRules({
          center: { anchor: '__container__', align: VerticalAlign.Center },
          middle: { anchor: '__container__', align: HorizontalAlign.Center }
        })
    }
    .height('100%')
    .width('100%')
  }
}
