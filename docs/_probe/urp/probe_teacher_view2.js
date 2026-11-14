// 探「查教师 → 查看 → 该教师课表」的真实路由，以及页面上「查看」控件的真实形态
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
    reqs.push(r.method() + ' ' + u.replace(BASE, '') + (r.postData() ? '\n      BODY=' + r.postData().slice(0, 400) : ''));
  });

  await page.goto(BASE + '/student/teachingResources/teacherCurriculum/index', { waitUntil: 'networkidle', timeout: 30000 });
  await page.waitForTimeout(1500);
  log('URL=' + page.url());

  // 1) 首行 HTML，看「查看」到底是什么
  const rowHtml = await page.evaluate(() => {
    const tr = document.querySelector('table tbody tr');
    return tr ? tr.innerHTML.replace(/\s+/g, ' ').slice(0, 1200) : '(no row)';
  });
  log('\n=== 首行 HTML ===');
  log(rowHtml);

  // 2) 所有 onclick / href 里带查看语义的
  const handlers = await page.evaluate(() => {
    const out = [];
    document.querySelectorAll('a,button,i,span').forEach((e) => {
      const oc = e.getAttribute('onclick') || '';
      const href = e.getAttribute('href') || '';
      const txt = (e.textContent || '').trim();
      if (/课表|查看|detail|Detail|schedule|Schedule|week|Week/.test(oc + ' ' + href + ' ' + txt)) {
        out.push({ tag: e.tagName, txt: txt.slice(0, 20), href: href.slice(0, 120), onclick: oc.slice(0, 200) });
      }
    });
    return out.slice(0, 12);
  });
  log('\n=== 可疑控件 ===');
  handlers.forEach((h) => log('  ' + JSON.stringify(h)));

  // 3) 直接调页面里的函数（若存在）
  reqs.length = 0;
  const called = await page.evaluate(() => {
    const names = ['toDetail', 'showDetail', 'viewDetail', 'goDetail', 'toSchedule', 'showSchedule', 'toKb', 'toKB'];
    const hit = names.find((n) => typeof window[n] === 'function');
    return hit || null;
  });
  log('\n=== 全局函数探测: ' + (called || '未命中') + ' ===');

  // 4) 点击第一行的「查看」（用真实鼠标事件）
  try {
    const cell = page.locator('table tbody tr').first().locator('text=查看').first();
    await cell.click({ timeout: 6000 });
    await page.waitForTimeout(3000);
  } catch (e) {
    log('click 查看 失败: ' + e.message.slice(0, 100));
  }
  log('\n=== 点击后请求 ===');
  if (reqs.length === 0) log('  (无新请求)');
  reqs.forEach((r) => log('  ' + r));

  // 5) 弹层/新页内容
  log('\nURL_AFTER=' + page.url());
  const modal = await page.evaluate(() => {
    const m = document.querySelector('.modal.in, .modal.show, .bootbox, [role=dialog]');
    return m ? m.innerText.replace(/\n{2,}/g, '\n').slice(0, 800) : null;
  });
  log('\n=== 弹层文本 ===');
  log(modal || '(无弹层)');

  // 6) 若页面内容变了（新窗口/跳转），抓 URL 与首屏文本
  const pages = b.contexts()[0].pages();
  log('\n=== 该浏览器所有标签 ===');
  for (const p of pages) log('  ' + p.url());

  fs.writeFileSync(DIR + 'teacher_view_probe.txt', out.join('\n'), 'utf8');
  fs.writeFileSync(DIR + 'teacher_view_page.html', await page.content(), 'utf8');
  await b.close().catch(() => {});
})().catch((e) => { console.error('FATAL ' + e.message); process.exit(1); });
