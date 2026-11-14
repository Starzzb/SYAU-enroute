// 抓教师课表 callback 的真实返回体，作为 App 实现与单测的夹具
const { chromium } = require('C:/Users/28759/AppData/Local/dsh-mcp/playwright-mcp/node_modules/playwright-core');
const fs = require('fs');
const BASE = 'http://10.2.0.47';
const DIR = 'O:/dev/enroute-android/docs/_probe/urp/';
const H = { 'X-Requested-With': 'XMLHttpRequest' };

(async () => {
  const b = await chromium.connectOverCDP('http://127.0.0.1:19240');
  const page = (await b.contexts()[0].pages()).slice(-1)[0];

  const teacherNum = '1995500023';   // 董秀茹（学生自己学院的老师，样本更贴近）
  const teacherName = '董秀茹';
  const planCode = '2026-2027-1-1';
  const planName = '2026-2027学年秋';

  const lo = await page.request.post(BASE + '/student/teachingResources/teacherCurriculum/searchCurriculumInfoLo', {
    form: { teacherNum, teacherName, planCode, planName }, headers: H, timeout: 25000,
  });
  const loBody = await lo.text();
  fs.writeFileSync(DIR + 'teacher_schedule_lo.json', loBody, 'utf8');
  console.log('[Lo] ' + lo.status() + ' len=' + loBody.length + '  ' + loBody.slice(0, 200).replace(/\s+/g, ' '));

  const cb = await page.request.get(
    BASE + '/student/teachingResources/teacherCurriculum/searchCurriculumInfo/callback?planCode=' + planCode + '&teacherNum=' + teacherNum,
    { headers: H, timeout: 25000 });
  const cbBody = await cb.text();
  fs.writeFileSync(DIR + 'teacher_schedule_callback.json', cbBody, 'utf8');
  console.log('\n[callback] ' + cb.status() + ' len=' + cbBody.length);
  console.log(cbBody.slice(0, 1400));

  // 结构摘要
  try {
    const j = JSON.parse(cbBody);
    console.log('\ntype=' + (Array.isArray(j) ? 'array' : typeof j));
    const keys = Array.isArray(j) ? Object.keys(j[0] || {}) : Object.keys(j);
    console.log('top keys: ' + keys.join(', '));
  } catch (e) {
    console.log('\n(非 JSON)');
  }

  await b.close().catch(() => {});
})().catch((e) => { console.error('FATAL ' + e.message); process.exit(1); });
