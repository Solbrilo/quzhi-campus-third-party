#!/usr/bin/env python3
"""趣智校园热水快捷脚本。

默认会优先从当前目录的 Charles 抓包文件 `1.chls` 里提取参数，
因此不需要把 loginCode、手机号等敏感信息再手动写进脚本。

示例:
    python qzxy_hotwater.py check
    python qzxy_hotwater.py status
    python qzxy_hotwater.py open
    python qzxy_hotwater.py close --order-no 1320...
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import warnings
import zlib
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Iterable

import requests
from urllib3.exceptions import InsecureRequestWarning

warnings.filterwarnings("ignore", category=InsecureRequestWarning)

BASE_URL = "https://v3-api.china-qzxy.cn"
DEFAULT_CAPTURE = "1.chls"
DEFAULT_ORDER_CACHE = "last_hotwater_order.json"


@dataclass
class HotWaterConfig:
    project_id: int
    user_id: int
    account_id: int
    login_code: str
    telephone: str
    sn_code: str
    mac_address: str | None = None
    phone_system: str = "Harmony"
    version: str = "0.0.1"

    def masked(self) -> dict[str, Any]:
        data = asdict(self)
        data["login_code"] = mask_middle(self.login_code)
        data["telephone"] = mask_middle(self.telephone, left=3, right=4)
        return data


def mask_middle(value: str, left: int = 6, right: int = 4) -> str:
    if len(value) <= left + right:
        return "*" * len(value)
    return f"{value[:left]}{'*' * (len(value) - left - right)}{value[-right:]}"


def extract_first(text: str, patterns: Iterable[str], field_name: str) -> str:
    for pattern in patterns:
        match = re.search(pattern, text)
        if match:
            return match.group(1)
    raise ValueError(f"未能从抓包中提取 {field_name}")


def extract_optional(text: str, patterns: Iterable[str]) -> str | None:
    for pattern in patterns:
        match = re.search(pattern, text)
        if match:
            return match.group(1)
    return None


def iter_gzip_texts(raw: bytes) -> Iterable[tuple[int, int, str]]:
    offset = 0
    while True:
        start = raw.find(b"\x1f\x8b", offset)
        if start < 0:
            return

        chunks: list[tuple[int, bytes]] = []
        if start >= 4:
            declared_length = int.from_bytes(raw[start - 4 : start], "big")
            if 2 <= declared_length <= len(raw) - start:
                chunks.append((start + declared_length, raw[start : start + declared_length]))
        chunks.append((len(raw), raw[start:]))

        yielded = False
        end = start + 2
        for fallback_end, chunk in chunks:
            try:
                decompressor = zlib.decompressobj(16 + zlib.MAX_WBITS)
                body = decompressor.decompress(chunk) + decompressor.flush()
            except zlib.error:
                continue
            if not body:
                continue
            end = fallback_end
            if decompressor.unused_data:
                end = start + len(chunk) - len(decompressor.unused_data)
            yield start, end, body.decode("utf-8", errors="ignore")
            yielded = True
            break

        offset = max(start + 2, end) if yielded else start + 2


def json_object(text: str) -> dict[str, Any] | None:
    try:
        payload = json.loads(text)
    except json.JSONDecodeError:
        return None
    return payload if isinstance(payload, dict) else None


def value_as_positive_int(value: Any) -> int | None:
    if isinstance(value, bool) or value in (None, ""):
        return None
    try:
        number = int(value)
    except (TypeError, ValueError):
        return None
    return number if number > 0 else None


def value_as_text(value: Any) -> str | None:
    if value in (None, ""):
        return None
    text = str(value).strip()
    return text or None


def extract_last_login_bundle(raw: bytes, text: str) -> dict[str, Any]:
    login_requests = list(
        re.finditer(r"POST /user/login(?:[^\x00\r\n]*)? HTTP/1\.1", text)
    )
    if not login_requests:
        return {}

    login_start = login_requests[-1].start()
    login_payload: dict[str, Any] | None = None
    login_end = login_start
    for gzip_start, gzip_end, gzip_text in iter_gzip_texts(raw):
        if gzip_start <= login_start:
            continue
        payload = json_object(gzip_text)
        data = payload.get("data") if payload else None
        if not isinstance(data, dict):
            continue
        if (
            find_nested_value(data, "userId") not in (None, "")
            and find_nested_value(data, "loginCode") not in (None, "")
            and find_nested_value(data, "telephone") not in (None, "")
        ):
            login_payload = data
            login_end = gzip_end
            break

    if login_payload is None:
        return {}

    tail = text[login_end:]
    account_payload = login_payload.get("userAccount")
    if not isinstance(account_payload, dict):
        account_payload = {}

    result: dict[str, Any] = {}
    for key in ("userId", "loginCode", "telephone"):
        value = value_as_text(find_nested_value(login_payload, key))
        if value is not None:
            result[key] = value

    for key in ("projectId", "accountId"):
        query_value = extract_optional(tail, [rf"[?&]{key}=(\d+)"])
        numeric = value_as_positive_int(query_value)
        if numeric is None:
            numeric = value_as_positive_int(find_nested_value(account_payload, key))
        if numeric is not None:
            result[key] = numeric

    for key in ("phoneSystem", "version"):
        value = value_as_text(find_nested_value(login_payload, key))
        if value is None:
            value = extract_optional(tail, [rf"[?&]{key}=([^&\s\x00]+)"])
        if value is not None:
            result[key] = value

    return result


def extract_capture_device_code(text: str) -> tuple[str, str | None]:
    sn_matches = re.findall(r"(?:snCode|macAddress)=([0-9A-Fa-f:]+)", text)
    sn_code = sn_matches[-1].replace(":", "").upper() if sn_matches else ""
    mac_matches = re.findall(r"macAddress=([0-9A-Fa-f:]+)", text)
    mac_address = mac_matches[-1].upper() if mac_matches else None
    if not sn_code:
        sn_code = os.getenv("QZXY_SN_CODE", "").replace(":", "").upper()
    if mac_address is None:
        mac_address = os.getenv("QZXY_MAC_ADDRESS")
    return sn_code, mac_address


def load_config_from_capture(path: Path) -> HotWaterConfig:
    raw = path.read_bytes()
    text = raw.decode("latin1", errors="ignore")
    login_bundle = extract_last_login_bundle(raw, text)

    project_id = int(login_bundle.get("projectId") or extract_first(text, [r"projectId=(\d+)"], "projectId"))
    user_id = int(login_bundle.get("userId") or extract_first(text, [r"userId=(\d+)"], "userId"))
    account_id = int(login_bundle.get("accountId") or extract_first(text, [r"accountId=(\d+)"], "accountId"))
    login_code = str(
        login_bundle.get("loginCode")
        or extract_first(text, [r"loginCode=([0-9a-fA-F]{32})"], "loginCode")
    )
    telephone = str(
        login_bundle.get("telephone")
        or extract_first(text, [r"telephone=(\d+)", r"telPhone=(\d+)"], "telephone")
    )
    sn_code, mac_address = extract_capture_device_code(text)

    phone_system_match = re.search(r"phoneSystem=([A-Za-z0-9_]+)", text)
    version_match = re.search(r"version=([0-9.]+)", text)

    return HotWaterConfig(
        project_id=project_id,
        user_id=user_id,
        account_id=account_id,
        login_code=login_code,
        telephone=telephone,
        sn_code=sn_code,
        mac_address=mac_address,
        phone_system=str(login_bundle.get("phoneSystem") or (phone_system_match.group(1) if phone_system_match else "Harmony")),
        version=str(login_bundle.get("version") or (version_match.group(1) if version_match else "0.0.1")),
    )


def load_config_from_env() -> HotWaterConfig:
    required = {
        "QZXY_PROJECT_ID": int,
        "QZXY_USER_ID": int,
        "QZXY_ACCOUNT_ID": int,
        "QZXY_LOGIN_CODE": str,
        "QZXY_TELEPHONE": str,
        "QZXY_SN_CODE": lambda value: value.replace(":", "").upper(),
    }

    values: dict[str, Any] = {}
    missing: list[str] = []

    for env_name, converter in required.items():
        raw_value = os.getenv(env_name)
        if raw_value is None or raw_value == "":
            missing.append(env_name)
            continue
        values[env_name] = converter(raw_value)

    if missing:
        missing_text = ", ".join(missing)
        raise ValueError(
            "找不到抓包文件，且环境变量也不完整。"
            f" 缺少: {missing_text}"
        )

    return HotWaterConfig(
        project_id=values["QZXY_PROJECT_ID"],
        user_id=values["QZXY_USER_ID"],
        account_id=values["QZXY_ACCOUNT_ID"],
        login_code=values["QZXY_LOGIN_CODE"],
        telephone=values["QZXY_TELEPHONE"],
        sn_code=values["QZXY_SN_CODE"],
        mac_address=os.getenv("QZXY_MAC_ADDRESS"),
        phone_system=os.getenv("QZXY_PHONE_SYSTEM", "Harmony"),
        version=os.getenv("QZXY_VERSION", "0.0.1"),
    )


def resolve_config(capture_arg: str | None) -> HotWaterConfig:
    if capture_arg:
        capture_path = Path(capture_arg)
        if not capture_path.exists():
            raise FileNotFoundError(f"抓包文件不存在: {capture_path}")
        return load_config_from_capture(capture_path)

    default_capture_path = Path(DEFAULT_CAPTURE)
    if default_capture_path.exists():
        return load_config_from_capture(default_capture_path)

    return load_config_from_env()


def ensure_sn_code(config: HotWaterConfig) -> None:
    if config.sn_code:
        return
    raise ValueError(
        "抓包里没有 snCode/macAddress。请从设备选择或扫码流程补抓一条证据，"
        "或者设置 QZXY_SN_CODE/手动保留已有 snCode 后再执行热水操作。"
    )


def find_nested_value(payload: Any, key_name: str) -> Any | None:
    if isinstance(payload, dict):
        if key_name in payload and payload[key_name] not in (None, ""):
            return payload[key_name]
        for value in payload.values():
            result = find_nested_value(value, key_name)
            if result not in (None, ""):
                return result
        return None

    if isinstance(payload, list):
        for item in payload:
            result = find_nested_value(item, key_name)
            if result not in (None, ""):
                return result
        return None

    return None


def order_cache_path() -> Path:
    return Path(DEFAULT_ORDER_CACHE)


def load_order_cache() -> dict[str, Any] | None:
    path = order_cache_path()
    if not path.exists():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return None


def save_order_cache(order_no: str, config: HotWaterConfig, source: str) -> None:
    payload = {
        "orderNo": str(order_no),
        "snCode": config.sn_code,
        "telephone": config.telephone,
        "projectId": config.project_id,
        "accountId": config.account_id,
        "userId": config.user_id,
        "source": source,
        "savedAt": int(time.time()),
    }
    order_cache_path().write_text(
        json.dumps(payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def clear_order_cache() -> None:
    path = order_cache_path()
    if path.exists():
        path.unlink()


def extract_money_yuan(payload: Any, primary_key: str = "accountRealMoney") -> float:
    string_candidates = [
        f"{primary_key}Str",
        "money",
        "consumeMoneyStr",
    ]

    for key in string_candidates:
        value = find_nested_value(payload, key)
        if value not in (None, ""):
            try:
                return float(value)
            except (TypeError, ValueError):
                pass

    raw_value = find_nested_value(payload, primary_key)
    if raw_value in (None, ""):
        return 0.0

    try:
        numeric = float(raw_value)
    except (TypeError, ValueError):
        return 0.0

    return numeric / 1000


class QzxyHotWaterClient:
    def __init__(self, config: HotWaterConfig) -> None:
        self.config = config
        self.session = requests.Session()
        self.session.verify = False
        self.session.headers.update(
            {
                "Accept": "application/json, text/plain, */*",
                "User-Agent": (
                    "Mozilla/5.0 (Linux; Android 14; "
                    "AppleWebKit/537.36 Mobile Safari/537.36)"
                ),
            }
        )

    def _query_params(self) -> dict[str, Any]:
        return {
            "projectId": self.config.project_id,
            "userId": self.config.user_id,
            "loginCode": self.config.login_code,
            "accountId": self.config.account_id,
            "telephone": self.config.telephone,
        }

    def _form_params(self, order_no: str | None = None) -> dict[str, Any]:
        params = {
            "accountId": self.config.account_id,
            "loginCode": self.config.login_code,
            "phoneSystem": self.config.phone_system,
            "projectId": self.config.project_id,
            "snCode": self.config.sn_code,
            "telephone": self.config.telephone,
            "userId": self.config.user_id,
            "version": self.config.version,
        }
        if order_no:
            params["orderNo"] = order_no
        return params

    def _get(self, endpoint: str, params: dict[str, Any]) -> dict[str, Any]:
        response = self.session.get(f"{BASE_URL}{endpoint}", params=params, timeout=15)
        response.raise_for_status()
        return response.json()

    def _post_form(self, endpoint: str, data: dict[str, Any]) -> dict[str, Any]:
        headers = {"Content-Type": "application/x-www-form-urlencoded;charset=UTF-8"}
        response = self.session.post(
            f"{BASE_URL}{endpoint}",
            data=data,
            headers=headers,
            timeout=15,
        )
        response.raise_for_status()
        return response.json()

    def user_info(self) -> dict[str, Any]:
        params = {
            "projectId": self.config.project_id,
            "userId": self.config.user_id,
            "loginCode": self.config.login_code,
        }
        return self._get("/user/info", params)

    def device_info(self) -> dict[str, Any]:
        params = self._query_params()
        params.update(
            {
                "macAddress": self.config.sn_code,
                "phoneSystem": self.config.phone_system,
                "version": self.config.version,
            }
        )
        return self._get("/device/info/mac", params)

    def balance(self) -> dict[str, Any]:
        params = {
            "projectId": self.config.project_id,
            "userId": self.config.user_id,
            "loginCode": self.config.login_code,
            "accountId": self.config.account_id,
            "telPhone": self.config.telephone,
            "phoneSystem": self.config.phone_system,
            "version": self.config.version,
        }
        return self._get("/account/wallet/money", params)

    def status(self) -> dict[str, Any]:
        return self._post_form(
            "/order/tcpDevice/query/rateOrder/using",
            self._form_params(),
        )

    def open_hot_water(self) -> dict[str, Any]:
        params = self._form_params()
        params["orderAuthCode"] = ""
        return self._post_form("/order/ali/tcpDevice/downRate/rateOrder", params)

    def open_result(self, order_no: str | None = None) -> dict[str, Any]:
        return self._post_form(
            "/order/tcpDevice/query/downRateResult",
            self._form_params(order_no=order_no),
        )

    def close_hot_water(self, order_no: str) -> dict[str, Any]:
        return self._post_form(
            "/order/tcpDevice/closeOrder",
            self._form_params(order_no=order_no),
        )

    def close_result(self, order_no: str) -> dict[str, Any]:
        return self._post_form(
            "/order/tcpDevice/closeOrder/result/query",
            self._form_params(order_no=order_no),
        )

    def consume_result(self, order_no: str) -> dict[str, Any]:
        return self._post_form(
            "/order/consumeOrder/result/query",
            self._form_params(order_no=order_no),
        )


def print_json(title: str, payload: Any) -> None:
    print(f"\n[{title}]")
    print(json.dumps(payload, ensure_ascii=False, indent=2))


def print_summary(config: HotWaterConfig, user_info: dict[str, Any], device_info: dict[str, Any]) -> None:
    user_data = user_info.get("data") or {}
    device_data = device_info.get("data") or {}
    balance_yuan = extract_money_yuan(user_info, "accountRealMoney")

    print("配置来源已就绪")
    print(f"用户: {user_data.get('telephone', config.telephone)}")
    print(f"余额: {balance_yuan:.2f} 元")
    print(f"设备: {device_data.get('deviceName', '未知设备')}")
    print(f"宿舍: {device_data.get('areaName', '')} {device_data.get('buildingName', '')} {device_data.get('roomName', '')}".strip())
    print(f"snCode: {config.sn_code}")


def command_extract(args: argparse.Namespace) -> int:
    config = resolve_config(args.capture)
    print(json.dumps(config.masked(), ensure_ascii=False, indent=2))
    return 0


def command_check(args: argparse.Namespace) -> int:
    config = resolve_config(args.capture)
    ensure_sn_code(config)
    client = QzxyHotWaterClient(config)

    user_info = client.user_info()
    device_info = client.device_info()
    balance = client.balance()
    status = client.status()

    print_summary(config, user_info, device_info)
    print_json("余额接口", balance)
    print_json("当前使用状态", status)

    return 0


def command_status(args: argparse.Namespace) -> int:
    config = resolve_config(args.capture)
    ensure_sn_code(config)
    client = QzxyHotWaterClient(config)
    status = client.status()
    print_json("当前使用状态", status)

    if status.get("success") and status.get("data") is None:
        print("\n当前看起来没有正在使用中的热水订单。")
    return 0


def command_open(args: argparse.Namespace) -> int:
    config = resolve_config(args.capture)
    ensure_sn_code(config)
    client = QzxyHotWaterClient(config)

    print("正在发送开热水请求...")
    open_response = client.open_hot_water()
    print_json("开热水响应", open_response)

    order_no = find_nested_value(open_response, "orderNo")
    last_result: dict[str, Any] | None = None

    for attempt in range(1, args.poll_attempts + 1):
        time.sleep(args.poll_interval)
        last_result = client.open_result(order_no=order_no)
        print_json(f"开热水结果轮询 #{attempt}", last_result)

        if not order_no:
            extracted_order_no = find_nested_value(last_result, "orderNo")
            if extracted_order_no:
                order_no = str(extracted_order_no)

        is_success = find_nested_value(last_result, "isSuccess")
        if is_success is True:
            print("\n热水已确认打开。")
            break

        if last_result.get("errorCode") == 0 and last_result.get("data") not in (None, {}, []):
            print("\n服务器返回了有效结果，热水大概率已经打开。")
            break
    else:
        print("\n轮询结束，但脚本还没拿到明确的成功标记。")
        print("如果你这时已经出水，说明只是返回结构和抓包时略有变化。")

    if order_no:
        save_order_cache(str(order_no), config, source="open")
        consume_response = client.consume_result(order_no)
        print_json("消费结果", consume_response)
        print(f"\n已缓存本次订单号: {order_no}")

    return 0


def command_close(args: argparse.Namespace) -> int:
    config = resolve_config(args.capture)
    ensure_sn_code(config)
    client = QzxyHotWaterClient(config)

    order_no = args.order_no
    if not order_no:
        status = client.status()
        order_no = find_nested_value(status, "orderNo")
        if not order_no:
            cache = load_order_cache()
            if cache and str(cache.get("snCode")) == config.sn_code:
                order_no = cache.get("orderNo")

        if not order_no:
            print_json("当前使用状态", status)
            print("\n没有自动拿到 orderNo，缓存里也没有可用订单号，所以暂时无法直接关热水。")
            print("如果你刚用这个脚本开过水，先试一次 open；脚本拿到订单号后，后续 close 就能直接复用。")
            print("或者你也可以手动执行: python qzxy_hotwater.py close --order-no 你的订单号")
            return 1

    print(f"正在发送关热水请求，orderNo={order_no}")
    close_response = client.close_hot_water(str(order_no))
    print_json("关热水响应", close_response)

    for attempt in range(1, args.poll_attempts + 1):
        time.sleep(args.poll_interval)
        result = client.close_result(str(order_no))
        print_json(f"关热水结果轮询 #{attempt}", result)
        if result.get("success") and result.get("errorCode") == 0:
            print("\n热水已确认关闭。")
            clear_order_cache()
            break

    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="趣智校园热水快捷脚本",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "--capture",
        default=None,
        help="Charles 抓包文件路径；不填时默认尝试当前目录下的 1.chls",
    )

    subparsers = parser.add_subparsers(dest="command", required=True)

    extract_parser = subparsers.add_parser("extract", help="只提取并打印配置")
    extract_parser.set_defaults(func=command_extract)

    check_parser = subparsers.add_parser("check", help="验证登录、设备和当前状态")
    check_parser.set_defaults(func=command_check)

    status_parser = subparsers.add_parser("status", help="查询当前热水使用状态")
    status_parser.set_defaults(func=command_status)

    open_parser = subparsers.add_parser("open", help="发送开热水请求")
    open_parser.add_argument("--poll-attempts", type=int, default=5, help="结果轮询次数")
    open_parser.add_argument("--poll-interval", type=float, default=2.0, help="轮询间隔秒数")
    open_parser.set_defaults(func=command_open)

    close_parser = subparsers.add_parser("close", help="发送关热水请求")
    close_parser.add_argument("--order-no", default=None, help="关热水所需的订单号")
    close_parser.add_argument("--poll-attempts", type=int, default=5, help="结果轮询次数")
    close_parser.add_argument("--poll-interval", type=float, default=2.0, help="轮询间隔秒数")
    close_parser.set_defaults(func=command_close)

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    try:
        return int(args.func(args))
    except requests.HTTPError as exc:
        response_text = exc.response.text[:1000] if exc.response is not None else str(exc)
        print("HTTP 请求失败:")
        print(response_text)
        return 1
    except Exception as exc:
        print(f"运行失败: {exc}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
