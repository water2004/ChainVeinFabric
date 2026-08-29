package org.edtp.chainveinfabric.client.config;

import org.edtp.chainveinfabric.client.config.ChainVeinConfig.ChainMode;
import org.edtp.chainveinfabric.client.config.preset.ConfigPreset;
import org.edtp.chainveinfabric.client.config.preset.WhitelistPreset;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Owns preset lifecycle and the runtime projection of active whitelists. */
final class ConfigPresetManager {
    private final ChainVeinConfig config;
    private final Map<ChainMode, Set<String>> activeWhitelists = new EnumMap<>(ChainMode.class);

    ConfigPresetManager(ChainVeinConfig config) {
        this.config = config;
    }

    List<ConfigPreset> getConfigPresets() {
        if (this.config.configPresets == null) this.config.configPresets = new ArrayList<>();
        return this.config.configPresets;
    }

    ConfigPreset getActiveConfigPreset() {
        return getConfigPreset(this.config.activeConfigPresetId);
    }

    ConfigPreset getConfigPreset(String id) {
        if (id == null) return null;
        for (ConfigPreset preset : getConfigPresets()) {
            if (id.equals(preset.id)) return preset;
        }
        return null;
    }

    ConfigPreset createConfigPreset(String name) {
        ConfigPreset preset = ConfigPreset.create(
                UUID.randomUUID().toString(), sanitizeName(name, "Config Preset"), this.config);
        getConfigPresets().add(preset);
        return preset;
    }

    boolean useConfigPreset(String id) {
        ConfigPreset preset = getConfigPreset(id);
        if (preset == null) return false;
        this.config.activeConfigPresetId = preset.id;
        preset.applyTo(this.config);
        return true;
    }

    boolean renameConfigPreset(String id, String name) {
        ConfigPreset preset = getConfigPreset(id);
        if (preset == null || name == null || name.isBlank()) return false;
        preset.name = name.trim();
        return true;
    }

    boolean deleteConfigPreset(String id) {
        if (id == null || id.equals(this.config.activeConfigPresetId)) return false;
        return getConfigPresets().removeIf(preset -> id.equals(preset.id));
    }

    List<WhitelistPreset> getWhitelistPresets(ChainMode mode) {
        if (this.config.whitelistPresets == null) this.config.whitelistPresets = new LinkedHashMap<>();
        ensureWhitelistPreset(mode);
        return this.config.whitelistPresets.get(mode.name());
    }

    String getActiveWhitelistPresetId(ChainMode mode) {
        if (this.config.activeWhitelistPresetIds == null) {
            this.config.activeWhitelistPresetIds = new LinkedHashMap<>();
        }
        ensureWhitelistPreset(mode);
        return this.config.activeWhitelistPresetIds.get(mode.name());
    }

    WhitelistPreset getActiveWhitelistPreset(ChainMode mode) {
        return getWhitelistPreset(mode, getActiveWhitelistPresetId(mode));
    }

    WhitelistPreset getWhitelistPreset(ChainMode mode, String id) {
        if (id == null) return null;
        List<WhitelistPreset> presets = this.config.whitelistPresets != null
                ? this.config.whitelistPresets.get(mode.name())
                : null;
        if (presets == null) return null;
        for (WhitelistPreset preset : presets) {
            if (id.equals(preset.id)) return preset;
        }
        return null;
    }

    WhitelistPreset createWhitelistPreset(ChainMode mode, String name) {
        WhitelistPreset active = getActiveWhitelistPreset(mode);
        Set<String> entries = active != null ? active.entries : Set.of();
        WhitelistPreset preset = WhitelistPreset.create(
                UUID.randomUUID().toString(), sanitizeName(name, "Whitelist Preset"), entries);
        getWhitelistPresets(mode).add(preset);
        return preset;
    }

    boolean useWhitelistPreset(ChainMode mode, String id) {
        WhitelistPreset preset = getWhitelistPreset(mode, id);
        if (preset == null) return false;
        this.config.activeWhitelistPresetIds.put(mode.name(), preset.id);
        applyActiveWhitelistPreset(mode);
        return true;
    }

