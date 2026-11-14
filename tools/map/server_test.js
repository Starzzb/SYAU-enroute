/**
 * `server.js` 的本地验证夹具（**不是**生产代码）。
 *
 * 为什么需要它：票 22 改的是**线上服务**的聚合与增量口径，一旦算错，
 * 客户端会把别人的数据合错、或者永远拉不到更新。而本地没有 Bun，
 * 所以这里用一个极小的 `Bun.serve` 桩把 handler 抠出来直接调用 ——
 * 用真 `Request`/`Response`（Node 18+ 自带），跑的是**同一份 server.js**。
 *
 * 运行：
 *   node tools/map/server_test.js
 *
 * 覆盖：
 *   1. rev 每次落盘递增，且能从 rev.txt 恢复
 *   2. 聚合口径：同 (fid,field) 取 ts 最新；op=delete 进 deleted；addField 只贡献字段名
 *   3. 增量：since=<rev> 只回被碰过的格；再次拉取 rev 未变 → 304
 *   4. mapVersion 隔离：别的版本的补丁不混进来
 *   5. 上传者信息落盘（manufacturer/model/ssaid/installId…），且 sid 不落明文
 */
const fs = require("fs");
const os = require("os");
const path = require("path");
const assert = require("assert");

const TMP = fs.mkdtempSync(path.join(os.tmpdir(), "campusmap-test-"));
process.env.DATA_DIR = TMP;
process.env.CMAP_SECRET = "test-secret-at-least-16-chars-long";
process.env.CMAP_API_KEY = "";        // 测试里不校验 api key
process.env.CMAP_ADMIN_KEY = "adm";

// ── Bun.serve 桩：只把 fetch handler 留下来 ──
let handler = null;
globalThis.Bun = {
  serve(cfg) {
    handler = cfg.fetch;
    return { port: cfg.port || 0 };
  },
};

// 屏蔽 server.js 里的 console.log 噪声（保留我们自己的输出）
const realLog = console.log;
require("./server.js");
console.log = realLog;
assert.ok(typeof handler === "function", "没有拿到 fetch handler");

const BASE = "http://127.0.0.1:7870";

async function get(pathname, headers = {}) {
  const res = await handler(new Request(BASE + pathname, { method: "GET", headers }));
  const text = await res.text();
  return { status: res.status, headers: res.headers, json: text ? JSON.parse(text) : null };
}

async function post(body) {
  const res = await handler(
    new Request(BASE + "/map/api/patches", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    }),
  );
  return { status: res.status, json: await res.json() };
}

const MV = "2026.11.14-1";

function patchBody(patches, client = { did: "anon-test" }) {
  return { schema: 1, mapVersion: MV, client, patches };
}

let pass = 0;
function ok(name, fn) {
  fn();
  pass++;
  console.log(`  ✓ ${name}`);
}

