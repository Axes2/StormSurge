package com.axes.floodingdisasters.logic;

import com.axes.floodingdisasters.FloodingDisasters;
import com.axes.floodingdisasters.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = FloodingDisasters.MODID, bus = EventBusSubscriber.Bus.GAME)
public class SurgePhysics {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        Level level = player.level();

        // Optimization: Only check if inside our block
        BlockPos pos = player.blockPosition();
        BlockState state = level.getBlockState(pos);

        if (state.is(ModBlocks.SURGE_WATER.get())) {
            applySurgeCurrent(player);
        }
    }

    private static void applySurgeCurrent(Entity entity) {
        // 1. Calculate Push Direction
        // For a true storm, this should match the Wind Direction.
        // For now, we will push them towards the highest nearby ground (Inland)
        // OR simpler: Push them in the direction they are looking (panic!)
        // ACTUALLY: Let's push them North (+Z) for testing, or use a "Wave" math.

        // Simple Test: Push everyone slightly UP and NORTH
        Vec3 currentMotion = entity.getDeltaMovement();

        // "Buoyancy" (Float up)
        double lift = 0.05;

        // "Drag" (Slow them down like water)
        double drag = 0.8;

        // Apply physics
        entity.setDeltaMovement(
                currentMotion.x * drag,
                currentMotion.y + lift,
                currentMotion.z * drag
        );

        // Reset fall distance so they don't take damage
        entity.resetFallDistance();
    }
}