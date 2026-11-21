# 资讯源正文形态实测报告（feed-render 渲染适配依据）

- 实测时间：2026-11-21 10:27–10:50 (Asia/Shanghai)
- 方式：curl 8.16.0 / Python 3.13，只 GET，无登录、无 Cookie、无表单提交
- 证据目录：`docs/_probe/feed-render/`

## 一、可达性（首轮探测）

| key | 入口 URL | HTTP | 最终 URL | Content-Type | 网络字节 |
|---|---|---|---|---|---|
| ncre | https://ncre.neea.edu.cn/html1/category/1507/872-1.htm | 200 | 同左（0 跳转） | text/html | 24091 |
| cet | https://cet.neea.edu.cn/html1/category/16093/1124-1.htm | 200 | 同左 | text/html | 23721 |
| ntce | https://ntce.neea.edu.cn/html1/category/1507/1148-1.htm | 200 | 同左 | text/html | 24174 |
| neea_gs | https://www.neea.edu.cn/html1/category/1508/151-1.htm | 200 | 同左 | text/html | 25255 |
| cpta | https://www.cpta.com.cn/notice.html | **000** | — | — | — |
| cpta | http://www.cpta.com.cn/notice.html | 200 | 同左 | text/html | 23916 |
| jy | https://jobsyau.jysd.com/ | 200 | 同左 | text/html;charset=utf-8 | 20995（chunked，落盘 124633） |

cpta 的 HTTPS 在两个独立 TLS 栈上都是同一条错误，判定为服务端侧问题而非本机证书问题：

```
curl (schannel) : schannel: next InitializeSecurityContext failed: SEC_E_ILLEGAL_MESSAGE (0x80090326)
python (openssl) : ssl.SSLError: [SSL: SSLV3_ALERT_HANDSHAKE_FAILURE] sslv3 alert handshake failure
curl --tlsv1.2 --tls-max 1.2 : HTTP=000（同错）
curl --tlsv1.3             : SEC_E_UNSUPPORTED_FUNCTION (0x80090302)
```

> 结论：cpta **只有 plain HTTP 可用**，客户端必须允许明文 HTTP，或走自带证书固定的降级策略；Android 9+ 默认 `cleartextTrafficPermitted=false`，必须对该域开白名单。

jy 的 20995 是 `size_download`（gzip/chunked 传输量），`stat -c%s` 落盘 124633 字节，两者都对，勿混用。

## 二、正文形态判定

| key | 可达 | HTTP | Content-Type | 正文形态 | 正文容器选择器 / 文件直链示例 | PDF 页数 / 图片张数 | 陷阱 |
|---|---|---|---|---|---|---|---|
| **ncre** | 是 | 200 | text/html | **A**（纯 HTML 文本，含 `<table>`） | `#Content1 > div.Condiv > span#ReportIDtext` | — / 0（14 篇抽样均无正文图） | UTF-8；列表分页 `872-1.htm`…`872-7.htm`；详情标题内有 `<br>`（来自 title 属性转义 `&#60;br>`）；`div#pdfDown` 恒为空壳 |
| **cet** | 是 | 200 | text/html | **B**（HTML + 内嵌 `/res/Home/` 图片，直接 `src`，非懒加载） | `span#ReportIDtext`；图例 `/res/Home/2608/26080007.png` | — / 14 篇中 7 篇带图，单篇 3 张二维码 180×180 | 图片是微信/支付宝/百度小程序码，180×180 会被 `style` 限死；正文含 `<table>` 考试时间表 |
| **ntce** | 是 | 200 | text/html | **B + C**（多数 HTML+图；省级公告汇总页正文是一张 31 行的省别表格） | `span#ReportIDtext`；PDF 例：`http://www.zhaokao.net/shks/doc/003/000/114/00300011498_c75e8f64.pdf` | PDF 12 页 / 347872 字节；14 篇中 2 篇含 .pdf | 汇总页 31 个省级链接里**只有天津直挂 PDF**，其余 30 个是各省考试院的 HTML 页面（bjeea.cn / zhaokao.net / hebeea.edu.cn …）；全部出站到第三方域、无 Referer/UA 限制 |
| **neea_gs** | 是 | 200 | text/html | **C**（HTML 正文 + DOCX 附件，无 PDF） | `div.conts > span#ReportIDtext`；附件例 `/res/Home/2604/3518c73ddca9b0ddb26edb7bc56e4973.docx?download=…` | — / 14 篇中 3 篇正文带 .docx 附件；正文区 0 张图 | 容器 class 是 `.conts`（不是 Condiv）；无 `#pdfDown`；附件是 Word，App 内无法直读 |
| **cpta** | 是（**仅 http**） | 200 | text/html | **B**（HTML 文本 + 正文大图混排） | `div#p_content`；图例 `/upload/image/202602/f7e4d988-…png` | — / 单篇正文 4 张，1350×1418、1350×1611、1352×1623、1350×1491，各 471–529 KB | 列表混排三类栏目（`/notice/` 8 条、`/examNotice/`、`/performance/`），**不是纯时间倒序**（首条 2026-02-03 之后是 2022 年的历史声明）；分页 `/notice_2.html`…`/notice_5.html`；HTTPS 不可用 |
| **jy** | 是 | 200 | text/html;charset=utf-8 | **F**（正文不在 HTML 里：`section#content8610` 为空壳，正文由内联 JS 用 `Base64.decode(unzip(...))` 注入；**可离线确定性解码**） | `div.aContent`（解码后为 `<p>` 序列，图片 `/attachment/jobsyau/ueditor/images/…`） | — / 单篇 1–2 张，**8192×6144、12.2 MB** | 部分条目（aid 752479/752480 等）直接被抗爬重定向到外站 `https://wap.lnrbxmt.com/news_details.html?...`；正文图带 `referrerpolicy="no-referrer"`；图片 301 跳到 `static.jysd.com` CDN；`robots.txt` = `User-agent:* / Disallow:/` |

