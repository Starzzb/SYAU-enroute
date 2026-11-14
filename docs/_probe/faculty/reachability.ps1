$ErrorActionPreference = 'Continue'
$hosts = @(
  'www.syau.edu.cn',
  'thxy.syau.edu.cn',
  'nxy.syau.edu.cn',
  'spxy.syau.edu.cn',
  'jgxy.syau.edu.cn',
  'xdxy.syau.edu.cn',
  'gcxy.syau.edu.cn',
  'slxy.syau.edu.cn',
  'yyxy.syau.edu.cn',
  'zbxy.syau.edu.cn',
  'lin.syau.edu.cn',
  'swxy.syau.edu.cn',
  'lxy.syau.edu.cn',
  'myxy.syau.edu.cn',
  'marx.syau.edu.cn',
  'tyb.syau.edu.cn',
  'wyb.syau.edu.cn',
  'jwc.syau.edu.cn'
)
$out = @()
foreach ($h in $hosts) {
  foreach ($scheme in @('https','http')) {
    $url = "$scheme`://$h/"
    $rec = [pscustomobject]@{ Host=$h; Scheme=$scheme; Status=''; Title=''; Len=0; FinalUrl='' }
    try {
      $r = Invoke-WebRequest -Uri $url -MaximumRedirection 5 -TimeoutSec 12 -UseBasicParsing -UserAgent 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36'
      $rec.Status = [string]$r.StatusCode
      $html = $r.Content
      $rec.Len = $html.Length
      $m = [regex]::Match($html, '<title>([^<]*)</title>')
      if ($m.Success) { $rec.Title = $m.Groups[1].Value.Trim() }
      $rec.FinalUrl = $r.RequestUri.AbsoluteUri
    } catch {
      $rec.Status = 'ERR ' + $_.Exception.Message.Split("`n")[0]
    }
    $out += $rec
    Start-Sleep -Milliseconds 300
    if ($rec.Status -notlike 'ERR*') { break }
  }
}
$out | Format-Table -AutoSize | Out-String -Width 260
