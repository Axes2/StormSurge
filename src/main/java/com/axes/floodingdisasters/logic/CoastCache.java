package com.axes.floodingdisasters.logic;

import com.axes.floodingdisasters.ModBlocks; // Don't forget this import!
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class CoastCache {

    public static boolean isCoastalChunk(Level level, ChunkPos chunkPos) {
        int seaLevel = 62;
        boolean hasWater = false;
        boolean hasLand = false;

        for (int x = -1; x <= 16; x += 2) {
            for (int z = -1; z <= 16; z += 2) {

                BlockPos pos = chunkPos.getWorldPosition().offset(x, seaLevel, z);
                BlockState state = level.getBlockState(pos);

                // FIX: Check for Vanilla Water OR Our Surge Water
                if (state.is(Blocks.WATER) || state.is(ModBlocks.SURGE_WATER.get())) {
                    if (level.canSeeSky(pos.above())) {
                        hasWater = true;
                    }
                }
                else if (state.isSolidRender(level, pos)) {
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
}