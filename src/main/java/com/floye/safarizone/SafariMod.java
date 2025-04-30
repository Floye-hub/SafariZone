package com.floye.safarizone; // Remplacez avec votre package

import com.floye.safarizone.commands.SafariZoneCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SafariMod implements ModInitializer {
	public static final String MOD_ID = "safari-zone-mod"; // ID unique pour votre mod
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Enregistrement de la commande
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			SafariZoneCommand.register(dispatcher);
		});
		LOGGER.info("Safari Zone Mod initialized!");
	}
}