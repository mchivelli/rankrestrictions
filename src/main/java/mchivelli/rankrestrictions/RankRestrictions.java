package mchivelli.rankrestrictions;

import mchivelli.rankrestrictions.commands.RankRestrictionsCommands;
import mchivelli.rankrestrictions.config.RankRestrictionsConfig;
import mchivelli.rankrestrictions.events.ItemRestrictionEvents;
import mchivelli.rankrestrictions.util.FTBRanksHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
// import mchivelli.rankrestrictions.config.RankRestrictionData; // No longer directly creating RankRestrictionData here for examples
// import java.util.Arrays; // For creating lists for items - Removed as it's no longer used
import java.util.Collection;

@Mod(RankRestrictions.MOD_ID)
public class RankRestrictions {
    public static final String MOD_ID = "rankrestrictions";
    public static final Logger LOGGER = LogManager.getLogger("FTBRanks Restrictions");

    private static RankRestrictions instance;
    private RankRestrictionsConfig config;
    private boolean hasInitializedRanks = false;

    public RankRestrictions() {
        instance = this;
        config = new RankRestrictionsConfig();

        // Register to the mod event bus using NeoForge's recommended approach
        // This avoids the deprecated FMLJavaModLoadingContext.get() method
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus(); // Still using this for now as the alternatives are more complex
        modEventBus.addListener(this::setup);

        // Register server and client event bus 
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new ItemRestrictionEvents());
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        
        LOGGER.info("FTBRanks Rank Restrictions mod initialized");
    }

    public static RankRestrictions getInstance() {
        return instance;
    }

    public RankRestrictionsConfig getConfig() {
        return config;
    }

    private void setup(final FMLCommonSetupEvent event) {
        // Do common setup
        LOGGER.info("FTBRanks Rank Restrictions addon is loading...");

        // Load the config
        config.loadConfig();
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Log config file status
        try {
            LOGGER.info("Checking RankRestrictions config file...");
            Path configPath = FMLPaths.CONFIGDIR.get().resolve("rankrestrictions/restrictions.toml");
            
            if (!Files.exists(configPath)) {
                LOGGER.info("Config file doesn't exist at " + configPath + ". Generating a new one with detailed examples.");
                
                String defaultConfigContent = """
# FTBRanks Rank Restrictions Configuration File
#
# How To Use:
#
# [messages]
#   default_restriction: The global default message shown when an item is restricted.
#                        %%item%% will be replaced with the item's name.
#                        Uses standard Minecraft color codes (e.g., &c for red).
#
# [restrictions]
#   This section will be automatically populated by the mod with ranks discovered
#   from your FTBRanks setup. Each discovered rank will initially appear with
#   an empty 'restriction_sets = []' list.
#
#   To define restrictions for a rank, you will modify its entry.
#   For example, if FTBRanks has a rank called 'player':
#
#   Initially, you might see this after the mod runs:
#     [restrictions.player]
#       restriction_sets = []
#
#   To add restrictions to the 'player' rank, you would change it to:
#     [restrictions.player]
#       # This is the first set of restrictions for 'player'
#       [[restrictions.player.restriction_sets]]
#         message = "&cPlayers cannot use %%item%%."
#         items = [
#           "minecraft:diamond_block",      # Restrict diamond blocks
#           "mekanism:*",                 # Restrict all items from the Mekanism mod
#           "#forge:ores/netherite_scrap" # Restrict items with the tag forge:ores/netherite_scrap
#         ]
#
#       # To add a second, different set of restrictions for 'player':
#       [[restrictions.player.restriction_sets]]
#         message = "&ePlayers also cannot use %%item%% from this special list."
#         items = ["minecraft:dragon_egg"]
#
#   Key points for defining restriction sets:
#     - Each `[[restrictions.your_rank_id.restriction_sets]]` block defines one set of rules.
#     - `message` (Optional): Custom message for this set. Uses %%item%% placeholder.
#                             If omitted, 'default_restriction' from [messages] is used.
#     - `items`: A list of strings specifying what to restrict.
#
#   Item Pattern Types for the 'items' list:
#     1. Exact Item ID: "minecraft:stone" or "mod_id:item_name"
#     2. Mod Wildcard:  "mod_id:*" (restricts all items from 'mod_id')
#     3. Item Tag:      "#namespace:tag_path" (e.g., "#forge:ores/diamond", "#minecraft:planks")
#                       (The tag must exist and be loaded by Minecraft/Forge).
#
#   The placeholder %%item%% in messages will be replaced with the specific item's display name.
#

[messages]
	default_restriction = "&cYou are not allowed to use %%item%% with your current rank!"

[restrictions]
	# This section is automatically managed by the mod.
	# - On first load (or if this file is deleted), the mod will discover all ranks
	#   from FTBRanks and list them here, each with 'restriction_sets = []'.
	# - You can then edit each rank to add your desired restriction rules as shown in the
	#   'How To Use' section above.
	# - If you add new ranks to FTBRanks later, run '/rankrestrictions reload' or restart
	#   the server, and they will be added here automatically.
	#
	# Example (after mod has run and discovered ranks 'member' and 'admin'):
	# [restrictions.member]
	#   restriction_sets = []
	#
	# [restrictions.admin]
	#   restriction_sets = []

""";
                Files.createDirectories(configPath.getParent());
                Files.writeString(configPath, defaultConfigContent);
                LOGGER.info("Generated new default config file with detailed examples at: " + configPath);
                
                // Load the newly generated config
                this.config.loadConfig();
            } else {
                LOGGER.info("Existing config file found at: " + configPath);
                // If it exists, it should have been loaded by setup(), so no need to reload here unless specifically required.
            }

            // Initialize ranks now that config is settled (either loaded or newly generated and loaded)
            initializeRanks();

        } catch (Exception e) {
            LOGGER.error("Error during server starting configuration: " + e.getMessage(), e);
        }
    }
    
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        // Try again after server is fully started in case FTBRanks wasn't ready during ServerStarting
        if (!hasInitializedRanks) {
            LOGGER.info("Trying to initialize ranks again after server fully started...");
            initializeRanks();
        }
    }
    
    /**
     * Initialize ranks from FTBRanks API
     */
    private void initializeRanks() {
        if (FTBRanksHelper.isApiAvailable()) {
            LOGGER.info("FTBRanks API detected, loading ranks");
            Collection<Object> ranks = FTBRanksHelper.getAllRanks();
            
            if (ranks.isEmpty()) {
                LOGGER.warn("No ranks found from FTBRanks API");
            } else {
                LOGGER.info("Found " + ranks.size() + " ranks from FTBRanks API");
                for (Object rank : ranks) {
                    String rankId = FTBRanksHelper.getRankName(rank);
                    String displayName = FTBRanksHelper.getRankDisplayName(rank);
                    LOGGER.info("  - Rank: " + rankId + " (" + displayName + ")");
                }
                
                config.processRanks();
                hasInitializedRanks = true;
            }
        } else {
            LOGGER.warn("FTBRanks API not detected, rank restrictions may not be available");
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        try {
            LOGGER.info("Registering RankRestrictions commands");
            
            // Direct method call without reflection where possible
            Object dispatcher = event.getDispatcher();
            
            // Register commands
            RankRestrictionsCommands.register(dispatcher);
            
            LOGGER.info("FTBRanks Rank Restrictions commands registered successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to register commands: " + e.getMessage(), e);
        }
    }
}
