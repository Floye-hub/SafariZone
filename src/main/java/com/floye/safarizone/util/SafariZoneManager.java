package com.floye.safarizone.util;

import com.floye.safarizone.SafariMod;
import com.floye.safarizone.config.PlayerStateManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SafariZoneManager {

    // Map contenant la configuration des zones, initialisée via le ConfigLoader
    private static Map<Integer, SafariZoneData> safariZones = new HashMap<>();

    // Utilisation d'un ConcurrentHashMap avec UUID comme clé pour suivre l'état des joueurs dans une zone
    private static Map<UUID, PlayerSafariState> playerStates;

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static MinecraftServer serverInstance;

    public static void init() {
        playerStates = PlayerStateManager.loadPlayerStates();
        startSaveTask();
        startActivePlayerCheck();
    }

    private static void startSaveTask() {
        scheduler.scheduleAtFixedRate(() -> {
            PlayerStateManager.savePlayerStates(playerStates);
            SafariMod.LOGGER.info("État des joueurs sauvegardé");
        }, 5, 5, TimeUnit.MINUTES);
    }

    public static void setZones(Map<Integer, SafariZoneData> zones) {
        safariZones = zones;
    }

    public static void setServerInstance(MinecraftServer server) {
        serverInstance = server;
    }

    public static void startActivePlayerCheck() {
        scheduler.scheduleAtFixedRate(() -> {
            long currentTimeMillis = System.currentTimeMillis();

            playerStates.forEach((playerUUID, state) -> {
                if (currentTimeMillis >= state.remainingTimeMillis) {
                    ServerPlayerEntity player = getPlayerById(playerUUID);
                    if (player == null) {
                        SafariMod.LOGGER.warn("Le joueur avec l'UUID {} n'a pas été trouvé lors de la vérification.", playerUUID);
                        // Optionnel : supprimer l'état du joueur pour éviter des vérifications futures
                        playerStates.remove(playerUUID);
                        return;
                    }
                    SafariZoneData zoneData = safariZones.get(state.zoneId);
                    if (zoneData == null) {
                        SafariMod.LOGGER.warn("Les données de la zone {} sont introuvables pour le joueur {}.", state.zoneId, playerUUID);
                        return;
                    }
                    SafariMod.LOGGER.info("Temps écoulé pour le joueur {} dans la zone {}. Lancement de la téléportation.", playerUUID, state.zoneId);
                    player.getServer().execute(() -> {
                        try {
                            // Vérifier si le joueur est toujours dans la zone
                            if (isInSafariZone(player, zoneData)) {
                                teleportPlayerOutOfSafariZone(player, state);
                                SafariMod.LOGGER.info("Le joueur {} a été téléporté hors de la Safari Zone.", playerUUID);
                            } else {
                                player.sendMessage(Text.literal("Votre temps dans la Safari Zone est écoulé."), true);
                                playerStates.remove(player.getUuid());
                                SafariMod.LOGGER.info("Le joueur {} n'était plus dans la zone mais message envoyé.", playerUUID);
                            }
                        } catch (Exception e) {
                            SafariMod.LOGGER.error("Erreur lors de la téléportation du joueur {}: ", playerUUID, e);
                        }
                    });
                }
            });
        }, 20, 20, TimeUnit.SECONDS);
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

        EconomyHandler.getAccount(player.getUuid()).thenAccept(account -> {
            if (account == null) {
                serverPlayer.sendMessage(Text.literal("Impossible de récupérer votre compte économie."), true);
                return;
            }

            double balance = EconomyHandler.getBalance(account);
            if (balance < zoneData.cost) {
                serverPlayer.sendMessage(Text.literal("Solde insuffisant pour accéder à la Safari Zone. Nécessaire: " + zoneData.cost), true);
                return;
            }

            boolean withdrawn = EconomyHandler.remove(account, zoneData.cost);
            if (!withdrawn) {
                serverPlayer.sendMessage(Text.literal("Erreur lors du retrait de fonds pour accéder à la Safari Zone."), true);
                return;
            }

            serverPlayer.getServer().execute(() -> {
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

                serverPlayer.sendMessage(Text.literal("Vous êtes entré dans la Safari Zone."), true);

                playerStates.put(serverPlayer.getUuid(), new PlayerSafariState(
                        originalPosition,
                        zoneId,
                        System.currentTimeMillis() + zoneData.durationMinutes * 60 * 1000
                ));
                PlayerStateManager.savePlayerStates(playerStates); // Ajoutez cette ligne
            });
        });
    }

    public static void updatePlayerStateOnLogout(ServerPlayerEntity player) {
        UUID playerUUID = player.getUuid();
        PlayerSafariState state = playerStates.get(playerUUID);
        if (state != null) {
            state.logoutTimeMillis = System.currentTimeMillis(); // Stocker le temps de déconnexion
            SafariMod.LOGGER.info("Joueur {} s'est déconnecté", playerUUID);
        } else {
            SafariMod.LOGGER.info("État du joueur {} non trouvé lors de la déconnexion", playerUUID);
        }
    }

    public static void handlePlayerReconnection(ServerPlayerEntity player) {
        SafariMod.LOGGER.info("Joueur {} s'est reconnecté", player.getUuid());
        UUID playerUUID = player.getUuid();
        PlayerSafariState state = playerStates.get(playerUUID);
        if (state == null) {
            SafariMod.LOGGER.info("État du joueur {} non trouvé", playerUUID);
            return;
        }
        if (state.logoutTimeMillis == null) {
            SafariMod.LOGGER.info("Temps de déconnexion du joueur {} non enregistré", playerUUID);
            return;
        }

        long currentTimeMillis = System.currentTimeMillis();
        long timeElapsedSinceLogout = currentTimeMillis - state.logoutTimeMillis;
        long newRemainingTimeMillis = state.remainingTimeMillis - timeElapsedSinceLogout;

        if (newRemainingTimeMillis <= 0) {
            state.remainingTimeMillis = 0; // Pour éviter les problèmes de valeur négative
            player.getServer().execute(() -> {
                teleportPlayerOutOfSafariZone(player, state);
            });
        } else {
            state.remainingTimeMillis = newRemainingTimeMillis;
            long remainingSeconds = state.remainingTimeMillis / 1000;
            player.sendMessage(Text.literal("Vous êtes toujours dans la Safari Zone. Temps restant : " + remainingSeconds + " secondes."), true);
        }
    }

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
        playerStates.remove(player.getUuid());
    }

    private static boolean isInSafariZone(ServerPlayerEntity player, SafariZoneData zoneData) {
        BlockPos pos = player.getBlockPos();
        return pos.getX() >= zoneData.bounds.minX &&
                pos.getX() <= zoneData.bounds.maxX &&
                pos.getY() >= zoneData.bounds.minY &&
                pos.getY() <= zoneData.bounds.maxY &&
                pos.getZ() >= zoneData.bounds.minZ &&
                pos.getZ() <= zoneData.bounds.maxZ;
    }

    private static ServerPlayerEntity getPlayerById(UUID playerUUID) {
        if (serverInstance == null) return null;
        return serverInstance.getPlayerManager().getPlayer(playerUUID);
    }

    public static class SafariZoneData {
        public final BlockPos spawnPosition;
        public final int durationMinutes;
        public final double cost;
        public final Bounds bounds;

        public SafariZoneData(BlockPos spawnPosition, int durationMinutes, double cost, Bounds bounds, String dimensionId) {
            this.spawnPosition = spawnPosition;
            this.durationMinutes = durationMinutes;
            this.cost = cost;
            this.bounds = bounds;
        }

        public static class Bounds {
            public final int minX;
            public final int maxX;
            public final int minY;
            public final int maxY;
            public final int minZ;
            public final int maxZ;

            public Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
                this.minX = minX;
                this.maxX = maxX;
                this.minY = minY;
                this.maxY = maxY;
                this.minZ = minZ;
                this.maxZ = maxZ;
            }
        }
    }

    public static class PlayerSafariState {
        public final BlockPos originalPosition;
        public final int zoneId;
        public long remainingTimeMillis;
        public Long logoutTimeMillis;

        public PlayerSafariState(BlockPos originalPosition, int zoneId, long remainingTimeMillis) {
            this.originalPosition = originalPosition;
            this.zoneId = zoneId;
            this.remainingTimeMillis = remainingTimeMillis;
        }
    }
}