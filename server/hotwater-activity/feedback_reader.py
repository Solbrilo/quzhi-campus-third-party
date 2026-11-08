"""Read-only, local Unix-socket bridge for the existing private feedback archive."""
import json
import os
import re
import socketserver
import stat
from pathlib import Path

MONTH = re.compile(r"[0-9]{4}-(?:0[1-9]|1[0-2])")
CATEGORIES = ("bug", "suggestion", "experience", "other")
FIELDS = ("id", "receivedAt", "platform", "appVersion", "category", "email", "message")
SOCKET = "/run/hotwater-feedback-reader/reader.sock"
STORAGE = "/www/wwwroot/.nebularive-private/hotwater-feedback"


def previous_line(stream, end):
    """Return one bounded line and its byte cursor, without reading the whole month."""
    parts = b""
    position = end
    while position:
        start = max(0, position - 8192)
        stream.seek(start)
        chunk = stream.read(position - start)
        if position == end:
            chunk = chunk.rstrip(b"\n")
        index = chunk.rfind(b"\n")
        if index >= 0:
            return chunk[index + 1:] + parts, start + index + 1
        parts = chunk + parts
        if len(parts) > 16384:
            raise ValueError("Invalid archive record")
        position = start
    return parts, 0


def read_feedback(directory, query):
    if not isinstance(query, dict) or set(query) - {"month", "cursor", "platform", "category"}:
        raise ValueError("Invalid query")
    category = query.get("category", "")
    platform = query.get("platform", "")
    if category not in ("", *CATEGORIES) or platform not in ("", "android", "harmonyos"):
        raise ValueError("Invalid filter")
    root = Path(directory)
    months = sorted((p.name[9:-6] for p in root.iterdir()
                     if re.fullmatch(r"feedback-[0-9]{4}-(?:0[1-9]|1[0-2])\.jsonl", p.name)
                     and not p.is_symlink() and p.is_file()), reverse=True)
    month = query.get("month") or (months[0] if months else "")
    if month and (not isinstance(month, str) or not MONTH.fullmatch(month)):
        raise ValueError("Invalid month")
    result = {"months": months, "month": month, "items": [], "nextCursor": None}
    if month not in months:
        return result
    cursor = query.get("cursor")
    if cursor is not None and (not isinstance(cursor, str) or not re.fullmatch(r"[0-9]{1,12}", cursor)):
        raise ValueError("Invalid cursor")
    # No symlinks, no access to rate state, and no change to the writer's 0700/0600 permissions.
    fd = os.open(root / ("feedback-" + month + ".jsonl"), os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
    with os.fdopen(fd, "rb") as stream:
        if not stat.S_ISREG(os.fstat(stream.fileno()).st_mode):
            raise ValueError("Invalid archive")
        if os.name == "posix":
            import fcntl
            fcntl.flock(stream, fcntl.LOCK_SH | fcntl.LOCK_NB)
        size = os.fstat(stream.fileno()).st_size
        end = size if cursor is None else int(cursor)
        if end > size:
            raise ValueError("Invalid cursor")
        scanned = 0
        while end and len(result["items"]) < 25 and scanned < 4 * 1024 * 1024:
            raw, start = previous_line(stream, end)
            scanned += end - start
            end = start
            try:
                row = json.loads(raw)
            except (ValueError, UnicodeDecodeError):
                continue
            if not isinstance(row, dict) or not all(isinstance(row.get(k), str) for k in FIELDS):
                continue
            if not re.fullmatch(r"fb_[0-9]{8}_[a-f0-9]{16}", row["id"]):
                continue
            if row["platform"] not in ("android", "harmonyos") or row["category"] not in CATEGORIES:
                continue
            if category and category != row["category"] or platform and platform != row["platform"]:
                continue
            result["items"].append({k: row[k] for k in FIELDS})
        result["nextCursor"] = str(end) if end else None
    return result


def serve():
    class Handler(socketserver.StreamRequestHandler):
        def handle(self):
            self.connection.settimeout(5)
            try:
                raw = self.rfile.readline(2049)
                if len(raw) > 2048 or not raw.endswith(b"\n"):
                    raise ValueError("Invalid request")
                response = read_feedback(STORAGE, json.loads(raw))
            except (OSError, ValueError, TypeError):
                response = {"error": "反馈暂时无法读取，请稍后重试"}
            self.wfile.write(json.dumps(response, ensure_ascii=False).encode() + b"\n")

    class Server(socketserver.UnixStreamServer):
        def handle_error(self, request, client_address):
            pass  # Never log user messages, email addresses, or request bodies.

    Path(SOCKET).unlink(missing_ok=True)
    with Server(SOCKET, Handler) as server:
        os.chmod(SOCKET, 0o660)
        server.serve_forever()


if __name__ == "__main__":
    serve()
