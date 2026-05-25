# ESP32 直连趣智校园热水按钮

这是一个基于 `ESP-IDF` 的最小工程骨架，用 `ESP32-S3-DevKitC-1-N8R8` 直接连接趣智校园接口。

交互约定：

- 短按按钮：开热水
- 长按按钮：关热水
- 外接状态 LED：提示联网和请求状态

## 目录

- `main/main.c`：主程序
- `main/hotwater_config.h`：配置模板
- `main/hotwater_secrets.h`：你自己生成的私密配置，不建议提交

## 配置方式

1. 复制 `main/hotwater_config.h` 为 `main/hotwater_secrets.h`
2. 填入你的 Wi-Fi 和抓包提取出来的账号参数

或者运行根目录脚本自动生成：

```powershell
python .\scripts\generate_esp32_secrets.py --capture .\1.chls --wifi-ssid 你的WiFi --wifi-password 你的密码
```

## 编译刷机

安装好 `ESP-IDF` 后，在本目录执行：

```powershell
idf.py set-target esp32s3
idf.py build
idf.py -p COMx flash monitor
```

## 接线建议

面向 `ESP32-S3-DevKitC-1-N8R8` 的默认接线：

- 外接按钮一端接 `GPIO4`
- 外接按钮另一端接 `GND`
- 外接状态 LED：`GPIO10 -> 220R 电阻 -> LED 正极`
- `LED 负极 -> GND`

不建议把业务按钮接到 `GPIO0`，因为这个脚常和启动模式相关。

`N8R8` 版本不要外接使用 `GPIO35/36/37`。

## 注意

- 这版为了尽快落地，配置是编译时写入的
- `loginCode` 过期后，需要重新抓包并重新生成 `hotwater_secrets.h`
- 关热水依赖缓存的 `orderNo`，所以最好用同一块板同时负责开和关
- 如果你准备长期用，下一步最值得补的是“配网页面 + NVS 保存配置”，这样就不用每次过期都重新编译刷机
