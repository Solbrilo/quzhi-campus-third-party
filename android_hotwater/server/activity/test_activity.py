import concurrent.futures
from datetime import date, timedelta
import json
import tempfile
import threading
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from pathlib import Path
from activity_server import ActivityStore, ActivityServer, validate


class ActivityTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.store = ActivityStore(Path(self.temp.name) / "activity.sqlite3")
        self.today = date(2026, 10, 1)
        self.payload = {"day": self.today.isoformat(), "token": "a" * 64}

    def tearDown(self):
        self.temp.cleanup()

    def test_concurrent_duplicate_reports_count_once(self):
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            results = list(pool.map(lambda _: self.store.record(self.payload, self.today), range(24)))
        self.assertEqual(sum(results), 1)
        self.assertEqual(self.store.report(30), [("2026-10-01", 1)])

    def test_rejects_sensitive_extra_fields_and_bad_dates(self):
        for extra in ["telephone", "userId", "snCode", "ip", "version"]:
            with self.assertRaises(ValueError):
                validate(dict(self.payload, **{extra: "test"}), self.today)
        for day in ["2026-10-02", "2026-09-23", "2026-2-01"]:
            with self.assertRaises(ValueError):
                validate(dict(self.payload, day=day), self.today)

    def test_expiry_deletes_receipts_but_preserves_counts(self):
        self.store.record(self.payload, self.today)
        self.store.cleanup(self.today + timedelta(days=8))
        with self.store.connect() as db:
            self.assertEqual(db.execute("SELECT count(*) FROM receipts").fetchone()[0], 0)
        self.assertEqual(self.store.report(1), [("2026-10-01", 1)])

    def test_http_contract(self):
        server = ActivityServer(("127.0.0.1", 0), self.store)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        url = "http://127.0.0.1:" + str(server.server_port)
        def status(path, data=None, content_type="application/json"):
            request = Request(url + path, data=data, headers={"Content-Type": content_type})
            try:
                with urlopen(request) as response:
                    return response.status
            except HTTPError as error:
                return error.code
        try:
            self.assertEqual(status("/health"), 200)
            self.assertEqual(status("/api/hotwater-activity"), 405)
            self.assertEqual(status("/api/hotwater-activity/", b"{}"), 404)
            self.assertEqual(status("/api/hotwater-activity", b"{}"), 400)
            self.assertEqual(status("/api/hotwater-activity", b"{}", "text/plain"), 415)
            self.assertEqual(status("/api/hotwater-activity", b"x" * 257), 413)
        finally:
            server.shutdown()
            server.server_close()


if __name__ == "__main__":
    unittest.main()
