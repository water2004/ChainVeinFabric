package org.edtp.chainveinfabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.edtp.chainveinfabric.Chainveinfabric;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig; // Corrected import
import org.edtp.chainveinfabric.client.gui.malilib.GuiChainVein;
import org.edtp.chainveinfabric.client.handler.ClientChainHandler;
import org.lwjgl.glfw.GLFW;

public class ChainveinfabricClient implements ClientModInitializer {

    public static ChainVeinConfig CONFIG;
    public static final KeyMapping.Category CHAIN_VEIN_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("chainveinfabric", "general"));
    private static KeyMapping configKeyBinding;

    @Override
    public void onInitializeClient() {
        CONFIG = ChainVeinConfig.load();
        
        configKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.chainveinfabric.config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                CHAIN_VEIN_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (configKeyBinding.consumeClick()) {
                client.setScreen(new GuiChainVein());
            }
            ClientChainHandler.onTick(client);
        });

        // Use modern HudElementRegistry instead of deprecated HudRenderCallback
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("chainveinfabric", "indicator"), (context, deltaTracker) -> {
            if (CONFIG != null && CONFIG.isChainVeinEnabled) {
                Component activeText = Component.translatable("hud.chainveinfabric.active");
                int width = context.guiWidth();
                context.drawCenteredString(
                        Minecraft.getInstance().font,
                        activeText,
                        width / 2,
                        5, // Small offset from top
                        0xFFFF0000 // Red color
                );
            }
        });
    }
}
