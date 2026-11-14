// 补最后两块：教师课表搜索的请求方式+参数；顺便验证待评教接口。
const { chromium } = require('C:/Users/28759/AppData/Local/dsh-mcp/playwright-mcp/node_modules/playwright-core');
const fs = require('fs');
const BASE = 'http://10.2.0.47';
const DIR = 'O:/dev/enroute-android/docs/_probe/urp/';
const out = [];
const log = (s) => { out.push(s); console.log(s); };

(async () => {
  const b = await chromium.connectOverCDP('http://127.0.0.1:19240');
  const ctx = b.contexts()[0];
  const page = await ctx.newPage();
  const reqs = [];
  page.on('request', (r) => {
    const u = r.url();
    if (!u.includes('10.2.0.47')) return;
    if (/\.(js|css|png|jpg|gif|woff2?)($|\?)/.test(u)) return;
    reqs.push({ method: r.method(), url: u.replace(BASE, ''), ct: r.headers()['content-type'] || '', body: (r.postData() || '').slice(0, 400) });
  });

  // ── 教师课表 ──
  await page.goto(BASE + '/student/teachingResources/teacherCurriculum/index', { waitUntil: 'networkidle', timeout: 25000 });
  await page.waitForTimeout(1200);
  const tHtml = await page.content();
  fs.writeFileSync(DIR + 'teacher_page.html', tHtml, 'utf8');
  const inputs = await page.evaluate(() => {
    const r = [];
    document.querySelectorAll('input,select').forEach((el) => {
      r.push({ tag: el.tagName, name: el.name || '', id: el.id || '', type: el.type || '', ph: el.placeholder || '' });
    });
    return r.filter((x) => x.name || x.id).slice(0, 25);
  });
  log('=== 教师课表页 表单控件 ===');
  inputs.forEach((i) => log('  ' + JSON.stringify(i)));

  // 触发一次搜索：填姓名关键字（用「董」）后点查询按钮
  try {
    const filled = await page.evaluate(() => {
      const el = document.querySelector('input[name="jsm"], input[name="xm"], input#jsm, input#xm');
      if (!el) return null;
      el.value = '董';
      return el.name || el.id;
    });
    log('filled field = ' + filled);
    const clicked = await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('button,a,input[type=button],input[type=submit]'));
      const b2 = btns.find((x) => /查询|搜索|检索/.test(x.innerText || x.value || ''));
      if (b2) { b2.click(); return (b2.innerText || b2.value || '').trim(); }
      return null;
    });
    log('clicked = ' + clicked);
    await page.waitForTimeout(3000);
  } catch (e) { log('search trigger failed: ' + e.message); }

  // ── 待评教 ──
  const ev = await page.request.get(BASE + '/student/teachingAssessment/evaluation/queryAll?pageNum=1&pageSize=30', { timeout: 20000, headers: { 'X-Requested-With': 'XMLHttpRequest' } });
  const evBody = await ev.text();
  fs.writeFileSync(DIR + 'evaluation_queryAll.json', evBody, 'utf8');
  log('\n=== 待评教 queryAll === HTTP ' + ev.status() + ' len=' + evBody.length);
  log(evBody.slice(0, 500));

  log('\n=== 期间请求 ===');
  reqs.forEach((r) => log(`  ${r.method} ${r.url}\n      ct=${r.ct}\n      body=${r.body}`));

  fs.writeFileSync(DIR + 'teacher_probe.txt', out.join('\n'), 'utf8');
  await page.close();
  await b.close().catch(() => {});
})().catch(e => { console.error('FATAL ' + e.message); process.exit(1); });
