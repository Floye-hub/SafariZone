package com.floye.safarizone.commands;

import com.floye.safarizone.util.SafariZoneManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class SafariZoneCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("safariZone")
                .requires(source -> source.hasPermissionLevel(2)) // Nécessite le niveau de permission 2 (opérateur)
                .then(CommandManager.argument("zoneId", IntegerArgumentType.integer(1)) // Exemple, vous pouvez ajuster la plage d'ID
                        .executes(context -> {
                            int zoneId = IntegerArgumentType.getInteger(context, "zoneId");
                            ServerCommandSource source = context.getSource();
                            SafariZoneManager.enterSafariZone(source.getPlayer(), zoneId);
                            source.sendFeedback(() -> Text.literal("Entrée dans la Safari Zone " + zoneId + "!"), true);
                            return 1; // Succès
                        })
                )
        );
    }
}