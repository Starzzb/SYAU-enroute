# Phase A: fetch every faculty-column URL discovered from the college homepages,
# and report the list-page rule evidence (info/{col}/{id}.htm, column IDs, paging).
# Read-only. Writes into docs/_probe/faculty/raw2/col_*.html
$ErrorActionPreference = 'Continue'
$ua = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36'
$dir = 'O:\dev\enroute-android\docs\_probe\faculty\raw2'
New-Item -ItemType Directory -Force -Path $dir | Out-Null

# absolute faculty URLs harvested from each site's own homepage HTML (2026-11-13)
$urls = @(
 'https://nxy.syau.edu.cn/szdw1.htm',
 'https://zbxy.syau.edu.cn/xkjs/szll.htm',
 'https://yyxy.syau.edu.cn/xkjs/szdw.htm',
 'https://thxy.syau.edu.cn/szdw/jsxl/zgjzc.htm',
 'https://thxy.syau.edu.cn/szdw/bssds.htm',
 'https://jgxy.syau.edu.cn/szdw/bssds.htm',
 'https://jgxy.syau.edu.cn/szdw/xz_jfry.htm',
 'https://myxy.syau.edu.cn/szdw/js.htm',
 'https://myxy.syau.edu.cn/szdw/fjs.htm',
 'https://myxy.syau.edu.cn/szdw/xwbssds.htm',
 'https://lin.syau.edu.cn/szdw/zyjs.htm',
 'https://gcxy.syau.edu.cn/szdw1/jsml.htm',
 'https://gcxy.syau.edu.cn/szdw1/xxmj.htm',
 'https://xdxy.syau.edu.cn/szdw.htm',
 'https://xdxy.syau.edu.cn/szdw/zgzc.htm',
 'https://slxy.syau.edu.cn/szdw2.htm',
 'https://slxy.syau.edu.cn/szdw2/js.htm',
 'https://spxy.syau.edu.cn/szdw/zysz.htm',
 'https://spxy.syau.edu.cn/szdw/dsfc.htm',
 'https://swxy.syau.edu.cn/szdw/zzjs.htm',
 'https://swxy.syau.edu.cn/szdw/szgk.htm',
 'https://marx.syau.edu.cn/szll/sxddyfzjys.htm',
 'https://lxy.syau.edu.cn/szdw1.htm',
 'https://lxy.syau.edu.cn/szdw1/js.htm',
 'https://tyb.syau.edu.cn/bmgk/jsdw.htm',
 'https://wyb.syau.edu.cn/szdw.htm',
 'https://wyb.syau.edu.cn/szdw/szdw.htm'
)

$rows = @()
foreach ($u in $urls) {
  $name = ($u -replace '^https://', '') -replace '[^\w\.\-]', '_'
  $rec = [pscustomobject]@{ url=$u; status=''; len=0; arts=0; cols=''; paging=''; title=''; kind='' }
  try {
    $r = Invoke-WebRequest -Uri $u -TimeoutSec 20 -UseBasicParsing -UserAgent $ua
    $html = $r.Content
    [IO.File]::WriteAllText((Join-Path $dir ("col_" + $name + ".html")), $html, [Text.Encoding]::UTF8)
    $rec.status = [string]$r.StatusCode
    $rec.len = $html.Length
    $m = [regex]::Match($html, '<title>([^<]*)</title>')
    if ($m.Success) { $rec.title = $m.Groups[1].Value.Trim() }
    $arts = [regex]::Matches($html, 'info/(\d{3,5})/(\d+)\.htm')
    $rec.arts = $arts.Count
    $rec.cols = (($arts | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique) -join '/')
    $pg = @()
    if ($html -match [regex]::Unescape('\u5171\s*\d+\s*\u6761')) { $pg += 'Count=n' }
    if ($html -match [regex]::Unescape('\d+\s*/\s*\d+\s*\u9875')) { $pg += 'N/Mpage' }
    if ($html -match '/2\.htm') { $pg += 'subpage_2.htm' }
    if ($html -match 'GOPAGE|gotopagebut|fanye') { $pg += 'jsGoto' }
    $rec.paging = ($pg -join '+')
    if ($arts.Count -gt 0) { $rec.kind = 'article-list' }
    elseif ($html -match 'vsb_content') { $rec.kind = 'richtext-only' }
    else { $rec.kind = 'unknown' }
  } catch {
    $rec.status = 'ERR ' + $_.Exception.Message.Split("`n")[0]
  }
  $rows += $rec
  Start-Sleep -Milliseconds 250
}
$rows | ForEach-Object { "{0,-58} {1,-6} len={2,-7} arts={3,-4} cols={4,-12} {5,-12} {6}" -f $_.url, $_.status, $_.len, $_.arts, $_.cols, $_.kind, $_.paging }
