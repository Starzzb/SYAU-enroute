// 空闲教室：抠出「校区号→教学楼号」映射，并实测 POST today 的返回结构。
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

  await page.goto(BASE + '/student/teachingResources/freeClassroom/index', { waitUntil: 'networkidle', timeout: 25000 });
  await page.waitForTimeout(1500);

  // 页面把校区/楼渲染成 #div-<campusNumber> 里的一堆 button（onclick=goDetail('<campus>_<building>','<校区名>')）
  const map = await page.evaluate(() => {
    const rows = [];
    document.querySelectorAll('button[onclick^="goDetail"]').forEach((btn) => {
      const oc = btn.getAttribute('onclick') || '';
      const m = oc.match(/goDetail\("([^"]+)","([^"]*)"\)/);
      if (m) rows.push({ position: m[1], xqm: m[2], buildingName: (btn.textContent || '').trim() });
    });
    // 校区名：从 input id=input-<campusNumber> 的 value / 上级标题抠
    const inputs = [];
    document.querySelectorAll('input[id^="input-"]').forEach((i) => inputs.push({ id: i.id, value: i.value }));
    return { rows, inputs };
  });
  log('=== 校区输入 ===');
  map.inputs.forEach((i) => log('  ' + i.id + ' = ' + i.value));
  log('=== 教学楼按钮（position / 校区 / 楼名）===');
  map.rows.forEach((r) => log('  ' + r.position + '  |  ' + r.xqm + '  |  ' + r.buildingName));
  fs.writeFileSync(DIR + 'freeclassroom_map.json', JSON.stringify(map, null, 2), 'utf8');

  // 选一个楼实测 POST
  const target = map.rows.find((r) => r.buildingName.includes('第二教学楼')) || map.rows[0];
  if (target) {
    log('\n=== 实测 POST today：' + JSON.stringify(target) + ' ===');
    const resp = await page.request.post(BASE + '/student/teachingResources/freeClassroom/today', {
      form: { position: target.position, xqm: target.xqm },
      timeout: 20000,
    });
    const body = await resp.text();
    log('STATUS=' + resp.status() + ' LEN=' + body.length);
    fs.writeFileSync(DIR + 'freeclassroom_today.html', body, 'utf8');
    const text = body.replace(/<script[\s\S]*?<\/script>/g, '').replace(/<[^>]+>/g, ' ').replace(/&nbsp;/g, ' ').replace(/\s+/g, ' ').trim();
    log('TEXT: ' + text.slice(0, 1200));
    // 表格结构
    const trs = (body.match(/<tr[\s\S]*?<\/tr>/g) || []).slice(0, 6);
    log('\n=== 前 6 行原始 HTML ===');
    trs.forEach((t) => log('  ' + t.replace(/\s+/g, ' ').slice(0, 400)));
  }

  fs.writeFileSync(DIR + 'freeclassroom_probe.txt', out.join('\n'), 'utf8');
  await page.close();
  await b.close().catch(() => {});
})().catch(e => { console.error('FATAL ' + e.message); process.exit(1); });
