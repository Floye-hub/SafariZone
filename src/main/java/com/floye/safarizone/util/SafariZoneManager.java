package com.floye.safarizone.util;

import com.floye.safarizone.util.EconomyHandler;
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

    // Map contenant la configuration des zones, initialisée via le ConfigLoader
    private static Map<Integer, SafariZoneData> safariZones = new HashMap<>();
    // Map pour suivre l'état des joueurs dans une zone
    private static final Map<String, PlayerSafariState> playerStates = new HashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static MinecraftServer serverInstance;

    /**
     * Permet d'assigner la configuration des zones lue depuis le JSON.
     */
    public static void setZones(Map<Integer, SafariZoneData> zones) {
        safariZones = zones;
    }

    /**
     * Définit l'instance du serveur Minecraft.
     */
    public static void setServerInstance(MinecraftServer server) {
        serverInstance = server;
    }

    /**
     * Lance une vérification périodique de l'état des joueurs dans les Safari Zones.
     */
    public static void startActivePlayerCheck() {
        scheduler.scheduleAtFixedRate(() -> {
            long currentTimeMillis = System.currentTimeMillis();

            playerStates.forEach((playerId, state) -> {
                if (currentTimeMillis >= state.remainingTimeMillis) {
                    ServerPlayerEntity player = getPlayerById(playerId);
                    if (player != null) {
                        SafariZoneData zoneData = safariZones.get(state.zoneId);
                        if (zoneData != null && isInSafariZone(player, zoneData)) {
                            teleportPlayerOutOfSafariZone(player, state);
                        } else {
                            player.sendMessage(Text.literal("Votre temps dans la Safari Zone est écoulé."), true);
                            playerStates.remove(playerId);
                        }
                    }
                }
            });
        }, 20, 20, TimeUnit.SECONDS);
    }

    /**
     * Permet à un joueur d'entrer dans une Safari Zone après vérification de son solde.
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

        // Vérification asynchrone du compte économie du joueur
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

            // Pour exécuter la téléportation sur le thread principal du serveur
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

                // Stocker l'état du joueur pour gérer la durée de présence dans la zone
                playerStates.put(serverPlayer.getUuidAsString(), new PlayerSafariState(
                        originalPosition,
                        zoneId,
                        System.currentTimeMillis() + zoneData.durationMinutes * 60 * 1000
                ));
            });
        });
    }

    /**
     * Met à jour l'état d'un joueur lors de sa déconnexion.
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
                teleportPlayerOutOfSafariZone(player, state);
            } else {
                long remainingSeconds = (state.remainingTimeMillis - currentTimeMillis) / 1000;
                player.sendMessage(Text.literal("Vous êtes toujours dans la Safari Zone. Temps restant : " + remainingSeconds + " secondes."), true);
            }
        }
    }

    /**
     * Téléporte un joueur hors de la Safari Zone.
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
        playerStates.remove(player.getUuidAsString());
    }

    /**
     * Vérifie si le joueur se trouve dans la zone délimitée (les bornes sont configurables via JSON).
     */
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
     * Récupère un joueur connecté par son UUID.
     */
    private static ServerPlayerEntity getPlayerById(String playerId) {
        if (serverInstance == null) return null;
        return serverInstance.getPlayerManager().getPlayer(playerId);
    }

    //////////////////////////////////////////////////////////////////////
    // Classes internes pour contenir les données de zones et états joueurs  //
    //////////////////////////////////////////////////////////////////////

    /**
     * Contient les informations d'une Safari Zone (position de spawn, durée, coût et bornes de la zone).
     */
    public static class SafariZoneData {
        public final BlockPos spawnPosition;
        public final int durationMinutes;
        public final double cost;
        public final Bounds bounds;

        public SafariZoneData(BlockPos spawnPosition, int durationMinutes, double cost, Bounds bounds) {
            this.spawnPosition = spawnPosition;
            this.durationMinutes = durationMinutes;
            this.cost = cost;
            this.bounds = bounds;
        }

        /**
         * Représente les bornes de la zone sous la forme de 6 valeurs (min/max pour X, Y et Z).
         */
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

    /**
     * Stocke l'état d'un joueur en Safari Zone (position initiale, zone, et temps restant).
     */
    public static class PlayerSafariState {
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