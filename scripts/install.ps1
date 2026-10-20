# 安装脚本：取最新 debug 产物装机并拉起主界面，用于 M0 及后续阶段真机验收
param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [string]$Serial = '',
    [switch]$Uninstall
)

$ErrorActionPreference = 'Stop'

$tcFile = Join-Path $Root 'toolchain.path'
$tc = (Get-Content -LiteralPath $tcFile -TotalCount 1).Trim()
$adb = Join-Path $tc 'android-sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { throw "未找到 adb：$adb" }

$apkDir = Join-Path $Root 'app\build\outputs\apk\debug'
$apk = Get-ChildItem -LiteralPath $apkDir -Filter '*.apk' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $apk) { throw "未找到 debug 产物，先运行 scripts\build.ps1" }

# 未指定序列号时取唯一在线设备；多设备在线必须显式传入
if (-not $Serial) {
    $lines = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' }
    if ($lines.Count -ne 1) { throw "在线设备数量为 $($lines.Count)，请用 -Serial 指定" }
    $Serial = ($lines[0] -split '\s+')[0]
}

$package = 'com.shuiyu.octoberrain'

if ($Uninstall) {
    & $adb -s $Serial uninstall $package
    return
}

& $adb -s $Serial install -r $apk.FullName
if ($LASTEXITCODE -ne 0) { throw "安装失败，退出码 $LASTEXITCODE" }

& $adb -s $Serial shell am start -n "$package/.MainActivity"
Write-Host "==> 已安装并启动: $($apk.Name) @ $Serial"
