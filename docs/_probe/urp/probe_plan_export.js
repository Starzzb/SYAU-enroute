// 找「培养方案」的可下载/可打印出口：导出按钮、print/export/download 接口、附件链接。
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
  const allReq = [];
  page.on('request', (r) => {
    const u = r.url().replace(BASE, '');
    if (/export|print|download|excel|xls|pdf|doc|word|attachment|file/i.test(u)) allReq.push(r.method() + ' ' + u);
  });

  // ── 指导性教学计划 ──
  await page.goto(BASE + '/student/integratedQuery/instructionPlanQuery/detail/index', { waitUntil: 'networkidle', timeout: 25000 });
  await page.waitForTimeout(1500);
  const html1 = await page.content();
  fs.writeFileSync(DIR + 'plan_page.html', html1, 'utf8');

  const btns = await page.evaluate(() => {
    const r = [];
    document.querySelectorAll('button,a,input[type=button],input[type=submit],[onclick]').forEach(el => {
      const t = (el.innerText || el.value || '').trim().replace(/\s+/g, ' ');
      const oc = el.getAttribute('onclick') || '';
      const href = el.getAttribute('href') || '';
      if (!t && !oc) return;
      if (/导出|打印|下载|pdf|excel|word|保存|生成|培养方案|教学计划/i.test(t + ' ' + oc + ' ' + href)) {
        r.push({ tag: el.tagName, text: t.slice(0, 40), onclick: oc.slice(0, 160), href: href.slice(0, 120) });
      }
    });
    return r;
  });
  log('== 指导性教学计划页 相关控件 ==');
  btns.forEach(x => log('  ' + JSON.stringify(x)));

  // 页面里出现的可疑路径
  const cands = new Set();
  const re = /["'`](\/[A-Za-z0-9_\/\-]*(?:export|print|download|Export|Print|Download)[A-Za-z0-9_\/\-]*)["'`]/g;
  let m; while ((m = re.exec(html1))) cands.add(m[1]);
  log('== HTML 里的导出/打印路径 ==');
  cands.forEach(c => log('  ' + c));

  // ── 学生证明打印（一般这里能出 PDF/打印件）──
  await page.goto(BASE + '/student/studentCertificatePrinting/index', { waitUntil: 'networkidle', timeout: 25000 });
  await page.waitForTimeout(1500);
  const html2 = await page.content();
  fs.writeFileSync(DIR + 'certificate_page.html', html2, 'utf8');
  const txt2 = await page.evaluate(() => (document.body.innerText || '').replace(/\n{3,}/g, '\n\n').trim());
  fs.writeFileSync(DIR + 'certificate.txt', txt2, 'utf8');
  log('\n== 学生证明打印页文本（前 900 字）==');
  log(txt2.slice(0, 900));
  const cands2 = new Set();
  const re2 = /["'`](\/[A-Za-z0-9_\/\-]*(?:print|Print|export|Export|download|Download|certificate|Certificate)[A-Za-z0-9_\/\-]*)["'`]/g;
  while ((m = re2.exec(html2))) cands2.add(m[1]);
  log('== 证明打印页里的路径 ==');
  cands2.forEach(c => log('  ' + c));

  log('\n== 两个页面期间命中的 export/print/download 请求 ==');
  Array.from(new Set(allReq)).forEach(u => log('  ' + u));

  fs.writeFileSync(DIR + 'plan_export_probe.txt', out.join('\n'), 'utf8');
  await page.close();
  await b.close().catch(() => {});
})().catch(e => { console.error('FATAL ' + e.message); process.exit(1); });
