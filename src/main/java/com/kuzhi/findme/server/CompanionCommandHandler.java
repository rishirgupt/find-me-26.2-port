package com.kuzhi.findme.server;

import com.kuzhi.findme.common.CompanionAction;
import com.kuzhi.findme.common.CompanionKind;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

final class CompanionCommandHandler {
   private CompanionCommandHandler() {
   }

   static void handle(ServerPlayer player, CompanionKind kind, CompanionAction action, int targetEntityId) {
      PlayerCompanionData data = CompanionManager.data(player);
      switch (action) {
         case SUMMON:
            CompanionSummonService.summonActive(player, data, kind);
            break;
         case RETURN_HOME:
            CompanionCollectionService.collectActive(player, data, kind);
            break;
         case NEXT:
            selectNext(player, data, kind);
            break;
         case PREVIOUS:
            selectPrevious(player, data, kind);
            break;
         case SELECT:
            select(player, data, kind, targetEntityId, false);
            break;
         case SELECT_SUMMON:
            select(player, data, kind, targetEntityId, true);
            break;
         case RECALL:
            recall(player, data, kind, targetEntityId);
            break;
         case REMOVE:
            remove(player, data, kind, targetEntityId);
            break;
         case MOVE_OTHER:
            CompanionListService.moveToOther(player, data, kind, targetEntityId);
            break;
         case ASSIGN_SLOT:
            CompanionListService.assignWheelSlot(player, kind, targetEntityId % 1000, targetEntityId / 1000);
            break;
         case REORDER:
            reorder(player, data, kind, targetEntityId);
            break;
         case IGNORE_SYNC:
            CompanionManager.syncIgnoredToClient(player);
            break;
         case IGNORE_REMOVE:
            removeIgnored(player, targetEntityId);
            break;
         case BLACKLIST_SYNC:
            CompanionManager.syncBlacklistToClient(player);
            break;
         case BLACKLIST_ADD:
            addBlacklist(player, kind, targetEntityId);
            break;
         case BLACKLIST_REMOVE:
            removeBlacklist(player, targetEntityId);
            break;
         case SET_HOME:
            CompanionHomeService.setIndexHome(player, data, kind, targetEntityId);
            break;
         case GO_HOME:
            CompanionHomeService.sendIndexHome(player, data, kind, targetEntityId);
            break;
         case CLEAR_HOME:
            CompanionHomeService.clearIndexHome(player, data, kind, targetEntityId);
            break;
         case DEAD_SYNC:
            syncDead(player, data);
            break;
         case DEAD_REMOVE:
            removeDead(player, data, kind, targetEntityId);
            break;
         case SYNC:
            CompanionRegistrationService.sweepDeadEntriesAndSync(player, data, kind);
            CompanionManager.syncToClient(player, kind);
      }
   }

   private static void selectNext(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
      data.cycle(kind, 1);
      CompanionManager.save(player, data);
      data.active(kind)
         .ifPresentOrElse(
            uuid -> CompanionManager.tell(player, "message.find_me.selected_next", ChatFormatting.GRAY, CompanionManager.label(kind)),
            () -> CompanionManager.tell(player, "message.find_me.no_registered", ChatFormatting.YELLOW, CompanionManager.label(kind))
         );
   }

   private static void selectPrevious(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
      data.previous(kind).ifPresentOrElse(uuid -> {
         data.add(kind, uuid);
         CompanionManager.save(player, data);
         CompanionManager.tell(player, "message.find_me.previous_selected", ChatFormatting.GRAY, CompanionManager.label(kind));
      }, () -> CompanionManager.tell(player, "message.find_me.no_previous", ChatFormatting.YELLOW, CompanionManager.label(kind)));
   }

   private static void select(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int targetEntityId, boolean summon) {
      if (!CompanionListService.selectWheelIndex(data, kind, targetEntityId)) {
         CompanionManager.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionManager.label(kind));
      } else {
         CompanionManager.save(player, data);
         if (summon) {
            CompanionSummonService.summonActive(player, data, kind);
         }

         CompanionManager.syncToClient(player, kind);
      }
   }

   private static void remove(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int targetEntityId) {
      Optional<UUID> uuid = data.wheelUuidAt(kind, targetEntityId);
      if (uuid.isPresent()) {
         CompanionListService.releaseAndRemove(player, data, kind, targetEntityId, uuid.get());
      } else {
         CompanionManager.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionManager.label(kind));
      }
   }

   private static void recall(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int targetEntityId) {
      if (!CompanionCollectionService.collectIndex(player, data, kind, targetEntityId)) {
         CompanionManager.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionManager.label(kind));
      }
   }

   private static void reorder(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int targetEntityId) {
      int from = targetEntityId % 1000;
      int to = targetEntityId / 1000;
      if (data.reorder(kind, from, to)) {
         CompanionManager.save(player, data);
         CompanionManager.syncToClient(player, kind);
      }
   }

   private static void removeIgnored(ServerPlayer player, int targetEntityId) {
      if (CompanionManager.removeIgnored(player, targetEntityId) != 0) {
         CompanionManager.syncIgnoredToClient(player);
      }
   }

   private static void addBlacklist(ServerPlayer player, CompanionKind kind, int targetEntityId) {
      if (CompanionManager.blacklistEntryType(player, kind, targetEntityId) != 0) {
         CompanionManager.syncBlacklistToClient(player);
      }
   }

   private static void removeBlacklist(ServerPlayer player, int targetEntityId) {
      if (CompanionManager.removeBlacklist(player, targetEntityId) != 0) {
         CompanionManager.syncBlacklistToClient(player);
      }
   }

   private static void syncDead(ServerPlayer player, PlayerCompanionData data) {
      CompanionRegistrationService.sweepDeadEntriesAndSync(player, data, CompanionKind.MOUNT);
      CompanionRegistrationService.sweepDeadEntriesAndSync(player, data, CompanionKind.COMPANION);
      CompanionManager.syncDeadToClient(player);
   }

   private static void removeDead(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int targetEntityId) {
      if (data.removeDeadAt(targetEntityId)) {
         CompanionManager.save(player, data);
         CompanionManager.syncToClient(player, CompanionKind.MOUNT);
         CompanionManager.syncToClient(player, CompanionKind.COMPANION);
         CompanionManager.syncDeadToClient(player);
      } else {
         CompanionManager.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionManager.label(kind));
      }
   }
}