## 三、逐源证据（文件 → URL）

### ncre
- `ncre-list.html` ← https://ncre.neea.edu.cn/html1/category/1507/872-1.htm （24091 B，列表 15 条）
- `ncre-detail.html` ← https://ncre.neea.edu.cn/html1/report/2611/14-1.htm （20713 B，《2026年9月…成绩、证书查询开通》，最短骨架样本）
- `ncre-detail2.html` ← https://ncre.neea.edu.cn/html1/report/2606/16-1.htm （66043 B，《…报名工作启动》，231 个 `<p>` + 2 张表格，最长样本）

正文骨架（A 类铁证）：
```html
<div id="Content1"><div class='Condiv'><ul><li>
  <h1><span id='ReportIDname'>…</span></h1>
  <div class='sou'><div class='lefts'>发布时间：<span id='ReportIDIssueTime'>2026-11-10</span></div>
  <span id='ReportIDClassName' data='0'></span></div>
  <div id="pdfDown"></div>
  <span id='ReportIDtext'><p style="text-align:justify">2026年9月全国计算机等级考试（NCRE）成绩…</p></span>
```

### cet
- `cet-list.html` ← https://cet.neea.edu.cn/html1/category/16093/1124-1.htm （23721 B，15 条）
- `cet-detail.html` ← https://cet.neea.edu.cn/html1/report/2609/1-1.htm （26355 B，报名通知，2 张表格）
- `cet-detail2.html` ← https://cet.neea.edu.cn/html1/report/2608/6-1.htm （21857 B，成绩查询，3 张正文图）

正文图片（B 类铁证，`src` 直给，无 `data-src`）：
```html
<p style="text-align:center"><img alt="" src="/res/Home/2608/26080007.png" style="height:180px; width:180px" /></p>
```

### ntce
- `ntce-list.html` ← https://ntce.neea.edu.cn/html1/category/1507/1148-1.htm （24174 B，15 条）
- `ntce-detail.html` ← https://ntce.neea.edu.cn/html1/report/2610/14-1.htm （39790 B，面试报名，72 个 `<p>` + 1 表格）
- `ntce-detail2.html` ← https://ntce.neea.edu.cn/html1/report/2610/12-1.htm （21563 B，成绩发布，3 张正文图）
- `ntce-detail3-provpdf.html` ← https://ntce.neea.edu.cn/html1/report/2606/25-1.htm （39968 B，**省级公告汇总页，正文是 32 行表格 = 1 表头 + 31 个省**，31 个链接中 30 个指向各省考试院 HTML 页、1 个（天津）直挂 PDF）
- `ntce-prov-tj.pdf` ← http://www.zhaokao.net/shks/doc/003/000/114/00300011498_c75e8f64.pdf （347872 B，**12 页**，PDF-1.7，未加密）

C 类铁证（附件/外链在第三方域，非 neea；31 行中仅天津是 PDF，其余是各省 HTML 公告页）：
```html
<a download="天津2026年下半年中小学教师资格考试（笔试）报名公告.pdf"
   href="http://www.zhaokao.net/shks/doc/003/000/114/00300011498_c75e8f64.pdf" target="_blank">天津…</a>
```
直链测试：`curl -H "User-Agent:"` → `HTTP=200, application/pdf, 347872`，**不需要 Referer、不需要 UA**。

