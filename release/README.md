# 正式版与更新源

版本来自根目录VERSION和VERSION_CODE。1.0.0是首个正式版，versionCode为52，高于历史测试版51；以后每次发布都增加versionCode。

## 本地打包

需要PowerShell7、项目工具链与仓库外正式签名配置。配置文件含storeFile、storePassword、keyAlias、keyPassword；不要提交、上传或打印口令。必须离线备份密钥和配置文件，并在后续所有正式版本中复用。

```powershell
$env:OCTOBER_SIGNING_PROPERTIES = 'C:/private/signing.properties'
pwsh -NoProfile -File scripts/package-release.ps1 -Offline
```

脚本运行check/debug/release，验证实际APK版本、正式签名、摘要，并生成dist/版本/：

- OctoberLauncher-v版本.apk：正式签名APK。
- update.json：应用读取的版本清单。
- SHA256SUMS.txt：安装包摘要。
- SIGNATURE.txt：公开证书指纹与签名验证结果，不含私钥。
- RELEASE_NOTES.md：发布说明。

不要把debug签名或未签名CI验证包作为正式Release。首次从测试签名迁移需先在应用内备份、卸载测试版、安装正式版，再导入备份；不要为升级测试设备清除用户数据。

## GitHub 发布

当前默认仓库为Mosley-Z/OctoberLauncher。清理旧历史并确认可公开后，先提交并推送最终源码，再为该提交创建v1.0.0标签。将dist/1.0.0下的五个文件一起上传到稳定Release，不能标为Prerelease。

```powershell
gh release create v1.0.0 dist/1.0.0/OctoberLauncher-v1.0.0.apk dist/1.0.0/update.json dist/1.0.0/SHA256SUMS.txt dist/1.0.0/SIGNATURE.txt dist/1.0.0/RELEASE_NOTES.md --repo Mosley-Z/OctoberLauncher --verify-tag --title 'V1.0.0' --notes-file dist/1.0.0/RELEASE_NOTES.md --latest
```

应用内置清单：[GitHub latest/update.json](https://github.com/Mosley-Z/OctoberLauncher/releases/latest/download/update.json)。清单中的APK地址固定到版本标签；设置中的updateUrl为空时使用内置地址。所有源失败显示检查失败，同版本或较旧versionCode不提示更新。

打包脚本只生成本地产物；源码提交、版本标签和GitHub附件上传按上述流程执行。

## 国内源接入

国内服务使用相同update.json格式，复制同一个正式签名APK，不重新打包或重新签名。

配置已有integrations.updateUrl可将国内清单设为首选；新增integrations.updateFallbackUrls数组按顺序提供其他清单地址；最后始终尝试GitHub默认源。字段默认空、旧配置可直接读取，不增加设置页面。取得实际地址后可通过受控配置导入或下一版默认配置接入。

清单的downloadUrl是首选APK地址，downloadUrls数组提供国内/其他镜像。APK下载失败、大小不符或摘要不符时尝试下一地址，所有地址均失败就停止安装。

```json
{
  "versionName": "1.0.0",
  "versionCode": 52,
  "notes": "发布说明",
  "downloadUrl": "https://github.com/Mosley-Z/OctoberLauncher/releases/download/v1.0.0/OctoberLauncher-v1.0.0.apk",
  "downloadUrls": [],
  "sha256": "由打包脚本写入实际64位SHA256",
  "sizeBytes": 0
}
```

上面是字段说明，实际发布必须使用打包脚本生成的有效摘要与大小。清单源与下载源要求HTTPS。尚未配置国内域名，不携带GitHub私有访问令牌，不假定镜像已经可用。连接真实国内源后应测试GitHub不可达时的完整检查、下载、校验和系统安装流程。
