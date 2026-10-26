package org.edtp.chainveinfabric.client.logic;

import org.edtp.chainveinfabric.client.config.ChainVeinConfig.ChainMode;

/** Shared trigger policy and the corresponding player-facing instructions. */
public enum AutoMiningTrigger {
    LEFT_CLICK("armed", "ctrlHint"),
    COOLDOWN("schematicArmed", "schematicCtrlHint");

    private final String armedKey;
    private final String tooltipKey;

    AutoMiningTrigger(String armedKey, String tooltipKey) {
        this.armedKey = armedKey;
        this.tooltipKey = tooltipKey;
    }

    public static AutoMiningTrigger forMode(ChainMode mode) {
        return mode.isSchematicMode() ? COOLDOWN : LEFT_CLICK;
    }

    public String armedMessageKey() {
        return "message.chainveinfabric.autoMine." + this.armedKey;
    }

    public String armedHudKey() {
        return "hud.chainveinfabric.autoMine." + this.armedKey;
    }

    public String enableTooltipKey() {
        return "options.chainveinfabric.autoMine." + this.tooltipKey;
    }
}
