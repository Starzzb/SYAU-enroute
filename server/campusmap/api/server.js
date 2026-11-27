/**
 * 校园地图属性补丁服务（campusmap）
 *
 * 职责：接收内测用户对地图要素属性的补充，追加到 JSONL，供回灌与审核。
 * 不做：不存几何、不存底图、不存任何明文身份信息。
 *
 * ── 身份与可追溯性（主人要求：知道是谁传的，但不泄露）──
 * 请求里带 sid（学号，登录后才有）与 did（设备随机 ID）：
 *   · contributor hash  ch = HMAC-SHA256(SECRET, sid|did)   → 用于「是不是同一个人」的判断与限流
 *   · sid_enc = AES-256-GCM(SECRET, sid)                    → 只有持 SECRET 才能解回学号（可追溯）
 *   · 明文 sid / did **绝不落盘、绝不进日志**，只在内存里算完即弃
 *   · 学号空间小，拿到 ch 无法反推；但服务器持有 SECRET 可以反查 —— 这正是「可追溯但不泄露」
 *
 * ── 存储 ──
 * /var/lib/campusmap/patches.jsonl  追加写，一行一条补丁
 * 启动时全量读入内存建索引 (mapVersion,fid,field) → 最新 ts 的那条生效
 *
 * 运行：bun /opt/campusmap/api/server.js   （systemd: campusmap.service）
 */
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const PORT = Number(process.env.PORT || 7870);
const DATA_DIR = process.env.DATA_DIR || "/var/lib/campusmap";
const SECRET = (process.env.CMAP_SECRET || "").trim();
const API_KEY = (process.env.CMAP_API_KEY || "").trim();
const ADMIN_KEY = (process.env.CMAP_ADMIN_KEY || "").trim();
const LOG_FILE = path.join(DATA_DIR, "patches.jsonl");
const MAX_BODY = 256 * 1024;          // 单次请求 256KB 上限
const MAX_PATCHES = 200;              // 单次最多 200 条
const MAX_VALUE = 200;                // 单个值最长 200 字
const RATE_PER_HOUR = 400;            // 每贡献者每小时上限

if (!SECRET || SECRET.length < 16) {
  console.error("[fatal] CMAP_SECRET 未设置或过短");
  process.exit(1);
}

fs.mkdirSync(DATA_DIR, { recursive: true });

/** 索引：(mv,fid,field) → record（ts 最新的那条 = 生效值） */
const index = new Map();
/** 全部已接受记录（按写入顺序）。增量同步要知道「哪些格被 rev>since 的记录碰过」 */
const records = [];
/** 每小时计数：ch → {hour, n} */
const rate = new Map();

/**
 * 数据版本号 `rev`：**每落盘一次自增**。
 * 客户端拿它当「服务器数据变了没有」的判据（不变就 304 / 空增量，零流量）。
 * 持久化到 `rev.txt`，重启不倒退；启动时还会跟已有记录里的最大值取 max。
 */
let rev = 0;
const REV_FILE = path.join(DATA_DIR, "rev.txt");

function loadRev() {
  try {
    const v = Number(String(fs.readFileSync(REV_FILE, "utf8")).trim());
    if (Number.isFinite(v) && v > 0) rev = v;
  } catch { /* 首次启动没有这个文件，正常 */ }
}

function bumpRev() {
  rev += 1;
  try {
    const tmp = REV_FILE + ".tmp";
    fs.writeFileSync(tmp, String(rev));
    fs.renameSync(tmp, REV_FILE);          // 原子替换，避免半截文件
  } catch (e) {
    console.error("[warn] rev.txt 写入失败（本次仍生效，重启可能回退）:", e.message);
  }
  return rev;
}

function keyOf(mv, fid, field) { return `${mv}\u0000${fid}\u0000${field}`; }

