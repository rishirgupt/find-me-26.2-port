package com.kuzhi.findme.server;

import com.kuzhi.findme.common.CompanionKind;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

final class CompanionSummonFailureService {
   private CompanionSummonFailureService() {
   }

   static void unavailable(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, boolean saveBeforeSync) {
      if (saveBeforeSync) {
         CompanionManager.save(player, data);
      }

      CompanionManager.syncToClient(player, kind);
      CompanionSummonLineService.showUnavailable(player, data, kind);
   }

   static void restoredEntityFailed(
      ServerPlayer player, PlayerCompanionData data, CompanionKind kind, LivingEntity living, CompanionSummonFailureService.Reason reason
   ) {
      CompanionManager.storeAndDiscard(player, data, living);
      CompanionManager.save(player, data);
      CompanionManager.syncToClient(player, kind);
      if (reason == CompanionSummonFailureService.Reason.TOO_HIGH) {
         CompanionSummonLineService.showTooHigh(player, data, kind, living);
      } else {
         CompanionSummonLineService.showNeedOpenSpace(player, data, kind, living);
      }
   }

   enum Reason {
      NEED_OPEN_SPACE,
      TOO_HIGH;
   }
}
