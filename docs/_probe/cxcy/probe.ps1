# Probe cxcy platform + new competition sources. Serial GET, 600ms gap, read-only.
# Outputs: docs/_probe/cxcy/raw/*.html + probe-meta.json + probe-done.txt
$ErrorActionPreference = 'Continue'
$root = 'O:\dev\enroute-android'
$outDir = Join-Path $root 'docs\_probe\cxcy\raw'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
'START ' + (Get-Date -Format o) | Set-Content -Path (Join-Path $root 'docs\_probe\cxcy\probe-start.txt') -Encoding UTF8
$ua = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36'
$targets = @(
  @{ name='cxcy_home';        url='http://cxcy.syau.edu.cn/' },
  @{ name='cxcy_comp_list';   url='http://cxcy.syau.edu.cn/comp/front/comp/list' },
  @{ name='cxcy_comp_list_p2';url='http://cxcy.syau.edu.cn/comp/front/comp/list?page=2' },
  @{ name='cxcy_articlelist'; url='http://cxcy.syau.edu.cn/comp/articlelist?cid=12' },
  @{ name='cxcy_dcplan';      url='http://cxcy.syau.edu.cn/dcplan/front/dcplan/list' },
  @{ name='cxcy_comp_detail'; url='http://cxcy.syau.edu.cn/comp/info?id=1542' },
  @{ name='tw_list';          url='https://tw.syau.edu.cn/channel.jsp?urltype=tree.TreeTempUrl&wbtreeid=1053' },
  @{ name='cahe_list';        url='https://www.cahe.edu.cn/site/term/373.html' },
  @{ name='jingsai_list';     url='https://www.52jingsai.com/bisai/xkjn/bangdan/' }
)
$meta = @()
foreach ($t in $targets) {
  $item = @{ name=$t.name; url=$t.url; status=0; bytes=0; contentType=''; error='' }
  try {
    $resp = Invoke-WebRequest -Uri $t.url -UserAgent $ua -TimeoutSec 15 -MaximumRedirection 5 -UseBasicParsing
    $item.status = [int]$resp.StatusCode
    $item.contentType = [string]$resp.Headers['Content-Type']
    $bytes = $resp.Content
    if ($bytes -is [string]) { $bytes = [System.Text.Encoding]::UTF8.GetBytes($bytes) }
    $item.bytes = $bytes.Length
    [System.IO.File]::WriteAllBytes((Join-Path $outDir ($t.name + '.html')), $bytes)
    if ($t.name -eq 'jingsai_list') {
      $gb = [System.Text.Encoding]::GetEncoding('GB18030')
      $text = $gb.GetString($bytes)
      $utf8 = New-Object System.Text.UTF8Encoding($false)
      [System.IO.File]::WriteAllText((Join-Path $outDir 'jingsai_list_utf8.html'), $text, $utf8)
    }
  } catch {
    $item.error = $_.Exception.Message
    if ($_.Exception.Response) { $item.status = [int]$_.Exception.Response.StatusCode }
  }
  $meta += $item
  Start-Sleep -Milliseconds 600
}
$meta | ConvertTo-Json -Depth 4 | Set-Content -Path (Join-Path $root 'docs\_probe\cxcy\probe-meta.json') -Encoding UTF8
'DONE ' + (Get-Date -Format o) | Set-Content -Path (Join-Path $root 'docs\_probe\cxcy\probe-done.txt') -Encoding UTF8