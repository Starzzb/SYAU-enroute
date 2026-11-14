# Probe a 52jingsai article to find the real body container (Discuz).
$ErrorActionPreference = 'Continue'
$root = 'O:\dev\enroute-android'
$outDir = Join-Path $root 'docs\_probe\cxcy\raw'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$ua = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36'
$url = 'https://www.52jingsai.com/article-23912-1.html'
try {
  $resp = Invoke-WebRequest -Uri $url -UserAgent $ua -TimeoutSec 15 -UseBasicParsing
  $bytes = $resp.Content
  if ($bytes -is [string]) { $bytes = [System.Text.Encoding]::UTF8.GetBytes($bytes) }
  [System.IO.File]::WriteAllBytes((Join-Path $outDir 'jingsai_article.html'), $bytes)
  ('OK ' + $bytes.Length) | Set-Content -Path (Join-Path $root 'docs\_probe\cxcy\jingsai-article-done.txt') -Encoding UTF8
} catch {
  ('ERR ' + $_.Exception.Message) | Set-Content -Path (Join-Path $root 'docs\_probe\cxcy\jingsai-article-done.txt') -Encoding UTF8
}