function loadAll() {
  loadRev();
  if (!fs.existsSync(LOG_FILE)) return;
  const lines = fs.readFileSync(LOG_FILE, "utf8").split("\n");
  let n = 0;
  let maxRev = 0;
  for (const line of lines) {
    if (!line.trim()) continue;
    try {
      const r = JSON.parse(line);
      if (!Number.isFinite(r.rev)) r.rev = 0;      // 老记录没有 rev → 0（不参与增量）
      // 老记录的「审核标记」借用的是 rev=1；现在 rev 是数据版本，审核独立成 approved
      if (r.approved == null) r.approved = (r.rev === 1 ? 1 : 0);
      records.push(r);
      if (r.rev > maxRev) maxRev = r.rev;
      const k = keyOf(r.mv, r.fid, r.field);
      const prev = index.get(k);
      if (!prev || String(r.ts) >= String(prev.ts)) index.set(k, r);
      n++;
    } catch { /* 跳过坏行，不让一条脏数据搞死启动 */ }
  }
  if (maxRev > rev) rev = maxRev;   // 记录比 rev.txt 新（比如 rev.txt 丢了）→ 以记录为准
  console.log(`[boot] loaded ${n} records, ${index.size} effective keys, rev=${rev}`);
}
loadAll();

/**
 * 每个 `(mv,fid,field)` 取「`ts` 最新的**已审核**记录」。
 *
 * 为什么不直接用 `index`：那里面装的是「`ts` 最新的一条」，**不看审核**。
 * 2026-11-27 复查发现数据集正是从 `index` 聚合的 —— 一条未审核的补丁只要 `ts` 更新，
 * 就会顶掉已审值、直接进客户端，审核等于形同虚设（`/map/api/snapshot` 才是唯一看审核的地方，
 * 而它只有管理端会调）。现在两边统一用这个函数。
 */
function approvedBest() {
  const best = new Map();
  for (const r of records) {
    if (r.approved !== 1) continue;
    const k = keyOf(r.mv, r.fid, r.field);
    const prev = best.get(k);
    if (!prev || String(r.ts) >= String(prev.ts)) best.set(k, r);
  }
  return best;
}

/**
 * 聚合：把补丁流折成「生效数据集」（票 22 口径，2026-11-27 加审核闸门）。
 * - 同一 `(mv,fid,field)` 取**已审核**记录里 `ts` 最新的一条（见 [approvedBest]）
 * - `op=delete` → 该字段视为删除（放在 `deleted` 里让客户端移除）
 * - `addField` 只贡献字段名（值可能是空）
 * - **未审核的记录一律不进聚合结果**，只影响 `rev`（客户端会知道「有变化」，但拿不到值）
 *
 * @param mv       地图版本（空 = 不过滤）
 * @param sinceRev >0 时只输出「被 rev>sinceRev 的已审记录碰过的格」的**当前生效值**
 */
function aggregate(mv, sinceRev) {
  const best = approvedBest();
  let only = null;
  if (sinceRev > 0) {
    only = new Set();
    for (const r of records) {
      if (r.approved === 1 && r.rev > sinceRev && (!mv || r.mv === mv)) only.add(keyOf(r.mv, r.fid, r.field));
    }
  }
  const fields = new Set();
  const features = {};
  const deleted = [];
  const fieldRev = {};
  for (const [k, r] of best) {
    if (mv && r.mv !== mv) continue;
    if (only && !only.has(k)) continue;
    if (r.op === "delete") {
      deleted.push({ featureId: r.fid, field: r.field, rev: r.rev || 0 });
      continue;
    }
    fields.add(r.field);
    (features[r.fid] ||= {})[r.field] = r.value == null ? "" : r.value;
    fieldRev[r.fid] ||= {};
    fieldRev[r.fid][r.field] = r.rev || 0;
  }
  // 字段名汇总按字典序，保证同一份数据每次输出完全一致（ETag 才有意义）
  return {
    fields: [...fields].sort(),
    features,
    deleted,
    fieldRev,
    keys: best.size,          // 已审生效格数（stats 用它，别再拿 index.size 冒充）
  };
}

