# Minecraft 1.21.1 回移与验证

基于 upstream master 的 ChainVeinFabric 4.1.0，保留受控自动挖掘、异步搜索、预设、投影模式、公平服务端调度、直接拾取和 Quick Shulker 溢出收纳。原始 master 与其它版本保留在 Git 远程分支。

## 构建与依赖

- JDK 21；`./gradlew build`（Windows 使用 `gradlew.bat build`）。
- Fabric Loader >= 0.16.14；Fabric API 0.116.12+1.21.1。
- MaLiLib 0.21.10；可选 Litematica 0.19.61 和 Mod Menu 11.0.3，均为 Minecraft 1.21 版本。
- 可选 Minecraft 1.21.1 版 Quick Shulker 4.0.1+1.21.1；新版存储 API 优先，旧版公开 API 由隔离反射桥支持。
- 发布产物：`build/libs/ChainVeinFabric-4.1.0-backport.1-1.21.1.jar`。

客户端 API `ChainVeinClientApi.queueMineJobs(Minecraft, Collection<BlockPos>)` 和 v4 网络协议保持不变。本分支使用 Mojang official mappings，发布 JAR 经 Loom 转为 Fabric intermediary。

## 已完成验证（2026-09-26）

| 验证 | 结果与覆盖 |
| --- | --- |
| `build` | 主代码、客户端代码、单元测试、服务端 GameTest、发布 JAR 与源码 JAR 全部成功 |
| 单元测试 | 30 项通过，涵盖调度器、请求队列、搜索、配置、GUI 布局、种植物品与批处理 |
| 不安装 Quick Shulker | 10 项核心服务端测试和 1 项缺失依赖测试通过；其余 12 项集成用例按模式跳过 |
| Quick Shulker 新版存储 API | 全部 23 项服务端 GameTest 通过 |
| Quick Shulker 旧版公开 API | 强制选择兼容桥后，全部 23 项服务端 GameTest 通过 |
| 正式发布 JAR 客户端 | Fabric intermediary 环境，与 Printer、Quick Shulker、Network Chaos、Litematica 和 MaLiLib 共装，进入真实单人世界并正常退出 |
| 客户端 GUI | 基础、设置、快捷键、预设四个页面均打开并渲染 |
| Printer 联动 | 反射解析 `queueMineJobs(Minecraft, Collection)` 成功，服务端实际挖除目标石头方块 |

服务端用例验证了任务公平轮转、距离约束、种植、冰块处理、容器掉落、掉落实体回退、完整物品数量守恒、64/16/1 堆叠、满容量溢出、随身盒子顺序以及嵌套盒子内容保留。新版和旧版 Quick Shulker 路径分别执行，未通过关闭生产混入来绕过验证。

测试命令（Quick Shulker JAR 路径按本地位置填写）：

```sh
./gradlew build
./gradlew runGameTest -PquickshulkerJar=/path/to/quickshulker-4.0.1+1.21.1.jar
./gradlew runGameTest -PquickshulkerJar=/path/to/quickshulker-4.0.1+1.21.1.jar -PquickshulkerGameTestMode=new-legacy
```

26.x Fabric 客户端 GameTest API 在 1.21.1 不存在；原有客户端测试源码保留，服务端测试已改为 1.21.1 原生 GameTest。客户端验证使用独立的正式 JAR 启动脚本，不依赖开发环境命名。未逐一人工检验所有配置组合、轮廓显示效果或长时间多玩家压力场景；CI 配置已更新，但 GitHub Actions 尚未在远程执行。

运行上述命令后，单元测试 XML/HTML 生成在 `build/test-results/test/` 与 `build/reports/tests/test/`，服务端日志生成在 `build/run/gameTest/logs/`。CI 自动运行构建、单元测试和不安装 Quick Shulker 的服务端用例；两个可选集成模式需提供相应 Quick Shulker JAR。