### neea_gs
- `neea_gs-list.html` ← https://www.neea.edu.cn/html1/category/1508/151-1.htm （25255 B，15 条）
- `neea_gs-detail.html` ← https://www.neea.edu.cn/html1/report/2609/27-1.htm （34379 B，立项名单公布）
- `neea_gs-detail2.html` ← https://www.neea.edu.cn/html1/report/2608/25-1.htm （35407 B，拟立项公示，正文表格）

⚠️ 与另外三站结构有差异（同 CMS，不同模板分支）：容器是 `div.conts` 而不是 `div.Condiv`，**没有** `#pdfDown` 占位。

C 类铁证（DOCX 附件）：
```html
附件：<a download="1. 国家教育考试科研规划课题管理办法（2026年修订）.docx"
        href="/res/Home/2604/3518c73ddca9b0ddb26edb7bc56e4973.docx?download=…">…</a>
```

### cpta
- `cpta-list.html` ← http://www.cpta.com.cn/notice.html （23916 B，**栏目混合 27 条**：notice 8 / examNotice 7 / performance 12）
- `cpta-list-p2.html` ← http://www.cpta.com.cn/notice_2.html （23728 B，第 2 页，全 `/examNotice/` 条目）
- `cpta-detail.html` ← http://www.cpta.com.cn/notice/2128.html （22890 B，2026 年度考试工作计划，正文 653 个汉字 + 4 张大图）
- `cpta-detail2.html` ← http://www.cpta.com.cn/notice/1146.html （16811 B，关停假冒网站，纯文本无图）
- `cpta-detail-img1.png` … `cpta-detail-img4.png` ← `/upload/image/202602/f7e4d988-…png` 等 4 个（509418 / 529165 / 523452 / 471148 B，尺寸 1350×1418、1350×1611、1352×1623、1350×1491）

容器铁证：
```html
<div class="p2j_content clearfix">
  <h1 id="p_title">人力资源社会保障部办公厅关于…的通知</h1>
  <h5><i id="p_publishtime">2026-02-03 15:20:00</i><i id="p_origin"></i></h5>
  <div class="text"><div class="text_img"></div>
    <div id="p_content"><p style="line-height:27px"><span style="font-family: 宋体;font-size: 14px">…</span></p>
      <p><img src="/upload/image/202602/f7e4d988-…png" width="550" height="680" …/></p>
```
HTML 里给的是 `width=550 height=680` 的**缩显尺寸**，原图 1350×1611 —— 必须按原图渲染，不能用属性值。

### jy
- `jy-list.html` ← https://jobsyau.jysd.com/ （落盘 124633 B，服务端渲染，90 条 `/news/view/aid/` 链接，日期严格倒序）
- `jy-detail.html` ← https://jobsyau.jysd.com/news/view/aid/752839/tag/xwkd （25359 B，2026-10-29 沈阳市人才政策宣讲会）
- `jy-detail2.html` ← https://jobsyau.jysd.com/news/view/aid/747533/tag/xwkd （25225 B，2026-09-08 就业工作业务培训会，2 张正文图）
- `jy-external-link.html` / `jy-antibot.html` ← https://jobsyau.jysd.com/news/view/aid/752480/tag/xwkd 与 …/752479/tag/xwkd （各 ~350 B，**抗爬外壳**）
- `jy-detail-img1.jpg` ← https://static.jysd.com/jobsyau/ueditor/images/20261029/1793256725421078.jpg （12258330 B，**8192×6144**）

F 类铁证（正文为空壳 + JS 注入）：
```html
<div class="aContent"><section id="content8610"
  style="padding: 50px;background-image: url(/static/axui-v1.0.2-20210211/images/loading.gif);…"></section></div>
…
$("#content8610").each(function() {
  $(this).parent().html(Base64.decode(unzip("eJztV11zqjgY/it7n4vaAlov9qLqqUbR0GIb4S5gCoEEEA1Wfv2+wdLTPbvn8szszhxnGGdC3s88z5OXRvDz3f6PX/HbKyn37WOOpw9J/3iLhyS2np1o/jLGxbPki6cTo04eUmdAxEO7sSY2sa7rAX2vwjt7iOfVLdhUYZGPornUoX9bhlQWbPE0xLOljHyMvMXmHFD7u+/spKI7Z+DSMI2oPPJtnevRjazL4yhUY72f3rZsV0mSfXPiRSK87NvZqS54nRVKDne5xrXMgqxpcKuOZVYp15Mau7muayUCS0n3pqjDCPaN5…").substr(91)).substr(15));
});
```
解包链路（实测可完整复现）：base64 → zlib inflate → `.substr(91)` → base64 → `.substr(15)` → UTF-8 正文 HTML。752839 解出 1926 B / 10 个 `<p>` / 436 汉字 / 1 张图。

