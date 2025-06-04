package mchivelli.rankrestrictions.events;

import mchivelli.rankrestrictions.RankRestrictions;
import mchivelli.rankrestrictions.util.FTBRanksHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;

public class ItemRestrictionEvents {

    // Note: Ensure this event is registered on the correct bus if it's a Forge event vs. FML event.
    // EntityItemPickupEvent is a Forge event, typically MinecraftForge.EVENT_BUS.
    @SubscribeEvent
    public void onPlayerAttemptPickupItem(net.minecraftforge.event.entity.player.EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;

        ServerPlayer player = (ServerPlayer) event.getEntity();
        ItemStack itemStack = event.getItem().getItem();

        if (player.isCreative() || itemStack.isEmpty()) {
            return;
        }

        if (!RankRestrictions.getInstance().getConfig().isConfigLoaded() || !FTBRanksHelper.isApiAvailable()) {
            return;
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(itemStack.getItem());
        if (itemId == null) {
            return;
        }

        Collection<Object> playerRanks = FTBRanksHelper.getPlayerRanks(player);
        if (playerRanks.isEmpty()) {
            return; 
        }

        for (Object rankObj : playerRanks) {
            String rankId = FTBRanksHelper.getRankName(rankObj);
            if (rankId == null || rankId.isEmpty()) continue;

            if (RankRestrictions.getInstance().getConfig().isItemRestrictedForRank(rankId, itemId)) {
                event.setCanceled(true); 

                String itemName = itemStack.getHoverName().getString();
                String message = RankRestrictions.getInstance().getConfig().getRestrictionMessage(itemId, rankId)
                        .replace("%item%", itemName);
                player.sendSystemMessage(Component.literal(formatColorCodes(message)));
                
                RankRestrictions.LOGGER.info("Prevented player " + player.getName().getString() + 
                                          " (rank " + rankId + ") from picking up restricted item " + itemName);
                break; 
            }
        }
    }

    @SubscribeEvent
    public void onPlayerRightClickItem(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;

        ServerPlayer player = (ServerPlayer) event.getEntity();
        ItemStack itemStack = event.getItemStack();

        if (player.isCreative() || itemStack.isEmpty()) {
            return;
        }

        if (!RankRestrictions.getInstance().getConfig().isConfigLoaded() || !FTBRanksHelper.isApiAvailable()) {
            return;
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(itemStack.getItem());
        if (itemId == null) {
            return;
        }

        Collection<Object> playerRanks = FTBRanksHelper.getPlayerRanks(player);
        if (playerRanks.isEmpty()) {
            return;
        }

        for (Object rankObj : playerRanks) {
            String rankId = FTBRanksHelper.getRankName(rankObj);
            if (rankId == null || rankId.isEmpty()) continue;

            if (RankRestrictions.getInstance().getConfig().isItemRestrictedForRank(rankId, itemId)) {
                event.setCanceled(true);
                // Consider if item should be removed here, or rely on tick/equip checks.
                // For now, just prevent usage and notify.
                String itemName = itemStack.getHoverName().getString();
                String message = RankRestrictions.getInstance().getConfig().getRestrictionMessage(itemId, rankId)
                        .replace("%item%", itemName);
                player.sendSystemMessage(Component.literal(formatColorCodes(message)));
                
                RankRestrictions.LOGGER.info("Prevented player " + player.getName().getString() + 
                                          " (rank " + rankId + ") from using restricted item " + itemName);
                break; 
            }
        }
    }


    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // Only process on server side and once every 30 seconds (every 600 ticks)
        if (event.phase == TickEvent.Phase.END && 
            event.player instanceof ServerPlayer serverPlayer && 
            event.player.tickCount % 600 == 0) { // Changed from % 20 to % 600

            if (!RankRestrictions.getInstance().getConfig().isConfigLoaded() || !FTBRanksHelper.isApiAvailable()) {
                //RankRestrictions.LOGGER.trace("Config not loaded or FTB Ranks API not available. Skipping tick check for player {}", serverPlayer.getName().getString());
                return;
            }

            //RankRestrictions.LOGGER.trace("Performing periodic inventory check for player: {}", serverPlayer.getName().getString());
            checkPlayerInventory(serverPlayer);
        }
    }
    
    @SubscribeEvent
    public void onEquipmentChange(LivingEquipmentChangeEvent event) {
        // Only process when a player equips an item
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            ItemStack newStack = event.getTo();
            
            if (!newStack.isEmpty()) {
                checkEquippedItem(serverPlayer, event.getSlot(), newStack);
            }
        }
    }
    
