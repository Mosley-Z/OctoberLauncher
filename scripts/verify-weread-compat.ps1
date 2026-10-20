# 微读兼容组合验证：四组补偿测试的前置脚本
# 职责：解析工具链、识别唯一在线设备、备份十雨配置、输出四组组合的执行清单与判据、生成报告文件
# 用法：pwsh -File scripts\verify-weread-compat.ps1 [-Case A|B|C|D|all] [-SkipBuild] [-DryRun] [-Serial 序列号]
# 说明：组合本身的安装、导入、截图与判定由人工按报告清单执行；脚本只做前置准备与清单输出
param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [ValidateSet('A', 'B', 'C', 'D', 'all')][string]$Case = 'all',
    [string]$Serial = '',
    [switch]$SkipBuild,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

# 微读安装包默认位置：组合 A 与 B 需要先安装微读并完成一次导入
$DefaultWereadApk = 'D:\WorkBuddyStation\WereadMoji\weread-widget\dist\weread-stats-1.3.2-beta.apk'
$TenrainPackage = 'com.shuiyu.octoberrain'
$WereadPackage = 'com.inkread.weekread'

# ---------------------------------------------------------------- 工具链解析

# 解析工具链目录：读取 toolchain.path，挑出含 bin\javac.exe 的 JDK，定位 SDK 与 Gradle
# 返回包含各路径的哈希表；任一路径缺失时抛出异常，由调用方决定是否终止
function Resolve-Toolchain {
    param([string]$RootDir)

    $tcFile = Join-Path $RootDir 'toolchain.path'
    if (-not (Test-Path -LiteralPath $tcFile)) { throw "缺少 toolchain.path：$tcFile" }
    $tc = (Get-Content -LiteralPath $tcFile -TotalCount 1).Trim()
    if (-not (Test-Path -LiteralPath $tc)) { throw "toolchain.path 指向的目录不存在：$tc" }

    $jdkRoot = Join-Path $tc 'jdk'
    $jdk = $null
    if (Test-Path -LiteralPath $jdkRoot) {
        $jdk = Get-ChildItem -LiteralPath $jdkRoot -Directory |
            Where-Object { Test-Path (Join-Path $_.FullName 'bin\javac.exe') } |
            Sort-Object Name -Descending | Select-Object -First 1
    }
    if (-not $jdk) { throw "在 $jdkRoot 下未找到含 bin\javac.exe 的 JDK" }

    $sdk = Join-Path $tc 'android-sdk'
    if (-not (Test-Path -LiteralPath $sdk)) { throw "未找到 Android SDK：$sdk" }
    $adb = Join-Path $sdk 'platform-tools\adb.exe'

    # Gradle 优先取工具链内的分发包，缺失时回落固定安装目录
    $gradle = $null
    $gradleRoot = Join-Path $tc 'gradle'
    if (Test-Path -LiteralPath $gradleRoot) {
        $gradle = Get-ChildItem -LiteralPath $gradleRoot -Directory |
            Where-Object { Test-Path (Join-Path $_.FullName 'bin\gradle.bat') } |
            Sort-Object Name -Descending | Select-Object -First 1
    }
    $gradleBat = ''
    if ($gradle) {
        $gradleBat = Join-Path $gradle.FullName 'bin\gradle.bat'
    } elseif (Test-Path -LiteralPath 'D:\Enviroment\gradle-8.11.1\bin\gradle.bat') {
        $gradleBat = 'D:\Enviroment\gradle-8.11.1\bin\gradle.bat'
    }

    return @{
        Toolchain       = $tc
        JdkHome         = $jdk.FullName
        AndroidHome     = $sdk
        GradleUserHome  = (Join-Path $tc 'gradle-home')
        Gradle          = $gradleBat
        Adb             = $adb
    }
}

# 设置构建相关环境变量，供 Gradle 与 javac 使用
function Set-BuildEnvironment {
    param([hashtable]$Toolchain)

    $env:JAVA_HOME = $Toolchain.JdkHome
    $env:ANDROID_HOME = $Toolchain.AndroidHome
    $env:ANDROID_SDK_ROOT = $Toolchain.AndroidHome
    $env:GRADLE_USER_HOME = $Toolchain.GradleUserHome
    $env:PATH = (Join-Path $Toolchain.JdkHome 'bin') + ';' + $env:PATH
}

