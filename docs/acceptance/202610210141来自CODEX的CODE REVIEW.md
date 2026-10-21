本次只读审查发现 **6 项问题：2 项 P1、4 项 P2**。未修改任何工作区文件。审查对象为当前源码；项目没有 Git 仓库，且其他 Agent 正在开发，因此以下结论不归因于具体提交。

1. **[P1] 配置重新加载会丢失迁移状态与成就数据**

   位置：[JsonConfigStore.kt (line 117)](/D:/DSHStation/OctoberLauncher/core/data/src/main/java/com/shuiyu/octoberrain/core/data/JsonConfigStore.kt:117)

   `LauncherConfig` 已包含 `wereadMigration`、`wereadAchievement`，但 `load()` 手动构造配置时没有读取这两个字段。完成导入后重启进程，两项便恢复默认值；随后任何配置编辑又会把默认值写回磁盘，导致成就数据丢失、迁移摘要去重失效。

   建议：补齐两节的加载，并验证“导入 → 重建存储实例 → 修改其他设置”后数据仍完整。

2. **[P1] 同步与待办并发写入会互相覆盖数据**

   位置：[WereadStore.kt (line 254)](/D:/DSHStation/OctoberLauncher/core/weread-sync/src/main/java/com/shuiyu/octoberrain/core/wereadsync/WereadStore.kt:254)

   偏好更新采用“读取整个文件 → 合并 → 整体写回”。后台同步与 `WereadTodoStore` 各自持有独立的 `WereadStore`，同步互斥锁和待办锁不能互相排斥。同步期间新增或勾选待办，后写入的一方可能覆盖另一方的数据；两者还共用 `kernel_prefs.json.tmp`，存在临时文件竞争。

   建议：按数据目录统一协调所有写入者，将整个读改写过程纳入同一事务。

3. **[P2] 划线请求失败仍会覆盖已有笔记正文**

   位置：[WereadGatewayProvider.kt (line 73)](/D:/DSHStation/OctoberLauncher/app/src/main/java/com/shuiyu/octoberrain/WereadGatewayProvider.kt:73)

   当某本书的划线请求失败时，`marks` 为 `null`，代码仍生成并提交该书的笔记文件。生成结果只有概览元数据，随后 `writeNotes()` 会覆盖已有的 `nt_<bookId>.json`，使原本缓存的划线消失。一次局部网络故障即可触发。

   建议：失败时跳过该文件或保留旧正文，明确区分“请求失败”和“成功返回空划线”。

4. **[P2] 文件写入失败仍被报告为同步成功**

   位置：[WereadStore.kt (line 386)](/D:/DSHStation/OctoberLauncher/core/weread-sync/src/main/java/com/shuiyu/octoberrain/core/wereadsync/WereadStore.kt:386)

   `writeText()` 将目录创建、写入和替换失败全部吞掉，只记录日志；调用方没有得到失败结果。同步服务继续累计“写入数量”，并以数量大于零判定成功。存储空间不足或目录不可写时，界面可能显示“同步完成”，实际数据没有保存。

   建议：让存储层返回或抛出写入失败，报告只统计确认落盘的数据。

5. **[P2] 成功同步空书架时不会清理旧书架**

   位置：[WereadSyncService.kt (line 222)](/D:/DSHStation/OctoberLauncher/core/weread-sync/src/main/java/com/shuiyu/octoberrain/core/wereadsync/WereadSyncService.kt:222)

   `shelf.isNullOrEmpty()` 把接口失败与合法空列表合并处理，直接返回。用户清空远端书架后再次同步，本地 `shelf.json`、`shelf_ids.json` 和当前书目仍保留旧内容；反复同步也无法清除。

   建议：仅在 `null` 时保留缓存；空列表应写入空书架、空标识列表并清除当前书目。

6. **[P2] 扫码录密钥服务的五分钟有效期无法保证**

   位置：[KeyEntryServer.kt (line 119)](/D:/DSHStation/OctoberLauncher/feature/settings/src/main/java/com/shuiyu/octoberrain/feature/settings/qr/KeyEntryServer.kt:119)

   有效期仅在进入阻塞式 `accept()` 前检查，监听套接字没有超时。无人连接时，服务超过五分钟仍不会关闭；五分钟后首次连接也会直接进入请求处理，因此旧链接仍能提交密钥。

   建议：为接受连接设置有界等待，并在处理请求、接收密钥前再次检查有效期。

验证方式为源码和调用链检查，并查阅了相关测试源码。为遵守只读边界，本次没有执行会生成工作区产物的构建、测试或静态检查任务，因此不提供当前构建通过的结论。