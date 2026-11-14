// 验证空闲教室接口是否依赖「先 POST 选楼」的会话状态
const { chromium } = require('C:/Users/28759/AppData/Local/dsh-mcp/playwright-mcp/node_modules/playwright-core');
const BASE = 'http://10.2.0.47';
const H = { 'X-Requested-With': 'XMLHttpRequest' };

(async () => {
  const b = await chromium.connectOverCDP('http://127.0.0.1:19240');
  const page = (await b.contexts()[0].pages()).slice(-1)[0];

  const brief = async (label, r) => {
    const t = await r.text();
    let names = [];
    try {
      const j = JSON.parse(t);
      names = (j.spareroomObjList || []).map((x) => x.acmcBuildingName + ':' + (x.claroom || []).length);
    } catch (e) {
      names = ['<not json> ' + t.slice(0, 60)];
    }
    console.log(label + '  ' + r.status() + '  len=' + t.length + '  ->  ' + names.join(', '));
  };

  const pick = (pos, xqm) =>
    page.request.post(BASE + '/student/teachingResources/freeClassroom/today', {
      form: { position: pos, xqm: xqm }, headers: H, timeout: 20000,
    });

  await pick('001_1', '主校');
  await brief('A POST 001_1 then GET /today/11', await page.request.get(BASE + '/student/teachingResources/freeClassroom/today/11?dayplus=0', { headers: H, timeout: 20000 }));

  await pick('001_6', '主校');
  await brief('B POST 001_6 then GET /today/11', await page.request.get(BASE + '/student/teachingResources/freeClassroom/today/11?dayplus=0', { headers: H, timeout: 20000 }));

  await brief('C GET /today/11 with position param', await page.request.get(BASE + '/student/teachingResources/freeClassroom/today/11?dayplus=0&position=001_2', { headers: H, timeout: 20000 }));

  await pick('001_2', '主校');
  await brief('E POST 001_2 then GET /today/11', await page.request.get(BASE + '/student/teachingResources/freeClassroom/today/11?dayplus=0', { headers: H, timeout: 20000 }));

  await pick('001_2', '主校');
  await brief('F POST 001_2 then GET /today/3', await page.request.get(BASE + '/student/teachingResources/freeClassroom/today/3?dayplus=0', { headers: H, timeout: 20000 }));

  await b.close().catch(() => {});
})().catch((e) => { console.error('FATAL ' + e.message); process.exit(1); });
