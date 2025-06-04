package mchivelli.rankrestrictions.util;

import mchivelli.rankrestrictions.RankRestrictions;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Helper class to safely interact with the FTBRanks API using reflection
 * This avoids hard dependencies on the FTBRanks API at compile time
 */
public class FTBRanksHelper {
    private static Class<?> ftbRanksClass;
    private static Class<?> rankClass;
    private static Class<?> rankManagerClass;
    
    private static Method getApiMethod;
    private static Method getManagerMethod;
    private static Method getAllRanksMethod;
    private static Method getPlayerRanksMethod;
    private static Method getRankIdMethod;
    private static Method getRankDisplayNameMethod;
    
    // Initialize reflection methods
    static {
        try {
            // First try the API class path
            try {
                ftbRanksClass = Class.forName("dev.ftb.mods.ftbranks.api.FTBRanksAPI");
                RankRestrictions.LOGGER.info("Found FTBRanks API class");
            } catch (ClassNotFoundException e) {
                // If API class not found, try the main FTBRanks class
                ftbRanksClass = Class.forName("dev.ftb.mods.ftbranks.FTBRanks");
                RankRestrictions.LOGGER.info("Found FTBRanks main class");
            }
            
            rankClass = Class.forName("dev.ftb.mods.ftbranks.api.Rank");
            rankManagerClass = Class.forName("dev.ftb.mods.ftbranks.api.RankManager");
            
            // Try different method names that might exist in different versions
            try {
                getApiMethod = ftbRanksClass.getMethod("getInstance");
            } catch (NoSuchMethodException e) {
                try {
                    getApiMethod = ftbRanksClass.getMethod("getAPI");
                } catch (NoSuchMethodException ex) {
                    getApiMethod = ftbRanksClass.getMethod("get");
                }
            }
            
            try {
                getManagerMethod = ftbRanksClass.getMethod("manager");
            } catch (NoSuchMethodException e) {
                try {
                    getManagerMethod = ftbRanksClass.getMethod("getRankManager");
                } catch (NoSuchMethodException ex) {
                    getManagerMethod = ftbRanksClass.getMethod("getManager");
                }
            }
            
            try {
                getAllRanksMethod = rankManagerClass.getMethod("getAllRanks");
            } catch (NoSuchMethodException e) {
                getAllRanksMethod = rankManagerClass.getMethod("getRanks");
            }
            
            getPlayerRanksMethod = rankManagerClass.getMethod("getRanks", ServerPlayer.class);
            
            try {
                getRankIdMethod = rankClass.getMethod("getId");
            } catch (NoSuchMethodException e) {
                getRankIdMethod = rankClass.getMethod("getName");
            }
            
            try {
                getRankDisplayNameMethod = rankClass.getMethod("getDisplayName");
            } catch (NoSuchMethodException e) {
                getRankDisplayNameMethod = rankClass.getMethod("getName");
            }
            
            RankRestrictions.LOGGER.info("FTBRanks API classes loaded successfully via reflection");
        } catch (Exception e) {
            RankRestrictions.LOGGER.error("Failed to initialize FTBRanks API reflection: " + e.getMessage(), e);
            // Don't rethrow - we'll handle the null methods gracefully
        }
    }
    
    /**
     * Get all available ranks from FTBRanks
     * @return Collection of rank objects or empty list if FTBRanks is not available
     */
    @SuppressWarnings("unchecked")
    public static Collection<Object> getAllRanks() {
        try {
            if (isApiAvailable()) {
                Object api = getApiMethod.invoke(null);
                Object rankManager = getManagerMethod.invoke(api);
                return (Collection<Object>) getAllRanksMethod.invoke(rankManager);
            }
        } catch (Exception e) {
            RankRestrictions.LOGGER.error("Failed to get ranks from FTBRanks API: " + e.getMessage(), e);
        }
        return Collections.emptyList();
    }
    
    /**
     * Get all ranks for a specific player
     * @param player The server player
     * @return List of rank objects or empty list if FTBRanks is not available
     */
    @SuppressWarnings("unchecked")
    public static List<Object> getPlayerRanks(ServerPlayer player) {
        try {
            if (isApiAvailable()) {
                Object api = getApiMethod.invoke(null);
                Object rankManager = getManagerMethod.invoke(api);
                return (List<Object>) getPlayerRanksMethod.invoke(rankManager, player);
            }
        } catch (Exception e) {
            RankRestrictions.LOGGER.error("Failed to get player ranks from FTBRanks API: " + e.getMessage(), e);
        }
        return Collections.emptyList();
    }
    
    /**
     * Get the ID of a rank object
     * @param rankObj The rank object from FTBRanks
     * @return The rank ID or empty string if not available
     */
    public static String getRankName(Object rankObj) {
        try {
            if (rankObj != null && rankClass.isInstance(rankObj)) {
                return (String) getRankIdMethod.invoke(rankObj);
            }
        } catch (Exception e) {
            RankRestrictions.LOGGER.error("Failed to get rank ID: " + e.getMessage(), e);
            try {
                // Last resort - try to get string representation
                return rankObj.toString();
            } catch (Exception ex) {
                RankRestrictions.LOGGER.error("Failed to get rank name as fallback: " + ex.getMessage(), ex);
            }
        }
        return "";
    }
    
    /**
     * Get the display name of a rank
     * @param rankObj The rank object from FTBRanks
     * @return The rank display name or empty string if not available
     */
    public static String getRankDisplayName(Object rankObj) {
        try {
            if (rankObj != null && rankClass.isInstance(rankObj)) {
                return (String) getRankDisplayNameMethod.invoke(rankObj);
            }
        } catch (Exception e) {
            RankRestrictions.LOGGER.error("Failed to get rank display name: " + e.getMessage(), e);
        }
        return getRankName(rankObj); // Fall back to ID if display name fails
    }
    
    /**
     * Get a property from a rank object
     * @param rankObj The rank object from FTBRanks
     * @param propertyName The name of the property method to invoke
     * @return The property value as a string or empty string if not available
     */
    public static String getRankProperty(Object rankObj, String propertyName) {
        try {
            if (rankObj != null && rankClass.isInstance(rankObj)) {
                Method method = rankClass.getMethod(propertyName);
                Object result = method.invoke(rankObj);
                return result != null ? result.toString() : "";
            }
        } catch (Exception e) {
            RankRestrictions.LOGGER.error("Failed to get rank property '" + propertyName + "': " + e.getMessage(), e);
        }
        return "";
    }
    
    /**
     * Check if FTBRanks API is available
     * @return true if FTBRanks API is available, false otherwise
     */
    public static boolean isApiAvailable() {
        try {
            if (ftbRanksClass == null || getApiMethod == null || getManagerMethod == null) {
                return false;
            }
            
            Object api = getApiMethod.invoke(null);
            return api != null && getManagerMethod.invoke(api) != null;
        } catch (Exception e) {
            RankRestrictions.LOGGER.error("Error checking FTBRanks API: " + e.getMessage(), e);
            return false;
        }
    }
}