    boolean renameWhitelistPreset(ChainMode mode, String id, String name) {
        WhitelistPreset preset = getWhitelistPreset(mode, id);
        if (preset == null || name == null || name.isBlank()) return false;
        preset.name = name.trim();
        return true;
    }

    boolean deleteWhitelistPreset(ChainMode mode, String id) {
        if (id == null || id.equals(getActiveWhitelistPresetId(mode))) return false;
        return getWhitelistPresets(mode).removeIf(preset -> id.equals(preset.id));
    }

    void syncActiveConfigPresetFromCurrent() {
        ConfigPreset preset = getActiveConfigPreset();
        if (preset != null) {
            preset.captureFrom(this.config);
        }
    }

    void applyActiveWhitelistPreset(ChainMode mode) {
        WhitelistPreset preset = getActiveWhitelistPreset(mode);
        Set<String> entries = preset != null ? preset.entries : new HashSet<>();
        this.activeWhitelists.put(mode, entries);
    }

    Set<String> getWhitelist(ChainMode mode) {
        Set<String> whitelist = this.activeWhitelists.get(mode);
        if (whitelist == null) {
            applyActiveWhitelistPreset(mode);
            whitelist = this.activeWhitelists.get(mode);
        }
        return whitelist;
    }

    void replaceWhitelist(ChainMode mode, Set<String> entries) {
        replaceWhitelist(mode, getActiveWhitelistPresetId(mode), entries);
    }

    void replaceWhitelist(ChainMode mode, String presetId, Set<String> entries) {
        WhitelistPreset preset = getWhitelistPreset(mode, presetId);
        if (preset == null) return;

        preset.entries.clear();
        preset.entries.addAll(entries);
        if (preset.id.equals(getActiveWhitelistPresetId(mode))) {
            this.activeWhitelists.put(mode, preset.entries);
        }
    }

    void initializeDefaults() {
        this.config.configPresets = new ArrayList<>();
        this.config.configPresets.add(ConfigPreset.create(
                ConfigPreset.DEFAULT_ID, ConfigPreset.DEFAULT_NAME, this.config));
        this.config.activeConfigPresetId = ConfigPreset.DEFAULT_ID;
        this.config.whitelistPresets = new LinkedHashMap<>();
        this.config.activeWhitelistPresetIds = new LinkedHashMap<>();
        for (ChainMode mode : ChainMode.values()) {
            putDefaultWhitelistPreset(mode, Set.of());
        }
    }

    boolean repair() {
        if (this.config.configPresets == null) this.config.configPresets = new ArrayList<>();
        if (this.config.whitelistPresets == null) this.config.whitelistPresets = new LinkedHashMap<>();
        if (this.config.activeWhitelistPresetIds == null) {
            this.config.activeWhitelistPresetIds = new LinkedHashMap<>();
        }
        Set<String> presetModeKeys = Set.copyOf(this.config.whitelistPresets.keySet());
        Set<String> activeModeKeys = Set.copyOf(this.config.activeWhitelistPresetIds.keySet());

        if (this.config.configPresets.isEmpty()) {
            this.config.configPresets.add(ConfigPreset.create(
                    ConfigPreset.DEFAULT_ID, ConfigPreset.DEFAULT_NAME, this.config));
            this.config.activeConfigPresetId = ConfigPreset.DEFAULT_ID;
        }

        for (ConfigPreset preset : this.config.configPresets) {
            repairConfigPreset(preset);
        }
        if (getConfigPreset(this.config.activeConfigPresetId) == null) {
            this.config.activeConfigPresetId = this.config.configPresets.get(0).id;
        }

        for (ChainMode mode : ChainMode.values()) {
            ensureWhitelistPreset(mode);
        }

        boolean whitelistChanged = normalizeWhitelistEntries();
        boolean structureChanged = !presetModeKeys.equals(this.config.whitelistPresets.keySet())
                || !activeModeKeys.equals(this.config.activeWhitelistPresetIds.keySet());
        applyActivePresets();
        return whitelistChanged || structureChanged;
    }

