# 上屏提示文案扫描器：剥离注释 / contentDescription，扫描字符串字面量中的说明书式文案。
# 用法：pwsh -File scan-hint-copy.ps1
$ErrorActionPreference = 'SilentlyContinue'
$repo = 'O:\dev\enroute-android'
$rx = '轻点|点开|点此|点一下|点中|去点|点「|点右上|点左上|点左下|点右下|点页眉|点格子|点要素|点一门|点身份|点登录|点检查|点安装|点保存|点收藏|点刷新|点重试|点导出|点添加|点下面|点这里|点任意|点任何|长按|按住|上滑|下滑|左滑|右滑|左右滑|上下滑|滑动切换|支持滑|可滑|拖拽|拖动|还有.{0,40}条'

$files = Get-ChildItem "$repo\app\src" -Recurse -Filter *.kt -File |
    Where-Object { $_.FullName -notmatch '\\build\\' -and $_.Name -ne 'FeedSummary.kt' }

$hits = @()
foreach ($f in $files) {
    $lines = Get-Content $f.FullName -Encoding UTF8
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        # 去块注释（同行的 /* ... */）
        $line = [regex]::Replace($line, '/\*.*?\*/', '')
        # 去行注释（// 之后，注意不要误删字符串里的 //，但字符串里有 // 的极少，先粗去）
        $code = ($line -split '//')[0]
        # 去 contentDescription 赋值（无障碍标签）
        $code = [regex]::Replace($code, 'contentDescription\s*=\s*"[^"]*"', 'contentDescription = ""')
        # 抽取字符串字面量内容
        foreach ($m in [regex]::Matches($code, '"([^"]*)"')) {
            $s = $m.Groups[1].Value
            if ($s -match $rx) {
                $hits += [pscustomobject]@{ File = $f.FullName.Substring($repo.Length + 1); Line = $i + 1; Text = $s }
            }
        }
    }
}
if ($hits.Count -eq 0) {
    Write-Output 'SCAN_RESULT=EMPTY'
} else {
    Write-Output "SCAN_RESULT=$($hits.Count) hits"
    $hits | Format-Table -AutoSize -Wrap
}
