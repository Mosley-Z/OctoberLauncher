# 双设备界面走查：逐屏截图并归档
#
# 用法：
#   pwsh -NoProfile -File scripts\check-devices.ps1 -Serial SA4B03CN2608081096X -OutDir _analysis\verify\devices
#
# 参数：
#   -Serial  设备序列号
#   -OutDir  截图输出目录，自动建子目录 <Serial>
#   -Tag     目录后缀，便于区分多轮
#
# 说明：坐标按屏幕宽高比例换算，兼容 480x800 与 552x768 两档尺寸。

param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$OutDir = "_analysis\verify\devices",
    [string]$Tag = ""
)

$ErrorActionPreference = "Stop"

# 解析工具链路径
$root = (Get-Location).Path
$toolchain = (Get-Content (Join-Path $root "toolchain.path")).Trim()
$adb = Join-Path $toolchain "android-sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "找不到 adb：$adb" }

$suffix = if ($Tag) { "-$Tag" } else { "" }
$target = Join-Path $root (Join-Path $OutDir "$Serial$suffix")
New-Item -ItemType Directory -Force -Path $target | Out-Null

# 保持亮屏，避免墨水屏休眠导致截图全黑
& $adb -s $Serial shell svc power stayon true | Out-Null
& $adb -s $Serial shell input keyevent KEYCODE_WAKEUP | Out-Null
& $adb -s $Serial shell wm dismiss-keyguard | Out-Null
Start-Sleep -Milliseconds 800

# 读取屏幕尺寸与版本
$sizeText = (& $adb -s $Serial shell wm size) -join " "
$densityText = (& $adb -s $Serial shell wm density) -join " "
$versionText = (& $adb -s $Serial shell dumpsys package com.shuiyu.octoberrain |
    Select-String -Pattern "versionName=" | Select-Object -First 1).Line
$match = [regex]::Match($sizeText, "(\d+)x(\d+)")
if (-not $match.Success) { throw "无法解析屏幕尺寸：$sizeText" }
$width = [int]$match.Groups[1].Value
$height = [int]$match.Groups[2].Value

# 按比例换算坐标
function PX([double]$ratio) { [int][math]::Round($width * $ratio) }
function PY([double]$ratio) { [int][math]::Round($height * $ratio) }

# 截图并记录文件名
$index = New-Object System.Collections.Generic.List[string]
function Shot([string]$name) {
    $file = Join-Path $target "$name.png"
    cmd /c "`"$adb`" -s $Serial exec-out screencap -p > `"$file`""
    $length = (Get-Item $file).Length
    $index.Add("- $name ：$length 字节")
    Write-Host ("截图 {0} {1} 字节" -f $name, $length)
}

# 回到桌面首页
& $adb -s $Serial shell am start -n "com.shuiyu.octoberrain/.MainActivity" | Out-Null
Start-Sleep -Seconds 4
Shot "01-home"

# 第二页图标
& $adb -s $Serial shell input swipe (PX 0.9) (PY 0.8) (PX 0.1) (PY 0.8) 220 | Out-Null
Start-Sleep -Seconds 2
Shot "02-page2"

# 右滑回首页再进负一屏
& $adb -s $Serial shell input swipe (PX 0.1) (PY 0.8) (PX 0.9) (PY 0.8) 220 | Out-Null
Start-Sleep -Seconds 2
& $adb -s $Serial shell input swipe (PX 0.02) (PY 0.9) (PX 0.95) (PY 0.9) 300 | Out-Null
Start-Sleep -Seconds 5
Shot "03-negative"

# 负一屏继续下滑看内核分区
for ($i = 0; $i -lt 4; $i++) {
    & $adb -s $Serial shell input swipe (PX 0.5) (PY 0.85) (PX 0.5) (PY 0.2) 300 | Out-Null
    Start-Sleep -Milliseconds 900
}
Shot "04-negative-kernel"

# 回到顶部并进设置态
for ($i = 0; $i -lt 8; $i++) {
    & $adb -s $Serial shell input swipe (PX 0.5) (PY 0.2) (PX 0.5) (PY 0.85) 220 | Out-Null
    Start-Sleep -Milliseconds 500
}
& $adb -s $Serial shell input tap (PX 0.07) (PY 0.09) | Out-Null
Start-Sleep -Seconds 3
Shot "05-negative-settings"

# 退出负一屏回桌面
& $adb -s $Serial shell input keyevent 4 | Out-Null
Start-Sleep -Milliseconds 800
& $adb -s $Serial shell input keyevent 4 | Out-Null
Start-Sleep -Seconds 2

# 桌面设置首页
& $adb -s $Serial shell input tap (PX 0.2) (PY 0.85) | Out-Null
Start-Sleep -Seconds 3
Shot "06-settings-home"

# 桌面偏好
& $adb -s $Serial shell input tap (PX 0.74) (PY 0.27) | Out-Null
Start-Sleep -Seconds 3
Shot "07-desk-prefs"

# 返回设置首页，进微读墨记
& $adb -s $Serial shell input keyevent 4 | Out-Null
Start-Sleep -Seconds 2
& $adb -s $Serial shell input tap (PX 0.25) (PY 0.23) | Out-Null
Start-Sleep -Seconds 5
Shot "08-app-reading"

# 阅读页四段页签
& $adb -s $Serial shell input tap (PX 0.42) (PY 0.31) | Out-Null
Start-Sleep -Seconds 3
Shot "09-app-month"
& $adb -s $Serial shell input tap (PX 0.66) (PY 0.31) | Out-Null
Start-Sleep -Seconds 3
Shot "10-app-book"
& $adb -s $Serial shell input tap (PX 0.88) (PY 0.31) | Out-Null
Start-Sleep -Seconds 3
Shot "11-app-insight"

# 主容器页签：设置、实验室、待办
& $adb -s $Serial shell input tap (PX 0.42) (PY 0.08) | Out-Null
Start-Sleep -Seconds 3
Shot "12-app-settings"
& $adb -s $Serial shell input tap (PX 0.64) (PY 0.08) | Out-Null
Start-Sleep -Seconds 3
Shot "13-app-lab"
& $adb -s $Serial shell input tap (PX 0.86) (PY 0.08) | Out-Null
Start-Sleep -Seconds 3
Shot "14-app-todo"

# 返回桌面
& $adb -s $Serial shell input keyevent 4 | Out-Null
Start-Sleep -Milliseconds 800
& $adb -s $Serial shell input keyevent 4 | Out-Null
Start-Sleep -Seconds 2
& $adb -s $Serial shell am start -n "com.shuiyu.octoberrain/.MainActivity" | Out-Null
Start-Sleep -Seconds 3

# 组件选择器：长按卡片抬头
& $adb -s $Serial shell input swipe (PX 0.3) (PY 0.13) (PX 0.3) (PY 0.13) 700 | Out-Null
Start-Sleep -Seconds 3
Shot "15-widget-picker"

$header = @(
    "# 设备走查 $Serial",
    "",
    "- 屏幕尺寸：$width x $height",
    "- 密度：$densityText",
    "- 版本：$versionText",
    "- 时间：$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')",
    ""
)
Set-Content -Path (Join-Path $target "index.md") -Value ($header + $index) -Encoding UTF8
Write-Host "归档目录：$target"
