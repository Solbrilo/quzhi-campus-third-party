# 趣智校园第三方热水控制项目

这是一个围绕趣智校园热水控制整理的第三方实现仓库，包含桌面 Python 脚本、Android 应用和 HarmonyOS 应用。移动端可以直接登录趣智校园获取账号参数，再通过学校热水器上的二维码绑定设备，不再要求用户为首次配置专门抓包。

## 项目结构

| 目录 | 用途 |
| --- | --- |
| [`qzxy_hotwater.py`](./qzxy_hotwater.py) | 从 Charles `.chls` 中提取配置，查询状态并开关热水 |
| [`android_hotwater`](./android_hotwater/) | Android 原生应用，详细说明见 [Android README](./android_hotwater/README.md) |
| [`harmony_hotwater`](./harmony_hotwater/) | HarmonyOS NEXT 原生应用，详细说明见 [HarmonyOS README](./harmony_hotwater/README.md) |

## 移动端功能

- 三个主视图：在“用水 / 账单 / 配置”之间切换，查询余额和状态、开关热水、查看历史并维护配置。
- 独立登录页：配置页的“登录趣智校园”按钮会跳转到单独页面，登录成功后自动回填账号字段。
- 扫码绑定：配置页 `snCode` 输入框右侧提供扫码按钮，也支持手动填写。
- 首次及更新引导：首次安装或应用版本更新后显示诗句、登录、扫码绑定和完成四个阶段；两句欢迎诗依次整句从下向上淡入，每句完成时给出短促触感，最后显示作者。
- 可跳过配置：引导中的登录和绑定均可跳过，之后仍能从配置页补充。
- 密码不落盘：密码只用于当前登录请求，不会保存到应用配置或本地存储。
- 同账号保留设备码；切换账号时清除旧账号的 `snCode` 和 MAC，避免误操作其他设备。
- Android 提供桌面小组件、快捷设置磁贴和 JSON 配置导入导出。
- HarmonyOS 提供服务卡片，以及与 Android 兼容的 JSON 配置导入导出。

## 登录协议

登录实现依据项目中的 Charles 验证文件校准，移动端使用以下请求：

```text
POST https://v3-api.china-qzxy.cn/user/login
Content-Type: application/x-www-form-urlencoded

version=1.1.11
phoneSystem=android 或 harmonyos
telephone=<手机号>
password=<MD5(UTF-8 密码) 的大写十六进制末 10 位>
type=0
```

登录成功后只保存热水接口需要的 `projectId`、`userId`、`accountId`、`loginCode` 和 `telephone`。登录响应不包含设备码，因此首次使用仍需扫码或手动填写 `snCode`。

## 设备二维码

学校热水器二维码的内容类似：

```text
KLCXKJ-Water,M,C47F0EDA85D8
```

应用校验 `KLCXKJ-Water` 前缀，并将最后一个字段规范化为 12 位大写十六进制设备码：

```text
C47F0EDA85D8
```

扫码需要相机权限。拒绝权限或设备没有可用相机时，可以返回配置页手动填写 `snCode`。

## 新手引导

首次安装或版本更新后的进入顺序如下：

1. 竖排展示“删繁就简三秋树”和“领异标新二月花”，两句高低错落；先由下向上浮现前句并短振，再浮现后句并短振，最后以细竖线引出署名。
2. 登录趣智校园。可以登录，也可以选择“跳过登录”。
3. “绑定你的热水器”实时扫码页。扫码成功并保存后进入完成页，也可以选择“跳过绑定”直接进入主界面。
4. 显示“恭喜你，完成配置！”，点击“立即开始”进入主界面。欢迎页底部入口为“快速开始”。

引导以当前应用版本为单位记录完成状态，所以升级后会重新展示一次。

## 使用方式

### 桌面脚本

当前目录存在 `1.chls` 时，可以运行：

```powershell
.\check_hotwater.bat
.\open_hotwater.bat
.\close_hotwater.bat
```

也可以指定其他抓包文件：

```powershell
python .\qzxy_hotwater.py --capture .\3.chls extract
python .\qzxy_hotwater.py --capture .\3.chls check
```

### Android

1. 用 Android Studio 打开 [`android_hotwater`](./android_hotwater/)。
2. 等待 Gradle 同步，配置本机签名信息后安装 `app`。
3. 首次打开按引导登录并扫描学校提供的设备二维码。

### HarmonyOS

1. 用 DevEco Studio 打开 [`harmony_hotwater`](./harmony_hotwater/)。
2. 确认 SDK 与签名配置后构建并安装 HAP。
3. 首次打开按引导登录并扫描学校提供的设备二维码。

## 构建与验证

Android：

```powershell
cd .\android_hotwater
.\gradlew.bat testDebugUnitTest testReleaseUnitTest
.\gradlew.bat assembleRelease
```

HarmonyOS 的可复现 Hvigor 命令见 [HarmonyOS README](./harmony_hotwater/README.md)。当前实现已完成以下验证：

- Android Debug 单元测试：86/86 通过。
- Android Release 单元测试：86/86 通过。
- HarmonyOS 单元测试：43/43 通过。
- Android Release APK 与 HarmonyOS Release HAP 均已构建成功。
- `qzxy_hotwater.py` 通过 `py_compile`，项目中的 `1.chls`、`2.chls`、`3.chls` 均可解析。

构建产物默认位于：

```text
android_hotwater/app/build/outputs/apk/release/app-release.apk
harmony_hotwater/entry/build/default/outputs/default/entry-default-signed.hap
```

## 抓包兼容与敏感信息

- Android 与 HarmonyOS 均不提供 `.chls` 导入，使用登录页和 JSON 配置备份。
- 桌面脚本仍可直接读取 `.chls`，并优先提取最后一次登录会话中的账号字段。
- `.chls`、登录凭据、签名材料和本地配置都可能包含敏感信息，不应提交或公开分享。
- 仓库通过 `.gitignore` 忽略 `*.chls`、本地订单缓存、移动端构建缓存，以及 Android 的 `local.properties` 和 `.jks` 文件；HarmonyOS 签名配置也应在提交前单独检查。
