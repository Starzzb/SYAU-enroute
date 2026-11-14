// 历年学期课表：探「切换学期」的真实参数与返回形态
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
    reqs.push(r.method() + ' ' + u.replace(BASE, '') + (r.postData() ? '\n      BODY=' + r.postData().slice(0, 300) : ''));
  });

  await page.goto(BASE + '/student/courseSelect/calendarSemesterCurriculum/index', { waitUntil: 'networkidle', timeout: 30000 });
  await page.waitForTimeout(1500);
  log('URL=' + page.url());

  // 学期下拉
  const sels = await page.evaluate(() => {
    const r = [];
    document.querySelectorAll('select').forEach((s) => {
      const opts = Array.from(s.options).slice(0, 8).map((o) => o.value + '=' + (o.textContent || '').trim());
      r.push({ name: s.name, id: s.id, opts });
    });
    return r;
  });
  log('\n=== select 控件 ===');
  sels.forEach((s) => log('  ' + JSON.stringify(s)));

  const xnxq = await page.evaluate(() => {
    const s = document.querySelector('select[name="xnxq"], select#xnxq, select[name="executiveEducationPlanNumber"]');
    return s ? { name: s.name || s.id, values: Array.from(s.options).map((o) => o.value).filter(Boolean) } : null;
  });
  log('\n=== 学期候选：' + JSON.stringify(xnxq) + ' ===');

  // 切到上一个学期
  reqs.length = 0;
  const switched = await page.evaluate(() => {
    const s = document.querySelector('select[name="xnxq"], select#xnxq, select[name="executiveEducationPlanNumber"]');
    if (!s) return null;
    const vals = Array.from(s.options).map((o) => o.value).filter(Boolean);
    if (vals.length < 2) return vals[0] || null;
    const pick = vals[1];
    s.value = pick;
    s.dispatchEvent(new Event('change', { bubbles: true }));
    return pick;
  });
  log('\n=== 切到学期: ' + switched + ' ===');
  await page.waitForTimeout(3500);
  log('\n=== 切换后的请求 ===');
  if (reqs.length === 0) log('  (无新请求，可能需要点查询按钮)');
  reqs.forEach((r) => log('  ' + r));

  // 若有查询按钮，点一下
  reqs.length = 0;
  try {
    const btn = page.locator('text=查询').first();
    await btn.click({ timeout: 4000 });
    await page.waitForTimeout(3000);
  } catch (e) { log('(无查询按钮)'); }
  log('\n=== 点查询后的请求 ===');
  reqs.forEach((r) => log('  ' + r));

  const txt = await page.evaluate(() => (document.body.innerText || '').replace(/\n{2,}/g, '\n').slice(0, 700));
  log('\n=== 页面文本 ===');
  log(txt);

  fs.writeFileSync(DIR + 'calendar_probe.txt', out.join('\n'), 'utf8');
  await b.close().catch(() => {});
})().catch((e) => { console.error('FATAL ' + e.message); process.exit(1); });