    private void checkPlayerInventory(ServerPlayer player) {
        // Skip players in creative mode
        if (player.isCreative()) {
            return;
        }
        
        // Skip if config not loaded or FTBRanks not available
        if (!RankRestrictions.getInstance().getConfig().isConfigLoaded() || !FTBRanksHelper.isApiAvailable()) {
            return;
        }
        
        // Get all player ranks from FTBRanks using reflection
        Collection<Object> playerRanks = FTBRanksHelper.getPlayerRanks(player);
        RankRestrictions.LOGGER.trace("Checking inventory for player: " + player.getName().getString() + " with FTBRanks: " + playerRanks.toString());
        if (playerRanks.isEmpty()) {
            RankRestrictions.LOGGER.trace("Player " + player.getName().getString() + " has no ranks. Skipping inventory check.");
            return; // Player has no ranks, nothing to check
        }
        
        // Get player inventory
        Inventory inventory = player.getInventory();
        boolean itemsRemoved = false;
        
        // Check each inventory slot - we need to make a copy of stacks to avoid concurrent modification
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            
            if (stack.isEmpty()) {
                continue;
            }
            
            // Get item ID - in 1.20.1 getRegistryName is deprecated, use registry directly
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (itemId == null) {
                continue;
            }
            
            // Check if this item is restricted for any of the player's ranks
            for (Object rankObj : playerRanks) {
                String rankId = FTBRanksHelper.getRankName(rankObj);
                if (rankId.isEmpty()) {
                    RankRestrictions.LOGGER.trace("Empty rankId found for player " + player.getName().getString() + ", skipping this rank object.");
                    continue;
                }
                RankRestrictions.LOGGER.trace("Checking item " + itemId.toString() + " for rank " + rankId + " for player " + player.getName().getString());
                boolean isRestricted = RankRestrictions.getInstance().getConfig().isItemRestrictedForRank(rankId, itemId);
                RankRestrictions.LOGGER.trace("Item " + itemId.toString() + " for rank " + rankId + ": isRestricted = " + isRestricted);
                
                if (isRestricted) {
                    // Create a copy of the stack before removing it
                    ItemStack droppedStack = stack.copy();
                    
                    // Remove the item from inventory - using the Minecraft method that actually works
                    inventory.setItem(i, ItemStack.EMPTY);
                    
                    // Drop the item at the player's feet
                    player.drop(droppedStack, true, false); // drop, isAddToInventory=true, bypassCooldown=false
                    
                    // Send message to player
                    String itemName = droppedStack.getHoverName().getString();
                    String message = RankRestrictions.getInstance().getConfig().getRestrictionMessage(itemId, rankId)
                            .replace("%item%", itemName);
                    
                    // In 1.20.1, we need to use the component directly
                    player.sendSystemMessage(Component.literal(formatColorCodes(message)));
                    
                    RankRestrictions.LOGGER.info("Dropped restricted item " + itemName + " from player " + player.getName().getString() + 
                                               " with rank " + rankId);
                    
                    itemsRemoved = true;
                    break;
                }
            }
        }
        
