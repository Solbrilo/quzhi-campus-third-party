import json
import os
from pathlib import Path
import tempfile
import unittest

from feedback_reader import read_feedback


class FeedbackTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=os.environ.get("HOTWATER_TEST_TEMP"))
        self.root = Path(self.temp.name)
        self.archive = self.root / "feedback-2026-11.jsonl"
        self.rows = [{"id": "fb_20261108_" + format(i, "016x"), "receivedAt": "2026-11-08T10:00:00Z",
                      "schemaVersion": 1, "platform": "android" if i % 2 else "harmonyos",
                      "appVersion": "4.0.0", "category": "suggestion" if i % 3 else "bug",
                      "email": "sample@example.invalid", "message": "合成反馈 " + str(i) + "\n第二行",
                      "unexpectedPrivateField": "must not return"} for i in range(61)]
        self.archive.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in self.rows), encoding="utf-8")

    def tearDown(self):
        self.temp.cleanup()

    def test_reverse_pagination_is_stable_when_feedback_arrives(self):
        page = read_feedback(self.root, {})
        self.assertEqual(len(page["items"]), 25)
        self.assertEqual(page["items"][0]["id"], self.rows[-1]["id"])
        self.assertNotIn("unexpectedPrivateField", page["items"][0])
        with self.archive.open("a", encoding="utf-8") as stream:
            stream.write(json.dumps(dict(self.rows[0], id="fb_20261108_ffffffffffffffff")) + "\n")
        seen = page["items"][:]
        while page["nextCursor"] is not None:
            page = read_feedback(self.root, {"month": page["month"], "cursor": page["nextCursor"]})
            seen.extend(page["items"])
        self.assertEqual([x["id"] for x in seen], [x["id"] for x in self.rows[::-1]])
        self.assertIn("\n第二行", seen[0]["message"])

    def test_filters_empty_month_and_private_files(self):
        (self.root / "rate.json").write_text("private", encoding="utf-8")
        result = read_feedback(self.root, {"platform": "android", "category": "bug"})
        expected = [r["id"] for r in self.rows[::-1] if r["platform"] == "android" and r["category"] == "bug"]
        self.assertEqual([r["id"] for r in result["items"]], expected)
        self.assertEqual(result["months"], ["2026-11"])
        self.assertEqual(read_feedback(self.root, {"month": "2026-10"})["items"], [])
        for query in ({"month": "../rate"}, {"cursor": "-1"}, {"platform": "unknown"}, {"file": "rate.json"}):
            with self.assertRaises(ValueError):
                read_feedback(self.root, query)


if __name__ == "__main__":
    unittest.main()
