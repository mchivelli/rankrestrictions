package mchivelli.rankrestrictions.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores restriction data for a specific rank, including
 * the list of restricted items/blocks and custom messages
 */
public class RankRestrictionData {
    private final String rankId;
    private final List<RestrictionSet> restrictionSets = new ArrayList<>();

    public RankRestrictionData(String rankId) {
        this.rankId = rankId;
    }

    public String getRankId() {
        return rankId;
    }

    public void addRestrictionSet(RestrictionSet set) {
        if (set != null) {
            this.restrictionSets.add(set);
        }
    }

    public void clearRestrictions() {
        this.restrictionSets.clear();
    }

    public List<RestrictionSet> getRestrictionSets() {
        return new ArrayList<>(restrictionSets); // Return a copy
    }

    /**
     * Checks if the given item is restricted by any of the RestrictionSets for this rank.
     * @param itemRL The ResourceLocation of the item to check.
     * @param itemToCheck The Item object itself.
     * @return The matching RestrictionSet if the item is restricted, null otherwise.
     */
    public RestrictionSet getMatchingRestrictionSet(ResourceLocation itemRL, Item itemToCheck) {
        if (itemToCheck == null) return null;

        String itemIdString = itemRL.toString();
        String modId = itemRL.getNamespace();

        for (RestrictionSet set : restrictionSets) {
            for (String restriction : set.getItems()) {
                String trimmedRestriction = restriction.trim();
                if (trimmedRestriction.isEmpty()) continue;

                // Handle comma-separated values within a single restriction string in the list
                if (trimmedRestriction.contains(",")) {
                    String[] parts = trimmedRestriction.split(",");
                    for (String part : parts) {
                        if (checkSingleRestriction(part.trim(), itemIdString, modId, itemToCheck, itemRL)) {
                            return set;
                        }
                    }
                } else {
                    if (checkSingleRestriction(trimmedRestriction, itemIdString, modId, itemToCheck, itemRL)) {
                        return set;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Helper method to check a single restriction pattern (item ID, mod wildcard, or tag).
     */
    private boolean checkSingleRestriction(String restriction, String itemIdString, String modId, Item itemToCheck, ResourceLocation itemResourceLocation) {
        if (restriction.isEmpty()) return false;

        // Direct item ID match
        if (restriction.equals(itemIdString)) {
            return true;
        }

        // Mod ID wildcard match (e.g., "modid:*")
        if (restriction.equals(modId + ":*")) {
            return true;
        }

        // Tag match (e.g., "#forge:tools")
        if (restriction.startsWith("#")) {
            String tagName = restriction.substring(1);
            ResourceLocation tagId;
            try {
                if (tagName.contains(":")) {
                    tagId = new ResourceLocation(tagName);
                } else {
                    tagId = new ResourceLocation("minecraft", tagName); // Default to minecraft namespace
                }
                TagKey<Item> tagKey = TagKey.create(ForgeRegistries.ITEMS.getRegistryKey(), tagId);
                if (itemToCheck.getDefaultInstance().is(tagKey)) {
                    return true;
                }
            } catch (Exception e) {
                mchivelli.rankrestrictions.RankRestrictions.LOGGER.warn("Invalid tag format in restrictions: " + restriction + " for rank " + rankId, e);
            }
        }
        return false;
    }

    /**
     * Convenience method to quickly check if an item is restricted without needing the specific set.
     */
    public boolean isRestricted(ResourceLocation itemRL, Item itemToCheck) {
        return getMatchingRestrictionSet(itemRL, itemToCheck) != null;
    }

    public boolean isEmpty() {
        return restrictionSets.isEmpty();
    }

    @Override
    public String toString() {
        return "RankRestrictionData{"
                + "rankId='" + rankId + '\''
                + ", restrictionSets=" + restrictionSets.size() + " sets"
                + '}';
    }
}
