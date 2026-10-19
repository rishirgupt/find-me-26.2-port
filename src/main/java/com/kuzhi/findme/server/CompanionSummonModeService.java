package com.kuzhi.findme.server;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

final class CompanionSummonModeService {
   private CompanionSummonModeService() {
   }

   static MountCinematicMode modeFor(
      CompanionKind kind, CompanionMoveType moveType, boolean fallingRescue, boolean switchingMount, boolean transitionFromFlyingMount
   ) {
      if (kind != CompanionKind.MOUNT) {
         return MountCinematicMode.NORMAL_SUMMON;
      }

      CompanionMoveType cinematicMoveType = landPresentationMoveType(moveType);
      if (transitionFromFlyingMount) {
         return switch (cinematicMoveType) {
            case FLY -> MountCinematicMode.AIR_TO_FLY_SWITCH;
            case WALK, SWIM, COMPANION -> MountCinematicMode.AIR_TO_WALK_SWITCH;
         };
      } else if (fallingRescue) {
         return switch (cinematicMoveType) {
            case FLY -> MountCinematicMode.FALL_RESCUE_FLY;
            case WALK, SWIM, COMPANION -> MountCinematicMode.FALL_RESCUE_WALK;
         };
      } else {
         return switchingMount ? MountCinematicMode.MOUNT_SWITCH : MountCinematicMode.NORMAL_SUMMON;
      }
   }

   static CompanionMoveType landPresentationMoveType(CompanionMoveType moveType) {
      return moveType == CompanionMoveType.SWIM ? CompanionMoveType.WALK : moveType;
   }

   static boolean isAirborneGroundRescue(ServerPlayer player, CompanionKind kind, CompanionMoveType moveType) {
      return kind == CompanionKind.MOUNT && player.getVehicle() == null && isFallingFar(player) && isGroundOrWater(moveType);
   }

   static boolean isAirborneGroundVehicleRescue(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, CompanionMoveType moveType) {
      Entity vehicle = player.getVehicle();
      return kind == CompanionKind.MOUNT && vehicle != null && !isRegisteredBiologicalMount(data, vehicle) && isFallingFar(player) && isGroundOrWater(moveType);
   }

   static boolean isRegisteredBiologicalMount(PlayerCompanionData data, Entity entity) {
      return entity instanceof LivingEntity && data.contains(CompanionKind.MOUNT, entity.getUUID());
   }

   static boolean isAirborneGroundMountSwitch(ServerPlayer player, CompanionKind kind, CompanionMoveType moveType) {
      Entity vehicle = player.getVehicle();
      return kind == CompanionKind.MOUNT
         && vehicle != null
         && isFallingFar(player)
         && CompanionManager.moveType(vehicle, CompanionKind.MOUNT) == CompanionMoveType.FLY
         && isGroundOrWater(moveType);
   }

   static boolean isAirborneGroundCompanionSummon(ServerPlayer player, CompanionKind kind, CompanionMoveType moveType) {
      Entity vehicle = player.getVehicle();
      return kind == CompanionKind.COMPANION
         && vehicle != null
         && isFallingFar(player)
         && CompanionManager.moveType(vehicle, CompanionKind.MOUNT) == CompanionMoveType.FLY
         && isGroundOrWater(moveType);
   }

   private static boolean isFallingFar(ServerPlayer player) {
      return !player.onGround() && CompanionCinematicLandingService.distanceToGround(player) > 6.0;
   }

   private static boolean isGroundOrWater(CompanionMoveType moveType) {
      return moveType == CompanionMoveType.WALK || moveType == CompanionMoveType.SWIM;
   }
}
