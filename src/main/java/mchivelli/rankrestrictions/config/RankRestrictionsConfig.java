package mchivelli.rankrestrictions.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import mchivelli.rankrestrictions.RankRestrictions;
import mchivelli.rankrestrictions.util.FTBRanksHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.loading.FMLPaths;
import com.electronwill.nightconfig.core.Config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Collection;

public class RankRestrictionsConfig {
    
    private final Path configDir;
    private final Path configFile;
    private final Map<String, RankRestrictionData> rankRestrictions = new HashMap<>();
    private String defaultRestrictionMessage = "&cYou are not allowed to use %item% with your current rank!";
    private boolean configLoaded = false;
    
    public RankRestrictionsConfig() {
        configDir = FMLPaths.CONFIGDIR.get().resolve("rankrestrictions");
        configFile = configDir.resolve("restrictions.toml");
        
        try {
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
                RankRestrictions.LOGGER.info("Created config directory: " + configDir);
            }
        } catch (Exception e) {
            RankRestrictions.LOGGER.error("Failed to create config directory: " + e.getMessage(), e);
        }
    }
    
    public boolean isConfigLoaded() {
        return configLoaded;
    }
    
    /**
     * Loads the configuration from file
     */
    public void loadConfig() {
        boolean isNewConfig = !Files.exists(configFile);
        
        // If config doesn't exist, create an empty config file but don't populate it yet
        // This prevents wiping out configurations when joining a world
        if (isNewConfig) {
            try {
                Files.createDirectories(configFile.getParent());
                // Only create the directory, don't create the file yet
                RankRestrictions.LOGGER.info("Config file doesn't exist, directories created");
            } catch (Exception e) {
                RankRestrictions.LOGGER.error("Failed to create config directory: " + e.getMessage(), e);
            }
            
            // We'll create the file with examples later in saveConfig if needed
            // This prevents creating empty files that could cause data loss
        }
        
        // If the file still doesn't exist, there's nothing to load
        if (!Files.exists(configFile)) {
            RankRestrictions.LOGGER.info("No config file to load yet - will be created when saving");
            return;
        }
        
        try {
            CommentedFileConfig config = CommentedFileConfig.builder(configFile)
                .sync()
                .autosave()
                .preserveInsertionOrder()
                .build();
            
            // Load the existing config
            config.load();
            
            // Load the default message
            if (config.contains("messages.default_restriction")) {
                defaultRestrictionMessage = config.get("messages.default_restriction");
            }
            
            // Don't clear existing restrictions if we're reloading - merge instead
            // This prevents data loss when the config is reloaded
            
            // Load rank restrictions
            if (config.contains("restrictions")) {
                // Get the restrictions table
                Object rawRestrictionsTable = config.get("restrictions");
                if (rawRestrictionsTable instanceof Config) {
                    Config restrictionsTable = (Config) rawRestrictionsTable;

                    for (com.electronwill.nightconfig.core.Config.Entry rankEntry : restrictionsTable.entrySet()) {
                        String rankId = rankEntry.getKey();
                        Object rawRankConfig = rankEntry.getValue();

                        if (rawRankConfig instanceof Config) {
                            Config rankConfig = (Config) rawRankConfig;
                            
                            RankRestrictionData data = rankRestrictions.computeIfAbsent(rankId, k -> new RankRestrictionData(rankId));
                            data.clearRestrictions(); // Clear existing sets before loading new ones for this rank

                            if (rankConfig.contains("restriction_sets")) {
                                Object rawSets = rankConfig.get("restriction_sets");
                                if (rawSets instanceof List) {
                                    List<?> setList = (List<?>) rawSets;
                                    for (Object setObj : setList) {
                                        if (setObj instanceof Config) {
                                            Config setTable = (Config) setObj;
                                            List<String> items = setTable.getOptional("items")
                                                .filter(List.class::isInstance)
                                                .map(l -> ((List<?>)l).stream().map(String::valueOf).collect(java.util.stream.Collectors.toList()))
                                                .orElseGet(ArrayList::new);
                                            List<String> blocks = setTable.getOptional("blocks")
                                                .filter(List.class::isInstance)
                                                .map(l -> ((List<?>)l).stream().map(String::valueOf).collect(java.util.stream.Collectors.toList()))
                                                .orElseGet(ArrayList::new);
                                            String message = setTable.getOptional("message").map(String::valueOf).orElse(null);
                                            if (!items.isEmpty() || !blocks.isEmpty()) {
                                                data.addRestrictionSet(new RestrictionSet(items, blocks, message));
                                            }
                                        }
                                    }
                                    RankRestrictions.LOGGER.debug("Loaded " + data.getRestrictionSets().size() + " restriction sets for rank " + rankId);
                                }
                            } else {
                                // LEGACY SUPPORT: Load old format if new 'restriction_sets' is not present
                                List<String> legacyRestrictions = rankConfig.getOptional("restrictions")
                                        .filter(List.class::isInstance)
                                        .map(l -> ((List<?>)l).stream().map(String::valueOf).collect(java.util.stream.Collectors.toList()))
                                        .orElseGet(() -> rankConfig.getOptional("items")
                                            .filter(List.class::isInstance)
                                            .map(l2 -> ((List<?>)l2).stream().map(String::valueOf).collect(java.util.stream.Collectors.toList()))
                                            .orElseGet(ArrayList::new));
                                String legacyMessage = rankConfig.getOptional("messageForRestrictionSet").map(String::valueOf).orElse(null);
                                if (!legacyRestrictions.isEmpty()) {
                                    data.addRestrictionSet(new RestrictionSet(legacyRestrictions, new ArrayList<>(), legacyMessage));
                                    RankRestrictions.LOGGER.debug("Loaded legacy restrictions as a single set for rank " + rankId);
                                }
                            }
                            rankRestrictions.put(rankId, data);
                        } else {
                            RankRestrictions.LOGGER.warn("Skipping non-config entry for rank: " + rankId + " under restrictions. Value type: " + (rawRankConfig != null ? rawRankConfig.getClass().getName() : "null"));
                        }
                    }
                } else if (rawRestrictionsTable != null) {
                    RankRestrictions.LOGGER.warn("The 'restrictions' entry in config is not a table. Found type: " + rawRestrictionsTable.getClass().getName() + ". Expected com.electronwill.nightconfig.core.Config.");
                } else {
                    RankRestrictions.LOGGER.info("No 'restrictions' table found in config, or it is empty.");
                }
            }
            
            configLoaded = true;
            RankRestrictions.LOGGER.info("Loaded config with " + rankRestrictions.size() + " ranks");
            
            // Log all loaded restrictions for debugging
            for (Map.Entry<String, RankRestrictionData> entry : rankRestrictions.entrySet()) {
                RankRestrictionData rrd = entry.getValue();
                if (!rrd.isEmpty()) {
                    RankRestrictions.LOGGER.info("Rank '" + entry.getKey() + "' has " + rrd.getRestrictionSets().size() + " restriction set(s).");
                    for (RestrictionSet rs : rrd.getRestrictionSets()) {
                        RankRestrictions.LOGGER.debug("  - Set with " + rs.getItems().size() + " items. Message: '" + (rs.getMessage() != null ? rs.getMessage() : "<default>") + "'. Items: " + String.join(", ", rs.getItems()));
                    }
                }
            }
            
        } catch (Exception e) {
            RankRestrictions.LOGGER.error("Failed to load config: " + e.getMessage(), e);
        }
    }
    
    /**
     * Saves the configuration to file
     */
    public void saveConfig() {
        try {
            Files.createDirectories(configFile.getParent());
            boolean isFirstSave = !Files.exists(configFile) || Files.size(configFile) == 0;

            if (isFirstSave) {
                RankRestrictions.LOGGER.info("Creating new config file with examples...");
                StringBuilder headerContent = new StringBuilder();
                headerContent.append("# FTBRanks Rank Restrictions Configuration File\n");
                headerContent.append("#\n");
                headerContent.append("# This mod allows you to restrict both ITEMS and BLOCKS based on player ranks.\n");
                headerContent.append("# Items are restricted from inventory, pickup, and usage.\n");
                headerContent.append("# Blocks are restricted from interaction (only blocks with block entities like furnaces, machines).\n");
                headerContent.append("#\n");
                headerContent.append("# IMPORTANT: FORMATTING GUIDE\n");
                headerContent.append("# - Each restriction MUST be enclosed in double quotes: \"minecraft:diamond_sword\"\n");
                headerContent.append("# - Multiple restrictions are placed in an array with square brackets: [ ]\n");
                headerContent.append("# - Each restriction entry must be separated by a comma except the last one\n");
                headerContent.append("# - Tags start with #: \"#minecraft:beds\"\n");
                headerContent.append("#\n");
                headerContent.append("# Pattern Types for both 'items' and 'blocks' lists:\n");
                headerContent.append("#   1. Exact ID: \"minecraft:diamond_sword\" or \"tconstruct:smeltery_controller\"\n");
                headerContent.append("#   2. Mod Wildcard: \"mod_id:*\" (restricts all items/blocks from 'mod_id')\n");
                headerContent.append("#   3. Tag: \"#namespace:tag_path\" (e.g., \"#minecraft:beds\", \"#forge:chests\")\n");
                headerContent.append("#\n");
                headerContent.append("# Block Restrictions (Right-Click Prevention):\n");
                headerContent.append("#   - Only affects blocks with block entities that can be right-clicked (interactive blocks)\n");
                headerContent.append("#   - Examples: furnaces, chests, crafting tables, modded machines, workbenches\n");
                headerContent.append("#   - Prevents right-clicking to open GUIs, access inventories, or interact with the block\n");
                headerContent.append("#   - Does NOT affect decorative blocks or blocks without block entities\n");
                headerContent.append("#   - Perfect for restricting access to modded machines like Tinkers' forges, Mekanism machines, etc.\n");
                headerContent.append("#\n");
                headerContent.append("# Common Block Entity Examples:\n");
                headerContent.append("#   - Restrict all Tinkers' Construct machines: \"tconstruct:*\"\n");
                headerContent.append("#   - Restrict all Mekanism machines: \"mekanism:*\"\n");
                headerContent.append("#   - Restrict all Thermal machines: \"thermal:*\"\n");
                headerContent.append("#   - Restrict vanilla furnaces: \"minecraft:furnace\"\n");
                headerContent.append("#   - Restrict all chests (vanilla + modded): \"#forge:chests\"\n");
                headerContent.append("#   - Restrict crafting tables: \"minecraft:crafting_table\"\n");
                headerContent.append("#   - Restrict anvils: \"#minecraft:anvil\"\n");
                headerContent.append("#\n");
                headerContent.append("# COMPLETE EXAMPLES (commented out):\n\n");
                headerContent.append("# Example configuration with the new 'restriction_sets' format:\n");
                headerContent.append("# [restrictions.example_rank] # This is the rank ID from FTB Ranks\n");
                headerContent.append("#   # This rank has multiple restriction sets for items and blocks.\n");
                headerContent.append("#   [[restriction_sets]] # First restriction set for 'example_rank'\n");
                headerContent.append("#     items = [\n");
                headerContent.append("#       \"minecraft:diamond_sword\",           # Regular item ID\n");
                headerContent.append("#       \"minecraft:netherite_pickaxe\",       # Another item\n");
                headerContent.append("#       \"#minecraft:beds\"                  # Minecraft tag (all bed variants)\n");
                headerContent.append("#     ]\n");
                headerContent.append("#     blocks = [\n");
                headerContent.append("#       \"minecraft:furnace\",               # Regular block ID\n");
                headerContent.append("#       \"tconstruct:smeltery_controller\",  # Tinkers' Construct smeltery\n");
                headerContent.append("#       \"tconstruct:*\",                   # All Tinkers' Construct blocks\n");
                headerContent.append("#       \"#minecraft:anvil\"                # Block tag for all anvil variants\n");
                headerContent.append("#     ]\n");
                headerContent.append("#     message = \"&cYou cannot use these high-tier tools and machines!\" # Custom message for this set\n");
                headerContent.append("#\n");
                headerContent.append("#   [[restriction_sets]] # Second restriction set for 'example_rank'\n");
                headerContent.append("#     items = [\"minecraft:rotten_flesh\", \"minecraft:poisonous_potato\"]\n");
                headerContent.append("#     blocks = [\"mekanism:digital_miner\", \"thermal:*\"] # Restrict specific modded machines\n");
                headerContent.append("#     # No message here, so it will use the default_restriction message from 'messages' table or a global default.\n");
                headerContent.append("#\n");
                headerContent.append("# [restrictions.another_rank]\n");
                headerContent.append("#   [[restriction_sets]]\n");
                headerContent.append("#     items = [\"modid:*\"] # Restrict all items from 'modid'\n");
                headerContent.append("#     blocks = [\"modid:*\"] # Restrict all blocks from 'modid'\n");
                headerContent.append("#     message = \"&eItems and blocks from this mod are forbidden for your rank.\"\n");
                headerContent.append("#\n");
                headerContent.append("# [restrictions.guest] # Example for a guest rank\n");
                headerContent.append("#   [[restriction_sets]]\n");
                headerContent.append("#     items = [ \"#forge:chests\", \"minecraft:shulker_box\" ]\n");
                headerContent.append("#     blocks = [ \"minecraft:chest\", \"minecraft:ender_chest\", \"#forge:chests\" ]\n");
                headerContent.append("#     message = \"&6Guests cannot use storage items or blocks like %item%.\"\n");
                headerContent.append("#\n");
                headerContent.append("# [messages]\n");
                headerContent.append("# default_restriction = \"&cYou are not allowed to use %item% with your current rank!\"\n");
                Files.writeString(configFile, headerContent.toString());
            }

            CommentedFileConfig config = CommentedFileConfig.builder(configFile)
                .sync()
                .autosave()
                .preserveInsertionOrder()
                .build();
            config.load();

            // Save default message
            config.set("messages.default_restriction", defaultRestrictionMessage);

            // Prepare the restrictions table
            Config restrictionsTable = config.getOptional("restrictions").map(o -> (Config)o).orElseGet(() -> Config.inMemory());
            
            // Clear existing rank configurations in the table to avoid orphaned entries if ranks are removed programmatically
            // (though this mod doesn't currently support removing ranks via commands that would reflect here)
            // For safety, we only update existing or add new ones.
            // If a rank is removed from FTB Ranks, its config will remain here unless manually deleted.

            for (Map.Entry<String, RankRestrictionData> entry : this.rankRestrictions.entrySet()) {
                String rankId = entry.getKey();
                RankRestrictionData data = entry.getValue();
                List<Config> setsToSave = new ArrayList<>();

                for (RestrictionSet set : data.getRestrictionSets()) {
                    Config setTable = Config.inMemory();
                    setTable.set("items", set.getItems());
                    setTable.set("blocks", set.getBlocks());
                    if (set.getMessage() != null && !set.getMessage().isEmpty()) {
                        setTable.set("message", set.getMessage());
                    }
                    setsToSave.add(setTable);
                }
                // Set the array of tables for the rank
                restrictionsTable.set(rankId + ".restriction_sets", setsToSave);
            }
            config.set("restrictions", restrictionsTable);
            
            config.save();
            RankRestrictions.LOGGER.info("Saved config with " + this.rankRestrictions.size() + " ranks.");

        } catch (Exception e) {
            RankRestrictions.LOGGER.error("Failed to save config: " + e.getMessage(), e);
        }
    }

    public String getDefaultRestrictionMessage() {
        return defaultRestrictionMessage;
    }

    public void setDefaultRestrictionMessage(String message) {
        this.defaultRestrictionMessage = message;
        saveConfig(); // Save config when default message changes
    }

    public Map<String, RankRestrictionData> getRankRestrictions() {
        return Collections.unmodifiableMap(rankRestrictions);
    }

    /**
     * Processes ranks from FTBRanks, adding new ones to the configuration and saving if changes occur.
     */
    public void processRanks() {
        RankRestrictions.LOGGER.info("Processing ranks from FTBRanks...");
        Collection<?> ranks = FTBRanksHelper.getAllRanks();
        if (ranks == null) {
            RankRestrictions.LOGGER.warn("Could not retrieve ranks from FTBRanks.");
            return;
        }

        boolean configWasModified = false;
        for (Object rankObj : ranks) {
            String rankId = FTBRanksHelper.getRankName(rankObj);
            if (rankId != null && !rankId.isEmpty()) {
                if (!rankRestrictions.containsKey(rankId)) {
                    RankRestrictions.LOGGER.info("Discovered new rank from FTBRanks: " + rankId + ". Adding to config with default (empty) restrictions.");
                    rankRestrictions.put(rankId, new RankRestrictionData(rankId)); // Add with no restrictions initially
                    configWasModified = true;
                }
            } else {
                RankRestrictions.LOGGER.warn("Found a rank from FTBRanks with a null or empty ID. Skipping.");
            }
        }

        if (configWasModified) {
            RankRestrictions.LOGGER.info("New ranks were added from FTBRanks. Saving configuration...");
            saveConfig();
        } else {
            RankRestrictions.LOGGER.info("No new ranks discovered from FTBRanks that were not already in the config.");
        }
    }

    /**
     * Checks if a specific item is restricted for a given rank.
     * @param rankId The ID of the rank to check.
     * @param itemLocation The ResourceLocation of the item to check.
     * @return True if the item is restricted for the rank, false otherwise.
     */
    public boolean isItemRestrictedForRank(String rankId, ResourceLocation itemLocation) {
        RankRestrictionData data = rankRestrictions.get(rankId);
        if (data != null) {
            Item itemToCheck = ForgeRegistries.ITEMS.getValue(itemLocation);
            // Ensure itemToCheck is not null, though isRestricted should handle it.
            return data.isRestricted(itemLocation, itemToCheck); 
        }
        return false; // Default to not restricted if rank or data not found
    }
    
    /**
     * Checks if a specific block is restricted for a given rank.
     * @param rankId The ID of the rank to check.
     * @param blockLocation The ResourceLocation of the block to check.
     * @return True if the block is restricted for the rank, false otherwise.
     */
    public boolean isBlockRestrictedForRank(String rankId, ResourceLocation blockLocation) {
        RankRestrictionData data = rankRestrictions.get(rankId);
        if (data != null) {
            Block blockToCheck = ForgeRegistries.BLOCKS.getValue(blockLocation);
            return data.isBlockRestricted(blockLocation, blockToCheck); 
        }
        return false; // Default to not restricted if rank or data not found
    }

    // Gets the specific restriction message for an item and rank
    public String getRestrictionMessage(ResourceLocation itemLocation, String rankId) {
        RankRestrictionData data = rankRestrictions.get(rankId);
        if (data != null) {
            // Iterate through restriction sets to find the one that restricts this item
            Item itemToCheck = ForgeRegistries.ITEMS.getValue(itemLocation);
            for (RestrictionSet set : data.getRestrictionSets()) {
                if (set.isRestricted(itemLocation, itemToCheck)) { // Check if the item is in this specific set
                    String message = set.getMessage();
                    if (message != null && !message.isEmpty()) {
                        return message.replace("%item%", itemLocation.toString());
                    }
                    break; // Found the restricting set
                }
            }
        }
        // If no specific message, return the default message
        return defaultRestrictionMessage.replace("%item%", itemLocation.toString());
    }
    
    // Gets the specific restriction message for a block and rank
    public String getBlockRestrictionMessage(ResourceLocation blockLocation, String rankId) {
        RankRestrictionData data = rankRestrictions.get(rankId);
        if (data != null) {
            // Iterate through restriction sets to find the one that restricts this block
            Block blockToCheck = ForgeRegistries.BLOCKS.getValue(blockLocation);
            for (RestrictionSet set : data.getRestrictionSets()) {
                if (set.isBlockRestricted(blockLocation, blockToCheck)) { // Check if the block is in this specific set
                    String message = set.getMessage();
                    if (message != null && !message.isEmpty()) {
                        return message.replace("%item%", blockLocation.toString());
                    }
                    break; // Found the restricting set
                }
            }
        }
        // If no specific message, return the default message
        return defaultRestrictionMessage.replace("%item%", blockLocation.toString());
    }
    
    // Method to add or update a restriction for a specific rank and set index
    public void addOrUpdateRestriction(String rankId, int setIndex, List<String> items, String message) {
        RankRestrictionData data = rankRestrictions.computeIfAbsent(rankId, k -> new RankRestrictionData(rankId));
        List<RestrictionSet> sets = data.getRestrictionSets();
        if (setIndex < 0 || setIndex > sets.size()) { // Allow adding as a new set if index is sets.size()
            RankRestrictions.LOGGER.warn("Invalid set index " + setIndex + " for rank " + rankId + ". Max index is " + sets.size());
            if (setIndex == sets.size()) { // If trying to add a new set at the end
                 // Handled by ensuring list size below
            } else {
                return;
            }
        }

        RestrictionSet setToUpdate;
        if (setIndex == sets.size()) {
            setToUpdate = new RestrictionSet(new ArrayList<>(), message);
            data.addRestrictionSet(setToUpdate); // Add new set
        } else {
            setToUpdate = sets.get(setIndex);
        }
        
        setToUpdate.getItems().clear(); // Clear existing items if updating
        setToUpdate.getItems().addAll(items);
        setToUpdate.setMessage(message); // Update message

        RankRestrictions.LOGGER.info("Updated restriction set " + setIndex + " for rank " + rankId);
        saveConfig();
    }

    // Method to remove a restriction set from a rank
    public void removeRestrictionFromRank(String rankId, int setIndex) {
        RankRestrictionData data = rankRestrictions.get(rankId);
        if (data != null) {
            List<RestrictionSet> sets = data.getRestrictionSets();
            if (setIndex >= 0 && setIndex < sets.size()) {
                sets.remove(setIndex);
                RankRestrictions.LOGGER.info("Removed restriction set " + setIndex + " from rank " + rankId);
                if (sets.isEmpty() && !rankId.equals("default")) { // Example: don't remove rank if it becomes empty unless it's not a special rank
                    // rankRestrictions.remove(rankId); // Optionally remove rank if all sets are gone
                    // RankRestrictions.LOGGER.info("Removed rank " + rankId + " as it has no more restriction sets.");
                }
                saveConfig();
            } else {
                RankRestrictions.LOGGER.warn("Invalid set index " + setIndex + " for rank " + rankId + " during removal.");
            }
        } else {
            RankRestrictions.LOGGER.warn("Rank " + rankId + " not found for restriction removal.");
        }
    }
}
