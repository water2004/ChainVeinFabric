## ChainVeinFabric v4.1.0-alpha.3

> 这是面向 Minecraft 26.2 与 26.1.x 的预发布测试版本。请在重要存档中谨慎使用自动挖掘，并在升级前备份。
>
> This is a prerelease build for Minecraft 26.2 and 26.1.x. Use automatic mining cautiously in important worlds and back them up before upgrading.

### 服务端调度 / Server Scheduling

- 服务端协议单个请求固定最多携带 `2048` 个坐标；该限制在解码阶段执行
- `/chainvein maxBlocks` 现在表示全服单 tick 合计处理上限，默认仍为 `256`，现有配置值会直接作为 tick 预算使用
- 同一 tick 内，各玩家的活动请求公平分享全局预算；短请求未使用的份额会重新分配，除不尽的余数会轮换
- 每名玩家每 tick 只接受第一份挖掘或交互请求；先处理手上的旧请求，再用新请求替换旧请求尚未开始的部分
- 服务端不会形成可无限增长的请求队列；玩家下线、重生或切换维度后，旧请求会被丢弃

- A dedicated-server request is hard-limited to `2048` positions at decode time
- `/chainvein maxBlocks` now controls the server-wide total processed per tick. Its default remains `256`, and existing configured values become tick budgets directly
- Active players share the global budget fairly each tick; unused capacity from short requests is redistributed and indivisible remainders rotate between players
- Only the first mining or interaction request from each player is accepted in a tick. The current request is processed first, then its unstarted remainder is replaced by the new request
- No unbounded request queue is created; stale work is discarded when a player disconnects, respawns, or changes dimensions

### 性能与正确性 / Performance and Correctness

- 连续同类掉落会批量写入快捷潜影盒，并按玩家背包中的盒子顺序按需解析和填充
- 只有最终无法进入背包或潜影盒的掉落物才会创建实体；满盒时仍在原位置正常掉落
- 优化了 Quick Shulker 新 API、强制旧适配器和稳定版兼容路径（26.2 为 3.0.2，26.1 为 3.0.1），保持部分接收及 `64/16/1` 堆叠上限
- 手动连锁挖掘会将点击原点与异步搜索余量分到不同 tick 发送，并阻止原版完成包抢先破坏原点；开启直接进入背包时，第一个方块与后续方块现在走同一服务端掉落路径
- 修复配置界面切换到 Advanced 时可能因下拉框构造顺序导致的崩溃
- 配置页、白名单、快捷键和四个配置分页均增加客户端回归测试

- Consecutive equal drops are inserted into Quick Shulker in batches, resolving and filling carried boxes lazily in inventory order
- Item entities are now created only for final overflow that fits in neither inventory nor shulker storage; full-box overflow still drops at its original position
- Optimized the Quick Shulker direct API, forced legacy adapter, and stable compatibility paths (3.0.2 on 26.2 and 3.0.1 on 26.1) while preserving partial insertion and `64/16/1` stack limits
- Manual chain mining now sends the clicked origin and asynchronous remainder in separate ticks and prevents the vanilla completion packet from breaking the origin first; with Direct to Inventory enabled, the first and subsequent blocks use the same server drop path
- Fixed a configuration-screen crash when opening Advanced caused by dropdown initialization order
- Added client regression coverage for configuration pages, whitelists, hotkeys, and all four configuration tabs

### 测试与兼容性 / Testing and Compatibility

- Minecraft 26.2 与 26.1.x 均验证无 Quick Shulker、DIRECT API、强制 LEGACY 与各自对应的稳定版路径
- 服务端 GameTest 覆盖 tick 预算、公平分配、同 tick 首包及新请求替换顺序
- 4.x 网络负载格式、客户端配置 schema 及公开 `ChainVeinClientApi` 方法签名均未改变
- 本预发布同时提供 Minecraft 26.2 与 26.1.x 构建；4.x 与 1.x～3.x 的专用协议仍然分离

- Both Minecraft 26.2 and 26.1.x validate operation without Quick Shulker, with the DIRECT API, with the forced LEGACY adapter, and with their respective stable Quick Shulker releases
- Server GameTests cover tick budgets, fair allocation, first-packet-per-tick behavior, and replacement ordering
- The 4.x network payload format, client configuration schema, and public `ChainVeinClientApi` method signatures are unchanged
- This prerelease provides builds for both Minecraft 26.2 and 26.1.x; the 4.x dedicated protocol remains separate from 1.x–3.x
