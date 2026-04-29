package org.edtp.chainveinfabric.client.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import org.edtp.chainveinfabric.client.gui.malilib.GuiChainVein;

public class ModMenuImpl implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return (screen) -> {
            GuiChainVein gui = new GuiChainVein();
            gui.setParent(screen);
            return gui;
        };
    }
}
