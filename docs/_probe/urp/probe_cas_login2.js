// 用无痕上下文看 CAS 真实登录表单（不影响现有登录态）
const { chromium } = require('C:/Users/28759/AppData/Local/dsh-mcp/playwright-mcp/node_modules/playwright-core');
const fs = require('fs');
const out = [];
const log = (s) => { out.push(s); console.log(s); };

(async () => {
  const b = await chromium.connectOverCDP('http://127.0.0.1:19240');
  const ctx = await b.newContext({ ignoreHTTPSErrors: true });
  const page = await ctx.newPage();
  await page.goto('https://pass.syau.edu.cn/tpass/login?service=http%3A%2F%2F10.2.0.47%2Flogin',
    { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForTimeout(2000);
  log('URL=' + page.url());
  log('TITLE=' + (await page.title()));

  const info = await page.evaluate(() => {
    const forms = [];
    document.querySelectorAll('form').forEach((f) => {
      forms.push({
        action: f.getAttribute('action'), method: f.getAttribute('method'),
        fields: Array.from(f.querySelectorAll('input,select,textarea')).map((i) => ({
          type: i.type, name: i.name, id: i.id, ph: i.getAttribute('placeholder') || '',
          visible: !!(i.offsetWidth || i.offsetHeight),
        })),
      });
    });
    const imgs = Array.from(document.querySelectorAll('img')).map((i) => ({
      src: (i.src || '').slice(0, 140), id: i.id, cls: (i.className || '').slice(0, 40),
    }));
    return { forms, imgs, text: (document.body.innerText || '').replace(/\n{2,}/g, '\n').slice(0, 1200) };
  });

  log('=== FORMS ===');
  log(JSON.stringify(info.forms, null, 1));
  log('=== IMGS ===');
  log(JSON.stringify(info.imgs, null, 1));
  log('=== TEXT ===');
  log(info.text);

  // 有没有「验证码」字样
  const hasCaptchaWord = /验证码|校验码|captcha|checkcode|randcode/i.test(info.text)
    || info.forms.some((f) => f.fields.some((x) => /captcha|checkcode|randcode|validate/i.test(x.name + x.id)));
  log('=== 是否含验证码线索: ' + hasCaptchaWord + ' ===');

  fs.writeFileSync('O:/dev/enroute-android/docs/_probe/urp/cas_login_form.txt', out.join('\n'), 'utf8');
  await ctx.close().catch(() => {});
  await b.close().catch(() => {});
})().catch((e) => { console.error('FATAL ' + e.message); process.exit(1); });
