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

    // Simple container for our data
    public record FloodInfo(int targetY, float dirX, float dirZ) {}

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

    public static FloodInfo getFloodInfoAt(BlockPos pos, ServerLevel level) {
        if (!weatherModFound || getWindMethod == null) return new FloodInfo(62, 1.0f, 0.0f);

        try {
            Vec3 weatherBalloonPos = new Vec3(pos.getX(), 180.0, pos.getZ());
            Vec3 windVector = (Vec3) getWindMethod.invoke(null, weatherBalloonPos, level, false, false, false, true);

            double windSpeed = windVector.length();

            // Normalize direction for the wave calculation
            Vec3 dir = windVector.normalize();
            // If calm, default to a gentle diagonal drift
            if (windSpeed < 1.0) dir = new Vec3(0.7, 0, 0.7);

            int targetY = 62;
            if (windSpeed >= 60.0) {
                double rise = (windSpeed - 60.0) / 24.0;
                targetY = 62 + (int) rise;
                targetY = Math.min(targetY, 75);
            }

            return new FloodInfo(targetY, (float)dir.x, (float)dir.z);

        } catch (Exception e) {
            return new FloodInfo(62, 1.0f, 0.0f);
        }
    }
}