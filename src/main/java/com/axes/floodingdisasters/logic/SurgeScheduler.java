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

    private static final int ABSOLUTE_MAX_HEIGHT = 90;
    private static final Map<ChunkPos, Boolean> COASTAL_CACHE = new HashMap<>();

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide) return;
        ServerLevel level = (ServerLevel) event.getLevel();

        Iterable<LevelChunk> loadedChunks = getLoadedChunksSafe(level);

        for (LevelChunk chunk : loadedChunks) {
            if (chunk == null) continue;
            ChunkPos cp = chunk.getPos();

            // PERFORMANCE: Staggered Updates with Noise
            if ((level.getGameTime() + (cp.x * 31L + cp.z * 17L)) % 20 != 0) continue;

            if (!COASTAL_CACHE.containsKey(cp)) {
                // Now uses the updated CoastCache which detects inland floods!
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

        BlockPos centerPos = cp.getWorldPosition().offset(8, 62, 8);
        int localTargetY = WeatherSurgeController.getSurgeLevelAt(centerPos, level);

        boolean spreadNorth = false;
        boolean spreadSouth = false;
        boolean spreadWest = false;
        boolean spreadEast = false;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {

                for (int y = 63; y <= ABSOLUTE_MAX_HEIGHT; y++) {
                    BlockPos pos = cp.getWorldPosition().offset(x, y, z);
                    BlockState currentState = level.getBlockState(pos);

                    // --- MODE A: RISING ---
                    if (y <= localTargetY) {
                        if (level.isEmptyBlock(pos) || canSurgeDestroy(level, pos)) {

                            BlockPos belowPos = pos.below();
                            BlockState below = level.getBlockState(belowPos);
                            boolean shouldFlood = false;

                            if (below.getFluidState().is(net.minecraft.tags.FluidTags.WATER) ||
                                    below.is(ModBlocks.SURGE_WATER.get())) {
                                shouldFlood = true;
                            }
                            else if (below.isSolidRender(level, belowPos)) {
                                if (hasWaterNeighbor(level, pos)) {
                                    shouldFlood = true;
                                }
                            }

                            if (shouldFlood) {
                                if (!level.isEmptyBlock(pos)) level.destroyBlock(pos, true);
                                level.setBlock(pos, ModBlocks.SURGE_WATER.get().defaultBlockState(), 2);

                                if (x == 0) spreadWest = true;
                                if (x == 15) spreadEast = true;
                                if (z == 0) spreadNorth = true;
                                if (z == 15) spreadSouth = true;

                                break; // Visual Smoothing
                            }
                        }
                    }
                    // --- MODE B: RECEDING ---
                    else {
                        if (currentState.is(ModBlocks.SURGE_WATER.get())) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);

                            // FIX: WAKE UP NEIGHBORS WHEN DRYING TOO!
                            // This ensures the "Recession" spreads inland.
                            if (x == 0) spreadWest = true;
                            if (x == 15) spreadEast = true;
                            if (z == 0) spreadNorth = true;
                            if (z == 15) spreadSouth = true;

                        } else if (currentState.isAir()) {
                            break;
                        }
                    }
                }
            }
        }

        if (spreadWest)  activateChunk(cp.x - 1, cp.z);
        if (spreadEast)  activateChunk(cp.x + 1, cp.z);
        if (spreadNorth) activateChunk(cp.x, cp.z - 1);
        if (spreadSouth) activateChunk(cp.x, cp.z + 1);
    }

    // --- Helpers (No changes below) ---
    private static boolean hasWaterNeighbor(ServerLevel level, BlockPos pos) {
        return isWaterOrSurge(level, pos.north()) || isWaterOrSurge(level, pos.south()) ||
                isWaterOrSurge(level, pos.east()) || isWaterOrSurge(level, pos.west());
    }

    private static boolean isWaterOrSurge(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.WATER) || state.is(ModBlocks.SURGE_WATER.get());
    }

    private static void activateChunk(int chunkX, int chunkZ) {
        ChunkPos neighbor = new ChunkPos(chunkX, chunkZ);
        if (!Boolean.TRUE.equals(COASTAL_CACHE.get(neighbor))) {
            COASTAL_CACHE.put(neighbor, true);
        }
    }

    private static boolean canSurgeDestroy(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.WATER) || state.is(ModBlocks.SURGE_WATER.get())) return false;
        return !state.isAir() && (
                state.is(net.minecraft.tags.BlockTags.REPLACEABLE) ||
                        state.is(net.minecraft.tags.BlockTags.FLOWERS) ||
                        state.is(net.minecraft.tags.BlockTags.CROPS) ||
                        state.getDestroySpeed(level, pos) == 0.0f
        );
    }

    private static Iterable<LevelChunk> getLoadedChunksSafe(ServerLevel level) {
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