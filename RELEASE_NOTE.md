## ChainVeinFabric v4.1.0

> [!CAUTION]
> **自动挖掘安全提示：** 自动挖掘会在玩家周围批量搜索并破坏方块。启用前请确认当前模式、白名单、搜索算法与范围；每次左键只触发一轮搜索，并受冷却时间限制。
>
> **Automatic mining safety:** Automatic mining searches for and breaks blocks around the player. Check the active mode, whitelist, algorithm, and range before arming it. Each left click triggers one search cycle and is subject to the configured cooldown.

### 受控自动挖掘与异步搜索 / Guarded Automatic Mining and Async Search

- 所有挖掘模式均支持自动挖掘：按住 Ctrl 点击主开关进入 **AUTO**，之后每次左键触发一轮以玩家为中心的搜索；点击 **AUTO** 即可关闭
- 自动挖掘继续使用当前模式的“白名单 × 搜索算法”规则；相邻同类算法以玩家脚下方块为起点，Litematica 模式仍遵循各自的选区、投影和渲染层限制
- 新增可配置的自动挖掘冷却，以及醒目的屏幕边缘警示与状态提示，避免误触后持续运行
- 预览、手动连锁与自动挖掘统一使用带优先级的异步搜索线程，减少大范围搜索对客户端主线程的影响

- Automatic mining is available in every mining mode: Ctrl-click the main toggle to arm **AUTO**, then left-click once for each player-centered search cycle; click **AUTO** to disarm it
- Automatic mining keeps the active mode's whitelist × search-algorithm rules. Same-type adjacency starts from the block beneath the player, while Litematica modes retain their area, placement, and render-layer restrictions
- Added a configurable automatic-mining cooldown plus a prominent screen-edge warning and status indicator to make accidental activation obvious
- Preview, manual chaining, and automatic mining now share one prioritized asynchronous search worker, reducing client-thread work during large searches

### 纯客户端模式 / Client-side Mode

- 未安装服务端模组时，挖掘会按原版破坏进度逐个完成方块，不再依赖同 tick 发送开始与结束包
- 顶部状态区会显示当前方块和整份请求的进度，并避开打印机类模组常用的准星进度位置
- 新请求会等待当前方块处理完成后替换尚未开始的旧任务；玩家再次手动挖掘会立即丢弃剩余任务
- 公开客户端 API 与界面操作会自动选择服务端协议或纯客户端路径，直接进入背包与潜影盒溢出收纳仍只在服务端安装模组时可用

- Without the server mod, blocks are now completed one at a time using vanilla breaking progress instead of relying on same-tick start/stop packets
- The top status area shows progress for both the current block and the complete request, away from crosshair progress displays commonly used by printer mods
- A newer request waits for the current block, then replaces the unstarted remainder; another manual mining action immediately discards all remaining fallback work
- Public client API calls and UI actions automatically select the dedicated protocol or client-side path. Direct to Inventory and shulker overflow still require the server mod

### 服务端调度与限制 / Server Scheduling and Limits

- 单个协议请求固定最多包含 `2048` 个位置，并在解码阶段拒绝超限负载
- `/chainvein maxBlocks [1..2048]` 现在表示全服每 tick 合计处理上限，默认 `256`；同一 tick 的活动玩家公平分享预算，未用份额会重新分配
- 每名玩家每 tick 只接受第一份挖掘或交互请求；服务端先处理已有请求，再以新请求替换尚未开始的剩余部分，因此不会形成无限增长的队列
- `/chainvein pickupRadius [0..64]` 控制直接进入背包及快捷潜影盒收纳的拾取半径，默认 `10`；设为 `0` 可关闭服务端掉落捕获
- 服务端协议挖掘不再额外限制玩家距离，但不会加载未加载区块；交互统一使用原版服务端的方块交互距离判定
- 玩家下线、重生、切换维度或服务器时会清除过期任务

- Each protocol request is hard-limited to `2048` positions at decode time
- `/chainvein maxBlocks [1..2048]` now controls the server-wide processed-position budget per tick and defaults to `256`; active players share it fairly and unused shares are redistributed
- Only the first mining or interaction request from each player is accepted per tick. Existing work is processed first, then its unstarted remainder is replaced by the new request, so no unbounded queue can form
- `/chainvein pickupRadius [0..64]` controls the capture radius for Direct to Inventory and optional Quick Shulker storage, defaults to `10`, and can be set to `0` to disable server-side drop capture
- Dedicated-protocol mining no longer imposes an additional player-distance limit but never loads unavailable chunks; interactions use the vanilla server block-interaction-range check
- Stale work is cleared when a player disconnects, respawns, changes dimension, or changes server

