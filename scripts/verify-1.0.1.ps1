# V1.0.1 验收门禁：构建产物、单元测试、真机安装、截图归档与结论文件
# 用法：pwsh -File scripts\verify-1.0.1.ps1 [-Serial 设备序列号] [-SkipBuild] [-Offline]
param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [string]$Serial = '',
    [switch]$SkipBuild,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'

# 工具链解析：沿用 build.ps1 的约定，显式设置环境变量后调用 Gradle
$tcFile = Join-Path $Root 'toolchain.path'
if (-not (Test-Path $tcFile)) { throw "缺少 toolchain.path：$tcFile" }
$tc = (Get-Content -LiteralPath $tcFile -TotalCount 1).Trim()
if (-not (Test-Path $tc)) { throw "toolchain.path 指向的目录不存在：$tc" }

$jdk = Get-ChildItem -LiteralPath (Join-Path $tc 'jdk') -Directory |
    Where-Object { Test-Path (Join-Path $_.FullName 'bin\javac.exe') } |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $jdk) { throw "未找到 JDK" }

$sdk = Join-Path $tc 'android-sdk'
$gradle = Join-Path $tc 'gradle\gradle-8.11.1\bin\gradle.bat'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
if (-not (Test-Path $gradle)) { throw "未找到 Gradle：$gradle" }

$env:JAVA_HOME = $jdk.FullName
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:GRADLE_USER_HOME = Join-Path $tc 'gradle-home'
$env:PATH = (Join-Path $jdk.FullName 'bin') + ';' + $env:PATH

$package = 'com.shuiyu.octoberrain'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outDir = Join-Path $Root "_analysis\verify\$stamp"
New-Item -ItemType Directory -Path $outDir -Force | Out-Null

$report = New-Object System.Collections.Generic.List[string]
$failures = New-Object System.Collections.Generic.List[string]

function Add-Line([string]$text) {
    $report.Add($text)
    Write-Host $text
}

Add-Line "# V1.0.1 验收记录 $stamp"
Add-Line ''
Add-Line "JDK：$($jdk.FullName)"
Add-Line "SDK：$sdk"
Add-Line ''

