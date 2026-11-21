#!/usr/bin/env python3
"""沈农在途 · 匿名使用统计接收端（单文件，只用标准库）。

设计口径：
  * 只收 POST /api/telemetry，body 是 gzip 过的 ndjson，或未压缩 ndjson。
  * 白名单校验以同目录 schema.json 为准：未知事件整条丢，未知字段丢，越域值降级为 "invalid"。
  * 不落 IP、不落 UA 全串、不落任何请求头 —— IP 只进内存里的限速桶，进程重启即忘。
  * 身份只有两个哈希，原像（installId）永不上传：
      i = SHA-256(installId)[:16]            永久安装标识，跨天可拼 -> 总量 / 留存 / 用龄
      d = SHA-256(installId + ":" + 日期)[:8] 旧包还在发，按天统计用
  * 原始数据按日切：data/events-YYYY-MM-DD.ndjson，保留 180 天后由 --prune 删除。
  * 聚合两层：
      --aggregate          日表 data/daily.json（日活按 i 去重，缺 i 的旧记录按 d 去重）
      --aggregate --all    另扫全量出累计表 data/users.json（设备数 / 留存 / 用龄 / 版本）
    users.json 里存的是每台设备的「按天去重的日期集合」，所以 prune 删掉原始文件也不会丢总量。

用法：
  python server.py --serve --port 18082
  python server.py --aggregate
  python server.py --aggregate --all --days 180     # cron 用这条
  python server.py --prune --days 180
  python server.py --schema-check
  curl -H 'X-Admin-Key: 见 /etc/enroute-telemetry.env' http://127.0.0.1:18090/api/telemetry/stats
"""

import argparse
import gzip
import hashlib
import json
import os
import re
import sys
import threading
import time
from datetime import date, datetime, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
SCHEMA_PATH = os.path.join(HERE, "schema.json")
DATA_DIR = os.path.join(HERE, "data")
DAILY_PATH = os.path.join(DATA_DIR, "daily.json")
USERS_PATH = os.path.join(DATA_DIR, "users.json")

UA_PREFIX = "SYAU-Enroute"
MAX_BODY = 64 * 1024
RATE_PER_MIN = 60
RETAIN_DAYS = 180
MAX_DAYS_PER_DEVICE = 400
ADMIN_KEY = os.environ.get("ENROUTE_TELEMETRY_ADMIN_KEY", "").strip()

# 公开统计页（https://wb.dkarbon.cn/enroute/）用的缓存秒数：页面每分钟刷新一次，
# 服务端没必要每次都去读两个 json
PUBLIC_TTL = 60
_public_cache = {"at": 0.0, "obj": None}

VERSION_RE = re.compile(r"^\d{1,3}\.\d{1,3}\.\d{1,3}(\+m\d{1,5})?$")
HEX = set("0123456789abcdef")


