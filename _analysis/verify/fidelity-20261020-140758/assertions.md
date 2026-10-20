# 微读融合结构断言

工程根：D:\DSHStation\OctoberLauncher
内核 jar：D:\DSHStation\OctoberLauncher\libs\weread-kernel-1.3.2-k2.jar

| 断言 | 期望 | 实际 | 结果 |
| --- | --- | --- | --- |
| 组件条目数 | 6 | 5 | 失败 |
| 组件标识集合 | calendar,clock,image,placeholder,usage,weread_board | calendar,clock,image,placeholder,usage | 失败 |
| 负一屏默认模块 | bookshelf,negative_files,bill,rank | bookshelf,negative_files,bill,rank | 通过 |
| 组件自绘方法 | 0 | 0 | 通过 |
| 内核 jar 摘要 | 5f6a3f3d8c72d8defb98738f5f6656955b8049c1da5aacfb6e3d2c4f3c775226 | 5f6a3f3d8c72d8defb98738f5f6656955b8049c1da5aacfb6e3d2c4f3c775226 | 通过 |
| 配置版本 | 9 | 9 | 通过 |
| v8 到 v9 迁移 | present | present | 通过 |

断言总数 7，失败 2
