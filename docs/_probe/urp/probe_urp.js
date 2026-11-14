// 探查 URP 学生端（10.2.0.47）登录态与菜单树。
// 用法：node probe_urp.js
const { chromium } = require('C:/Users/28759/AppData/Local/dsh-mcp/playwright-mcp/node_modules/playwright-core');
const fs = require('fs');

(async () => {
  const out = [];
  const log = (s) => { out.push(s); console.log(s); };
  const b = await chromium.connectOverCDP('http://127.0.0.1:19240');
  const ctx = b.contexts()[0];
  const pages = ctx.pages();
  const p = pages[pages.length - 1];

  await p.goto('http://10.2.0.47/index', { waitUntil: 'domcontentloaded', timeout: 20000 });
  await p.waitForTimeout(1500);

  log('URL=' + p.url());
  log('TITLE=' + (await p.title()));

  const html = await p.content();
  fs.writeFileSync('O:/dev/enroute-android/docs/_probe/urp/index.html', html, 'utf8');
  log('HTML_LEN=' + html.length);

  // 判断是否还在 CAS
  const isCas = p.url().includes('pass.syau.edu.cn') || html.includes('统一身份认证');
  log('CAS_LOGIN_PAGE=' + isCas);

  if (!isCas) {
    // 抓所有导航链接（URP 的左侧菜单是 <a href="/student/...">）
    const links = await p.evaluate(() => {
      const seen = new Map();
      document.querySelectorAll('a[href]').forEach(a => {
        const href = a.getAttribute('href') || '';
        if (!href || href === '#' || href.startsWith('javascript')) return;
        const text = (a.textContent || '').trim().replace(/\s+/g, ' ');
        if (!seen.has(href)) seen.set(href, text);
      });
      return Array.from(seen.entries());
    });
    log('LINKS=' + links.length);
    links.forEach(([h, t]) => log('  ' + h + '  |  ' + t));
    fs.writeFileSync('O:/dev/enroute-android/docs/_probe/urp/links.json', JSON.stringify(links, null, 2), 'utf8');
  } else {
    log('NEED_LOGIN');
  }

  fs.writeFileSync('O:/dev/enroute-android/docs/_probe/urp/probe1.txt', out.join('\n'), 'utf8');
  await b.close().catch(() => {});
})().catch(e => {
  console.error('FATAL ' + e.message);
  try { fs.writeFileSync('O:/dev/enroute-android/docs/_probe/urp/probe1.txt', 'FATAL ' + e.message + '\n', 'utf8'); } catch (_) {}
  process.exit(1);
});
