package com.axes.floodingdisasters.logic;

import com.axes.floodingdisasters.FloodingDisasters;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level; // Import Level
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = FloodingDisasters.MODID, bus = EventBusSubscriber.Bus.GAME)
public class WeatherSurgeController {

    private static boolean weatherModFound = true;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        // 1. Client Check
        if (event.getLevel().isClientSide || !weatherModFound) return;

        // 2. DIMENSION FIX: Only run this logic in the Overworld
        if (event.getLevel().dimension() != Level.OVERWORLD) return;

        ServerLevel level = (ServerLevel) event.getLevel();

        // Run every 60 ticks (3 seconds)
        if (level.getGameTime() % 60 != 0) return;

        try {
            updateSurgeTargetReflectively(level);
        } catch (Exception e) {
            FloodingDisasters.LOGGER.warn("ProtoManly Weather integration failed: " + e.getMessage());
            e.printStackTrace();
            weatherModFound = false;
        }
    }

    private static void updateSurgeTargetReflectively(ServerLevel level) throws Exception {
        Class<?> gameBusClass = Class.forName("dev.protomanly.pmweather.event.GameBusEvents");
        Field managersField = gameBusClass.getDeclaredField("MANAGERS");
        Map<?, ?> managers = (Map<?, ?>) managersField.get(null);

        Object weatherHandler = managers.get(level.dimension());
        if (weatherHandler == null) return;

        Method getStormsMethod = weatherHandler.getClass().getMethod("getStorms");
        List<?> storms = (List<?>) getStormsMethod.invoke(weatherHandler);

        float maxWindSpeed = 0.0f;

        for (Object stormObj : storms) {
            Class<?> stormClass = stormObj.getClass();

            boolean visualOnly = stormClass.getField("visualOnly").getBoolean(stormObj);
            int stormType = stormClass.getField("stormType").getInt(stormObj);
            float windspeed = stormClass.getField("windspeed").getFloat(stormObj);

            // Added Type 2 (Tornado/Cyclone variant) to the allowed list
            if (!visualOnly && (stormType == 1 || stormType == 0 || stormType == 2)) {
                if (windspeed > maxWindSpeed) {
                    maxWindSpeed = windspeed;
                }
            }
        }

        int targetY;
        if (maxWindSpeed < 50.0f) {
            targetY = 62;
        } else {
            float rise = (maxWindSpeed - 50.0f) / 7.0f;
            targetY = 62 + (int) rise;
        }

        targetY = Math.min(targetY, 80);

        // Update Global Target
        if (SurgeScheduler.TARGET_SURGE_LEVEL != targetY) {
            SurgeScheduler.TARGET_SURGE_LEVEL = targetY;
            level.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("🌊 Surge Updating! Wind: " + (int)maxWindSpeed + "mph -> Flood Level: " + targetY),
                    false
            );
        }
    }
}