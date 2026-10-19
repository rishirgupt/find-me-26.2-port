package com.kuzhi.findme.server;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

final class CompanionOwnershipGuardService {
   private CompanionOwnershipGuardService() {
   }

   static Optional<ServerPlayer> registeredOwner(MinecraftServer server, UUID entityUuid) {
      if (server != null && entityUuid != null) {
         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (CompanionManager.data(player).contains(entityUuid)) {
               return Optional.of(player);
            }
         }

         return Optional.empty();
      } else {
         return Optional.empty();
      }
   }

   static boolean isRegisteredFor(ServerPlayer player, Entity entity) {
      return player != null && entity != null && CompanionManager.data(player).contains(entity.getUUID());
   }
}
