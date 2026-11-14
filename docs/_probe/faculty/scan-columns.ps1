$ErrorActionPreference = 'Continue'
$urls = @(
 'https://thxy.syau.edu.cn/szdw/jsxl/zgjzc.htm',
 'https://thxy.syau.edu.cn/szdw/bssds.htm',
 'https://gcxy.syau.edu.cn/szdw1/xxmj.htm',
 'https://spxy.syau.edu.cn/szdw/zysz.htm',
 'https://spxy.syau.edu.cn/szdw/dsfc.htm',
 'https://slxy.syau.edu.cn/szdw2.htm',
 'https://lin.syau.edu.cn/szdw/zyjs.htm',
 'https://swxy.syau.edu.cn/szdw/zzjs.htm',
 'https://jgxy.syau.edu.cn/szdw/bssds.htm',
 'https://xdxy.syau.edu.cn/szdw.htm',
 'https://myxy.syau.edu.cn/szdw.htm',
 'https://lxy.syau.edu.cn/szdw1.htm',
 'https://nxy.syau.edu.cn/szdw1.htm',
 'https://zbxy.syau.edu.cn/xkjs/szll.htm',
 'https://yyxy.syau.edu.cn/xkjs/szdw.htm',
 'https://marx.syau.edu.cn/szll/sxddyfzjys.htm',
 'https://wyb.syau.edu.cn/szdw.htm',
 'https://tyb.syau.edu.cn/bmgk/jsdw.htm'
)
foreach ($u in $urls) {
  try {
    $r = Invoke-WebRequest -Uri $u -TimeoutSec 15 -UseBasicParsing -UserAgent 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36'
    $html = $r.Content
  } catch { Write-Output "=== $u  ERR $($_.Exception.Message.Split("`n")[0])"; Start-Sleep -Milliseconds 250; continue }
  $t = [regex]::Match($html,'<title>([^<]*)</title>').Groups[1].Value.Trim()
  $arts = [regex]::Matches($html, 'info/(\d{3,5})/(\d+)\.htm') | ForEach-Object { "$($_.Groups[1].Value)/$($_.Groups[2].Value)" } | Select-Object -Unique
  $links = [regex]::Matches($html, '<a\b[^>]*href="([^"]*)"[^>]*>(.*?)</a>', 'Singleline,IgnoreCase')
  $named = @()
  foreach ($m in $links) {
    $txt = [regex]::Replace($m.Groups[2].Value, '<[^>]+>', '')
    $txt = [regex]::Replace($txt, '\s+', '')
    if ($txt.Length -ge 2 -and $txt.Length -le 10 -and $txt -notmatch '[，。：；\.]') {
      if ($m.Groups[1].Value -match 'info/\d+/|content\.jsp') { $named += "$txt -> $($m.Groups[1].Value)" }
    }
  }
  Write-Output "=== $u  [$($r.StatusCode)] len=$($html.Length) title=$t"
  Write-Output ("  article ids: " + (($arts | Select-Object -First 12) -join ' '))
  Write-Output ("  short-link labels (candidate teacher names): " + (($named | Select-Object -First 10) -join ' | '))
  Start-Sleep -Milliseconds 300
}