抗爬重定向外壳（aid 752479、752480 命中）：
```html
<script src="/static/js/base64.min.js"></script><script>window.location=Base64.decode(unzip("eJx…").substr(19)).substr(103);</script>
```
解出目标：`https://wap.lnrbxmt.com/news_details.html?from=iosapp&id=602391&userId=not_login&timestamp=…` —— **正文根本不在 jysd 上，是辽宁日报客户端的外链转载**。

## 四、robots.txt 与反爬

| key | robots.txt | 结论 |
|---|---|---|
| ncre / cet / ntce / www.neea.edu.cn | **404**（返回 17074 B 的 CMS 404 页，不是文本） | 无 robots 约束；也没有 UA 门禁（`-H "User-Agent:"` 全部 200） |
| cpta | **404**（544 B） | 无 robots；无 UA 门禁；HTTPS 握手即失败 |
| jobsyau.jysd.com | **200** → `User-agent:*` / `Disallow:/` | 全站禁止抓取（注意：该域是 `jysd.com`，不属于 `*.syau.edu.cn`，AGENTS.md 里的 VSB robots 口径不覆盖它） |

反爬实测：
- 六个源去掉 UA 后全部 `HTTP 200`，无 403/412，无验证码。
- cpta 正文图**不需要 Referer**（带/不带 Referer 均 200、同字节数 509418）。
- jy 正文图**不需要 Referer**，但会 301 到 `static.jysd.com` CDN，且原图 12.2 MB。
- jy 是唯一有主动反爬行为的源：部分 aid 被 302/JS 重定向到外站。

## 五、对 App 渲染的直接影响

1. **jy 必须由客户端自己解包，WebView 靠不住。** 正文在 `Base64.decode(unzip(...))→substr→base64→substr` 里，且 `robots` 全站 Disallow。要么在数据层用 Kotlin 复刻这条解包链（纯计算、可单测、无需 JS 引擎），要么直接渲染空壳。**不要**交给 WebView —— 它还需要先跑 `base64.min.js`，并会因外链重定向跳到辽宁日报客户端页。
2. **cpta 必须走明文 HTTP + 域名白名单 + 按原图渲染。** HTTPS 在 schannel 与 openssl 两个栈上都是 SSL alert（`SSLV3_ALERT_HANDSHAKE_FAILURE`），只能 http；Android 需为 `www.cpta.com.cn` 配 `cleartextTrafficPermitted`。正文图 HTML 标 `550×680` 而原图 1350×1623，渲染要按原图尺寸，否则等于二次降质。
3. **ntce 的省级公告是「一行一个省 + 一个出站链接」，形态不统一。** 31 个省里 30 个链接到各省考试院 HTML 页（bjeea.cn、hebeea.edu.cn、lnzsks.com…），只有天津直挂 PDF。所以这个源不能统一处理：省别条目应当作**外部链接**渲染（打开外部浏览器），只有命中 `.pdf` 的那一条才需要 PDF 渲染能力。实测该 PDF 直链无需 Referer/UA，12 页 / 348 KB。
4. **四个 neea 站共用一套 CMS，但 neea_gs 的模板分支不同。** 前三个正文容器是 `#Content1 > div.Condiv > span#ReportIDtext`，neea_gs 是 `div.conts > span#ReportIDtext`。选择器不能只写 `div.Condiv`，否则资讯公告全空。四站都**没有**「扫描 PDF 当正文」的情况（14×4 = 56 篇抽样，`#pdfDown` 恒为空、正文区 0 个 iframe/embed/object），可以按纯 HTML 处理。
5. **图片要按「噪声」对待：尺寸、体积、懒加载形态三站三样。** neea 内嵌图是 `src` 直给的 180×180 二维码（体积 79.7 KB，但会被 `style` 限死成小方块）；cpta 是 1350×1623 的正文扫描大图（每张 ~500 KB）；jy 是 8192×6144 / **12.2 MB** 的单张原图。三处都**不是** `data-src` 懒加载。jy 那张必须在下载侧做降采样，否则一次详情页 = 12 MB 流量。
