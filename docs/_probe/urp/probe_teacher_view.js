// 教师课表「查看」按钮走哪条路由 + 返回什么
const { chromium } = require('C:/Users/28759/AppData/Local/dsh-mcp/playwright-mcp/node_modules/playwright-core');
const fs = require('fs');
const BASE = 'http://10.2.0.47';
const DIR = 'O:/dev/enroute-android/docs/_probe/urp/';

(async () => {
  const b = await chromium.connectOverCDP('http://127.0.0.1:19240');
  const page = (await b.contexts()[0].pages()).slice(-1)[0];
  const reqs = [];
  page.on('request', (r) => {
    const u = r.url();
    if (!u.includes('10.2.0.47')) return;
    if (/\.(js|css|png|jpg|gif|woff2?)($|\?)/.test(u)) return;
    reqs.push(r.method() + ' ' + u.replace(BASE, '') + (r.postData() ? '  BODY=' + r.postData().slice(0, 200) : ''));
  });

  await page.goto(BASE + '/student/teachingResources/teacherCurriculum/index', { waitUntil: 'networkidle', timeout: 25000 });
  await page.waitForTimeout(1000);

  // 找「查看」这个控件的真实形态
  const btn = await page.evaluate(() => {
    const els = Array.from(document.querySelectorAll('a,button,span,td *'));
    const hit = els.filter((e) => (e.textContent || '').trim() === '查看').slice(0, 3).map((e) => ({
      tag: e.tagName,
      href: e.getAttribute('href') || '',
      onclick: e.getAttribute('onclick') || '',
      cls: e.className || '',
    }));
    return hit;
  });
  console.log('== 查看控件 ==');
  btn.forEach((x) => console.log('  ' + JSON.stringify(x)));

  reqs.length = 0;
  // 用真实点击（Playwright 事件），点第一个「查看」
  try {
    const el = page.locator('text=查看').first();
    await el.click({ timeout: 5000 });
    await page.waitForTimeout(3000);
  } catch (e) {
    console.log('click failed: ' + e.message.slice(0, 80));
  }
  console.log('URL=' + page.url());
  console.log('== 点击后的请求 ==');
  reqs.forEach((r) => console.log('  ' + r));

  // 弹窗内容
  const modal = await page.evaluate(() => {
    const m = document.querySelector('.modal.in, .modal.show, [style*="display: block"], .bootbox');
    return m ? m.innerText.replace(/\n{2,}/g, '\n').slice(0, 700) : null;
  });
  console.log('== 弹窗文本 ==');
  console.log(modal || '(无)');

  const html = await page.content();
  fs.writeFileSync(DIR + 'teacher_after_view.html', html, 'utf8');
  await b.close().catch(() => {});
})().catch((e) => { console.error('FATAL ' + e.message); process.exit(1); });
