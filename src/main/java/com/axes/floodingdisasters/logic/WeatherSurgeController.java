package com.axes.floodingdisasters.logic;

import com.axes.floodingdisasters.FloodingDisasters;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.lang.reflect.Method;

@EventBusSubscriber(modid = FloodingDisasters.MODID, bus = EventBusSubscriber.Bus.GAME)
public class WeatherSurgeController {

    private static boolean weatherModFound = true;
    private static Method getWindMethod;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (weatherModFound && getWindMethod == null) {
            try {
                Class<?> windEngineClass = Class.forName("dev.protomanly.pmweather.weather.WindEngine");
                getWindMethod = windEngineClass.getMethod("getWind", Vec3.class, Level.class, boolean.class, boolean.class, boolean.class, boolean.class);
            } catch (Exception e) {
                weatherModFound = false;
            }
        }
    }

    public static int getSurgeLevelAt(BlockPos pos, ServerLevel level) {
        if (!weatherModFound || getWindMethod == null) return 62;

        try {
            // Check at High Altitude (Y=180) to avoid terrain drag
            Vec3 weatherBalloonPos = new Vec3(pos.getX(), 180.0, pos.getZ());
            Vec3 windVector = (Vec3) getWindMethod.invoke(null, weatherBalloonPos, level, false, false, false, true);
            double windSpeed = windVector.length();

            // --- BALANCED MATH ---
            // Goal: 250mph = +8 Blocks. Weak storms = 0 Blocks.
            // Threshold: 60mph. Divisor: 24.

            if (windSpeed < 60.0) {
                return 62;
            } else {
                double rise = (windSpeed - 60.0) / 24.0;
                int target = 62 + (int) rise;

                // Hard Cap at 75 (Sea Level + 13) to prevent world-ending floods
                return Math.min(target, 75);
            }

        } catch (Exception e) {
            return 62;
        }
    }
}