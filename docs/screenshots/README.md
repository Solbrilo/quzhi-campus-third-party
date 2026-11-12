# Android 界面截图

这些图片于 2026-11-06、2026-11-08 与 2026-11-12 使用 Android API 36 模拟器拍摄，来自当前开发源码中的真实 Compose 组件。主题预览、自定义主题和关于应用来自 4.0.0。设备、余额、账单、连接状态及充值套餐均为离线演示数据，不包含实际账号，也不会发送登录、开关水或支付请求。

模拟器截图分辨率为 720 × 1600。两个 README 使用此目录；网站已使用前六张截图。

应用内更新介绍中的潮汐、余晖和墨序缩略图由对应的 `android-home-*.png` 缩小至 360 × 800 后以 WebP 保存，位于 `android_hotwater/app/src/main/res/drawable-nodpi/update_theme_*.webp`，首组沿用之前的三张图。四页介绍还使用 `update_custom.webp`、`update_widgets.webp` 和 `update_recharge.webp`；自定义示例使用用户提供的花卉图片。小组件截图来自 Debug 展示页，使用与 RemoteViews 相同的 Canvas 绘制器，不冒充 OEM 桌面截图。它们是随应用打包的静态演示图，不需要联网。

| 文件 | 页面与主题 |
| --- | --- |
| android-home-tide.png | 用水首页 · 潮汐浅色 |
| android-bills.png | 月度账单 · 澄空浅色 |
| android-recharge.png | 半屏充值 · 澄空浅色 |
| android-home-ember.png | 用水首页 · 余晖浅色 |
| android-home-mono.png | 用水首页 · 墨序深色 |
| android-open-source.png | 开源支持 · 潮汐浅色 |
| android-theme-preview.png | 带手机外壳的主题预览 · 花卉自定义背景 |
| android-theme-custom.png | 与主页同比例的固定手机预览与编辑卡片 |
| android-home-custom.png | 使用提供的花卉背景图片的主页 |
| android-widgets.png | 2×1 / 2×2 小组件实际绘制效果 |
| android-about.png | 关于应用半屏页 · 潮汐浅色 |
| android-welcome-chinese.png | 欢迎页 · 中文两段竖排 |
| android-update-highlights.png | 更新后的四页半屏功能展示 · 潮汐浅色 |

## 复现

`DocumentationPreviewActivity` 只编译进 Debug，Release 不包含演示入口或演示数据。它直接复用应用的页面、主题、标题栏和底栏，不读取保存的账号配置。

构建并安装 Debug APK 后，在自己的测试设备或模拟器运行：

```powershell
adb shell am start -W -n cn.qzxy.hotwater/.DocumentationPreviewActivity --es screen home --es palette tide
```

`screen` 支持 `home`、`bills`、`recharge`、`opensource`、`settings`、`theme`、`welcome`、`update`、`custom`、`widgets`，`palette` 支持项目中的主题标识（如 `sky`、`tide`、`ember`、`mono`、`custom`）。主题页可交互选主题；设置页可进入关于应用；`welcome` 展示中文分段欢迎动画，`update` 展示升级功能半屏页。添加 `--ez dark true` 可拍摄深色模式。更换参数前用 `adb shell am force-stop cn.qzxy.hotwater` 关闭预览。

使用 `adb exec-out screencap -p` 采集画面；Windows 上应通过 Python `subprocess` 写入二进制文件，避免旧版 PowerShell 的重定向改变 PNG 字节。运行示例入口不会验证真实设备控制、学校套餐或充值到账。

`custom` 和 `widgets` 搭配 `--es palette custom` 使用提供的示例背景。主题页使用 `--es screen theme --es palette custom --ez showcaseBackground true`，可拖动图片、调节模糊和色轮。背景素材仅在 Debug 中作为截图输入，Release 仅包含压缩后的展示截图，不覆盖用户背景。
