package com.floye.safarizone;

import com.floye.safarizone.commands.SafariZoneCommand;
import com.floye.safarizone.config.ConfigLoader;
import com.floye.safarizone.util.SafariZoneManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class SafariMod implements ModInitializer {
	public static final String MOD_ID = "safari-zone-mod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Chargement de la configuration et autres initialisations
		Map<Integer, SafariZoneManager.SafariZoneData> zones = ConfigLoader.loadConfig("config/SafariZone/safarizone_config.json");
		SafariZoneManager.setZones(zones);

		// Initialisation de l'instance du serveur dès le démarrage
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			SafariZoneManager.setServerInstance(server);
			LOGGER.info("Instance du serveur définie pour SafariZoneManager.");
		});
		SafariZoneManager.init();
		SafariZoneManager.startActivePlayerCheck();

		// Enregistrement de la commande
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			SafariZoneCommand.register(dispatcher);
		});

		// Gestion des événements de déconnexion
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				SafariZoneManager.updatePlayerStateOnLogout(handler.getPlayer()));

		// Gestion des événements de reconnexion
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				SafariZoneManager.handlePlayerReconnection(handler.getPlayer()));

		LOGGER.info("Safari Zone Mod initialized!");
	}
}