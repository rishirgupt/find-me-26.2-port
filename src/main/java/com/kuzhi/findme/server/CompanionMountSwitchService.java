package com.kuzhi.findme.server;

import com.kuzhi.findme.Config;
import java.util.Iterator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

final class CompanionMountSwitchService {
   private static final int MOUNT_SWITCH_FAIL_TICKS = 160;

   private CompanionMountSwitchService() {
   }

   static void tickMountSwitch(
      MinecraftServer server, Iterator<PendingMountCinematic> iterator, PendingMountCinematic cinematic, ServerPlayer player, LivingEntity mount
   ) {
      MountCinematicMode mode = cinematic.mode();
      boolean groundRescue = mode.isGroundOrWaterRescue();
      boolean airRescue = mode.isFlyingRescue();
      Entity currentVehicle = player.getVehicle();
      player.fallDistance = 0.0F;
      player.invulnerableTime = Math.max(player.invulnerableTime, Config.postTeleportInvulnerabilityTicks);
      mount.invulnerableTime = Math.max(mount.invulnerableTime, Config.postTeleportInvulnerabilityTicks);
      if (currentVehicle == mount) {
         CompanionMountCinematicFlowService.finishMountCinematic(iterator, cinematic, player, mount, true);
      } else if (CompanionMountCinematicFlowService.correctExternalCinematicPull(cinematic, mount, player)) {
         cinematic.incrementSwitchAge();
      } else {
         if (!cinematic.switched() && mode.isAirToGroundSwitch()) {
            CompanionMountCinematicFlowService.completeMountSwitch(server, player, mount);
            cinematic.setSwitched();
            currentVehicle = player.getVehicle();
         }

         if (groundRescue && mode.isRescue()) {
            boolean atGroundWaitPoint = cinematic.waitLocked()
               || mount.position().distanceTo(CompanionCinematicLandingService.cinematicTarget(cinematic, mount.level(), player)) <= 2.25;
            if (atGroundWaitPoint && !CompanionMountCinematicFlowService.isTouchingMountCollision(player, mount, currentVehicle)) {
               CompanionCinematicPositionService.lockMountAtGroundWait(cinematic, mount, player);
               if (CompanionCinematicLandingService.distanceToGround(player) <= 5.0) {
                  CompanionMountCinematicFlowService.stabilizeFallingPlayer(player);
               }

               cinematic.incrementSwitchAge();
               return;
            }
         }

         if (!groundRescue
            || cinematic.waitLocked()
            || !(CompanionCinematicLandingService.distanceToGround(player) >= 14.0)
            || !mode.isAirToGroundSwitch() && !(player.distanceTo(mount) > 4.0)) {
            boolean urgentAirRescue = mode.isFlyingRescue()
               && (
                  !Double.isNaN(cinematic.catchY())
                     ? player.getY() <= cinematic.catchY() + 2.5
                     : CompanionCinematicLandingService.distanceToGround(player) <= 12.0
               );
            boolean quickFlyingMount = mode.isQuickFlyingMount();
            boolean inFlyingCatchZone = airRescue
               && !cinematic.physicalCatchOnly()
               && CompanionMountCinematicFlowService.isPlayerInFlyingCatchZone(player, mount);
            if (!groundRescue && !urgentAirRescue && !quickFlyingMount && !inFlyingCatchZone && cinematic.switchAge() < 4) {
               CompanionCinematicMovementService.moveTowardMountContact(cinematic, mount, player, currentVehicle);
               cinematic.incrementSwitchAge();
            } else {
               Entity immediateContactVehicle = !mode.isRescue() ? currentVehicle : null;
               boolean physicalCatchContact = CompanionMountCinematicFlowService.isTouchingMountCollision(player, mount, immediateContactVehicle);
               if (airRescue
                  && cinematic.physicalCatchOnly()
                  && cinematic.switchAge() < 6
                  && !physicalCatchContact
                  && !CompanionMountCinematicFlowService.isTouchingMountCollision(player, mount, currentVehicle)) {
                  CompanionCinematicPositionService.lockMountAtFlyingWait(cinematic, mount, player);
                  CompanionMountCinematicFlowService.stabilizeFallingPlayer(player);
                  cinematic.incrementSwitchAge();
               } else if (airRescue
                  && cinematic.physicalCatchOnly()
                  && !physicalCatchContact
                  && !CompanionMountCinematicFlowService.isTouchingMountCollision(player, mount, currentVehicle)) {
                  CompanionCinematicPositionService.lockMountAtFlyingWait(cinematic, mount, player);
                  if (CompanionCinematicLandingService.distanceToGround(player) <= 5.0) {
                     CompanionMountCinematicFlowService.stabilizeFallingPlayer(player);
                  }

                  cinematic.incrementSwitchAge();
               } else if (airRescue
                  && cinematic.switchAge() < 30
                  && CompanionCinematicLandingService.distanceToGround(player) > 6.0
                  && !inFlyingCatchZone
                  && !physicalCatchContact) {
                  CompanionCinematicPositionService.lockMountAtFlyingWait(cinematic, mount, player);
                  cinematic.incrementSwitchAge();
               } else if (airRescue
                  && !Double.isNaN(cinematic.catchY())
                  && player.getY() > cinematic.catchY() + 2.5
                  && !inFlyingCatchZone
                  && !physicalCatchContact
                  && !CompanionMountCinematicFlowService.isTouchingMountCollision(player, mount, currentVehicle)) {
                  CompanionCinematicPositionService.lockMountAtFlyingWait(cinematic, mount, player);
                  cinematic.incrementSwitchAge();
               } else {
                  boolean emergencyAirRescue = airRescue
                     && CompanionCinematicLandingService.distanceToGround(player) <= 2.0
                     && player.distanceTo(mount) <= 6.0F;
                  if (airRescue && !emergencyAirRescue && !inFlyingCatchZone && player.distanceTo(mount) > 4.0) {
                     CompanionCinematicMovementService.moveTowardMountContact(cinematic, mount, player, currentVehicle);
                     if (CompanionCinematicLandingService.distanceToGround(player) <= 5.0) {
                        CompanionMountCinematicFlowService.stabilizeFallingPlayer(player);
                     }

                     cinematic.incrementSwitchAge();
                  } else {
                     if (emergencyAirRescue && !cinematic.physicalCatchOnly()) {
                        CompanionCinematicPositionService.stabilizeAirRescueMount(mount, player);
                     }

                     Entity contactVehicle = !mode.isRescue() ? currentVehicle : (cinematic.age() >= 8 ? currentVehicle : null);
                     boolean rescueAtWaitPoint = !mode.isRescue()
                        || groundRescue && cinematic.waitLocked()
                        || mount.position().distanceTo(CompanionCinematicLandingService.cinematicTarget(cinematic, mount.level(), player)) <= 2.25;
                     if (mode.isRescue() && !rescueAtWaitPoint) {
                        if (airRescue) {
                           CompanionCinematicPositionService.lockMountAtFlyingWait(cinematic, mount, player);
                        } else {
                           CompanionCinematicMovementService.moveTowardMountContact(cinematic, mount, player, currentVehicle);
                        }

                        cinematic.incrementSwitchAge();
                     } else if (!physicalCatchContact && !CompanionMountCinematicFlowService.isTouchingMountCollision(player, mount, contactVehicle)) {
                        if (groundRescue && mode.isRescue()) {
                           CompanionCinematicPositionService.lockMountAtGroundWait(cinematic, mount, player);
                        } else {
                           CompanionCinematicMovementService.moveTowardMountContact(cinematic, mount, player, currentVehicle);
                        }

                        if (mode.isRescue() && CompanionCinematicLandingService.distanceToGround(player) <= 5.0) {
                           CompanionMountCinematicFlowService.stabilizeFallingPlayer(player);
                        }

                        if (cinematic.switchAge() > 160 && !mode.isRescue()) {
                           CompanionMountCinematicFlowService.restoreCinematicPhysics(cinematic, mount);
                           iterator.remove();
                        } else {
                           cinematic.incrementSwitchAge();
                        }
                     } else if (!tryCompleteMountSwitch(server, iterator, cinematic, player, mount)) {
                        if (mode.isMountSwitch()) {
                           if (cinematic.switchAge() > 160) {
                              CompanionMountCinematicFlowService.finishMountCinematic(iterator, cinematic, player, mount, false);
                           } else {
                              cinematic.incrementSwitchAge();
                           }
                        } else if (mode.isRescue()) {
                           CompanionMountCinematicFlowService.softLandFailedMountCatch(player, mount);
                           CompanionMountCinematicFlowService.finishMountCinematic(iterator, cinematic, player, mount, false);
                        } else if (cinematic.switchAge() > 10) {
                           CompanionMountCinematicFlowService.finishMountCinematic(iterator, cinematic, player, mount, false);
                        } else {
                           cinematic.incrementSwitchAge();
                        }
                     }
                  }
               }
            }
         } else {
            CompanionCinematicMovementService.moveTowardMountContact(cinematic, mount, player, currentVehicle);
            cinematic.incrementSwitchAge();
         }
      }
   }

   private static boolean tryCompleteMountSwitch(
      MinecraftServer server, Iterator<PendingMountCinematic> iterator, PendingMountCinematic cinematic, ServerPlayer player, LivingEntity mount
   ) {
      CompanionCinematicMovementService.snapWalkMountToGround(cinematic, mount);
      Entity previousVehicle = player.getVehicle();
      if (!CompanionMountCinematicFlowService.startRidingAfterContact(player, mount, cinematic.mode())) {
         return false;
      }

      if (!cinematic.switched()) {
         CompanionMountCinematicFlowService.completeMountSwitch(server, player, mount, previousVehicle);
         cinematic.setSwitched();
      }

      CompanionMountCinematicFlowService.finishMountCinematic(iterator, cinematic, player, mount, true);
      return true;
   }
}