/**
 * 人工审核记录（票 22 补）：`approved.jsonl` 一行一个 patch `id`。
 *
 * 为什么不直接改 `patches.jsonl`：那是**只追加**的审计日志，改写它等于毁证据。
 * 审核是另一个维度的事实，单独一个文件、同样只追加，启动时并回内存。
 */
const APPROVED_FILE = path.join(DATA_DIR, "approved.jsonl");
const approvedIds = new Set();

function loadApproved() {
  if (!fs.existsSync(APPROVED_FILE)) return;
  let n = 0;
  for (const line of fs.readFileSync(APPROVED_FILE, "utf8").split("\n")) {
    const id = line.trim();
    if (!id) continue;
    approvedIds.add(id);
    n++;
  }
  for (const r of records) if (approvedIds.has(r.id)) r.approved = 1;
  console.log(`[boot] approved ids: ${n}`);
}
loadApproved();


/** 最近一次落盘时间（UTC ISO），客户端只用来显示「数据更新于」 */
function lastUpdatedAt() {
  let max = "";
  for (const r of records) if (String(r.srv_ts || "") > max) max = String(r.srv_ts);
  return max;
}


const enc = (s) => {
  const iv = crypto.randomBytes(12);
  const c = crypto.createCipheriv("aes-256-gcm", crypto.createHash("sha256").update(SECRET).digest(), iv);
  const out = Buffer.concat([c.update(String(s), "utf8"), c.final()]);
  return "v1:" + Buffer.concat([iv, c.getAuthTag(), out]).toString("base64");
};

const hmac = (s) => crypto.createHmac("sha256", SECRET).update(String(s)).digest("hex");

function contributorOf(sid, did) {
  const s = (sid || "").trim();
  const d = (did || "").trim();
  if (!s && !d) return { ch: hmac("anon|" + crypto.randomUUID()), sidEnc: null, hasSid: false };
  return { ch: hmac((s || "-") + "|" + (d || "-")), sidEnc: s ? enc(s) : null, hasSid: !!s };
}

function rateOk(ch) {
  const hour = Math.floor(Date.now() / 3600000);
  const r = rate.get(ch);
  if (!r || r.hour !== hour) { rate.set(ch, { hour, n: 1 }); return true; }
  if (r.n >= RATE_PER_HOUR) return false;
  r.n++;
  return true;
}

// Bun 的 fetch 处理函数必须返回 Response（不是 Node 的 ServerResponse）
function json(code, obj) {
  return new Response(JSON.stringify(obj), {
    status: code,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
    },
  });
}

// 注意：Bun 的 req 是标准 Request（不是 Node 的 IncomingMessage），
// 没有 req.on('data')，必须用 req.text()/arrayBuffer() 读；大小在读完后再判。
async function readBody(req) {
  // 先看声明长度：能提前拒的就在读之前拒，别先把 10 MB 读进内存再判断
  const declared = Number(req.headers.get("content-length") || 0);
  if (declared > MAX_BODY) throw new Error("body too large");
  const text = await req.text();
  if (text.length > MAX_BODY) throw new Error("body too large");
  return text;
}

const MV_RE = /^\d{4}\.\d{1,2}\.\d{1,2}(-\d+)?$/;
const FID_RE = /^f\d{1,6}$/;

function validate(p, mv) {
  const errs = [];
  if (!FID_RE.test(p.featureId || "")) errs.push("bad featureId");
  const field = String(p.field || "").trim();
  if (!field || field.length > 20) errs.push("bad field");
  const op = p.op || "set";
  if (!["set", "delete", "addField"].includes(op)) errs.push("bad op");
  const value = p.value == null ? "" : String(p.value);
  if (value.length > MAX_VALUE) errs.push("value too long");
  if (!MV_RE.test(mv)) errs.push("bad mapVersion");
  return errs;
}

