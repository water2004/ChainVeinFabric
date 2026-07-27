## ChainVeinFabric v3.0.0

> [!IMPORTANT]
> **兼容性说明：3.x 及以后版本不再支持 Minecraft 1.21.x。** `2.2.1` 是 Minecraft 1.21.x 系列的最终版本。3.0.0 仅提供 Minecraft 26.2 与 26.1.x 构建。
>
> **Compatibility: Minecraft 1.21.x is no longer supported starting with the 3.x series.** `2.2.1` is the final release for Minecraft 1.21.x. Version 3.0.0 is released only for Minecraft 26.2 and 26.1.x.

### 支持版本 / Supported versions

- Minecraft 26.2：需要 MaLiLib 0.29.2 或更高版本；可选安装 Litematica 0.28.4 或更高版本
- Minecraft 26.1.x：需要 MaLiLib 0.28.8 或更高版本；可选安装 Litematica 0.27.10 或更高版本
- 未安装 Litematica 时，新增的三个投影模式不会显示，其他功能不受影响

- Minecraft 26.2: requires MaLiLib 0.29.2 or newer; Litematica 0.28.4 or newer is optional
- Minecraft 26.1.x: requires MaLiLib 0.28.8 or newer; Litematica 0.27.10 or newer is optional
- Without Litematica installed, the three new schematic modes remain hidden and all other features continue to work

### 主要更新 / Highlights

- 新增“投影：选区方块”“投影：多余方块”“投影：错误方块”三种模式
- “投影：选区方块”使用 Litematica 木棍工具当前框选的区域，并匹配区域内实际存在的方块
- “投影：多余方块”和“投影：错误方块”使用已启用的原理图放置区域，分别匹配投影中应为空气的多余方块，以及实际方块与投影方块不一致的位置
- 三种投影模式拥有相互独立的白名单；既可手动编辑，也可一键扫描当前模式范围并覆盖当前白名单预设
- 白名单导入采用分帧扫描，不等待未加载区块；未加载区块会被直接跳过并计入结果
- 多余/错误方块模式新增“渲染层”开关，可选择是否遵循 Litematica 当前渲染层级限制
- 配置 schema 升级至 v3，并提供从 v2 到 v3 的一次性迁移
- 白名单统一保存和匹配物品 ID；方块变体会归一为对应物品，旧的 v2 配置会在迁移时自动转换
- 精简投影白名单控件布局；新增共享客户端作业 API，统一处理挖掘、种植和交互任务的去重、排队、服务端批量发送及原版客户端回退

- Added three modes: Schematic Selection Blocks, Schematic Extra Blocks, and Schematic Wrong Blocks
- Schematic Selection Blocks uses the current area selected with Litematica's stick tool and matches actual blocks inside that area
- Extra and Wrong modes use enabled schematic placements, matching unexpected blocks where the schematic expects air and placed blocks that differ from the schematic respectively
- Each schematic mode has an independent whitelist that can be edited manually or overwritten by scanning the current mode scope
- Whitelist imports are processed incrementally and never wait for unloaded chunks; unloaded chunks are skipped and reported
- Extra and Wrong modes include a Layer toggle for optionally respecting Litematica's current render-layer restriction
- Upgraded the configuration schema to v3 with a one-time v2-to-v3 migration
- Whitelists now consistently store and match item IDs, normalizing block variants to their corresponding items during migration
- Compacted the schematic whitelist controls and added a shared client job API for deduplicating, queuing, batching, and providing vanilla-client fallbacks for mining, planting, and interaction jobs
