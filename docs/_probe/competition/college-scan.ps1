param(
  [string[]]$Hosts = @(),
  [string[]]$Paths = @('/index/tzgg.htm','/tzgg.htm','/xwzx/tzgg.htm')
)
# 学院通知公告栏目探测：返回状态/条目数/其中“竞赛相关”条目数
$ProgressPreference = 'SilentlyContinue'
try { [System.Text.Encoding]::RegisterProvider([System.Text.CodePagesEncodingProvider]::Instance) } catch {}
$UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36'
$KW = '竞赛|大赛|挑战杯|创新创业|学科竞赛|比赛'

function Fetch([string]$pu) {
  $tmp = [System.IO.Path]::GetTempFileName()
  try {
    $r = Invoke-WebRequest -Uri $pu -UseBasicParsing -TimeoutSec 20 -MaximumRedirection 5 `
      -Headers @{ 'User-Agent'=$UA } -OutFile $tmp -PassThru
    $b = [System.IO.File]::ReadAllBytes($tmp)
    $hd = [System.Text.Encoding]::ASCII.GetString($b, 0, [Math]::Min(2048, $b.Length))
    $c = 'utf-8'
    $h = @($r.Headers['Content-Type']) -join ''
    if ($h -match 'charset=([\w-]+)') { $c = $Matches[1] } elseif ($hd -match 'charset="?([\w-]+)') { $c = $Matches[1] }
    if ($c -match '^(gb2312|gbk|gb18030)$') { $c = 'gb18030' }
    try { $e = [System.Text.Encoding]::GetEncoding($c) } catch { $e = [System.Text.Encoding]::UTF8 }
    return [pscustomobject]@{ Status=$r.StatusCode; Text=$e.GetString($b); Final=($r.BaseResponse.RequestMessage.RequestUri.AbsoluteUri) }
  } finally { Remove-Item $tmp -ErrorAction SilentlyContinue }
}

$rows = @()
foreach ($h in $Hosts) {
  foreach ($p in $Paths) {
    $u = "https://$h$p"
    try { $pg = Fetch $u } catch { continue }
    if ($pg.Status -ne 200) { continue }
    $t = $pg.Text
    if ($t -match '(?i)<title>\s*(404|页面不存在|Not Found)') { continue }
    $arts = [regex]::Matches($t,'(?is)<a\b[^>]*href="([^"]*(?:/info/\d+/\d+\.htm|content\.jsp\?[^"]*wbnewsid=\d+))"[^>]*>(.*?)</a>')
    $titles = @()
    foreach ($a in $arts) {
      $tx = ($a.Groups[2].Value -replace '<[^>]+>','' -replace '\s+',' ').Trim()
      if ($tx.Length -ge 4) { $titles += $tx }
    }
    $titles = $titles | Select-Object -Unique
    if ($titles.Count -lt 3) { continue }
    $hit = $titles | Where-Object { $_ -match $KW }
    $rows += [pscustomobject]@{
      Host = $h; Path = $p; Status = $pg.Status; Items = $titles.Count
      Comp = @($hit).Count; Title = [regex]::Match($t,'(?is)<title>(.*?)</title>').Groups[1].Value.Trim()
      Sample = (($hit | Select-Object -First 1) -join '')
    }
    break
  }
}
$rows | Format-Table -AutoSize -Wrap Host,Path,Status,Items,Comp,Sample | Out-String -Width 220
$rows | ForEach-Object { "$($_.Host)`t$($_.Path)`titems=$($_.Items)`tcomp=$($_.Comp)`t$($_.Title)" }
