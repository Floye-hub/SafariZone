package com.floye.safarizone.config;

import com.floye.safarizone.util.SafariZoneManager.SafariZoneData;
import com.floye.safarizone.util.SafariZoneManager.SafariZoneData.Bounds;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigLoader {

    private static final String CONFIG_FILE_PATH = "config/SafariZone/safarizone_config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Map<Integer, SafariZoneData> loadConfig(String configFilePath) {
        ConfigData configData = null;
        File configFile = new File(CONFIG_FILE_PATH);

        if (!configFile.exists()) {
            configData = getDefaultConfig();
            saveConfig(configData, configFile);
        } else {
            try (FileReader reader = new FileReader(configFile)) {
                configData = GSON.fromJson(reader, ConfigData.class);
            } catch (Exception e) {
                e.printStackTrace();
                configData = getDefaultConfig();
            }
        }

        Map<Integer, SafariZoneData> zones = new HashMap<>();
        for (ZoneConfig zone : configData.zones) {
            zones.put(zone.id, new SafariZoneData(
                    new BlockPos(zone.spawnPosition.x, zone.spawnPosition.y, zone.spawnPosition.z),
                    zone.durationMinutes,
                    zone.cost,
                    new Bounds(zone.bounds.minX, zone.bounds.maxX, zone.bounds.minY, zone.bounds.maxY, zone.bounds.minZ, zone.bounds.maxZ)
            ));
        }
        return zones;
    }

    private static void saveConfig(ConfigData configData, File configFile) {
        try {
            configFile.getParentFile().mkdirs();
            try (Writer writer = new FileWriter(configFile)) {
                GSON.toJson(configData, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ConfigData getDefaultConfig() {
        ConfigData data = new ConfigData();
        data.zones = new ArrayList<>();
        // Zone par défaut 1
        data.zones.add(new ZoneConfig(
                1,
                new SpawnPosition(100, 70, 100),
                1,
                100.0,
                new BoundsConfig(90, 110, 65, 75, 90, 110)
        ));
        // Zone par défaut 2
        data.zones.add(new ZoneConfig(
                2,
                new SpawnPosition(-100, 70, -100),
                20,
                250.0,
                new BoundsConfig(-110, -90, 65, 75, -110, -90)
        ));
        return data;
    }

    // Structure de la configuration JSON
    private static class ConfigData {
        List<ZoneConfig> zones;
    }

    private static class ZoneConfig {
        int id;
        SpawnPosition spawnPosition;
        int durationMinutes;
        double cost;
        BoundsConfig bounds;

        public ZoneConfig(int id, SpawnPosition spawnPosition, int durationMinutes, double cost, BoundsConfig bounds) {
            this.id = id;
            this.spawnPosition = spawnPosition;
            this.durationMinutes = durationMinutes;
            this.cost = cost;
            this.bounds = bounds;
        }
    }

    private static class SpawnPosition {
        int x;
        int y;
        int z;

        public SpawnPosition(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static class BoundsConfig {
        int minX;
        int maxX;
        int minY;
        int maxY;
        int minZ;
        int maxZ;

        public BoundsConfig(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }
    }
}