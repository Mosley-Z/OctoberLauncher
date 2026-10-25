# UX150 P4 状态栏诊断交接

- 用户授权：按研究建议进行最小实验；未授权新增无障碍交互。
- 负责人：Codex主代理；master/f025b18、接手472项既有差异，保留。
- [任务](../../task/TASK-UX-150-p4-hidden-status-bar.md)、[验收及下一步](../../docs/acceptance/2026-10-25-ux150-p4-statusbar-diagnosis.md)为当前结论。
- 30组有效P4独立Activity实验：28组隐藏栏均未展开通知面板，2组显示栏正对照成功。HOME身份/十雨重复隐藏不是复现必要条件；shell主动展开成功不代表普通App权限可用。
- 正式源码/安装包/配置未改，生产79px回退保留。原APK摘要e037225772f4bf023901fe706242754fecd930ec509b0c601fcc9178837d3cd6相同，临时包卸载，恢复原横屏桌面并释放P4；S4未使用。
- 工具增量：scripts/android-ui.ps1增加edge-swipe，按实时逻辑坐标执行系统边缘诊断；保留既有其他差异与UTF-8 BOM。
- 下一步候选：复用现有OverlayAccessibilityService响应自绘栏下滑，实测GLOBAL_ACTION_NOTIFICATIONS；当前未接入或授权启用。物理手指和系统源码根因仍未验证。
- 没有后台任务、整包构建、提交、升版、发布或休眠调度。
