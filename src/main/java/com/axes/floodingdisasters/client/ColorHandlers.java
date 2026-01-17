package com.axes.floodingdisasters.client;

import com.axes.floodingdisasters.FloodingDisasters;
import com.axes.floodingdisasters.ModBlocks;
import net.minecraft.client.renderer.BiomeColors;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(modid = FloodingDisasters.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ColorHandlers {

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, world, pos, tintIndex) -> {
            // If we are in the world, grab the biome's water color
            if (world != null && pos != null) {
                return BiomeColors.getAverageWaterColor(world, pos);
            }
            // Default "Water Blue" for inventory or debug
            return 0x3F76E4;
        }, ModBlocks.SURGE_WATER.get());
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        // Color the item in your hand blue too
        event.register((stack, tintIndex) -> 0x3F76E4, ModBlocks.SURGE_WATER_ITEM.get());
    }
}