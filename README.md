# ChainVeinFabric

[简体中文](README_zh_CN.md)

A modern, efficient, and configurable chain mining and interaction mod for Minecraft Fabric.
It works on vanilla servers in client-side mode and gains additional capabilities when installed on the server.

> **Version support:** ChainVeinFabric 3.x supports the Minecraft 26.1.x and 26.2.x release lines. Version 3.x and later no longer support Minecraft 1.21.x; use ChainVeinFabric 2.2.1 for 1.21.x.

---

## ✨ Features

- **Chain Mining:** Mine connected matching or whitelisted blocks automatically.
- **Chain Planting:** Plant compatible crops across matching soil. The planting whitelist accepts plantable items and the target-whitelist hotkey reads the item in your main hand.
- **Chain Utility:** Batch wax copper, scrape oxidation, strip logs, till soil, and perform similar item interactions.
- **Search Algorithms:** Same-type adjacency, whitelist adjacency, sphere, square plane, and cuboid searches. Edge and corner adjacency are configurable.
- **Independent Whitelists and Presets:** Maintain a separate whitelist for every mode, create reusable whitelist presets, and save complete configuration presets.
- **Mode Hotkeys:** Configure a key to cycle to the next available mode and a direct key for every mode. An optional setting immediately enables chaining after a mode hotkey is used.
- **Search Outlines:** Preview the blocks found by the current search. Each Litematica mode has its own outline color.
- **Tool Protection:** Dynamically limits the chain count to preserve a 10-durability safety buffer.
- **Anti-Kick Protection:** Configure the packet interval used with vanilla servers.
- **Optional Server Support:** A server installation enables efficient batch processing and Direct to Inventory.
- **Optional Quick Shulker Overflow:** After Direct to Inventory fills the player inventory, remaining drops can be stored in carried shulker boxes.

### Optional Litematica integration

When Litematica is installed, ChainVeinFabric exposes three additional mining modes:

- **Schematic: Selection Blocks:** Matches non-air world blocks inside the current Litematica area selection created with its stick selection tool. This mode uses the area selection, not a schematic placement.
- **Schematic: Extra Blocks:** Matches placed world blocks where enabled schematic placements expect air.
- **Schematic: Wrong Blocks:** Matches placed world blocks whose state differs from the non-air state expected by enabled schematic placements.

The Extra and Wrong modes can optionally follow Litematica's current render-layer range. Their search and whitelist import both respect this setting. Litematica-only modes and direct hotkeys are hidden when Litematica is not installed, and mode cycling skips them.

Every schematic mode supports manual whitelist editing and one-click import. Import replaces the active whitelist with the matching block types found in the current mode scope. It scans loaded chunks immediately and skips unloaded chunks without waiting for them.

### Optional Quick Shulker integration

