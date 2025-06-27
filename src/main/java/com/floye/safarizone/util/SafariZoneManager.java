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
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.*;

public class SafariZoneManager {
    private static final Map<Integer, SafariZoneData> safariZones = new ConcurrentHashMap<>();
    private static final Map<UUID, PlayerSafariState> playerStates = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static MinecraftServer serverInstance;

    /* Initialisation */
    public static void init() {
        playerStates.putAll(PlayerStateManager.loadPlayerStates());
        cleanInvalidStates();
        startSaveTask();
        startActivePlayerCheck();
        SafariMod.LOGGER.info("SafariZoneManager initialisé avec {} états joueurs", playerStates.size());
    }

    /* Gestion des états */
    public static int cleanInvalidStates() {
        int invalidCount = 0;
        Iterator<Map.Entry<UUID, PlayerSafariState>> it = playerStates.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, PlayerSafariState> entry = it.next();
            if (!entry.getValue().isValid()) {
                it.remove();
                invalidCount++;
                SafariMod.LOGGER.debug("Nettoyage état invalide: {}", entry.getKey());
            }
        }

        if (invalidCount > 0) {
            PlayerStateManager.forceSavePlayerStates();
        }
        return invalidCount;
    }

    /* Tâches planifiées */
    private static void startSaveTask() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                PlayerStateManager.savePlayerStates(getPlayerStates());
            } catch (Exception e) {
                SafariMod.LOGGER.error("Erreur sauvegarde planifiée", e);
            }
        }, 5, 5, TimeUnit.MINUTES);
    }

    private static void startActivePlayerCheck() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                long now = System.currentTimeMillis();
                playerStates.entrySet().removeIf(entry -> {
                    if (now >= entry.getValue().remainingTimeMillis) {
                        handleExpiredPlayer(entry.getKey(), entry.getValue());
                        return true;
                    }
                    return false;
                });
            } catch (Exception e) {
                SafariMod.LOGGER.error("Erreur vérification joueurs", e);
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    private static void handleExpiredPlayer(UUID playerId, PlayerSafariState state) {
        serverInstance.execute(() -> {
            ServerPlayerEntity player = serverInstance.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                teleportPlayerOutOfSafariZone(player, state);
            }
        });
    }

    /* Gestion des joueurs */
    public static void enterSafariZone(PlayerEntity player, int zoneId) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;

        SafariZoneData zone = safariZones.get(zoneId);
        if (zone == null) {
            player.sendMessage(Text.literal("Zone invalide"), false);
            return;
        }

        EconomyHandler.getAccount(player.getUuid()).thenAccept(account -> {
            if (account == null || !EconomyHandler.remove(account, zone.cost)) {
                serverPlayer.sendMessage(Text.literal("Erreur paiement"), false);
                return;
            }

            serverInstance.execute(() -> {
                try {
                    ServerWorld safariWorld = serverInstance.getWorld(
                            RegistryKey.of(RegistryKey.ofRegistry(Identifier.of("minecraft", "dimension")),
                                    Identifier.of(zone.dimensionId)
                            ));

                    if (safariWorld == null) {
                        serverPlayer.sendMessage(Text.literal("Dimension introuvable"), false);
                        return;
                    }

                    PlayerSafariState state = new PlayerSafariState(
                            player.getBlockPos(),
                            player.getWorld().getRegistryKey(),
                            zoneId,
                            System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(zone.durationMinutes)
                    );

                    playerStates.put(player.getUuid(), state);
                    serverPlayer.teleport(
                            safariWorld,
                            zone.spawnPosition.getX() + 0.5,
                            zone.spawnPosition.getY(),
                            zone.spawnPosition.getZ() + 0.5,
                            player.getYaw(),
                            player.getPitch()
                    );
                    PlayerStateManager.forceSavePlayerStates();
                } catch (Exception e) {
                    SafariMod.LOGGER.error("Erreur entrée SafariZone", e);
                }
            });
        });
    }

    public static void updatePlayerStateOnLogout(ServerPlayerEntity player) {
        PlayerSafariState state = playerStates.get(player.getUuid());
        if (state != null) {
            state.logoutTimeMillis = System.currentTimeMillis();
            PlayerStateManager.forceSavePlayerStates();
        }
    }

    public static void handlePlayerReconnection(ServerPlayerEntity player) {
        PlayerSafariState state = playerStates.get(player.getUuid());
        if (state == null) return;

        if (!state.isValid()) {
            playerStates.remove(player.getUuid());
            return;
        }

        serverInstance.execute(() -> {
            try {
                if (state.remainingTimeMillis <= 0) {
                    teleportPlayerOutOfSafariZone(player, state);
                    return;
                }

                if (state.logoutTimeMillis != null) {
                    long remaining = state.remainingTimeMillis -
                            (System.currentTimeMillis() - state.logoutTimeMillis);
                    state.remainingTimeMillis = Math.max(0, remaining);
                }

                if (state.remainingTimeMillis <= 0) {
                    teleportPlayerOutOfSafariZone(player, state);
                } else {
                    player.sendMessage(Text.literal("Temps restant: " +
                            TimeUnit.MILLISECONDS.toSeconds(state.remainingTimeMillis) + "s"), true);
                }
            } catch (Exception e) {
                SafariMod.LOGGER.error("Erreur reconnexion joueur", e);
            }
        });
    }

    private static void teleportPlayerOutOfSafariZone(ServerPlayerEntity player, PlayerSafariState state) {
        try {
            ServerWorld targetWorld = resolveOriginalWorld(player, state);
            player.teleport(
                    targetWorld,
                    state.originalPosition.getX() + 0.5,
                    state.originalPosition.getY(),
                    state.originalPosition.getZ() + 0.5,
                    player.getYaw(),
                    player.getPitch()
            );
            playerStates.remove(player.getUuid());
            PlayerStateManager.forceSavePlayerStates();
        } catch (Exception e) {
            SafariMod.LOGGER.error("Échec téléportation", e);
            player.sendMessage(Text.literal("Erreur téléportation"), false);
        }
    }

    private static ServerWorld resolveOriginalWorld(ServerPlayerEntity player, PlayerSafariState state) {
        // Essai avec la dimension sauvegardée
        if (state.originalDimension != null) {
            ServerWorld world = player.getServer().getWorld(state.originalDimension);
            if (world != null) return world;
        }

        // Fallback avec l'ID de dimension
        if (state.originalDimensionId != null) {
            Identifier dimId = Identifier.tryParse(state.originalDimensionId);
            if (dimId != null) {
                RegistryKey<World> worldKey = RegistryKey.of(
                        RegistryKey.ofRegistry(Identifier.of("minecraft", "dimension")),
                        dimId
                );
                ServerWorld world = player.getServer().getWorld(worldKey);
                if (world != null) return world;
            }
        }

        // Dernier recours
        return player.getServer().getOverworld();
    }

    /* Classes internes */
    public static class SafariZoneData {
        public final BlockPos spawnPosition;
        public final int durationMinutes;
        public final double cost;
        public final Bounds bounds;
        public final String dimensionId;

        public SafariZoneData(BlockPos pos, int duration, double cost, Bounds bounds, String dimId) {
            this.spawnPosition = pos;
            this.durationMinutes = duration;
            this.cost = cost;
            this.bounds = bounds;
            this.dimensionId = dimId;
        }

        public static class Bounds {
            public final int minX, maxX, minY, maxY, minZ, maxZ;
            public Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
                this.minX = minX; this.maxX = maxX;
                this.minY = minY; this.maxY = maxY;
                this.minZ = minZ; this.maxZ = maxZ;
            }
        }
    }

    public static class PlayerSafariState {
        public final BlockPos originalPosition;
        public transient RegistryKey<World> originalDimension;
        public final String originalDimensionId;
        public final int zoneId;
        public long remainingTimeMillis;
        public Long logoutTimeMillis;

        public PlayerSafariState(BlockPos pos, RegistryKey<World> dim, int zone, long time) {
            this.originalPosition = pos;
            this.originalDimension = dim;
            this.originalDimensionId = dim != null ? dim.getValue().toString() : null;
            this.zoneId = zone;
            this.remainingTimeMillis = time;
        }

        public boolean isValid() {
            return originalPosition != null &&
                    originalDimensionId != null &&
                    remainingTimeMillis > 0 &&
                    safariZones.containsKey(zoneId);
        }
    }

    /* Getters/Setters */
    public static void setServerInstance(MinecraftServer server) {
        serverInstance = server;
    }

    public static void setZones(Map<Integer, SafariZoneData> zones) {
        safariZones.clear();
        safariZones.putAll(zones);
    }

    public static boolean isInSafariZone(ServerPlayerEntity player, SafariZoneData zone) {
        BlockPos pos = player.getBlockPos();
        return pos.getX() >= zone.bounds.minX && pos.getX() <= zone.bounds.maxX &&
                pos.getY() >= zone.bounds.minY && pos.getY() <= zone.bounds.maxY &&
                pos.getZ() >= zone.bounds.minZ && pos.getZ() <= zone.bounds.maxZ;
    }

    public static Map<UUID, PlayerSafariState> getPlayerStates() {
        return playerStates;
    }
}