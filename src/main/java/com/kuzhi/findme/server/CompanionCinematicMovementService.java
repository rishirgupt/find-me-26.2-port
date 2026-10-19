package com.kuzhi.findme.server;

import com.kuzhi.findme.common.CompanionMoveType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

final class CompanionCinematicMovementService {
   private CompanionCinematicMovementService() {
   }

   static void moveTowardCinematicTarget(PendingMountCinematic cinematic, LivingEntity mount, ServerPlayer player, Vec3 target, double distance) {
      double speed = CompanionCinematicSpeedService.cinematicSpeed(cinematic, mount, player, distance);
      moveTowardTarget(cinematic, mount, player, target, Math.min(speed, distance), speed, false);
   }

   static void moveTowardMountContact(PendingMountCinematic cinematic, LivingEntity mount, ServerPlayer player, Entity currentVehicle) {
      Vec3 target = mountContactTarget(cinematic, player, currentVehicle);
      double distance = mount.position().distanceTo(target);
      if (distance < 0.15) {
         snapWalkMountToGround(cinematic, mount);
         mount.setDeltaMovement(Vec3.ZERO);
      } else {
         double speed = Math.min(CompanionCinematicSpeedService.cinematicSpeed(cinematic, mount, player, distance), distance);
         moveTowardTarget(cinematic, mount, player, target, speed, speed, true);
      }
   }

   private static void moveTowardTarget(
      PendingMountCinematic cinematic, LivingEntity mount, ServerPlayer player, Vec3 target, double stepDistance, double speed, boolean forceFlyingPose
   ) {
      Vec3 direction = target.subtract(mount.position()).normalize();
      float lookYaw = CompanionCinematicOrientationHelper.yawTowardStable(mount, direction);
      if (cinematic.moveType() == CompanionMoveType.FLY) {
         Vec3 next = mount.position().add(direction.scale(stepDistance));
         float pitch = cinematic.mode().isFlyingRescue() ? CompanionCinematicOrientationHelper.pitchToward(direction) : mount.getXRot();
         mount.snapTo(next.x, next.y, next.z, lookYaw, pitch);
         mount.setPos(next.x, next.y, next.z);
         mount.setXRot(pitch);
         if (forceFlyingPose) {
            CompanionManager.forceFlyingAnimationPose(mount);
         }
      } else if (cinematic.moveType() == CompanionMoveType.WALK) {
         walkCinematicStep(cinematic, mount, direction, stepDistance, target, lookYaw);
      } else {
         mount.move(MoverType.SELF, direction.scale(stepDistance));
      }

      CompanionCinematicOrientationHelper.faceYaw(mount, lookYaw);
      mount.setDeltaMovement(direction.scale(cinematic.moveType() == CompanionMoveType.FLY ? speed * 0.35 : speed * 0.55));
      mount.fallDistance = 0.0F;
      CompanionCinematicPositionService.keepFlyingRescueAboveLanding(cinematic, mount, player);
      cinematic.rememberPosition(mount.position());
   }

   private static void walkCinematicStep(PendingMountCinematic cinematic, LivingEntity mount, Vec3 direction, double speed, Vec3 target, float lookYaw) {
      Vec3 horizontal = new Vec3(direction.x, 0.0, direction.z);
      if (horizontal.lengthSqr() < 0.001) {
         horizontal = new Vec3(0.0, 0.0, 1.0);
      }

      horizontal = horizontal.normalize();
      Vec3 step = horizontal.scale(speed);
      double nextX = mount.getX() + step.x;
      double nextZ = mount.getZ() + step.z;
      double nextY = CompanionCinematicLandingService.walkGroundY(mount.level(), nextX, mount.getY(), nextZ, target.y)
         + CompanionCinematicLandingService.rescueGroundYOffset(cinematic);
      Vec3 skip;
      if (cinematic.mode().isGroundOrWaterRescue()
         && nextY < mount.getY() - 1.45
         && mount.position().distanceTo(target) > 4.0
         && (skip = findPitSkipStep(cinematic, mount, horizontal, target)) != null) {
         CompanionManager.spawnBlinkParticles(mount);
         mount.snapTo(skip.x, skip.y, skip.z, lookYaw, mount.getXRot());
         mount.setPos(skip.x, skip.y, skip.z);
         CompanionManager.spawnBlinkParticles(mount);
      } else {
         mount.snapTo(nextX, nextY, nextZ, lookYaw, mount.getXRot());
         mount.setPos(nextX, nextY, nextZ);
      }
   }

   private static Vec3 findPitSkipStep(PendingMountCinematic cinematic, LivingEntity mount, Vec3 horizontal, Vec3 target) {
      double currentY = mount.getY();

      for (double distance = 3.0; distance <= 12.0; distance++) {
         double x = mount.getX() + horizontal.x * distance;
         double z = mount.getZ() + horizontal.z * distance;
         double y = CompanionCinematicLandingService.walkGroundY(mount.level(), x, currentY, z, target.y)
            + CompanionCinematicLandingService.rescueGroundYOffset(cinematic);
         if (y >= currentY - 1.15) {
            return new Vec3(x, y, z);
         }
      }

      return null;
   }

   static void snapWalkMountToGround(PendingMountCinematic cinematic, LivingEntity mount) {
      if (cinematic.moveType() == CompanionMoveType.WALK) {
         double groundY = CompanionCinematicLandingService.walkGroundY(mount.level(), mount.getX(), mount.getY(), mount.getZ(), mount.getY());
         groundY += CompanionCinematicLandingService.rescueGroundYOffset(cinematic);
         if (Math.abs(groundY - mount.getY()) > 0.001) {
            mount.snapTo(mount.getX(), groundY, mount.getZ(), mount.getYRot(), mount.getXRot());
            mount.setPos(mount.getX(), groundY, mount.getZ());
         }

         mount.setDeltaMovement(Vec3.ZERO);
         mount.fallDistance = 0.0F;
      }
   }

   private static Vec3 mountContactTarget(PendingMountCinematic cinematic, ServerPlayer player, Entity currentVehicle) {
      if (currentVehicle != null && currentVehicle != player && !cinematic.mode().isAirToGroundSwitch()) {
         Vec3 center = currentVehicle.getBoundingBox().getCenter();
         return cinematic.moveType() == CompanionMoveType.WALK ? new Vec3(center.x, currentVehicle.getY(), center.z) : center;
      } else {
         Vec3 center = player.getBoundingBox().getCenter();
         if (cinematic.mode().isFlyingRescue()) {
            return center.add(0.0, -0.35, 0.0);
         } else if (cinematic.moveType() == CompanionMoveType.WALK && player.level() instanceof ServerLevel level2) {
            BlockPos landing = CompanionCinematicLandingService.predictedLanding(level2, player);
            return Vec3.atBottomCenterOf(landing).add(0.0, CompanionCinematicLandingService.rescueGroundYOffset(cinematic), 0.0);
         } else {
            return center;
         }
      }
   }
}
