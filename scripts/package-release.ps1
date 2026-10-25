#requires -Version 7.0
param(
    [string]$SigningPropertiesPath = $env:OCTOBER_SIGNING_PROPERTIES,
    [string]$Repository = 'Mosley-Z/OctoberLauncher',
    [string[]]$ApkMirrors = @(),
    [switch]$SkipBuild,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $SigningPropertiesPath) {
    $SigningPropertiesPath = Join-Path $env:USERPROFILE '.android/octoberlauncher-release/signing.properties'
}
if (-not (Test-Path -LiteralPath $SigningPropertiesPath -PathType Leaf)) { throw '需要仓库外的正式签名配置' }
if ($Repository -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') { throw 'GitHub仓库格式应为owner/repository' }
$env:OCTOBER_SIGNING_PROPERTIES = [IO.Path]::GetFullPath($SigningPropertiesPath)
$tc = (Get-Content -LiteralPath (Join-Path $root 'toolchain.path') -TotalCount 1).Trim()
$jdk = Get-ChildItem (Join-Path $tc 'jdk') -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'bin/javac.exe') } |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $jdk) { throw '缺少JDK' }
$env:JAVA_HOME = $jdk.FullName
$env:ANDROID_HOME = Join-Path $tc 'android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $tc 'gradle-home'
$env:PATH = (Join-Path $jdk.FullName 'bin') + ';' + $env:PATH
if (-not $SkipBuild) {
    $tasks = @('check', ':app:assembleDebug', ':app:assembleRelease', '--console=plain', '--max-workers=2')
    if ($Offline) { $tasks += '--offline' }
    & (Join-Path $tc 'gradle/gradle-8.11.1/bin/gradle.bat') -p $root @tasks
    if ($LASTEXITCODE -ne 0) { throw '正式版构建门禁失败' }
}
$apk = Join-Path $root 'app/build/outputs/apk/release/app-release.apk'
$tools = Join-Path $tc 'android-sdk/build-tools/35.0.0'
$verification = & (Join-Path $tools 'apksigner.bat') verify --verbose --print-certs $apk
if ($LASTEXITCODE -ne 0) { throw 'APK签名验证失败' }
if (($verification -join "`n") -match 'CN=Android Debug') { throw '拒绝将测试签名打包为正式版本' }
$certificate = [regex]::Match(($verification -join "`n"), 'Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]+)').Groups[1].Value.ToLowerInvariant()
if (-not $certificate) { throw '无法读取正式签名指纹' }
$version = (Get-Content (Join-Path $root 'VERSION') -Raw).Trim()
$code = [int]((Get-Content (Join-Path $root 'VERSION_CODE') -Raw).Trim())
$badging = (& (Join-Path $tools 'aapt.exe') dump badging $apk | Select-Object -First 1)
if ($badging -notmatch "versionCode='$code'" -or $badging -notmatch "versionName='$([regex]::Escape($version))'") {
    throw 'APK版本与版本文件不一致，先重新构建'
}
foreach ($url in $ApkMirrors) { if (-not $url.StartsWith('https://')) { throw '镜像地址必须使用HTTPS' } }
$destination = Join-Path $root "dist/$version"
New-Item -ItemType Directory -Force $destination | Out-Null
$name = "OctoberLauncher-v$version.apk"
$outputApk = Join-Path $destination $name
Copy-Item -LiteralPath $apk -Destination $outputApk -Force
$digest = (Get-FileHash -LiteralPath $outputApk -Algorithm SHA256).Hash.ToLowerInvariant()
$notes = Join-Path $root "release/notes/v$version.md"
$manifest = [ordered]@{
    versionName = $version
    versionCode = $code
    notes = (Get-Content -LiteralPath $notes -Raw -Encoding UTF8).Trim()
    downloadUrl = "https://github.com/$Repository/releases/download/v$version/$name"
    downloadUrls = @($ApkMirrors)
    sha256 = $digest
    sizeBytes = (Get-Item -LiteralPath $outputApk).Length
    signerCertificateSha256 = $certificate
}
$manifest | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $destination 'update.json') -Encoding utf8NoBOM
"$digest  $name" | Set-Content (Join-Path $destination 'SHA256SUMS.txt') -Encoding ascii
Copy-Item -LiteralPath $notes -Destination (Join-Path $destination 'RELEASE_NOTES.md') -Force
$verification | Set-Content (Join-Path $destination 'SIGNATURE.txt') -Encoding utf8NoBOM
Write-Host "正式发布产物：$destination"
Write-Host "版本：$version ($code)；APK SHA256：$digest"
Write-Host "签名证书 SHA256：$certificate"
