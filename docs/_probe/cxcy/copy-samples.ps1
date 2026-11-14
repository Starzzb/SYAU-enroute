# Copy fresh real-page samples into test resources (UTF-8 as served).
$src = 'O:\dev\enroute-android\docs\_probe\cxcy\raw'
$dst = 'O:\dev\enroute-android\app\src\test\resources\feed'
$map = @{
  'cxcy_comp_list.html'      = 'cxcy_comp_list.html'
  'cxcy_comp_list_p2.html'   = 'cxcy_comp_list_p2.html'
  'cxcy_comp_list_p3.html'   = 'cxcy_comp_list_p3.html'
  'cxcy_dcplan.html'         = 'cxcy_dcplan_list.html'
  'cxcy_articlelist_v2.html' = 'cxcy_article_list.html'
  'cxcy_comp_detail.html'    = 'cxcy_comp_detail.html'
  'tw_list.html'             = 'tw_list.html'
  'cahe_list.html'           = 'cahe_list.html'
  'jingsai_list_utf8.html'   = 'jingsai_list.html'
}
foreach ($k in $map.Keys) {
  Copy-Item -Path (Join-Path $src $k) -Destination (Join-Path $dst $map[$k]) -Force
}
'COPIED' | Set-Content -Path 'O:\dev\enroute-android\docs\_probe\cxcy\copy-done.txt' -Encoding UTF8