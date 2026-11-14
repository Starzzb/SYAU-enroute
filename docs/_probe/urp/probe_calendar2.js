// 历年课表：确定「按学期取数据」的调用形式
const { chromium } = require('C:/Users/28759/AppData/Local/dsh-mcp/playwright-mcp/node_modules/playwright-core');
const fs = require('fs');
const BASE = 'http://10.2.0.47';
const DIR = 'O:/dev/enroute-android/docs/_probe/urp/';
const H = { 'X-Requested-With': 'XMLHttpRequest' };
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
    reqs.push(r.method() + ' ' + u.replace(BASE, ''));
  });

  // ① 用 select[name=planCode] 改值并触发 change
  await page.goto(BASE + '/student/courseSelect/calendarSemesterCurriculum/index', { waitUntil: 'networkidle', timeout: 30000 });
  await page.waitForTimeout(1200);
  const before = await page.evaluate(() => (document.body.innerText.match(/20\d\d-20\d\d学年[春秋]/) || [''])[0]);
  log('切换前页面学期标注: ' + before);

  reqs.length = 0;
  const picked = await page.evaluate(() => {
    const s = document.querySelector('select[name="planCode"]');
    if (!s) return null;
    const target = '2024-2025-1-1';
    s.value = target;
    s.dispatchEvent(new Event('change', { bubbles: true }));
    return target;
  });
  await page.waitForTimeout(3500);
  log('切换目标: ' + picked);
  log('URL after change: ' + page.url());
  log('请求: ' + (reqs.length ? reqs.join(' | ') : '(无)'));
  const after = await page.evaluate(() => (document.body.innerText.match(/20\d\d-20\d\d学年[春秋]/) || [''])[0]);
  log('切换后页面学期标注: ' + after);
  const sample = await page.evaluate(() => (document.body.innerText.match(/[\u4e00-\u9fa5]{2,10}_\d\d/) || ['']).slice(0, 4).join(', '));
  log('切换后出现的课程: ' + sample);

  // ② 直接 GET 带 planCode
  for (const code of ['2024-2025-1-1', '2025-2026-1-1']) {
    const r = await page.request.get(BASE + '/student/courseSelect/calendarSemesterCurriculum/index?planCode=' + code, { headers: H, timeout: 25000 });
    const t = await r.text();
    const m = (t.match(/20\d\d-20\d\d学年[春秋][^<"]{0,6}/) || [''])[0];
    const courses = Array.from(new Set((t.match(/[\u4e00-\u9fa5]{2,12}_\d\d/g) || []))).slice(0, 5);
    log(`GET index?planCode=${code} → ${r.status()} len=${t.length} 学期=${m} 课程样例=${courses.join(',')}`);
  }

  // ③ 试 ajaxStudentSchedule/callback 带学期参数
  for (const code of ['2024-2025-1-1', '2025-2026-1-1']) {
    const r = await page.request.get(
      BASE + '/student/courseSelect/thisSemesterCurriculum/ajaxStudentSchedule/callback?xnxq=' + code,
      { headers: H, timeout: 25000 });
    const t = await r.text();
    let keys = '';
    try { keys = Object.keys(JSON.parse(t)).join(','); } catch (e) { keys = '<not json>'; }
    log(`GET ajaxStudentSchedule/callback?xnxq=${code} → ${r.status()} len=${t.length} keys=${keys}`);
    if (code === '2024-2025-1-1') fs.writeFileSync(DIR + 'calendar_callback_2024_2025_1.json', t, 'utf8');
  }

  fs.writeFileSync(DIR + 'calendar_probe2.txt', out.join('\n'), 'utf8');
  await b.close().catch(() => {});
})().catch((e) => { console.error('FATAL ' + e.message); process.exit(1); });
