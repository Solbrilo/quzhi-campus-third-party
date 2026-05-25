#pragma once

/*
 * 先复制本文件为 hotwater_secrets.h，再填入你自己的配置。
 * 也可以运行仓库根目录下的 scripts/generate_esp32_secrets.py 自动生成。
 *
 * 当前默认按 ESP32-S3-DevKitC-1-N8R8 规划：
 * - 外接按钮：GPIO4 -> 按钮 -> GND
 * - 外接状态 LED：GPIO10 -> 220R -> LED -> GND
 *
 * 不建议把业务按钮接到 GPIO0，这个脚常和启动模式相关。
 */

#define HOTWATER_WIFI_SSID "YOUR_WIFI_SSID"
#define HOTWATER_WIFI_PASSWORD "YOUR_WIFI_PASSWORD"

#define HOTWATER_PROJECT_ID 3796
#define HOTWATER_USER_ID 19738568
#define HOTWATER_ACCOUNT_ID 2047
#define HOTWATER_LOGIN_CODE "REPLACE_WITH_LOGIN_CODE"
#define HOTWATER_TELEPHONE "REPLACE_WITH_TELEPHONE"
#define HOTWATER_SN_CODE "C47F0EDA85D8"
#define HOTWATER_PHONE_SYSTEM "Harmony"
#define HOTWATER_VERSION "0.0.1"

/* 按钮和 LED 引脚可按开发板修改 */
#define HOTWATER_BUTTON_GPIO 4
#define HOTWATER_STATUS_LED_GPIO 10

/* 交互参数 */
#define HOTWATER_DEBOUNCE_MS 50
#define HOTWATER_LONG_PRESS_MS 1800
