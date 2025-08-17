package mchivelli.rankrestrictions.config;

import mchivelli.rankrestrictions.RankRestrictions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class RestrictionSet {
    private String message; // Can be null if this set should use the rank's default or global default message
    private final List<String> rawItemPatterns; // Keep for saving/display if needed, or remove if not
    private final List<String> rawBlockPatterns; // Keep for saving/display if needed

    // Item restrictions
    private final Set<ResourceLocation> restrictedExactItems = new HashSet<>();
    private final Set<String> restrictedItemModIds = new HashSet<>(); // Store just the mod ID, e.g., "mekanism"
    private final List<TagKey<Item>> restrictedItemTags = new ArrayList<>();
    
    // Block restrictions
    private final Set<ResourceLocation> restrictedExactBlocks = new HashSet<>();
    private final Set<String> restrictedBlockModIds = new HashSet<>(); // Store just the mod ID, e.g., "mekanism"
    private final List<TagKey<Block>> restrictedBlockTags = new ArrayList<>();

    public RestrictionSet(List<String> rawItemPatterns, String message) {
        this.rawItemPatterns = Objects.requireNonNull(rawItemPatterns, "Raw item patterns list cannot be null");
        this.rawBlockPatterns = new ArrayList<>(); // Initialize empty for now
        this.message = message; // Message can be null
        preprocessItemRestrictions(rawItemPatterns);
    }
    
    public RestrictionSet(List<String> rawItemPatterns, List<String> rawBlockPatterns, String message) {
        this.rawItemPatterns = Objects.requireNonNull(rawItemPatterns, "Raw item patterns list cannot be null");
        this.rawBlockPatterns = Objects.requireNonNull(rawBlockPatterns, "Raw block patterns list cannot be null");
        this.message = message; // Message can be null
        preprocessItemRestrictions(rawItemPatterns);
        preprocessBlockRestrictions(rawBlockPatterns);
    }

    private void preprocessItemRestrictions(List<String> patterns) {
        for (String patternGroup : patterns) {
            if (patternGroup == null) continue; // Skip null patterns
            String[] individualPatterns = patternGroup.split(","); // Handle comma-separated first
            for (String p : individualPatterns) {
                String trimmedPattern = p.trim();
                if (trimmedPattern.isEmpty()) continue;

                if (trimmedPattern.startsWith("#")) { // Tag
                    String tagName = trimmedPattern.substring(1);
                    ResourceLocation tagId = ResourceLocation.tryParse(tagName);
                    // If tag doesn't specify a namespace, default to 'minecraft' as per common convention for some tags.
                    // However, most modded tags (e.g. #forge:ingots) will be namespaced.
                    // ResourceLocation.tryParse handles this correctly if namespace is missing by returning null or throwing error depending on string.
                    // Let's ensure we handle cases like `#beds` (implicitly minecraft:beds)
                    if (tagId == null && !tagName.contains(":")) { // tryParse might return null for unnamespaced simple strings
                        tagId = ResourceLocation.tryParse("minecraft:" + tagName);
                    }

                    if (tagId != null) {
                        restrictedItemTags.add(TagKey.create(ForgeRegistries.ITEMS.getRegistryKey(), tagId));
                    } else {
                        RankRestrictions.LOGGER.warn("Invalid tag format or could not parse tag ID: '" + trimmedPattern + "'");
                    }
                } else if (trimmedPattern.endsWith(":*")) { // Mod wildcard
                    restrictedItemModIds.add(trimmedPattern.substring(0, trimmedPattern.length() - 2));
                } else { // Exact item ID
                    ResourceLocation itemRL = ResourceLocation.tryParse(trimmedPattern);
                    if (itemRL != null) {
                        restrictedExactItems.add(itemRL);
                    } else {
                        RankRestrictions.LOGGER.warn("Invalid item ID format: '" + trimmedPattern + "'");
                    }
                }
            }
        }
    }
    
    private void preprocessBlockRestrictions(List<String> patterns) {
        for (String patternGroup : patterns) {
            if (patternGroup == null) continue; // Skip null patterns
            String[] individualPatterns = patternGroup.split(","); // Handle comma-separated first
            for (String p : individualPatterns) {
                String trimmedPattern = p.trim();
                if (trimmedPattern.isEmpty()) continue;

                if (trimmedPattern.startsWith("#")) { // Tag
                    String tagName = trimmedPattern.substring(1);
                    ResourceLocation tagId = ResourceLocation.tryParse(tagName);
                    // If tag doesn't specify a namespace, default to 'minecraft' as per common convention for some tags.
                    if (tagId == null && !tagName.contains(":")) { // tryParse might return null for unnamespaced simple strings
                        tagId = ResourceLocation.tryParse("minecraft:" + tagName);
                    }

                    if (tagId != null) {
                        restrictedBlockTags.add(TagKey.create(ForgeRegistries.BLOCKS.getRegistryKey(), tagId));
                    } else {
                        RankRestrictions.LOGGER.warn("Invalid block tag format or could not parse tag ID: '" + trimmedPattern + "'");
                    }
                } else if (trimmedPattern.endsWith(":*")) { // Mod wildcard
                    restrictedBlockModIds.add(trimmedPattern.substring(0, trimmedPattern.length() - 2));
                } else { // Exact block ID
                    ResourceLocation blockRL = ResourceLocation.tryParse(trimmedPattern);
                    if (blockRL != null) {
                        restrictedExactBlocks.add(blockRL);
                    } else {
                        RankRestrictions.LOGGER.warn("Invalid block ID format: '" + trimmedPattern + "'");
                    }
                }
            }
        }
    }

    public List<String> getItems() {
        // Return the original raw patterns if they are needed for config saving or display.
        // If not, this method might be redundant or could be removed.
        return rawItemPatterns;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
    
    public List<String> getBlocks() {
        return rawBlockPatterns;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RestrictionSet that = (RestrictionSet) o;
        // Equality should be based on the effective restrictions and message.
        // Comparing preprocessed sets ensures semantic equality.
        return restrictedExactItems.equals(that.restrictedExactItems) &&
               restrictedItemModIds.equals(that.restrictedItemModIds) &&
               restrictedItemTags.equals(that.restrictedItemTags) &&
               restrictedExactBlocks.equals(that.restrictedExactBlocks) &&
               restrictedBlockModIds.equals(that.restrictedBlockModIds) &&
               restrictedBlockTags.equals(that.restrictedBlockTags) &&
               Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(restrictedExactItems, restrictedItemModIds, restrictedItemTags, 
                           restrictedExactBlocks, restrictedBlockModIds, restrictedBlockTags, message);
    }

    /**
     * Checks if the given item is restricted by this set using pre-processed patterns.
     * @param itemRL The ResourceLocation of the item to check.
     * @param itemToCheck The Item object itself.
     * @return True if the item is restricted by this set, false otherwise.
     */
    public boolean isRestricted(ResourceLocation itemRL, Item itemToCheck) {
        if (itemRL == null || itemToCheck == null) return false;

        // 1. Check exact item IDs (HashSet O(1) average)
        if (restrictedExactItems.contains(itemRL)) {
            return true;
        }

        // 2. Check mod wildcards (HashSet O(1) average for mod ID lookup)
        if (restrictedItemModIds.contains(itemRL.getNamespace())) {
            return true;
        }

        // 3. Check tags (List iteration, but item.getDefaultInstance().is(tagKey) is efficient)
        ItemStack itemStack = itemToCheck.getDefaultInstance(); // Get once
        for (TagKey<Item> tagKey : restrictedItemTags) {
            if (itemStack.is(tagKey)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Checks if the given block is restricted by this set using pre-processed patterns.
     * @param blockRL The ResourceLocation of the block to check.
     * @param blockToCheck The Block object itself.
     * @return True if the block is restricted by this set, false otherwise.
     */
    public boolean isBlockRestricted(ResourceLocation blockRL, Block blockToCheck) {
        if (blockRL == null || blockToCheck == null) return false;

        // 1. Check exact block IDs (HashSet O(1) average)
        if (restrictedExactBlocks.contains(blockRL)) {
            return true;
        }

        // 2. Check mod wildcards (HashSet O(1) average for mod ID lookup)
        if (restrictedBlockModIds.contains(blockRL.getNamespace())) {
            return true;
        }

        // 3. Check tags (List iteration, but block.defaultBlockState().is(tagKey) is efficient)
        BlockState blockState = blockToCheck.defaultBlockState(); // Get once
        for (TagKey<Block> tagKey : restrictedBlockTags) {
            if (blockState.is(tagKey)) {
                return true;
            }
        }
        return false;
    }
}
