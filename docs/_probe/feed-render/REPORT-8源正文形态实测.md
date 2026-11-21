# 资讯页信息源正文形态实测（feed-render probe）

实测时间：2026-11-21 10:27–11:05 (UTC+8)  
执行环境：校内网可达（cxcy.syau.edu.cn 直连成功），只读 GET，未登录、未提交任何表单。  
原始响应目录：`docs/_probe/feed-render/`

## 一、总表

| key | 可达 | HTTP | Content-Type | 正文形态 | 正文容器选择器 / 文件直链示例 | PDF 页数 / 图片张数 | 陷阱 |
|---|---|---|---|---|---|---|---|
| cxcycomp 竞赛平台 | 是（校内网） | 200 | text/html; charset=UTF-8 | **A** 纯 HTML 文本 | `div.content`（4 个 `<p>` + 裸文本混排，正文纯文字 4499 字） | PDF 0 / 正文图 0 | 详情 URL 带 `?id=`（仓库 robots 口径禁 `?`）；列表分页是 jquery.pagination 的 JS callback（`?page=N`），页面上没有任何页码 `<a>`；页内 `$.post('getall')` 只加载报名成员名单，不是正文；`robots.txt` 返回 403 |
| cxcydc 大创项目 | 是（校内网） | 200 | text/html; charset=UTF-8 | **A** 纯 HTML 文本 | `div.content`（`<p class="p">` / `<p class="MsoNormal">` 共 42 段，4392 字） | PDF 0 / 正文图 0 | 同上 `?id=` 与 JS 分页；正文被 Word 拆成大量行内 `<span>`，逐 span 拼接才能成句 |
| cxcyart 竞赛成果 | 是（校内网） | 200 | text/html; charset=UTF-8 | **E** 正文只有图片（附 **C**） | `div.content_detail` 内 4 张 `<img src>`：`/static/uploads/res/editor/202512/20251208172845_51496.jpg`；附件在 `div.upload_list` → `../comp/articledownload?aid=163&id=150` | 图 4 张：2845×1280 / 2890×1280 / 2845×1280 / 2890×1280，383–529 KB，合计 **1.84 MB**；另挂 docx 748 KB | 正文 0 字，纯图片墙；附件走 `articledownload?aid=&id=`（Content-Disposition 带 RFC5987 文件名）；另一条 upload_list 挂的是 3 张 jpg（1600×1200，合计 1.33 MB） |
| tzb 挑战杯 | 是 | 200 | text/html; charset=utf-8 | **B** HTML 正文 + 内嵌图 | `<!-- 正文区域 -->` 之后那个无 id/class 语义名的 `div`（Tailwind 原子类容器）；图在 `/media/ckeditor_uploads/genrui/2026/11/05/1____.jpg` | 正文图 **13 张**（1280×720，合计 **2.16 MB**，最大边 1280）；PDF 0 | 正文容器没有语义 id/class，只能靠 HTML 注释锚点定位；列表是 3 个板块混排（动态/事件/媒体）共 56 条链接、无分页；`robots.txt` 对 GPTBot/ClaudeBot/anthropic-ai/PerplexityBot 等 UA 全部 `Disallow: /`（普通浏览器 UA 实测 200） |
| lnjyt 辽宁省教育厅 | 是 | 200 | text/html（**无 charset 声明**） | **C** HTML 正文 + PDF 附件 | 正文 `div.zoom`（69 个 `<p>`，5207 字）；附件 `/jyt/gk/zxtz/2026102110290659632/2026102110254421584.pdf` | PDF **11 页 / 222 KB**；正文图 0 | 附件列表由 `document.write` + JS 数组 `attachHuilanMediaArray.push({src,title,...})` 生成，静态 HTML 里**没有 `<a href>`**，必须从 JS 字面量取 `src`；`robots.txt` 是软 404（200 + 404 页）；列表翻页是 `f797aa36-2.shtml`（`index_1.shtml` 实测 404） |
| cahe 高教学会 | 是 | 200 | text/html; charset=UTF-8 | **C** HTML 正文 + PDF/docx 附件 | 正文 `div.branch_text_box`（3363–3719 字）；附件 `https://cmsfiles.zhongkefu.com.cn/caheedu/gdjy/file/20260928/1790586060381787.pdf` | PDF **105 页 / 2.03 MB**；另一条 **74 页 / 1.09 MB**；另一条只挂 docx | 附件托管在**跨域 CDN** `cmsfiles.zhongkefu.com.cn`，部分又在 `www.cahe.edu.cn/upload/`，两种前缀都要认；列表 `?page=1 / ?page=2 / ?pageNo=2` 返回字节级相同的 48 条，服务端翻页**实际无效**；`robots.txt` 404 |
| jingsai 我爱竞赛网 | 是 | 200 | text/html; charset=**gbk** | **B** HTML 正文 + 内嵌图 | 正文 `td#article_content`（Discuz X3.2 风格）；图 `http://cdn1.52jingsai.com/portal/202611/04/104655xlxd533olleox23l.jpg`（独立 CDN，`src` 直出） | 正文图 1 张（900×300，78 KB）；另一条 2 张 png；PDF 0 | **GB18030 强制**（UTF-8 在 byte 254 失败，声明 `charset=gbk`）；详情 URL 受 `<base href="https://www.52jingsai.com/">` 支配——列表写 `article-24180-1.html`，真地址是根目录 `/article-24180-1.html`，套 `/bisai/xkjn/bangdan/` 前缀必 404；页尾 `article-N-2.html` 是推荐位不是正文续页（第 2、3 页正文长度相同） |
| fwwb 服务外包大赛 | 是 | 200 | text/html;charset=utf-8 | **E** 正文是长截图（+ **C** PDF 附件） | 正文 `div.show-content`：1 张 `/attached/image/20261120/20261120204717_669.jpeg`；附件 `/attached/file/20261120/20261120204900_532.pdf`（链接文字「下载PDF文件」） | 图 1 张 **873×3780**（706 KB 长图，最大边 3780）；PDF **3 页 / 250 KB**；另一条 **1 页 / 201 KB** | 详情页可见正文只有 7 个字符；`<noscript>` 里塞了 `<meta http-equiv="refresh" content="0; URL=/forbidden">`（禁用 JS 就被踢到 /forbidden）；`/news/index` 与 `?page=2` 返回同一份 9 条、无分页；`robots.txt` 404 |

