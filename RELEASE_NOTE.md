## ChainVeinFabric v4.0.1

### 修复 / Fixes

- 移除跨操作保留五秒的挖掘坐标去重状态；连续快速执行多个重叠范围时，后续范围不再只破坏一部分，也会正常显示破坏数量
- 服务端协议可用时恢复即时发送挖掘批次，确保开启“直接进入背包”后，玩家最先破坏的方块也进入背包而不是掉落在地
- 配置界面只显示实际存在的说明翻译，不再显示 MaLiLib 自动生成的 `Comment?` 占位文本
- 纯客户端回退模式继续遵循“发包间隔”，重复坐标会由服务端按空气方块安全忽略

- Removed the five-second cross-operation mining-coordinate lock; rapidly chaining overlapping areas no longer leaves later areas partially mined, and the affected-block message is shown normally
- Restored immediate mining-batch dispatch when the dedicated server protocol is available, so the initially broken block also honors Direct to Inventory instead of dropping on the ground
- Config screens now show only real translated descriptions instead of MaLiLib's generated `Comment?` placeholders
- Client-side fallback mode continues to honor Packet Interval, while duplicate coordinates are safely ignored as air by the server

### 兼容性 / Compatibility

- 4.0.1 没有修改 4.x 网络协议、配置 schema 或公开 `ChainVeinClientApi` 方法签名，可与 4.0.0 服务端/客户端混用
- Minecraft 26.2 与 26.1.x 均继续维护；Minecraft 1.21.x 继续使用 ChainVeinFabric 2.2.1
- 4.x 与 1.x～3.x 的专用协议仍不兼容，混用时会安全退回纯客户端模式；直接进入背包和快捷潜影盒溢出收纳需要双方均为 4.x

- 4.0.1 does not change the 4.x network protocol, config schema, or public `ChainVeinClientApi` method signatures and can interoperate with 4.0.0 clients/servers
- Minecraft 26.2 and 26.1.x remain maintained; Minecraft 1.21.x remains on ChainVeinFabric 2.2.1
- The 4.x dedicated protocol remains incompatible with 1.x–3.x; mixed versions safely fall back to client-side mode, while Direct to Inventory and Quick Shulker overflow require 4.x on both sides
