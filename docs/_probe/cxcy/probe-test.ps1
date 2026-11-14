'START ' + (Get-Date -Format o) | Set-Content -Path 'O:\dev\enroute-android\docs\_probe\cxcy\probe-start.txt' -Encoding UTF8
try {
  $r = Invoke-WebRequest -Uri 'http://cxcy.syau.edu.cn/' -UseBasicParsing -TimeoutSec 10 -UserAgent 'Mozilla/5.0'
  ('OK ' + $r.StatusCode + ' len=' + $r.RawContentLength) | Set-Content -Path 'O:\dev\enroute-android\docs\_probe\cxcy\probe-test.txt' -Encoding UTF8
} catch {
  ('ERR ' + $_.Exception.Message) | Set-Content -Path 'O:\dev\enroute-android\docs\_probe\cxcy\probe-test.txt' -Encoding UTF8
}