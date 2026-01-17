package com.axes.floodingdisasters.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class CoastCache {

    /**
     * ROBUST DETECTION V3
     * 1. Ignores Biomes (Works on Plains, Forests, Stony Shores).
     * 2. Checks for "Sky Water" (Prevents Cave Flooding).
     * 3. Checks Neighbors (Fixes "Cliffs" that are 100% land but touch ocean).
     */
    public static boolean isCoastalChunk(Level level, ChunkPos chunkPos) {
        int seaLevel = 62;
        boolean hasWater = false;
        boolean hasLand = false;

        // Scan the chunk AND its immediate border.
        // x goes from -1 (neighbor left) to 16 (neighbor right)
        // This ensures if we are a "Land Chunk" touching an "Ocean Chunk", we trigger.
        for (int x = -1; x <= 16; x += 2) {
            for (int z = -1; z <= 16; z += 2) {

                // Get world position
                BlockPos pos = chunkPos.getWorldPosition().offset(x, seaLevel, z);
                BlockState state = level.getBlockState(pos);

                // CHECK 1: WATER (Biome Agnostic)
                if (state.is(Blocks.WATER)) {
                    // CRITICAL: Must see the sky.
                    // If we don't check this, aquifers under your base will flood.
                    if (level.canSeeSky(pos.above())) {
                        hasWater = true;
                    }
                }
                // CHECK 2: LAND
                else if (state.isSolidRender(level, pos)) {
                    // We only care about land that is INSIDE the actual chunk
                    // (x and z between 0 and 15)
                    if (x >= 0 && x <= 15 && z >= 0 && z <= 15) {
                        hasLand = true;
                    }
                }

                // If we found both, we are done. Early exit to save CPU.
                if (hasWater && hasLand) {
                    return true;
                }
            }
        }

        return false;
    }
}