When the compatible [Quick Shulker fork](https://github.com/water2004/quickshulker) is installed on both the client and server, the Advanced page exposes **Overflow to Shulker Boxes**. This setting only applies when **Direct to Inventory** is enabled.

This integration is currently available in the Minecraft 26.2 build.

ChainVein first inserts drops into the normal player inventory, then tries carried shulker boxes in inventory order. It uses Quick Shulker's public insertion rules, so nested shulker boxes remain prohibited. Anything that still does not fit drops into the world normally. The integration is optional and its setting is hidden when Quick Shulker is absent.

---

## 📸 Screenshots

![Mining Config](screenshots/image.png)
> *Description: Mining mode configuration with block whitelist.*

![Chain Mine Result](screenshots/image-2.png)
> *Description: Action Bar feedback after chain mining blocks.*

![Planting Config](screenshots/image-3.png)
> *Description: Chain Planting configuration showing the item whitelist (seeds/crops).*

![Chain Planting](screenshots/image-4.png)
> *Description: Chain planting carrots in a large area with a single click.*

![Utility Config](screenshots/image-5.png)
> *Description: Chain Wax/Scrape/Strip configuration showing the "Applicable Blocks" whitelist.*

![Chain Stripping](screenshots/image-6.png)
> *Description: Stripping a whole stack of logs instantly using an axe.*

---

## 🛠️ Usage

1. Press **`V`** to open the configuration screen.
2. Select Mining, Planting, or Utility mode. The three schematic modes also appear when Litematica is installed.
3. Configure the active whitelist:
   - In Planting mode, use a plantable item such as `carrot` or `wheat_seeds`. The target-whitelist hotkey adds or removes the plantable item held in your main hand.
   - In other modes, the target-whitelist hotkey adds or removes the block under your crosshair.
   - In a schematic mode, you may instead import and replace the whitelist from the current mode scope.
4. Open the Hotkeys page to bind next-mode, direct-mode, chain toggle, and target-whitelist shortcuts. Enable **Enable Chaining after Mode Hotkey** if switching modes should also turn chaining on.
5. Perform the corresponding action:
   - Break a matching block to mine.
   - Right-click compatible soil while holding a whitelisted plantable item to plant.
   - Right-click with the relevant tool or item to run a utility interaction.

---

## ⚙️ Main Configuration Options

| Option | Description |
| :--- | :--- |
| **Chain Mode** | Mining, Planting, Utility, or one of the three optional Litematica modes. |
| **Search Algorithm** | Same-type adjacency, whitelist adjacency, sphere, square plane, or cuboid. |
| **Max Blocks** | Maximum number of blocks or interactions per action. |
| **Max Radius** | Maximum distance from the initial position. |
| **Diagonal Edge / Corner** | Include edge-connected or corner-connected neighbors in adjacency searches. |
| **Packet Interval** | Delay between vanilla-server packets. |
| **Tool Protection** | Limits the operation to preserve a 10-durability buffer. |
| **Direct to Inventory** | Requires ChainVeinFabric on the server and is disabled otherwise. |
| **Overflow to Shulker Boxes** | Requires Quick Shulker on both sides; stores Direct to Inventory overflow in carried shulker boxes. |
| **Show Outlines** | Displays a preview of the current search result. |
| **Respect Render Layer** | Extra/Wrong modes only; limits matching and import to Litematica's render range. |
| **Mode Hotkeys** | Cycle to the next available mode or switch directly to any available mode. |
| **Enable Chaining after Mode Hotkey** | Turns chaining on immediately after a mode shortcut is used. |

---

## 🤝 Compatibility

### Minecraft and dependencies

- **Minecraft:** Current 3.x builds target Minecraft 26.1.x and 26.2.x. Minecraft 1.21.x remains on ChainVeinFabric 2.2.1.
- **MaLiLib:** Required on the client.
- **Litematica:** Optional. Only the three schematic modes depend on it.
- **Quick Shulker:** Optional. Enables shulker-box storage for Direct to Inventory overflow when installed on both sides.
- **Mod Menu:** Optional configuration entry point.

### Client and server behavior

1. **Vanilla server (mod not installed):**
   - The client performs the search and sends standard interaction packets.
   - Use **Packet Interval** if a server rejects packets sent too quickly.
   - **Direct to Inventory** is unavailable.
2. **Modded server (mod installed):**
   - The client sends the resolved positions through the ChainVein protocol.
   - The server processes them efficiently and supports **Direct to Inventory**.
   - With compatible Quick Shulker installations on both sides, overflow can be stored in carried shulker boxes.
   - The packet interval is not required.

---

## Client Job API

Other client-side mods can submit resolved positions through the same queue used by ChainVeinFabric:

```java
ChainVeinClientApi.queueMineJobs(client, positions);
ChainVeinClientApi.queuePlantJobs(client, positions);
ChainVeinClientApi.queueUseJobs(client, positions);
```

The API deduplicates jobs, applies the current Direct to Inventory and Tool Protection settings, and automatically chooses the dedicated-server protocol or vanilla client packets. Direct to Inventory takes effect only when the server has ChainVeinFabric installed. Callers should declare ChainVeinFabric as an optional client dependency and invoke the API only after confirming that the mod is loaded.

---

## 📝 License

ChainVeinFabric is licensed under the GPL-3.0 License.
