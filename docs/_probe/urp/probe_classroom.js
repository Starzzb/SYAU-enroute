// 票 03 研究步骤：摸清 classroomCurriculum/search 的请求参数，以及「某间教室的课表」怎么取
// 用法：node probe_classroom.js   （需要先起 Edge + CDP 19240，登录态 profile）
const { chromium } = require('C:/Users/28759/AppData/Local/dsh-mcp/playwright-mcp/node_modules/playwright-core');
const fs = require('fs');
const BASE = 'http://10.2.0.47';
const DIR = 'O:/dev/enroute-android/docs/_probe/urp/';
const out = [];
const log = (s) => { out.push(s); console.log(s); };
const H = { 'X-Requested-With': 'XMLHttpRequest' };

(async () => {
  const b = await chromium.connectOverCDP('http://127.0.0.1:19240');
  const ctx = b.contexts()[0];
  const page = await ctx.newPage();
  const reqs = [];
  page.on('request', (r) => {
    const u = r.url();
    if (!u.includes('10.2.0.47')) return;
    if (/\.(js|css|png|jpg|gif|woff2?)($|\?)/.test(u)) return;
    reqs.push({
      method: r.method(),
      url: u.replace(BASE, ''),
      ct: (r.headers()['content-type'] || ''),
      body: (r.postData() || '').slice(0, 800),
    });
  });

  const idx = BASE + '/student/teachingResources/classroomCurriculum/index';
  const resp = await page.goto(idx, { waitUntil: 'networkidle', timeout: 30000 });
  await page.waitForTimeout(1500);
  const html = await page.content();
  fs.writeFileSync(DIR + 'classroom_page.html', html, 'utf8');
  const loginish = /loginForm|tpass\/login|pass\.syau\.edu\.cn/.test(page.url() + html);
  log('=== goto index ===  HTTP ' + (resp && resp.status()) + '  url=' + page.url() + '  len=' + html.length);
  log('    登录态=' + (loginish ? '**已失效**（被踢回登录页）' : '有效'));

  if (loginish) {
    log('\n!! 登录态失效，停止参数探测（需要主人在这个 Edge profile 里手动登录一次）');
    fs.writeFileSync(DIR + 'probe_classroom.txt', out.join('\n'), 'utf8');
    await page.close(); await b.close().catch(() => {});
    return;
  }

  // ── 表单控件 ──
  const inputs = await page.evaluate(() => {
    const r = [];
    document.querySelectorAll('input,select').forEach((el) => {
      r.push({ tag: el.tagName, name: el.name || '', id: el.id || '', type: el.type || '', ph: el.placeholder || '' });
    });
    return r.filter((x) => x.name || x.id).slice(0, 40);
  });
  log('\n=== 表单控件 ===');
  inputs.forEach((i) => log('  ' + JSON.stringify(i)));

  // ── 首屏请求（这里就能拿到 search 的**真实请求体**）──
  log('\n=== 首屏期间的请求 ===');
  reqs.forEach((r) => log('  ' + r.method + ' ' + r.url + '\n      ct=' + r.ct + '\n      body=' + r.body));
  fs.writeFileSync(DIR + 'classroom_reqs_initial.json', JSON.stringify(reqs, null, 2), 'utf8');

  const post = (body) =>
    page.request.post(BASE + '/student/teachingResources/classroomCurriculum/search',
      { form: body, headers: H, timeout: 25000 });

  const brief = async (label, r) => {
    const t = await r.text();
    let total = null, first = [];
    try {
      const j = JSON.parse(t);
      const pd = j[0] && j[0].pageData;
      total = pd && pd.pageContext && pd.pageContext.totalCount;
      first = ((pd && pd.records) || []).slice(0, 3).map((x) =>
        [x.campusName, x.teachingBuildingName, x.classroomName, x.classNumberOfSeats].join('/'));
    } catch (e) { /* not json */ }
    log(label + '  HTTP ' + r.status() + ' len=' + t.length + ' totalCount=' + total +
      '  first=' + first.join(' , '));
    return t;
  };

  // ── 变体探测：以真实请求体为基线，逐个字段改 ──
  const initial = reqs.filter((r) => /classroomCurriculum\/search/.test(r.url) && r.method === 'POST').pop();
  const base = {};
  if (initial && initial.body) {
    initial.body.split('&').forEach((kv) => {
      const i = kv.indexOf('=');
      if (i > 0) base[decodeURIComponent(kv.slice(0, i))] = decodeURIComponent(kv.slice(i + 1));
    });
  }
  log('\n=== 基线请求体 ===\n  ' + JSON.stringify(base));

  log('\n=== 变体 ===');
  await brief('A0 原样（页面自己发的）', await post(base));

  const variants = [
    ['A1 classroomName=719', { classroomName: '719' }],
    ['A2 classroomName=719 且 pageSize=200', { classroomName: '719', pageSize: '200' }],
    ['A3 teachingBuildingName=第二教学楼', { teachingBuildingName: '第二教学楼' }],
    ['A4 xqName/xqm=主校', { xqm: '主校' }],
    ['A5 campusName=主校', { campusName: '主校' }],
    ['A6 pageSize=400（能否一次拿全 295 间）', { pageSize: '400' }],
  ];
  for (const [label, patch] of variants) {
    await brief(label, await post(Object.assign({}, base, patch)));
  }

  // ── 行内「课表信息查看」到底打哪条接口 ──
  log('\n=== 点第一行的「课表信息查看」 ===');
  const before = reqs.length;
  let clicked = null;
  try {
    clicked = await page.evaluate(() => {
      // 表格里第一个「课表信息查看」链接/按钮
      const cells = Array.from(document.querySelectorAll('a,button,span,input[type=button]'));
      const el = cells.find((x) => /课表信息查看|课表查看|查看课表/.test((x.innerText || x.value || '').trim()));
      if (!el) return null;
      el.click();
      return (el.innerText || el.value || el.tagName).trim();
    });
    log('  clicked = ' + JSON.stringify(clicked));
    await page.waitForTimeout(3000);
  } catch (e) { log('  点击失败: ' + e.message); }

  log('  —— 点击后新增请求 ——');
  reqs.slice(before).forEach((r) => log('  ' + r.method + ' ' + r.url + '\n      ct=' + r.ct + '\n      body=' + r.body));

  // 弹层里有没有课表文本
  try {
    const modal = await page.evaluate(() => {
      const cands = Array.from(document.querySelectorAll('.layui-layer, .modal, .dialog, [role=dialog], .urp-dialog'));
      return cands.map((el) => (el.innerText || '').slice(0, 1200)).filter((t) => t.trim()).slice(0, 3);
    });
    log('\n=== 弹层文本 ===');
    modal.forEach((m) => log('  ---\n  ' + m.replace(/\s+/g, ' ')));
    fs.writeFileSync(DIR + 'classroom_modal.txt', modal.join('\n=====\n'), 'utf8');
  } catch (e) { log('  取弹层失败: ' + e.message); }

  fs.writeFileSync(DIR + 'probe_classroom.txt', out.join('\n'), 'utf8');
  await page.close();
  await b.close().catch(() => {});
})().catch((e) => { console.error('FATAL ' + e.message); process.exit(1); });
