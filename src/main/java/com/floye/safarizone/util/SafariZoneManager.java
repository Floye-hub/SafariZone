package com.floye.safarizone.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SafariZoneManager {

    private static final Map<Integer, SafariZoneData> safariZones = new HashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    static {
        safariZones.put(1, new SafariZoneData(new BlockPos(100, 70, 100), 1));
        safariZones.put(2, new SafariZoneData(new BlockPos(-100, 70, -100), 20));
    }

    public static void enterSafariZone(PlayerEntity player, int zoneId) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        SafariZoneData zoneData = safariZones.get(zoneId);
        if (zoneData == null) {
            player.sendMessage(Text.literal("Safari Zone " + zoneId + " non trouvée."), true);
            return;
        }

        BlockPos originalPosition = player.getBlockPos();
        ServerWorld world = (ServerWorld) serverPlayer.getWorld();

        serverPlayer.teleport(
                world,
                zoneData.spawnPosition.getX() + 0.5,
                zoneData.spawnPosition.getY(),
                zoneData.spawnPosition.getZ() + 0.5,
                player.getYaw(),
                player.getPitch()
        );

        player.sendMessage(Text.literal("Vous êtes entré dans la Safari Zone."), true);
        scheduleReturn(serverPlayer, originalPosition, zoneData, zoneId); // Modification ici
    }

    private static void scheduleReturn(ServerPlayerEntity player, BlockPos originalPosition, SafariZoneData zoneData, int zoneId) { // Modification ici
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            if (isInSafariZone(player, zoneData)) {
                ServerWorld world = (ServerWorld) player.getWorld();
                player.teleport(
                        world,
                        originalPosition.getX() + 0.5,
                        originalPosition.getY(),
                        originalPosition.getZ() + 0.5,
                        player.getYaw(),
                        player.getPitch()
                );
                player.sendMessage(Text.literal("Temps de la Safari Zone écoulé. Retour à votre position originale."), true);
            } else {
                player.sendMessage(Text.literal("Temps de la Safari Zone écoulé, mais vous n'étiez pas dans la zone.  Vous restez où vous êtes."), true);
                // Optionnel : Loguer l'événement
                // SafariMod.LOGGER.info("Le joueur {} n'était pas dans la zone {} au moment du renvoi.", player.getName().getString(), zoneId);
            }
        }, zoneData.durationMinutes, TimeUnit.MINUTES);
    }

    private static boolean isInSafariZone(ServerPlayerEntity player, SafariZoneData zoneData) {
        BlockPos playerPos = player.getBlockPos();
        // Ajuster la logique de vérification selon la forme de la zone
        // Ici, on vérifie si le joueur est dans un "cube" centré sur spawnPosition
        // Vous devrez peut-être adapter cela en fonction de la forme de vos zones.
        return playerPos.getX() >= zoneData.spawnPosition.getX() - 10 && // Exemple: +/- 10 blocs autour
                playerPos.getX() <= zoneData.spawnPosition.getX() + 10 &&
                playerPos.getZ() >= zoneData.spawnPosition.getZ() - 10 &&
                playerPos.getZ() <= zoneData.spawnPosition.getZ() + 10 &&
                playerPos.getY() >= zoneData.spawnPosition.getY() - 5 && // Ajustez ceci si besoin
                playerPos.getY() <= zoneData.spawnPosition.getY() + 5;
    }

    private static class SafariZoneData {
        public final BlockPos spawnPosition;
        public final int durationMinutes;

        public SafariZoneData(BlockPos spawnPosition, int durationMinutes) {
            this.spawnPosition = spawnPosition;
            this.durationMinutes = durationMinutes;
        }
    }
}