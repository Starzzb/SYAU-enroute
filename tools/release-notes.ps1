<#
.SYNOPSIS
    生成「沈农在途」的 GitHub Release 正文（发版说明）。

.DESCRIPTION
    结构固定三段：
        第 1 行    一句话摘要（取 CHANGELOG.md 里 `## <Tag>` 小节正文的首句）
        ## 改了什么  `git log <PrevTag>..<Tag>` 的提交标题（去掉 release: 发版提交本身）
        ## 详细     CHANGELOG.md 对应小节的正文

    只读仓库，不 commit、不 tag、不 push；最后把 `gh release create ...` 这条命令
    打印出来（不执行），由人确认后再跑。

.PARAMETER Tag
    要生成发版说明的版本标签，例如 m31（必须已经打上 tag）。

.PARAMETER PrevTag
    上一个版本标签。省略时自动用 `git describe --tags --abbrev=0 <Tag>^` 取。

.PARAMETER OutFile
    输出文件路径；相对路径按仓库根解析。默认 `.tmp/release-notes-<Tag>.md`。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools\release-notes.ps1 -Tag m31

.NOTES
    兼容 Windows PowerShell 5.1（不使用 ?? / 三元 / -Parallel 等 7.x 语法）。
    输出文件为 UTF-8（无 BOM），中文注释按 UTF-8 保存。
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Tag,

    [string]$PrevTag = '',

    [string]$OutFile = ''
)

$ErrorActionPreference = 'Stop'

# 让 git 的中文输出按 UTF-8 解码（PS 5.1 下原生命令的输出编码看这个）
try {
    [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
} catch {
    # 某些宿主不允许改控制台编码；读文件仍走 -Encoding UTF8，不影响结果
}

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrEmpty($repoRoot)) {
    $repoRoot = (Get-Location).Path
}
$changelogPath = Join-Path $repoRoot 'CHANGELOG.md'
$gradlePath = Join-Path $repoRoot 'app\build.gradle.kts'

if (-not (Test-Path -LiteralPath $changelogPath)) {
    throw "找不到 CHANGELOG.md：$changelogPath"
}

# ── git 调用（只读）────────────────────────────────────────────
function Invoke-Git {
    param([string[]]$GitArgs)

    # 2>&1 在 PS 5.1 + $ErrorActionPreference=Stop 下会把 stderr 当终止错误，
    # 这里临时放宽，靠退出码判断成败。
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $raw = & git -C $repoRoot @GitArgs 2>&1
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prev
    }

    $lines = @()
    foreach ($item in $raw) {
        if ($null -ne $item) { $lines += $item.ToString() }
    }
    if ($code -ne 0) {
        throw ("git " + ($GitArgs -join ' ') + " 退出码 $code：" + ($lines -join "`n"))
    }
    return $lines
}

function Trim-BlankEdges {
    param([string[]]$Lines)

    $list = @()
    if ($null -ne $Lines) { $list = @($Lines) }
    $start = 0
    while ($start -lt $list.Count -and $list[$start].Trim() -eq '') { $start++ }
    $end = $list.Count - 1
    while ($end -ge $start -and $list[$end].Trim() -eq '') { $end-- }
    if ($end -lt $start) { return @() }
    return @($list[$start..$end])
}

# ── 1. 从 CHANGELOG.md 取「一句话摘要」与「详细正文」─────────────
$changelog = @(Get-Content -LiteralPath $changelogPath -Encoding UTF8)
$tagPattern = '^##\s+' + [regex]::Escape($Tag) + '(?![0-9A-Za-z])'
$headingIndex = -1
for ($i = 0; $i -lt $changelog.Count; $i++) {
    if ($changelog[$i] -match $tagPattern) {
        $headingIndex = $i
        break
    }
}

$sectionBody = @()
$headingLine = ''
if ($headingIndex -ge 0) {
    $headingLine = $changelog[$headingIndex]
    $collected = @()
    for ($j = $headingIndex + 1; $j -lt $changelog.Count; $j++) {
        if ($changelog[$j] -match '^##\s') { break }
        $collected += $changelog[$j]
    }
    $sectionBody = Trim-BlankEdges -Lines $collected
    Write-Host "CHANGELOG：找到 $Tag 小节（第 $($headingIndex + 1) 行，正文 $($sectionBody.Count) 行）"
} else {
    Write-Warning "CHANGELOG.md 里没有找到「## $Tag」小节，摘要与详细都留占位"
}

