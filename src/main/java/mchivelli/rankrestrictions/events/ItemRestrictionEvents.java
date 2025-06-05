package mchivelli.rankrestrictions.events;

import mchivelli.rankrestrictions.RankRestrictions;
import mchivelli.rankrestrictions.util.FTBRanksHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ItemRestrictionEvents {

    // Cooldown for item pickup denial messages: PlayerUUID -> <ItemEntityID, LastMessageTimestamp>
    private static final Map<UUID, Map<Integer, Long>> pickupMessageCooldowns = new ConcurrentHashMap<>();
    private static final long PICKUP_MESSAGE_COOLDOWN_MS = 5000; // 5 seconds

    @SubscribeEvent
    public void onPlayerAttemptPickupItem(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemEntity itemEntity = event.getItem();
        ItemStack itemStack = itemEntity.getItem();

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

                UUID playerUUID = player.getUUID();
                int entityId = itemEntity.getId();
                long currentTime = System.currentTimeMillis();

                pickupMessageCooldowns.putIfAbsent(playerUUID, new ConcurrentHashMap<>());
                Map<Integer, Long> playerCooldowns = pickupMessageCooldowns.get(playerUUID);
                long lastMessageTime = playerCooldowns.getOrDefault(entityId, 0L);

                String itemNameForLog = itemStack.getDisplayName().getString();
                if (itemNameForLog.isEmpty()) itemNameForLog = itemId.toString();

                if (currentTime - lastMessageTime > PICKUP_MESSAGE_COOLDOWN_MS) {
                    String itemName = itemStack.getDisplayName().getString();
                    if (itemName.isEmpty()) {
                        itemName = itemId.toString();
                    }
                    String messageFormat = RankRestrictions.getInstance().getConfig().getRestrictionMessage(itemId, rankId);
                    String rawMessage = messageFormat.replace("%item%", itemName);
                    player.sendSystemMessage(Component.literal(rawMessage.replace('&', ChatFormatting.PREFIX_CODE)));
                    playerCooldowns.put(entityId, currentTime);
                }
                
                RankRestrictions.LOGGER.info("Prevented player " + player.getName().getString() +
                                          " (rank " + rankId + ") from picking up restricted item " + itemNameForLog);
                break;
            }
        }
    }

    @SubscribeEvent
    public void onPlayerRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
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
                String itemName = itemStack.getDisplayName().getString();
                if (itemName.isEmpty()) {
                    itemName = itemId.toString();
                }
                String messageFormat = RankRestrictions.getInstance().getConfig().getRestrictionMessage(itemId, rankId);
                String rawMessage = messageFormat.replace("%item%", itemName);
                player.sendSystemMessage(Component.literal(rawMessage.replace('&', ChatFormatting.PREFIX_CODE)));
                
                RankRestrictions.LOGGER.info("Prevented player " + player.getName().getString() +
                                          " (rank " + rankId + ") from using restricted item " + itemName);
                break;
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END &&
            event.player instanceof ServerPlayer serverPlayer &&
            event.player.tickCount % 600 == 0) { // Check every 30 seconds (600 ticks)

            if (!RankRestrictions.getInstance().getConfig().isConfigLoaded() || !FTBRanksHelper.isApiAvailable()) {
                return;
            }
            checkPlayerInventory(serverPlayer);
        }
    }
    
    @SubscribeEvent
    public void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            ItemStack newStack = event.getTo();
            if (!newStack.isEmpty()) {
                checkEquippedItem(serverPlayer, event.getSlot(), newStack);
            }
        }
    }
    
    private void sendRestrictionRemovedMessage(ServerPlayer player, ItemStack restrictedStack, String rankIdContext) {
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(restrictedStack.getItem());
        if (itemId == null) itemId = ResourceLocation.fromNamespaceAndPath("rankrestrictions", "unknown_item"); // Fixed deprecated constructor

        String itemName = restrictedStack.getDisplayName().getString();
        if (itemName.isEmpty()) {
            itemName = itemId.toString();
        }
        String messageFormat = RankRestrictions.getInstance().getConfig().getRestrictionMessage(itemId, rankIdContext);
        String rawMessage = messageFormat.replace("%item%", itemName);
        player.sendSystemMessage(Component.literal(rawMessage.replace('&', ChatFormatting.PREFIX_CODE)));
    }

    private void checkPlayerInventory(ServerPlayer player) {
        if (player.isCreative()) {
            return;
        }
        
        if (!RankRestrictions.getInstance().getConfig().isConfigLoaded() || !FTBRanksHelper.isApiAvailable()) {
            return;
        }
        
        Collection<Object> playerRanks = FTBRanksHelper.getPlayerRanks(player);
        if (playerRanks.isEmpty()) {
            return;
        }
        
        Inventory inventory = player.getInventory();
        
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stackInSlot = inventory.getItem(i);
            
            if (stackInSlot.isEmpty()) {
                continue;
            }
            
            ResourceLocation currentItemId = ForgeRegistries.ITEMS.getKey(stackInSlot.getItem());
            if (currentItemId == null) {
                continue;
            }
            
            for (Object rankObj : playerRanks) {
                String rankId = FTBRanksHelper.getRankName(rankObj);
                if (rankId == null || rankId.isEmpty()) continue;

                if (RankRestrictions.getInstance().getConfig().isItemRestrictedForRank(rankId, currentItemId)) {
                    inventory.setItem(i, ItemStack.EMPTY);
                    
                    String itemNameForLog = stackInSlot.getDisplayName().getString();
                    if (itemNameForLog.isEmpty()) itemNameForLog = currentItemId.toString();

                    RankRestrictions.LOGGER.info("Removed restricted item " + itemNameForLog +
                                              " from player " + player.getName().getString() + "'s inventory (rank " + rankId + ")");
                    sendRestrictionRemovedMessage(player, stackInSlot, rankId);
                    break; 
                }
            }
        }
    }

    private void checkEquippedItem(ServerPlayer player, EquipmentSlot slot, ItemStack equippedStack) {
        if (player.isCreative() || equippedStack.isEmpty()) {
            return;
        }

        if (!RankRestrictions.getInstance().getConfig().isConfigLoaded() || !FTBRanksHelper.isApiAvailable()) {
            return;
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(equippedStack.getItem());
        if (itemId == null) {
            return;
        }

        Collection<Object> playerRanks = FTBRanksHelper.getPlayerRanks(player);
        if (playerRanks.isEmpty()) {
            return;
        }
        
        for (Object rankObj : playerRanks) {
            String rankId = FTBRanksHelper.getRankName(rankObj);
            if (rankId == null || rankId.isEmpty()) {
                RankRestrictions.LOGGER.trace("Empty or null rankId found for player " + player.getName().getString() + ", skipping this rank object during equipment check.");
                continue;
            }
            
            if (RankRestrictions.getInstance().getConfig().isItemRestrictedForRank(rankId, itemId)) {
                ItemStack itemToReturn = equippedStack.copy();
                player.setItemSlot(slot, ItemStack.EMPTY); // Unequip the item
                
                if (!player.getInventory().add(itemToReturn)) { // Attempt to return the item to player's inventory
                    player.drop(itemToReturn, false); // Drop if inventory is full
                }
                
                String itemNameForMessage = itemToReturn.getDisplayName().getString();
                if (itemNameForMessage.isEmpty()) itemNameForMessage = itemId.toString();

                String messageFormat = RankRestrictions.getInstance().getConfig().getRestrictionMessage(itemId, rankId);
                String rawMessage = messageFormat.replace("%item%", itemNameForMessage);
                player.sendSystemMessage(Component.literal(rawMessage.replace('&', ChatFormatting.PREFIX_CODE)));
                
                String itemNameForLog = equippedStack.getDisplayName().getString();
                if (itemNameForLog.isEmpty()) itemNameForLog = itemId.toString();
                RankRestrictions.LOGGER.info("Unequipped restricted item " + itemNameForLog + " from slot " + slot.getName() + 
                                           " for player " + player.getName().getString() + " (rank " + rankId + ")");
                break;
            }
        }
    }
}
