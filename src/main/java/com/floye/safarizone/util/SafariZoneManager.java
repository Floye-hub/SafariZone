package com.floye.safarizone.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SafariZoneManager {

    private static final Map<Integer, SafariZoneData> safariZones = new HashMap<>();
    private static final Map<String, PlayerSafariState> playerStates = new HashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static MinecraftServer serverInstance; // Référence au serveur Minecraft pour récupérer les joueurs connectés

    static {
        safariZones.put(1, new SafariZoneData(new BlockPos(100, 70, 100), 1));  // Zone 1 : 1 minute
        safariZones.put(2, new SafariZoneData(new BlockPos(-100, 70, -100), 20)); // Zone 2 : 20 minutes
    }

    /**
     * Initialise la gestion des joueurs actifs avec une vérification périodique.
     * Cette méthode doit être appelée lors de l'initialisation du mod.
     */
    public static void startActivePlayerCheck() {
        scheduler.scheduleAtFixedRate(() -> {
            long currentTimeMillis = System.currentTimeMillis();
            playerStates.forEach((playerId, state) -> {
                if (currentTimeMillis >= state.remainingTimeMillis) {
                    // Temps écoulé, téléporter le joueur
                    ServerPlayerEntity player = getPlayerById(playerId);
                    if (player != null) {
                        teleportPlayerOutOfSafariZone(player, state);
                    }
                }
            });
        }, 20, 20, TimeUnit.SECONDS); // Vérifie toutes les secondes
    }

    /**
     * Démarre un joueur dans une Safari Zone.
     */
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

        // Sauvegarde l'état du joueur
        playerStates.put(player.getUuidAsString(), new PlayerSafariState(
                originalPosition,
                zoneId,
                System.currentTimeMillis() + zoneData.durationMinutes * 60 * 1000 // Calcul du temps de fin
        ));
    }

    /**
     * Met à jour l'état du joueur lorsqu'il se déconnecte.
     */
    public static void updatePlayerStateOnLogout(ServerPlayerEntity player) {
        String playerId = player.getUuidAsString();
        PlayerSafariState state = playerStates.get(playerId);

        if (state != null) {
            long currentTimeMillis = System.currentTimeMillis();
            state.remainingTimeMillis = Math.max(0, state.remainingTimeMillis - currentTimeMillis);
        }
    }

    /**
     * Gère la reconnexion d'un joueur.
     */
    public static void handlePlayerReconnection(ServerPlayerEntity player) {
        String playerId = player.getUuidAsString();
        PlayerSafariState state = playerStates.get(playerId);

        if (state != null) {
            long currentTimeMillis = System.currentTimeMillis();

            if (currentTimeMillis >= state.remainingTimeMillis) {
                // Temps écoulé, téléporte le joueur à sa position d'origine
                teleportPlayerOutOfSafariZone(player, state);
            } else {
                // Temps restant, permet au joueur de rester dans la zone
                long remainingSeconds = (state.remainingTimeMillis - currentTimeMillis) / 1000;
                player.sendMessage(Text.literal("Vous êtes toujours dans la Safari Zone. Temps restant : " + remainingSeconds + " secondes."), true);
            }
        }
    }

    /**
     * Téléporte le joueur hors de la Safari Zone.
     */
    private static void teleportPlayerOutOfSafariZone(ServerPlayerEntity player, PlayerSafariState state) {
        player.teleport(
                (ServerWorld) player.getWorld(),
                state.originalPosition.getX() + 0.5,
                state.originalPosition.getY(),
                state.originalPosition.getZ() + 0.5,
                player.getYaw(),
                player.getPitch()
        );
        player.sendMessage(Text.literal("Temps de la Safari Zone écoulé. Vous avez été téléporté à votre position initiale."), true);

        // Supprime l'état du joueur
        playerStates.remove(player.getUuidAsString());
    }

    /**
     * Récupère un joueur connecté par son UUID.
     */
    private static ServerPlayerEntity getPlayerById(String playerId) {
        if (serverInstance == null) return null;
        return serverInstance.getPlayerManager().getPlayer(playerId);
    }

    /**
     * Définit l'instance du serveur Minecraft.
     */
    public static void setServerInstance(MinecraftServer server) {
        serverInstance = server;
    }

    /**
     * Vérifie si un joueur est toujours dans la Safari Zone.
     */
    private static boolean isInSafariZone(ServerPlayerEntity player, SafariZoneData zoneData) {
        BlockPos playerPos = player.getBlockPos();
        return playerPos.getX() >= zoneData.spawnPosition.getX() - 10 &&
                playerPos.getX() <= zoneData.spawnPosition.getX() + 10 &&
                playerPos.getZ() >= zoneData.spawnPosition.getZ() - 10 &&
                playerPos.getZ() <= zoneData.spawnPosition.getZ() + 10 &&
                playerPos.getY() >= zoneData.spawnPosition.getY() - 5 &&
                playerPos.getY() <= zoneData.spawnPosition.getY() + 5;
    }

    /**
     * Les données d'une Safari Zone.
     */
    private static class SafariZoneData {
        public final BlockPos spawnPosition;
        public final int durationMinutes;

        public SafariZoneData(BlockPos spawnPosition, int durationMinutes) {
            this.spawnPosition = spawnPosition;
            this.durationMinutes = durationMinutes;
        }
    }

    /**
     * L'état d'un joueur dans une Safari Zone.
     */
    private static class PlayerSafariState {
        public final BlockPos originalPosition;
        public final int zoneId;
        public long remainingTimeMillis;

        public PlayerSafariState(BlockPos originalPosition, int zoneId, long remainingTimeMillis) {
            this.originalPosition = originalPosition;
            this.zoneId = zoneId;
            this.remainingTimeMillis = remainingTimeMillis;
        }
    }
}