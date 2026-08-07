## ChainVeinFabric v4.0.0

> [!IMPORTANT]
> **网络兼容性：** 4.x 服务端专用协议与 1.x～3.x 明确不兼容。版本不匹配时不会发送或解码 ChainVein 专用数据包，客户端会自动退回纯客户端模式；直接进入背包和快捷潜影盒溢出收纳将不可用。
>
> **Network compatibility:** The 4.x dedicated-server protocol is intentionally incompatible with 1.x–3.x. Mismatched versions do not send or decode ChainVein payloads and automatically fall back to client-side mode, where Direct to Inventory and Quick Shulker overflow are unavailable.

### 支持版本 / Supported versions

- Minecraft 26.2：需要 MaLiLib 0.29.2 或更高版本；可选安装 Litematica 0.28.4 或更高版本、Quick Shulker 3.0.2-26.2 或更高版本
- Minecraft 26.1.x：需要 MaLiLib 0.28.8 或更高版本；可选安装 Litematica 0.27.10 或更高版本、Quick Shulker 3.0.1-26.1 或更高版本
- Minecraft 1.21.x 继续使用 ChainVeinFabric 2.2.1

- Minecraft 26.2: requires MaLiLib 0.29.2 or newer; Litematica 0.28.4 or newer and Quick Shulker 3.0.2-26.2 or newer are optional
- Minecraft 26.1.x: requires MaLiLib 0.28.8 or newer; Litematica 0.27.10 or newer and Quick Shulker 3.0.1-26.1 or newer are optional
- Minecraft 1.21.x remains on ChainVeinFabric 2.2.1

### 相对 3.1.0 的变更 / Changes since 3.1.0

- 新增可选的“背包满后存入潜影盒”：直接进入背包后仍无法容纳的掉落物会继续尝试放入随身潜影盒
- 仅使用 Quick Shulker 的公开 API；未安装时不显示选项，也不会加载兼容实现
- 玩家背包和潜影盒都无法容纳的剩余物仍会正常掉落，不会吞物品
- 配置 schema 升级到 v4，并处理 v3 到 v4 的迁移；现有配置和配置预设默认关闭新功能
- 服务端专用协议升级为 `chainveinfabric:mine_v4` 与 `chainveinfabric:interact_v4`；旧版本会安全退回纯客户端模式
- `ChainVeinClientApi` 的公开方法签名保持不变

- Added optional **Overflow to Shulker Boxes** storage after Direct to Inventory fills the player inventory
- Uses only Quick Shulker's public API; the option and linked implementation stay unavailable when the mod is absent
- Items that fit in neither the player inventory nor carried shulker boxes still drop normally
- Upgraded the config schema to v4 with v3-to-v4 migration; existing configs and presets default the new feature to off
- Versioned the dedicated-server protocol as `chainveinfabric:mine_v4` and `chainveinfabric:interact_v4`; older versions safely fall back to client-side mode
- Kept all public `ChainVeinClientApi` method signatures unchanged

### 升级须知 / Upgrade notes

- 如需使用服务端专用发包、直接进入背包或快捷潜影盒溢出收纳，客户端与服务端必须同时升级到 4.x
- 4.x 与 1.x～3.x 混用时不会断开连接，但只能使用纯客户端模式；纯客户端模式继续遵循发包间隔
- v3 配置会在首次加载时自动迁移到 v4，新选项默认关闭；无需手动重建配置或预设
- 快捷潜影盒仍是可选依赖；未安装时不会显示新选项，其他连锁功能不受影响

- Both client and server must run 4.x to use the dedicated protocol, Direct to Inventory, or Quick Shulker overflow
- Mixing 4.x with 1.x–3.x does not disconnect the player, but only client-side mode is available and continues to honor Packet Interval
- v3 configurations migrate to v4 automatically on first load; the new option defaults to off, so configs and presets do not need to be recreated
- Quick Shulker remains optional; without it the new option stays hidden and all other chaining features continue to work
