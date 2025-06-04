package mchivelli.rankrestrictions.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import mchivelli.rankrestrictions.RankRestrictions;
import mchivelli.rankrestrictions.config.RankRestrictionsConfig;
import mchivelli.rankrestrictions.util.FTBRanksHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Collection;

/**
 * Command handler for RankRestrictions mod
 */
public class RankRestrictionsCommands {
    /**
     * Register the commands with the Minecraft command dispatcher
     * 
     * @param dispatcher The command dispatcher from RegisterCommandsEvent
     */
    public static void register(Object dispatcher) {
        try {
            RankRestrictions.LOGGER.info("Registering RankRestrictions commands with dispatcher: " + dispatcher.getClass().getName());

            // Try to cast the dispatcher to the actual type if possible
            if (dispatcher instanceof CommandDispatcher) {
                registerWithCommandDispatcher((CommandDispatcher<?>) dispatcher);
            } else {
                // Fall back to reflection if needed
                registerWithReflection(dispatcher);
            }
        } catch (Exception e) {
            RankRestrictions.LOGGER.error("Failed to register commands: " + e.getMessage(), e);
        }
    }
    
    /**
     * Register commands using direct access to CommandDispatcher
     */
    @SuppressWarnings("unchecked")
    private static void registerWithCommandDispatcher(CommandDispatcher<?> dispatcher) {
        RankRestrictions.LOGGER.info("Using direct CommandDispatcher registration");
        
        CommandDispatcher<CommandSourceStack> typedDispatcher = (CommandDispatcher<CommandSourceStack>) dispatcher;
        
        typedDispatcher.register(
            Commands.literal("rankrestrictions")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload")
                    .executes(context -> {
                        return executeReload(context);
                    })
                )
                .then(Commands.literal("list")
                    .executes(context -> {
                        return executeListRanks(context);
                    })
                )
        );
        
        RankRestrictions.LOGGER.info("Successfully registered rankrestrictions commands via direct access");
    }
    
    /**
     * Register commands using reflection as a fallback
     */
    private static void registerWithReflection(Object dispatcher) {
        try {
            RankRestrictions.LOGGER.info("Using reflection-based command registration");
            
            // Main command
            Object mainCommand = Commands.literal("rankrestrictions")
                .requires(source -> {
                    try {
                        return (boolean) source.getClass().getMethod("hasPermission", int.class).invoke(source, 2);
                    } catch (Exception e) {
                        RankRestrictions.LOGGER.error("Error checking permission: " + e.getMessage());
                        return false;
                    }
                });
            
            // Reload subcommand
            Object reloadCmd = Commands.literal("reload")
                .executes(context -> {
                    try {
                        RankRestrictions.getInstance().getConfig().loadConfig();
                        RankRestrictions.getInstance().getConfig().processRanks();
                        
                        Object source = context.getClass().getMethod("getSource").invoke(context);
                        source.getClass().getMethod("sendSuccess", Component.class, boolean.class)
                            .invoke(source, Component.literal("Rank restrictions config reloaded!"), true);
                        
                        return 1;
                    } catch (Exception e) {
                        RankRestrictions.LOGGER.error("Error executing reload command: " + e.getMessage());
                        return 0;
                    }
                });
                
            // List subcommand
            Object listCmd = Commands.literal("list")
                .executes(context -> {
                    try {
                        Object source = context.getClass().getMethod("getSource").invoke(context);
                        Collection<Object> ranks = FTBRanksHelper.getAllRanks();
                        
                        source.getClass().getMethod("sendSuccess", Component.class, boolean.class)
                            .invoke(source, Component.literal("Found " + ranks.size() + " ranks"), false);
                            
                        for (Object rank : ranks) {
                            String rankId = FTBRanksHelper.getRankName(rank);
                            source.getClass().getMethod("sendSuccess", Component.class, boolean.class)
                                .invoke(source, Component.literal(" - " + rankId), false);
                        }
                        
                        return 1;
                    } catch (Exception e) {
                        RankRestrictions.LOGGER.error("Error executing list command: " + e.getMessage());
                        return 0;
                    }
                });
                
            // Add subcommands to main command
            Object commandBuilder = mainCommand;
            commandBuilder = mainCommand.getClass().getMethod("then", mainCommand.getClass().getInterfaces()[0])
                .invoke(commandBuilder, reloadCmd);
            commandBuilder = commandBuilder.getClass().getMethod("then", commandBuilder.getClass().getInterfaces()[0])
                .invoke(commandBuilder, listCmd);
                
            // Build and register the command
            Object builtCommand = commandBuilder.getClass().getMethod("build").invoke(commandBuilder);
            dispatcher.getClass().getMethod("register", Object.class).invoke(dispatcher, builtCommand);
            
            RankRestrictions.LOGGER.info("Successfully registered rankrestrictions commands via reflection");
        } catch (Exception e) {
            RankRestrictions.LOGGER.error("Failed to register commands via reflection: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute the reload command
     */
    private static int executeReload(CommandContext<CommandSourceStack> context) {
        RankRestrictionsConfig config = RankRestrictions.getInstance().getConfig();
        config.loadConfig();
        config.processRanks(); // processRanks now fetches ranks internally
        
        context.getSource().sendSuccess(() -> 
            Component.literal("§aRank restrictions config reloaded! Found " + config.getRankRestrictions().size() + " ranks in config."), true);
        
        return 1;
    }
    
    /**
     * Execute the list ranks command
     */
    private static int executeListRanks(CommandContext<CommandSourceStack> context) {
        Collection<Object> ranks = FTBRanksHelper.getAllRanks();
        
        if (ranks.isEmpty()) {
            context.getSource().sendSuccess(() -> 
                Component.literal("§cNo ranks found from FTBRanks API!"), false);
            return 0;
        }
        
        context.getSource().sendSuccess(() -> 
            Component.literal("§aFound " + ranks.size() + " ranks:"), false);
            
        for (Object rank : ranks) {
            String rankId = FTBRanksHelper.getRankName(rank);
            String displayName = FTBRanksHelper.getRankDisplayName(rank);
            context.getSource().sendSuccess(() -> 
                Component.literal("§7 - " + rankId + (displayName.equals(rankId) ? "" : " (" + displayName + ")")), false);
        }
        
        return 1;
    }
}
