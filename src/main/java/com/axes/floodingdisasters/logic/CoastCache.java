package com.axes.floodingdisasters.logic;

import com.axes.floodingdisasters.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public class CoastCache {

    public static boolean isCoastalChunk(Level level, ChunkPos chunkPos) {
        // 1. FAST CHECK: Is this chunk already flooded?
        // If we find SURGE_WATER at the surface, this chunk MUST be active.
        // We scan the center and corners to be efficient.
        if (isFloodedSurface(level, chunkPos, 0, 0) ||
                isFloodedSurface(level, chunkPos, 15, 0) ||
                isFloodedSurface(level, chunkPos, 0, 15) ||
                isFloodedSurface(level, chunkPos, 8, 8)) {
            return true;
        }

        // 2. STANDARD CHECK: Is this a coastline? (Water meets Land at Sea Level)
        int seaLevel = 62;
        boolean hasWater = false;
        boolean hasLand = false;

        for (int x = -1; x <= 16; x += 4) { // Optimization: Step 4 is usually enough for coast detection
            for (int z = -1; z <= 16; z += 4) {

                BlockPos pos = chunkPos.getWorldPosition().offset(x, seaLevel, z);
                BlockState state = level.getBlockState(pos);

                if (state.is(Blocks.WATER) || state.is(ModBlocks.SURGE_WATER.get())) {
                    if (level.canSeeSky(pos.above())) {
                        hasWater = true;
                    }
                }
                else if (state.isSolidRender(level, pos)) {
                    // Make sure we are looking at the chunk itself for 'Land' credit
                    if (x >= 0 && x <= 15 && z >= 0 && z <= 15) {
                        hasLand = true;
                    }
                }

                if (hasWater && hasLand) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isFloodedSurface(Level level, ChunkPos cp, int xOffset, int zOffset) {
        int x = cp.getMinBlockX() + xOffset;
        int z = cp.getMinBlockZ() + zOffset;
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);

        // Check the top block (and the one slightly below in case of foliage)
        BlockPos topPos = new BlockPos(x, surfaceY, z);
        BlockState topState = level.getBlockState(topPos);

        // Also check one below, just in case the heightmap grabbed a flower/grass
        BlockState belowState = level.getBlockState(topPos.below());

        return topState.is(ModBlocks.SURGE_WATER.get()) || belowState.is(ModBlocks.SURGE_WATER.get());
    }
}