# Android 界面截图

这些图片于 2026-11-06 和 2026-11-08 使用 Android API 36 模拟器拍摄，来自当前开发源码中的真实 Compose 组件。主题预览、自定义主题和关于应用来自 4.0.0。设备、余额、账单、连接状态及充值套餐均为离线演示数据，不包含实际账号，也不会发送登录、开关水或支付请求。

模拟器截图分辨率为 720 × 1600。两个 README 使用此目录；网站已使用前六张截图。

| 文件 | 页面与主题 |
| --- | --- |
| android-home-tide.png | 用水首页 · 潮汐浅色 |
| android-bills.png | 月度账单 · 澄空浅色 |
| android-recharge.png | 半屏充值 · 澄空浅色 |
| android-home-ember.png | 用水首页 · 余晖浅色 |
| android-home-mono.png | 用水首页 · 墨序深色 |
| android-open-source.png | 开源支持 · 潮汐浅色 |
| android-theme-preview.png | 带手机外壳的主题预览 · 潮汐浅色 |
| android-theme-custom.png | 自定义主题与编辑入口 |
| android-about.png | 关于应用半屏页 · 潮汐浅色 |

## 复现

`DocumentationPreviewActivity` 只编译进 Debug，Release 不包含演示入口或演示数据。它直接复用应用的页面、主题、标题栏和底栏，不读取保存的账号配置。

构建并安装 Debug APK 后，在自己的测试设备或模拟器运行：

```powershell
adb shell am start -W -n cn.qzxy.hotwater/.DocumentationPreviewActivity --es screen home --es palette tide
```

`screen` 支持 `home`、`bills`、`recharge`、`opensource`、`settings`、`theme`，`palette` 支持项目中的主题标识（如 `sky`、`tide`、`ember`、`mono`、`custom`）。主题页可交互选主题；设置页可进入关于应用。添加 `--ez dark true` 可拍摄深色模式。更换参数前用 `adb shell am force-stop cn.qzxy.hotwater` 关闭预览。

使用 `adb exec-out screencap -p` 采集画面；Windows 上应通过 Python `subprocess` 写入二进制文件，避免旧版 PowerShell 的重定向改变 PNG 字节。运行示例入口不会验证真实设备控制、学校套餐或充值到账。
