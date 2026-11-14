// 深挖高价值接口：把完整 JSON 落盘，并找出「空闲教室」等表单页的提交口。
const { chromium } = require('C:/Users/28759/AppData/Local/dsh-mcp/playwright-mcp/node_modules/playwright-core');
const fs = require('fs');
const BASE = 'http://10.2.0.47';
const DIR = 'O:/dev/enroute-android/docs/_probe/urp/';

const w = (name, txt) => fs.writeFileSync(DIR + name, typeof txt === 'string' ? txt : JSON.stringify(txt, null, 2), 'utf8');

(async () => {
  const b = await chromium.connectOverCDP('http://127.0.0.1:19240');
  const ctx = b.contexts()[0];
  const page = await ctx.newPage();
  const captured = [];
  page.on('response', async (resp) => {
    try {
      const url = resp.url();
      if (!url.includes('10.2.0.47')) return;
      const ct = (resp.headers()['content-type'] || '').toLowerCase();
      if (!/json/.test(ct)) return;
      if (/\.js(\?|$)/.test(url)) return;
      const t = (await resp.text()).trim();
      if (!/^[[{]/.test(t)) return;
      captured.push({ url: url.replace(BASE, ''), body: t });
    } catch (_) {}
  });

  // ① 本学期课表（含学分与课程属性）
  await page.goto(BASE + '/student/courseSelect/thisSemesterCurriculum/index', { waitUntil: 'networkidle', timeout: 25000 });
  await page.waitForTimeout(1200);
  // ② 全部及格成绩（学校端已算好的学分/门数统计）
  await page.goto(BASE + '/student/integratedQuery/scoreQuery/allPassingScores/index', { waitUntil: 'networkidle', timeout: 25000 });
  await page.waitForTimeout(1200);
  // ③ 培养方案
  await page.goto(BASE + '/student/integratedQuery/instructionPlanQuery/detail/index', { waitUntil: 'networkidle', timeout: 25000 });
  await page.waitForTimeout(1200);
  // ④ 学籍信息（服务端渲染，抠 HTML）
  const r4 = await page.goto(BASE + '/student/rollManagement/rollInfo/index', { waitUntil: 'networkidle', timeout: 25000 });
  await page.waitForTimeout(800);
  const rollHtml = await page.content();
  w('rollinfo.html', rollHtml);
  const rollText = await page.evaluate(() => (document.body.innerText || '').replace(/\n{3,}/g, '\n\n').trim());
  w('rollinfo.txt', rollText);
  // ⑤ 空闲教室：先看 HTML 里的接口线索
  await page.goto(BASE + '/student/teachingResources/freeClassroom/index', { waitUntil: 'networkidle', timeout: 25000 });
  await page.waitForTimeout(800);
  const freeHtml = await page.content();
  w('freeclassroom.html', freeHtml);
  const freeText = await page.evaluate(() => (document.body.innerText || '').replace(/\n{3,}/g, '\n\n').trim().slice(0, 1500));
  w('freeclassroom.txt', freeText);

  // 落盘所有抓到的 JSON
  let i = 0;
  for (const c of captured) {
    const safe = c.url.replace(/[^A-Za-z0-9]+/g, '_').slice(0, 80);
    w('json_' + String(i++).padStart(2, '0') + '_' + safe + '.json', c.body);
  }

  // 从 HTML 里抠出候选接口路径
  const urls = new Set();
  const re = /["'`](\/(?:student|ajax|main|teachingResources)[A-Za-z0-9_\/\-]*)["'`]/g;
  for (const html of [freeHtml, rollHtml]) {
    let m;
    while ((m = re.exec(html))) urls.add(m[1]);
  }
  w('html_endpoints.json', Array.from(urls).sort());
  console.log('captured=' + captured.length + ' htmlEndpoints=' + urls.size);
  console.log(Array.from(urls).sort().join('\n'));
  await page.close();
  await b.close().catch(() => {});
})().catch(e => { console.error('FATAL ' + e.message); process.exit(1); });
