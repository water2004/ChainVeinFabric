## ChainVeinFabric v4.0.2

### 修复 / Fixes

- 修复连锁种植将首次右键已消耗的物品重复计入限制，导致一组 64 个种子只能种下 63 个的问题；现在会正确种下全部 64 个
- 统一按“首次原版交互 + 后续连锁交互”计算最大方块数、物品数量与工具保护限制
- 新增自动化行为测试，覆盖真实客户端种植、服务端连锁采集与直接进背包、搜索算法、配置迁移、模式语义、可种植物品及可选依赖回退

- Fixed chain planting counting the item already consumed by the initial vanilla interaction twice, which left one seed behind from a stack of 64; all 64 seeds are now planted
- Unified Max Blocks, available-item, and tool-protection limits around the initial vanilla interaction plus queued chain interactions
- Added automated behavior coverage for real client planting, server-side chain mining and direct inventory collection, search algorithms, config migration, mode semantics, plantable items, and optional-dependency fallbacks

### 兼容性 / Compatibility

- 4.0.2 没有修改 4.x 网络协议、配置 schema 或公开 `ChainVeinClientApi` 方法签名，可与 4.0.0/4.0.1 服务端和客户端混用
- Minecraft 26.2 与 26.1.x 均继续维护；Minecraft 1.21.x 继续使用 ChainVeinFabric 2.2.1
- 4.x 与 1.x～3.x 的专用协议仍不兼容，混用时会安全退回纯客户端模式；直接进入背包和快捷潜影盒溢出收纳需要双方均为 4.x

- 4.0.2 does not change the 4.x network protocol, config schema, or public `ChainVeinClientApi` method signatures and can interoperate with 4.0.0/4.0.1 clients and servers
- Minecraft 26.2 and 26.1.x remain maintained; Minecraft 1.21.x remains on ChainVeinFabric 2.2.1
- The 4.x dedicated protocol remains incompatible with 1.x–3.x; mixed versions safely fall back to client-side mode, while Direct to Inventory and Quick Shulker overflow require 4.x on both sides