        // Sync the inventory to the client if items were removed
        if (itemsRemoved) {
            player.inventoryMenu.broadcastChanges();
        }
    }
    
    private void checkEquippedItem(ServerPlayer player, EquipmentSlot slot, ItemStack stack) {
        // Skip players in creative mode
        if (player.isCreative()) {
            return;
        }
        
        if (!RankRestrictions.getInstance().getConfig().isConfigLoaded() || !FTBRanksHelper.isApiAvailable()) {
            return;
        }
        
        // Get all player ranks from FTBRanks using reflection
        Collection<Object> playerRanks = FTBRanksHelper.getPlayerRanks(player);
        RankRestrictions.LOGGER.trace("Checking equipped item for player: " + player.getName().getString() + " with FTBRanks: " + playerRanks.toString());
        if (playerRanks.isEmpty()) {
            RankRestrictions.LOGGER.trace("Player " + player.getName().getString() + " has no ranks. Skipping equipped item check.");
            return; // Player has no ranks, nothing to check
        }
        
        // Get item ID - in 1.20.1 getRegistryName is deprecated, use registry directly
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) {
            return;
        }
        
        // Check if this item is restricted for any of the player's ranks
        for (Object rankObj : playerRanks) {
            String rankId = FTBRanksHelper.getRankName(rankObj);
            if (rankId.isEmpty()) {
                RankRestrictions.LOGGER.trace("Empty rankId found for player " + player.getName().getString() + ", skipping this rank object during equipment check.");
                continue;
            }
            RankRestrictions.LOGGER.trace("Checking equipped item " + itemId.toString() + " in slot " + slot.getName() + " for rank " + rankId + " for player " + player.getName().getString());
            boolean isRestricted = RankRestrictions.getInstance().getConfig().isItemRestrictedForRank(rankId, itemId);
            RankRestrictions.LOGGER.trace("Equipped item " + itemId.toString() + " for rank " + rankId + ": isRestricted = " + isRestricted);
            
            if (isRestricted) {
                // Make a copy of the stack before removing it
                ItemStack droppedStack = stack.copy();
                
                // Item is restricted - unequip it
                player.setItemSlot(slot, ItemStack.EMPTY);
                
                // Drop the item at the player's feet
                player.drop(droppedStack, true, false);
                
                // Send message to player
                String itemName = droppedStack.getHoverName().getString();
                String message = RankRestrictions.getInstance().getConfig().getRestrictionMessage(itemId, rankId)
                        .replace("%item%", itemName);
                
                // In 1.20.1, we need to use the component directly
                player.sendSystemMessage(Component.literal(formatColorCodes(message)));
                
                RankRestrictions.LOGGER.info("Removed restricted equipped item " + itemName + " from slot " + slot.getName() + 
                                           " for player " + player.getName().getString());
                break;
            }
        }
    }
    
    private String formatColorCodes(String message) {
        return message.replace("&0", ChatFormatting.BLACK.toString())
                .replace("&1", ChatFormatting.DARK_BLUE.toString())
                .replace("&2", ChatFormatting.DARK_GREEN.toString())
                .replace("&3", ChatFormatting.DARK_AQUA.toString())
                .replace("&4", ChatFormatting.DARK_RED.toString())
                .replace("&5", ChatFormatting.DARK_PURPLE.toString())
                .replace("&6", ChatFormatting.GOLD.toString())
                .replace("&7", ChatFormatting.GRAY.toString())
                .replace("&8", ChatFormatting.DARK_GRAY.toString())
                .replace("&9", ChatFormatting.BLUE.toString())
                .replace("&a", ChatFormatting.GREEN.toString())
                .replace("&b", ChatFormatting.AQUA.toString())
                .replace("&c", ChatFormatting.RED.toString())
                .replace("&d", ChatFormatting.LIGHT_PURPLE.toString())
                .replace("&e", ChatFormatting.YELLOW.toString())
                .replace("&f", ChatFormatting.WHITE.toString())
                .replace("&k", ChatFormatting.OBFUSCATED.toString())
                .replace("&l", ChatFormatting.BOLD.toString())
                .replace("&m", ChatFormatting.STRIKETHROUGH.toString())
                .replace("&n", ChatFormatting.UNDERLINE.toString())
                .replace("&o", ChatFormatting.ITALIC.toString())
                .replace("&r", ChatFormatting.RESET.toString());
    }
}
