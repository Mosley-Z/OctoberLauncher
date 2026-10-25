# REL001 V1.0.0 本地正式版准备

用户最初要求提交、推送并创建正式GitHub Release，同时准备国内更新源。随后明确选择新长期正式签名，并将本轮范围收窄为“旧历史我自行处理，暂时只准备正式签名、版本与发布产物”。本记录验收本地准备，不代表GitHub已发布。[任务](../../task/TASK-REL-001-v1-stable-release.md)、[发行约定](../../release/README.md)。

## 交付

- 显示版本1.0.0，versionCode52，高于历史测试版51。VERSION与新增VERSION_CODE作为构建和打包的同一来源。
- 新生产签名：RSA4096、PKCS12；密钥与签名配置仅在当前用户仓库外私有目录，目录ACL仅当前用户及SYSTEM。口令为随机生成，不进入日志、文档或Git。签名验证为APK v2通过；最低SDK26支持此签名。
- 公开证书SHA256：`b60065d80ca58550ed99770d70f150272c8e02605e98c9c8856a6229e58283be`。以后正式版本必须复用同一密钥。
- 新增本地scripts/package-release.ps1，先完整门禁，再验证正式签名与实际APK版本、生成清单和摘要；无密钥时CI只构建未签名验证包，不再冒用debug签名作为release。
- 正式说明位于release/notes/v1.0.0.md。APK、update.json、SHA256SUMS.txt、SIGNATURE.txt、RELEASE_NOTES.md五个本地产物位于dist/1.0.0/，已忽略入库。

## 更新源

默认清单为`https://github.com/Mosley-Z/OctoberLauncher/releases/latest/download/update.json`；实际发布后才会上线，当前未声称远端可用。

原updateUrl显式值保留；新增可选updateFallbackUrls供国内清单源回退，最后兜底GitHub。清单新增可选downloadUrls供同一APK的镜像回退；每次下载校验HTTPS地址、实际字节数与SHA256，损坏镜像不交给安装器。国内域名尚未提供，因此未配置或验证真实国内服务。

修正手动检查同版本误报更新，以及断网误报“已是最新版本”；所有清单源失败会显示失败。补齐ChangelogEntry序列化，使正式版内置更新说明能读取。没有新增设置页面或权限。

## 验证

证据根：`_analysis/verify/release-1.0.0/`。

- 完整check/assembleDebug/assembleRelease通过，check-build.log；Debug、Release各1027项，0失败、0错误、1既有跳过。
- 新增9项更新测试覆盖旧配置默认兼容、源优先级、同版本/旧版本排除、网络/非法清单回退、全源失败、损坏APK镜像回退、错误摘要/大小拒绝、HTTPS与更新说明反序列化。
- package-release.ps1实际运行成功，APK签名验证通过，包名com.shuiyu.octoberrain、versionName1.0.0、versionCode52、minSdk26、targetSdk35，无application-debuggable标志。
- APK为2211143字节，SHA256 `8aaf0ddfbb9c33723cadc6bd59288ff188638791054333ba020286c9212940cc`；清单、SHA256SUMS与实际文件一致，见artifact-summary.json、package.log及dist/1.0.0/SIGNATURE.txt。
- 国内镜像故障与损坏包测试使用可控本地测试数据，不冒充公网镜像实测。未安装新签名到用户现有测试设备，未卸载、清数据或更改设备配置；首次迁移须先导出备份，卸载测试签名版后安装正式版再导入。

## 远端与后续

现有Git历史发现设备备份、笔记快照及非空凭据字段。用户已选择自行处理历史。本轮未改Git索引/历史、未提交推送、未创建tag或Release；没有再次执行仓库可见性变更。临时恢复私有的尝试被自动审批拒绝，原因是用户此前明确主动公开、未授权反向的持续外部访问变更；随后用户将范围改为仅本地准备。

新增忽略规则只阻止以后新增分析产物、签名文件和dist，不能移除已经跟踪的文件或旧历史。远端清理由用户完成；确认后再按release/README.md提交最终源码、推送版本标签并上传五个产物。V1.0.0清单固定使用v1.0.0标签中的APK，GitHub latest清单指向稳定版本，不携带私有访问令牌。

长期签名的本机位置：`C:/Users/MosleyZ/.android/octoberlauncher-release/`。需离线备份其中的密钥及signing.properties；本记录不包含口令。工作区既有修改保留，无后台构建或设备占用。
