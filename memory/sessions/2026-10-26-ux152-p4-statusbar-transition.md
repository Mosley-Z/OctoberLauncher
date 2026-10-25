# UX152 交接

- 用户授权修复通知关闭时79px真实栏压缩桌面的短暂过渡。
- [任务](../../task/TASK-UX-152-p4-statusbar-transition.md)、[验收及证据边界](../../docs/acceptance/2026-10-26-ux152-p4-statusbar-transition.md)。
- MainActivity P4已连接服务分支关闭decor fitting并设置FULLSCREEN，保持实际View全屏；未连接回退仍开启decor fitting/显示真实栏。仅3行源码增量。
- 最终release横竖实际View在展开前/展开/10次关闭采样/返回后均保持完整尺寸，各另2次循环通过；非每显示帧/光学验收。
- 完整门禁通过，各1018项0失败/错误1既有跳过；P4安装1.0.31/51、2205403字节、SHA256 6185500aae8afa494dfed7afd72aa922c3cae5490dd08e866e66c17e9f67fefa匹配。
- 原方向、配置、选书选记恢复，服务保持开启。P4/构建释放，S4/Tango未操作，无后台进程，不提交升版发布。
- 录屏绿帧、截图采样慢均不能作逐帧视觉证据；需要用户确认物理收起瞬间的观感。
