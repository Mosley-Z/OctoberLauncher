# 2026-10-23 UX111 完整离线使用说明验收

## 范围与源码

master/f025b18及本轮阅读桌面未提交差异。原入口：设置→微读墨记→设置→初始化→怎么用。WereadSettingsPage.showHelp接入LauncherHelpDialog，正文为APK内assets/help/launcher-guide.md；16章，17,615字节。不新增依赖，不修改业务配置、Manifest或产品版本。

## 构建与资产

- `:feature:weread-app:ktlintFormat`退出0；`check :app:assembleDebug :app:assembleRelease --offline --console=plain`退出0，25秒、1564任务（75执行、1489 up-to-date）。日志：本地`_analysis/verify/help-guide/gate.log`。
- 现有单测每变体905例、0失败、1个既有MonthGrid Ignore；本帮助更新未新增镜像实现的单测。
- debug和release的`assets/help/launcher-guide.md`与源文件字节完全一致；原生View不需要网络或WebView。
- debug SHA256：`db7b51fe7949375248dcca977ec82ed7ffb08cd6ba66430ba63ff991127a92b1`。
- release SHA256：`2aea95829dbebe22304e70ab0e5542972d3ec6af8d788ff2eff1f0e862b34553`；路径`app/build/outputs/apk/release/app-release.apk`，版本1.0.31/51未变。

## 实体观测

root独占S4（SA4B03CN2608081096X），通过scripts/android-ui.ps1操作真实入口；未用直接Activity替代路径。

| 包/场景 | 操作与实际结果 | 本地证据 |
| --- | --- | --- |
| debug入口 | HOME齿轮→微读墨记→设置→初始化→怎么用；完整目录可见 | `_ui-out/20261023-130306-888-SA4B03CN2608081096X/layout.json`、`20261023-130308-922-SA4B03CN2608081096X/screen-annotated.png` |
| debug章节 | 第02章显示唯一二维码手机粘贴保存流程及文字获取方法，正文黑字白底可读 | `_ui-out/20261023-130341-921-SA4B03CN2608081096X/layout.json`、`20261023-130344-003-SA4B03CN2608081096X/screen-annotated.png` |
| debug长文和返回 | 原生ScrollView滚到第16章，再滚到底部；点击返回目录、硬件返回关闭，仍为原初始化页 | `_ui-out/20261023-130628-947-SA4B03CN2608081096X/layout.json`、`20261023-130708-036-SA4B03CN2608081096X/layout.json`、`20261023-130718-558-SA4B03CN2608081096X/layout.json` |
| release入口及资源 | R8发布包同一路径打开目录与第02章；内置正文正常加载 | `_ui-out/20261023-131346-579-SA4B03CN2608081096X/layout.json`、`20261023-131423-174-SA4B03CN2608081096X/layout.json` |

一次Canvas标签滑动/点击解析失败，随后改用文档规定的原生ScrollView滚动完成验证；工具解析失败不计产品失败或通过。P4本次只核对覆盖安装和包摘要，未重复帮助页操作；横屏帮助长文矩阵未单独执行。

## 恢复与边界

为避免布局采集包含凭据，S4原Key与启用状态私有留存并暂时停用，未改阅读缓存。测试后从当前配置逐字段合并恢复这两个原值并核对相等，不回写全量旧配置。私有备份不进入Git、日志或报告。随后两机覆盖安装上述release，实际base.apk摘要均与其一致并回HOME；无本批设备或构建占用。

本次说明按真实源码和入口核对，未宣传不存在的实验室入口或厂商通用休眠/关机壁纸。微信读书真实手机同网实扫、真实Key在线成功等上一批边界仍保留，见[实现验收](2026-10-23-reading-desktop-implementation.md)。未提交、推送、升版或正式发布。
