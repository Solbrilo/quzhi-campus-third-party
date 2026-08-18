# HarmonyOS 热水应用

这是趣智校园第三方热水控制项目的 HarmonyOS NEXT 客户端，使用 ArkTS、ArkUI、Network Kit 和 Scan Kit 实现。当前版本为 `2.2.0 (2002000)`，兼容 HarmonyOS SDK `6.1.0(23)`，目标 SDK 为 `6.1.1(24)`。

## 当前功能

- “用水 / 账单 / 配置”三个主视图：查询余额和状态，开关热水、查看历史并维护配置。
- 独立趣智校园登录页，配置页通过“登录趣智校园”按钮跳转。
- 自动填写 `projectId`、`userId`、`accountId`、`loginCode` 和 `telephone`。
- `snCode` 输入框右侧提供扫码入口，使用 Scan Kit 在 ArkUI `XComponent` 中显示实时相机画面，并只识别 QR Code。
- 首次安装、重新安装及版本更新引导：两句欢迎诗依次整句从下向上淡入，每句完成时短振一次，最后显示作者；已有完整配置时快速开始会直接恢复主界面，仅缺 `snCode` 时恢复扫码，只有没有账号配置时才进入登录流程。
- 登录和绑定均可跳过，之后可以从配置页补充。
- 支持 JSON 配置导入导出，导出文件使用不含账号信息或特殊字符的固定名称。
- HarmonyOS 服务卡片、深色模式、操作历史和应用内更新。
- 密码只用于当前登录请求，不写入 Preferences 或热水配置。

## 登录实现

登录页向趣智校园发送表单请求：

```text
POST https://v3-api.china-qzxy.cn/user/login
version=1.1.11
phoneSystem=harmonyos
telephone=<手机号>
password=<MD5(UTF-8 密码) 的大写十六进制末 10 位>
type=0
```

该格式依据项目中的 Charles 抓包验证文件校准。登录成功后只保存热水接口需要的账号字段：

- 同一手机号重新登录保留已有 `snCode` 和 MAC。
- 切换手机号时清除旧账号的设备码和 MAC。
- `loginCode` 更新不会被误判为切换账号，也不会清除仍在使用的订单状态。

## 扫码绑定

支持的二维码示例：

```text
KLCXKJ-Water,M,C47F0EDA85D8
```

应用校验 `KLCXKJ-Water` 前缀和非空类型字段，取最后一个字段，移除冒号并转为大写，最终要求得到 12 位十六进制 `snCode`。

扫码页按需请求 `ohos.permission.CAMERA`。拒绝权限后页面会保留恢复入口；也可以返回配置页手动填写。扫描器会随页面显隐执行初始化、启动、停止和释放，防止离开页面后继续占用相机。

## 新手引导

引导完成状态同时记录应用 `versionCode` 和安装时间，用于覆盖首次安装、重新安装及版本更新；更新时会保留已保存的账号和设备配置，并从最远已完成的阶段继续：

1. 竖排展示无逗号的两句诗。前句在右侧略高，后句在左侧下错；前句整句上浮并淡入后短振一次，再播放后句，最后由细竖线引出署名。
2. 登录趣智校园，可选择“跳过登录”。
3. “绑定你的热水器”实时扫码页，可选择“跳过绑定”直接进入主界面。
4. 扫码保存成功后显示完成页，点击“立即开始”进入主界面；已有完整配置时直接完成引导，不再要求重新登录。

欢迎页的“快速开始”按钮独立固定在底部安全区，不参与中部诗句布局。

页面返回、异步登录和扫码保存都带有当前流程校验，避免旧请求回调覆盖新页面状态。

## 配置导入导出

配置页可以导入或导出 JSON 配置备份。导出文件固定命名为 `hotwater_config.json`，不会在文件名中包含手机号等账号信息；导入时会校验文件格式和必需字段，再用当前设备信息补全运行时配置。

配置文件包含登录凭据和设备信息，只应在自己的设备上保存和导入，不应提交到仓库或公开分享。

## 打开与运行

1. 使用 DevEco Studio 打开当前目录。
2. 确认已安装工程要求的 HarmonyOS SDK。
3. 在 DevEco Studio 中配置本机签名，不要提交个人证书或口令。
4. 选择 `entry` 模块，构建并安装到真机或模拟器。

Scan Kit 实扫与权限设置返回流程建议在 HarmonyOS 真机上验收。

## 测试与构建

项目未提交固定机器路径的 `hvigorw` 包装脚本。可以从 DevEco Studio 执行测试与构建，也可以在 PowerShell 中调用 DevEco 自带的 Ohpm 与 Hvigor。将 `$deveco` 改为本机安装目录：

```powershell
$deveco = 'D:\DevEco Studio'
$env:NODE_HOME = Join-Path $deveco 'tools\node'
$env:DEVECO_SDK_HOME = Join-Path $deveco 'sdk'
$ohpm = Join-Path $deveco 'tools\ohpm\bin\ohpm.bat'
$hvigor = Join-Path $deveco 'tools\hvigor\bin\hvigorw.bat'

& $ohpm install
& $hvigor --mode module -p product=default test --no-daemon --no-incremental --parallel
& $hvigor --mode module -p product=default -p buildMode=release assembleHap --no-daemon --no-incremental --parallel
```

本次改动的单元测试为 49/49 通过，Release HAP 构建成功。签名产物输出到：

```text
entry/build/default/outputs/default/entry-default-signed.hap
```

测试覆盖登录请求格式与响应、二维码解析、版本引导门控、配置身份切换和抓包字段提取。

## 已知边界

- `loginCode` 失效后，从配置页重新进入登录页即可刷新。
- 相机权限被永久拒绝时，需要按扫码页提示进入系统设置恢复，或手动填写 `snCode`。
- 自动化测试不替代真实账号登录、学校二维码实扫、权限拒绝后恢复和窄屏视觉验收。
