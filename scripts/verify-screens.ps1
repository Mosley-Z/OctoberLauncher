# 多分辨率验证脚本
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-screens.ps1 [-Serial SA4B03CN2608081096X]
# 作用：临时改写设备显示尺寸与密度，逐组启动十雨桌面并截图，结束后恢复默认设置。
# 说明：wm size 只改变逻辑显示尺寸，不改变物理面板；截图尺寸与设定一致，用于验证布局断点。

param(
    [string]$Serial = "SA4B03CN2608081096X"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$toolchain = (Get-Content (Join-Path $root "toolchain.path") | Select-Object -First 1).Trim()
$adb = Join-Path $toolchain "android-sdk\platform-tools\adb.exe"
$shotDir = Join-Path $root "_shots"
New-Item -ItemType Directory -Force -Path $shotDir | Out-Null

# 待验证的尺寸组合：宽 x 高 @ 密度
# 目标机型为 552x768 竖屏墨水屏；wm size 只接受可旋转的尺寸，横置会被系统改写为竖屏。
$specs = @(
    @{ W = 480; H = 800; D = 219; Name = "native-480x800" },
    @{ W = 552; H = 768; D = 160; Name = "target-552x768-mdpi" },
    @{ W = 552; H = 768; D = 213; Name = "target-552x768-hdpi" },
    @{ W = 480; H = 800; D = 160; Name = "tall-480x800-mdpi" },
    @{ W = 414; H = 577; D = 160; Name = "compact-414x577" }
)

# 执行一次 adb 命令，失败时抛出异常
function Invoke-Adb {
    param([string[]]$CommandArgs)
    & $adb -s $Serial @CommandArgs
    if ($LASTEXITCODE -ne 0) { throw "adb 执行失败: $($CommandArgs -join ' ')" }
}

# 恢复设备的默认显示设置
function Reset-Display {
    Invoke-Adb -CommandArgs @("shell", "wm", "size", "reset")
    Invoke-Adb -CommandArgs @("shell", "wm", "density", "reset")
    Invoke-Adb -CommandArgs @("shell", "settings", "put", "system", "accelerometer_rotation", "1")
}

try {
    # 清理可能存在的虚拟显示，保证每组尺寸按竖屏生效
    Invoke-Adb -CommandArgs @("shell", "settings", "delete", "global", "overlay_display_devices")
    foreach ($spec in $specs) {
        $size = "$($spec.W)x$($spec.H)"
        Write-Host "==> 验证 $($spec.Name) ($size @ $($spec.D)dpi)"
        Invoke-Adb -CommandArgs @("shell", "wm", "size", $size)
        Invoke-Adb -CommandArgs @("shell", "wm", "density", "$($spec.D)")
        Start-Sleep -Milliseconds 800

        # 重新启动应用，确保按新的屏幕尺寸重建布局
        Invoke-Adb -CommandArgs @("shell", "am", "force-stop", "com.shuiyu.octoberrain")
        Start-Sleep -Milliseconds 300
        Invoke-Adb -CommandArgs @("shell", "am", "start", "-n", "com.shuiyu.octoberrain/.MainActivity")
        Start-Sleep -Seconds 2
        Invoke-Adb -CommandArgs @("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        Start-Sleep -Milliseconds 500

        $remote = "/sdcard/verify_$($spec.Name).png"
        Invoke-Adb -CommandArgs @("shell", "screencap", "-p", $remote)
        Invoke-Adb -CommandArgs @("pull", $remote, (Join-Path $shotDir "verify-$($spec.Name).png"))
        Invoke-Adb -CommandArgs @("shell", "rm", $remote)
        Write-Host "    截图: _shots\verify-$($spec.Name).png"
    }
}
finally {
    Write-Host "==> 恢复默认显示设置"
    Reset-Display
    Invoke-Adb -CommandArgs @("shell", "am", "force-stop", "com.shuiyu.octoberrain")
    Invoke-Adb -CommandArgs @("shell", "am", "start", "-n", "com.shuiyu.octoberrain/.MainActivity")
}
