$ErrorActionPreference = 'Continue'
$hosts = @(
  'thxy.syau.edu.cn','nxy.syau.edu.cn','spxy.syau.edu.cn','jgxy.syau.edu.cn',
  'xdxy.syau.edu.cn','gcxy.syau.edu.cn','slxy.syau.edu.cn','yyxy.syau.edu.cn',
  'zbxy.syau.edu.cn','lin.syau.edu.cn','swxy.syau.edu.cn','lxy.syau.edu.cn',
  'myxy.syau.edu.cn','marx.syau.edu.cn','tyb.syau.edu.cn','wyb.syau.edu.cn'
)
foreach ($h in $hosts) {
  $url = "https://$h/"
  try {
    $r = Invoke-WebRequest -Uri $url -TimeoutSec 15 -UseBasicParsing -UserAgent 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36'
    $html = $r.Content
  } catch { Write-Output "=== $h ERR $($_.Exception.Message.Split("`n")[0])"; Start-Sleep -Milliseconds 250; continue }
  Write-Output "=== $h"
  $ms = [regex]::Matches($html, '<a\b([^>]*?)>(.*?)</a>', 'Singleline,IgnoreCase')
  foreach ($m in $ms) {
    $attrs = $m.Groups[1].Value
    $text = [regex]::Replace($m.Groups[2].Value, '<[^>]+>', '')
    $text = [regex]::Replace($text, '\s+', '').Trim()
    if ($text -notmatch '师资|教师| TEAM|队伍|professor|teacher|faculty|导师|人员') { continue }
    $href = [regex]::Match($attrs, 'href\s*=\s*["'']([^"'']+)["'']').Groups[1].Value
    if ([string]::IsNullOrWhiteSpace($href)) { continue }
    try { $abs = ([Uri]::new([Uri]::new($url), $href)).AbsoluteUri } catch { $abs = $href }
    Write-Output ("  {0,-16} {1}" -f $text.Substring(0,[Math]::Min(16,$text.Length)), $abs)
  }
  Start-Sleep -Milliseconds 250
}