## 二、可达性原始记录（步骤 1）

| key | 请求 URL | 状态 | 最终 URL | Content-Type | 字节 |
|---|---|---|---|---|---|
| cxcycomp | http://cxcy.syau.edu.cn/comp/front/comp/list | 200 | 同 | text/html; charset=UTF-8 | 31348 |
| cxcydc | http://cxcy.syau.edu.cn/dcplan/front/dcplan/list | 200 | 同 | text/html; charset=UTF-8 | 24936 |
| cxcyart | http://cxcy.syau.edu.cn/comp/front/comp/articlelist?cid=12 | 200 | 同 | text/html; charset=UTF-8 | 21050 |
| tzb | https://www.tiaozhanbei.net/tzb | 200 | 同 | text/html; charset=utf-8 | 61268 |
| lnjyt | https://jyt.ln.gov.cn/jyt/gk/zxtz/index.shtml | 200 | 同 | text/html（无 charset） | 40627 |
| cahe | https://www.cahe.edu.cn/site/term/373.html | 200 | 同 | text/html; charset=UTF-8 | 94322 |
| jingsai | https://www.52jingsai.com/bisai/xkjn/bangdan/ | 200 | 同 | text/html; charset=gbk | 60263 |
| fwwb | https://www.fwwb.org.cn/ | 200 | 同 | text/html;charset=utf-8 | 43296 |

服务器指纹：cxcy 三站 = `Apache/2.4.47 (Win64) OpenSSL/1.1.1k PHP/8.5.12`；tzb / cahe / fwwb = nginx；lnjyt = panyun（惠联 EasySite CMS）。
8 个源全部 200，无跳转、无 403/412、无验证码。cxcy 三站当前机器在校内网直连成功，不是「校外不可达」。

## 三、证据文件 → URL 对照（步骤 2）

