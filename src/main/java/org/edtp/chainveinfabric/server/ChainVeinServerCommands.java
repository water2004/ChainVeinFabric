package org.edtp.chainveinfabric.server;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

/** Registers owner-only commands for persistent ChainVein server limits. */
public final class ChainVeinServerCommands {
    private ChainVeinServerCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
                dispatcher.register(Commands.literal("chainvein")
                        .requires(source -> source.permissions()
                                .hasPermission(Permissions.COMMANDS_OWNER))
                        .then(Commands.literal("maxBlocks")
                                .executes(context -> showMaxBlocks(context.getSource()))
                                .then(Commands.argument("value", IntegerArgumentType.integer(
                                                ChainVeinServerConfig.MIN_MAX_BLOCKS,
                                                ChainVeinServerConfig.MAX_MAX_BLOCKS))
                                        .executes(context -> setMaxBlocks(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "value")))))
                        .then(Commands.literal("pickupRadius")
                                .executes(context -> showPickupRadius(context.getSource()))
                                .then(Commands.argument("value", IntegerArgumentType.integer(
                                                ChainVeinServerConfig.MIN_PICKUP_RADIUS,
                                                ChainVeinServerConfig.MAX_PICKUP_RADIUS))
                                        .executes(context -> setPickupRadius(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "value")))))));
    }

    private static int showMaxBlocks(CommandSourceStack source) {
        return sendValue(source, "Maximum blocks per server tick",
                ChainVeinServerConfig.values().maxBlocks());
    }

    private static int setMaxBlocks(CommandSourceStack source, int value) {
        return sendValue(source, "Maximum blocks per server tick",
                ChainVeinServerConfig.setMaxBlocks(value));
    }

    private static int showPickupRadius(CommandSourceStack source) {
        return sendValue(source, "Direct-pickup radius",
                ChainVeinServerConfig.values().pickupRadius());
    }

    private static int setPickupRadius(CommandSourceStack source, int value) {
        return sendValue(source, "Direct-pickup radius",
                ChainVeinServerConfig.setPickupRadius(value));
    }

    private static int sendValue(CommandSourceStack source, String label, int value) {
        source.sendSuccess(() -> Component.literal(label + ": " + value), false);
        return 1;
    }
}
