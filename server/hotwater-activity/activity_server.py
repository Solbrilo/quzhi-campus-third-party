"""Anonymous daily and monthly installation counts. No account data or HTTP request logs."""
import argparse
from contextlib import contextmanager
from datetime import date, datetime, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import sqlite3
import threading
import time
from zoneinfo import ZoneInfo
from console import ConsoleStore, ConsoleHandler

ZONE = ZoneInfo("Asia/Shanghai")
MAX_BODY = 256


def validate(payload, today=None):
    today = today or datetime.now(ZONE).date()
    if not isinstance(payload, dict) or not {"day", "token"} <= set(payload) or set(payload) - {"day", "token", "version"}:
        raise ValueError("invalid fields")
    validate_version(payload)
    day, token = payload["day"], payload["token"]
    if not isinstance(day, str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", day):
        raise ValueError("invalid day")
    parsed = date.fromisoformat(day)
    if parsed > today or parsed < today - timedelta(days=7):
        raise ValueError("day outside retry window")
    if not isinstance(token, str) or not re.fullmatch(r"[a-f0-9]{64}", token):
        raise ValueError("invalid token")
    return day, token


def validate_monthly(payload, today=None):
    today = today or datetime.now(ZONE).date()
    if not isinstance(payload, dict) or not {"month", "token"} <= set(payload) or set(payload) - {"month", "token", "version"}:
        raise ValueError("invalid fields")
    validate_version(payload)
    month, token = payload["month"], payload["token"]
    if not isinstance(month, str) or not re.fullmatch(r"\d{4}-\d{2}", month):
        raise ValueError("invalid month")
    try:
        date.fromisoformat(month + "-01")
    except ValueError as error:
        raise ValueError("invalid month") from error
    earliest = (today - timedelta(days=7)).strftime("%Y-%m")
    latest = today.strftime("%Y-%m")
    if month < earliest or month > latest:
        raise ValueError("month outside retry window")
    if not isinstance(token, str) or not re.fullmatch(r"[a-f0-9]{64}", token):
        raise ValueError("invalid token")
    return month, token


def validate_version(payload):
    version = payload.get("version", "")
    if not isinstance(version, str) or (version and not re.fullmatch(r"[0-9]{1,4}\.[0-9]{1,4}\.[0-9]{1,4}", version)):
        raise ValueError("invalid version")
    return version


class ActivityStore(ConsoleStore):
    def __init__(self, path):
        self.path = str(path)
        self.lock = threading.Lock()
        with self.connect() as db:
            db.execute("PRAGMA journal_mode=WAL")
            db.execute("CREATE TABLE IF NOT EXISTS receipts (day TEXT NOT NULL, token TEXT NOT NULL, PRIMARY KEY(day,token)) WITHOUT ROWID")
            db.execute("CREATE TABLE IF NOT EXISTS daily_counts (day TEXT PRIMARY KEY, count INTEGER NOT NULL)")
            db.execute("CREATE TABLE IF NOT EXISTS monthly_receipts (month TEXT NOT NULL, token TEXT NOT NULL, PRIMARY KEY(month,token)) WITHOUT ROWID")
            db.execute("CREATE TABLE IF NOT EXISTS monthly_counts (month TEXT PRIMARY KEY, count INTEGER NOT NULL)")
            for table in ("receipts", "monthly_receipts"):
                if "version" not in [row[1] for row in db.execute("PRAGMA table_info(" + table + ")")]:
                    db.execute("ALTER TABLE " + table + " ADD COLUMN version TEXT NOT NULL DEFAULT ''")
            db.execute("CREATE TABLE IF NOT EXISTS version_counts (kind TEXT, period TEXT, version TEXT, count INTEGER NOT NULL, PRIMARY KEY(kind,period,version))")
            self.init_console(db)
        self.cleanup()

    @contextmanager
    def connect(self):
        db = sqlite3.connect(self.path, timeout=15)
        try:
            with db:
                yield db
        finally:
            db.close()

    def cleanup(self, today=None):
        today = today or datetime.now(ZONE).date()
        with self.lock, self.connect() as db:
            db.execute("PRAGMA secure_delete=ON")
            db.execute("DELETE FROM receipts WHERE day < ?", ((today - timedelta(days=7)).isoformat(),))
            db.execute("DELETE FROM monthly_receipts WHERE month < ?", ((today - timedelta(days=7)).strftime("%Y-%m"),))
            db.commit()
            db.execute("PRAGMA wal_checkpoint(TRUNCATE)")

    def record(self, payload, today=None):
        day, token = validate(payload, today)
        return self.record_period("daily", day, token, validate_version(payload))

    def record_monthly(self, payload, today=None):
        month, token = validate_monthly(payload, today)
        return self.record_period("monthly", month, token, validate_version(payload))

    def record_period(self, kind, period, token, version):
        table, counts, key = ("receipts", "daily_counts", "day") if kind == "daily" else ("monthly_receipts", "monthly_counts", "month")
        with self.lock, self.connect() as db:
            db.execute("BEGIN IMMEDIATE")
            old = db.execute(f"SELECT version FROM {table} WHERE {key}=? AND token=?", (period, token)).fetchone()
            added = old is None
            if added:
                db.execute(f"INSERT INTO {table}({key},token,version) VALUES (?,?,?)", (period, token, version))
                db.execute(f"INSERT INTO {counts}({key},count) VALUES (?,1) ON CONFLICT({key}) DO UPDATE SET count=count+1", (period,))
            elif version and old[0] != version:
                # An upgrade reclassifies this installation; it never increases DAU/MAU.
                db.execute(f"UPDATE {table} SET version=? WHERE {key}=? AND token=?", (version, period, token))
                if old[0]:
                    db.execute("UPDATE version_counts SET count=max(0,count-1) WHERE kind=? AND period=? AND version=?", (kind, period, old[0]))
            if version and (added or old[0] != version):
                db.execute("INSERT INTO version_counts VALUES (?,?,?,1) ON CONFLICT(kind,period,version) DO UPDATE SET count=count+1", (kind, period, version))
        return bool(added)

    def report(self, days):
        with self.connect() as db:
            return db.execute("SELECT day,count FROM daily_counts ORDER BY day DESC LIMIT ?", (days,)).fetchall()

    def report_monthly(self, months):
        with self.connect() as db:
            return db.execute(
                "SELECT month,count FROM monthly_counts ORDER BY month DESC LIMIT ?",
                (months,),
            ).fetchall()


class ActivityServer(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 32

    def __init__(self, address, store):
        super().__init__(address, ActivityHandler)
        self.store = store
        self.rate_lock = threading.Lock()
        self.window = int(time.monotonic() / 60)
        self.requests = 0

    def allow_request(self):
        with self.rate_lock:
            window = int(time.monotonic() / 60)
            if window != self.window:
                self.window, self.requests = window, 0
            self.requests += 1
            return self.requests <= 600

    def handle_error(self, request, client_address):
        # The standard handler logs addresses and request exceptions; do not retain them.
        pass


class ActivityHandler(ConsoleHandler, BaseHTTPRequestHandler):
    server_version = "Activity"
    sys_version = ""

    def setup(self):
        super().setup()
        self.connection.settimeout(10)

    def log_message(self, *_args):
        pass

    def reply(self, status, result):
        data = json.dumps(result, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(data)
        self.close_connection = True

    def do_GET(self):
        if self.console_get():
            return
        if self.path == "/health":
            self.reply(200, {"ok": True})
        else:
            self.reply(405 if self.path in (
                "/api/hotwater-activity", "/api/hotwater-activity/monthly"
            ) else 404, {"ok": False})

    def do_POST(self):
        if self.console_post():
            return
        if self.path not in ("/api/hotwater-activity", "/api/hotwater-activity/monthly"):
            return self.reply(404, {"ok": False})
        monthly = self.path.endswith("/monthly")
        if not self.server.allow_request():
            return self.reply(429, {"ok": False})
        if self.headers.get("Content-Type", "").split(";", 1)[0].lower() != "application/json":
            return self.reply(415, {"ok": False})
        if self.headers.get("Content-Encoding") or self.headers.get("Transfer-Encoding"):
            return self.reply(400, {"ok": False})
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            return self.reply(400, {"ok": False})
        if length <= 0 or length > MAX_BODY:
            return self.reply(413, {"ok": False})
        try:
            raw = self.rfile.read(length)
            if len(raw) != length:
                return self.reply(400, {"ok": False})
            payload = json.loads(raw)
            if monthly:
                self.server.store.record_monthly(payload)
            else:
                self.server.store.record(payload)
        except (ValueError, UnicodeDecodeError):
            return self.reply(400, {"ok": False})
        except sqlite3.Error:
            return self.reply(503, {"ok": False})
        # Do not expose whether a token has previously been observed.
        self.reply(200, {"ok": True})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--database", default="/var/lib/hotwater-activity/activity.sqlite3")
    parser.add_argument("--port", type=int, default=8101)
    reports = parser.add_mutually_exclusive_group()
    reports.add_argument("--report", type=int, metavar="DAYS")
    reports.add_argument("--report-monthly", type=int, metavar="MONTHS")
    args = parser.parse_args()
    os.umask(0o077)
    store = ActivityStore(args.database)
    if args.report is not None:
        print(json.dumps([{"day": day, "active_installations": count} for day, count in
                          store.report(max(1, min(args.report, 3660)))], ensure_ascii=False))
        return
    if args.report_monthly is not None:
        print(json.dumps([{"month": month, "active_installations": count} for month, count in
                          store.report_monthly(max(1, min(args.report_monthly, 120)))], ensure_ascii=False))
        return
    def cleanup():
        while True:
            time.sleep(3600)
            try:
                store.cleanup()
            except sqlite3.Error:
                pass
    threading.Thread(target=cleanup, daemon=True).start()
    ActivityServer(("127.0.0.1", args.port), store).serve_forever()


if __name__ == "__main__":
    main()
