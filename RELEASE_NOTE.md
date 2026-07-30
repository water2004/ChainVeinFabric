## ChainVeinFabric v3.1.0

> [!IMPORTANT]
> **兼容性说明：3.x 及以后版本不再支持 Minecraft 1.21.x。** `2.2.1` 是 Minecraft 1.21.x 系列的最终版本。3.1.0 仅提供 Minecraft 26.2 与 26.1.x 构建。
>
> **Compatibility: Minecraft 1.21.x is no longer supported starting with the 3.x series.** `2.2.1` is the final release for Minecraft 1.21.x. Version 3.1.0 is released only for Minecraft 26.2 and 26.1.x.

### 支持版本 / Supported versions

- Minecraft 26.2：需要 MaLiLib 0.29.2 或更高版本；可选安装 Litematica 0.28.4 或更高版本
- Minecraft 26.1.x：需要 MaLiLib 0.28.8 或更高版本；可选安装 Litematica 0.27.10 或更高版本
- 未安装 Litematica 时，三个投影模式及其直达快捷键不会显示，其他功能不受影响

- Minecraft 26.2: requires MaLiLib 0.29.2 or newer; Litematica 0.28.4 or newer is optional
- Minecraft 26.1.x: requires MaLiLib 0.28.8 or newer; Litematica 0.27.10 or newer is optional
- Without Litematica installed, the three schematic modes and their direct hotkeys remain hidden; all other features continue to work

### 主要更新 / Highlights

- 新增“切换到下一个模式”快捷键；循环切换只经过当前可用的模式
- 为六种模式分别新增直达快捷键，可一键切换至采集、种植、交互或任一投影模式
- 快捷键页面新增“模式快捷键切换后开启连锁”开关，可在切换模式的同时立即启用连锁
- 未安装 Litematica 时，不注册、不显示三个投影模式的直达快捷键，循环切换也会自动跳过这些模式
- 种植模式下，目标白名单快捷键现在读取主手物品；只有可种植物品才能加入或移出白名单
- GUI、白名单快捷键和实际连锁种植共用同一套可种植物品判定，避免配置与执行行为不一致
- 配置界面改为响应式布局：高 GUI Scale 或较窄窗口下会自动换行控件、调整列表宽度，并在空间不足时切换为单列表页签；预设页和长方块名称也会自适应可用宽度
- 更新项目文档：默认 README 改为英文，并提供完整的简体中文版本

- Added a Next Mode hotkey that cycles through currently available modes only
- Added a direct hotkey for each of the six modes, allowing one-key switching to Mining, Planting, Utility, or any schematic mode
- Added an Enable Chaining after Mode Hotkey option that can turn chaining on immediately when a mode hotkey is used
- When Litematica is absent, the three schematic direct hotkeys are neither registered nor shown, and mode cycling skips those modes
- In Planting mode, the target-whitelist hotkey now reads the main-hand item and accepts only plantable items
- The GUI, whitelist hotkey, and actual chain-planting execution now share one plantable-item check so configuration and behavior remain consistent
- Made the configuration GUI responsive: high GUI scales and narrow windows now wrap controls, resize lists, switch to a single-list tab layout when needed, and keep preset rows and long block names within the available width
- Updated the project documentation with English as the default README and a complete Simplified Chinese version
