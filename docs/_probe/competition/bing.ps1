param(
  [Parameter(Mandatory=$true)][string]$Q,
  [int]$Count = 10,
  [switch]$Raw
)
# Bing 搜索兜底（百度已出验证码，tavily/deepseek MCP 故障）
$ProgressPreference = 'SilentlyContinue'
$UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36'
$q = [uri]::EscapeDataString($Q)
$out = @()
for ($first = 1; $first -le [math]::Ceiling($Count / 10); $first++) {
  $url = "https://cn.bing.com/search?q=$q"
  try { $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 25 -Headers @{ 'User-Agent' = $UA; 'Accept-Language' = 'zh-CN,zh;q=0.9' } }
  catch { Write-Output "ERR $($_.Exception.Message)"; break }
  $t = $r.Content
  foreach ($m in [regex]::Matches($t,'(?is)<li class="b_algo".*?</li>')) {
    $blk = $m.Value
    $a = [regex]::Match($blk,'(?is)<h2[^>]*>\s*<a[^>]*href="([^"]+)"[^>]*>(.*?)</a>')
    if (-not $a.Success) { continue }
    $link = $a.Groups[1].Value
    $title = ($a.Groups[2].Value -replace '<[^>]+>','' -replace '&amp;','&' -replace '\s+',' ').Trim()
    $cite = [regex]::Match($blk,'(?is)<cite[^>]*>(.*?)</cite>').Groups[1].Value -replace '<[^>]+>',''
    $out += [pscustomobject]@{ Title=$title; Link=$link; Cite=($cite -replace '\s+',' ').Trim() }
  }
  Start-Sleep -Milliseconds 300
}
if ($Raw) { $out | ForEach-Object { "$($_.Title)`t$($_.Link)" } }
else { $out | ForEach-Object { "- $($_.Title)`n    $($_.Link)   [$($_.Cite)]" } }
"TOTAL $($out.Count)"
