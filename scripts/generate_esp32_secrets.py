from __future__ import annotations

import argparse
from pathlib import Path


def extract_first(text: str, patterns: list[str], field_name: str) -> str:
    import re

    for pattern in patterns:
        match = re.search(pattern, text)
        if match:
            return match.group(1)
    raise ValueError(f"未能从抓包中提取 {field_name}")


def build_header(capture_path: Path, wifi_ssid: str, wifi_password: str) -> str:
    text = capture_path.read_bytes().decode("latin1", errors="ignore")

    project_id = extract_first(text, [r"projectId=(\d+)"], "projectId")
    user_id = extract_first(text, [r"userId=(\d+)"], "userId")
    account_id = extract_first(text, [r"accountId=(\d+)"], "accountId")
    login_code = extract_first(text, [r"loginCode=([0-9a-f]{32})"], "loginCode")
    telephone = extract_first(text, [r"telephone=(\d+)", r"telPhone=(\d+)"], "telephone")
    sn_code = extract_first(
        text,
        [r"snCode=([0-9A-Fa-f]+)", r"macAddress=([0-9A-Fa-f:]+)"],
        "snCode/macAddress",
    ).replace(":", "").upper()
    phone_system = extract_first(text, [r"phoneSystem=([A-Za-z0-9_]+)"], "phoneSystem")
    version = extract_first(text, [r"version=([0-9.]+)"], "version")

    return f"""#pragma once

#define HOTWATER_WIFI_SSID "{wifi_ssid}"
#define HOTWATER_WIFI_PASSWORD "{wifi_password}"

#define HOTWATER_PROJECT_ID {project_id}
#define HOTWATER_USER_ID {user_id}
#define HOTWATER_ACCOUNT_ID {account_id}
#define HOTWATER_LOGIN_CODE "{login_code}"
#define HOTWATER_TELEPHONE "{telephone}"
#define HOTWATER_SN_CODE "{sn_code}"
#define HOTWATER_PHONE_SYSTEM "{phone_system}"
#define HOTWATER_VERSION "{version}"

#define HOTWATER_BUTTON_GPIO 4
#define HOTWATER_STATUS_LED_GPIO 10

#define HOTWATER_DEBOUNCE_MS 50
#define HOTWATER_LONG_PRESS_MS 1800
"""


def main() -> int:
    parser = argparse.ArgumentParser(description="从 Charles 抓包生成 ESP32 secrets 头文件")
    parser.add_argument("--capture", required=True, help="Charles 抓包文件路径")
    parser.add_argument("--wifi-ssid", required=True, help="Wi-Fi 名称")
    parser.add_argument("--wifi-password", required=True, help="Wi-Fi 密码")
    parser.add_argument(
        "--output",
        default=str(Path("esp32_hotwater") / "main" / "hotwater_secrets.h"),
        help="输出头文件路径",
    )
    args = parser.parse_args()

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        build_header(Path(args.capture), args.wifi_ssid, args.wifi_password),
        encoding="utf-8",
    )
    print(f"已生成: {output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
