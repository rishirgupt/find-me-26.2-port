package com.kuzhi.findme.server;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionMoveType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

final class CompanionCinematicPositionService {
   private CompanionCinematicPositionService() {
   }

   static void stabilizeAirRescueMount(LivingEntity mount, ServerPlayer player) {
      double minY = player.getY() - 1.2;
      if (player.level() instanceof ServerLevel serverLevel && CompanionCinematicLandingService.hasReliableLandingBelow(serverLevel, player)) {
         minY = Math.max(minY, CompanionCinematicLandingService.predictedLanding(serverLevel, player).getY() + 1.2);
      }

      mount.snapTo(player.getX(), minY, player.getZ(), player.getYRot(), 0.0F);
      mount.setPos(player.getX(), minY, player.getZ());
      CompanionCinematicOrientationHelper.faceYaw(mount, player.getYRot());
      mount.setDeltaMovement(Vec3.ZERO);
      mount.fallDistance = 0.0F;
      player.setDeltaMovement(player.getDeltaMovement().x, 0.0, player.getDeltaMovement().z);
      player.fallDistance = 0.0F;
      player.hurtMarked = true;
   }

   static double directFlyingCatchY(ServerPlayer player) {
      if (player.level() instanceof ServerLevel serverLevel) {
         if (!CompanionCinematicLandingService.hasReliableLandingBelow(serverLevel, player)) {
            return player.getY() - 1.2;
         }

         BlockPos landing = CompanionCinematicLandingService.rescueAnchor(serverLevel, player);
         return CompanionCinematicLandingService.shouldDirectFlyingRescue(player)
            ? landing.getY()
            : landing.getY() + CompanionCinematicLandingService.flyingCatchHeight(player);
      } else {
         return player.getY() - 1.2;
      }
   }

   static void placeFlyingRescueAtLanding(ServerPlayer player, LivingEntity mount, double catchY) {
      if (player.level() instanceof ServerLevel serverLevel) {
         BlockPos landing = CompanionCinematicLandingService.rescueAnchor(serverLevel, player);
         double x = landing.getX() + 0.5;
         double z = landing.getZ() + 0.5;
         Vec3 look = player.position().subtract(x, catchY, z);
         float yaw = look.lengthSqr() < 0.001 ? player.getYRot() : CompanionCinematicOrientationHelper.yawTowardStable(mount, look);
         mount.snapTo(x, catchY, z, yaw, 0.0F);
         mount.setPos(x, catchY, z);
         CompanionCinematicOrientationHelper.faceYaw(mount, yaw);
         mount.setXRot(0.0F);
         mount.setDeltaMovement(Vec3.ZERO);
         mount.setNoGravity(true);
         mount.fallDistance = 0.0F;
         mount.invulnerableTime = Math.max(mount.invulnerableTime, Config.postTeleportInvulnerabilityTicks);
         mount.hurtMarked = true;
         CompanionManager.forceFlyingAnimationPose(mount);
         if (mount instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
         }
      }
   }

   static void keepFlyingRescueAboveLanding(PendingMountCinematic cinematic, LivingEntity mount, ServerPlayer player) {
      if (cinematic.mode().isFlyingRescue() && player.level() instanceof ServerLevel serverLevel) {
         double minY = CompanionCinematicLandingService.hasReliableLandingBelow(serverLevel, player)
            ? CompanionCinematicLandingService.predictedLanding(serverLevel, player).getY() + 1.2
            : player.getY() - 1.2;
         if (mount.getY() < minY) {
            mount.snapTo(mount.getX(), minY, mount.getZ(), mount.getYRot(), mount.getXRot());
            mount.setPos(mount.getX(), minY, mount.getZ());
            mount.setDeltaMovement(mount.getDeltaMovement().x, Math.max(0.0, mount.getDeltaMovement().y), mount.getDeltaMovement().z);
            mount.fallDistance = 0.0F;
            mount.hurtMarked = true;
         }
      }
   }

   static void lockMountAtFlyingWait(PendingMountCinematic cinematic, LivingEntity mount, ServerPlayer player) {
      if (player.level() instanceof ServerLevel level && !Double.isNaN(cinematic.catchY())) {
         BlockPos landing = CompanionCinematicLandingService.rescueAnchor(level, player);
         Vec3 waitPos = new Vec3(landing.getX() + 0.5, cinematic.catchY(), landing.getZ() + 0.5);
         mount.snapTo(waitPos.x, waitPos.y, waitPos.z, mount.getYRot(), mount.getXRot());
         mount.setPos(waitPos.x, waitPos.y, waitPos.z);
         mount.setDeltaMovement(Vec3.ZERO);
         mount.setNoGravity(true);
         mount.fallDistance = 0.0F;
         mount.hurtMarked = true;
         cinematic.rememberPosition(mount.position());
         CompanionManager.forceFlyingAnimationPose(mount);
         if (mount instanceof Mob mob) {
            mob.setNoAi(!CompanionManager.keepsFlyingAnimationWithActiveAi(mount));
            mob.getNavigation().stop();
            mob.setTarget(null);
         }
      } else {
         mount.setDeltaMovement(Vec3.ZERO);
      }
   }

   static void lockMountAtGroundWait(PendingMountCinematic cinematic, LivingEntity mount, ServerPlayer player) {
      if (!cinematic.waitLocked()) {
         Vec3 waitPos;
         if (cinematic.moveType() == CompanionMoveType.WALK && player.level() instanceof ServerLevel serverLevel) {
            waitPos = Vec3.atBottomCenterOf(CompanionCinematicLandingService.rescueAnchor(serverLevel, player))
               .add(0.0, CompanionCinematicLandingService.rescueGroundYOffset(cinematic), 0.0);
         } else {
            waitPos = mount.position();
         }

         if (cinematic.moveType() == CompanionMoveType.WALK) {
            double groundY = CompanionCinematicLandingService.walkGroundY(mount.level(), waitPos.x, waitPos.y, waitPos.z, waitPos.y);
            waitPos = new Vec3(waitPos.x, groundY + CompanionCinematicLandingService.rescueGroundYOffset(cinematic), waitPos.z);
         }

         cinematic.lockWait(waitPos);
      }

      Vec3 waitPos;
      if ((waitPos = cinematic.waitPosition()) != null && mount.position().distanceToSqr(waitPos) > 0.0025) {
         mount.setPos(waitPos.x, waitPos.y, waitPos.z);
      } else if (cinematic.moveType() == CompanionMoveType.WALK) {
         double groundY = CompanionCinematicLandingService.walkGroundY(mount.level(), mount.getX(), mount.getY(), mount.getZ(), mount.getY());
         if (Math.abs((groundY = groundY + CompanionCinematicLandingService.rescueGroundYOffset(cinematic)) - mount.getY()) > 0.001) {
            mount.setPos(mount.getX(), groundY, mount.getZ());
         }
      }

      mount.setDeltaMovement(Vec3.ZERO);
      mount.setNoGravity(cinematic.moveType() != CompanionMoveType.WALK);
      mount.fallDistance = 0.0F;
      mount.hurtMarked = true;
      cinematic.rememberPosition(mount.position());
      if (mount instanceof Mob mob) {
         mob.setNoAi(!CompanionManager.keepsFlyingAnimationWithActiveAi(mount));
         mob.getNavigation().stop();
         mob.setTarget(null);
      }
   }
}
