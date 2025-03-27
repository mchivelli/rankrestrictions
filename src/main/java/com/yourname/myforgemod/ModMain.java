package com.yourname.myforgemod;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/* [Guide: ModMain.java is the entry point of your Forge mod.
   - The @Mod annotation registers this class with Forge using the unique mod ID "examplemod".
   - The constructor sets up the mod by registering items, blocks, tile entities, and configuration.
   - It also adds event listeners for both common (server and client) and client-specific setup.
   - Use this file to initialize your mod’s core functionality without modifying the critical steps.
] */
@Mod(ModMain.MODID)
public class ModMain {

  public static final String MODID = "myforgemod"; // [Guide: Unique identifier for your mod; must be all lowercase.]
  public static final Logger LOGGER = LogManager.getLogger(); // [Guide: Logger for outputting debug/info messages.]

  public ModMain() {
    // [Guide: Retrieve the mod event bus for registering events during mod loading.]
    IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();

    // [Guide: Register your mod’s blocks, items, and tile entities so they are initialized correctly.]
    ModRegistry.BLOCKS.register(eventBus);
    ModRegistry.ITEMS.register(eventBus);
    ModRegistry.TILE_ENTITIES.register(eventBus);
    // [Guide: Initialize the configuration settings for your mod.]
    new ConfigManager();
    // [Guide: Add listeners for common and client-specific setup events.]
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setupClient);
  }

  private void setup(final FMLCommonSetupEvent event) {
    // [Guide: Common setup method. Use this to initialize logic that should run on both client and server.]
    //    MinecraftForge.EVENT_BUS.register(new WhateverEvents());
  }

  private void setupClient(final FMLClientSetupEvent event) {
    // [Guide: Client-only setup method. Use this for client-specific initialization like rendering registration.]
    //for client side only setup
  }

  /**
   * Method to handle server starting event.
   */
  @SubscribeEvent
  public void onServerStarting(ServerStartingEvent event) {
    LOGGER.info("Server starting: Initializing Mod");
    // Initialize Mod Systems
    //TaxManager.initialize(event.getServer());
  }

  /**
   * Registers commands for the mod
   */
  @SubscribeEvent
  public void onRegisterCommands(RegisterCommandsEvent event) {

    /**
     *     WarCommands.register(event.getDispatcher()); // Register the PvP command
     *     LOGGER.info("MineColonyTax: PvP command registered.");
     *     ClaimTaxCommand.register(event.getDispatcher()); // Register the Claim Tax command
     *     CheckTaxRevenueCommand.register(event.getDispatcher()); // Register the Check Tax Revenue command
     *     LOGGER.info("MineColonyTax: Commands registered.");
     *     loadArenaPositions();
     */

  }
  
}