| 文件 | 对应 URL |
|---|---|
| `cxcycomp-list.html` (31348 B) | http://cxcy.syau.edu.cn/comp/front/comp/list |
| `cxcycomp-detail.html` (36240 B) | http://cxcy.syau.edu.cn/comp/front/comp/info?id=1542 |
| `cxcycomp-detail2.html` (26605 B) | http://cxcy.syau.edu.cn/comp/front/comp/info?id=1583 |
| `cxcydc-list.html` (24936 B) | http://cxcy.syau.edu.cn/dcplan/front/dcplan/list |
| `cxcydc-detail.html` (39938 B) | http://cxcy.syau.edu.cn/dcplan/front/dcplan/info?id=1417 |
| `cxcydc-detail2.html` (41991 B) | http://cxcy.syau.edu.cn/dcplan/front/dcplan/info?id=1081 |
| `cxcyart-list.html` (21050 B) | http://cxcy.syau.edu.cn/comp/front/comp/articlelist?cid=12 |
| `cxcyart-detail.html` (18768 B) | http://cxcy.syau.edu.cn/comp/front/comp/articledetail?id=163 |
| `cxcyart-detail2.html` (19046 B) | http://cxcy.syau.edu.cn/comp/front/comp/articledetail?id=154 |
| `cxcyart-img1.jpg` (444457 B) | http://cxcy.syau.edu.cn/static/uploads/res/editor/202512/20251208172845_51496.jpg |
| `tzb-list.html` (61268 B) | https://www.tiaozhanbei.net/tzb |
| `tzb-detail.html` (28511 B) | https://www.tiaozhanbei.net/article/15864/ |
| `tzb-detail2.html` (38805 B) | https://www.tiaozhanbei.net/article/15858/ |
| `tzb-img1.jpg` (251189 B) | https://www.tiaozhanbei.net/media/ckeditor_uploads/genrui/2026/11/05/1____.jpg |
| `lnjyt-list.html` (40627 B) | https://jyt.ln.gov.cn/jyt/gk/zxtz/index.shtml |
| `lnjyt-detail.html` (59502 B) | https://jyt.ln.gov.cn/jyt/gk/zxtz/2026102110290659632/index.shtml |
| `lnjyt-detail2.html` (39960 B) | https://jyt.ln.gov.cn/jyt/gk/zxtz/2026092211315648073/index.shtml |
| `lnjyt.pdf` (222003 B, 11 页) | https://jyt.ln.gov.cn/jyt/gk/zxtz/2026102110290659632/2026102110254421584.pdf |
| `cahe-list.html` (94322 B) | https://www.cahe.edu.cn/site/term/373.html |
| `cahe-detail.html` (25747 B) | https://www.cahe.edu.cn/site/content/19967.html |
| `cahe-detail2.html` (26722 B) | https://www.cahe.edu.cn/site/content/19836.html |
| `cahe.pdf` (2034818 B, 105 页) | https://cmsfiles.zhongkefu.com.cn/caheedu/gdjy/file/20260928/1790586060381787.pdf |
| `cahe2.pdf` (1085194 B, 74 页) | https://www.cahe.edu.cn/upload/gdjy/file/20250930/1759215429196221.pdf |
| `jingsai-list.html` (60263 B, GB18030) | https://www.52jingsai.com/bisai/xkjn/bangdan/ |
| `jingsai-detail.html` (50873 B, GB18030) | https://www.52jingsai.com/article-24180-1.html |
| `jingsai-detail2.html` (42506 B, GB18030) | https://www.52jingsai.com/article-24182-1.html |
| `jingsai-img1.jpg` (79793 B) | http://cdn1.52jingsai.com/portal/202611/04/104655xlxd533olleox23l.jpg |
| `fwwb-list.html` (43296 B) | https://www.fwwb.org.cn/ |
| `fwwb-detail.html` (4527 B) | https://www.fwwb.org.cn/news/show/666 |
| `fwwb-detail2.html` (4452 B) | https://www.fwwb.org.cn/news/show/665 |
| `fwwb.pdf` (250231 B, 3 页) | https://www.fwwb.org.cn/attached/file/20261120/20261120204900_532.pdf |
| `fwwb2.pdf` (200749 B, 1 页) | https://www.fwwb.org.cn/attached/file/20261120/20261120204253_476.pdf |
| `fwwb-img1.jpeg` (706692 B, 873×3780) | https://www.fwwb.org.cn/attached/image/20261120/20261120204717_669.jpeg |

robots.txt 与探测脚本同目录：`www.tiaozhanbei.net.robots.txt`(200, 785 B)、`www.52jingsai.com.robots.txt`(200, 694 B)、`jyt.ln.gov.cn.robots.txt`(200 但内容是 404 页, 9693 B)、`www.cahe.edu.cn.robots.txt`(404)、`www.fwwb.org.cn.robots.txt`(404)、`cxcy.syau.edu.cn.robots.txt`(403)。

