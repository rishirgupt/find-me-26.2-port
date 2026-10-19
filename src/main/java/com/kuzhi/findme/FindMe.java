package com.kuzhi.findme;

import com.kuzhi.findme.common.ModBlocks;
import com.kuzhi.findme.common.ModItems;
import com.kuzhi.findme.common.ModParticles;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.server.CompanionEvents;
import com.kuzhi.findme.server.CompanionManager;
import com.kuzhi.findme.server.VehicleManager;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;

public class FindMe implements ModInitializer {
   public static final String MODID = "find_me";
   public static final Logger LOGGER = LogUtils.getLogger();

   @Override
   public void onInitialize() {
      ModBlocks.register();
      ModItems.register();
      ModParticles.register();
      Config.register();
      ModNetwork.register();
      CompanionEvents.register();
      CompanionManager.bootstrap();
      VehicleManager.bootstrap();
      LOGGER.info("Find me core loaded");
   }
}
