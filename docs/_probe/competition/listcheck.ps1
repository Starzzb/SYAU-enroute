param(
  [Parameter(Mandatory=$true)][string[]]$Urls,
  [string]$DumpDir = 'O:\dev\enroute-android\docs\_probe\competition\raw',
  [switch]$ShowItems,
  [int]$Top = 4
)
# 列表页体检 v3：家族无关。以“文章型 href”为锚，向两侧窗口找日期。
$ProgressPreference = 'SilentlyContinue'
try { [System.Text.Encoding]::RegisterProvider([System.Text.CodePagesEncodingProvider]::Instance) } catch {}
$UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36'

# 常见 CMS 文章 URL 模式
$ART = '(info/\d+/\d+\.htm|content\.jsp\?[^"'']*wbnewsid=|/site/content/\d+\.html|article-\d+-\d+\.html|/article/\d+|/art/\d{4}/\d+/\d+\.html|/t\d{8}_\d+\.html|/\d{6}/t\d+_\d+\.html|/post/\d+\.html|/detail[^"'']*[?&]id=\d+|/a/\d{4}\d{2}\d{2}/\d+\.html|/20\d{2}/\d{4}/\d+\.html|/show-\d+\.html|/(?:node|section)/[0-9a-f]{16,}\.html|/\d{12,20}/index\.shtml|\.php\?[^"'']*id=\d+)'

function Get-Page([string]$pu) {
  $tmp = [System.IO.Path]::GetTempFileName()
  try {
    $r = Invoke-WebRequest -Uri $pu -UseBasicParsing -TimeoutSec 30 -MaximumRedirection 5 `
      -Headers @{ 'User-Agent'=$UA; 'Accept'='text/html,application/xhtml+xml,*/*'; 'Accept-Language'='zh-CN,zh;q=0.9' } `
      -OutFile $tmp -PassThru
    $pgBytes = [System.IO.File]::ReadAllBytes($tmp)
    $pgHead  = [System.Text.Encoding]::ASCII.GetString($pgBytes, 0, [Math]::Min(2048, $pgBytes.Length))
    $pgCs = 'utf-8'
    $pgH = @($r.Headers['Content-Type']) -join ''
    if ($pgH -match 'charset=([\w-]+)') { $pgCs = $Matches[1] }
    elseif ($pgHead -match 'charset="?([\w-]+)') { $pgCs = $Matches[1] }
    if ($pgCs -match '^(gb2312|gbk|gb18030)$') { $pgCs = 'gb18030' }
    try { $pgEnc = [System.Text.Encoding]::GetEncoding($pgCs) } catch { $pgEnc = [System.Text.Encoding]::UTF8 }
    return [pscustomobject]@{ Status=$r.StatusCode; Bytes=$pgBytes.Length; Charset=$pgCs
      Text=$pgEnc.GetString($pgBytes); Final=($r.BaseResponse.RequestMessage.RequestUri.AbsoluteUri) }
  } finally { Remove-Item $tmp -ErrorAction SilentlyContinue }
}

function Abs([string]$base, [string]$href) {
  if (-not $href) { return '' }
  if ($href -match '^(?i)https?://') { return $href }
  try { return ([uri]::new([uri]$base, $href)).AbsoluteUri } catch { return $href }
}

function Get-Date3([string]$w) {
  $m = [regex]::Match($w,'(20\d{2})\s*[-/\.年]\s*(\d{1,2})\s*[-/\.月]\s*(\d{1,2})')
  if ($m.Success) { return ("{0}-{1:D2}-{2:D2}" -f $m.Groups[1].Value,[int]$m.Groups[2].Value,[int]$m.Groups[3].Value) }
  $mb = [regex]::Match($w,'(?is)<b>\s*(\d{1,2})\s*</b>')
  $ms = [regex]::Match($w,'(?is)<span>\s*(20\d{2})\s*[-/\.]\s*(\d{1,2})\s*</span>')
  if ($mb.Success -and $ms.Success) { return ("{0}-{1:D2}-{2:D2}" -f $ms.Groups[1].Value,[int]$ms.Groups[2].Value,[int]$mb.Groups[1].Value) }
  $m3 = [regex]::Match($w,'(20\d{2})\s*[-/\.]\s*(\d{1,2})(?!\d)')
  if ($m3.Success) { return ("{0}-{1:D2}-??" -f $m3.Groups[1].Value,[int]$m3.Groups[2].Value) }
  $m5 = [regex]::Match($w,'class="date"[^>]*>\s*(\d{1,2})\s*</span>\s*<span[^>]*class="year"[^>]*>\s*(20\d{2})[-/](\d{1,2})')
  if ($m5.Success) { return ("{0}-{1:D2}-{2:D2}" -f $m5.Groups[2].Value,[int]$m5.Groups[3].Value,[int]$m5.Groups[1].Value) }
  $m4 = [regex]::Match($w,'(\d{1,2})\s*[-月]\s*(\d{1,2})\s*日?')
  if ($m4.Success) { return ("MM-DD:" + $m4.Groups[1].Value + "-" + $m4.Groups[2].Value) }
  return ''
}