探测脚本（可直接复跑）：`_probe_list.py`（可达性）、`_probe_detail.py`（详情+robots）、`_probe_links.py` / `_probe_links2.py`（列表链接）、`_shape.py` / `_bodies2.py`（正文容器与资产）、`_assets.py` / `_measure.py` / `_measure3.py`（PDF 页数与图片尺寸）、`_verify.py`（jingsai 分页与 cxcy `?page=`）、`_last.py`（Referer/UA 与编码字节证据）。中间转储：`_links.txt`、`_links2.txt`、`_shape.txt`、`_bodies.json`、`_assets.json`、`_deep.txt`、`_precise.txt`、`_attach.txt`、`_pag*.txt`、`_verify.txt`、`_last.txt`。

## 四、PDF 与图片类测量（步骤 4）

| 文件 | 页数 | 字节 | 无 Referer | 无 UA | 备注 |
|---|---|---|---|---|---|
| lnjyt.pdf | 11 | 222003 | 200 | 200 | %PDF-1.7，application/pdf，同域直链 |
| fwwb.pdf | 3 | 250231 | 200 | 200 | Content-Type 带 `;charset=utf-8` 后缀，别拿全串做等值比较 |
| fwwb2.pdf | 1 | 200749 | 200 | 200 | 同上 |
| cahe.pdf | 105 | 2034818 | 200 | 200 | 跨域 CDN，%PDF-1.5 |
| cahe2.pdf | 74 | 1085194 | 200 | 200 | 本域 `/upload/` |

图片：三处正文图**全部没有懒加载**——都是 `src` 直出，`data-src` / `data-original` 计数为 0，不需要任何 JS 触发就能取到。
- cxcyart：4 张 2845×1280 / 2890×1280 / 2845×1280 / 2890×1280，合计 1.84 MB，最大边 **2890**；另一条附件挂 3 张 1600×1200，合计 1.33 MB。
- tzb：详情 15864 正文 13 张 1280×720，合计 **2.16 MB**，最大边 1280；详情 15858 正文 12 张。
- jingsai：详情 24180 正文 1 张 900×300（78 KB）；24182 正文 2 张 png。图在独立 CDN `cdn1.52jingsai.com`，HTTP 与 HTTPS 都 200。
- fwwb：1 张 **873×3780**（706 KB），最大边 3780，属于整页截图。
- cxcycomp / cxcydc / lnjyt / cahe：正文区**无配图**（lnjyt 与 cahe 命中的 1–2 张是站点 logo 与页脚图）。

## 五、robots 与反爬（步骤 5）

- 用 `Python-urllib/3.13` UA 与**完全不带 UA** 重放 6 个非校内列表页，6 个全部 200，没有 403 / 412 / 验证码，也没有 JS 挑战页。
- 唯一被拦的是 `cxcy.syau.edu.cn/robots.txt` → **403**（文件本身禁读，不是站点反爬）。
- `www.tiaozhanbei.net/robots.txt` 明显针对 AI 抓取：GPTBot / ChatGPT-User / ClaudeBot / anthropic-ai / PerplexityBot / Bytespider / CCBot / Applebot-Extended 全部 `Disallow: /`；`User-agent: *` 只禁 `/project/`、`/project/list/` 等人才项目库路径，`/article/` 与 `/tzb` 未被禁。
- `52jingsai` 是 Discuz 默认 robots：禁 `/data/`、`/source/`、`/member.php` 等；`/data/attachment/` 与 `/bisai/` 是 Allow。
- `jyt.ln.gov.cn/robots.txt` 是软 404（HTTP 200 + 9693 B 的 404 页面），等于没有规则；`cahe` 与 `fwwb` 的 robots.txt 直接 404。
- 附带发现：`fwwb` 详情页 `<noscript>` 里是 `<meta http-equiv="refresh" content="0; URL=/forbidden">`，浏览器关 JS 会被踢走——抓取器必须带正常 UA 且不做 JS 依赖。

## 六、对 App 渲染的直接影响

