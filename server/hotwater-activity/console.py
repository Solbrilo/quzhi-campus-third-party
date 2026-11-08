"""Private operator console and public announcements. No request/body logging."""
import hashlib
import hmac
import json
import os
import re
import secrets
import socket
import sqlite3
import threading
import time
from datetime import datetime, timedelta
from http.cookies import SimpleCookie
from pathlib import Path
from zoneinfo import ZoneInfo
from urllib.parse import parse_qs, urlsplit

ZONE = ZoneInfo("Asia/Shanghai")
PREFIX = "/project-qzxy-console"
PUBLIC = "/api/hotwater-notices"
AUTH_FILE = os.environ.get("HOTWATER_ADMIN_AUTH", "/var/lib/hotwater-activity/admin-auth.json")
AUTH_LOCK = threading.Lock()
SESSIONS = {}
ATTEMPTS = {}
IP_SALT = secrets.token_bytes(32)


def feedback_page(query_string):
    values = parse_qs(query_string, keep_blank_values=True, max_num_fields=4)
    if set(values) - {"month", "cursor", "platform", "category"} or any(len(v) != 1 for v in values.values()):
        raise ValueError("Invalid query")
    query = {key: value[0] for key, value in values.items()}
    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as connection:
        connection.settimeout(5)
        connection.connect("/run/hotwater-feedback-reader/reader.sock")
        connection.sendall(json.dumps(query).encode() + b"\n")
        with connection.makefile("rb") as stream:
            raw = stream.readline(512 * 1024 + 1)
    if len(raw) > 512 * 1024 or not raw.endswith(b"\n"):
        raise ValueError("Invalid response")
    result = json.loads(raw)
    if "error" in result:
        raise ValueError("Reader unavailable")
    return result


def timestamp():
    return int(time.time())


def clean_text(value, maximum, required=False):
    if not isinstance(value, str) or len(value) > maximum or (required and not value.strip()):
        raise ValueError("文字长度或格式不正确")
    if any(ord(char) < 32 and char not in "\n\r\t" for char in value):
        raise ValueError("文字包含无效字符")
    return value.strip()


def validate_notice(payload):
    if not isinstance(payload, dict):
        raise ValueError("无效公告")
    result = {"title": clean_text(payload.get("title"), 120, True),
              "body": clean_text(payload.get("body"), 12000, True)}
    for key in ("starts", "pushes", "expires"):
        value = payload.get(key)
        if type(value) is not int or value < 0 or value > 4102444800:
            raise ValueError("请填写有效时间")
        result[key] = value
    if not result["starts"] <= result["pushes"] < result["expires"]:
        raise ValueError("推送时间必须在公告生效至到期之间")
    if result["expires"] <= timestamp() and payload.get("enabled", True):
        raise ValueError("到期时间应晚于当前时间")
    options = payload.get("options", [])
    if not isinstance(options, list) or len(options) > 8:
        raise ValueError("最多设置 8 个选项")
    result["options"] = []
    for index, option in enumerate(options):
        if not isinstance(option, dict) or type(option.get("input", False)) is not bool:
            raise ValueError("无效选项")
        result["options"].append({"id": str(index + 1), "label": clean_text(option.get("label"), 80, True),
                                  "input": option.get("input", False)})
    if type(payload.get("enabled", True)) is not bool:
        raise ValueError("无效发布状态")
    result["enabled"] = payload.get("enabled", True)
    return result


