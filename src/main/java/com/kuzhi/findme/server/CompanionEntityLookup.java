package com.kuzhi.findme.server;

import com.kuzhi.findme.common.SavedPosition;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

final class CompanionEntityLookup {
   private CompanionEntityLookup() {
   }

   static Optional<Entity> findEntity(MinecraftServer server, UUID uuid) {
      for (ServerLevel level : server.getAllLevels()) {
         Entity entity = level.getEntity(uuid);
         if (entity != null && !entity.isRemoved()) {
            return Optional.of(entity);
         }
      }

      return Optional.empty();
   }

   static Optional<Entity> findLoadedEntity(MinecraftServer server, PlayerCompanionData data, UUID uuid) {
      Optional<Entity> loaded = findEntity(server, uuid);
      if (loaded.isPresent()) {
         return loaded;
      }

      Optional<SavedPosition> maybePosition = data.lastKnownPosition(uuid);
      if (maybePosition.isEmpty()) {
         return Optional.empty();
      }

      SavedPosition position = maybePosition.get();
      ServerLevel level = server.getLevel(position.dimension());
      return level != null && level.hasChunkAt(position.blockPos()) && level.isLoaded(position.blockPos())
         ? Optional.ofNullable(level.getEntity(uuid))
         : Optional.empty();
   }

   static Optional<Entity> locateEntity(MinecraftServer server, PlayerCompanionData data, UUID uuid) {
      Optional<Entity> loaded = findEntity(server, uuid);
      if (loaded.isPresent()) {
         return loaded;
      }

      Optional<SavedPosition> maybePosition = data.lastKnownPosition(uuid);
      if (maybePosition.isEmpty()) {
         return Optional.empty();
      }

      SavedPosition position = maybePosition.get();
      ServerLevel level = server.getLevel(position.dimension());
      if (level == null) {
         return Optional.empty();
      }

      level.getChunk(position.blockPos());
      return Optional.ofNullable(level.getEntity(uuid));
   }
}