1. **PDF 必须是一等公民，而且有 105 页的先例。** lnjyt / cahe / fwwb 三个源的正文主体就是 PDF 附件（cahe 单条 105 页 / 2.03 MB，fwwb 3 页 / 250 KB），四份 PDF 全部无 Referer、无 UA 可直链，可以直接交给系统 PDF 渲染器或 `PdfRenderer`；不要往 WebView 塞 `<iframe src=...pdf>`，Android WebView 不内置 PDF 插件，会白屏。渲染前先读 `Content-Length` 决定是否给加载占位，105 页那份 2 MB 在弱网下要 5–15 秒。

2. **图片类要按「长截图」设计，不是「配图」。** cxcyart 一条正文 4 张 2845×1280、合计 1.84 MB；fwwb 正文是一张 873×3780 的整页截图；tzb 正文 13 张合计 2.16 MB。Coil 侧要按**宽度**约束（`size(WidthSize)` 或 `fillMaxWidth()` + `ContentScale.FillWidth`），并且**必须剥掉内联 `style="width:600px;height:338px"`**——tzb 每张图都写了这个，照着渲染会在手机上窄成一列。fwwb 那张长图则相反：等比缩到屏宽后高度约 1500dp，需要可滚动的图片容器，不能塞进固定高度卡片。

3. **fwwb 与 lnjyt 的附件链接不能只靠 DOM 解析。** lnjyt 的 `<a>` 是 `document.write` 从 `attachHuilanMediaArray.push({src:...})` 生成的，静态 HTML 里根本没有 `<a href>`，必须在源码里匹配 `attachHuilanMediaArray\.push\(\{[^}]*'src':'([^']+)'` 并同时取 `title`；fwwb 的 PDF 链接文字是「下载PDF文件」而无扩展名，只能靠 `.pdf` 后缀或 `/attached/file/` 前缀识别。cxcyart 更绕：附件走 `articledownload?aid=&id=`，响应头 `Content-Disposition` 才有真实文件名（含 RFC5987 `filename*`）。

4. **jingsai 必须显式 GB18030 解码，且 URL 要按 `<base>` 解析；否则乱码 + 404 双杀。** 该站 UTF-8 解码在 byte 254 就抛错，声明 `charset=gbk`。省事做法：OkHttp 取 `bytes()` 后 `String(bytes, Charset.forName("GB18030"))`，绝不能用 `response.body.string()`（它默认按响应头猜，遇到只有 `charset=gbk` 会猜错）。URL 侧列表 href 是相对站点根（受 `<base href="https://www.52jingsai.com/">` 支配），当前实现若按「相对当前页目录」拼会得到 `/bisai/xkjn/bangdan/article-24180-1.html` → 实测 404。

5. **cxcy 三站不是 SPA、不需要执行 JS，但分页与详情都落在查询参数上，和现有 robots 口径冲突。** 详情正文是服务端渲染好的 `div.content`（4499 / 4392 字纯文本，可直接抽 `<p>`；页内唯一的 `$.post('getall')` 只负责加载报名成员名单分页，不影响正文）。真要翻列表才需要 `?page=N`——页面上没有页码 `<a>`，页码由 jquery.pagination 的 callback 拼出 `../comp/list?page=N`。这与仓库 AGENTS.md 的 robots 口径「URL 含 `?` 直接报错」冲突：要么给这三个前缀记一条白名单例外，要么 cxcy 只能停在列表首页（每页 7 条）+ 手动构造的详情 `?id=`。

6. **cahe 的服务端翻页实际是坏的，只能拿最新 48 条。** `?page=1` / `?page=2` / `?pageNo=2` 三份响应字节级相同（都 94322 B、同样的 48 条 id），`/site/term/373_2.html` 是 404，翻页逻辑只存在于 JS 里重拼 `pageLink` 而后端不认。所以 cahe 源在 App 里就是「最新 48 条」的固定快照，不要设计「加载更多」。lnjyt 相反：`f797aa36-N.shtml` 真能翻（共 18 页，每页 15 条，`-2.shtml` 实测 200 且日期明显更旧）。

补充证据：cxcy 详情正文可抽取性实测——`div.content` 内 `<p>` 4 个、纯文本 5687 字符（含页脚噪声前），`<script>` 存在但只在容器末尾，抽取时按 `div.content` 收敛后正则剥 script 即可；cxcydc 的 Word 粘贴正文有 42–49 个 `<p>`、4382–4481 字，行内 `<span>` 逐字切分（如 `我`+`校`+`全日制`），必须按段拼接后再做空格规范化。