class ConsoleStore:
    def init_console(self, db):
        db.execute("CREATE TABLE IF NOT EXISTS notices (id TEXT PRIMARY KEY, revision INTEGER NOT NULL, title TEXT, body TEXT, starts INTEGER, pushes INTEGER, expires INTEGER, enabled INTEGER, options TEXT, updated INTEGER)")
        db.execute("CREATE TABLE IF NOT EXISTS notice_responses (notice_id TEXT, revision INTEGER, token TEXT, option_id TEXT, response TEXT, submitted INTEGER, PRIMARY KEY(notice_id,revision,token))")
        db.execute("CREATE TABLE IF NOT EXISTS notice_versions (notice_id TEXT, revision INTEGER, options TEXT, PRIMARY KEY(notice_id,revision))")

    @staticmethod
    def notice_row(row):
        keys = ("id", "revision", "title", "body", "starts", "pushes", "expires", "enabled", "options", "updated")
        item = dict(zip(keys, row))
        item["options"] = json.loads(item["options"])
        item["enabled"] = bool(item["enabled"])
        return item

    def notices(self, public=False, now=None):
        now = timestamp() if now is None else now
        with self.connect() as db:
            if public:
                rows = db.execute("SELECT * FROM notices WHERE enabled=1 AND starts<=? AND expires>? ORDER BY pushes DESC LIMIT 100", (now, now)).fetchall()
            else:
                rows = db.execute("SELECT * FROM notices ORDER BY updated DESC LIMIT 200").fetchall()
        return [self.notice_row(row) for row in rows]

    def save_notice(self, payload):
        item = validate_notice(payload)
        notice_id = payload.get("id") or secrets.token_hex(12)
        if not isinstance(notice_id, str) or not re.fullmatch(r"[a-f0-9]{24}", notice_id):
            raise ValueError("无效公告编号")
        with self.lock, self.connect() as db:
            db.execute("BEGIN IMMEDIATE")
            old = db.execute("SELECT revision FROM notices WHERE id=?", (notice_id,)).fetchone()
            if old and payload.get("revision") != old[0]:
                raise ValueError("公告已被更新，请刷新后重试")
            if payload.get("id") and not old:
                raise ValueError("公告不存在")
            revision = old[0] + 1 if old else 1
            db.execute("INSERT OR REPLACE INTO notices VALUES (?,?,?,?,?,?,?,?,?,?)", (
                notice_id, revision, item["title"], item["body"], item["starts"], item["pushes"], item["expires"],
                int(item["enabled"]), json.dumps(item["options"], ensure_ascii=False), timestamp()))
            db.execute("INSERT INTO notice_versions VALUES (?,?,?)", (notice_id, revision, json.dumps(item["options"], ensure_ascii=False)))
        return notice_id

    def respond(self, payload, now=None):
        now = timestamp() if now is None else now
        if not isinstance(payload, dict) or set(payload) != {"id", "revision", "token", "option", "text"}:
            raise ValueError("无效回复")
        if not isinstance(payload["token"], str) or not re.fullmatch(r"[a-f0-9]{64}", payload["token"]):
            raise ValueError("无效匿名标识")
        if not isinstance(payload["id"], str) or type(payload["revision"]) is not int or not isinstance(payload["option"], str):
            raise ValueError("无效公告版本")
        answer = clean_text(payload["text"], 1000)
        with self.lock, self.connect() as db:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute("SELECT * FROM notices WHERE id=? AND revision=? AND enabled=1 AND starts<=? AND expires>?",
                             (payload["id"], payload["revision"], now, now)).fetchone()
            if not row:
                raise ValueError("公告已更新或结束，请刷新消息")
            notice = self.notice_row(row)
            option = next((o for o in notice["options"] if o["id"] == payload["option"]), None)
            if not option or (option["input"] and not answer) or (not option["input"] and answer):
                raise ValueError("请选择有效选项并填写内容")
            db.execute("INSERT INTO notice_responses VALUES (?,?,?,?,?,?) ON CONFLICT(notice_id,revision,token) DO UPDATE SET option_id=excluded.option_id,response=excluded.response,submitted=excluded.submitted",
                       (notice["id"], notice["revision"], payload["token"], option["id"], answer, now))

    def dashboard(self):
        today = datetime.now(ZONE).date()
        with self.connect() as db:
            daily_map = dict(db.execute("SELECT day,count FROM daily_counts WHERE day>=?", ((today - timedelta(days=89)).isoformat(),)))
            daily = [{"period": (today - timedelta(days=i)).isoformat(), "count": daily_map.get((today - timedelta(days=i)).isoformat(), 0)} for i in reversed(range(90))]
            monthly = [{"period": p, "count": count} for p, count in db.execute("SELECT month,count FROM monthly_counts ORDER BY month DESC LIMIT 24")][::-1]
            version_data = {}
            for kind, period, table, key in (("daily", today.isoformat(), "daily_counts", "day"), ("monthly", today.strftime("%Y-%m"), "monthly_counts", "month")):
                total = db.execute(f"SELECT count FROM {table} WHERE {key}=?", (period,)).fetchone()
                versions = [{"version": v, "count": n} for v, n in db.execute("SELECT version,count FROM version_counts WHERE kind=? AND period=? AND count>0 ORDER BY count DESC", (kind, period))]
                unknown = max(0, (total[0] if total else 0) - sum(x["count"] for x in versions))
                if unknown:
                    versions.append({"version": "旧版 / 未上报", "count": unknown})
                version_data[kind] = versions
        return {"daily": daily, "monthly": monthly, "versions": version_data, "today": today.isoformat(), "timezone": "Asia/Shanghai"}

    def responses(self, notice_id, revision):
        if not re.fullmatch(r"[a-f0-9]{24}", notice_id) or revision < 1:
            raise ValueError("无效公告")
        with self.connect() as db:
            counts = [{"option": o, "count": n} for o, n in db.execute("SELECT option_id,count(*) FROM notice_responses WHERE notice_id=? AND revision=? GROUP BY option_id", (notice_id, revision))]
            texts = [{"option": o, "text": t, "submitted": at} for o, t, at in db.execute("SELECT option_id,response,submitted FROM notice_responses WHERE notice_id=? AND revision=? AND response!='' ORDER BY submitted DESC LIMIT 500", (notice_id, revision))]
            options = db.execute("SELECT options FROM notice_versions WHERE notice_id=? AND revision=?", (notice_id, revision)).fetchone()
        return {"counts": counts, "texts": texts, "options": json.loads(options[0]) if options else []}