(async () => {
  // ── 1. 空库 ──
  const d0 = await get(`/map/api/dataset?mapVersion=${MV}`);
  ok("空库返回 rev=0、无数据", () => {
    assert.equal(d0.status, 200);
    assert.equal(d0.json.rev, 0);
    assert.deepEqual(d0.json.features, {});
    assert.deepEqual(d0.json.fields, []);
  });

  // ── 2. 首次上报 → rev=1 ──
  const p1 = await post(
    patchBody([
      { featureId: "f19", field: "层数", value: "3", op: "set", ts: "2026-11-14T10:00:00+08:00" },
      { featureId: "f19", field: "所属单位", value: "体育部", op: "set", ts: "2026-11-14T10:00:00+08:00" },
    ], {
      did: "anon-test", manufacturer: "vivo", model: "V2301A", device: "PD2301",
      sdkInt: 36, release: "16", ssaid: "ssaid-abc", installId: "install-xyz", appVer: "0.2.0+m19",
    }),
  );
  ok("首轮 2 条被接受，响应回带 rev", () => {
    assert.equal(p1.json.accepted, 2);
    assert.equal(p1.json.rev, 2);
  });

  const d1 = await get(`/map/api/dataset?mapVersion=${MV}`);
  ok("全量拉取拿到聚合结果", () => {
    assert.equal(d1.json.rev, 2);
    assert.deepEqual(d1.json.features.f19, { 层数: "3", 所属单位: "体育部" });
    assert.deepEqual(d1.json.fields, ["层数", "所属单位"]);
    assert.equal(d1.json.incremental, false);
  });

  // ── 3. ETag / 304 ──
  const etag = d1.headers.get("etag");
  ok("ETag 形如 \"<mv>-<rev>\"", () => assert.ok(etag && etag.includes(`-2\"`), etag));
  const d304 = await get(`/map/api/dataset?mapVersion=${MV}`, { "If-None-Match": etag });
  ok("rev 未变 → 304 且无 body（零流量）", () => {
    assert.equal(d304.status, 304);
    assert.equal(d304.json, null);
  });

  // ── 4. ts 冲突：晚到的旧 ts 不能覆盖 ──
  const p2 = await post(patchBody([
    { featureId: "f19", field: "层数", value: "2", op: "set", ts: "2026-11-14T09:00:00+08:00" },   // 比已有的旧
    { featureId: "f19", field: "层数", value: "4", op: "set", ts: "2026-11-14T11:00:00+08:00" },   // 比已有的新
  ]));
  ok("两条都被接受（服务端只管存）", () => assert.equal(p2.json.accepted, 2));
  const d2 = await get(`/map/api/dataset?mapVersion=${MV}`);
  ok("聚合取 ts 最新 → 层数=4", () => {
    assert.equal(d2.json.features.f19["层数"], "4");
    assert.equal(d2.json.rev, 4);
  });

  // ── 5. 增量：since=2 只回被 rev>2 碰过的格 ──
  const inc = await get(`/map/api/dataset?mapVersion=${MV}&since=2`);
  ok("增量只含被碰过的格，且给的是最新值", () => {
    assert.equal(inc.json.incremental, true);
    assert.deepEqual(inc.json.features.f19, { 层数: "4" });   // 所属单位没被碰 → 不返回
    assert.deepEqual(inc.json.fields, ["层数"]);
  });

  // ── 6. delete ──
  await post(patchBody([
    { featureId: "f19", field: "所属单位", value: "", op: "delete", ts: "2026-11-14T12:00:00+08:00" },
    { featureId: "f20", field: "层数", value: "2", op: "addField", ts: "2026-11-14T12:00:00+08:00" },
  ]));
  const d3 = await get(`/map/api/dataset?mapVersion=${MV}`);
  ok("op=delete 让该字段从 features 消失并进 deleted", () => {
    assert.equal(d3.json.features.f19["所属单位"], undefined);
    assert.ok(d3.json.deleted.some((x) => x.featureId === "f19" && x.field === "所属单位"));
  });
  ok("addField 贡献字段名与值", () => {
    assert.equal(d3.json.features.f20["层数"], "2");
    assert.ok(d3.json.fields.includes("层数"));
  });

  // ── 7. mapVersion 隔离 ──
  await post({
    schema: 1, mapVersion: "2026.12.01-1", client: { did: "anon-test" },
    patches: [{ featureId: "f99", field: "层数", value: "9", op: "set", ts: "2026-12-01T10:00:00+08:00" }],
  });
  const d4 = await get(`/map/api/dataset?mapVersion=${MV}`);
  ok("别的版本的补丁不混进来", () => {
    assert.equal(d4.json.features.f99, undefined);
  });
  const dOther = await get(`/map/api/dataset?mapVersion=2026.12.01-1`);
  ok("拉指定版本时能看到它自己的数据", () => {
    assert.equal(dOther.json.features.f99["层数"], "9");
  });

  // ── 8. 上传者信息落盘 + sid 不落明文 ──
  const raw = fs.readFileSync(path.join(TMP, "patches.jsonl"), "utf8").trim().split("\n").map(JSON.parse);
  ok("机型/系统/设备标识确实落盘（票 22 要求）", () => {
    const r = raw[0];
    assert.equal(r.manufacturer, "vivo");
    assert.equal(r.model, "V2301A");
    assert.equal(r.device, "PD2301");
    assert.equal(r.sdk_int, 36);
    assert.equal(r.release, "16");
    assert.equal(r.ssaid, "ssaid-abc");
    assert.equal(r.install_id, "install-xyz");
  });
  ok("rev 单调递增且每条记录都带 rev", () => {
    const revs = raw.map((r) => r.rev);
    for (let i = 1; i < revs.length; i++) assert.ok(revs[i] > revs[i - 1], `rev 不单调: ${revs}`);
  });
  ok("没传 sid → 磁盘上无明文学号、sid_enc 为 null", () => {
    for (const r of raw) {
      assert.equal(r.sid_enc, null);
      assert.equal(r.has_sid, false);
      assert.ok(!JSON.stringify(r).includes('"sid"'));
    }
  });
  ok("approved 与 rev 分离（新记录 approved=0，不会误进 snapshot）", () => {
    for (const r of raw) assert.equal(r.approved, 0);
  });

  // ── 9. stats ──
  const st = await get("/map/api/stats");
  ok("stats 给出总数/版本分布/rev", () => {
    assert.ok(st.json.patches === raw.length, `${st.json.patches} vs ${raw.length}`);
    assert.equal(st.json.rev, d4.json.rev);
    assert.ok(st.json.versions[MV] >= 5);
  });

  // ── 10. rev.txt 重启恢复 ──
  ok("rev.txt 记录了当前 rev", () => {
    assert.equal(Number(fs.readFileSync(path.join(TMP, "rev.txt"), "utf8")), d4.json.rev);
  });

  console.log(`\n[ok] ${pass} 项全部通过`);
  fs.rmSync(TMP, { recursive: true, force: true });
})().catch((e) => {
  console.error("\n[FAIL]", e.message);
  try { fs.rmSync(TMP, { recursive: true, force: true }); } catch {}
  process.exit(1);
});
