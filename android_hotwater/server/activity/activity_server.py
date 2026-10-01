"""Anonymous daily installation counts. No account data or HTTP request logs."""
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

ZONE = ZoneInfo("Asia/Shanghai")
MAX_BODY = 256


def validate(payload, today=None):
    today = today or datetime.now(ZONE).date()
    if not isinstance(payload, dict) or set(payload) != {"day", "token"}:
        raise ValueError("invalid fields")
    day, token = payload["day"], payload["token"]
    if not isinstance(day, str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", day):
        raise ValueError("invalid day")
    parsed = date.fromisoformat(day)
    if parsed > today or parsed < today - timedelta(days=7):
        raise ValueError("day outside retry window")
    if not isinstance(token, str) or not re.fullmatch(r"[a-f0-9]{64}", token):
        raise ValueError("invalid token")
    return day, token


class ActivityStore:
    def __init__(self, path):
        self.path = str(path)
        self.lock = threading.Lock()
        with self.connect() as db:
            db.execute("PRAGMA journal_mode=WAL")
            db.execute("CREATE TABLE IF NOT EXISTS receipts (day TEXT NOT NULL, token TEXT NOT NULL, PRIMARY KEY(day,token)) WITHOUT ROWID")
            db.execute("CREATE TABLE IF NOT EXISTS daily_counts (day TEXT PRIMARY KEY, count INTEGER NOT NULL)")
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
            db.commit()
            db.execute("PRAGMA wal_checkpoint(TRUNCATE)")

    def record(self, payload, today=None):
        day, token = validate(payload, today)
        with self.lock, self.connect() as db:
            db.execute("BEGIN IMMEDIATE")
            added = db.execute("INSERT OR IGNORE INTO receipts(day,token) VALUES (?,?)", (day, token)).rowcount
            if added:
                db.execute("INSERT INTO daily_counts(day,count) VALUES (?,1) ON CONFLICT(day) DO UPDATE SET count=count+1", (day,))
        return bool(added)

    def report(self, days):
        with self.connect() as db:
            return db.execute("SELECT day,count FROM daily_counts ORDER BY day DESC LIMIT ?", (days,)).fetchall()


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


class ActivityHandler(BaseHTTPRequestHandler):
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
        if self.path == "/health":
            self.reply(200, {"ok": True})
        else:
            self.reply(405 if self.path == "/api/hotwater-activity" else 404, {"ok": False})

    def do_POST(self):
        if self.path != "/api/hotwater-activity":
            return self.reply(404, {"ok": False})
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
    parser.add_argument("--report", type=int, metavar="DAYS")
    args = parser.parse_args()
    os.umask(0o077)
    store = ActivityStore(args.database)
    if args.report is not None:
        print(json.dumps([{"day": day, "active_installations": count} for day, count in
                          store.report(max(1, min(args.report, 3660)))], ensure_ascii=False))
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
