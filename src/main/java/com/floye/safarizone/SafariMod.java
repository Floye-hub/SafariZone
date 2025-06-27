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

public class SafariMod implements ModInitializer {
	public static final String MOD_ID = "safari-zone-mod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		SafariZoneManager.setZones(ConfigLoader.loadConfig("config/SafariZone/safarizone_config.json"));

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			SafariZoneManager.setServerInstance(server);
			LOGGER.info("Instance serveur initialisée");
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			int cleaned = SafariZoneManager.validatePlayerStates();
			LOGGER.info("{} états joueurs invalides nettoyés", cleaned);
		});

		SafariZoneManager.init();

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) ->
				SafariZoneCommand.register(dispatcher));

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				SafariZoneManager.updatePlayerStateOnLogout(handler.getPlayer()));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				SafariZoneManager.handlePlayerReconnection(handler.getPlayer()));

		LOGGER.info("SafariZone Mod initialisé");
	}
}