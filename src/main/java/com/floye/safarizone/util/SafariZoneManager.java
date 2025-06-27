package com.floye.safarizone.util;

import com.floye.safarizone.SafariMod;
import com.floye.safarizone.config.PlayerStateManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.Registries;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;



public class SafariZoneManager {

    // Map contenant la configuration des zones, initialisée via le ConfigLoader
    private static Map<Integer, SafariZoneData> safariZones = new HashMap<>();

    // Map pour suivre l'état des joueurs dans une zone
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
                        // Si le joueur est déconnecté, on marque le temps écoulé
                        state.remainingTimeMillis = 0;
                        PlayerStateManager.savePlayerStates(playerStates);
                        return;
                    }
                    SafariZoneData zoneData = safariZones.get(state.zoneId);
                    if (zoneData == null) {
                        return;
                    }
                    player.getServer().execute(() -> {
                        try {
                            if (isInSafariZone(player, zoneData)) {
                                teleportPlayerOutOfSafariZone(player, state);
                            } else {
                                player.sendMessage(Text.literal("Votre temps dans la Safari Zone est écoulé."), true);
                                playerStates.remove(player.getUuid());
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
                RegistryKey<World> originalDimension = serverPlayer.getWorld().getRegistryKey(); // Sauvegarde de la dimension actuelle

                // Création du RegistryKey pour la dimension Safari Zone
                RegistryKey<World> dimensionKey = RegistryKey.of(
                        net.minecraft.registry.RegistryKeys.WORLD, // Correct pour Fabric 1.21.1
                        Identifier.of(zoneData.dimensionId)
                );

                // Récupération de la dimension Safari Zone
                ServerWorld safariWorld = serverPlayer.getServer().getWorld(dimensionKey);

                if (safariWorld != null) {
                    // Téléportation dans la dimension Safari Zone
                    serverPlayer.teleport(
                            safariWorld,
                            zoneData.spawnPosition.getX() + 0.5,
                            zoneData.spawnPosition.getY(),
                            zoneData.spawnPosition.getZ() + 0.5,
                            player.getYaw(),
                            player.getPitch()
                    );

                    serverPlayer.sendMessage(Text.literal("Vous êtes entré dans la Safari Zone."), true);

                    // Sauvegarde de l'état du joueur, y compris la dimension d'origine
                    playerStates.put(serverPlayer.getUuid(), new PlayerSafariState(
                            originalPosition,
                            originalDimension, // Sauvegarde de la dimension d'origine
                            zoneId,
                            System.currentTimeMillis() + zoneData.durationMinutes * 60 * 1000
                    ));
                    PlayerStateManager.savePlayerStates(playerStates);
                } else {
                    serverPlayer.sendMessage(Text.literal("La dimension spécifiée pour la Safari Zone n'existe pas."), true);
                }
            });
        });
    }

    public static void updatePlayerStateOnLogout(ServerPlayerEntity player) {
        UUID playerUUID = player.getUuid();
        PlayerSafariState state = playerStates.get(playerUUID);
        if (state != null) {
            state.logoutTimeMillis = System.currentTimeMillis(); // Stocker le temps de déconnexion
        }
    }

    public static void handlePlayerReconnection(ServerPlayerEntity player) {
        UUID playerUUID = player.getUuid();
        PlayerSafariState state = playerStates.get(playerUUID);
        if (state == null) {
            return;
        }

        if (state.remainingTimeMillis <= 0) {
            player.getServer().execute(() -> teleportPlayerOutOfSafariZone(player, state));
            return;
        }

        long currentTimeMillis = System.currentTimeMillis();
        long timeElapsedSinceLogout = currentTimeMillis - state.logoutTimeMillis;
        long newRemainingTimeMillis = state.remainingTimeMillis - timeElapsedSinceLogout;

        if (newRemainingTimeMillis <= 0) {
            state.remainingTimeMillis = 0;
            player.getServer().execute(() -> teleportPlayerOutOfSafariZone(player, state));
        } else {
            state.remainingTimeMillis = newRemainingTimeMillis;
            long remainingSeconds = state.remainingTimeMillis / 1000;
            player.sendMessage(Text.literal("Vous êtes toujours dans la Safari Zone. Temps restant : " + remainingSeconds + " secondes."), true);
        }
    }

    private static void teleportPlayerOutOfSafariZone(ServerPlayerEntity player, PlayerSafariState state) {
        // Récupération de la dimension d'origine
        ServerWorld originalWorld = player.getServer().getWorld(state.originalDimension);

        if (originalWorld != null) {
            // Téléportation dans la dimension d'origine
            player.teleport(
                    originalWorld,
                    state.originalPosition.getX() + 0.5,
                    state.originalPosition.getY(),
                    state.originalPosition.getZ() + 0.5,
                    player.getYaw(),
                    player.getPitch()
            );
            player.sendMessage(Text.literal("Temps de la Safari Zone écoulé. Vous avez été téléporté à votre position initiale."), true);
        } else {
            // Si la dimension d'origine n'existe pas, utilisez la dimension actuelle
            player.sendMessage(Text.literal("Erreur : Impossible de trouver la dimension d'origine. Téléportation annulée."), true);
        }

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

    /**
     * Récupère le ServerWorld correspondant à l'identifiant de la dimension.
     *
     * @param dimensionId l'identifiant de la dimension, par exemple "minecraft:overworld"
     * @return le ServerWorld correspondant ou null si introuvable
     */
    private static ServerWorld getWorldByDimension(String dimensionId) {
        if (serverInstance == null) return null;

        Identifier dimIdentifier = Identifier.of(dimensionId);
        RegistryKey<World> worldKey = RegistryKey.of(RegistryKey.ofRegistry(Identifier.of("minecraft", "dimension")), dimIdentifier);
        return serverInstance.getWorld(worldKey);
    }



    private static ServerPlayerEntity getPlayerById(UUID playerUUID) {
        if (serverInstance == null) return null;
        return serverInstance.getPlayerManager().getPlayer(playerUUID);
    }

    // Classe encapsulant les données d'une Safari Zone
    public static class SafariZoneData {
        public final BlockPos spawnPosition;
        public final int durationMinutes;
        public final double cost;
        public final Bounds bounds;
        public final String dimensionId; // Champ pour la dimension

        public SafariZoneData(BlockPos spawnPosition, int durationMinutes, double cost, Bounds bounds, String dimensionId) {
            this.spawnPosition = spawnPosition;
            this.durationMinutes = durationMinutes;
            this.cost = cost;
            this.bounds = bounds;
            this.dimensionId = dimensionId;
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

    // Classe qui maintient l'état d'un joueur dans une Safari Zone
    public static class PlayerSafariState {
        public final BlockPos originalPosition;
        public final RegistryKey<World> originalDimension; // Nouvelle dimension d'origine
        public final int zoneId;
        public long remainingTimeMillis;
        public Long logoutTimeMillis;

        public PlayerSafariState(BlockPos originalPosition, RegistryKey<World> originalDimension, int zoneId, long remainingTimeMillis) {
            this.originalPosition = originalPosition;
            this.originalDimension = originalDimension; // Sauvegarde de la dimension d'origine
            this.zoneId = zoneId;
            this.remainingTimeMillis = remainingTimeMillis;
        }
    }
}