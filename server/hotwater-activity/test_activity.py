import hashlib
import http.client
import json
import os
from pathlib import Path
import secrets
import sqlite3
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
from datetime import datetime

import console
from activity_server import ActivityStore, ActivityServer, ZONE, validate, validate_monthly


class ActivityTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=os.environ.get("HOTWATER_TEST_TEMP"))
        self.path = Path(self.temp.name) / "test.sqlite"
        self.today = datetime.now(ZONE).date()
        self.day = self.today.isoformat()
        self.month = self.day[:7]

    def tearDown(self):
        self.temp.cleanup()

    def test_legacy_database_and_upgrade_do_not_double_count(self):
        with sqlite3.connect(self.path) as db:
            db.execute("CREATE TABLE receipts(day TEXT,token TEXT,PRIMARY KEY(day,token)) WITHOUT ROWID")
            db.execute("CREATE TABLE daily_counts(day TEXT PRIMARY KEY,count INTEGER)")
            db.execute("INSERT INTO receipts VALUES (?,?)", (self.day, "a" * 64))
            db.execute("INSERT INTO daily_counts VALUES (?,1)", (self.day,))
        db.close()
        store = ActivityStore(self.path)
        self.assertFalse(store.record({"day": self.day, "token": "a" * 64, "version": "4.0.0"}))
        store.record({"day": self.day, "token": "a" * 64})
        store.record_monthly({"month": self.month, "token": "b" * 64})
        store.record_monthly({"month": self.month, "token": "b" * 64, "version": "4.0.0"})
        store.record_monthly({"month": self.month, "token": "b" * 64, "version": "4.0.1"})
        self.assertEqual(store.report(1)[0][1], 1)
        self.assertEqual(store.report_monthly(1)[0][1], 1)
        stats = store.dashboard()
        self.assertEqual(stats["versions"]["monthly"], [{"version": "4.0.1", "count": 1}])
        self.assertEqual(stats["versions"]["daily"], [{"version": "4.0.0", "count": 1}])

    def test_optional_version_and_rejected_fields(self):
        validate({"day": self.day, "token": "a" * 64})
        validate_monthly({"month": self.month, "token": "a" * 64, "version": "4.0.0"})
        with self.assertRaises(ValueError):
            validate({"day": self.day, "token": "a" * 64, "telephone": "redacted"})
        with self.assertRaises(ValueError):
            validate_monthly({"month": self.month, "token": "a" * 64, "version": "<script>"})

    def fixture(self):
        now = int(time.time())
        return {"title": "演示公告", "body": "离线测试内容", "starts": now - 10, "pushes": now,
                "expires": now + 86400, "enabled": True, "options": [{"label": "已了解", "input": False}, {"label": "建议", "input": True}]}

    def test_notice_schedule_revision_and_response_idempotence(self):
        store = ActivityStore(self.path)
        payload = self.fixture()
        notice_id = store.save_notice(payload)
        now = int(time.time())
        self.assertEqual(len(store.notices(True, now)), 1)
        self.assertEqual(store.notices(True, now + 90000), [])
        self.assertEqual(store.notices(True, now - 100), [])
        response = {"id": notice_id, "revision": 1, "token": "c" * 64, "option": "1", "text": ""}
        store.respond(response); store.respond(response)
        store.respond(dict(response, option="2", text="<script>这是纯文本</script>"))
        self.assertEqual(store.responses(notice_id, 1)["counts"], [{"option": "2", "count": 1}])
        store.save_notice(dict(payload, id=notice_id, revision=1, title="更新公告"))
        self.assertEqual(store.notices(True)[0]["revision"], 2)
        self.assertEqual(len(store.responses(notice_id, 1)["texts"]), 1)
        self.assertEqual(store.responses(notice_id, 1)["options"][1]["label"], "建议")
        with self.assertRaises(ValueError): store.respond(response)
        with self.assertRaises(ValueError): store.save_notice(dict(payload, id=notice_id, revision=1))

    def test_console_auth_csrf_and_public_isolation(self):
        salt = secrets.token_bytes(16); password = secrets.token_urlsafe(20)
        auth = Path(self.temp.name) / "auth.json"
        auth.write_text(json.dumps({"salt": salt.hex(), "hash": hashlib.pbkdf2_hmac("sha256", password.encode(), salt, 600000).hex()}))
        original_auth = console.AUTH_FILE; console.AUTH_FILE = str(auth)
        server = ActivityServer(("127.0.0.1", 0), ActivityStore(self.path))
        thread = threading.Thread(target=server.serve_forever, daemon=True); thread.start()
        def call(path, data=None, headers=None):
            client = http.client.HTTPConnection("127.0.0.1", server.server_port)
            h = {"Content-Type": "application/json"}; h.update(headers or {})
            client.request("GET" if data is None else "POST", path, None if data is None else json.dumps(data), h)
            response = client.getresponse(); status = response.status; hdr = dict(response.getheaders()); body = response.read(); client.close()
            return status, hdr, json.loads(body)
        try:
            self.assertEqual(call(console.PREFIX + "/stats")[0], 401)
            with patch.object(console, "feedback_page", return_value={"items": []}) as reader:
                self.assertEqual(call(console.PREFIX + "/feedback?month=2026-11")[0], 401)
                reader.assert_not_called()
            self.assertEqual(call(console.PREFIX + "/login", {"password": password})[0], 403)
            status, hdr, body = call(console.PREFIX + "/login", {"password": password}, {"Origin": "https://nebularive.top"})
            self.assertEqual(status, 200)
            self.assertIn("HttpOnly; Secure; SameSite=Strict", hdr["Set-Cookie"])
            cookie = hdr["Set-Cookie"].split(";")[0]
            with patch.object(console, "feedback_page", return_value={"items": []}) as reader:
                self.assertEqual(call(console.PREFIX + "/feedback?month=2026-11", headers={"Cookie": cookie})[2], {"items": []})
                reader.assert_called_once_with("month=2026-11")
            self.assertEqual(call(console.PREFIX + "/notices", self.fixture(), {"Cookie": cookie, "Origin": "https://nebularive.top"})[0], 403)
            self.assertEqual(call(console.PREFIX + "/notices", self.fixture(), {"Cookie": cookie, "Origin": "https://nebularive.top", "X-CSRF-Token": body["csrf"]})[0], 200)
            public = call(console.PUBLIC)[2]
            self.assertEqual(len(public["notices"]), 1)
            self.assertNotIn("responses", public)
        finally:
            server.shutdown(); server.server_close(); thread.join(); console.AUTH_FILE = original_auth


if __name__ == "__main__": unittest.main()
