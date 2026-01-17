package com.axes.floodingdisasters.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.axes.floodingdisasters.logic.SurgeScheduler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class FloodCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("flood")
                .then(Commands.literal("set")
                        .then(Commands.argument("level", IntegerArgumentType.integer(62, 100))
                                .executes(context -> {
                                    int newLevel = IntegerArgumentType.getInteger(context, "level");
                                    SurgeScheduler.TARGET_SURGE_LEVEL = newLevel;

                                    context.getSource().sendSuccess(() ->
                                            Component.literal("🌊 Surge Level set to Y=" + newLevel), true);
                                    return 1;
                                })
                        )
                )
        );
    }
}