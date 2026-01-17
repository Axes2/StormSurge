package com.axes.floodingdisasters;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod(FloodingDisasters.MODID)
public class FloodingDisasters {
    public static final String MODID = "floodingdisasters";
    public static final Logger LOGGER = LogManager.getLogger();

    public FloodingDisasters(IEventBus modEventBus) {
        // Register the Setup method for mod loading
        modEventBus.addListener(this::setup);


        // Register Blocks and Items (We will create this class next)
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.ITEMS.register(modEventBus);
    }


    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("ProtoFlood Initialized - Beware the tides.");
    }
}