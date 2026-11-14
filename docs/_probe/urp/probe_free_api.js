// 验证空闲教室的真实 JSON 口：/freeClassroom/today/{节次}?dayplus=0|1|2
const { chromium } = require('C:/Users/28759/AppData/Local/dsh-mcp/playwright-mcp/node_modules/playwright-core');
const fs = require('fs');
const BASE = 'http://10.2.0.47';
const DIR = 'O:/dev/enroute-android/docs/_probe/urp/';

(async () => {
  const b = await chromium.connectOverCDP('http://127.0.0.1:19240');
  const ctx = b.contexts()[0];
  const page = await ctx.newPage();
  await page.goto(BASE + '/student/teachingResources/freeClassroom/index', { waitUntil: 'domcontentloaded', timeout: 25000 });

  const probes = [
    ['campus', '/student/teachingResources/freeClassroom/queryCodeCampusList'],
    ['buildings', '/student/teachingResources/freeClassroom/queryCodeTeaBuildingList?&xqh=001'],
    ['today_s11', '/student/teachingResources/freeClassroom/today/11?dayplus=0'],
    ['today_s3', '/student/teachingResources/freeClassroom/today/3?dayplus=0'],
    ['tomorrow_s3', '/student/teachingResources/freeClassroom/today/3?dayplus=1'],
  ];
  const out = [];
  for (const [name, path] of probes) {
    const r = await page.request.get(BASE + path, { timeout: 20000, headers: { 'X-Requested-With': 'XMLHttpRequest' } });
    const body = await r.text();
    out.push(`### ${name}  ${path}\nHTTP ${r.status()}  len=${body.length}\n${body.slice(0, 700)}\n`);
    fs.writeFileSync(DIR + `free_${name}.json`, body, 'utf8');
    console.log(`[${name}] ${r.status()} ${body.length}`);
  }
  fs.writeFileSync(DIR + 'free_api_probe.txt', out.join('\n'), 'utf8');
  // 结构摘要
  const s11 = JSON.parse(fs.readFileSync(DIR + 'free_today_s11.json', 'utf8'));
  console.log('spareroomObjList len =', s11.spareroomObjList && s11.spareroomObjList.length);
  console.log('curxqm =', s11.curxqm);
  if (s11.spareroomObjList && s11.spareroomObjList[0]) {
    console.log('item keys =', Object.keys(s11.spareroomObjList[0]).join(', '));
    console.log('first item =', JSON.stringify(s11.spareroomObjList[0]).slice(0, 400));
  }
  await page.close();
  await b.close().catch(() => {});
})().catch(e => { console.error('FATAL ' + e.message); process.exit(1); });
