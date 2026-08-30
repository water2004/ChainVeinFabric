package org.edtp.chainveinfabric.client.gui.malilib;

import fi.dy.masa.malilib.config.ConfigType;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigBoolean;
import fi.dy.masa.malilib.config.IConfigOptionList;
import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.config.IConfigResettable;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.ConfigButtonBoolean;
import fi.dy.masa.malilib.gui.interfaces.IKeybindConfigGui;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetHoverInfo;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptions;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Config list that reserves scrollbar/reset space before sizing labels and controls. */
final class ResponsiveConfigList extends WidgetListConfigOptions {
    private static final int LABEL_GAP = 10;
    private static final int RESET_GAP = 2;
    private static final int SCROLLBAR_CLEARANCE = 6;
    private static final int MIN_LABEL_WIDTH = 40;
    private static final int MIN_CONTROL_WIDTH = 40;

    private final IKeybindConfigGui hostScreen;
    private final Consumer<OverlayDropdown<?>> dropdownRegistrar;

    ResponsiveConfigList(int x, int y, int width, int height, int configWidth,
                         boolean useKeybindSearch, GuiConfigsBase host,
                         Consumer<OverlayDropdown<?>> dropdownRegistrar) {
        super(x, y, width, height, configWidth, 0.0F, useKeybindSearch, host);
        this.hostScreen = host;
        this.dropdownRegistrar = dropdownRegistrar;
    }

    @Override
    protected WidgetConfigOption createListEntryWidget(int x, int y, int listIndex,
                                                       boolean isOdd, ConfigOptionWrapper wrapper) {
        int resetWidth = this.getStringWidth(StringUtils.translate("malilib.gui.button.reset.caps")) + 10;
        int fixedWidth = resetWidth + LABEL_GAP + RESET_GAP + SCROLLBAR_CLEARANCE;
        int availableWidth = Math.max(1, this.browserEntryWidth - fixedWidth);
        int responsiveConfigWidth = Math.min(this.configWidth,
                Math.max(MIN_CONTROL_WIDTH, availableWidth - MIN_LABEL_WIDTH));
        responsiveConfigWidth = Math.min(responsiveConfigWidth, Math.max(1, availableWidth - 1));
        int labelWidth = Math.min(this.maxLabelWidth,
                Math.max(1, availableWidth - responsiveConfigWidth));
        return new ResponsiveConfigOption(
                x, y, this.browserEntryWidth, this.browserEntryHeight,
                labelWidth, responsiveConfigWidth, wrapper, listIndex,
                this.hostScreen, this);
    }

    private void registerDropdown(OverlayDropdown<?> dropdown) {
        this.dropdownRegistrar.accept(dropdown);
    }

    private static final class ResponsiveConfigOption extends WidgetConfigOption {
        private ResponsiveConfigOption(int x, int y, int width, int height,
                                       int labelWidth, int configWidth,
                                       ConfigOptionWrapper wrapper, int listIndex,
                                       IKeybindConfigGui host,
                                       ResponsiveConfigList parent) {
            super(x, y, width, height, labelWidth, configWidth, wrapper, listIndex, host, parent);
        }

        @Override
        protected void addConfigOption(int x, int y, int labelWidth,
                                       int configWidth, IConfigBase config) {
            if (config.getType() == ConfigType.HOTKEY) {
                y += 1;
                int controlsX = getControlsX(configWidth);
                addResponsiveConfigLabel(x, y, controlsX - x - LABEL_GAP, config);
                this.addHotkeyConfigElements(
                        controlsX, y, configWidth, config.getName(), (IHotkey) config);
                return;
            }

            if (config.getType() == ConfigType.BOOLEAN) {
                y += 1;
                int controlsX = getControlsX(configWidth);
                addResponsiveConfigLabel(x, y, controlsX - x - LABEL_GAP, config);
                IConfigBoolean booleanConfig = (IConfigBoolean) config;
                ConfigButtonBoolean button = new ConfigButtonBoolean(
                        controlsX, y, configWidth, 20, booleanConfig);
                this.addConfigButtonEntry(
                        controlsX + configWidth + RESET_GAP, y, (IConfigResettable) config, button);
                return;
            }

            if (config.getType() == ConfigType.OPTION_LIST) {
                addOptionList(x, y + 1, configWidth, config);
                return;
            }

            int controlsX = getControlsX(configWidth);
            super.addConfigOption(
                    x, y, Math.max(1, controlsX - x - LABEL_GAP), configWidth, config);
        }

        private void addOptionList(int x, int y, int configWidth, IConfigBase config) {
            IConfigOptionList optionList = (IConfigOptionList) config;
            IConfigResettable resettable = (IConfigResettable) config;
            int controlsX = getControlsX(configWidth);
            addResponsiveConfigLabel(x, y, controlsX - x - LABEL_GAP, config);

            List<IConfigOptionListEntry> entries = new ArrayList<>();
            IConfigOptionListEntry current = optionList.getOptionListValue();
            IConfigOptionListEntry iterator = current;
            if (iterator != null) {
                do {
                    entries.add(iterator);
                    iterator = iterator.cycle(true);
                } while (iterator != current && iterator != null && entries.size() < 100);
            }

            ButtonGeneric resetButton = this.createResetButton(
                    controlsX + configWidth + RESET_GAP, y, resettable);
            OverlayDropdown<IConfigOptionListEntry> dropdown =
                    new OverlayDropdown<>(controlsX, y, configWidth, 20, 200, 5,
                            entries, IConfigOptionListEntry::getDisplayName) {
                        @Override
                        protected void setSelectedEntry(int index) {
                            super.setSelectedEntry(index);
                            if (this.getSelectedEntry() != null) {
                                optionList.setOptionListValue(this.getSelectedEntry());
                                resetButton.setEnabled(resettable.isModified());
                            }
                        }
                    };
            ((ResponsiveConfigList) this.parent).registerDropdown(dropdown);
            dropdown.setSelectedEntry(current);

            this.addWidget(dropdown);
            this.addButton(resetButton, (button, mouseButton) -> {
                resettable.resetToDefault();
                dropdown.setSelectedEntry(optionList.getOptionListValue());
                resetButton.setEnabled(resettable.isModified());
            });
        }

        private int getControlsX(int configWidth) {
            int resetWidth = this.textRenderer.width(
                    StringUtils.translate("malilib.gui.button.reset.caps")) + 10;
            return this.x + this.width - configWidth - resetWidth - RESET_GAP - SCROLLBAR_CLEARANCE;
        }

        private void addResponsiveConfigLabel(int x, int y, int availableWidth,
                                              IConfigBase config) {
            int labelWidth = Math.max(0, availableWidth);
            String translatedName = StringUtils.translate(config.getName());
            String configName = fitText(
                    translatedName.equals(config.getName())
                            ? config.getConfigGuiDisplayName()
                            : translatedName,
                    labelWidth);
            this.addLabel(x, y + 7, labelWidth, 8, 0xFFFFFFFF, configName);

            var infoProvider = this.host.getHoverInfoProvider();
            String comment = infoProvider != null
                    ? infoProvider.getHoverInfo(config)
                    : config.getComment();
            if (comment != null) {
                this.addWidget(new WidgetHoverInfo(x, y + 5, labelWidth, 12, comment));
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
