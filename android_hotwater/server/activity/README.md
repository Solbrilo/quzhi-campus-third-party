# Anonymous daily activity

Android sends only 'day' (Asia/Shanghai date) and 'token' (64 lowercase hex
characters) to POST https://nebularive.top/api/hotwater-activity.

The token is HMAC-SHA256 of the date and a domain separator, keyed by a random
32-byte installation seed in Android's no-backup directory. The seed never
leaves the device. Tokens change daily and contain no account, telephone,
hardware identifier, heater code, app version, bill or message data.

## Counting and retention

- This measures active installations, not identified people. Separate phones,
  reinstallations and clearing app data can count separately.
- A transaction and unique (day, token) key count repeated deliveries once.
- The client records app foreground use, acknowledges successful delivery,
  retries through WorkManager, and drops reports older than seven days.
- Receipts remain for the retry window (up to approximately eight calendar
  days) and expire hourly. Aggregate day/count rows remain available.
- The switch at the bottom of Settings > View configuration cancels pending reports. Clearing app data deletes the
  local seed and disables reporting until re-enabled.
- No request bodies or remote addresses are logged. The exact Nginx location
  disables normal and site-total access logs, and does not forward client
  headers or addresses to the collector.

## Operations

Production uses a dedicated dynamic system user, Python's standard library,
SQLite, and loopback port 8101. Private database:
/var/lib/hotwater-activity/activity.sqlite3 (mode 0600).
There is no public read/report API.

Read date/count totals over the authorized SSH connection:

    ssh nebularive "python3 /opt/hotwater-activity/activity_server.py --report 30"

Check health:

    ssh nebularive "systemctl is-active hotwater-activity.service; curl -fsS http://127.0.0.1:8101/health"

Before deployment, back up exact targets and record absent targets in a
manifest. Install only the collector, unit and exact Nginx location; validate
the unit and Nginx before reload. See deployment.txt for the rollback point.
Rollback restores backed-up files (or removes only targets marked absent),
stops/disables the new service if reverting its introduction, validates and
reloads Nginx, and preserves the private database.

Local checks: python -m unittest -v test_activity.
Public smoke checks cover 405 GET, 404 path-info, 400 invalid fields and 415
wrong media type. Valid duplicate smoke reports use synthetic tokens; remove
only that synthetic receipt and its single count contribution afterward.
