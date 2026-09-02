param(
    [string]$SourcePath = (Join-Path $PSScriptRoot "..\dify_competition_consulting_test_kb.md"),
    [string]$OutputPath = (Join-Path $PSScriptRoot "..\dify_competition_documents"),
    [string]$NameSuffix = "",
    [switch]$Flat
)

$source = Get-Content -Raw -Encoding utf8 $SourcePath
$eventDirectory = [regex]::Match($source, '(?s)## 赛事目录\s*(.*?)\s*---\s*## 人才组：')
if (-not $eventDirectory.Success) {
    throw "无法读取赛事目录：$SourcePath"
}

$groups = @{}
$groupMatches = [regex]::Matches($source, '(?s)## 人才组：(?<name>[^\r\n]+)\s*(?<body>.*?)(?=\s*## 人才组：|\s*## 组队建议|\z)')
foreach ($match in $groupMatches) {
    # Keep the original field layout so Dify can return ready-to-render teacher and teammate records.
    $groups[$match.Groups['name'].Value.Trim()] = $match.Groups['body'].Value.Trim()
}

if (Test-Path $OutputPath) {
    Remove-Item -Recurse -Force $OutputPath
}
New-Item -ItemType Directory -Path $OutputPath | Out-Null

$events = [regex]::Matches($eventDirectory.Groups[1].Value, '(?s)### (?<name>[^\r\n]+)\s*(?<body>.*?)(?=\s*### |\z)')
foreach ($event in $events) {
    $name = $event.Groups['name'].Value.Trim()
    $body = $event.Groups['body'].Value
    $alias = [regex]::Match($body, '- 别称：(?<value>[^\r\n]+)').Groups['value'].Value.Trim()
    $direction = [regex]::Match($body, '- 方向：(?<value>[^\r\n]+)').Groups['value'].Value.Trim()
    $group = [regex]::Match($body, '- 推荐人才组：(?<value>[^\r\n]+)').Groups['value'].Value.Trim()
    $anchors = [regex]::Match($body, '- 检索锚点：(?<value>[^\r\n]+)').Groups['value'].Value.Trim()

    if (-not $groups.ContainsKey($group)) {
        throw "赛事 $name 未找到人才组 $group 的人员记录"
    }

    # Exact event anchors must dominate retrieval over the shared talent-group vocabulary.
    $prefix = @"
# $name 专属老师与队友资料

## 赛事唯一标识

赛事名称：$name
赛事名称：$name
赛事名称：$name
别称：$alias
比赛方向：$direction
所属人才组：$group
检索关键词：$anchors

本文件仅适用于“$name”。查询“$name 推荐指导老师”、“$name 推荐队友”或“$name 老师和队友”时，应只使用本文件的人员记录，不能混用其他赛事资料。

## $name 的人员资料

以下指导老师和推荐队友均归属赛事：$name。

"@
    $content = $prefix + $groups[$group] + @"

## 归属校验

本文件中的每一位指导老师和每一位推荐队友均对应赛事：$name。不得将本文件人员用于其他赛事的推荐。
"@
    if ($Flat) {
        # Dify treats Markdown headings and line breaks as hard chunk boundaries. A flat source keeps
        # the event, teachers, and teammates in the same retrieval chunk; the app prompt formats output.
        $flatPersonnel = ($groups[$group] -replace '[\r\n]+', ' ' -replace '\s{2,}', ' ' -replace '###\s*', '' -replace '\*\*', '').Trim()
        $content = '赛事名称：{0}。赛事名称：{0}。赛事名称：{0}。别称：{1}。比赛方向：{2}。所属人才组：{3}。检索关键词：{4}。本资料仅适用于赛事“{0}”。查询“{0} 推荐指导老师”、“{0} 推荐队友”或“{0} 老师和队友”时，只能使用以下人员资料，不得混用其他赛事资料。人员归属赛事：{0}。{5}。归属校验：本资料中的每一位指导老师和每一位推荐队友均对应赛事“{0}”。' -f $name, $alias, $direction, $group, $anchors, $flatPersonnel
    }
    $safeName = $name -replace '[\\/:*?"<>|]', '_'
    Set-Content -Path (Join-Path $OutputPath "$safeName$NameSuffix.md") -Value $content -Encoding utf8 -NoNewline
}

Write-Output "生成 $($events.Count) 份赛事联合文档：$OutputPath"