foreach ($u in $Urls) {
  $u = $u.Trim()
  Write-Output ('=' * 96)
  Write-Output "URL   : $u"
  try { $p = Get-Page $u } catch { Write-Output ("STATUS: ERR  " + $_.Exception.Message); continue }
  $t = $p.Text
  Write-Output ("STATUS: {0}  bytes={1}  charset={2}" -f $p.Status, $p.Bytes, $p.Charset)
  Write-Output ("TITLE : " + (([regex]::Match($t,'(?is)<title[^>]*>(.*?)</title>').Groups[1].Value -replace '\s+',' ').Trim()))
  $total = [regex]::Match($t,'共\s*(\d+)\s*条').Groups[1].Value

  $items = @(); $seen = @{}; $famScore = @{ li=0; divdate=0; td=0; dl=0; json=0 }; $dfmt = @{}
  foreach ($am in [regex]::Matches($t,'(?is)<a\b([^>]*)>(.*?)</a>')) {
    $attrs = $am.Groups[1].Value
    $href = if ($attrs -match 'href="([^"]*)"') { $Matches[1] } elseif ($attrs -match "href='([^']*)'") { $Matches[1] } else { '' }
    if (-not $href -or $href -match '(?i)javascript:|^#') { continue }
    if ($href -notmatch $ART) { continue }
    $txt = ($am.Groups[2].Value -replace '<[^>]+>','' -replace '&nbsp;',' ' -replace '&amp;','&' -replace '&quot;','"' -replace '\s+',' ').Trim()
    if ($txt.Length -lt 5 -and $attrs -match 'title="([^"]{5,})"') { $txt = $Matches[1] }
    if ($txt.Length -lt 5) { continue }
    $key = $href
    if ($seen.ContainsKey($key)) { continue }
    $seen[$key] = 1
    # 日期窗口：优先锚点之后
    $s = $am.Index + $am.Length
    $afterW  = if ($s -lt $t.Length) { $t.Substring($s, [Math]::Min(220, $t.Length - $s)) } else { '' }
    $beforeS = [Math]::Max(0, $am.Index - 260)
    $beforeW = $t.Substring($beforeS, $am.Index - $beforeS)
    $innerW = $am.Groups[2].Value
    $d = Get-Date3 ($innerW + " " + $afterW)
    if (-not $d) { $d = Get-Date3 $beforeW }
    # 家族判定（锚点前 300 字符）
    $ctx = $beforeW
    $fam = 'other'
    if ($ctx -match '(?is)<li[^>]*>\s*$' -or $ctx -match '(?is)<li[^>]*>(?:(?!</li>).)*$') { $fam='li' }
    if ($afterW -match '(?is)^\s*</div>\s*$' -and $ctx -match 'class="date"') { $fam='divdate' }
    if ($ctx -match '(?is)<td[^>]*>\s*$') { $fam='td' }
    if ($ctx -match 'class="[^"]*date[^"]*"' -or $afterW -match 'class="[^"]*date') { $fam='divdate' }
    $famScore[$fam]++
    if ($d) { $dfmt[$d.Substring(0,[Math]::Min(7,$d.Length))] = 1 }
    $items += [pscustomobject]@{ Title=$txt; Date=$d; Url=(Abs $p.Final $href); Fam=$fam
      Raw=(($am.Value -replace '\s+',' ').Trim()) }
  }
  $famTop = ($famScore.GetEnumerator() | Sort-Object Value -Descending | Where-Object {$_.Value -gt 0} | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ' '
  Write-Output ("ITEMS : {0}   total='{1}'   families[{2}]" -f $items.Count, $total, $famTop)
  Write-Output ("DATES : " + (($dfmt.Keys | Select-Object -First 4) -join ' | '))
  $pg = [regex]::Matches($t,'(?i)href="([^"]*(?:/\d+\.htm|index_\d+\.htm|page=\d+|PageNo=\d+|page/\d+|_2\.html|/2\.html))"') |
        ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique -First 4
  Write-Output ("PAGER : " + $(if($pg){$pg -join '  '}else{'(none)'}))
  $js = [regex]::Matches($t,'(?i)enableJS|document\.write\(|window\.location').Count
  $waf = [regex]::Matches($t,'(?i)captcha|验证码|安全验证|cloudflare|雷池|人机验证').Count
  $sum = [regex]::Matches($t,'(?is)<p>(?:(?!</p>).){20,}?</p>').Count
  Write-Output ("JS/WAF: jsHook=$js wafHint=$waf pBlocks=$sum")

  foreach ($it in ($items | Select-Object -First $Top)) {
    Write-Output ("  ITEM> {0}  |  {1}  |  {2}  [{3}]" -f $it.Title, $it.Date, $it.Url, $it.Fam)
    if ($ShowItems) { Write-Output ("  RAW > " + $it.Raw.Substring(0,[Math]::Min(300,$it.Raw.Length))) }
  }
  if ($DumpDir) {
    if (-not (Test-Path $DumpDir)) { New-Item -ItemType Directory -Path $DumpDir -Force | Out-Null }
    $name = ($u -replace '^https?://','' -replace '[^\w\.\-]','_'); if ($name.Length -gt 80) { $name = $name.Substring(0,80) }
    [System.IO.File]::WriteAllText((Join-Path $DumpDir "$name.html"), $t, [System.Text.Encoding]::UTF8)
  }
  Start-Sleep -Milliseconds 400
}


