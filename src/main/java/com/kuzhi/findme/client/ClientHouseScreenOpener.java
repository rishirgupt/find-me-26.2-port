package com.kuzhi.findme.client;

import com.kuzhi.findme.common.CompanionKind;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

public final class ClientHouseScreenOpener {
   private ClientHouseScreenOpener() {
   }

   public static void open(BlockPos housePos, Identifier dimensionId) {
      ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
      Minecraft.getInstance().gui.setScreen(new CompanionManageScreen(CompanionKind.MOUNT, housePos, dimension));
   }
}
