package org.edtp.chainveinfabric.client.gui.malilib;

import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.config.preset.ConfigPreset;
import org.edtp.chainveinfabric.client.config.preset.WhitelistPreset;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Preset page list, including preset selection, naming, creation, and deletion. */
final class PresetListWidget extends WidgetListBase<PresetListWidget.PresetRow,
        PresetListWidget.PresetEntryWidget> {
    private final GuiChainVein parent;

    PresetListWidget(int x, int y, int width, int height, GuiChainVein parent) {
        super(x, y, width, height, null);
        this.parent = parent;
        this.browserEntryHeight = 24;
    }

    @Override
    protected Collection<PresetRow> getAllEntries() {
        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        List<PresetRow> rows = new ArrayList<>();
        rows.add(PresetRow.whitelistHeader());
        rows.add(PresetRow.newWhitelist());
        for (WhitelistPreset preset : config.getWhitelistPresets(this.parent.presetWhitelistMode())) {
            rows.add(PresetRow.whitelist(preset));
        }
        rows.add(PresetRow.configHeader());
        rows.add(PresetRow.newConfig());
        for (ConfigPreset preset : config.getConfigPresets()) {
            rows.add(PresetRow.config(preset));
        }
        return rows;
    }

    @Override
    protected int getBrowserEntryHeightFor(PresetRow row) {
        return row.type == PresetRow.Type.WHITELIST_HEADER
                || row.type == PresetRow.Type.CONFIG_HEADER ? 28 : 24;
    }

    @Override
    protected PresetEntryWidget createListEntryWidget(int x, int y, int listIndex,
                                                      boolean isOdd, PresetRow row) {
        return new PresetEntryWidget(
                x, y, this.browserEntryWidth, getBrowserEntryHeightFor(row),
                isOdd, row, listIndex, this.parent);
    }

    @Override
    protected void reCreateListEntryWidgets() {
        this.parent.clearDropdowns();
        super.reCreateListEntryWidgets();
    }

    void refreshEntriesPreserveScroll() {
        int scroll = this.scrollBar.getValue();
        this.refreshEntries();
        this.scrollBar.setValue(scroll);
        this.reCreateListEntryWidgets();
    }

    @Override
    public boolean onKeyTyped(KeyEvent input) {
        for (PresetEntryWidget widget : this.listWidgets) {
            if (widget.onKeyTyped(input)) return true;
        }
        return super.onKeyTyped(input);
    }

    private static String nextWhitelistPresetName(ChainVeinConfig config,
                                                  ChainVeinConfig.ChainMode mode) {
        Set<String> names = new HashSet<>();
        for (WhitelistPreset preset : config.getWhitelistPresets(mode)) names.add(preset.name);
        return nextPresetName(names);
    }

    private static String nextConfigPresetName(ChainVeinConfig config) {
        Set<String> names = new HashSet<>();
        for (ConfigPreset preset : config.getConfigPresets()) names.add(preset.name);
        return nextPresetName(names);
    }

    private static String nextPresetName(Set<String> usedNames) {
        int index = 1;
        String baseName = StringUtils.translate("options.chainveinfabric.preset.newName");
        String name = baseName + " " + index;
        while (usedNames.contains(name)) {
            index++;
            name = baseName + " " + index;
        }
        return name;
    }

    static final class PresetRow {
        private enum Type {
            WHITELIST_HEADER, NEW_WHITELIST, WHITELIST,
            CONFIG_HEADER, NEW_CONFIG, CONFIG
        }

        private final Type type;
        private final WhitelistPreset whitelistPreset;
        private final ConfigPreset configPreset;

        private PresetRow(Type type, WhitelistPreset whitelistPreset, ConfigPreset configPreset) {
            this.type = type;
            this.whitelistPreset = whitelistPreset;
            this.configPreset = configPreset;
        }

        private static PresetRow whitelistHeader() {
            return new PresetRow(Type.WHITELIST_HEADER, null, null);
        }

        private static PresetRow newWhitelist() {
            return new PresetRow(Type.NEW_WHITELIST, null, null);
        }

        private static PresetRow whitelist(WhitelistPreset preset) {
            return new PresetRow(Type.WHITELIST, preset, null);
        }

        private static PresetRow configHeader() {
            return new PresetRow(Type.CONFIG_HEADER, null, null);
        }

        private static PresetRow newConfig() {
            return new PresetRow(Type.NEW_CONFIG, null, null);
        }

        private static PresetRow config(ConfigPreset preset) {
            return new PresetRow(Type.CONFIG, null, preset);
        }
    }

    static final class PresetEntryWidget extends WidgetListEntryBase<PresetRow> {
        private final boolean isOdd;
        private final GuiChainVein parentScreen;
        private GuiTextFieldGeneric nameField;
        private String lastSavedName;
        private int headerLabelWidth;

        private PresetEntryWidget(int x, int y, int width, int height,
                                  boolean isOdd, PresetRow row, int listIndex,
                                  GuiChainVein parentScreen) {
            super(x, y, width, height, row, listIndex);
            this.isOdd = isOdd;
            this.parentScreen = parentScreen;

            int buttonWidth = Math.min(58, Math.max(36, (width - 50) / 2));
            int nameWidth = Math.max(1, width - buttonWidth * 2 - 30);
            int buttonY = y + (height - 20) / 2;

            switch (row.type) {
                case WHITELIST_HEADER -> addWhitelistHeader(x, y, width, buttonY);
                case NEW_WHITELIST -> addNewWhitelistButton(x, buttonY, buttonWidth);
                case WHITELIST -> addWhitelistRow(x, buttonY, buttonWidth, nameWidth, row);
                case NEW_CONFIG -> addNewConfigButton(x, buttonY, buttonWidth);
                case CONFIG -> addConfigRow(x, buttonY, buttonWidth, nameWidth, row);
                default -> {
                }
            }
        }

        private void addWhitelistHeader(int x, int y, int width, int buttonY) {
            List<ChainVeinConfig.ChainMode> modes = this.parentScreen.availableModes();
            String header = StringUtils.translate("options.chainveinfabric.preset.whitelist");
            int preferredLabelWidth = this.textRenderer.width(header) + 10;
            int minimumDropdownWidth = Math.min(120, Math.max(70, width / 2));
            int labelWidth = Math.min(
                    preferredLabelWidth, Math.max(50, width - minimumDropdownWidth - 4));
            int dropdownWidth = Math.max(1, width - labelWidth - 4);
            this.headerLabelWidth = Math.max(1, labelWidth - 4);

            OverlayDropdown<ChainVeinConfig.ChainMode> modeDropdown = new OverlayDropdown<>(
                    x + labelWidth, buttonY, dropdownWidth, 20,
                    200, 5, modes, this.parentScreen::modeString) {
                @Override
                protected void setSelectedEntry(int index) {
                    super.setSelectedEntry(index);
                    ChainVeinConfig.ChainMode selected = this.getSelectedEntry();
                    if (selected != null && parentScreen.presetWhitelistMode() != selected) {
                        parentScreen.selectPresetWhitelistMode(selected);
                    }
                }
            };
            modeDropdown.setSelectedEntry(this.parentScreen.presetWhitelistMode());
            this.parentScreen.registerDropdown(modeDropdown);
            this.addWidget(modeDropdown);
        }

        private void addNewWhitelistButton(int x, int buttonY, int buttonWidth) {
            ButtonGeneric button = new ButtonGeneric(
                    x + 2, buttonY, buttonWidth, 20,
                    StringUtils.translate("options.chainveinfabric.preset.new"));
            this.addButton(button, (ignored, mouseButton) -> {
                ChainVeinConfig config = ChainveinfabricClient.CONFIG;
                ChainVeinConfig.ChainMode mode = this.parentScreen.presetWhitelistMode();
                config.createWhitelistPreset(mode, nextWhitelistPresetName(config, mode));
                config.save();
                this.parentScreen.refreshPresetList();
            });
        }

        private void addWhitelistRow(int x, int buttonY, int buttonWidth,
                                     int nameWidth, PresetRow row) {
            ChainVeinConfig.ChainMode mode = this.parentScreen.presetWhitelistMode();
            boolean active = row.whitelistPreset.id.equals(
                    ChainveinfabricClient.CONFIG.getActiveWhitelistPresetId(mode));
            this.nameField = new GuiTextFieldGeneric(
                    x + 2, buttonY, nameWidth, 20, this.textRenderer);
            this.nameField.setValue(row.whitelistPreset.name);
            this.nameField.setEditable(!active);
            this.lastSavedName = row.whitelistPreset.name;

            ButtonGeneric useButton = new ButtonGeneric(
                    x + nameWidth + 10, buttonY, buttonWidth, 20,
                    StringUtils.translate(active
                            ? "options.chainveinfabric.preset.active"
                            : "options.chainveinfabric.preset.use"));
            useButton.setEnabled(!active);
            this.addButton(useButton, (ignored, mouseButton) -> {
                if (ChainveinfabricClient.CONFIG.useWhitelistPreset(
                        this.parentScreen.presetWhitelistMode(), row.whitelistPreset.id)) {
                    ChainveinfabricClient.CONFIG.mode = this.parentScreen.presetWhitelistMode();
                    ChainveinfabricClient.CONFIG.save();
                    ConfigProxies.load();
                    this.parentScreen.refreshLists();
                    this.parentScreen.refreshPresetList();
                }
            });

            ButtonGeneric deleteButton = new ButtonGeneric(
                    x + nameWidth + buttonWidth + 15, buttonY, buttonWidth, 20,
                    StringUtils.translate("options.chainveinfabric.preset.delete"));
            deleteButton.setEnabled(!active);
            this.addButton(deleteButton, (ignored, mouseButton) -> {
                if (ChainveinfabricClient.CONFIG.deleteWhitelistPreset(
                        this.parentScreen.presetWhitelistMode(), row.whitelistPreset.id)) {
                    ChainveinfabricClient.CONFIG.save();
                    this.parentScreen.refreshPresetList();
                }
            });
        }

        private void addNewConfigButton(int x, int buttonY, int buttonWidth) {
            ButtonGeneric button = new ButtonGeneric(
                    x + 2, buttonY, buttonWidth, 20,
                    StringUtils.translate("options.chainveinfabric.preset.new"));
            this.addButton(button, (ignored, mouseButton) -> {
                ChainVeinConfig config = ChainveinfabricClient.CONFIG;
                config.createConfigPreset(nextConfigPresetName(config));
                config.save();
                this.parentScreen.refreshPresetList();
            });
        }

        private void addConfigRow(int x, int buttonY, int buttonWidth,
                                  int nameWidth, PresetRow row) {
            boolean active = row.configPreset.id.equals(
                    ChainveinfabricClient.CONFIG.activeConfigPresetId);
            this.nameField = new GuiTextFieldGeneric(
                    x + 2, buttonY, nameWidth, 20, this.textRenderer);
            this.nameField.setValue(row.configPreset.name);
            this.nameField.setEditable(!active);
            this.lastSavedName = row.configPreset.name;

            ButtonGeneric useButton = new ButtonGeneric(
                    x + nameWidth + 10, buttonY, buttonWidth, 20,
                    StringUtils.translate(active
                            ? "options.chainveinfabric.preset.active"
                            : "options.chainveinfabric.preset.use"));
            useButton.setEnabled(!active);
            this.addButton(useButton, (ignored, mouseButton) -> {
                if (ChainveinfabricClient.CONFIG.useConfigPreset(row.configPreset.id)) {
                    this.parentScreen.ensureAvailableMode();
                    ChainveinfabricClient.CONFIG.save();
                    ConfigProxies.load();
                    this.parentScreen.refreshLists();
                    this.parentScreen.refreshPresetList();
                }
            });

            ButtonGeneric deleteButton = new ButtonGeneric(
                    x + nameWidth + buttonWidth + 15, buttonY, buttonWidth, 20,
                    StringUtils.translate("options.chainveinfabric.preset.delete"));
            deleteButton.setEnabled(!active);
            this.addButton(deleteButton, (ignored, mouseButton) -> {
                if (ChainveinfabricClient.CONFIG.deleteConfigPreset(row.configPreset.id)) {
                    ChainveinfabricClient.CONFIG.save();
                    this.parentScreen.refreshPresetList();
                }
            });
        }

        @Override
        public void render(GuiContext context, int mouseX, int mouseY, boolean selected) {
            if (this.entry != null && this.entry.type == PresetRow.Type.WHITELIST_HEADER) {
                String label = fitText(
                        StringUtils.translate("options.chainveinfabric.preset.whitelist"),
                        this.headerLabelWidth);
                this.drawString(context, this.x + 2, this.y + 8, 0xFFFFFFFF, label);
                super.render(context, mouseX, mouseY, selected);
                return;
            }
            if (this.entry != null && this.entry.type == PresetRow.Type.CONFIG_HEADER) {
                String label = fitText(
                        StringUtils.translate("options.chainveinfabric.preset.config"),
                        Math.max(1, this.width - 4));
                this.drawString(context, this.x + 2, this.y + 8, 0xFFFFFFFF, label);
                return;
            }

            if (selected || this.isMouseOver(mouseX, mouseY)) {
                RenderUtils.drawRect(context, this.x, this.y, this.width, this.height, 0x50FFFFFF);
            } else if (this.isOdd) {
                RenderUtils.drawRect(context, this.x, this.y, this.width, this.height, 0x20FFFFFF);
            }
            if (this.nameField != null) {
                this.nameField.extractRenderState(context.getGuiGraphics(), mouseX, mouseY, 0.0F);
            }
            super.render(context, mouseX, mouseY, selected);
        }

        @Override
        public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick) {
            if (this.nameField != null && this.nameField.mouseClicked(click, doubleClick)) return true;
            if (this.nameField != null && !this.nameField.isMouseOver(click.x(), click.y())) {
                this.nameField.setFocused(false);
            }
            return super.onMouseClicked(click, doubleClick);
        }

        @Override
        public boolean onKeyTyped(KeyEvent input) {
            if (this.nameField != null && this.nameField.isFocused()) {
                String before = this.nameField.getValue();
                boolean handled = this.nameField.keyPressed(input);
                if (!before.equals(this.nameField.getValue())) saveName();
                return handled;
            }
            return super.onKeyTyped(input);
        }

        @Override
        public boolean onCharTyped(CharacterEvent input) {
            if (this.nameField != null && this.nameField.isFocused()) {
                String before = this.nameField.getValue();
                boolean handled = this.nameField.charTyped(input);
                if (!before.equals(this.nameField.getValue())) saveName();
                return handled;
            }
            return super.onCharTyped(input);
        }

        @Override
        public boolean canSelectAt(MouseButtonEvent click) {
            return false;
        }

        private void saveName() {
            if (this.entry == null || this.nameField == null
                    || this.nameField.getValue().equals(this.lastSavedName)) return;

            boolean saved = false;
            if (this.entry.type == PresetRow.Type.WHITELIST
                    && this.entry.whitelistPreset != null) {
                saved = ChainveinfabricClient.CONFIG.renameWhitelistPreset(
                        this.parentScreen.presetWhitelistMode(),
                        this.entry.whitelistPreset.id, this.nameField.getValue());
            } else if (this.entry.type == PresetRow.Type.CONFIG
                    && this.entry.configPreset != null) {
                saved = ChainveinfabricClient.CONFIG.renameConfigPreset(
                        this.entry.configPreset.id, this.nameField.getValue());
            }

            if (saved) {
                this.lastSavedName = this.nameField.getValue();
                ChainveinfabricClient.CONFIG.save();
            }
        }

        private String fitText(String text, int maxWidth) {
            if (this.textRenderer.width(text) <= maxWidth) return text;
            String ellipsis = "…";
            int contentWidth = maxWidth - this.textRenderer.width(ellipsis);
            return contentWidth > 0
                    ? this.textRenderer.plainSubstrByWidth(text, contentWidth) + ellipsis
                    : "";
        }
    }
}
