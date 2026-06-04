package it.pruefert.headvault;

import it.pruefert.headvault.access.command.HeadVaultCommands;
import it.pruefert.headvault.access.npc.NpcManager;
import it.pruefert.headvault.access.villager.NamedVillagerListener;
import it.pruefert.headvault.compat.EntityInteractions;
import it.pruefert.headvault.compat.McItems;
import it.pruefert.headvault.config.HeadVaultConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * HeadVault entry point. Wires the live {@link HeadVaultRuntime} to the three access modes
 * (command, named villager, NPC), command registration, and the server lifecycle. Server-side
 * only — vanilla clients need nothing installed.
 */
public class HeadVault implements ModInitializer {

    public static final String MOD_ID = "headvault";
    public static final Logger LOGGER = LoggerFactory.getLogger("HeadVault");

    @Override
    public void onInitialize() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        HeadVaultRuntime runtime = new HeadVaultRuntime(configDir, LOGGER);
        NpcManager npcManager = new NpcManager(runtime);

        // One interaction handler serves both the NPC and named-villager access modes.
        EntityInteractions.register((player, entity) -> {
            // Holding a name tag? Let vanilla renaming win, so a "Head Trader" can be renamed back.
            if (McItems.isHoldingNameTag(player)) {
                return false;
            }
            HeadVaultConfig config = runtime.config();
            if (config.access.npc.enabled && npcManager.tryOpen(player, entity)) {
                return true;
            }
            if (config.access.villager.enabled && NamedVillagerListener.matches(entity, config)) {
                runtime.openShop(player);
                return true;
            }
            return false;
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                HeadVaultCommands.register(dispatcher, runtime, npcManager));

        ServerLifecycleEvents.SERVER_STARTED.register(runtime::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> runtime.onServerStopping());

        LOGGER.info("HeadVault initialized (server-side; vanilla clients supported)");
    }
}