const server = Bun.serve({
  hostname: "127.0.0.1",
  port: PORT,
  async fetch(req) {
    const url = new URL(req.url);
    const p = url.pathname;

    if (p === "/map/api/health") {
      return new Response(JSON.stringify({
        ok: true, keys: index.size, ts: new Date().toISOString(),
      }), { headers: { "Content-Type": "application/json" } });
    }

    // ── 提交补丁 ──
    if (p === "/map/api/patches" && req.method === "POST") {
      if (API_KEY && req.headers.get("x-api-key") !== API_KEY) {
        return new Response(JSON.stringify({ error: "forbidden" }), { status: 403, headers: { "Content-Type": "application/json" } });
      }
      let body;
      try { body = await readBody(req); } catch { return new Response('{"error":"body"}', { status: 413 }); }
      let o;
      try { o = JSON.parse(body); } catch { return new Response('{"error":"json"}', { status: 400 }); }

      const mv = String(o.mapVersion || "");
      const client = o.client || {};
      const patches = Array.isArray(o.patches) ? o.patches.slice(0, MAX_PATCHES) : [];
      const { ch, sidEnc, hasSid } = contributorOf(client.sid, client.did);
      if (!rateOk(ch)) {
        return new Response(JSON.stringify({ error: "rate_limited" }), { status: 429, headers: { "Content-Type": "application/json" } });
      }

      const now = new Date().toISOString();
      const accepted = [];
      const rejected = [];
      for (const pt of patches) {
        const errs = validate(pt, mv);
        if (errs.length) { rejected.push({ featureId: pt.featureId, field: pt.field, reasons: errs }); continue; }
        const rec = {
          id: crypto.createHash("sha1")
            .update([mv, pt.featureId, pt.field, pt.value ?? "", ch, now].join("|"))
            .digest("hex"),
          rev: rev + accepted.length + 1,      // 本次落盘后的全局版本（见下方 bumpRev）
          mv,
          fid: pt.featureId,
          field: String(pt.field).trim(),
          value: pt.value == null ? "" : String(pt.value),
          op: pt.op || "set",
          ch,
          sid_enc: sidEnc,
          has_sid: hasSid,
          did_h: hmac("did|" + (client.did || "-")).slice(0, 16),
          // ── 上传者信息（票 22：主人明确要求收集机型/设备标识）──
          // 注意：这些**是可识别信息**，不再是匿名数据；`sid`（学号）依然不传。
          manufacturer: String(client.manufacturer || "").slice(0, 32),
          model: String(client.model || "").slice(0, 40),
          device: String(client.device || "").slice(0, 40),
          sdk_int: Number.isFinite(Number(client.sdkInt)) ? Number(client.sdkInt) : null,
          release: String(client.release || "").slice(0, 20),
          ssaid: String(client.ssaid || "").slice(0, 64),
          install_id: String(client.installId || "").slice(0, 64),
          app_ver: String(client.appVer || "").slice(0, 20),
          ts: pt.ts || now,
          srv_ts: now,
          approved: 0,                             // 人工审核标记（1=可进 snapshot）。**与数据版本 rev 无关**
        };
        accepted.push(rec);
      }

      if (accepted.length) {
        const fd = fs.openSync(LOG_FILE, "a");
        try {
          fs.writeSync(fd, accepted.map((r) => JSON.stringify(r)).join("\n") + "\n");
        } finally {
          fs.closeSync(fd);
        }
        for (const r of accepted) {
          records.push(r);
          const k = keyOf(r.mv, r.fid, r.field);
          const prev = index.get(k);
          if (!prev || String(r.ts) >= String(prev.ts)) index.set(k, r);
        }
        // rev 在**落盘之后**推进到与记录一致（记录里预留的 rev 就是最终值）
        rev = Math.max(rev, accepted[accepted.length - 1].rev);
        try {
          const tmp = REV_FILE + ".tmp";
          fs.writeFileSync(tmp, String(rev));
          fs.renameSync(tmp, REV_FILE);
        } catch (e) {
          console.error("[warn] rev.txt 写入失败:", e.message);
        }
      }

      console.log(`[patch] mv=${mv} ch=${ch.slice(0, 8)}… acc=${accepted.length} rej=${rejected.length} rev=${rev}`);
      return json(200, { accepted: accepted.length, rejected: rejected.length, reasons: rejected, keys: index.size, rev });
    }

    // ── 共享数据集（票 22）：客户端按 rev 增量拉取 ──
    // 只读、匿名、无需 admin key。返回**聚合后的生效数据**（不是补丁流），
    // 字段与客户端 edits.json 同构，便于直接落地。
    //
    // 2026-11-27：只出**已审核**的格（见 aggregate）。未审核的补丁不再进客户端，
    // 但仍会让 rev 前进 —— 客户端因此会看到「服务器变了」，只是拿不到那些值，
    // 直到有人在 /map/admin/ 里点了通过。
    if (p === "/map/api/dataset" && req.method === "GET") {
      const mv = url.searchParams.get("mapVersion") || "";
      const since = Math.max(0, Number(url.searchParams.get("since") || 0) || 0);
      const etag = `"${mv || "any"}-${rev}"`;

      // 304：rev 没变就一个字节都不回（这就是「只有服务器更新才拉取」）
      const inm = req.headers.get("if-none-match");
      if (inm && inm.split(",").map((s) => s.trim()).includes(etag)) {
        return new Response(null, {
          status: 304,
          headers: { ETag: etag, "Cache-Control": "no-store" },
        });
      }

      const agg = aggregate(mv, since);
      const body = {
        schema: 1,
        mapVersion: mv,
        rev,
        since,                                  // 回显，便于客户端对账
        incremental: since > 0,
        updatedAt: lastUpdatedAt(),
        fields: agg.fields,
        features: agg.features,
        deleted: agg.deleted,
        fieldRev: agg.fieldRev,
      };
      return new Response(JSON.stringify(body), {
        status: 200,
        headers: {
          "Content-Type": "application/json; charset=utf-8",
          "Cache-Control": "no-store",
          ETag: etag,
          "X-Content-Type-Options": "nosniff",
        },
      });
    }

    // ── 排查用的统计（票 22 可选端点）──
    // 2026-11-27：加管理钥匙。这里会列出每个地图版本各有多少补丁、有哪些字段，
    // 等于把「校内设施数据覆盖到什么程度、补到什么粒度」摊在公网 —— 不需要谁都能看。
    if (p === "/map/api/stats" && req.method === "GET") {
      if (!ADMIN_KEY || req.headers.get("x-admin-key") !== ADMIN_KEY) {
        return new Response('{"error":"forbidden"}', { status: 403 });
      }
      const versions = {};
      for (const r of records) versions[r.mv] = (versions[r.mv] || 0) + 1;
      const agg = aggregate("", 0);
      const pending = records.reduce((n, r) => n + (r.approved === 1 ? 0 : 1), 0);
      return json(200, {
        patches: records.length,
        approved: records.length - pending,
        pending,
        effectiveKeys: agg.keys,
        features: Object.keys(agg.features).length,
        fields: agg.fields,
        rev,
        updatedAt: lastUpdatedAt(),
        versions,
      });
    }

    // ── 待审列表（审核管理页用，2026-11-27 加）──
    // 只回**未审核**的记录，字段与补丁同构，管理页直接渲染成表格。
    // 要钥匙：这批数据是「还没对外的事实」，比已审数据更不该公开。
    if (p === "/map/api/pending" && req.method === "GET") {
      if (!ADMIN_KEY || req.headers.get("x-admin-key") !== ADMIN_KEY) {
        return new Response('{"error":"forbidden"}', { status: 403 });
      }
      const limit = Math.min(500, Math.max(1, Number(url.searchParams.get("limit") || 200) || 200));
      const items = records
        .filter((r) => r.approved !== 1)
        .slice(-limit)
        .map((r) => ({
          id: r.id, mv: r.mv, fid: r.fid, field: r.field, op: r.op,
          value: r.value == null ? "" : r.value, ts: r.ts, rev: r.rev || 0,
        }));
      return json(200, { pending: items.length, items, rev });
    }

    // ── 人工审核：把一批 patch id 置为已审核（票 22 补）──
    // 只允许 0→1（要撤回另做端点，绝不让一个接口既能审又能撤）；
    // 幂等：重复 approve 同一批不报错，计数只按**实际变更**算；
    // 找不到的 id **如实报回来**，不静默跳过。
    if (p === "/map/api/approve" && req.method === "POST") {
      if (!ADMIN_KEY || req.headers.get("x-admin-key") !== ADMIN_KEY) {
        return new Response('{"error":"forbidden"}', { status: 403 });
      }
      let body;
      try { body = await readBody(req); } catch { return new Response('{"error":"body"}', { status: 413 }); }
      let o;
      try { o = JSON.parse(body); } catch { return new Response('{"error":"json"}', { status: 400 }); }

      const ids = Array.isArray(o.ids) ? o.ids.map((x) => String(x).trim()).filter(Boolean) : [];
      if (!ids.length) return json(400, { error: "no_ids" });

      const byId = new Map();
      for (const r of records) byId.set(r.id, r);

      const missing = [];
      const fresh = [];
      let already = 0;
      for (const id of ids) {
        const r = byId.get(id);
        if (!r) { missing.push(id); continue; }
        if (r.approved === 1) { already++; continue; }   // 幂等：已审的不再计数
        r.approved = 1;                                   // 只 0→1
        fresh.push(id);
      }

      if (fresh.length) {
        try {
          const fd = fs.openSync(APPROVED_FILE, "a");
          try { fs.writeSync(fd, fresh.join("\n") + "\n"); } finally { fs.closeSync(fd); }
          for (const id of fresh) approvedIds.add(id);
        } catch (e) {
          // 落盘失败要如实说：内存里已置 1，但重启会丢
          console.error("[warn] approved.jsonl 写入失败:", e.message);
          return json(500, { error: "persist_failed", approved: 0, missing, detail: e.message });
        }
      }

      console.log(`[approve] fresh=${fresh.length} already=${already} missing=${missing.length}`);
      return json(200, { approved: fresh.length, missing, alreadyApproved: already });
    }

    // ── 拉审核通过后的合并快照（管理端用）──
    if (p === "/map/api/snapshot" && req.method === "GET") {
      if (!ADMIN_KEY || req.headers.get("x-admin-key") !== ADMIN_KEY) {
        return new Response('{"error":"forbidden"}', { status: 403 });
      }
      const mv = url.searchParams.get("mv") || "";
      // ⚠️ 这里**不能**直接用 `index`：那里面是「ts 最新的记录」，
      // 一条更新但**未审核**的补丁会把已审值顶掉 —— 审核结果就凭空消失了。
      // 正确口径：每个 (mv,fid,field) 取「最新的**已审核**记录」。
      const best = new Map();
      for (const r of records) {
        if (!r.approved) continue;
        if (mv && r.mv !== mv) continue;
        const k = keyOf(r.mv, r.fid, r.field);
        const prev = best.get(k);
        if (!prev || String(r.ts) >= String(prev.ts)) best.set(k, r);
      }
      const fields = new Set();
      const features = {};
      for (const r of best.values()) {
        if (r.op === "delete") continue;          // 已审核的删除 → 该字段不出现在快照里
        fields.add(r.field);
        (features[r.fid] ||= {})[r.field] = r.value == null ? "" : r.value;
      }
      return json(200, { mapVersion: mv, fields: [...fields].sort(), features, approvedKeys: best.size });
    }

    return new Response('{"error":"not_found"}', { status: 404, headers: { "Content-Type": "application/json" } });
  },
});

console.log(`[ready] campusmap api on 127.0.0.1:${server.port}  data=${DATA_DIR}`);
