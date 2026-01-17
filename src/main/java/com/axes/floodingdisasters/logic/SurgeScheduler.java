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

    // Cache for wind data to avoid reflection lag
    private static final Map<ChunkPos, CachedChunkData> SURGE_DATA_CACHE = new HashMap<>();

    private record CachedChunkData(int targetY, float dirX, float dirZ, long lastUpdate) {}

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide) return;
        ServerLevel level = (ServerLevel) event.getLevel();

        Iterable<LevelChunk> loadedChunks = getLoadedChunksSafe(level);

        for (LevelChunk chunk : loadedChunks) {
            if (chunk == null) continue;
            ChunkPos cp = chunk.getPos();

            // 1. Coast Check (Runs once mostly)
            if (!COASTAL_CACHE.containsKey(cp)) {
                boolean isCoastal = CoastCache.isCoastalChunk(level, cp);
                COASTAL_CACHE.put(cp, isCoastal);
            }

            // 2. Run Surge Logic if Coastal
            if (COASTAL_CACHE.get(cp)) {
                processChunkSurge(level, chunk);
            }
        }
    }

    private static void processChunkSurge(ServerLevel level, LevelChunk chunk) {
        ChunkPos cp = chunk.getPos();
        long time = level.getGameTime();

        // --- DATA CACHING START ---
        CachedChunkData data = SURGE_DATA_CACHE.get(cp);

        // Refresh data every 20 ticks (1 second) to follow changing winds
        if (data == null || (time - data.lastUpdate) > 20) {
            BlockPos centerPos = cp.getWorldPosition().offset(8, 62, 8);
            WeatherSurgeController.FloodInfo info = WeatherSurgeController.getFloodInfoAt(centerPos, level);
            data = new CachedChunkData(info.targetY(), info.dirX(), info.dirZ(), time);
            SURGE_DATA_CACHE.put(cp, data);
        }
        // --- DATA CACHING END ---

        boolean spreadNorth = false;
        boolean spreadSouth = false;
        boolean spreadWest = false;
        boolean spreadEast = false;

        // "RATE": Controls how fast the wave moves.
        // 8 = Update every 8 ticks (0.4s).
        // Lower = Faster wave. Higher = Slower wave.
        int updateRate = 8;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {

                // --- DIRECTIONAL SWEEP LOGIC ---
                // We project the block's position onto the wind direction vector.
                // This creates a "Wave Front" value that moves across the chunk.
                // We calculate global coordinates so the wave flows seamlessly between chunks.
                long globalX = cp.x * 16L + x;
                long globalZ = cp.z * 16L + z;

                // The Offset determines "When" this block updates relative to time.
                // Multiply by a small factor to stretch the wave if needed, but 1.0 works well for 1-block steps.
                float waveOffset = globalX * data.dirX + globalZ * data.dirZ;

                // If it's not this block's turn, skip it.
                // This creates the "Scanning Line" effect moving in the wind direction.
                if ((time - (int)waveOffset) % updateRate != 0) continue;

                // --- STANDARD LOGIC BELOW ---

                // We optimize by scanning only surface interaction points
                for (int y = 63; y <= ABSOLUTE_MAX_HEIGHT; y++) {
                    BlockPos pos = cp.getWorldPosition().offset(x, y, z);
                    BlockState currentState = level.getBlockState(pos);

                    if (y <= data.targetY) {
                        // Rising Logic
                        if (level.isEmptyBlock(pos) || canSurgeDestroy(level, pos)) {
                            BlockPos belowPos = pos.below();
                            BlockState below = level.getBlockState(belowPos);
                            boolean shouldFlood = false;

                            if (below.getFluidState().is(net.minecraft.tags.FluidTags.WATER) ||
                                    below.is(ModBlocks.SURGE_WATER.get())) {
                                shouldFlood = true;
                            } else if (below.isSolidRender(level, belowPos)) {
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

                                break; // Place 1 block per sweep
                            }
                        }
                    } else {
                        // Receding Logic
                        if (currentState.is(ModBlocks.SURGE_WATER.get())) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
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

    // --- Helpers (Same as before) ---
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