param(
  [Parameter(Mandatory=$true)][string[]]$Urls,
  [string]$Filter = '竞赛|大赛|比赛|挑战杯|创新|创业|赛事|选拔|报名|申报|实践',
  [int]$MaxLinks = 40,
  [string]$DumpDir = '',
  [switch]$NoFilter
)
# 竞赛信息源探测：状态码 + 编码 + 结构统计 + 关键词锚点
$ProgressPreference = 'SilentlyContinue'
try { [System.Text.Encoding]::RegisterProvider([System.Text.CodePagesEncodingProvider]::Instance) } catch {}
$UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36'

function Get-Page([string]$u) {
  $tmp = [System.IO.Path]::GetTempFileName()
  try {
    $r = Invoke-WebRequest -Uri $u -UseBasicParsing -TimeoutSec 30 -MaximumRedirection 5 `
      -Headers @{ 'User-Agent'=$UA; 'Accept'='text/html,application/xhtml+xml,*/*'; 'Accept-Language'='zh-CN,zh;q=0.9' } `
      -OutFile $tmp -PassThru
    $bytes = [System.IO.File]::ReadAllBytes($tmp)
    $head = [System.Text.Encoding]::ASCII.GetString($bytes, 0, [Math]::Min(2048, $bytes.Length))
    $cs = 'utf-8'
    $h = @($r.Headers['Content-Type']) -join ''
    if ($h -match 'charset=([\w-]+)') { $cs = $Matches[1] }
    elseif ($head -match 'charset="?([\w-]+)') { $cs = $Matches[1] }
    if ($cs -match '^(gb2312|gbk|gb18030)$') { $cs = 'gb18030' }
    try { $enc = [System.Text.Encoding]::GetEncoding($cs) } catch { $enc = [System.Text.Encoding]::UTF8 }
    $text = $enc.GetString($bytes)
    return [pscustomobject]@{ Status=$r.StatusCode; Bytes=$bytes.Length; Charset=$cs; Text=$text;
      CType=($h -join ''); Server=(@($r.Headers['Server']) -join ''); Final=$r.BaseResponse.RequestMessage.RequestUri.AbsoluteUri }
  } finally { Remove-Item $tmp -ErrorAction SilentlyContinue }
}

$kw = if ($NoFilter -or -not $Filter) { $null } else { [regex]::new($Filter) }
$Urls = $Urls -split '[;|]' | Where-Object { $_.Trim() }

foreach ($u in $Urls) {
  $u = $u.Trim()
  Write-Output ("=" * 100)
  Write-Output ("URL   : $u")
  try { $p = Get-Page $u } catch {
    Write-Output ("STATUS: ERR  " + $_.Exception.Message)
    continue
  }
  Write-Output ("STATUS: {0}  bytes={1}  charset={2}  server={3}" -f $p.Status, $p.Bytes, $p.Charset, $p.Server)
  Write-Output ("FINAL : {0}" -f $p.Final)
  $t = $p.Text
  Write-Output ("TITLE : {0}" -f ([regex]::Match($t,'(?is)<title[^>]*>(.*?)</title>').Groups[1].Value -replace '\s+',' ').Trim())
  # 结构指纹
  $fp = [ordered]@{
    infoHtm  = [regex]::Matches($t,'/info/\d+/\d+\.htm').Count
    contentJsp = [regex]::Matches($t,'content\.jsp\?[^"''<>\s]*wbnewsid=\d+').Count
    list_jsp = [regex]::Matches($t,'list\.jsp\?[^"''<>\s]+').Count
    spanDate = [regex]::Matches($t,'<span[^>]*>\s*\d{4}[-/年]\d{1,2}').Count
    liA      = [regex]::Matches($t,'(?is)<li[^>]*>\s*<a[^>]+href').Count
    trTd     = [regex]::Matches($t,'(?is)<tr[^>]*>\s*<td').Count
    dlDt     = [regex]::Matches($t,'(?is)<dl[^>]*>').Count
    vsbCss   = [regex]::Matches($t,'index\.vsb\.css|_sitegray').Count
    jsonScript = [regex]::Matches($t,'(?i)type="application/(ld\+)?json"').Count
    rssLink  = [regex]::Matches($t,'(?i)<link[^>]+application/rss\+xml').Count
    totalNum = [regex]::Match($t,'共\s*(\d+)\s*条').Groups[1].Value
    pagerP_t = [regex]::Matches($t,'class="p_t"').Count
    pagerPage = [regex]::Matches($t,'(?i)(下一页|尾页|共\d+页)').Count
    jsenable = [regex]::Matches($t,'(?i)enableJS|document\.write\(|__NUXT__|__NEXT_DATA__|vue\.js|react').Count
    captcha  = [regex]::Matches($t,'(?i)captcha|验证码|滑动验证').Count
  }
  Write-Output ("FINGER: " + (($fp.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join '  '))

  if ($DumpDir) {
    if (-not (Test-Path $DumpDir)) { New-Item -ItemType Directory -Path $DumpDir -Force | Out-Null }
    $name = ($u -replace '^https?://','' -replace '[^\w\.\-]','_')
    if ($name.Length -gt 90) { $name = $name.Substring(0,90) }
    [System.IO.File]::WriteAllText((Join-Path $DumpDir "$name.html"), $t, [System.Text.Encoding]::UTF8)
  }

  # 关键词锚点
  $shown = 0
  foreach ($m in [regex]::Matches($t,'(?is)<a\b([^>]*)>(.*?)</a>')) {
    $attrs = $m.Groups[1].Value
    $txt = ($m.Groups[2].Value -replace '<[^>]+>','' -replace '&nbsp;',' ' -replace '\s+',' ').Trim()
    if (-not $txt -and ($attrs -match 'title="([^"]{4,})"')) { $txt = $Matches[1] }
    if (-not $txt) { continue }
    if ($kw -and -not $kw.IsMatch($txt)) { continue }
    $href = if ($attrs -match 'href="([^"]*)"') { $Matches[1] } elseif ($attrs -match "href='([^']*)'") { $Matches[1] } else { '' }
    $ttl  = if ($attrs -match 'title="([^"]*)"') { $Matches[1] } else { '' }
    Write-Output ("  A> [{0}] {1} :: {2}" -f $txt, $href, $ttl)
    $shown++
    if ($shown -ge $MaxLinks) { Write-Output "  ...(truncated)"; break }
  }
  Write-Output ("MATCHED: $shown")
  Start-Sleep -Milliseconds 500
}
