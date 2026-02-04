package org.edtp.chainveinfabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.edtp.chainveinfabric.client.gui.ChainVeinScreen;
import org.lwjgl.glfw.GLFW;

public class ChainveinfabricClient implements ClientModInitializer {

    private static final KeyBinding.Category CHAIN_VEIN_CATEGORY = KeyBinding.Category.create(Identifier.of("chainveinfabric", "general"));
    private static KeyBinding configKeyBinding;

    @Override
    public void onInitializeClient() {
        configKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chainveinfabric.config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                CHAIN_VEIN_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (configKeyBinding.wasPressed()) {
                client.setScreen(new ChainVeinScreen());
            }
        });
    }
}