    void prepareForSave() {
        syncActiveConfigPresetFromCurrent();
        for (ChainMode mode : ChainMode.values()) {
            ensureWhitelistPreset(mode);
        }
    }

    private void applyActivePresets() {
        ConfigPreset configPreset = getActiveConfigPreset();
        if (configPreset != null) {
            configPreset.applyTo(this.config);
        }
        for (ChainMode mode : ChainMode.values()) {
            applyActiveWhitelistPreset(mode);
        }
    }

    private void putDefaultWhitelistPreset(ChainMode mode, Set<String> entries) {
        List<WhitelistPreset> presets = new ArrayList<>();
        presets.add(WhitelistPreset.create(
                WhitelistPreset.DEFAULT_ID, WhitelistPreset.DEFAULT_NAME, entries));
        this.config.whitelistPresets.put(mode.name(), presets);
        this.config.activeWhitelistPresetIds.put(mode.name(), WhitelistPreset.DEFAULT_ID);
    }

    private void ensureWhitelistPreset(ChainMode mode) {
        if (this.config.whitelistPresets == null) this.config.whitelistPresets = new LinkedHashMap<>();
        if (this.config.activeWhitelistPresetIds == null) {
            this.config.activeWhitelistPresetIds = new LinkedHashMap<>();
        }
        List<WhitelistPreset> presets = this.config.whitelistPresets.computeIfAbsent(
                mode.name(), key -> new ArrayList<>());
        if (presets.isEmpty()) {
            presets.add(WhitelistPreset.create(
                    WhitelistPreset.DEFAULT_ID, WhitelistPreset.DEFAULT_NAME, Set.of()));
            this.config.activeWhitelistPresetIds.put(mode.name(), WhitelistPreset.DEFAULT_ID);
        }

        for (WhitelistPreset preset : presets) {
            repairWhitelistPreset(preset);
        }

        String activeId = this.config.activeWhitelistPresetIds.get(mode.name());
        if (getWhitelistPreset(mode, activeId) == null) {
            this.config.activeWhitelistPresetIds.put(mode.name(), presets.get(0).id);
        }
    }

    private boolean normalizeWhitelistEntries() {
        boolean changed = false;
        for (ChainMode mode : ChainMode.values()) {
            List<WhitelistPreset> presets = this.config.whitelistPresets.get(mode.name());
            if (presets == null) continue;

            for (WhitelistPreset preset : presets) {
                Set<String> normalized = new LinkedHashSet<>();
                for (String entry : preset.entries) {
                    String normalizedEntry = WhitelistEntryNormalizer.normalize(entry);
                    if (normalizedEntry != null) normalized.add(normalizedEntry);
                }
                if (!normalized.equals(preset.entries)) {
                    preset.entries = normalized;
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static void repairConfigPreset(ConfigPreset preset) {
        if (preset.id == null || preset.id.isBlank()) preset.id = UUID.randomUUID().toString();
        if (preset.name == null || preset.name.isBlank()) preset.name = ConfigPreset.DEFAULT_NAME;
        if (preset.mode == null) preset.mode = ChainMode.CHAIN_MINE;
        if (preset.searchAlgorithm == null) {
            preset.searchAlgorithm = ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME;
        }
        if (preset.squareMiningPoint == null) preset.squareMiningPoint = ChainVeinConfig.MiningPoint.CENTER;
        if (preset.cuboidMiningPoint == null) preset.cuboidMiningPoint = ChainVeinConfig.MiningPoint.CENTER;
        if (preset.autoMineCooldownTicks < 10 || preset.autoMineCooldownTicks > 200) {
            preset.autoMineCooldownTicks = 40;
        }
    }

    private static void repairWhitelistPreset(WhitelistPreset preset) {
        if (preset.id == null || preset.id.isBlank()) preset.id = UUID.randomUUID().toString();
        if (preset.name == null || preset.name.isBlank()) preset.name = WhitelistPreset.DEFAULT_NAME;
        if (preset.entries == null) preset.entries = new HashSet<>();
    }

    private static String sanitizeName(String name, String fallback) {
        return name == null || name.isBlank() ? fallback : name.trim();
    }
}
