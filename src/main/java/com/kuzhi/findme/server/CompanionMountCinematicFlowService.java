package com.kuzhi.findme.server;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.network.RescueMagicPacket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class CompanionMountCinematicFlowService {
   private static final List<PendingMountCinematic> PENDING_MOUNT_CINEMATICS = new ArrayList<>();

   private CompanionMountCinematicFlowService() {
   }

   static void tickMountCinematics(MinecraftServer server) {
      Iterator<PendingMountCinematic> iterator = PENDING_MOUNT_CINEMATICS.iterator();

      while (iterator.hasNext()) {
         PendingMountCinematic cinematic = iterator.next();
         ServerPlayer player = server.getPlayerList().getPlayer(cinematic.playerUuid());
         Entity entity = CompanionManager.findEntity(server, cinematic.mountUuid()).orElse(null);
         LivingEntity living;
         if (entity instanceof LivingEntity && player != null && (living = (LivingEntity)entity).isAlive()) {
            CompanionCinematicSpeedService.samplePlayerHorizontalSpeed(player);
            if (living.level() != player.level()) {
               restoreCinematicPhysics(cinematic, living);
               iterator.remove();
            } else {
               living.noPhysics = shouldUseCinematicNoPhysics(cinematic, living);
               living.setNoGravity(cinematic.mode().isGroundOrWaterRescue() && cinematic.moveType() != CompanionMoveType.WALK);
               if (cinematic.moveType() == CompanionMoveType.FLY) {
                  CompanionManager.forceFlyingAnimationPose(living);
               }

               if (correctExternalCinematicPull(cinematic, living, player)) {
                  cinematic.incrementAge();
               } else if (cinematic.warmupTicks() > 0) {
                  if (living instanceof Mob mob) {
                     mob.getNavigation().stop();
                     mob.setTarget(null);
                  }

                  living.fallDistance = 0.0F;
                  living.invulnerableTime = Math.max(living.invulnerableTime, Config.postTeleportInvulnerabilityTicks);
                  cinematic.rememberPosition(living.position());
                  cinematic.decrementWarmup();
                  cinematic.incrementAge();
               } else if (cinematic.stage() == MountCinematicStage.SWITCH) {
                  CompanionMountSwitchService.tickMountSwitch(server, iterator, cinematic, player, living);
               } else if (enforceFlyingIntro(cinematic, living, player)) {
                  cinematic.incrementAge();
               } else {
                  Vec3 target = CompanionCinematicLandingService.cinematicTarget(cinematic, living.level(), player);
                  double distance = living.position().distanceTo(target);
                  if (cinematic.mode().isFlyingRescue() && !cinematic.flyingRescueStaged() && !Double.isNaN(cinematic.catchY()) && distance <= 6.0) {
                     cinematic.setFlyingRescueStaged();
                     cinematic.incrementAge();
                  } else {
                     double threshold = cinematic.mode().isRescue() ? 1.5 : 2.5;
                     boolean rescueFallback = cinematic.mode().isRescue()
                        && !cinematic.mode().isFlyingRescue()
                        && distance < 8.0
                        && CompanionCinematicLandingService.distanceToGround(player) <= 12.0;
                     boolean flyingCatchWindow = cinematic.mode().isFlyingRescue()
                        && cinematic.flyingRescueStaged()
                        && !Double.isNaN(cinematic.catchY())
                        && !cinematic.physicalCatchOnly()
                        && isPlayerInFlyingCatchZone(player, living);
                     boolean flyingEmergencyCatch = cinematic.mode().isFlyingRescue()
                        && CompanionCinematicLandingService.distanceToGround(player) <= 2.0
                        && player.distanceTo(living) <= 6.0F;
                     if (flyingCatchWindow) {
                        cinematic.beginSwitch();
                        CompanionMountSwitchService.tickMountSwitch(server, iterator, cinematic, player, living);
                     } else if (cinematic.mode() == MountCinematicMode.MOUNT_SWITCH
                        && player.getVehicle() != null
                        && player.getVehicle() != living
                        && isMountApproachContact(player, living, player.getVehicle())) {
                        cinematic.beginSwitch();
                        CompanionMountSwitchService.tickMountSwitch(server, iterator, cinematic, player, living);
                     } else if (!(distance <= threshold) && !rescueFallback && !flyingCatchWindow && !flyingEmergencyCatch) {
                        CompanionCinematicMovementService.moveTowardCinematicTarget(cinematic, living, player, target, distance);
                        living.invulnerableTime = Math.max(living.invulnerableTime, Config.postTeleportInvulnerabilityTicks);
                        if (cinematic.mode().isFlyingRescue() && CompanionCinematicLandingService.distanceToGround(player) <= 5.0) {
                           stabilizeFallingPlayer(player);
                        }

                        if (living instanceof Mob mob) {
                           mob.getNavigation().stop();
                           mob.setTarget(null);
                        }

                        cinematic.incrementAge();
                     } else if (cinematic.mode().isGroundOrWaterRescue() && CompanionCinematicLandingService.distanceToGround(player) > 10.0) {
                        CompanionCinematicMovementService.moveTowardMountContact(cinematic, living, player, player.getVehicle());
                        living.fallDistance = 0.0F;
                        living.invulnerableTime = Math.max(living.invulnerableTime, Config.postTeleportInvulnerabilityTicks);
                        cinematic.rememberPosition(living.position());
                        cinematic.incrementAge();
                     } else {
                        CompanionCinematicMovementService.snapWalkMountToGround(cinematic, living);
                        cinematic.beginSwitch();
                     }
                  }
               }
            }
         } else {
            CompanionCinematicSpeedService.forgetPlayer(cinematic.playerUuid());
            iterator.remove();
         }
      }
   }

   static void scheduleMountCinematic(
      ServerPlayer player, LivingEntity mount, CompanionMoveType moveType, MountCinematicMode mode, boolean restoredFromStorage, boolean sendArrivalMagic
   ) {
      boolean originalNoGravity = mount.isNoGravity();
      boolean originalNoAi = originalNoAiForCinematic(mount);
      collectConflictingMountCinematics(player, mount);
      CompanionRetreatService.cancelRetreat(mount);
      if (!CompanionManager.isArrivalPending(mount)) {
         CompanionManager.restoreAnimationControl(mount);
      }

      if (mode.isRescue() && CompanionAnimationHelper.shouldSuppressOwnerFollowDuringCinematic(mount) && mount instanceof Mob mob) {
         if (shouldFreezeWholeAiDuringCinematic(moveType, mount)) {
            mob.setNoAi(true);
         }

         mob.getNavigation().stop();
         mob.setTarget(null);
      }

      double catchY;
      if (mode.isFlyingRescue() && player.level() instanceof ServerLevel level2) {
         catchY = CompanionCinematicLandingService.predictedLanding(level2, player).getY() + CompanionCinematicLandingService.flyingCatchHeight(player);
      } else {
         catchY = Double.NaN;
      }

      Vec3 flyingStageTarget = null;
      boolean startStaged = mode.isFlyingRescue();
      Vec3 introAnchor = moveType == CompanionMoveType.FLY ? mount.position() : null;
      int warmupTicks = restoredFromStorage && mode.isRescue() ? 4 : 0;
      PENDING_MOUNT_CINEMATICS.add(
         new PendingMountCinematic(
            player.getUUID(),
            mount.getUUID(),
            moveType,
            mode,
            catchY,
            flyingStageTarget,
            startStaged,
            introAnchor,
            false,
            warmupTicks,
            mount.position(),
            originalNoGravity,
            originalNoAi
         )
      );
      FindMeDebugLogger.info(
         "mount-cinematic",
         "scheduled player={} mount={} moveType={} mode={} restoredFromStorage={} sendArrivalMagic={} warmup={} originalNoGravity={} originalNoAi={} pos={}",
         FindMeDebugLogger.entity(player),
         FindMeDebugLogger.entity(mount),
         moveType,
         mode,
         restoredFromStorage,
         sendArrivalMagic,
         warmupTicks,
         originalNoGravity,
         originalNoAi,
         mount.position()
      );
      if (sendArrivalMagic) {
         sendMountArrivalMagic(player, mount, mode);
      }
   }

   static void scheduleDirectFlyingCatchCinematic(
      ServerPlayer player, LivingEntity mount, CompanionMoveType moveType, MountCinematicMode mode, boolean restoredFromStorage, boolean sendArrivalMagic
   ) {
      boolean originalNoGravity = mount.isNoGravity();
      boolean originalNoAi = originalNoAiForCinematic(mount);
      collectConflictingMountCinematics(player, mount);
      CompanionRetreatService.cancelRetreat(mount);
      if (!CompanionManager.isArrivalPending(mount)) {
         CompanionManager.restoreAnimationControl(mount);
      }

      double catchY = CompanionCinematicPositionService.directFlyingCatchY(player);
      CompanionCinematicPositionService.placeFlyingRescueAtLanding(player, mount, catchY);
      int warmupTicks = 0;
      PendingMountCinematic pending2 = new PendingMountCinematic(
         player.getUUID(),
         mount.getUUID(),
         moveType,
         mode,
         catchY,
         null,
         true,
         mount.position(),
         true,
         warmupTicks,
         mount.position(),
         originalNoGravity,
         originalNoAi
      );
      pending2.beginSwitch();
      PENDING_MOUNT_CINEMATICS.add(pending2);
      FindMeDebugLogger.info(
         "mount-cinematic",
         "scheduled direct catch player={} mount={} moveType={} mode={} restoredFromStorage={} sendArrivalMagic={} catchY={} originalNoGravity={} originalNoAi={} pos={}",
         FindMeDebugLogger.entity(player),
         FindMeDebugLogger.entity(mount),
         moveType,
         mode,
         restoredFromStorage,
         sendArrivalMagic,
         catchY,
         originalNoGravity,
         originalNoAi,
         mount.position()
      );
      if (sendArrivalMagic) {
         sendDirectFlyingCatchMagic(player, mount);
      }
   }

   private static void sendMountArrivalMagic(ServerPlayer player, LivingEntity mount, MountCinematicMode mode) {
      CompanionArrivalMagicService.send(
         player, mount, player.position(), mountArrivalMagicDuration(mode), RescueMagicPacket.Style.VERTICAL_PORTAL, arrivalPurpose(mode)
      );
   }

   private static int mountArrivalMagicDuration(MountCinematicMode mode) {
      return mode.isRescue() ? 48 : 42;
   }

   private static boolean originalNoAiForCinematic(LivingEntity mount) {
      return CompanionManager.arrivalOriginalNoAi(mount).orElseGet(() -> mount instanceof Mob mob && mob.isNoAi());
   }

   private static void sendDirectFlyingCatchMagic(ServerPlayer player, LivingEntity mount) {
      CompanionArrivalMagicService.openAt(
         player, mount, mount.position(), player.position(), 28, RescueMagicPacket.Style.GROUND_CIRCLE, RescueMagicPacket.Purpose.RESCUE
      );
   }

   private static RescueMagicPacket.Purpose arrivalPurpose(MountCinematicMode mode) {
      return mode.isRescue() ? RescueMagicPacket.Purpose.RESCUE : RescueMagicPacket.Purpose.SUMMON;
   }

   static boolean collectOtherVisibleMountCinematics(ServerPlayer player, PlayerCompanionData data, UUID keepUuid, UUID currentVehicleUuid) {
      boolean changed = false;
      Iterator<PendingMountCinematic> cinematicIterator = PENDING_MOUNT_CINEMATICS.iterator();

      while (cinematicIterator.hasNext()) {
         PendingMountCinematic pending = cinematicIterator.next();
         UUID uuid = pending.mountUuid();
         if (pending.playerUuid().equals(player.getUUID())
            && !uuid.equals(keepUuid)
            && !uuid.equals(currentVehicleUuid)
            && data.contains(CompanionKind.MOUNT, uuid)) {
            Entity entity = CompanionManager.findEntity(player.level().getServer(), uuid).orElse(null);
            if (entity instanceof LivingEntity living && living.isAlive()) {
               restoreCinematicPhysics(pending, living);
               CompanionDeploymentService.collectLiving(player, data, CompanionKind.MOUNT, living);
            }

            cinematicIterator.remove();
            changed = true;
         }
      }

      return changed;
   }

   private static void collectConflictingMountCinematics(ServerPlayer player, LivingEntity newMount) {
      boolean changed = false;
      PlayerCompanionData data = CompanionManager.data(player);
      Iterator<PendingMountCinematic> cinematicIterator = PENDING_MOUNT_CINEMATICS.iterator();

      while (cinematicIterator.hasNext()) {
         PendingMountCinematic pending = cinematicIterator.next();
         boolean samePlayer = pending.playerUuid().equals(player.getUUID());
         boolean sameMount = pending.mountUuid().equals(newMount.getUUID());
         if (samePlayer || sameMount) {
            Entity entity = CompanionManager.findEntity(player.level().getServer(), pending.mountUuid()).orElse(null);
            if (entity instanceof LivingEntity living && living.isAlive()) {
               restoreCinematicPhysics(pending, living);
               if (samePlayer && !sameMount && data.contains(CompanionKind.MOUNT, pending.mountUuid())) {
                  CompanionDeploymentService.collectLiving(player, data, CompanionKind.MOUNT, living);
                  changed = true;
               }
            }

            cinematicIterator.remove();
         }
      }

      if (changed) {
         CompanionManager.save(player, data);
         CompanionManager.syncToClient(player, CompanionKind.MOUNT);
      }
   }

   static boolean correctExternalCinematicPull(PendingMountCinematic cinematic, LivingEntity mount, ServerPlayer player) {
      if (cinematic.mode().isRescue() && cinematic.lastCinematicPosition() != null && !cinematic.switched()) {
         Vec3 expected = cinematic.lastCinematicPosition();
         Vec3 current = mount.position();
         boolean jumpedAwayFromPath = current.distanceTo(expected) > 10.0;
         boolean pulledToPlayer = mount.distanceTo(player) < 8.0F && expected.distanceTo(player.position()) > 12.0;
         if (jumpedAwayFromPath && pulledToPlayer) {
            if (mount instanceof Mob mob) {
               if (shouldFreezeWholeAiDuringCinematic(cinematic.moveType(), mount)) {
                  mob.setNoAi(true);
               }

               mob.getNavigation().stop();
               mob.setTarget(null);
            }

            mount.setPos(expected.x, expected.y, expected.z);
            mount.setDeltaMovement(Vec3.ZERO);
            mount.fallDistance = 0.0F;
            mount.hurtMarked = true;
            if (cinematic.moveType() == CompanionMoveType.FLY) {
               CompanionManager.forceFlyingAnimationPose(mount);
            }

            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   static void completeMountSwitch(MinecraftServer server, ServerPlayer player, LivingEntity newMount) {
      completeMountSwitch(server, player, newMount, player.getVehicle());
   }

   static void completeMountSwitch(MinecraftServer server, ServerPlayer player, LivingEntity newMount, Entity oldVehicle) {
      PlayerCompanionData data = CompanionManager.data(player);
      UUID oldVehicleUuid = oldVehicle == null ? null : oldVehicle.getUUID();
      if (oldVehicle != null && oldVehicle != newMount) {
         Entity currentVehicle = player.getVehicle();
         if (currentVehicle == oldVehicle) {
            player.stopRiding();
         }

         oldVehicle.fallDistance = 0.0F;
         if (data.contains(CompanionKind.MOUNT, oldVehicleUuid) && oldVehicle instanceof LivingEntity oldMount) {
            data.rememberPrevious(CompanionKind.MOUNT, oldVehicleUuid);
            retireSwitchedMount(player, data, oldMount);
         } else if (player.getVehicle() == newMount) {
            VehicleManager.collectIfFindMeVehicle(player, data, oldVehicle);
         }
      }

      data.deployed(CompanionKind.MOUNT)
         .ifPresent(
            previousUuid -> {
               if (!previousUuid.equals(newMount.getUUID()) && !previousUuid.equals(oldVehicleUuid)) {
                  CompanionManager.locateEntity(server, data, previousUuid)
                     .filter(previous -> previous instanceof LivingEntity)
                     .map(previous -> (LivingEntity)previous)
                     .ifPresent(previous -> retireSwitchedMount(player, data, previous));
               }
            }
         );
      CompanionManager.save(player, data);
   }

   private static void retireSwitchedMount(ServerPlayer player, PlayerCompanionData data, LivingEntity oldMount) {
      UUID uuid = oldMount.getUUID();
      CompanionManager.sendAway(oldMount, player, data);
      data.clearDeployed(CompanionKind.MOUNT, uuid);
   }

   static void stabilizeFallingPlayer(ServerPlayer player) {
      player.fallDistance = 0.0F;
      player.invulnerableTime = Math.max(player.invulnerableTime, Config.postTeleportInvulnerabilityTicks);
   }

   static void restoreCinematicPhysics(PendingMountCinematic cinematic, LivingEntity living) {
      living.noPhysics = false;
      if (living instanceof Mob mob) {
         mob.setNoAi(cinematic.originalNoAi());
      }

      living.setNoGravity(cinematic.originalNoGravity());
   }

   private static boolean shouldFreezeWholeAiDuringCinematic(CompanionMoveType moveType, LivingEntity mount) {
      return moveType != CompanionMoveType.FLY && !CompanionManager.keepsFlyingAnimationWithActiveAi(mount);
   }

   static boolean shouldUseCinematicNoPhysics(PendingMountCinematic cinematic, LivingEntity living) {
      if (cinematic.moveType() == CompanionMoveType.FLY) {
         return false;
      }

      if (cinematic.moveType() == CompanionMoveType.WALK) {
         return true;
      }

      double maxSize = Math.max(living.getBbWidth(), living.getBbHeight());
      return maxSize < 2.6;
   }

   static boolean isPlayerInFlyingCatchZone(ServerPlayer player, LivingEntity mount) {
      if (player.getDeltaMovement().y > 0.25) {
         return false;
      }

      AABB contactBox = CompanionMountContactService.mountContactBox(mount);
      double dx = player.getX() - mount.getX();
      double dz = player.getZ() - mount.getZ();
      double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
      double contactRadius = Math.max(contactBox.getXsize(), contactBox.getZsize()) * 0.5;
      double catchRadius = Math.max(3.0, contactRadius + 1.25);
      double minY = contactBox.minY - 0.75;
      double maxY = contactBox.maxY + 1.25;
      return horizontalDistance <= catchRadius && player.getY() >= minY && player.getY() <= maxY;
   }

   static boolean enforceFlyingIntro(PendingMountCinematic cinematic, LivingEntity mount, ServerPlayer player) {
      if (cinematic.introAnchor() == null) {
         return false;
      } else if (cinematic.mode().isFlyingRescue()
         && !cinematic.flyingRescueStaged()
         && cinematic.flyingStageTarget() != null
         && cinematic.age() < 20
         && mount.distanceTo(player) <= 10.0F) {
         return forceFlyingIntroPosition(cinematic, mount, cinematic.flyingStageTarget(), 20);
      } else if (cinematic.requiresFlyingIntroLock() && cinematic.age() < 14) {
         Vec3 target = player.position();
         Vec3 anchor = cinematic.introAnchor();
         return !(anchor.distanceTo(target) < 14.0) && !(mount.distanceTo(player) > 9.0F) ? forceFlyingIntroPosition(cinematic, mount, target, 14) : false;
      } else {
         return false;
      }
   }

   static boolean isTouchingMountCollision(ServerPlayer player, LivingEntity mount, Entity currentVehicle) {
      return isMountRideContact(player, mount, currentVehicle);
   }

   static boolean isMountApproachContact(ServerPlayer player, LivingEntity mount, Entity currentVehicle) {
      return isMountRideContact(player, mount, currentVehicle);
   }

   private static boolean isMountVehicleContact(ServerPlayer player, LivingEntity mount, Entity currentVehicle) {
      if (currentVehicle != null && currentVehicle != mount) {
         boolean vehicleContact = CompanionMountContactService.contactsIntersect(currentVehicle, mount, 0.45);
         FindMeDebugLogger.info(
            "mount-contact",
            "vehicleBox checked player={} mount={} vehicle={} result={}",
            FindMeDebugLogger.entity(player),
            FindMeDebugLogger.entity(mount),
            FindMeDebugLogger.entity(currentVehicle),
            vehicleContact
         );
         return vehicleContact;
      } else {
         FindMeDebugLogger.info(
            "mount-contact",
            "no vehicle contact player={} mount={} vehicle={} reason=no-current-vehicle",
            FindMeDebugLogger.entity(player),
            FindMeDebugLogger.entity(mount),
            FindMeDebugLogger.entity(currentVehicle)
         );
         return false;
      }
   }

   static boolean isMountRideContact(ServerPlayer player, LivingEntity mount, Entity currentVehicle) {
      AABB playerBox = player.getBoundingBox().inflate(0.28);
      boolean playerContact = CompanionMountContactService.intersectsMountContact(mount, playerBox, 0.0);
      if (playerContact) {
         FindMeDebugLogger.info(
            "mount-contact",
            "ride playerBox hit player={} mount={} vehicle={} box={}",
            FindMeDebugLogger.entity(player),
            FindMeDebugLogger.entity(mount),
            FindMeDebugLogger.entity(currentVehicle),
            FindMeDebugLogger.box(playerBox)
         );
         return true;
      } else {
         AABB sweptPlayerBox = playerBox.expandTowards(player.getDeltaMovement().scale(-1.0)).inflate(0.2);
         boolean sweptPlayerContact = CompanionMountContactService.intersectsMountContact(mount, sweptPlayerBox, 0.0);
         if (sweptPlayerContact) {
            FindMeDebugLogger.info(
               "mount-contact",
               "ride sweptPlayerBox hit player={} mount={} vehicle={} box={} motion={}",
               FindMeDebugLogger.entity(player),
               FindMeDebugLogger.entity(mount),
               FindMeDebugLogger.entity(currentVehicle),
               FindMeDebugLogger.box(sweptPlayerBox),
               player.getDeltaMovement()
            );
            return true;
         } else if (currentVehicle != null && currentVehicle != mount) {
            double down = Math.max(1.5, Math.min(8.0, currentVehicle.getBbHeight() + 1.5));
            AABB handoffBox = playerRideBox(player, 2.75, down, 1.75);
            boolean handoffContact = CompanionMountContactService.intersectsMountContact(mount, handoffBox, 0.0);
            boolean vehicleContact = handoffContact && isMountVehicleContact(player, mount, currentVehicle);
            FindMeDebugLogger.info(
               "mount-contact",
               "ride handoffBox checked player={} mount={} vehicle={} handoff={} vehicle={} box={}",
               FindMeDebugLogger.entity(player),
               FindMeDebugLogger.entity(mount),
               FindMeDebugLogger.entity(currentVehicle),
               handoffContact,
               vehicleContact,
               FindMeDebugLogger.box(handoffBox)
            );
            return vehicleContact;
         } else {
            return false;
         }
      }
   }

   private static AABB playerRideBox(ServerPlayer player, double horizontal, double down, double up) {
      AABB base = player.getBoundingBox();
      return new AABB(base.minX - horizontal, base.minY - down, base.minZ - horizontal, base.maxX + horizontal, base.maxY + up, base.maxZ + horizontal);
   }

   static boolean startRidingAfterContact(ServerPlayer player, LivingEntity mount, MountCinematicMode mode) {
      if (player.getVehicle() == mount) {
         FindMeDebugLogger.info(
            "mount-ride", "already riding player={} mount={} mode={}", FindMeDebugLogger.entity(player), FindMeDebugLogger.entity(mount), mode
         );
         return true;
      } else {
         mount.interact(player, InteractionHand.MAIN_HAND, mount.position());
         if (player.getVehicle() == mount) {
            FindMeDebugLogger.info(
               "mount-ride", "normal interaction success player={} mount={} mode={}", FindMeDebugLogger.entity(player), FindMeDebugLogger.entity(mount), mode
            );
            return true;
         } else if (!CompanionMountContactService.canMountByNormalUse(player, mount)) {
            FindMeDebugLogger.info(
               "mount-ride",
               "normal interaction failed and force ride blocked player={} mount={} mode={}",
               FindMeDebugLogger.entity(player),
               FindMeDebugLogger.entity(mount),
               mode
            );
            return false;
         } else {
            boolean result = player.startRiding(mount, true, true);
            FindMeDebugLogger.info(
               "mount-ride",
               "normal forced ride player={} mount={} mode={} result={}",
               FindMeDebugLogger.entity(player),
               FindMeDebugLogger.entity(mount),
               mode,
               result
            );
            return result;
         }
      }
   }

   private static boolean forceFlyingIntroPosition(PendingMountCinematic cinematic, LivingEntity mount, Vec3 target, int ticks) {
      Vec3 anchor = cinematic.introAnchor();
      double progress = Mth.clamp((cinematic.age() + 1.0) / 14.0, 0.0, 0.82);
      if (ticks != 14) {
         progress = Mth.clamp((cinematic.age() + 1.0) / ticks, 0.0, 0.88);
      }

      Vec3 desired;
      Vec3 direction;
      float yaw = (direction = target.subtract(desired = anchor.lerp(target, progress))).lengthSqr() < 0.001
         ? mount.getYRot()
         : CompanionCinematicOrientationHelper.yawTowardStable(mount, direction.normalize());
      mount.snapTo(desired.x, desired.y, desired.z, yaw, mount.getXRot());
      mount.setPos(desired.x, desired.y, desired.z);
      CompanionCinematicOrientationHelper.faceYaw(mount, yaw);
      mount.setDeltaMovement(direction.lengthSqr() < 0.001 ? Vec3.ZERO : direction.normalize().scale(0.45));
      mount.fallDistance = 0.0F;
      CompanionManager.forceFlyingAnimationPose(mount);
      if (mount instanceof Mob mob) {
         mob.getNavigation().stop();
         mob.setTarget(null);
      }

      return true;
   }

   static void finishMountCinematic(
      Iterator<PendingMountCinematic> iterator, PendingMountCinematic cinematic, ServerPlayer player, LivingEntity mount, boolean mounted
   ) {
      restoreCinematicPhysics(cinematic, mount);
      PlayerCompanionData data = CompanionManager.data(player);
      if (mounted
         && cinematic.mode().isRescue()
         && mount.level() instanceof ServerLevel level
         && !CompanionPlacementFinder.hasOpenEntitySpace(level, mount, mount.getX(), mount.getY(), mount.getZ())) {
         if (player.getVehicle() == mount) {
            player.stopRiding();
         }

         player.fallDistance = 0.0F;
         player.invulnerableTime = Math.max(player.invulnerableTime, Config.postTeleportInvulnerabilityTicks);
         CompanionManager.storeAndDiscard(player, data, mount);
         data.clearDeployed(CompanionKind.MOUNT);
         CompanionManager.save(player, data);
         CompanionManager.syncToClient(player, CompanionKind.MOUNT);
         CompanionSummonLineService.showBlocked(player, mount);
         iterator.remove();
      } else {
         data.setDeployed(CompanionKind.MOUNT, mount.getUUID());
         data.setLastKnownPosition(mount.getUUID(), SavedPosition.of(mount.level(), mount.getX(), mount.getY(), mount.getZ(), mount.getYRot(), mount.getXRot()));
         VehicleManager.enforceSingleRideSlotForMount(player, data, mount.getUUID(), null);
         CompanionManager.save(player, data);
         CompanionManager.syncToClient(player, CompanionKind.MOUNT);
         player.fallDistance = 0.0F;
         CompanionMountSettleProtectionService.rememberSettledMount(player, mount);
         iterator.remove();
      }
   }

   static void softLandFailedMountCatch(ServerPlayer player, LivingEntity mount) {
      if (player.getVehicle() != mount) {
         double safeY = Math.max(player.getY(), mount.getBoundingBox().maxY + 0.2);
         player.teleportTo(player.getX(), safeY, player.getZ());
         player.setDeltaMovement(Vec3.ZERO);
         player.fallDistance = 0.0F;
         mount.fallDistance = 0.0F;
         player.invulnerableTime = Math.max(player.invulnerableTime, Config.postTeleportInvulnerabilityTicks);
         mount.invulnerableTime = Math.max(mount.invulnerableTime, Config.postTeleportInvulnerabilityTicks);
      }
   }
}
