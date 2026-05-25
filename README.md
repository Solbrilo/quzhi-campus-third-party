# 趣智校园第三方热水控制项目

这是一个围绕“趣智校园热水控制”整理出来的第三方实现仓库，当前主要包含三部分：

- 桌面版 Python 脚本
- 安卓版应用工程
- HarmonyOS 版应用工程

项目目标是把原本依赖抓包参数的热水开关流程，整理成更容易复用的脚本和移动端应用。

## 目录说明

### 1. 桌面版脚本

主脚本：

- [qzxy_hotwater.py](D:\water\qzxy_hotwater.py)

桌面快捷入口：

- [open_hotwater.bat](D:\water\open_hotwater.bat)
- [close_hotwater.bat](D:\water\close_hotwater.bat)
- [check_hotwater.bat](D:\water\check_hotwater.bat)

用途：

- 从 `.chls` 抓包文件中提取参数
- 查询余额、设备和当前状态
- 开热水
- 关热水

### 2. 安卓版

工程目录：

- [android_hotwater](D:\water\android_hotwater)

当前能力：

- 双页结构：操作页 + 配置页
- 导入 `.chls`
- 自动提取关键字段
- 刷新余额、开关热水
- 桌面小组件
- `2x2 / 4x2` 两套小组件布局

### 3. HarmonyOS 版

工程目录：

- [harmony_hotwater](D:\water\harmony_hotwater)

当前能力：

- 双页结构：操作页 + 配置页
- 导入 `.chls`
- 自动提取关键字段
- 刷新余额、开关热水
- HarmonyOS 小组件

## 使用方式

### 桌面版

如果当前目录有 `1.chls`，可以直接运行：

```powershell
.\check_hotwater.bat
.\open_hotwater.bat
.\close_hotwater.bat
```

也可以手动执行：

```powershell
python .\qzxy_hotwater.py check
python .\qzxy_hotwater.py open
python .\qzxy_hotwater.py close
```

### 安卓版

1. 用 Android Studio 打开 [android_hotwater](D:\water\android_hotwater)
2. 等待 Gradle 同步
3. 安装运行 `app`
4. 首次打开后导入 `.chls` 或手动填写参数

### HarmonyOS 版

1. 用 DevEco Studio 打开 [harmony_hotwater](D:\water\harmony_hotwater)
2. 检查签名配置
3. 构建并安装 `hap`
4. 首次打开后导入 `.chls` 或手动填写参数

## 关于抓包文件

当前方案默认不内置“自动抓取其他应用流量”的插件，而是采用更稳妥的方式：

1. 用户自己抓包
2. 导出 `.chls`
3. 在脚本或应用中导入

这样可以明显降低安全风险、维护成本和平台策略风险。

## 仓库说明

为了避免敏感信息泄露，仓库已经默认忽略这些内容：

- `1.chls`
- `last_hotwater_order.json`
- `esp32_hotwater/main/hotwater_secrets.h`
- Android / Harmony / IDE 本地构建缓存

## 当前建议

如果你只关心实际使用，优先建议直接用移动端：

- 安卓设备使用 [android_hotwater](D:\water\android_hotwater)
- 鸿蒙设备使用 [harmony_hotwater](D:\water\harmony_hotwater)

如果你想做接口排查或快速验证，使用：

- [qzxy_hotwater.py](D:\water\qzxy_hotwater.py)