# ---------------------------------------------------------------- 设备识别

# 识别唯一在线设备：未显式传入序列号时要求 adb devices 中恰好一台处于 device 状态
# 返回序列号字符串；零台或多台时返回空字符串，由调用方记录警告
function Get-UniqueDeviceSerial {
    param([string]$AdbPath, [string]$Explicit)

    if ($Explicit) { return $Explicit }
    if (-not (Test-Path -LiteralPath $AdbPath)) { return '' }

    $lines = @(& $AdbPath devices | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' })
    if ($lines.Count -eq 1) {
        return (($lines[0].Trim() -split '\s+')[0])
    }
    return ''
}

# 读取设备上是否安装指定包名，返回布尔值；设备不可用时返回 $null 表示未知
function Test-PackageInstalled {
    param([string]$AdbPath, [string]$DeviceSerial, [string]$PackageName)

    if (-not $DeviceSerial) { return $null }
    $listed = & $AdbPath -s $DeviceSerial shell "pm list packages $PackageName" 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }
    return [bool]($listed | Where-Object { $_ -match [regex]::Escape("package:$PackageName") })
}

# ---------------------------------------------------------------- 组合清单

# 返回四组组合的定义：编号、标题、前置条件、执行步骤、截图文件名、判据与日志关键字
# 判据文字均为人工核对依据，脚本不自动判定
function Get-CompatCases {
    param([string]$WereadApk)

    return @(
        @{
            Id = 'A'
            Title = '装微读 1.3.2 且已完成导入'
            Precondition = '微读 1.3.2 已安装，且在微读内完成一次书架与笔记导入'
            Steps = @(
                "adb install -r `"$WereadApk`"",
                '启动微读墨记，完成导入（书架、时长、笔记三部分均可见数据）',
                '返回桌面，右滑打开十雨负一屏',
                '负一屏切到墨单位置并停留三秒',
                '长按桌面空白处，检查组件「本周」形态'
            )
            Shots = @('compat-A-negative.png', 'compat-A-widget-week.png')
            Criteria = @(
                '负一屏墨单分区显示逐条书目：书名、作者、时长，存在价格数据时显示原价与收入',
                '组件本周形态显示统计分钟数，与微读侧本周时长一致',
                '两处均不出现导入引导、空态占位或错误提示'
            )
            LogKeywords = @('weread', 'WereadClient', 'kernel', 'FATAL EXCEPTION', 'ANR in com.shuiyu.octoberrain')
        },
        @{
            Id = 'B'
            Title = '装微读 1.3.2 但未导入'
            Precondition = '微读 1.3.2 已安装，但未完成导入（或导入后清除微读数据）'
            Steps = @(
                "adb install -r `"$WereadApk`"",
                '启动微读墨记，停留在未导入状态，不要执行导入',
                '返回桌面，打开十雨负一屏',
                '检查墨单、阅读资产、阅读指纹三个分区',
                '长按桌面空白处，检查组件「本周」形态'
            )
            Shots = @('compat-B-negative.png', 'compat-B-widget-week.png')
            Criteria = @(
                '负一屏三个分区均显示导入引导文案，指向在微读墨记中完成导入',
                '组件本周形态显示暂无数据文案，不显示错误的零值统计',
                '引导文案完整可见，不被截断或覆盖'
            )
            LogKeywords = @('weread', 'snapshot', 'missing', 'FATAL EXCEPTION', 'ANR in com.shuiyu.octoberrain')
        },
        @{
            Id = 'C'
            Title = '未装微读且独立同步过'
            Precondition = '微读已卸载，十雨内置同步至少成功一次（integrations.wereadSource 为 local）'
            Steps = @(
                "adb uninstall $WereadPackage",
                '打开十雨设置，确认微读同步状态为已同步',
                '返回桌面，打开十雨负一屏',
                '依次检查墨单、阅读资产、阅读指纹三个分区',
                '长按桌面空白处，检查组件「本周」形态'
            )
            Shots = @('compat-C-negative.png', 'compat-C-widget-week.png')
            Criteria = @(
                '负一屏三个分区直接显示内容，不依赖微读进程',
                '组件本周形态显示统计分钟数，数值与同步结果一致',
                '全程不出现导入引导文案，不出现空白分区'
            )
            LogKeywords = @('weread', 'WereadClient', 'write', 'FATAL EXCEPTION', 'ANR in com.shuiyu.octoberrain')
        },
        @{
            Id = 'D'
            Title = '未装微读且无数据'
            Precondition = '微读已卸载，十雨从未同步（files/weread 为空或不存在）'
            Steps = @(
                "adb uninstall $WereadPackage",
                "清除十雨微读数据：adb shell pm clear $TenrainPackage（会同时清除其他配置，执行前先确认备份）"
                '打开十雨负一屏',
                '依次检查墨单、阅读资产、阅读指纹三个分区',
                '长按桌面空白处，检查组件「本周」形态'
            )
            Shots = @('compat-D-negative.png', 'compat-D-widget-week.png')
            Criteria = @(
                '负一屏显示「在书架设置中填写密钥后显示」一类引导文案',
                '组件显示同类引导文案或明确的空态说明',
                '两处均不出现纯白空白分区，也不出现崩溃或错误提示'
            )
            LogKeywords = @('weread', 'key', 'empty', 'FATAL EXCEPTION', 'ANR in com.shuiyu.octoberrain')
        }
    )
}