### 掉落与 Quick Shulker / Drops and Quick Shulker

- 手动连锁挖掘的首个方块与后续方块现在使用同一条服务端破坏和掉落路径；开启直接进入背包后，首个方块也会正确进入背包
- 修复无精准采集冰块、含物品容器等方块在连锁破坏时与原版语义不一致的问题
- Quick Shulker 保持可选：背包装满后，剩余掉落会按玩家背包中的盒子顺序写入潜影盒，并支持公开新 API 与兼容能力探测路径
- 连续同类掉落会批量写入，潜影盒按需解析；只有背包和潜影盒都无法接收的最终剩余物才会在原位置创建掉落实体
- 保持部分接收以及 `64/16/1` 堆叠上限；满盒、超量掉落、箱子内容物和物品展示框均保留原版可见结果，不会吞物品

- The initially clicked block and all subsequent blocks now use the same server-side breaking and drop path; with Direct to Inventory enabled, the first block is captured correctly as well
- Fixed chained breaking of blocks such as non-Silk-Touch ice and containers with contents so their behavior matches vanilla semantics
- Quick Shulker remains optional: after the normal inventory fills, overflow is inserted into carried shulker boxes in inventory order through the public API and capability-detected compatibility path
- Consecutive equal drops are inserted in batches and boxes are resolved lazily; item entities are created only for final overflow that fits in neither inventory nor shulker storage
- Partial insertion and `64/16/1` stack limits are preserved. Full boxes, excess contents, container drops, and item-frame drops remain visible in the world instead of being lost

### 界面、配置与稳定性 / UI, Configuration, and Stability

- 配置界面改为响应式布局，在不同 GUI 比例和分辨率下为标签、按钮及滚动条正确预留空间
- 修复切换到 Advanced 页面时下拉框构造顺序可能触发的崩溃，并隐藏不存在的翻译注释占位符
- 拆分客户端任务调度、配置持久化、预设管理、白名单归一化与界面组件，保持各层职责清晰
- 放宽兼容的 Litematica 版本范围；Litematica 与 Quick Shulker 未安装时，对应设置与模式仍保持隐藏
- 扩充服务端与真实客户端 GameTest，覆盖自动挖掘、纯客户端回退、服务端公平调度、配置迁移、四个配置页、白名单、快捷键、种植、容器及潜影盒边界行为

- The configuration screen now uses a responsive layout that reserves space correctly for labels, controls, and scrollbars across GUI scales and resolutions
- Fixed a dropdown initialization crash when opening Advanced and hid missing translation-comment placeholders
- Split client job dispatch, configuration persistence, preset management, whitelist normalization, and screen components into focused layers
- Broadened the supported Litematica version range; Litematica- and Quick Shulker-specific modes and settings remain hidden when those optional mods are absent
- Expanded server and real-client GameTests for automatic mining, client fallback, fair server scheduling, schema migration, all four configuration tabs, whitelists, hotkeys, planting, containers, and shulker-storage boundaries

### 兼容性 / Compatibility

- 同时提供 Minecraft 26.2 与 26.1.x 构建；Minecraft 1.21.x 继续使用 ChainVeinFabric 2.2.1
- 相比 4.0.x，4.1.0 未更改 4.x 网络负载格式、客户端配置 schema 或公开 `ChainVeinClientApi` 方法签名，可在同一 Minecraft 版本内与 4.0.x 混用
- 4.x 专用协议仍与 1.x～3.x 分离；版本不匹配时会安全退回纯客户端模式，而不会尝试解码不兼容的数据包
- Quick Shulker 仍为可选依赖；未安装时不影响其他连锁功能

- Builds are provided for both Minecraft 26.2 and 26.1.x; Minecraft 1.21.x remains on ChainVeinFabric 2.2.1
- Compared with 4.0.x, 4.1.0 keeps the 4.x network payload format, client configuration schema, and public `ChainVeinClientApi` method signatures unchanged, allowing 4.0.x interoperability within the same Minecraft version
- The 4.x dedicated protocol remains separate from 1.x–3.x; mismatched versions safely use client-side mode instead of decoding incompatible payloads
- Quick Shulker remains optional and its absence does not affect other chaining features
