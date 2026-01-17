package com.axes.floodingdisasters.logic;

import com.axes.floodingdisasters.FloodingDisasters;
import com.axes.floodingdisasters.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = FloodingDisasters.MODID, bus = EventBusSubscriber.Bus.GAME)
public class SurgeScheduler {

    public static int TARGET_SURGE_LEVEL = 62;
    private static final Map<ChunkPos, Boolean> COASTAL_CACHE = new HashMap<>();

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide) return;

        ServerLevel level = (ServerLevel) event.getLevel();

        // Tick every 20 ticks (1 second)
        if (level.getGameTime() % 20 != 0) return;

        Iterable<LevelChunk> loadedChunks = getLoadedChunksSafe(level);

        for (LevelChunk chunk : loadedChunks) {
            if (chunk == null) continue;

            ChunkPos cp = chunk.getPos();

            if (!COASTAL_CACHE.containsKey(cp)) {
                boolean isCoastal = CoastCache.isCoastalChunk(level, cp);
                COASTAL_CACHE.put(cp, isCoastal);
            }

            if (COASTAL_CACHE.get(cp)) {
                processChunkSurge(level, chunk);
            }
        }
    }

    private static void processChunkSurge(ServerLevel level, LevelChunk chunk) {
        ChunkPos cp = chunk.getPos();
        int currentMaxY = Math.min(TARGET_SURGE_LEVEL, 75);

        // OPTIMIZATION REMOVED: Scan full 16x16.
        // Modern Java can handle 256 iterations easily.
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {

                // Scan heights
                for (int y = 63; y <= currentMaxY; y++) {
                    BlockPos pos = cp.getWorldPosition().offset(x, y, z);

                    if (level.isEmptyBlock(pos)) {
                        BlockPos belowPos = pos.below();
                        BlockState below = level.getBlockState(belowPos);

                        // Check for support
                        if (below.isSolidRender(level, belowPos) ||
                                below.is(Blocks.WATER) ||
                                below.is(ModBlocks.SURGE_WATER.get())) {

                            // Place water. Use flag 2 (Send to client) instead of 3 (Update neighbors) to save lag.
                            level.setBlock(pos, ModBlocks.SURGE_WATER.get().defaultBlockState(), 2);
                        }
                    }
                }
            }
        }
    }

    // Keep your getLoadedChunksSafe method exactly as it was!
    private static Iterable<LevelChunk> getLoadedChunksSafe(ServerLevel level) {
        // ... (Paste your Reflection code here from the previous step) ...
        // Or just leave it if you are editing the existing file.
        try {
            ServerChunkCache chunkSource = level.getChunkSource();
            Field mapField = ServerChunkCache.class.getDeclaredField("chunkMap");
            mapField.setAccessible(true);
            Object chunkMap = mapField.get(chunkSource);
            java.lang.reflect.Method getChunksMethod = chunkMap.getClass().getDeclaredMethod("getChunks");
            getChunksMethod.setAccessible(true);
            Iterable<?> holders = (Iterable<?>) getChunksMethod.invoke(chunkMap);
            return () -> java.util.stream.StreamSupport.stream(holders.spliterator(), false)
                    .map(holder -> {
                        try {
                            java.lang.reflect.Method getTicking = holder.getClass().getDeclaredMethod("getTickingChunk");
                            getTicking.setAccessible(true);
                            return (LevelChunk) getTicking.invoke(holder);
                        } catch (Exception e) { return null; }
                    }).filter(c -> c != null).iterator();
        } catch (Exception e) { return Collections.emptyList(); }
    }
}