# 按 -Case 过滤组合；all 表示四组全部输出
function Select-CompatCases {
    param([array]$Cases, [string]$Selection)

    if ($Selection -eq 'all') { return $Cases }
    return @($Cases | Where-Object { $_.Id -eq $Selection })
}

# ---------------------------------------------------------------- 报告输出

# 汇总报告行集合，同时写控制台
function Add-ReportLine {
    param([System.Collections.Generic.List[string]]$Report, [string]$Text)

    $null = $Report.Add($Text)
    Write-Host $Text
}

# 把工具链与设备信息写入报告
function Add-EnvironmentSection {
    param([System.Collections.Generic.List[string]]$Report, [hashtable]$Toolchain, [string]$DeviceSerial,
          [object]$TenrainInstalled, [object]$WereadInstalled)

    Add-ReportLine $Report '## 环境'
    Add-ReportLine $Report ''
    Add-ReportLine $Report "工具链目录：$($Toolchain.Toolchain)"
    Add-ReportLine $Report "JDK：$($Toolchain.JdkHome)"
    Add-ReportLine $Report "Android SDK：$($Toolchain.AndroidHome)"
    Add-ReportLine $Report "Gradle：$(if ($Toolchain.Gradle) { $Toolchain.Gradle } else { '未找到' })"
    Add-ReportLine $Report "Gradle 用户目录：$($Toolchain.GradleUserHome)"
    Add-ReportLine $Report "ADB：$($Toolchain.Adb)"
    Add-ReportLine $Report "设备序列号：$(if ($DeviceSerial) { $DeviceSerial } else { '未识别到唯一在线设备' })"
    Add-ReportLine $Report "十雨安装状态：$(Format-TriState $TenrainInstalled)"
    Add-ReportLine $Report "微读安装状态：$(Format-TriState $WereadInstalled)"
    Add-ReportLine $Report ''
}

# 把布尔或未知状态转成中文描述
function Format-TriState {
    param([object]$Value)

    if ($null -eq $Value) { return '未知' }
    if ($Value) { return '已安装' }
    return '未安装'
}

