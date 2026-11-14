$ErrorActionPreference = 'Continue'
$dir = 'O:\dev\enroute-android\docs\_probe\faculty\raw'
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$ua = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36'

$jobs = @(
 # 栏目页（子栏目树 + 翻页）
 @{n='col_thxy_jsxl';    u='https://thxy.syau.edu.cn/szdw/jsxl.htm'},
 @{n='col_thxy_zgjzc';   u='https://thxy.syau.edu.cn/szdw/jsxl/zgjzc.htm'},
 @{n='col_gcxy_szdw';    u='https://gcxy.syau.edu.cn/szdw1.htm'},
 @{n='col_gcxy_jsml';    u='https://gcxy.syau.edu.cn/szdw1/gcjs.htm'},
 @{n='col_yyxy_szdw';    u='https://yyxy.syau.edu.cn/xkjs/szdw.htm'},
 @{n='col_tyb_jsdw';     u='https://tyb.syau.edu.cn/bmgk/jsdw.htm'},
 @{n='col_wyb_szdw';     u='https://wyb.syau.edu.cn/szdw.htm'},
 @{n='col_marx_szll';    u='https://marx.syau.edu.cn/szll/sxddyfzjys.htm'},
 @{n='col_spxy_animal';  u='https://spxy.syau.edu.cn/szdw/zysz/1.htm'},
 # 详情页样本
 @{n='det_thxy_wangjingkuan'; u='https://thxy.syau.edu.cn/info/1611/4929.htm'},
 @{n='det_thxy_liuxiaohu';    u='https://thxy.syau.edu.cn/info/1611/4936.htm'},
 @{n='det_slxy_zhengsiyu';    u='https://slxy.syau.edu.cn/info/1241/8158.htm'},
 @{n='det_nxy_zhangwenzhong'; u='https://nxy.syau.edu.cn/info/1393/6990.htm'},
 @{n='det_swxy_quanbo';       u='https://swxy.syau.edu.cn/info/1020/1198.htm'},
 @{n='det_xdxy_wangjun';      u='https://xdxy.syau.edu.cn/info/1008/2524.htm'},
 @{n='det_jgxy_zhoumi';       u='https://jgxy.syau.edu.cn/szdw/bssds.htm'},
 @{n='det_jgxy_6071';         u='https://jgxy.syau.edu.cn/info/1326/6071.htm'},
 @{n='det_lin_luxiujun';      u='https://lin.syau.edu.cn/info/1032/2661.htm'},
 @{n='det_gcxy_1058';         u='https://gcxy.syau.edu.cn/info/1044/1058.htm'},
 @{n='det_myxy_6589';         u='https://myxy.syau.edu.cn/info/1166/6589.htm'},
 @{n='det_lxy_1289';          u='https://lxy.syau.edu.cn/info/1025/1289.htm'},
 @{n='det_zbxy_1808';         u='https://zbxy.syau.edu.cn/info/1124/1808.htm'}
)
foreach ($j in $jobs) {
  $f = Join-Path $dir ($j.n + '.html')
  try {
    $r = Invoke-WebRequest -Uri $j.u -TimeoutSec 20 -UseBasicParsing -UserAgent $ua
    [IO.File]::WriteAllText($f, $r.Content, [Text.Encoding]::UTF8)
    Write-Output ("OK  {0,-24} {1,7} bytes  {2}" -f $j.n, $r.Content.Length, $j.u)
  } catch {
    Write-Output ("ERR {0,-24} {1}  {2}" -f $j.n, $_.Exception.Message.Split("`n")[0], $j.u)
  }
  Start-Sleep -Milliseconds 350
}