class ConsoleHandler:
    def session(self):
        cookie = SimpleCookie()
        try:
            cookie.load(self.headers.get("Cookie", ""))
            token = cookie["qzxy_admin"].value
        except (KeyError, ValueError):
            return None
        with AUTH_LOCK:
            session = SESSIONS.get(token)
            return session if session and session["expires"] > time.monotonic() else None

    def send_data(self, status, data, content_type="application/json; charset=utf-8", cookie=None):
        if not isinstance(data, bytes):
            data = json.dumps(data, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("X-Frame-Options", "DENY")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("X-Robots-Tag", "noindex, nofollow, noarchive")
        self.send_header("Content-Security-Policy", "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'")
        self.send_header("Connection", "close")
        if cookie:
            self.send_header("Set-Cookie", cookie)
        self.end_headers()
        self.wfile.write(data)
        self.close_connection = True

    def console_get(self):
        if self.path == PUBLIC:
            try:
                self.send_data(200, {"notices": self.server.store.notices(public=True), "now": timestamp()})
            except sqlite3.Error:
                self.send_data(503, {"error": "暂时无法读取公告"})
            return True
        if not self.path.startswith(PREFIX):
            return False
        files = {PREFIX + "/": "console.html", PREFIX + "/console.css": "console.css", PREFIX + "/console.js": "console.js"}
        resource_path = self.path.split("?", 1)[0]
        if resource_path in files:
            filename = files[resource_path]
            mime = "text/html" if filename.endswith("html") else "text/css" if filename.endswith("css") else "application/javascript"
            self.send_data(200, Path(__file__).with_name(filename).read_bytes(), mime + "; charset=utf-8")
            return True
        session = self.session()
        if not session:
            self.send_data(401, {"error": "请先登录"}); return True
        try:
            if self.path == PREFIX + "/session":
                self.send_data(200, {"csrf": session["csrf"]})
            elif self.path == PREFIX + "/stats":
                self.send_data(200, self.server.store.dashboard())
            elif self.path == PREFIX + "/notices":
                self.send_data(200, {"notices": self.server.store.notices()})
            elif resource_path == PREFIX + "/feedback":
                self.send_data(200, feedback_page(urlsplit(self.path).query))
            else:
                match = re.fullmatch(re.escape(PREFIX) + r"/responses/([a-f0-9]{24})/([0-9]{1,6})", self.path)
                if not match:
                    self.send_data(404, {"error": "页面不存在"})
                else:
                    self.send_data(200, self.server.store.responses(match[1], int(match[2])))
        except (ValueError, sqlite3.Error, OSError):
            self.send_data(503, {"error": "读取失败，请稍后重试"})
        return True

    def read_json(self, limit=32768):
        if self.headers.get("Content-Type", "").split(";")[0].lower() != "application/json":
            raise ValueError("请使用 JSON 请求")
        if self.headers.get("Transfer-Encoding") or self.headers.get("Content-Encoding"):
            raise ValueError("不支持编码请求")
        length = int(self.headers.get("Content-Length", "0"))
        if not 0 < length <= limit:
            raise ValueError("请求过大")
        raw = self.rfile.read(length)
        if len(raw) != length:
            raise ValueError("请求不完整")
        data = json.loads(raw)
        if not isinstance(data, dict):
            raise ValueError("无效请求")
        return data

    def console_post(self):
        if self.path not in (PUBLIC + "/response", PREFIX + "/login", PREFIX + "/logout", PREFIX + "/notices"):
            return False
        if not self.server.allow_request():
            self.send_data(429, {"error": "请求过多，请稍后重试"}); return True
        try:
            if self.path == PUBLIC + "/response":
                self.server.store.respond(self.read_json(8192))
                self.send_data(200, {"ok": True}); return True
            # Only the same HTTPS origin may mutate the operator console.
            origin = self.headers.get("Origin", "")
            if origin != "https://nebularive.top" and not (os.environ.get("HOTWATER_CONSOLE_TEST") == "1" and origin == "http://127.0.0.1:8109"):
                self.send_data(403, {"error": "来源验证失败"}); return True
            if self.path == PREFIX + "/login":
                self.login_console(self.read_json(1024)); return True
            session = self.session()
            if not session or not hmac.compare_digest(self.headers.get("X-CSRF-Token", ""), session["csrf"]):
                self.send_data(403, {"error": "登录已过期，请重新登录"}); return True
            data = self.read_json()
            if self.path.endswith("/logout"):
                cookie = SimpleCookie(self.headers.get("Cookie", ""))
                with AUTH_LOCK:
                    SESSIONS.pop(cookie["qzxy_admin"].value, None)
                self.send_data(200, {"ok": True}, cookie="qzxy_admin=; Max-Age=0; Path=/project-qzxy-console/; HttpOnly; Secure; SameSite=Strict")
            else:
                notice_id = self.server.store.save_notice(data)
                self.send_data(200, {"ok": True, "id": notice_id})
        except (ValueError, UnicodeDecodeError) as error:
            self.send_data(400, {"error": str(error) if isinstance(error, ValueError) and not isinstance(error, json.JSONDecodeError) else "无效请求"})
        except (sqlite3.Error, OSError, KeyError):
            self.send_data(503, {"error": "服务暂不可用"})
        return True

    def login_console(self, payload):
        password = payload.get("password", "")
        if not isinstance(password, str) or not 1 <= len(password) <= 200:
            raise ValueError("请输入密码")
        peer = self.headers.get("X-Console-Client", self.client_address[0])
        key = hmac.new(IP_SALT, peer.encode(), hashlib.sha256).digest()
        now = time.monotonic()
        with AUTH_LOCK:
            for old in [k for k, (_, until) in ATTEMPTS.items() if until < now]:
                del ATTEMPTS[old]
            count, until = ATTEMPTS.get(key, (0, now + 900))
            if count >= 8 or len(ATTEMPTS) > 4096:
                self.send_data(429, {"error": "尝试次数过多，请 15 分钟后再试"}); return
            ATTEMPTS[key] = (count + 1, until)
        auth = json.loads(Path(AUTH_FILE).read_text())
        derived = hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(auth["salt"]), 600000).hex()
        if not hmac.compare_digest(derived, auth["hash"]):
            self.send_data(401, {"error": "密码不正确"}); return
        token = secrets.token_urlsafe(32)
        csrf = secrets.token_urlsafe(32)
        with AUTH_LOCK:
            ATTEMPTS.pop(key, None)
            for old in [k for k, value in SESSIONS.items() if value["expires"] < now]:
                del SESSIONS[old]
            if len(SESSIONS) >= 32:
                SESSIONS.pop(next(iter(SESSIONS)))
            SESSIONS[token] = {"expires": now + 3600 * 8, "csrf": csrf}
        self.send_data(200, {"csrf": csrf}, cookie=f"qzxy_admin={token}; Max-Age=28800; Path=/project-qzxy-console/; HttpOnly; Secure; SameSite=Strict")