# 写四组组合的执行清单与判据
function Add-CaseSection {
    param([System.Collections.Generic.List[string]]$Report, [array]$Cases, [string]$OutDir)

    Add-ReportLine $Report '## 组合执行清单与判据'
    Add-ReportLine $Report ''
    foreach ($item in $Cases) {
        Add-ReportLine $Report "### 组合 $($item.Id)：$($item.Title)"
        Add-ReportLine $Report ''
        Add-ReportLine $Report "前置条件：$($item.Precondition)"
        Add-ReportLine $Report ''
        Add-ReportLine $Report '执行步骤：'
        $index = 1
        foreach ($step in $item.Steps) {
            Add-ReportLine $Report "$index. $step"
            $index++
        }
        Add-ReportLine $Report ''
        Add-ReportLine $Report '判据：'
        foreach ($rule in $item.Criteria) {
            Add-ReportLine $Report "- $rule"
        }
        Add-ReportLine $Report ''
        Add-ReportLine $Report '日志关键字：'
        Add-ReportLine $Report ('  ' + ($item.LogKeywords -join '、'))
        Add-ReportLine $Report ''
        Add-ReportLine $Report '截图路径：'
        foreach ($shot in $item.Shots) {
            Add-ReportLine $Report "  $(Join-Path $OutDir $shot)"
        }
        Add-ReportLine $Report ''
        Add-ReportLine $Report '结论：待执行'
        Add-ReportLine $Report ''
    }
}

# ---------------------------------------------------------------- 主流程

$cases = Select-CompatCases -Cases (Get-CompatCases -WereadApk $DefaultWereadApk) -Selection $Case
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'

if ($DryRun) {
    Write-Host '# 微读兼容组合验证（DryRun）'
    Write-Host ''
    try {
        $toolchain = Resolve-Toolchain -RootDir $Root
        Write-Host "工具链目录：$($toolchain.Toolchain)"
        Write-Host "JDK：$($toolchain.JdkHome)"
        Write-Host "Android SDK：$($toolchain.AndroidHome)"
        Write-Host "Gradle：$(if ($toolchain.Gradle) { $toolchain.Gradle } else { '未找到（构建步骤会跳过）' })"
        Write-Host "ADB：$($toolchain.Adb)"
        $device = Get-UniqueDeviceSerial -AdbPath $toolchain.Adb -Explicit $Serial
        Write-Host "设备序列号：$(if ($device) { $device } else { '未识别到唯一在线设备' })"
    } catch {
        Write-Host "工具链解析失败：$($_.Exception.Message)"
    }
    Write-Host ''
    Write-Host "计划输出目录：$(Join-Path $Root "_analysis\verify\$stamp")"
    Write-Host "配置备份目标：$(Join-Path $Root "_analysis\verify\$stamp\cfg-backup.xml")"
    Write-Host "构建步骤：$(if ($SkipBuild) { '跳过' } else { '执行 :app:assembleRelease' })"
    Write-Host ''
    foreach ($item in $cases) {
        Write-Host "组合 $($item.Id)：$($item.Title)"
        Write-Host "  前置条件：$($item.Precondition)"
        Write-Host '  执行步骤：'
        $index = 1
        foreach ($step in $item.Steps) {
            Write-Host "    $index. $step"
            $index++
        }
        Write-Host '  判据：'
        foreach ($rule in $item.Criteria) {
            Write-Host "    - $rule"
        }
        Write-Host ''
    }
    Write-Host 'DryRun 结束，未执行任何设备操作，未写入文件。'
    exit 0
}

# 真实执行：准备目录、备份配置、可选构建、写报告
$toolchain = Resolve-Toolchain -RootDir $Root
Set-BuildEnvironment -Toolchain $toolchain

if (-not (Test-Path -LiteralPath $toolchain.Adb)) {
    throw "未找到 adb：$($toolchain.Adb)"
}

$outDir = Join-Path $Root "_analysis\verify\$stamp"
New-Item -ItemType Directory -Path $outDir -Force | Out-Null

$device = Get-UniqueDeviceSerial -AdbPath $toolchain.Adb -Explicit $Serial
$tenrainInstalled = Test-PackageInstalled -AdbPath $toolchain.Adb -DeviceSerial $device -PackageName $TenrainPackage
$wereadInstalled = Test-PackageInstalled -AdbPath $toolchain.Adb -DeviceSerial $device -PackageName $WereadPackage

$report = New-Object System.Collections.Generic.List[string]
$failures = New-Object System.Collections.Generic.List[string]

