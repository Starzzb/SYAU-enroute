$ProgressPreference='SilentlyContinue'
$h=@{
  'User-Agent'='Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36'
  'Accept-Encoding'='gzip'
}
$urls=@(
 'https://www.syau.edu.cn/index/tzgg.htm',
 'https://news.syau.edu.cn/snyw.htm',
 'https://jwc.syau.edu.cn/index/jwtz.htm',
 'http://xsc.syau.edu.cn/tzgg.htm',
 'https://grs.syau.edu.cn/tzgg.htm',
 'http://tw.syau.edu.cn/index/tzgg.htm',
 'http://zbxy.syau.edu.cn/index/tzgg.htm',
 'http://spxy.syau.edu.cn/index/tzgg.htm',
 'https://lib.syau.edu.cn/index/xwgg.htm',
 'https://gzc.syau.edu.cn/tzgg/zbgg.htm'
)
$rows=@()
foreach($u in $urls){
  $row=[ordered]@{url=$u; status=''; len=0; info=0; jsp=0; h6=0; spanD=0; em=0; lineId=0; titAttr=0; total=''; pager=''; cs=''}
  try{
    $r=Invoke-WebRequest -Uri $u -TimeoutSec 25 -UseBasicParsing -Headers $h
    $t=$r.Content
    $row.status=$r.StatusCode
    $row.len=$t.Length
    $row.info=[regex]::Matches($t,'/info/\d+/\d+\.htm').Count
    $row.jsp=[regex]::Matches($t,'content\.jsp\?[^"' + [char]39 + ']*wbnewsid=\d+').Count
    $row.h6=[regex]::Matches($t,'<h6[^>]*class="[^"]*title[^"]*"').Count
    $row.spanD=[regex]::Matches($t,'<span[^>]*class="[^"]*date[^"]*"').Count
    $row.em=[regex]::Matches($t,'<em').Count
    $row.lineId=[regex]::Matches($t,'id="line_u\d+_\d+"').Count
    $row.titAttr=[regex]::Matches($t,'<a[^>]+title="[^"]{6,}"[^>]*>').Count
    $row.total=[regex]::Match($t,'共\s*(\d+)\s*条').Groups[1].Value
    $row.pager=[regex]::Match($t,'class="p_t"').Success
    $row.cs=[regex]::Match($t,'charset="?([\w-]+)').Groups[1].Value
  }catch{
    $row.status='ERR'
  }
  $rows+=[pscustomobject]$row
  Start-Sleep -Milliseconds 900
}
$rows | Format-Table -AutoSize url,status,len,info,jsp,h6,spanD,em,lineId,titAttr,total,pager,cs | Out-String -Width 300
