<#
.SYNOPSIS
    断言十雨桌面的微读融合实现满足一比一适配的结构约束。

.DESCRIPTION
    逐项检查组件条目数、负一屏模块标识、组件内是否残留自绘方法、内核 jar 摘要与
    文档记录是否一致、配置版本号与迁移分支是否齐备。每项输出通过或失败，任一失败
    时脚本以退出码 1 结束，便于接入门禁。

.PARAMETER OutputDirectory
    断言结果文件输出目录，默认为 _analysis\verify 下的时间戳子目录。

.PARAMETER KernelJar
    内核 jar 路径，默认为 libs\weread-kernel-1.3.2-k2.jar。

.EXAMPLE
    pwsh -File scripts\assert-weread-fidelity.ps1
#>
param(
    [string]$OutputDirectory = '',
    [string]$KernelJar = ''
)

$ErrorActionPreference = 'Stop'

# 以脚本所在目录的上级为工程根，保证在任意当前目录下执行结果一致
$root = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $OutputDirectory = Join-Path $root "_analysis\verify\fidelity-$stamp"
}
if ([string]::IsNullOrWhiteSpace($KernelJar)) {
    $KernelJar = Join-Path $root 'libs\weread-kernel-1.3.2-k2.jar'
}
if (-not (Test-Path -LiteralPath $OutputDirectory)) {
    New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
}

# 断言结果收集，每项为名称、期望、实际、是否通过
$results = New-Object System.Collections.Generic.List[object]

<#
    记录一条断言结果。

    @param name 断言名称
    @param expected 期望值
    @param actual 实际值
    @param passed 是否通过
#>
function Add-Assertion {
    param(
        [string]$Name,
        [string]$Expected,
        [string]$Actual,
        [bool]$Passed
    )

    $results.Add([pscustomobject]@{
            Name = $Name
            Expected = $Expected
            Actual = $Actual
            Passed = $Passed
        })
}

# 组件目录：读取 WidgetCatalog 的 id 声明，整体卡片以常量声明标识，单独计入
$catalogPath = Join-Path $root 'feature\widgets\src\main\java\com\shuiyu\octoberrain\feature\widgets\WidgetCatalog.kt'
$catalogText = Get-Content -LiteralPath $catalogPath -Raw
$catalogIds = @([regex]::Matches($catalogText, 'id\s*=\s*"([a-z_]+)"') | ForEach-Object { $_.Groups[1].Value })
if ($catalogText -match 'WEREAD_BOARD_WIDGET_ID') {
    $catalogIds += 'weread_board'
}
$catalogSet = ($catalogIds | Sort-Object -Unique)
Add-Assertion -Name '组件条目数' -Expected '6' -Actual "$($catalogSet.Count)" -Passed ($catalogSet.Count -eq 6)
$expectedCatalog = @('calendar', 'clock', 'image', 'placeholder', 'usage', 'weread_board')
$catalogMatches = (@($catalogSet | Where-Object { $_ -notin $expectedCatalog }).Count -eq 0) -and
    (@($expectedCatalog | Where-Object { $_ -notin $catalogSet }).Count -eq 0)
Add-Assertion -Name '组件标识集合' -Expected ($expectedCatalog -join ',') -Actual ($catalogSet -join ',') -Passed $catalogMatches

# 负一屏模块：读取 Models.kt 的默认模块列表
$modelsPath = Join-Path $root 'core\common\src\main\java\com\shuiyu\octoberrain\core\common\Models.kt'
$modelsText = Get-Content -LiteralPath $modelsPath -Raw
$defaultMatch = [regex]::Match($modelsText, 'DEFAULT_NEGATIVE_MODULES[^=]*=\s*listOf\(([^)]*)\)')
$defaultIds = if ($defaultMatch.Success) {
    [regex]::Matches($defaultMatch.Groups[1].Value, '"([a-z_]+)"') | ForEach-Object { $_.Groups[1].Value }
} else {
    @()
}
Add-Assertion -Name '负一屏默认模块' -Expected 'bookshelf,negative_files,bill,rank' -Actual ($defaultIds -join ',') -Passed (($defaultIds -join ',') -eq 'bookshelf,negative_files,bill,rank')

# 组件自绘方法：整体卡片不得再出现自绘分支
$boardPath = Join-Path $root 'feature\widgets\src\main\java\com\shuiyu\octoberrain\feature\widgets\WereadBoardWidget.kt'
$boardText = Get-Content -LiteralPath $boardPath -Raw
$selfDraw = [regex]::Matches($boardText, 'fun\s+(drawWeek|drawMonth|drawBook|drawNote|drawTodo|drawBars|drawHeatGrid|drawProgress|drawFooter|drawMenu)\b') |
    ForEach-Object { $_.Groups[1].Value }
Add-Assertion -Name '组件自绘方法' -Expected '0' -Actual "$(@($selfDraw).Count)" -Passed (@($selfDraw).Count -eq 0)

# 内核 jar 摘要：与同名 .sha256 记录一致
$jarSha = if (Test-Path -LiteralPath $KernelJar) {
    (Get-FileHash -LiteralPath $KernelJar -Algorithm SHA256).Hash.ToLower()
} else {
    ''
}
$shaFile = [System.IO.Path]::ChangeExtension($KernelJar, '.sha256')
$recorded = if (Test-Path -LiteralPath $shaFile) {
    ((Get-Content -LiteralPath $shaFile -Raw).Trim() -split '\s+')[0].ToLower()
} else {
    ''
}
Add-Assertion -Name '内核 jar 摘要' -Expected $recorded -Actual $jarSha -Passed ($jarSha -ne '' -and $jarSha -eq $recorded)

# 配置版本：必须已升到 9 且迁移分支存在
$versionMatch = [regex]::Match($modelsText, 'CURRENT_VERSION\s*=\s*(\d+)')
$version = if ($versionMatch.Success) { $versionMatch.Groups[1].Value } else { '' }
Add-Assertion -Name '配置版本' -Expected '9' -Actual $version -Passed ($version -eq '9')

$migrationPath = Join-Path $root 'core\data\src\main\java\com\shuiyu\octoberrain\core\data\ConfigMigration.kt'
$migrationText = Get-Content -LiteralPath $migrationPath -Raw
Add-Assertion -Name 'v8 到 v9 迁移' -Expected 'present' -Actual $(if ($migrationText -match 'migrateV8ToV9') { 'present' } else { 'missing' }) -Passed ($migrationText -match 'migrateV8ToV9')

# 输出结果
$failed = @($results | Where-Object { -not $_.Passed }).Count
$lines = @()
$lines += '# 微读融合结构断言'
$lines += ''
$lines += "工程根：$root"
$lines += "内核 jar：$KernelJar"
$lines += ''
$lines += '| 断言 | 期望 | 实际 | 结果 |'
$lines += '| --- | --- | --- | --- |'
foreach ($row in $results) {
    $flag = if ($row.Passed) { '通过' } else { '失败' }
    $lines += "| $($row.Name) | $($row.Expected) | $($row.Actual) | $flag |"
}
$lines += ''
$lines += "断言总数 $($results.Count)，失败 $failed"

$reportPath = Join-Path $OutputDirectory 'assertions.md'
$lines | Set-Content -LiteralPath $reportPath -Encoding utf8
$lines | ForEach-Object { Write-Output $_ }

if ($failed -gt 0) {
    exit 1
}
