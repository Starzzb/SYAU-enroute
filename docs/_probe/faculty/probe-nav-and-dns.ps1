# Fill the gaps the previous run left (ASCII-only on purpose: PS file encoding is GBK here).
#  * confirm each college's faculty column URL from its own homepage nav (no guessing)
#  * confirm list-page rule (info/{col}/{id}.htm) + detail column IDs per college
#  * record DNS so the reachability column in the report is evidence-backed
# Read-only; writes only into docs/_probe/faculty/raw2/
$ErrorActionPreference = 'Continue'
$ua = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36'
$dir = 'O:\dev\enroute-android\docs\_probe\faculty\raw2'
New-Item -ItemType Directory -Force -Path $dir | Out-Null

$hosts = @(
  'nxy.syau.edu.cn','zbxy.syau.edu.cn','yyxy.syau.edu.cn','thxy.syau.edu.cn',
  'jgxy.syau.edu.cn','myxy.syau.edu.cn','lin.syau.edu.cn','gcxy.syau.edu.cn',
  'xdxy.syau.edu.cn','slxy.syau.edu.cn','spxy.syau.edu.cn','swxy.syau.edu.cn',
  'marx.syau.edu.cn','lxy.syau.edu.cn','tyb.syau.edu.cn','wyb.syau.edu.cn'
)

Write-Output "### 1. DNS A records"
foreach ($h in $hosts) {
  $ips = ''
  try {
    $ips = (Resolve-DnsName -Name $h -Type A -ErrorAction Stop | Where-Object { $_.IPAddress } | ForEach-Object { $_.IPAddress }) -join ','
  } catch { $ips = 'DNS-ERR' }
  Write-Output ("{0,-22} {1}" -f $h, $ips)
}

Write-Output ""
Write-Output "### 2. homepage nav -> faculty columns"
foreach ($h in $hosts) {
  $url = "https://$h/"
  try {
    $r = Invoke-WebRequest -Uri $url -TimeoutSec 20 -UseBasicParsing -UserAgent $ua
  } catch {
    Write-Output ("=== {0}  ERR {1}" -f $h, $_.Exception.Message.Split("`n")[0]); continue
  }
  $html = $r.Content
  [IO.File]::WriteAllText((Join-Path $dir ("home_" + $h + ".html")), $html, [Text.Encoding]::UTF8)
  Write-Output ("=== {0}  [{1}] len={2}" -f $h, $r.StatusCode, $html.Length)
  $seen = @{}
  foreach ($m in [regex]::Matches($html, '<a\b([^>]*)>(.*?)</a>', 'Singleline,IgnoreCase')) {
    $txt = [regex]::Replace($m.Groups[2].Value, '<[^>]+>', '')
    $txt = [regex]::Replace($txt, '\s+', '').Trim()
    if ($txt -notmatch [regex]::Unescape('\u5e08\u8d44|\u6559\u5e08|\u961f\u4f0d|\u5bfc\u5e08|\u4eba\u5458')) { continue }
    $hm = [regex]::Match($m.Groups[1].Value, 'href\s*=\s*["'']([^"'']+)["'']')
    if (-not $hm.Success) { continue }
    $href = $hm.Groups[1].Value
    if ($href -match '^javascript|^#') { continue }
    try { $abs = ([Uri]::new([Uri]::new($url), $href)).AbsoluteUri } catch { $abs = $href }
    if ($seen.ContainsKey($abs)) { continue }
    $seen[$abs] = 1
    Write-Output ("    NAV {0,-12} {1}" -f $txt.Substring(0, [Math]::Min(12, $txt.Length)), $abs)
  }
  Start-Sleep -Milliseconds 200
}
