## ChainVeinFabric v4.1.0-alpha.1

> 这是面向 Minecraft 26.2 与 26.1.x 的预发布测试版本。请在重要存档中谨慎使用自动挖掘，并在升级前备份。
>
> This is a prerelease build for Minecraft 26.2 and 26.1.x. Use automatic mining cautiously in important worlds and back them up before upgrading.

### 新功能 / Features

- 自动挖掘不再是独立模式：在任意挖掘模式中按住 Ctrl 点击主开关即可待命，每次左键只触发一轮以玩家为中心的搜索与挖掘；点击 **AUTO** 可直接解除
- 新增自动挖掘冷却与高可见度危险提示 HUD；同类相邻算法以玩家脚下方块为目标，其余白名单与形状算法继续复用统一异步搜索路径
- 新增持久化的权限等级 4 命令：`/chainvein maxBlocks [1..2048]`（默认 `256`）与 `/chainvein pickupRadius [0..64]`（默认 `10`）
- 服务端挖掘请求不再使用硬编码玩家距离，也不会为请求加载区块；连锁交互交由原版服务端交互距离判断
- 快捷潜影盒优先使用新的标准 Fabric Storage API：先解析随身存储槽位，再对整批溢出物全局合并已有堆叠并使用空槽；装不下的剩余物仍正常掉落。旧公开 API 继续作为能力探测后的回退路径
- Minecraft 26.2 的可选 Litematica 最低兼容版本放宽至 `0.28.3`

- Automatic mining is no longer a separate mode. Ctrl-click the main toggle in any mining mode to arm it; each left click triggers exactly one player-centered search-and-mine batch, and clicking **AUTO** disarms it directly
- Added an automatic-mining cooldown and a high-visibility hazard HUD. Same-type adjacency targets the block beneath the player, while the other whitelist and shape algorithms continue to use the shared asynchronous search path
- Added persistent permission-level-4 commands: `/chainvein maxBlocks [1..2048]` (default `256`) and `/chainvein pickupRadius [0..64]` (default `10`)
- Server mining requests no longer use a hard-coded player-distance limit and never load chunks for a request; chain interactions defer to the vanilla server interaction-range check
- Quick Shulker now prefers its standard Fabric Storage API: carried storage slots are resolved once, then the complete overflow set is merged globally into existing stacks before empty slots are used. Remainders that do not fit still drop normally, and the legacy public API remains a capability-selected fallback
- The minimum optional Litematica version for Minecraft 26.2 is now `0.28.3`

### 测试 / Testing

- 自动化测试覆盖自动挖掘按次触发与冷却、原版掉落语义、冰与含物品容器、直接进入背包、快捷潜影盒容量边界、服务端限制和配置迁移
- Minecraft 26.2 的 CI 同时验证 Quick Shulker `3.0.2` 旧 API、`4.0.0-alpha.1-26.2` 新 API、强制旧适配路径，以及服务端未提供 ChainVein 协议时的纯客户端回退；26.1.x 验证公开的旧 API 与纯客户端回退

- Automated coverage includes guarded automatic-mining pulses and cooldowns, vanilla drop semantics, ice and populated containers, Direct to Inventory, Quick Shulker capacity boundaries, server limits, and configuration migration
- On Minecraft 26.2, CI validates the Quick Shulker `3.0.2` legacy API, the `4.0.0-alpha.1-26.2` direct API, the forced legacy adapter, and client-only fallback when the server does not advertise the ChainVein protocol; the 26.1.x build validates the published legacy API and client-only fallback

### 兼容性 / Compatibility

- 配置 schema 升级到 v5；现有 v4 配置会自动迁移，旧版的顺序迁移路径保持可用
- 4.1.0-alpha.1 没有修改 4.x 网络负载格式或公开 `ChainVeinClientApi` 方法签名；`isManualMiningMode()` 保留为兼容别名
- 本预发布同时提供 Minecraft 26.2 与 26.1.x 构建
- 4.x 与 1.x～3.x 的专用协议仍然分离，版本不匹配时安全退回纯客户端模式

- The configuration schema is now v5. Existing v4 configurations migrate automatically, and the sequential migration path from older schemas remains available
- 4.1.0-alpha.1 does not change the 4.x network payload format or public `ChainVeinClientApi` method signatures; `isManualMiningMode()` remains as a compatibility alias
- This prerelease provides builds for both Minecraft 26.2 and 26.1.x
- The 4.x dedicated protocol remains separate from 1.x–3.x, with mismatched versions safely falling back to client-side mode