# 一句话摘要：小节正文第一段非空、非列表行的首句；取不到就留 TODO
$summary = 'TODO 一句话'
foreach ($line in $sectionBody) {
    $text = $line.Trim()
    if ($text -eq '') { continue }
    if ($text.StartsWith('-') -or $text.StartsWith('*') -or $text.StartsWith('·')) { break }
    $stop = $text.IndexOf('。')
    if ($stop -ge 0) {
        $text = $text.Substring(0, $stop + 1)
    }
    $summary = $text
    break
}

# versionName：优先取小节标题里的 `· 0.7.4+m31`，其次 build.gradle.kts，最后退回 Tag
$versionName = ''
if ($headingLine -ne '') {
    $match = [regex]::Match($headingLine, '[·•]\s*([^\s（(]+)')
    if ($match.Success) {
        $versionName = $match.Groups[1].Value.Trim()
    }
}
if ($versionName -eq '' -and (Test-Path -LiteralPath $gradlePath)) {
    $gradleLines = @(Get-Content -LiteralPath $gradlePath -Encoding UTF8)
    foreach ($line in $gradleLines) {
        $match = [regex]::Match($line, 'versionName\s*=\s*"([^"]+)"')
        if ($match.Success) {
            $versionName = $match.Groups[1].Value.Trim()
            break
        }
    }
}
if ($versionName -eq '') { $versionName = $Tag }

# ── 2. 上一个 tag ─────────────────────────────────────────────
if ($PrevTag -eq '') {
    $prev = $null
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $rawPrev = & git -C $repoRoot describe --tags --abbrev=0 ($Tag + '^') 2>$null
        if ($LASTEXITCODE -eq 0 -and $null -ne $rawPrev) {
            $prev = ($rawPrev | Select-Object -First 1).ToString().Trim()
        }
    } finally {
        $ErrorActionPreference = $prevEap
    }
    if ($null -ne $prev -and $prev -ne '') {
        $PrevTag = $prev
    }
}

# ── 3. 提交标题（过滤发版提交本身）────────────────────────────
# 用 tformat + %x20 拼空格：PS 5.1 传原生参数时带空格的字符串会被拆开
if ($PrevTag -ne '') {
    Write-Host "提交范围：$PrevTag..$Tag"
    $logLines = Invoke-Git @('log', '--no-merges', '--pretty=tformat:-%x20%s', ($PrevTag + '..' + $Tag))
} else {
    Write-Warning "取不到上一个 tag，改为列出 $Tag 可达的全部提交"
    $logLines = Invoke-Git @('log', '--no-merges', '--pretty=tformat:-%x20%s', $Tag)
}

$commits = @()
foreach ($line in $logLines) {
    $text = $line.Trim()
    if ($text -eq '') { continue }
    # 发版提交本身不列（release: / release：）
    if ($text -match '^-\s+release[:：]') { continue }
    $commits += $text
}

# ── 4. 拼正文 ─────────────────────────────────────────────────
$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine($summary)
[void]$sb.AppendLine('')
[void]$sb.AppendLine('## 改了什么')
[void]$sb.AppendLine('')
if ($commits.Count -gt 0) {
    foreach ($commit in $commits) {
        [void]$sb.AppendLine($commit)
    }
} else {
    [void]$sb.AppendLine('- （本版没有可列的提交标题）')
}
[void]$sb.AppendLine('')
[void]$sb.AppendLine('## 详细')
[void]$sb.AppendLine('')
if ($sectionBody.Count -gt 0) {
    foreach ($line in $sectionBody) {
        [void]$sb.AppendLine($line)
    }
} else {
    [void]$sb.AppendLine('TODO 详细内容（CHANGELOG.md 缺本版小节）')
}

# ── 5. 落盘 ───────────────────────────────────────────────────
if ($OutFile -eq '') {
    $OutFile = '.tmp/release-notes-' + $Tag + '.md'
}
if ([System.IO.Path]::IsPathRooted($OutFile)) {
    $outPath = $OutFile
} else {
    $outPath = Join-Path $repoRoot $OutFile
}
$outDir = Split-Path -Parent $outPath
if ($outDir -ne '' -and -not (Test-Path -LiteralPath $outDir)) {
    [void][System.IO.Directory]::CreateDirectory($outDir)
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($outPath, $sb.ToString(), $utf8NoBom)

# 打印给人看的相对路径（命令行里要用）
$displayOut = $OutFile
$rootPrefix = $repoRoot.TrimEnd('\') + '\'
if ($outPath.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    $displayOut = $outPath.Substring($rootPrefix.Length)
}

Write-Host ''
Write-Host "已生成：$displayOut（$($commits.Count) 条提交，摘要：$summary）"
Write-Host ''
Write-Host "gh release create $Tag releases\SYAU-enroute-$Tag.apk --title `"$Tag · $versionName`" --notes-file $displayOut"
