// 抓「切换历年学期」时 POST 的真实 body
const { chromium } = require('C:/Users/28759/AppData/Local/dsh-mcp/playwright-mcp/node_modules/playwright-core');
const fs = require('fs');
const BASE = 'http://10.2.0.47';
const DIR = 'O:/dev/enroute-android/docs/_probe/urp/';
const out = [];
const log = (s) => { out.push(s); console.log(s); };

(async () => {
  const b = await chromium.connectOverCDP('http://127.0.0.1:19240');
  const page = (await b.contexts()[0].pages()).slice(-1)[0];
  const reqs = [];
  page.on('request', (r) => {
    const u = r.url();
    if (!u.includes('10.2.0.47')) return;
    if (/\.(js|css|png|jpg|gif|woff2?)($|\?)/.test(u)) return;
    reqs.push({ m: r.method(), u: u.replace(BASE, ''), ct: r.headers()['content-type'] || '', body: r.postData() || '' });
  });

  // 抓响应体：拿到切学期后的 schedule JSON
  const bodies = [];
  page.on('response', async (r) => {
    const u = r.url();
    if (!u.includes('ajaxStudentSchedule/callback')) return;
    try {
      const t = await r.text();
      bodies.push({ u: u.replace(BASE, ''), len: t.length, head: t.slice(0, 160) });
      fs.writeFileSync(DIR + 'calendar_schedule_after_switch.json', t, 'utf8');
    } catch (e) {}
  });

  await page.goto(BASE + '/student/courseSelect/calendarSemesterCurriculum/index', { waitUntil: 'networkidle', timeout: 30000 });
  await page.waitForTimeout(1200);

  for (const target of ['2024-2025-1-1', '2023-2024-2-1']) {
    reqs.length = 0;
    const ok = await page.evaluate((t) => {
      const s = document.querySelector('select[name="planCode"]');
      if (!s) return false;
      const has = Array.from(s.options).some((o) => o.value === t);
      if (!has) return false;
      s.value = t;
      s.dispatchEvent(new Event('change', { bubbles: true }));
      return true;
    }, target);
    log(`\n=== 切到 ${target}（存在=${ok}）===`);
    await page.waitForTimeout(3000);
    reqs.forEach((r) => {
      log(`  ${r.m} ${r.u}`);
      if (r.body) log(`      CT=${r.ct}\n      BODY=${r.body.slice(0, 500)}`);
    });
    if (bodies.length) {
      log('  响应: ' + JSON.stringify(bodies[bodies.length - 1]));
    }
  }

  fs.writeFileSync(DIR + 'calendar_probe3.txt', out.join('\n'), 'utf8');
  await b.close().catch(() => {});
})().catch((e) => { console.error('FATAL ' + e.message); process.exit(1); });