def load_schema():
    with open(SCHEMA_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


SCHEMA = load_schema()
EVENTS = SCHEMA["events"]
LIMITS = SCHEMA.get("$limits", {})


def _hex_ok(v, lo, hi):
    return isinstance(v, str) and lo <= len(v) <= hi and all(c in HEX for c in v)


def clean_line(raw, schema=SCHEMA):
    """把一条客户端记录清洗成可落盘的形式；不合格返回 None。

    客户端的白名单是善意，不能当成事实 —— 这里必须再筛一遍，
    因为抓包之后伪造一条 POST 太容易了。
    """
    try:
        o = json.loads(raw)
    except Exception:
        return None
    if not isinstance(o, dict):
        return None
    if o.get("v") != schema["version"]:
        return None
    e = o.get("e")
    if not isinstance(e, str) or e not in EVENTS:
        return None
    dom = EVENTS[e]
    props_in = o.get("p") or {}
    if not isinstance(props_in, dict):
        props_in = {}
    props = {}
    for k, v in props_in.items():
        if k not in dom:
            continue
        allowed = dom[k]
        if allowed == ["@ver"]:
            props[k] = v if isinstance(v, str) and VERSION_RE.match(v) else "invalid"
        else:
            props[k] = v if isinstance(v, str) and v in allowed else "invalid"
    t = o.get("t")
    # t 归一成毫秒。客户端 `Telemetry.buildLine` 发的是**秒**（now/1000），
    # 早期的合成测试数据用的是毫秒；两种都收，秒自动 ×1000。
    if not isinstance(t, int) or isinstance(t, bool):
        return None
    if 1_000_000_000 <= t < 100_000_000_000:
        t *= 1000
    if t < 1_500_000_000_000 or t > 4_000_000_000_000:
        return None
    i = o.get("i")
    d = o.get("d")
    s = o.get("s")
    return {
        "v": schema["version"],
        "e": e,
        "t": t,
        "p": props,
        # 永久安装标识 16 位 / 当日标识 8 位；旧服务端写过的 8 位 d 也照收
        "i": i if _hex_ok(i, 16, 16) else "",
        "d": d if _hex_ok(d, 8, 16) else "",
        "s": s if isinstance(s, str) and len(s) <= 8 else "",
    }


def day_path(day):
    os.makedirs(DATA_DIR, exist_ok=True)
    return os.path.join(DATA_DIR, "events-" + day + ".ndjson")


_write_lock = threading.Lock()


def append_lines(lines):
    """按服务器本地日期分文件；一次请求拿一次锁，"不丢半条" 比 "写飞快" 重要。"""
    if not lines:
        return 0
    day = date.today().isoformat()
    path = day_path(day)
    payload = "".join(json.dumps(x, ensure_ascii=False, separators=(",", ":")) + "\n" for x in lines)
    with _write_lock:
        with open(path, "a", encoding="utf-8") as f:
            f.write(payload)
    return len(lines)


# ── 限速：每 IP 每分钟 N 次。IP 只活在这个字典里，不落盘、不进日志 ──
_buckets = {}
_bucket_lock = threading.Lock()


def rate_ok(ip):
    now = time.time()
    window = int(now // 60)
    with _bucket_lock:
        key = (ip, window)
        n = _buckets.get(key, 0) + 1
        _buckets[key] = n
        if len(_buckets) > 4096:
            for k in [k for k in _buckets if k[1] < window - 1]:
                _buckets.pop(k, None)
    return n <= RATE_PER_MIN


# ── 读盘小工具 ──


def _read_json(path, default):
    if not os.path.exists(path):
        return default
    try:
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return default


def _write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
    os.replace(tmp, path)


def event_files(days=None):
    """返回 [(日期, 路径)]，按日期升序；days 给了就只留最近这些天。"""
    if not os.path.isdir(DATA_DIR):
        return []
    out = []
    for name in os.listdir(DATA_DIR):
        m = re.match(r"^events-(\d{4}-\d{2}-\d{2})\.ndjson$", name)
        if m:
            out.append((m.group(1), os.path.join(DATA_DIR, name)))
    out.sort()
    if days is not None:
        cutoff = (date.today() - timedelta(days=max(days, 1) - 1)).isoformat()
        out = [(d, p) for d, p in out if d >= cutoff]
    return out


def _iter_records(path):
    try:
        with open(path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    o = json.loads(line)
                except Exception:
                    continue
                if isinstance(o, dict):
                    yield o
    except OSError:
        return


def device_key(o):
    """一台设备在这份数据里的键：优先 i，缺 i 的旧记录退回 "d:" + d。"""
    i = o.get("i") or ""
    if i:
        return "i:" + i
    d = o.get("d") or ""
    if d:
        return "d:" + d
    return ""


# ── 服务 ──


class Handler(BaseHTTPRequestHandler):
    server_version = "enroute-telemetry/2"
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        # 默认实现会把 IP 与路径写进 stderr，这里只留一行状态码，不带 IP
        sys.stderr.write("%s %s\n" % (self.command, fmt % args))

    def _send(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_text(self, code, text):
        body = text.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _real_ip(self):
        """真客户端 IP。

        我们在 nginx 后面，socket 对端永远是 127.0.0.1；nginx 用
        `X-Forwarded-For $remote_addr` 把真地址塞进来。不读这个头的话，
        限流会把全网当成一台机器限，管理钥匙也会被「本机」两个字直接放行。
        """
        xff = (self.headers.get("X-Forwarded-For") or "").split(",")[0].strip()
        return xff or self.client_address[0]

    def _admin_ok(self):
        # 本机直连（systemd 只监听 127.0.0.1）不用钥匙；经 nginx 进来的必须带对钥匙
        if self._real_ip() in ("127.0.0.1", "::1"):
            return True
        if not ADMIN_KEY:
            return False
        return self.headers.get("X-Admin-Key", "").strip() == ADMIN_KEY

    def do_GET(self):
        path = self.path.split("?", 1)[0].rstrip("/")
        if path == "/api/telemetry/health":
            self._send(200, {"ok": True, "version": SCHEMA["version"], "events": len(EVENTS)})
            return
        if path == "/api/telemetry/public":
            # 公开：聚合数字，不需要钥匙，但只有每分钟刷新的快照
            now = time.time()
            if _public_cache["obj"] is None or now - _public_cache["at"] > PUBLIC_TTL:
                _public_cache["obj"] = build_public()
                _public_cache["at"] = now
            self._send(200, _public_cache["obj"])
            return
        if path == "/api/telemetry/stats":
            if not self._admin_ok():
                self._send(403, {"error": "admin key required"})
                return
            self._send(200, build_stats())
            return
        if path == "/api/telemetry/stats.txt":
            if not self._admin_ok():
                self._send_text(403, "admin key required\n")
                return
            self._send_text(200, render_stats_text(build_stats()))
            return
        self._send(404, {"error": "not found"})

    def do_POST(self):
        if self.path.rstrip("/") != "/api/telemetry":
            self._send(404, {"error": "not found"})
            return
        ua = self.headers.get("User-Agent", "")
        if not ua.startswith(UA_PREFIX):
            self._send(403, {"error": "bad user agent"})
            return
        ip = self._real_ip()
        if not rate_ok(ip):
            self._send(429, {"error": "slow down"})
            return
        try:
            n = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            n = 0
        if n <= 0 or n > MAX_BODY:
            self._send(413, {"error": "body size"})
            return
        raw = self.rfile.read(n)
        if self.headers.get("Content-Encoding", "").lower() == "gzip":
            try:
                raw = gzip.decompress(raw)
            except Exception:
                self._send(400, {"error": "gzip"})
                return
        text = raw.decode("utf-8", "replace")
        kept, dropped = [], 0
        for line in text.splitlines():
            line = line.strip()
            if not line:
                continue
            c = clean_line(line)
            if c is None:
                dropped += 1
            else:
                kept.append(c)
        accepted = append_lines(kept)
        self._send(200, {"accepted": accepted, "dropped": dropped})


def serve(port, host="0.0.0.0"):
    addr = (host, port)
    httpd = ThreadingHTTPServer(addr, Handler)
    print("telemetry listening on %s:%d, %d events in schema" % (host, port, len(EVENTS)))
    if not ADMIN_KEY:
        print("warning: ENROUTE_TELEMETRY_ADMIN_KEY 未设置，/stats 只对本机开放")
    httpd.serve_forever()


# ── 聚合 ──


def aggregate(days=1, all_time=False):
    """日表 + （可选）累计表。

    日表：每天一条，dau 按 i 去重；没有 i 的旧记录按 d 去重，单独记在 dau_legacy 里。
    累计表：扫全量原始文件，每台设备记「按天去重的日期集合」，据此算总量、留存、用龄。
    """
    if not os.path.isdir(DATA_DIR):
        print("no data dir")
        return
    out = {}
    for day, path in event_files(days):
        devices, legacy = set(), set()
        events, versions, abis = {}, {}, {}
        for o in _iter_records(path):
            k = device_key(o)
            if k.startswith("i:"):
                devices.add(k)
            elif k:
                legacy.add(k)
            e = o.get("e", "")
            events[e] = events.get(e, 0) + 1
            p = o.get("p") or {}
            ver = p.get("ver")
            if isinstance(ver, str):
                versions[ver] = versions.get(ver, 0) + 1
            abi = p.get("abi")
            if isinstance(abi, str):
                abis[abi] = abis.get(abi, 0) + 1
        out[day] = {
            "dau": len(devices | legacy),
            "dau_legacy": len(legacy),
            "events": events,
            "versions": versions,
            "abis": abis,
        }
    old = _read_json(DAILY_PATH, {})
    if not isinstance(old, dict):
        old = {}
    old.update(out)
    keys = sorted(old)[-400:]
    _write_json(DAILY_PATH, {k: old[k] for k in keys})
    print("aggregated %d day(s) -> %s" % (len(out), DAILY_PATH))

    if all_time:
        keys_now = _scan_devices()
        _write_json(USERS_PATH, _users_payload(keys_now))
        print("aggregated %d device(s) -> %s" % (len(keys_now), USERS_PATH))


def _scan_devices():
    """全量扫原始文件，与 users.json 里已有的日期集合取并集（prune 之后也不丢）。"""
    stored = _read_json(USERS_PATH, {})
    prev = stored.get("keys") if isinstance(stored, dict) else None
    keys = {}
    if isinstance(prev, dict):
        for k, rec in prev.items():
            days = rec.get("days") if isinstance(rec, dict) else None
            if isinstance(days, list):
                keys[k] = {"days": sorted(set(x for x in days if isinstance(x, str))), "v": rec.get("v", "")}
    for day, path in event_files(None):
        for o in _iter_records(path):
            k = device_key(o)
            if not k.startswith("i:"):
                continue
            rec = keys.get(k)
            if rec is None:
                rec = {"days": [], "v": ""}
                keys[k] = rec
            days = rec["days"]
            if day not in days and len(days) < MAX_DAYS_PER_DEVICE:
                days.append(day)
            ver = (o.get("p") or {}).get("ver")
            if isinstance(ver, str) and VERSION_RE.match(ver):
                rec["v"] = ver
    return keys


def _retention(keys, today):
    cohorts = {}
    for rec in keys.values():
        days = rec.get("days") or []
        if not days:
            continue
        first = min(days)
        cohorts.setdefault(first, []).append(set(days))

    def rate(offset):
        cohort = returned = 0
        for first, group in cohorts.items():
            try:
                fd = date.fromisoformat(first)
            except ValueError:
                continue
            target = fd + timedelta(days=offset)
            # 只统计「那一天已经过完」的队列，否则当天注册当天回访会被算成没回访
            if target >= today:
                continue
            cohort += len(group)
            hit = target.isoformat()
            returned += sum(1 for s in group if hit in s)
        return {
            "cohort": cohort,
            "returned": returned,
            "rate": round(returned / cohort, 4) if cohort else None,
        }

    return {"d1": rate(1), "d7": rate(7)}


def _users_payload(keys):
    today = date.today()
    buckets = {"1": 0, "2-3": 0, "4-7": 0, "8-30": 0, "31+": 0}
    by_version = {}
    for rec in keys.values():
        n = len(rec.get("days") or [])
        if n <= 1:
            buckets["1"] += 1
        elif n <= 3:
            buckets["2-3"] += 1
        elif n <= 7:
            buckets["4-7"] += 1
        elif n <= 30:
            buckets["8-30"] += 1
        else:
            buckets["31+"] += 1
        v = rec.get("v") or "unknown"
        by_version[v] = by_version.get(v, 0) + 1
    return {
        "generated_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "users_total": len(keys),
        "active_days": buckets,
        "by_version": by_version,
        "retention": _retention(keys, today),
        "keys": keys,
    }


# ── 查看 ──


def build_stats():
    daily = _read_json(DAILY_PATH, {})
    users = _read_json(USERS_PATH, {})
    if not isinstance(daily, dict):
        daily = {}
    if not isinstance(users, dict):
        users = {}
    today = date.today().isoformat()
    return {
        "today": daily.get(today, {"dau": 0, "dau_legacy": 0, "events": {}}),
        "days": daily,
        "total": {
            "users": users.get("users_total", 0),
            "active_days": users.get("active_days", {}),
            "by_version": users.get("by_version", {}),
        },
        "retention": users.get("retention", {}),
        "generated_at": users.get("generated_at", ""),
    }


def build_public(days=30):
    """公开统计页的数据源。

    只给聚合数字：**一个设备标识都不出去**（不给 i、不给 d、不给 IP），
    也不给事件明细里的任何内容字段 —— 事件本身就只带枚举与档位。
    """
    daily = _read_json(DAILY_PATH, {})
    users = _read_json(USERS_PATH, {})
    if not isinstance(daily, dict):
        daily = {}
    if not isinstance(users, dict):
        users = {}
    today = date.today().isoformat()
    kept = sorted(daily.keys())[-days:]
    series = []
    for d in kept:
        row = daily.get(d) or {}
        series.append({"d": d, "dau": int(row.get("dau", 0) or 0)})
    t = daily.get(today) or {}
    events = t.get("events") or {}
    versions = users.get("by_version") or t.get("versions") or {}
    versions = {str(k): int(v) for k, v in versions.items() if isinstance(k, str)}
    return {
        "generated_at": users.get("generated_at") or "",
        "server_time": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "today": {
            "date": today,
            "dau": int(t.get("dau", 0) or 0),
            "events": int(sum(events.values())) if events else 0,
        },
        "dau_series": series,
        "users_total": int(users.get("users_total", 0) or 0),
        "versions": versions,
        "active_days": users.get("active_days") or {},
        "retention": users.get("retention") or {},
        "events_today": {str(k): int(v) for k, v in (events or {}).items()},
        "days_tracked": len(daily),
    }


def render_stats_text(stats):
    lines = []
    lines.append("沈农在途 · 使用统计")
    lines.append("生成于 " + (stats.get("generated_at") or "（还没跑过 --aggregate --all）"))
    t = stats.get("today") or {}
    total = stats.get("total") or {}
    lines.append("")
    lines.append("今日日活 %-6s 设备总数 %-6s" % (t.get("dau", 0), total.get("users", 0)))
    lines.append("")
    lines.append("日期          日活   旧dau  事件数")
    days = stats.get("days") or {}
    for day in sorted(days)[-14:]:
        e = days[day] or {}
        nevents = sum((e.get("events") or {}).values())
        lines.append("%-12s  %-5s  %-5s  %-5s" % (day, e.get("dau", 0), e.get("dau_legacy", 0), nevents))
    r = stats.get("retention") or {}
    lines.append("")
    for key in ("d1", "d7"):
        item = r.get(key) or {}
        rate = item.get("rate")
        pct = "n/a" if rate is None else "%.1f%%" % (rate * 100)
        lines.append("%s 回访 %-7s (%s/%s)" % (key.upper(), pct, item.get("returned", 0), item.get("cohort", 0)))
    lines.append("")
    lines.append("用龄分布 " + json.dumps(total.get("active_days") or {}, ensure_ascii=False))
    lines.append("版本分布 " + json.dumps(total.get("by_version") or {}, ensure_ascii=False))
    return "\n".join(lines) + "\n"


def prune(days):
    if not os.path.isdir(DATA_DIR):
        print("no data dir")
        return
    cutoff = date.today() - timedelta(days=days)
    removed = 0
    for name in os.listdir(DATA_DIR):
        m = re.match(r"^events-(\d{4}-\d{2}-\d{2})\.ndjson$", name)
        if not m:
            continue
        try:
            d = date.fromisoformat(m.group(1))
        except ValueError:
            continue
        if d < cutoff:
            os.remove(os.path.join(DATA_DIR, name))
            removed += 1
    print("pruned %d file(s) older than %s" % (removed, cutoff.isoformat()))
    if removed and not os.path.exists(USERS_PATH):
        print("warning: users.json 不存在，清掉的这些天的设备数已经算不回来了")


def schema_check():
    bad = 0
    for name, fields in EVENTS.items():
        if not isinstance(fields, dict):
            print("bad event " + name)
            bad += 1
            continue
        for k, dom in fields.items():
            if not isinstance(dom, list) or not dom:
                print("bad domain " + name + "." + k)
                bad += 1
    print("schema ok: %d events, %d problems" % (len(EVENTS), bad))
    return 1 if bad else 0


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--serve", action="store_true")
    ap.add_argument("--host", default="0.0.0.0")
    ap.add_argument("--port", type=int, default=18082)
    ap.add_argument("--aggregate", action="store_true")
    ap.add_argument("--all", action="store_true", help="随 --aggregate 一起扫全量，写 users.json")
    ap.add_argument("--days", type=int, default=1)
    ap.add_argument("--prune", action="store_true")
    ap.add_argument("--schema-check", action="store_true")
    a = ap.parse_args(argv)
    if a.schema_check:
        return schema_check()
    if a.aggregate:
        aggregate(a.days, all_time=a.all)
    if a.prune:
        prune(a.days if a.days != 1 else RETAIN_DAYS)
    if a.serve:
        serve(a.port, a.host)
    if not (a.aggregate or a.prune or a.serve):
        ap.print_help()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
