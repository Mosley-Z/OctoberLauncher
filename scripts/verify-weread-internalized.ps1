<#
.SYNOPSIS
    断言微读墨记内化改造满足结构与交互约束。

.DESCRIPTION
    逐项检查导航目标、待办契约、卡片交互、负一屏无详情页、内核产物与模块接入。
    每项输出通过或失败，任一失败时脚本以退出码 1 结束，便于接入门禁。

.PARAMETER OutputDirectory
    断言结果文件输出目录，默认为 _analysis\verify 下的时间戳子目录。

.PARAMETER KernelJar
    内核 jar 路径，默认为 libs\weread-kernel-1.3.2-k5.jar。

.EXAMPLE
    pwsh -File scripts\verify-weread-internalized.ps1
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
    $OutputDirectory = Join-Path $root "_analysis\verify\internalized-$stamp"
}
if ([string]::IsNullOrWhiteSpace($KernelJar)) {
    $KernelJar = Join-Path $root 'libs\weread-kernel-1.3.2-k5.jar'
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

<#
    读取源码文本，文件缺失时返回空串。

    @param path 文件路径
    @return 文件内容
#>
function Read-Source {
    param([string]$Path)

    if (Test-Path -LiteralPath $Path) {
        return (Get-Content -LiteralPath $Path -Raw)
    }
    return ''
}

# 一、导航目标：微读墨记内置应用页必须有目标与标签
$contractsPath = Join-Path $root 'core\common\src\main\java\com\shuiyu\octoberrain\core\common\Contracts.kt'
$contractsText = Read-Source $contractsPath
$hasAppDestination = $contractsText -match 'data object WereadApp\s*:\s*Destination'
Add-Assertion -Name '内置应用页目标' -Expected 'present' -Actual $(if ($hasAppDestination) { 'present' } else { 'missing' }) -Passed $hasAppDestination
$hasAppLabel = $contractsText -match 'Destination\.WereadApp\s+to\s+"微读墨记"'
Add-Assertion -Name '内置应用页标签' -Expected 'present' -Actual $(if ($hasAppLabel) { 'present' } else { 'missing' }) -Passed $hasAppLabel

# 二、待办契约：接口与装配字段必须存在
$todoHostPath = Join-Path $root 'core\common\src\main\java\com\shuiyu\octoberrain\core\common\WereadTodoHost.kt'
$todoHostText = Read-Source $todoHostPath
$todoMethods = @('fun list', 'fun add', 'fun toggle', 'fun remove', 'fun move', 'fun update')
$missingMethods = @($todoMethods | Where-Object { $todoHostText -notmatch [regex]::Escape($_) })
Add-Assertion -Name '待办接口方法' -Expected '6' -Actual "$(6 - $missingMethods.Count)" -Passed ($missingMethods.Count -eq 0)
$hasTodoService = $contractsText -match 'var wereadTodos\s*:\s*WereadTodoHost\?'
Add-Assertion -Name '待办服务装配字段' -Expected 'present' -Actual $(if ($hasTodoService) { 'present' } else { 'missing' }) -Passed $hasTodoService

$todoStorePath = Join-Path $root 'core\weread-sync\src\main\java\com\shuiyu\octoberrain\core\wereadsync\WereadTodoStore.kt'
$todoStoreText = Read-Source $todoStorePath
$hasListKey = $todoStoreText -match 'KEY_LIST:\s*String\s*=\s*"todo_list"'
$hasNextKey = $todoStoreText -match 'KEY_NEXT_ID:\s*String\s*=\s*"todo_next_id"'
Add-Assertion -Name '待办存储键名' -Expected 'todo_list,todo_next_id' -Actual "$(if ($hasListKey) { 'todo_list' } else { '-' }),$(if ($hasNextKey) { 'todo_next_id' } else { '-' })" -Passed ($hasListKey -and $hasNextKey)
$kernelKeys = @('"content"', '"date"', '"min"', '"done"', '"order"')
$missingKeys = @($kernelKeys | Where-Object { $todoStoreText -notmatch [regex]::Escape($_) })
Add-Assertion -Name '待办字段与内核一致' -Expected '5' -Actual "$(5 - $missingKeys.Count)" -Passed ($missingKeys.Count -eq 0)

# 三、卡片交互：抬头按钮删除、形态池字段存在、待办勾选接入
$modelsPath = Join-Path $root 'core\common\src\main\java\com\shuiyu\octoberrain\core\common\Models.kt'
$modelsText = Read-Source $modelsPath
$hasPool = $modelsText -match 'wereadBoardPoolMask'
Add-Assertion -Name '形态池字段' -Expected 'present' -Actual $(if ($hasPool) { 'present' } else { 'missing' }) -Passed $hasPool
$deskFields = @('assetShelfOnly', 'assetFinishedOnly', 'hiddenIds', 'fpFontPx')
$missingDesk = @($deskFields | Where-Object { $modelsText -notmatch [regex]::Escape($_) })
Add-Assertion -Name '墨台设置字段' -Expected '4' -Actual "$(4 - $missingDesk.Count)" -Passed ($missingDesk.Count -eq 0)

$boardPath = Join-Path $root 'feature\widgets\src\main\java\com\shuiyu\octoberrain\feature\widgets\WereadBoardWidget.kt'
$boardText = Read-Source $boardPath
$openDeskCount = ([regex]::Matches($boardText, '打开墨台')).Count
Add-Assertion -Name '卡片抬头打开墨台' -Expected '0' -Actual "$openDeskCount" -Passed ($openDeskCount -eq 0)
$hasTodoToggle = $boardText -match 'wereadTodos'
Add-Assertion -Name '卡片待办勾选接入' -Expected 'present' -Actual $(if ($hasTodoToggle) { 'present' } else { 'missing' }) -Passed $hasTodoToggle

# 四、负一屏：不得再从分区发起页面跳转
$negativeFiles = @(Get-ChildItem -LiteralPath (Join-Path $root 'feature\content\src\main\java\com\shuiyu\octoberrain\feature\content\ui') -Recurse -Filter '*.kt' -ErrorAction SilentlyContinue)
$negativePath = Join-Path $root 'feature\home\src\main\java\com\shuiyu\octoberrain\feature\home\NegativeScreen.kt'
if (Test-Path -LiteralPath $negativePath) {
    $negativeFiles += Get-Item -LiteralPath $negativePath
}
$routingHits = 0
foreach ($file in $negativeFiles) {
    $text = Get-Content -LiteralPath $file.FullName -Raw
    $routingHits += ([regex]::Matches($text, 'open\(\s*Destination\.')).Count
}
Add-Assertion -Name '负一屏详情页跳转' -Expected '0' -Actual "$routingHits" -Passed ($routingHits -eq 0)

# 五、内核产物：jar 摘要与记录一致，且版本标识为 k5
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
$jarName = Split-Path -Leaf $KernelJar
Add-Assertion -Name '内核版本标识' -Expected 'k5' -Actual $jarName -Passed ($jarName -match 'k5\.jar$')

# 六、模块接入：内置 App 模块目录、构建引用与反射名单
$appModulePath = Join-Path $root 'feature\weread-app'
Add-Assertion -Name '内置应用模块目录' -Expected 'present' -Actual $(if (Test-Path -LiteralPath $appModulePath) { 'present' } else { 'missing' }) -Passed (Test-Path -LiteralPath $appModulePath)
$settingsText = Read-Source (Join-Path $root 'settings.gradle.kts')
$hasInclude = $settingsText -match ':feature:weread-app'
Add-Assertion -Name '构建模块声明' -Expected 'present' -Actual $(if ($hasInclude) { 'present' } else { 'missing' }) -Passed $hasInclude
$applicationPath = Join-Path $root 'app\src\main\java\com\shuiyu\octoberrain\OctoberRainApp.kt'
$applicationText = Read-Source $applicationPath
$hasModuleName = $applicationText -match 'feature\.wereadapp\.WereadAppModule'
Add-Assertion -Name '模块装载名单' -Expected 'present' -Actual $(if ($hasModuleName) { 'present' } else { 'missing' }) -Passed $hasModuleName

# 输出结果
$failed = @($results | Where-Object { -not $_.Passed }).Count
$lines = @()
$lines += '# 微读墨记内化结构断言'
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
