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
#   This section contains restriction rules for different FTBRanks rank IDs.
#
#   [restrictions.your_rank_id_here]
#     Replace 'your_rank_id_here' with the actual ID of an FTBRank (e.g., member, vip, admin).
#     You can find rank IDs using FTBRanks commands in-game or by looking at FTBRanks config.
#
#     Defining Restriction Sets:
#       To define a set of restricted items (with an optional custom message) for a rank,
#       you use the following block structure. A rank can have multiple such blocks.
#
#       [[restrictions.your_rank_id_here.restriction_sets]]
#         message (Optional): "&cCustom message for this set. %%item%% is the item."
#         items: ["minecraft:item_one", "modid:item_two", "modid:*", "#forge:tags/example"]
#
#       - message: A custom message for this specific set. %%item%% is replaced by the item name.
#                  If omitted, the global 'default_restriction' message is used.
#       - items: A list of strings defining items to restrict.
#                Patterns can be:
#                  1. Exact Item ID: "minecraft:stone" or "modid:itemname"
#                  2. Mod Wildcard: "modid:*" (restricts all items from 'modid')
#                                   Example: "mekanism:*"
#                  3. Item Tag: "#namespace:tag_path" (restricts all items matching this tag)
#                               Example: "#forge:ores/diamond", "#minecraft:planks"
#                               (Ensure the tag exists and is loaded by Minecraft/Forge).
#
# Adding Restrictions to a New or Empty Rank:
#   If a rank (e.g., 'some_new_rank' or a rank newly added by FTBRanks) shows up as:
#     [restrictions.some_new_rank]
#       restriction_sets = [] # This means NO restrictions are currently defined.
#
#   To add restrictions, DELETE the `restriction_sets = []` line entirely and then
#   add one or more `[[restrictions.some_new_rank.restriction_sets]]` blocks as shown above.
#   For example:
#     [restrictions.some_new_rank]
#       [[restrictions.some_new_rank.restriction_sets]]
#         message = "&eNewly restricted items for %%item%%!"
#         items = ["minecraft:bedrock"]
#       # You could add another [[...]] block here for a second set of restrictions.

[messages]
	default_restriction = "&cYou are not allowed to use %%item%% with your current rank!"

[restrictions]

	# Example of a rank with pre-defined restrictions:
	[restrictions.member]
		# Members cannot use diamond swords or diamond pickaxes.
		[[restrictions.member.restriction_sets]]
			message = "&cMembers cannot use %%item%%!"
			items = ["minecraft:diamond_sword", "minecraft:diamond_pickaxe"]

	# Another example with a different set of items:
	[restrictions.vip]
		[[restrictions.vip.restriction_sets]]
			message = "&6VIPs cannot use %%item%%!"
			items = ["minecraft:command_block"]

	# Example with multiple types of restrictions (exact, mod wildcard, tag):
	[restrictions.baron]
		[[restrictions.baron.restriction_sets]]
			message = "&bBarons are too noble for %%item%%."
			items = ["minecraft:golden_apple", "examplemod:*", "#forge:ingots/gold"]
		
		# To add a second set of restrictions for Barons, you would add another block like this:
		# [[restrictions.baron.restriction_sets]]
		#   message = "&bBarons also cannot use netherite."
		#   items = ["minecraft:netherite_ingot", "minecraft:netherite_sword"]

	# Examples of ranks that are initially empty (as they might be when newly discovered from FTBRanks):
	# To add restrictions to 'count', DELETE the 'restriction_sets = []' line below
	# and add one or more '[[restrictions.count.restriction_sets]]' blocks.
	[restrictions.count]
		restriction_sets = [] # Currently no restrictions for 'count'. 

	[restrictions.knight]
		restriction_sets = [] # Currently no restrictions for 'knight'. See 'count' example for how to add them.

	[restrictions.admin]
		restriction_sets = [] # Admins usually have no restrictions. Add them like for 'count' if needed.
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
