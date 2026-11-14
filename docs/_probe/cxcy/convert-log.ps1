# Convert GBK build log to UTF-8 (round 2)
$log = 'O:\dev\enroute-android\build-log.txt'
$gbk = [System.Text.Encoding]::GetEncoding('GBK')
$text = [System.IO.File]::ReadAllText($log, $gbk)
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText('O:\dev\enroute-android\build-log-utf8.txt', $text, $utf8)
'CONVERTED2' | Set-Content -Path 'O:\dev\enroute-android\log-convert-done.txt' -Encoding UTF8