// 探 CAS 登录页表单：有没有验证码、有哪些必填字段（决定能否静默重登）
const { chromium } = require('C:/Users/28759/AppData/Local/dsh-mcp/playwright-mcp/node_modules/playwright-core');
const fs = require('fs');
const out = [];
const log = (s) => { out.push(s); console.log(s); };

(async () => {
  const b = await chromium.connectOverCDP('http://127.0.0.1:19240');
  const page = (await b.contexts()[0].pages()).slice(-1)[0];
  await page.goto('https://pass.syau.edu.cn/tpass/login?service=http%3A%2F%2F10.2.0.47%2Flogin',
    { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForTimeout(1500);
  log('URL=' + page.url());
  log('TITLE=' + (await page.title()));

  const forms = await page.evaluate(() => {
    const r = [];
    document.querySelectorAll('form').forEach((f) => {
      r.push({
        action: f.getAttribute('action'), method: f.getAttribute('method'),
        fields: Array.from(f.querySelectorAll('input,select,textarea')).map((i) => ({
          tag: i.tagName, type: i.type, name: i.name, id: i.id,
          ph: i.getAttribute('placeholder') || '',
        })),
      });
    });
    return r;
  });
  log('=== FORMS ===');
  log(JSON.stringify(forms, null, 1));

  const imgs = await page.evaluate(() =>
    Array.from(document.querySelectorAll('img')).map((i) => ({ src: (i.src || '').slice(0, 120), id: i.id, cls: i.className })));
  log('=== IMGS（找验证码图）===');
  log(JSON.stringify(imgs, null, 1));

  const txt = await page.evaluate(() => (document.body.innerText || '').replace(/\n{2,}/g, '\n').slice(0, 900));
  log('=== PAGE TEXT ===');
  log(txt);

  fs.writeFileSync('O:/dev/enroute-android/docs/_probe/urp/cas_login_form.txt', out.join('\n'), 'utf8');
  await b.close().catch(() => {});
})().catch((e) => { console.error('FATAL ' + e.message); process.exit(1); });
