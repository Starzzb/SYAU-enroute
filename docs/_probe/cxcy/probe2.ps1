# Follow-up probe: correct articlelist path + state variety. Serial, 600ms gap.
$ErrorActionPreference = 'Continue'
$root = 'O:\dev\enroute-android'
$outDir = Join-Path $root 'docs\_probe\cxcy\raw'
$ua = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36'
$targets = @(
  @{ name='cxcy_articlelist_v2'; url='http://cxcy.syau.edu.cn/comp/front/comp/articlelist?cid=12' },
  @{ name='cxcy_comp_list_p3';   url='http://cxcy.syau.edu.cn/comp/front/comp/list?page=3' }
)
$meta = @()
foreach ($t in $targets) {
  $item = @{ name=$t.name; url=$t.url; status=0; bytes=0; error='' }
  try {
    $resp = Invoke-WebRequest -Uri $t.url -UserAgent $ua -TimeoutSec 15 -MaximumRedirection 5 -UseBasicParsing
    $item.status = [int]$resp.StatusCode
    $bytes = $resp.Content
    if ($bytes -is [string]) { $bytes = [System.Text.Encoding]::UTF8.GetBytes($bytes) }
    $item.bytes = $bytes.Length
    [System.IO.File]::WriteAllBytes((Join-Path $outDir ($t.name + '.html')), $bytes)
  } catch {
    $item.error = $_.Exception.Message
    if ($_.Exception.Response) { $item.status = [int]$_.Exception.Response.StatusCode }
  }
  $meta += $item
  Start-Sleep -Milliseconds 600
}
$meta | ConvertTo-Json -Depth 4 | Set-Content -Path (Join-Path $root 'docs\_probe\cxcy\probe2-meta.json') -Encoding UTF8
'DONE2' | Set-Content -Path (Join-Path $root 'docs\_probe\cxcy\probe2-done.txt') -Encoding UTF8