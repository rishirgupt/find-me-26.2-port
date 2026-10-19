package com.kuzhi.findme.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

final class VehicleCompatibilityService {
   private VehicleCompatibilityService() {
   }

   static boolean tryBoardVehicle(ServerPlayer player, Entity target, Entity previousRide) {
      if (target == null || target.isRemoved()) {
         restorePreviousRide(player, previousRide);
         return false;
      }

      if (isRiding(player, target)) {
         return true;
      }

      if (tryForcedRide(player, target)) {
         return true;
      }

      if (tryInteractionRide(player, target)) {
         return true;
      }

      if (tryForcedRide(player, target)) {
         return true;
      }

      restorePreviousRide(player, previousRide);
      return false;
   }

   private static boolean tryForcedRide(ServerPlayer player, Entity target) {
      if (target.isRemoved()) {
         return false;
      }

      try {
         return player.startRiding(target, true, true) && isRiding(player, target);
      } catch (RuntimeException ignored) {
         return false;
      }
   }

   private static boolean tryInteractionRide(ServerPlayer player, Entity target) {
      if (target.isRemoved()) {
         return false;
      }

      try {
         target.interact(player, InteractionHand.MAIN_HAND, target.position());
         return isRiding(player, target);
      } catch (RuntimeException ignored) {
         return false;
      }
   }

   private static void restorePreviousRide(ServerPlayer player, Entity previousRide) {
      if (previousRide != null && !previousRide.isRemoved() && !isRiding(player, previousRide)) {
         tryForcedRide(player, previousRide);
      }
   }

   private static boolean isRiding(ServerPlayer player, Entity target) {
      for (Entity vehicle = player.getVehicle(); vehicle != null; vehicle = vehicle.getVehicle()) {
         if (vehicle == target || vehicle.getUUID().equals(target.getUUID())) {
            return true;
         }
      }

      return false;
   }
}
