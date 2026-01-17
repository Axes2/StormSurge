package com.axes.floodingdisasters.events;

import com.axes.floodingdisasters.FloodingDisasters;
import com.axes.floodingdisasters.logic.CoastCache;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = FloodingDisasters.MODID, bus = EventBusSubscriber.Bus.GAME)
public class FloodDebugEvents {

    // We use this to track if you've moved to a new chunk so we don't spam the chat every tick.
    private static ChunkPos lastChunkPos = null;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        // Only run on the Server side (Logic happens on Server)
        if (event.getEntity().level().isClientSide) return;

        // Cast to ServerPlayer for chat features
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Get current chunk position
        ChunkPos currentPos = player.chunkPosition();

        // Check if player moved to a new chunk
        if (!currentPos.equals(lastChunkPos)) {
            lastChunkPos = currentPos;
            performCoastCheck(player, currentPos);
        }
    }

    private static void performCoastCheck(ServerPlayer player, ChunkPos pos) {
        boolean isCoastal = CoastCache.isCoastalChunk(player.level(), pos);

        if (isCoastal) {
            player.sendSystemMessage(Component.literal("⚠ COASTAL CHUNK DETECTED ⚠")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        } else {
            player.sendSystemMessage(Component.literal("Safe Inland Chunk")
                    .withStyle(ChatFormatting.GREEN));
        }
    }
}