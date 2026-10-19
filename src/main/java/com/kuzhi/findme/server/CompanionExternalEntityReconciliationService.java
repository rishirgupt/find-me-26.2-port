package com.kuzhi.findme.server;

import com.kuzhi.findme.FindMe;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.SavedPosition;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

final class CompanionExternalEntityReconciliationService {
   private CompanionExternalEntityReconciliationService() {
   }

   static void reconcileJoinedEntity(LivingEntity living) {
      if (living != null && !living.level().isClientSide()) {
         if (CompanionEntityClassifier.hasOwnershipSignal(living)) {
            MinecraftServer server = living.level().getServer();
            if (server != null) {
               UUID newUuid = living.getUUID();
               String entityType = EntityType.getKey(living.getType()).toString();
               String liveName = living.getDisplayName().getString();

               for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                  PlayerCompanionData data = CompanionManager.data(player);
                  if (!data.contains(newUuid) && CompanionManager.isOwnedBy(player, living)) {
                     Optional<UUID> replacement = findExternalReplacement(server, data, entityType, liveName);
                     if (!replacement.isEmpty()) {
                        UUID oldUuid = replacement.get();
                        reconcileReplacement(player, data, oldUuid, living, "join");
                        return;
                     }
                  }
               }
            }
         }
      }
   }

   static void reconcileAfterRegisteredEntityLeave(MinecraftServer server, LivingEntity oldLiving) {
      if (server != null && oldLiving != null && !oldLiving.level().isClientSide()) {
         if (CompanionEntityClassifier.hasOwnershipSignal(oldLiving)) {
            UUID oldUuid = oldLiving.getUUID();
            String entityType = EntityType.getKey(oldLiving.getType()).toString();

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
               PlayerCompanionData data = CompanionManager.data(player);
               if (data.contains(oldUuid) || data.deadList().contains(oldUuid)) {
                  String expectedName = expectedName(data, oldUuid, oldLiving);
                  Optional<LivingEntity> replacement = findLiveReplacement(server, player, data, oldUuid, entityType, expectedName, oldLiving);
                  if (!replacement.isEmpty()) {
                     reconcileReplacement(player, data, oldUuid, replacement.get(), "leave");
                     return;
                  }
               }
            }
         }
      }
   }

   private static Optional<UUID> findExternalReplacement(MinecraftServer server, PlayerCompanionData data, String entityType, String liveName) {
      List<UUID> candidates = new ArrayList<>();

      for (CompanionKind kind : CompanionKind.values()) {
         for (UUID uuid : data.list(kind)) {
            collectCandidate(server, data, uuid, entityType, liveName, candidates);
         }
      }

      for (UUID uuid : data.deadList()) {
         collectCandidate(server, data, uuid, entityType, liveName, candidates);
      }

      return candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.empty();
   }

   private static Optional<LivingEntity> findLiveReplacement(
      MinecraftServer server, ServerPlayer player, PlayerCompanionData data, UUID oldUuid, String entityType, String expectedName, LivingEntity oldLiving
   ) {
      List<LivingEntity> candidates = new ArrayList<>();
      Set<UUID> seen = new HashSet<>();
      collectLiveCandidates(
         player.level(), player.getBoundingBox().inflate(96.0, 64.0, 96.0), player, data, oldUuid, entityType, expectedName, candidates, seen
      );
      if (oldLiving.level() instanceof ServerLevel oldLevel) {
         collectLiveCandidates(
            oldLevel, oldLiving.getBoundingBox().inflate(48.0, 32.0, 48.0), player, data, oldUuid, entityType, expectedName, candidates, seen
         );
      }

      return candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.empty();
   }

   private static void collectLiveCandidates(
      ServerLevel level,
      AABB area,
      ServerPlayer player,
      PlayerCompanionData data,
      UUID oldUuid,
      String entityType,
      String expectedName,
      List<LivingEntity> candidates,
      Set<UUID> seen
   ) {
      for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive)) {
         UUID newUuid = living.getUUID();
         if (!newUuid.equals(oldUuid)
            && seen.add(newUuid)
            && !data.contains(newUuid)
            && entityType.equals(EntityType.getKey(living.getType()).toString())
            && CompanionManager.isOwnedBy(player, living)) {
            String liveName = living.getDisplayName().getString();
            if (namesMatch(expectedName, liveName)) {
               candidates.add(living);
            }
         }
      }
   }

   private static void collectCandidate(
      MinecraftServer server, PlayerCompanionData data, UUID oldUuid, String entityType, String liveName, List<UUID> candidates
   ) {
      if (!CompanionManager.findEntity(server, oldUuid).isPresent()) {
         Optional<CompoundTag> maybeStored = data.storedEntity(oldUuid);
         if (!maybeStored.isEmpty()) {
            CompoundTag stored = maybeStored.get();
            String storedType = CompanionEntitySnapshots.storedEntityType(stored);
            if (entityType.equals(storedType)) {
               String storedName = data.displayName(oldUuid).orElseGet(() -> CompanionEntitySnapshots.storedEntityName(stored, oldUuid));
               if (storedName.isBlank() || liveName.isBlank() || storedName.equals(liveName)) {
                  candidates.add(oldUuid);
               }
            }
         }
      }
   }

   private static void reconcileReplacement(ServerPlayer player, PlayerCompanionData data, UUID oldUuid, LivingEntity replacement, String reason) {
      UUID newUuid = replacement.getUUID();
      CompanionKind kind = data.kindOf(oldUuid).orElse(CompanionKind.COMPANION);
      data.replaceUuid(oldUuid, newUuid);
      if (!data.contains(newUuid)) {
         data.add(kind, newUuid);
      }

      data.deadCompanions.remove(newUuid);
      data.deadKinds.remove(newUuid);
      data.removeStoredEntity(newUuid);
      data.setDeployed(kind, newUuid);
      data.setLastKnownPosition(
         newUuid,
         SavedPosition.of(replacement.level(), replacement.getX(), replacement.getY(), replacement.getZ(), replacement.getYRot(), replacement.getXRot())
      );
      data.setDisplayName(newUuid, replacement.getDisplayName().getString());
      CompanionManager.save(player, data);
      CompanionManager.syncToClient(player, CompanionKind.MOUNT);
      CompanionManager.syncToClient(player, CompanionKind.COMPANION);
      CompanionManager.syncDeadToClient(player);
      FindMe.LOGGER
         .info(
            "FindMe reconciled externally recreated companion {} -> {} for {} after {}.",
            new Object[]{oldUuid, newUuid, player.getGameProfile().name(), reason}
         );
   }

   private static String expectedName(PlayerCompanionData data, UUID oldUuid, LivingEntity oldLiving) {
      return data.displayName(oldUuid)
         .orElseGet(
            () -> data.storedEntity(oldUuid).map(tag -> CompanionEntitySnapshots.storedEntityName(tag, oldUuid)).orElse(oldLiving.getDisplayName().getString())
         );
   }

   private static boolean namesMatch(String expectedName, String liveName) {
      return expectedName == null || liveName == null || expectedName.isBlank() || liveName.isBlank() || expectedName.equals(liveName);
   }
}
