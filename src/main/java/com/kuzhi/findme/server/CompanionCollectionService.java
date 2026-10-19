package com.kuzhi.findme.server;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

final class CompanionCollectionService {
   private CompanionCollectionService() {
   }

   static void collectActive(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
      Optional<UUID> active = data.active(kind);
      Optional<UUID> maybeUuid = active.filter(uuid -> data.isDeployed(kind, uuid)).or(() -> data.deployed(kind)).or(() -> active);
      if (maybeUuid.isEmpty()) {
         CompanionSummonLineService.showNoRegistered(player, kind);
      } else {
         collectUuid(player, data, kind, maybeUuid.get());
      }
   }

   static boolean collectIndex(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int index) {
      Optional<UUID> maybeUuid = data.wheelUuidAt(kind, index);
      if (maybeUuid.isEmpty()) {
         return false;
      }

      collectUuid(player, data, kind, maybeUuid.get());
      return true;
   }

   private static void collectUuid(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid) {
      if (CompanionManager.isStoragePending(uuid)) {
         CompanionSummonLineService.showBusy(player, data, kind, uuid);
      } else {
         boolean wasDeployed = data.isDeployed(kind, uuid);
         Entity entity = CompanionManager.locateEntity(player.level().getServer(), data, uuid).orElse(null);
         if (!(entity instanceof LivingEntity living)) {
            Optional<CompoundTag> shoulderTag = CompanionManager.shoulderEntityTag(player, uuid);
            if (shoulderTag.isPresent()) {
               Optional<LivingEntity> released = CompanionManager.releaseShoulderCompanion(player, uuid, shoulderTag.get());
               if (released.isPresent()) {
                  CompanionManager.storeAndDiscard(player, data, released.get());
               } else {
                  data.storeEntity(uuid, CompanionManager.storedShoulderTag(player, uuid, shoulderTag.get()));
                  CompanionManager.clearPlayerShoulderEntity(player, uuid);
               }

               data.clearDeployed(kind, uuid);
               CompanionManager.save(player, data);
               CompanionManager.syncToClient(player, kind);
            } else if (wasDeployed && data.storedEntity(uuid).isPresent()) {
               data.clearDeployed(kind, uuid);
               CompanionManager.save(player, data);
               CompanionManager.syncToClient(player, kind);
            } else {
               CompanionSummonLineService.showUnavailable(player, data, kind);
            }
         } else {
            CompanionDeploymentService.collectLiving(player, data, kind, living);
            CompanionManager.save(player, data);
            CompanionManager.syncToClient(player, kind);
         }
      }
   }

   static boolean collectActiveShoulderCompanion(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
      if (CompanionManager.isStoragePending(uuid)) {
         CompanionSummonLineService.showBusy(player, data, CompanionKind.COMPANION, uuid);
         return true;
      }

      Optional<CompoundTag> shoulderTag = CompanionManager.shoulderEntityTag(player, uuid);
      if (shoulderTag.isEmpty()) {
         return false;
      }

      Optional<LivingEntity> released = CompanionManager.releaseShoulderCompanion(player, uuid, shoulderTag.get());
      if (released.isPresent()) {
         CompanionManager.storeAndDiscard(player, data, released.get());
      } else {
         data.storeEntity(uuid, CompanionManager.storedShoulderTag(player, uuid, shoulderTag.get()));
         CompanionManager.clearPlayerShoulderEntity(player, uuid);
      }

      data.clearDeployed(CompanionKind.COMPANION, uuid);
      data.setReadyAt(CompanionKind.COMPANION, player.level().getGameTime() + Config.companionCooldownTicks);
      CompanionManager.save(player, data);
      CompanionManager.syncToClient(player, CompanionKind.COMPANION);
      return true;
   }

   static void collectDeployedOnDeath(ServerPlayer player) {
      PlayerCompanionData data = CompanionManager.data(player);
      boolean changed = false;

      for (CompanionKind kind : CompanionKind.values()) {
         for (UUID uuid : data.deployedList(kind)) {
            Entity entity = CompanionManager.locateEntity(player.level().getServer(), data, uuid).orElse(null);
            if (entity instanceof LivingEntity living && living.isAlive()) {
               if (player.getVehicle() == living) {
                  player.stopRiding();
               }

               CompanionManager.storeAndDiscard(player, data, living);
            } else {
               CompanionManager.shoulderEntityTag(player, uuid).ifPresent(tag -> {
                  data.storeEntity(uuid, CompanionManager.storedShoulderTag(player, uuid, tag));
                  CompanionManager.clearPlayerShoulderEntity(player, uuid);
               });
            }

            data.clearDeployed(kind, uuid);
            changed = true;
         }
      }

      if (changed) {
         CompanionManager.save(player, data);
      }
   }

   static void collectDeployedForTravel(ServerPlayer player) {
      if (CompanionManager.isMountSettleProtected(player)) {
         CompanionManager.syncToClient(player, CompanionKind.MOUNT);
         CompanionManager.syncToClient(player, CompanionKind.COMPANION);
      } else {
         collectDeployedOnDeath(player);
         CompanionManager.syncToClient(player, CompanionKind.MOUNT);
         CompanionManager.syncToClient(player, CompanionKind.COMPANION);
      }
   }

   static void collectDeployedForLogout(ServerPlayer player) {
      PlayerCompanionData data = CompanionManager.data(player);
      boolean changed = false;

      for (CompanionKind kind : CompanionKind.values()) {
         for (UUID uuid : data.deployedList(kind)) {
            Entity entity = CompanionManager.locateEntity(player.level().getServer(), data, uuid).orElse(null);
            if (entity instanceof LivingEntity living && living.isAlive()) {
               if (player.getVehicle() == living) {
                  player.stopRiding();
               }

               CompanionManager.storeAndDiscard(player, data, living);
               changed = true;
            } else if (CompanionManager.shoulderEntityTag(player, uuid).isPresent()) {
               CompoundTag tag = CompanionManager.shoulderEntityTag(player, uuid).get();
               data.storeEntity(uuid, CompanionManager.storedShoulderTag(player, uuid, tag));
               CompanionManager.clearPlayerShoulderEntity(player, uuid);
               changed = true;
            } else if (data.storedEntity(uuid).isPresent()) {
               changed = true;
            }

            data.clearDeployed(kind, uuid);
         }
      }

      if (changed) {
         CompanionManager.save(player, data);
      }
   }

   static void snapshotRegisteredBeforeUnload(MinecraftServer server, Entity entity) {
      if (entity instanceof LivingEntity living && living.isAlive()) {
         UUID uuid = living.getUUID();

         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerCompanionData data = CompanionManager.data(player);
            if (data.contains(uuid)) {
               CompanionManager.clearHomeResident(living);
               CompanionManager.snapshotEntity(player, data, living);
               CompanionManager.save(player, data);
               break;
            }
         }
      }
   }
}
