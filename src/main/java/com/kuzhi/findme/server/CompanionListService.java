package com.kuzhi.findme.server;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.SavedPosition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

final class CompanionListService {
   private CompanionListService() {
   }

   static boolean selectWheelIndex(PlayerCompanionData data, CompanionKind kind, int wheelIndex) {
      Optional<UUID> maybeUuid = data.wheelUuidAt(kind, wheelIndex);
      return maybeUuid.isPresent() && data.setActiveUuid(kind, maybeUuid.get());
   }

   static int list(ServerPlayer player, CompanionKind kind) {
      PlayerCompanionData data = CompanionManager.data(player);
      List<UUID> uuids = data.list(kind);
      if (uuids.isEmpty()) {
         CompanionManager.tell(player, "message.find_me.no_registered", ChatFormatting.YELLOW, CompanionManager.label(kind));
         return 0;
      }

      int active = data.activeIndex(kind);
      CompanionManager.tell(player, "message.find_me.registered_header", ChatFormatting.AQUA, CompanionManager.label(kind));

      for (int i = 0; i < uuids.size(); i++) {
         UUID uuid = uuids.get(i);
         Entity entity = CompanionManager.findLoadedEntity(player.level().getServer(), data, uuid).orElse(null);
         String name = entity == null ? uuid.toString() : entity.getDisplayName().getString();
         String marker = i == active ? "* " : "  ";
         MutableComponent state = entity == null
            ? Component.translatable("message.find_me.state_unloaded")
            : Component.translatable(entity.isAlive() ? "message.find_me.state_loaded" : "message.find_me.state_dead");
         player.sendSystemMessage(
            Component.translatable("message.find_me.list_entry", new Object[]{marker, i, name, state})
               .withStyle(i == active ? ChatFormatting.GREEN : ChatFormatting.GRAY)
         );
      }

      return uuids.size();
   }

