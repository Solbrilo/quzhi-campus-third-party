import concurrent.futures
from datetime import date, datetime, timedelta
import json
import tempfile
import threading
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from pathlib import Path
from activity_server import ActivityStore, ActivityServer, ZONE, validate, validate_monthly


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

    def test_monthly_receipts_count_each_installation_once_and_keep_daily_totals(self):
        self.store.record(self.payload, self.today)
        monthly = {"month": "2026-10", "token": "b" * 64}
        self.assertTrue(self.store.record_monthly(monthly, self.today))
        self.assertFalse(self.store.record_monthly(monthly, self.today))
        self.assertEqual(self.store.report(30), [("2026-10-01", 1)])
        self.assertEqual(self.store.report_monthly(12), [("2026-10", 1)])
        self.assertTrue(self.store.record_monthly({"month": "2026-09", "token": "b" * 64}, self.today))
        self.assertEqual(self.store.report_monthly(12), [("2026-10", 1), ("2026-09", 1)])

    def test_monthly_payload_accepts_current_or_retry_month_only(self):
        self.assertEqual(validate_monthly({"month": "2026-10", "token": "c" * 64}, self.today),
                         ("2026-10", "c" * 64))
        for month in ["2026-08", "2026-11", "2026-13", "2026-9"]:
            with self.assertRaises(ValueError):
                validate_monthly({"month": month, "token": "c" * 64}, self.today)
        with self.assertRaises(ValueError):
            validate_monthly({"month": "2026-10", "token": "c" * 64, "telephone": "test"}, self.today)

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
            monthly = json.dumps({
                "month": datetime.now(ZONE).strftime("%Y-%m"), "token": "d" * 64,
            }).encode()
            self.assertEqual(status("/api/hotwater-activity/monthly", monthly), 200)
            self.assertEqual(status("/api/hotwater-activity/monthly/extra", monthly), 404)
            self.assertEqual(status("/api/hotwater-activity/monthly", b"{}"), 400)
        finally:
            server.shutdown()
            server.server_close()


if __name__ == "__main__":
    unittest.main()