Add-ReportLine $report "# 微读兼容组合验证报告 $stamp"
Add-ReportLine $report ''
Add-EnvironmentSection -Report $report -Toolchain $toolchain -DeviceSerial $device `
    -TenrainInstalled $tenrainInstalled -WereadInstalled $wereadInstalled

# 真机操作前备份配置：备份失败只记录警告，不中断清单输出
Add-ReportLine $report '## 配置备份'
Add-ReportLine $report ''
$cfgBackedUp = $false
if ($device) {
    $cfgLocal = Join-Path $outDir 'cfg-backup.xml'
    & $toolchain.Adb -s $device shell "run-as $TenrainPackage cat shared_prefs/octoberrain_config.xml" > $cfgLocal 2>$null
    if ((Test-Path -LiteralPath $cfgLocal) -and ((Get-Item -LiteralPath $cfgLocal).Length -gt 0)) {
        $cfgHash = (Get-FileHash -LiteralPath $cfgLocal -Algorithm SHA256).Hash.ToLower()
        $cfgBackedUp = $true
        Add-ReportLine $report "配置已备份：$cfgLocal"
        Add-ReportLine $report "备份摘要：$cfgHash"
    } else {
        Remove-Item -LiteralPath $cfgLocal -ErrorAction SilentlyContinue
        Add-ReportLine $report '配置备份：未取得配置文件（应用未安装或 run-as 不可用）'
    }
} else {
    Add-ReportLine $report '配置备份：无唯一在线设备，跳过'
}
Add-ReportLine $report ''

# 构建发布包：门禁需要可安装产物，构建日志留在验收目录
Add-ReportLine $report '## 构建'
Add-ReportLine $report ''
$apkPath = Join-Path $Root 'app\build\outputs\apk\release\app-release.apk'
if ($SkipBuild) {
    Add-ReportLine $report '构建步骤：已按 -SkipBuild 跳过'
} elseif (-not $toolchain.Gradle) {
    Add-ReportLine $report '构建步骤：未找到 Gradle，跳过'
    $failures.Add('未找到 Gradle，无法构建发布包')
} else {
    $buildLog = Join-Path $outDir 'build.log'
    Add-ReportLine $report "执行：$($toolchain.Gradle) -p `"$Root`" :app:assembleRelease --console=plain"
    & $toolchain.Gradle -p $Root ':app:assembleRelease' '--console=plain' 2>&1 |
        Tee-Object -FilePath $buildLog | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Add-ReportLine $report '构建失败，见 build.log'
        $failures.Add('构建失败，见 build.log')
    } else {
        Add-ReportLine $report '构建通过'
    }
}
if (Test-Path -LiteralPath $apkPath) {
    $apkHash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLower()
    $apkSize = (Get-Item -LiteralPath $apkPath).Length
    Add-ReportLine $report "发布包：$apkPath"
    Add-ReportLine $report "发布包字节数：$apkSize"
    Add-ReportLine $report "发布包摘要：$apkHash"
} else {
    Add-ReportLine $report "发布包：未找到 $apkPath"
}
Add-ReportLine $report ''

# 四组组合清单：只输出执行步骤与判据，不执行安装与导入
Add-CaseSection -Report $report -Cases $cases -OutDir $outDir

# 结论：前置条件齐备与否单独说明，组合结论留给人工填写
Add-ReportLine $report '## 结论'
Add-ReportLine $report ''
if ($cfgBackedUp) {
    Add-ReportLine $report "前置条件：设备 $device 已连接，配置已备份，可以按上表逐组执行。"
} elseif ($device) {
    Add-ReportLine $report "前置条件：设备 $device 已指定，配置未取得备份；执行组合前需人工确认配置备份方式。"
} else {
    Add-ReportLine $report '前置条件：未识别到唯一在线设备，请连接设备或使用 -Serial 指定后重新运行。'
}
if ($failures.Count -eq 0) {
    Add-ReportLine $report '脚本层面无失败项；四组组合的通过与否以人工核对判据后填写。'
} else {
    Add-ReportLine $report '脚本层面存在失败项：'
    foreach ($item in $failures) { Add-ReportLine $report "- $item" }
}

$reportPath = Join-Path $outDir 'compat-report.md'
Set-Content -LiteralPath $reportPath -Value $report -Encoding utf8

Write-Host ''
Write-Host "==> 报告：$reportPath"
Write-Host "==> 验收目录：$outDir"

if ($failures.Count -ne 0) { exit 1 }
exit 0
