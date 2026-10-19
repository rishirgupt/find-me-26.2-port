package com.kuzhi.findme.server;

import com.kuzhi.findme.common.CompanionKind;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

final class CompanionDeploymentService {
   private CompanionDeploymentService() {
   }

   static boolean collectOtherDeployed(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID keepUuid) {
      return collectOtherDeployed(player, data, kind, keepUuid, null, true);
   }

   static boolean collectOtherDeployed(
      ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID keepUuid, UUID excludedUuid, boolean collectPendingMounts
   ) {
      if (kind == CompanionKind.COMPANION) {
         return false;
      }

      boolean changed = false;

      for (UUID uuid : List.copyOf(data.deployedList(kind))) {
         if (!uuid.equals(keepUuid) && !uuid.equals(excludedUuid)) {
            changed |= collectOne(player, data, kind, uuid);
         }
      }

      if (collectPendingMounts && kind == CompanionKind.MOUNT) {
         changed |= CompanionMountCinematicFlowService.collectOtherVisibleMountCinematics(player, data, keepUuid, excludedUuid);
      }

      return changed;
   }

   static void rememberDeployedWithinLimit(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID keepUuid) {
      if (kind == CompanionKind.COMPANION) {
         data.setDeployed(kind, keepUuid);
         CompanionManager.enforceCompanionDeploymentLimit(player, data, keepUuid);
      } else {
         collectOtherDeployed(player, data, kind, keepUuid);
         data.setDeployed(kind, keepUuid);
      }
   }

   private static boolean collectOne(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid) {
      boolean changed = data.isDeployed(kind, uuid);
      Entity entity = CompanionManager.locateEntity(player.level().getServer(), data, uuid).orElse(null);
      if (entity instanceof LivingEntity living && living.isAlive()) {
         collectLiving(player, data, kind, living);
         changed = true;
      } else if (kind == CompanionKind.COMPANION) {
         Optional<CompoundTag> shoulderTag = CompanionManager.shoulderEntityTag(player, uuid);
         if (shoulderTag.isPresent()) {
            data.storeEntity(uuid, CompanionManager.storedShoulderTag(player, uuid, shoulderTag.get()));
            CompanionManager.clearPlayerShoulderEntity(player, uuid);
            changed = true;
         }
      }

      data.clearDeployed(kind, uuid);
      return changed;
   }

   static boolean collectLiving(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, LivingEntity living) {
      if (player == null || data == null || kind == null || living == null || !living.isAlive()) {
         return false;
      }

      if (CompanionManager.isStoragePending(living)) {
         return false;
      }

      UUID uuid = living.getUUID();
      if (player.getVehicle() == living) {
         player.stopRiding();
      }

      if (data.homeNestBlock(uuid).isPresent() && CompanionManager.sendHomeIfPossible(player, data, kind, living)) {
         data.clearDeployed(kind, uuid);
         return true;
      } else {
         CompanionManager.storeAndDiscard(player, data, living);
         data.clearDeployed(kind, uuid);
         return true;
      }
   }

   static boolean retireMountAfterSharedRideSwitch(ServerPlayer player, PlayerCompanionData data, Entity previousRide) {
      if (previousRide instanceof LivingEntity oldMount && data != null && oldMount.isAlive()) {
         UUID uuid = oldMount.getUUID();
         if (!data.contains(CompanionKind.MOUNT, uuid)) {
            return false;
         }

         data.rememberPrevious(CompanionKind.MOUNT, uuid);
         CompanionManager.sendAway(oldMount, player, data);
         data.clearDeployed(CompanionKind.MOUNT, uuid);
         CompanionManager.save(player, data);
         CompanionManager.syncToClient(player, CompanionKind.MOUNT);
         return true;
      } else {
         return false;
      }
   }
}
