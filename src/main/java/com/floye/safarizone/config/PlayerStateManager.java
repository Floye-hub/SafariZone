package com.floye.safarizone.config;

import com.floye.safarizone.SafariMod;
import com.floye.safarizone.util.SafariZoneManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerStateManager {
    private static final String FILE_NAME = "config/SafariZone/player_states.json";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(java.lang.reflect.Modifier.TRANSIENT)
            .create();
    private static final Type TYPE = new TypeToken<Map<UUID, SafariZoneManager.PlayerSafariState>>() {}.getType();

    public static void savePlayerStates(Map<UUID, SafariZoneManager.PlayerSafariState> playerStates) {
        try {
            File file = new File(FILE_NAME);
            file.getParentFile().mkdirs(); // Crée le dossier si inexistant

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(playerStates, writer);
                SafariMod.LOGGER.info("Sauvegarde des états joueurs effectuée");
            }
        } catch (IOException e) {
            SafariMod.LOGGER.error("Erreur lors de l'enregistrement des états des joueurs", e);
        }
    }

    public static Map<UUID, SafariZoneManager.PlayerSafariState> loadPlayerStates() {
        File file = new File(FILE_NAME);
        if (!file.exists()) {
            return new ConcurrentHashMap<>();
        }

        try (FileReader reader = new FileReader(file)) {
            Map<UUID, SafariZoneManager.PlayerSafariState> states = GSON.fromJson(reader, TYPE);
            return states != null ? states : new ConcurrentHashMap<>();
        } catch (IOException e) {
            SafariMod.LOGGER.error("Erreur lors du chargement des états des joueurs", e);
            return new ConcurrentHashMap<>();
        }
    }

    public static void forceSavePlayerStates() {
        savePlayerStates(SafariZoneManager.playerStates);
    }
}