# 在线设备判定：未指定序列号时要求唯一设备，允许多设备场景显式传入
$deviceSerial = $Serial
if (-not $deviceSerial -and (Test-Path $adb)) {
    # 强制数组包裹，避免单行输出被 PowerShell 解包为字符串后按字符取索引
    $lines = @(& $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' })
    if ($lines.Count -eq 1) { $deviceSerial = ($lines[0].Trim() -split '\s+')[0] }
    if ($lines.Count -gt 1) { Add-Line "在线设备多于一台，跳过真机步骤；使用 -Serial 指定。" }
}
$hasDevice = [bool]$deviceSerial
Add-Line "设备：$(if ($hasDevice) { $deviceSerial } else { '未连接' })"
Add-Line ''

# 真机操作前备份配置，红线要求：任何注入或覆盖之前先留档
if ($hasDevice) {
    $cfgLocal = Join-Path $outDir 'cfg-backup.xml'
    & $adb -s $deviceSerial shell "run-as $package cat shared_prefs/octoberrain_config.xml" > $cfgLocal 2>$null
    if ((Test-Path $cfgLocal) -and ((Get-Item $cfgLocal).Length -gt 0)) {
        Add-Line "配置备份：$cfgLocal"
    } else {
        Remove-Item -LiteralPath $cfgLocal -ErrorAction SilentlyContinue
        Add-Line '配置备份：应用未安装或无配置，跳过'
    }
    Add-Line ''
}

# 构建与单元测试：门禁的硬条件，任一步失败即整体失败
if (-not $SkipBuild) {
    $args = @('-p', $Root, 'testDebugUnitTest', 'check', ':app:assembleDebug', ':app:assembleRelease', '--console=plain')
    if ($Offline) { $args += '--offline' }
    Add-Line '## 构建与测试'
    $buildLog = Join-Path $outDir 'build.log'
    & $gradle @args 2>&1 | Tee-Object -FilePath $buildLog | Out-Null
    if ($LASTEXITCODE -ne 0) {
        $failures.Add('构建或单元测试失败，见 build.log')
        Add-Line '构建或单元测试失败，见 build.log'
    } else {
        Add-Line '构建与单元测试通过'
    }
    Add-Line ''
}

# 产物归档与摘要：apk、mapping 与校验和一并进入验收目录
$artifacts = @(
    (Join-Path $Root 'app\build\outputs\apk\debug\app-debug.apk'),
    (Join-Path $Root 'app\build\outputs\apk\release\app-release.apk'),
    (Join-Path $Root 'app\build\outputs\mapping\release\mapping.txt')
)
Add-Line '## 产物'
$checksumLines = New-Object System.Collections.Generic.List[string]
foreach ($artifact in $artifacts) {
    if (-not (Test-Path $artifact)) {
        $failures.Add("缺少产物：$artifact")
        Add-Line "缺少产物：$artifact"
        continue
    }
    $name = Split-Path -Leaf $artifact
    Copy-Item -LiteralPath $artifact -Destination (Join-Path $outDir $name) -Force
    $hash = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLower()
    $size = (Get-Item -LiteralPath $artifact).Length
    $checksumLines.Add("$hash  $name")
    Add-Line "- $name  $size 字节  sha256=$hash"
}
Set-Content -LiteralPath (Join-Path $outDir 'checksums.sha256') -Value $checksumLines -Encoding utf8
Add-Line ''

# 真机步骤：安装 debug 包、启动主界面、按页面顺序截图、抓内存与启动日志
if ($hasDevice) {
    Add-Line '## 真机验证'
    # 冷启动与内存取发布包数据，发布包经过 R8 优化；缺失时回落调试包
    $installApk = Join-Path $outDir 'app-release.apk'
    if (-not (Test-Path $installApk)) { $installApk = Join-Path $outDir 'app-debug.apk' }
    if (Test-Path $installApk) {
        & $adb -s $deviceSerial install -r $installApk | Out-Null
        if ($LASTEXITCODE -ne 0) {
            $failures.Add("安装失败：$(Split-Path -Leaf $installApk)")
            Add-Line "安装失败：$(Split-Path -Leaf $installApk)"
        }
        # 唤醒并解锁屏幕，息屏状态下启动与截图都会停在锁屏或黑屏
        & $adb -s $deviceSerial shell input keyevent KEYCODE_WAKEUP | Out-Null
        Start-Sleep -Milliseconds 600
        & $adb -s $deviceSerial shell wm dismiss-keyguard | Out-Null
        Start-Sleep -Milliseconds 600
        # 清空旧日志后重启应用，冷启动耗时取 ActivityTaskManager 的 Displayed 行
        & $adb -s $deviceSerial logcat -c
        & $adb -s $deviceSerial shell am force-stop $package | Out-Null
        Start-Sleep -Milliseconds 800
        & $adb -s $deviceSerial shell am start -n "$package/.MainActivity" | Out-Null
        Start-Sleep -Seconds 5
        $displayed =
            & $adb -s $deviceSerial logcat -d -s ActivityTaskManager:I |
                Select-String -Pattern 'Displayed com\.shuiyu\.octoberrain' |
                Select-Object -First 1
        $displayedMs = $null
        if ($displayed) {
            $match = [regex]::Match($displayed.Line, '\+(\d+)ms')
            if ($match.Success) { $displayedMs = [int]$match.Groups[1].Value }
        }
        if ($displayedMs) {
            Add-Line "- 冷启动 Displayed：$displayedMs 毫秒"
            if ($displayedMs -gt 1500) { $failures.Add("冷启动超过 1500 毫秒：$displayedMs") }
        } else {
            Add-Line '- 冷启动耗时未取得'
        }
        Start-Sleep -Seconds 2

        # 逐页截图：首页、负一屏、负一屏设置与桌面图标长按菜单
        # commands 直接交给 adb shell，避免数组嵌套被 PowerShell 展平后丢失参数
        $shots = @(
            @{ name = 'shot-01-home'; commands = @() },
            @{ name = 'shot-02-negative'; commands = @('input swipe 40 420 430 420 250') },
            @{ name = 'shot-03-negative-settings'; commands = @('input tap 446 70') },
            @{
                name = 'shot-04-app-menu'
                commands = @(
                    'input keyevent 4',
                    'input swipe 430 420 40 420 250',
                    'input swipe 240 740 240 740 900'
                )
            }
        )
        foreach ($shot in $shots) {
            foreach ($cmd in $shot.commands) {
                & $adb -s $deviceSerial shell $cmd | Out-Null
                Start-Sleep -Milliseconds 900
            }
            $png = Join-Path $outDir ("$($shot.name).png")
            & $adb -s $deviceSerial exec-out screencap -p > $png 2>$null
            if ((Test-Path $png) -and ((Get-Item $png).Length -gt 1024)) {
                Add-Line "- 截图 $($shot.name).png  $((Get-Item $png).Length) 字节"
            } else {
                $failures.Add("截图失败：$($shot.name)")
                Add-Line "截图失败：$($shot.name)"
            }
        }

        # 内存与崩溃：只保留关键行，完整日志留在 accept-log.txt
        $mem = & $adb -s $deviceSerial shell dumpsys meminfo $package | Select-String -Pattern 'TOTAL PSS|TOTAL RSS' | Select-Object -First 2
        foreach ($line in $mem) { Add-Line "- 内存：$($line.ToString().Trim())" }
        $logPath = Join-Path $outDir 'logcat.txt'
        & $adb -s $deviceSerial logcat -d > $logPath 2>$null
        # 过滤 adb input 命令自身的运行时日志，只保留应用进程与崩溃关键字
        $crash = Get-Content -LiteralPath $logPath -ErrorAction SilentlyContinue |
            Select-String -Pattern 'FATAL EXCEPTION|ANR in com\.shuiyu\.octoberrain|E OctoberRainApp' |
            Select-Object -First 20
        if ($crash) {
            Add-Line '- 启动日志关键行：'
            foreach ($line in $crash) { Add-Line "  $($line.ToString().Trim())" }
        } else {
            Add-Line '- 启动日志无崩溃关键字'
        }
        $fatal = Get-Content -LiteralPath $logPath -ErrorAction SilentlyContinue |
            Select-String -Pattern 'FATAL EXCEPTION' | Select-Object -First 1
        if ($fatal) { $failures.Add('启动出现 FATAL EXCEPTION') }
    }
    Add-Line ''
}

# 结论文件：失败项汇总为空即判定通过
Add-Line '## 结论'
if ($failures.Count -eq 0) {
    Add-Line '通过'
} else {
    Add-Line '失败'
    foreach ($item in $failures) { Add-Line "- $item" }
}

Set-Content -LiteralPath (Join-Path $outDir 'report.md') -Value $report -Encoding utf8
Write-Host ''
Write-Host "==> 验收目录：$outDir"

if ($failures.Count -ne 0) { exit 1 }
