# 编辑页真机走查脚本（task-27）
#
# 前置条件：
#   1. 已安装包含编辑入口的调试包（P3 接线完成后）。
#   2. 设备已连接且序列号可见（默认 S4 墨水屏）。
#   3. 设备上存在 /sdcard/Pictures/thumbs-bench 用例目录。
#
# 用法示例：
#   pwsh -File _analysis/team/edit-walkthrough.ps1
#   pwsh -File _analysis/team/edit-walkthrough.ps1 -Serial SA4B03CN2608081096X -EditEntryX 300 -EditEntryY 742
#
# 说明：页面全部自绘，uiautomator 无法通过控件树定位，脚本使用屏幕坐标点击。
# 入口按钮坐标由 P3 接线后按实际界面填入 -EditEntryX/-EditEntryY 参数。

param(
    [string]$Serial = "SA4B03CN2608081096X",
    [string]$Adb = "D:\DSHStation\.toolchain\android-sdk\platform-tools\adb.exe",
    [int]$EditEntryX = 0,
    [int]$EditEntryY = 0,
    [string]$ShotDir = "_analysis\team\shots",
    [string]$Bench = "/sdcard/Pictures/thumbs-bench",
    [string]$OutputDir = "/sdcard/Pictures/十雨桌面"
)

$ErrorActionPreference = "Stop"
$Package = "com.shuiyu.octoberrain"
New-Item -ItemType Directory -Force -Path $ShotDir | Out-Null

function Invoke-Adb {
    param([string[]]$Arguments)
    & $Adb -s $Serial @Arguments
}

function Send-Tap {
    param([int]$X, [int]$Y)
    Invoke-Adb @("shell", "input", "tap", "$X", "$Y") | Out-Null
    Start-Sleep -Milliseconds 900
}

function Send-Swipe {
    param([int]$X1, [int]$Y1, [int]$X2, [int]$Y2, [int]$DurationMs = 400)
    Invoke-Adb @("shell", "input", "swipe", "$X1", "$Y1", "$X2", "$Y2", "$DurationMs") | Out-Null
    Start-Sleep -Milliseconds 900
}

function Save-Shot {
    param([string]$Name)
    $remote = "/sdcard/$Name.png"
    Invoke-Adb @("shell", "screencap", "-p", $remote) | Out-Null
    Invoke-Adb @("pull", $remote, (Join-Path $ShotDir "$Name.png")) | Out-Null
    Invoke-Adb @("shell", "rm", "-f", $remote) | Out-Null
    Write-Host "截图：$(Join-Path $ShotDir "$Name.png")"
}

function Set-LastPath {
    param([string]$Path)
    $xml = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map>" +
        "<string name=""last_path"">$Path</string>" +
        "<boolean name=""view_grid"" value=""true"" />" +
        "<int name=""sort_by"" value=""0"" />" +
        "<boolean name=""sort_asc"" value=""true"" />" +
        "<boolean name=""show_hidden"" value=""false"" /></map>"
    $xml | & $Adb -s $Serial shell "run-as $Package sh -c 'cat > shared_prefs/files_prefs.xml'" | Out-Null
}

function Enter-Bench {
    Invoke-Adb @("shell", "am", "force-stop", $Package) | Out-Null
    Set-LastPath -Path $Bench
    Invoke-Adb @("shell", "am", "start", "-n", "$Package/.MainActivity") | Out-Null
    Start-Sleep -Seconds 4
    Invoke-Adb @("shell", "input", "keyevent", "KEYCODE_WAKEUP") | Out-Null
    Start-Sleep -Milliseconds 500
    # 桌面第二页底部中间的「文件」图标。
    Send-Swipe 400 400 80 400 300
    Send-Swipe 400 400 80 400 300
    Send-Tap 240 630
    Start-Sleep -Seconds 2
    Save-Shot "edit-00-files"
}

Write-Host "== 编辑页走查开始，设备 $Serial =="
Enter-Bench

if ($EditEntryX -le 0 -or $EditEntryY -le 0) {
    Write-Host "未提供编辑入口坐标，已停在文件页。传入 -EditEntryX/-EditEntryY 后继续。"
    exit 0
}

# 进入首张图片的查看器，随后点击编辑入口。
Send-Tap 80 200
Start-Sleep -Seconds 2
Save-Shot "edit-01-viewer"
Send-Tap $EditEntryX $EditEntryY
Start-Sleep -Seconds 3
Save-Shot "edit-02-crop"

# 裁剪：切换 1:1、拖拽右下角手柄、应用裁剪。
Send-Tap 200 690
Save-Shot "edit-03-crop-aspect"
Send-Swipe 320 420 280 460 300
Send-Tap 90 690
Save-Shot "edit-04-crop-applied"

# 旋转页签：顺时针旋转两次并撤销一次。
Send-Tap 180 762
Send-Tap 120 690
Send-Tap 120 690
Save-Shot "edit-05-rotate"
Send-Tap 360 750
Save-Shot "edit-06-undo"

# 滤镜页签：五档滤镜与高对比度。
Send-Tap 300 762
for ($level = 0; $level -lt 5; $level++) {
    $x = 60 + $level * 90
    Send-Tap $x 690
    Save-Shot ("edit-07-filter-{0}" -f $level)
}
Send-Tap 360 750
Save-Shot "edit-08-contrast-high"

# 另存页签：保存并确认。
Send-Tap 420 762
Send-Tap 240 690
Start-Sleep -Milliseconds 500
# InkDialogs 确认按钮位于对话框右下方。
Send-Tap 300 520
Start-Sleep -Seconds 3
Save-Shot "edit-09-saved"

# 导出结果核对：目录存在、文件名匹配、数量与修改时间。
Write-Host "== 导出目录 =="
Invoke-Adb @("shell", "ls", "-l", $OutputDir)
$names = Invoke-Adb @("shell", "ls", $OutputDir)
$pattern = '^.+-\u7f16-\d{8}-\d{6}\.png$'
$matched = @($names | Where-Object { $_ -match $pattern })
Write-Host "匹配「原名-编-yyyyMMdd-HHmmss.png」的文件数：$($matched.Count)"
$matched | Select-Object -First 5 | ForEach-Object { Write-Host "  $_" }

# 恢复设备：清空模块偏好并重启桌面。
Invoke-Adb @("shell", "run-as", $Package, "rm", "-f", "shared_prefs/files_prefs.xml") | Out-Null
Invoke-Adb @("shell", "am", "force-stop", $Package) | Out-Null
Invoke-Adb @("shell", "am", "start", "-n", "$Package/.MainActivity") | Out-Null
Write-Host "== 走查结束 =="
