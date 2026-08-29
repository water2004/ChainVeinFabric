## ChainVeinFabric v4.1.0-alpha.2

> 这是面向 Minecraft 26.2 与 26.1.x 的预发布测试版本。请在重要存档中谨慎使用自动挖掘，并在升级前备份。
>
> This is a prerelease build for Minecraft 26.2 and 26.1.x. Use automatic mining cautiously in important worlds and back them up before upgrading.

### 修复与改进 / Fixes and Improvements

- 纯客户端回退不再于同一 tick 发送开始与结束挖掘包；现在逐方块遵循原版挖掘进度、工具速度与方块间延迟
- 纯客户端挖掘会在状态 HUD 显示当前方块进度与请求进度，服务端批量挖掘路径不受影响
- 多次提交纯客户端挖掘请求时，会先完成当前已经开始的方块，再丢弃旧请求尚未开始的部分并切换到最新请求
- 稳定了纯客户端掉落测试场地，避免掉落实体滚出测试范围造成偶发误报

- Client-only fallback no longer sends start and stop mining packets in the same tick. Blocks are now mined one at a time using vanilla progress, tool speed, and the vanilla delay between blocks
- Client-only mining now shows both current-block progress and request progress in the status HUD; the dedicated-server batch path is unchanged
- When multiple client-only mining requests arrive, the currently started block finishes first, then the unstarted remainder of the old request is discarded in favor of the latest request
- Stabilized the client-only drop fixture so item entities cannot roll outside the assertion area and cause intermittent false failures

### 测试与集成 / Testing and Integration

- Minecraft 26.2 与 26.1.x 均验证 Quick Shulker `4.0.0-alpha.1` 的直接 Fabric Storage API 路径和强制旧适配路径
- 两个 Minecraft 分支均通过服务端 GameTest、纯客户端挖掘/种植/交互测试及完整构建

- Both Minecraft 26.2 and 26.1.x validate Quick Shulker `4.0.0-alpha.1` through the direct Fabric Storage API path and the forced legacy adapter path
- Both Minecraft branches pass server GameTests, client-only mining/planting/interaction tests, and full builds

### 兼容性 / Compatibility

- 配置 schema、4.x 网络负载格式及公开 `ChainVeinClientApi` 方法签名均未改变
- 本预发布同时提供 Minecraft 26.2 与 26.1.x 构建
- 4.x 与 1.x～3.x 的专用协议仍然分离，版本不匹配时安全退回纯客户端模式

- The configuration schema, 4.x network payload format, and public `ChainVeinClientApi` method signatures are unchanged
- This prerelease provides builds for both Minecraft 26.2 and 26.1.x
- The 4.x dedicated protocol remains separate from 1.x–3.x, with mismatched versions safely falling back to client-side mode
