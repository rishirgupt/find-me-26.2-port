package com.kuzhi.findme.server;

import com.kuzhi.findme.common.CompanionKind;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

final class CompanionBindingService {
   private CompanionBindingService() {
   }

   static CompanionBindingService.BindingCheck checkManualBinding(ServerPlayer player, LivingEntity target) {
      if (target != player && target.isAlive() && player.level() == target.level()) {
         PlayerCompanionData data = CompanionManager.data(player);
         if (data.contains(target.getUUID())) {
            return CompanionBindingService.BindingCheck.failure("message.find_me.contract_already_bound", ChatFormatting.YELLOW, target.getDisplayName());
         }

         Optional<CompanionKind> classified = CompanionRegistrationService.manualBindingKind(player, target);
         return classified.isEmpty()
            ? CompanionBindingService.BindingCheck.failure("message.find_me.contract_not_allowed", ChatFormatting.RED, target.getDisplayName())
            : CompanionBindingService.BindingCheck.allowed(classified.get());
      } else {
         return CompanionBindingService.BindingCheck.failure("message.find_me.contract_invalid_target", ChatFormatting.RED);
      }
   }

   static boolean bindAndStore(ServerPlayer player, LivingEntity target, CompanionKind kind) {
      if (!CompanionRegistrationService.registerContractedForStorage(player, target, kind)) {
         return false;
      }

      storeBoundTarget(player, kind, target);
      return true;
   }

   private static void storeBoundTarget(ServerPlayer player, CompanionKind kind, LivingEntity target) {
      if (player.getVehicle() != target && !target.hasPassenger(player)) {
         PlayerCompanionData data = CompanionManager.data(player);
         CompanionManager.storeAndDiscard(player, data, target);
         data.clearDeployed(kind, target.getUUID());
         CompanionManager.save(player, data);
         CompanionManager.syncToClient(player, CompanionKind.MOUNT);
         CompanionManager.syncToClient(player, CompanionKind.COMPANION);
      }
   }

   record BindingCheck(boolean allowed, CompanionKind kind, String messageKey, ChatFormatting color, Object[] args) {
      static CompanionBindingService.BindingCheck allowed(CompanionKind kind) {
         return new CompanionBindingService.BindingCheck(true, kind, "", ChatFormatting.WHITE, new Object[0]);
      }

      static CompanionBindingService.BindingCheck failure(String messageKey, ChatFormatting color, Object... args) {
         return new CompanionBindingService.BindingCheck(false, null, messageKey, color, args);
      }
   }
}