   static int select(ServerPlayer player, CompanionKind kind, int index) {
      PlayerCompanionData data = CompanionManager.data(player);
      if (!data.setActiveIndex(kind, index)) {
         CompanionManager.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionManager.label(kind));
         return 0;
      } else {
         CompanionManager.save(player, data);
         CompanionManager.syncToClient(player, kind);
         CompanionManager.tell(player, "message.find_me.selected_index", ChatFormatting.GREEN, CompanionManager.label(kind), index);
         return 1;
      }
   }

   static int remove(ServerPlayer player, CompanionKind kind, int index) {
      PlayerCompanionData data = CompanionManager.data(player);
      Optional<UUID> uuid = data.wheelUuidAt(kind, index);
      if (uuid.isEmpty()) {
         CompanionManager.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionManager.label(kind));
         return 0;
      } else {
         releaseAndRemove(player, data, kind, index, uuid.get());
         return 1;
      }
   }

   static int assignWheelSlot(ServerPlayer player, CompanionKind kind, int index, int slot) {
      PlayerCompanionData data = CompanionManager.data(player);
      if (!data.setWheelSlot(kind, index, slot)) {
         CompanionManager.tell(player, "message.find_me.invalid_slot", ChatFormatting.RED, CompanionManager.label(kind));
         return 0;
      } else {
         CompanionManager.save(player, data);
         CompanionManager.syncToClient(player, kind);
         CompanionManager.tell(player, "message.find_me.assigned_slot", ChatFormatting.GREEN, CompanionManager.label(kind), index, slot + 1);
         return 1;
      }
   }

   static void moveToOther(ServerPlayer player, PlayerCompanionData data, CompanionKind fromKind, int index) {
      Optional<UUID> maybeUuid = data.wheelUuidAt(fromKind, index);
      if (maybeUuid.isEmpty()) {
         CompanionManager.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionManager.label(fromKind));
      } else {
         CompanionKind targetKind = CompanionManager.other(fromKind);
         UUID uuid = maybeUuid.get();
         if (CompanionManager.isStoragePending(uuid)) {
            CompanionSummonLineService.showBusy(player, data, fromKind, uuid);
         } else if (isEntryActiveInWorld(player, data, fromKind, uuid)) {
            CompanionManager.tell(player, "message.find_me.cannot_move_active", ChatFormatting.RED);
         } else {
            if (fromKind == CompanionKind.MOUNT) {
               data.markMountEligible(uuid);
            }

            data.add(targetKind, uuid);
            CompanionManager.save(player, data);
            CompanionManager.syncToClient(player, fromKind);
            CompanionManager.syncToClient(player, targetKind);
            CompanionManager.tell(player, "message.find_me.moved_list", ChatFormatting.GREEN, CompanionManager.label(targetKind));
         }
      }
   }

   static void releaseAndRemove(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int index, UUID uuid) {
      if (CompanionManager.isStoragePending(uuid)) {
         CompanionSummonLineService.showBusy(player, data, kind, uuid);
      } else {
         BlockPos releasePos = CompanionSpawnPlacementService.findSummonSpot(player, kind, CompanionMoveType.WALK);
         Entity entity = CompanionManager.locateEntity(player.level().getServer(), data, uuid)
            .orElseGet(
               () -> CompanionEntityTransferService.restoreStoredEntity(player, data, uuid, releasePos, player.getYRot(), player.getXRot()).orElse(null)
            );
         if (entity == null) {
            data.remove(uuid);
            CompanionManager.save(player, data);
            CompanionManager.syncToClient(player, CompanionKind.MOUNT);
            CompanionManager.syncToClient(player, CompanionKind.COMPANION);
            CompanionManager.syncDeadToClient(player);
            CompanionManager.tell(player, "message.find_me.released", ChatFormatting.GRAY, CompanionManager.label(kind), index);
         } else {
            if (entity instanceof LivingEntity living && living.isAlive()) {
               if (living.level() != player.level()) {
                  living = CompanionEntityTransferService.moveEntityTo(living, player.level(), releasePos, player.getYRot(), player.getXRot());
               }

               LivingEntity var9 = CompanionEntityTransferService.moveEntityTo(living, player.level(), releasePos, player.getYRot(), player.getXRot());
               if (player.getVehicle() == var9) {
                  player.stopRiding();
               }

               if (var9.isPassenger()) {
                  var9.stopRiding();
               }

               var9.invulnerableTime = Math.max(var9.invulnerableTime, Config.postTeleportInvulnerabilityTicks);
               var9.fallDistance = 0.0F;
               data.setLastKnownPosition(uuid, SavedPosition.of(var9.level(), var9.getX(), var9.getY(), var9.getZ(), var9.getYRot(), var9.getXRot()));
            }

            data.remove(uuid);
            String name = entity instanceof LivingEntity living ? living.getDisplayName().getString() : uuid.toString();
            data.ignoreAutoBind(uuid, name);
            CompanionManager.save(player, data);
            CompanionManager.syncToClient(player, CompanionKind.MOUNT);
            CompanionManager.syncToClient(player, CompanionKind.COMPANION);
            CompanionManager.tell(player, "message.find_me.released", ChatFormatting.GRAY, CompanionManager.label(kind), index);
         }
      }
   }

   static void rename(ServerPlayer player, CompanionKind kind, int index, String name) {
      PlayerCompanionData data = CompanionManager.data(player);
      Optional<UUID> uuid = data.wheelUuidAt(kind, index);
      if (uuid.isEmpty()) {
         CompanionManager.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionManager.label(kind));
      } else {
         String trimmed = name == null ? "" : name.trim();
         if (trimmed.length() > 48) {
            trimmed = trimmed.substring(0, 48);
         }

         data.setDisplayName(uuid.get(), trimmed);
         CompanionManager.save(player, data);
         CompanionManager.syncToClient(player, kind);
         CompanionManager.tell(player, "message.find_me.renamed", ChatFormatting.GREEN);
      }
   }

   private static boolean isEntryActiveInWorld(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid) {
      Entity vehicle = player.getVehicle();
      return vehicle != null && vehicle.getUUID().equals(uuid) ? true : data.isDeployed(kind, uuid) || data.isDeployed(CompanionManager.other(kind), uuid);
   }
}
