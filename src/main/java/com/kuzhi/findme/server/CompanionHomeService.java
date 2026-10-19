package com.kuzhi.findme.server;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.SavedPosition;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

final class CompanionHomeService {
   private CompanionHomeService() {
   }

   static int setActiveHome(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
      Optional<UUID> maybeUuid = data.active(kind);
      if (maybeUuid.isEmpty()) {
         CompanionSummonLineService.showNoRegistered(player, kind);
         return 0;
      } else {
         return setHome(player, data, kind, maybeUuid.get());
      }
   }

   static int setIndexHome(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int index) {
      Optional<UUID> maybeUuid = data.wheelUuidAt(kind, index);
      if (maybeUuid.isEmpty()) {
         CompanionManager.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionManager.label(kind));
         return 0;
      } else {
         return setHome(player, data, kind, maybeUuid.get());
      }
   }

   static int clearActiveHome(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
      Optional<UUID> maybeUuid = data.active(kind);
      if (maybeUuid.isEmpty()) {
         CompanionSummonLineService.showNoRegistered(player, kind);
         return 0;
      } else {
         return clearHome(player, data, kind, maybeUuid.get());
      }
   }

   static int clearIndexHome(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int index) {
      Optional<UUID> maybeUuid = data.wheelUuidAt(kind, index);
      if (maybeUuid.isEmpty()) {
         CompanionManager.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionManager.label(kind));
         return 0;
      } else {
         return clearHome(player, data, kind, maybeUuid.get());
      }
   }

   static int setIndexHomeAtHouse(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int index, ServerLevel houseLevel, BlockPos housePos) {
      Optional<UUID> maybeUuid = data.wheelUuidAt(kind, index);
      if (maybeUuid.isEmpty()) {
         CompanionManager.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionManager.label(kind));
         return 0;
      } else {
         UUID uuid = maybeUuid.get();
         SavedPosition home = SavedPosition.of(houseLevel, housePos.getX() + 0.5, housePos.getY() + 1.0, housePos.getZ() + 0.5, player.getYRot(), 0.0F);
         SavedPosition source = SavedPosition.of(houseLevel, housePos.getX(), housePos.getY(), housePos.getZ(), 0.0F, 0.0F);
         data.setHomePosition(uuid, home);
         data.setHomeNestBlock(uuid, source);
         CompanionManager.save(player, data);
         CompanionManager.syncToClient(player, kind);
         CompanionSummonLineService.showHomeSet(player, displayName(data, uuid));
         return 1;
      }
   }

   static int sendActiveHome(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
      return sendActiveToSavedPosition(player, data, kind, true);
   }

   static int sendIndexHome(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int index) {
      Optional<UUID> maybeUuid = data.wheelUuidAt(kind, index);
      if (maybeUuid.isEmpty()) {
         CompanionManager.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionManager.label(kind));
         return 0;
      } else {
         return sendToSavedPosition(player, data, kind, maybeUuid.get(), true);
      }
   }

   static int returnActive(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
      return sendActiveToSavedPosition(player, data, kind, false);
   }

   private static int sendActiveToSavedPosition(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, boolean requireHome) {
      Optional<UUID> maybeUuid = data.active(kind);
      if (maybeUuid.isEmpty()) {
         CompanionManager.tell(player, "message.find_me.no_registered", ChatFormatting.YELLOW, CompanionManager.label(kind));
         return 0;
      } else {
         return sendToSavedPosition(player, data, kind, maybeUuid.get(), requireHome);
      }
   }

   private static int setHome(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid) {
      SavedPosition home = SavedPosition.of(player.level(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
      data.setHomePosition(uuid, home);
      CompanionManager.save(player, data);
      CompanionManager.syncToClient(player, kind);
      CompanionSummonLineService.showHomeSet(player, displayName(data, uuid));
      return 1;
   }

   private static int clearHome(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid) {
      data.clearHomePosition(uuid);
      CompanionManager.save(player, data);
      CompanionManager.syncToClient(player, kind);
      CompanionSummonLineService.showHomeCleared(player, displayName(data, uuid));
      return 1;
   }

   static int clearHomesForHouse(ServerPlayer player, PlayerCompanionData data, SavedPosition source) {
      int removed = data.clearHomesForNestBlock(source);
      if (removed > 0) {
         CompanionManager.save(player, data);
         CompanionManager.syncToClient(player, CompanionKind.MOUNT);
         CompanionManager.syncToClient(player, CompanionKind.COMPANION);
      }

      return removed;
   }

   private static int sendToSavedPosition(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid, boolean requireHome) {
      if (CompanionManager.isStoragePending(uuid)) {
         CompanionSummonLineService.showBusy(player, data, kind, uuid);
         return 0;
      }

      Optional<SavedPosition> maybeTarget = data.homePosition(uuid);
      boolean usingHome = maybeTarget.isPresent();
      if (maybeTarget.isEmpty() && !requireHome) {
         maybeTarget = data.origin(uuid);
      }

      if (maybeTarget.isEmpty()) {
         CompanionManager.tell(
            player, requireHome ? "message.find_me.no_home" : "message.find_me.no_origin", ChatFormatting.YELLOW, CompanionManager.label(kind)
         );
         return 0;
      }

      SavedPosition target = maybeTarget.get();
      ServerLevel destination = player.level().getServer().getLevel(target.dimension());
      if (destination == null) {
         CompanionManager.tell(player, "message.find_me.dimension_unavailable", ChatFormatting.YELLOW);
         destination = player.level();
      }

      ServerLevel restoreDestination = destination;
      BlockPos safe = CompanionPlacementFinder.findSafe(destination, target.blockPos()).orElse(target.blockPos());
      Entity entity = CompanionManager.locateEntity(player.level().getServer(), data, uuid)
         .orElseGet(
            () -> CompanionEntityTransferService.restoreStoredEntity(restoreDestination, player, data, uuid, safe, target.yRot(), target.xRot()).orElse(null)
         );
      if (entity instanceof LivingEntity living && living.isAlive()) {
         if (player.getVehicle() == living) {
            player.stopRiding();
         }

         if (living.isPassenger()) {
            living.stopRiding();
         }

         LivingEntity var13 = CompanionEntityTransferService.moveEntityTo(living, destination, safe, target.yRot(), target.xRot());
         data.clearDeployed(kind, var13.getUUID());
         data.setLastKnownPosition(var13.getUUID(), SavedPosition.of(var13.level(), var13.getX(), var13.getY(), var13.getZ(), var13.getYRot(), var13.getXRot()));
         data.setReadyAt(kind, player.level().getGameTime() + Config.returnCooldownTicks);
         CompanionManager.save(player, data);
         CompanionManager.syncToClient(player, kind);
         CompanionManager.tell(player, usingHome ? "message.find_me.returned_home" : "message.find_me.returned", ChatFormatting.GRAY, var13.getDisplayName());
         return 1;
      } else {
         CompanionManager.tell(player, "message.find_me.unavailable", ChatFormatting.RED, CompanionManager.label(kind));
         return 0;
      }
   }

   private static String displayName(PlayerCompanionData data, UUID uuid) {
      return data.displayName(uuid)
         .orElseGet(() -> data.storedEntity(uuid).map(tag -> CompanionManager.storedEntityName(tag, uuid)).orElse(uuid.toString().substring(0, 8)));
   }
}
