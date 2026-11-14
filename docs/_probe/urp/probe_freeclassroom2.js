// 真正拿到空闲教室数据：模拟点「第二教学楼」，捕获其后的 JSON 请求。
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
  const seen = [];
  page.on('response', async (r) => {
    try {
      const u = r.url();
      if (!u.includes('10.2.0.47')) return;
      if (/\.(js|css|png|jpg|gif|woff2?)($|\?)/.test(u)) return;
      const ct = (r.headers()['content-type'] || '').toLowerCase();
      let body = '';
      try { body = await r.text(); } catch (_) { return; }
      seen.push({ url: u.replace(BASE, ''), ct, len: body.length, head: body.trim().slice(0, 300) });
    } catch (_) {}
  });

  await page.goto(BASE + '/student/teachingResources/freeClassroom/index', { waitUntil: 'networkidle', timeout: 25000 });
  await page.waitForTimeout(1200);

  // 直接调页面自带的 goDetail，等同于点「第二教学楼」
  await page.evaluate(() => { if (window.goDetail) window.goDetail('001_2', '主校'); });
  await page.waitForTimeout(4000);
  log('URL_AFTER=' + page.url());

  const body = await page.content();
  fs.writeFileSync(DIR + 'freeclassroom_today_rendered.html', body, 'utf8');
  const text = await page.evaluate(() => (document.body.innerText || '').replace(/\n{3,}/g, '\n\n').trim());
  fs.writeFileSync(DIR + 'freeclassroom_today.txt', text, 'utf8');
  log('=== 渲染后文本（前 900 字）===');
  log(text.slice(0, 900));

  log('\n=== 期间的所有非静态响应 ===');
  seen.forEach((s) => log(`  [${s.len}] ${s.url}\n      ${s.head.replace(/\s+/g, ' ').slice(0, 200)}`));

  fs.writeFileSync(DIR + 'freeclassroom_probe2.txt', out.join('\n'), 'utf8');
  await page.close();
  await b.close().catch(() => {});
})().catch(e => { console.error('FATAL ' + e.message); process.exit(1); });
