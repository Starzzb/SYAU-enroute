// 逐个打开 URP 学生端全部入口，抓出每页背后的 JSON 数据接口。
// 只做 GET 浏览，不点任何按钮，不提交任何表单。
const { chromium } = require('C:/Users/28759/AppData/Local/dsh-mcp/playwright-mcp/node_modules/playwright-core');
const fs = require('fs');

const BASE = 'http://10.2.0.47';
const PAGES = [
  ['学籍信息', '/student/rollManagement/rollInfo/index'],
  ['我的学籍卡片', '/student/rollManagement/myRollCard/index'],
  ['电子注册', '/student/rollManagement/electronicRegistration/index'],
  ['奖惩信息', '/student/rollManagement/rewardsAndPenalties/index'],
  ['学籍异动', '/student/rollManagement/rollChanges/index'],
  ['本学期课表', '/student/courseSelect/thisSemesterCurriculum/index'],
  ['本学期周课表', '/student/personalSenate/giveLessonInfo/thisSemesterSchedule/weekLySchedule'],
  ['历年学期课表', '/student/courseSelect/calendarSemesterCurriculum/index'],
  ['选课公告', '/student/courseSelect/courseSelectNotice/index'],
  ['选课结果', '/student/courseSelect/courseSelectResult/index'],
  ['选课失败信息', '/student/courseSelect/courseSelectFailed/index'],
  ['计费学分确认', '/student/courseSelect/creditCheck/index'],
  ['空闲教室查询', '/student/teachingResources/freeClassroom/index'],
  ['教室使用状况', '/student/teachingResources/classroomUseStatus/index'],
  ['教室课表', '/student/teachingResources/classroomCurriculum/index'],
  ['教师课表', '/student/teachingResources/teacherCurriculum/index'],
  ['班级课表', '/student/teachingResources/classCurriculum/index'],
  ['课程课表', '/student/teachingResources/courseCurriculum/index'],
  ['成绩查询(全学期)', '/student/integratedQuery/scoreQuery/allTermScores/index'],
  ['本学期成绩', '/student/integratedQuery/scoreQuery/thisTermScores/index'],
  ['不及格成绩', '/student/integratedQuery/scoreQuery/unpassedScores/index'],
  ['全部及格成绩', '/student/integratedQuery/scoreQuery/allPassingScores/index'],
  ['课程属性成绩', '/student/integratedQuery/scoreQuery/coursePropertyScores/index'],
  ['方案成绩', '/student/integratedQuery/scoreQuery/schemeScores/index'],
  ['方案完成情况', '/student/integratedQuery/planCompletion/index'],
  ['指导性教学计划', '/student/integratedQuery/instructionPlanQuery/detail/index'],
  ['本学期课程安排', '/student/integratedQuery/course/courseSchdule/index'],
  ['课程基本信息', '/student/integratedQuery/course/courseBasicInformation/basicInf'],
  ['班级订书详单', '/student/integratedQuery/teachingMaterial/bookList/index'],
  ['考试安排', '/student/examinationManagement/examPlan/index'],
  ['考试报名', '/student/examinationManagement/examSignUp/index'],
  ['教学评估', '/student/teachingEvaluation/newEvaluation/index'],
  ['我的实习周报', '/student/internship/mysxzb/index'],
  ['实习过程管理', '/student/internship/sxgcgl/index'],
  ['学生证明打印', '/student/studentCertificatePrinting/index'],
];

(async () => {
  const b = await chromium.connectOverCDP('http://127.0.0.1:19240');
  const ctx = b.contexts()[0];
  const page = await ctx.newPage();
  const results = [];

  for (const [label, path] of PAGES) {
    const jsons = [];
    const onResp = async (resp) => {
      try {
        const url = resp.url();
        const ct = (resp.headers()['content-type'] || '').toLowerCase();
        if (!/json|javascript/.test(ct)) return;
        if (!url.includes('10.2.0.47')) return;
        if (/\.js(\?|$)/.test(url)) return;              // 跳过静态脚本
        let body = '';
        try { body = await resp.text(); } catch (_) { return; }
        if (!body || body.length < 3) return;
        const t = body.trim();
        if (!/^[[{]/.test(t)) return;                     // 只留 JSON
        jsons.push({ url: url.replace(BASE, ''), status: resp.status(), len: body.length, sample: t.slice(0, 260) });
      } catch (_) {}
    };
    page.on('response', onResp);

    let title = '', text = '', err = '';
    try {
      const r = await page.goto(BASE + path, { waitUntil: 'networkidle', timeout: 25000 });
      await page.waitForTimeout(900);
      title = await page.title();
      text = await page.evaluate(() => (document.body.innerText || '').replace(/\n{3,}/g, '\n\n').trim().slice(0, 500));
      if (r && r.status() >= 400) err = 'HTTP ' + r.status();
    } catch (e) {
      err = e.message.slice(0, 120);
    }
    page.off('response', onResp);

    results.push({ label, path, title, err, jsons, text });
    console.log(`[${results.length}/${PAGES.length}] ${label}  json=${jsons.length} ${err}`);
  }

  fs.writeFileSync('O:/dev/enroute-android/docs/_probe/urp/pages.json', JSON.stringify(results, null, 2), 'utf8');
  await page.close();
  await b.close().catch(() => {});
})().catch(e => { console.error('FATAL ' + e.message); process.exit(1); });
