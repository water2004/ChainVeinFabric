## ChainVeinFabric v2.1.3-26.2

### 变更 / Changes

- 迁移至 Minecraft 26.2 / Fabric API 0.153.0+26.2 / MaLiLib 0.29.1
- 修复连锁采集模式下无法将作物方块加入白名单的问题
- 修复骨粉等客户端返回 PASS 的物品无法触发连锁的问题
- 修复自定义包处理器在创造模式下消耗物品的问题

- Migrated to Minecraft 26.2 / Fabric API 0.153.0+26.2 / MaLiLib 0.29.1
- Fixed crop blocks not being addable to whitelist in Chain Mine mode
- Fixed items like bone meal (which return PASS on client) not triggering chain actions
- Fixed items being consumed in Creative mode when using the custom packet handler
