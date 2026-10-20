# 构建脚本：读取工程根的 toolchain.path，显式设置 JAVA_HOME、ANDROID_HOME 与 GRADLE_USER_HOME 后调用 Gradle
param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [ValidateSet('debug', 'release')][string]$Variant = 'debug',
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'

# 读取工具链位置：工程根 toolchain.path 首行即工具链目录
$tcFile = Join-Path $Root 'toolchain.path'
if (-not (Test-Path $tcFile)) { throw "缺少 toolchain.path：$tcFile" }
$tc = (Get-Content -LiteralPath $tcFile -TotalCount 1).Trim()
if (-not (Test-Path $tc)) { throw "toolchain.path 指向的目录不存在：$tc" }

# 解析 JDK：工具链下 jdk 目录中取第一个含 bin\javac 的版本目录
$jdkRoot = Join-Path $tc 'jdk'
$jdk = Get-ChildItem -LiteralPath $jdkRoot -Directory |
    Where-Object { Test-Path (Join-Path $_.FullName 'bin\javac.exe') } |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $jdk) { throw "未在 $jdkRoot 找到 JDK" }

$sdk = Join-Path $tc 'android-sdk'
if (-not (Test-Path (Join-Path $sdk 'platforms'))) { throw "未在 $sdk 找到 Android SDK" }

$gradle = Join-Path $tc 'gradle\gradle-8.11.1\bin\gradle.bat'
if (-not (Test-Path $gradle)) { throw "未找到 Gradle：$gradle" }

# 环境变量是 Gradle 与 AGP 定位工具链的唯一入口，不写入全局环境
$env:JAVA_HOME = $jdk.FullName
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:GRADLE_USER_HOME = Join-Path $tc 'gradle-home'
$env:PATH = (Join-Path $jdk.FullName 'bin') + ';' + $env:PATH

# local.properties 与 toolchain.path 保持一致，供 IDE 与命令行共同使用
$localProps = Join-Path $Root 'local.properties'
$sdkLine = 'sdk.dir=' + ($sdk -replace '\\', '\\')
Set-Content -LiteralPath $localProps -Value $sdkLine -Encoding ASCII

$task = if ($Variant -eq 'release') { 'assembleRelease' } else { 'assembleDebug' }
$gradleArgs = @('-p', $Root, $task, '--console=plain')
if ($Offline) { $gradleArgs += '--offline' }

Write-Host "JDK        $($jdk.FullName)"
Write-Host "SDK        $sdk"
Write-Host "Gradle     $gradle"
Write-Host "任务       $task"
Write-Host ''

& $gradle @gradleArgs
if ($LASTEXITCODE -ne 0) { throw "Gradle 构建失败，退出码 $LASTEXITCODE" }

# 输出产物位置，便于脚本串联
$apkDir = Join-Path $Root "app\build\outputs\apk\$Variant"
$apk = Get-ChildItem -LiteralPath $apkDir -Filter '*.apk' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($apk) { Write-Host ''; Write-Host "==> 完成: $($apk.FullName)" }
