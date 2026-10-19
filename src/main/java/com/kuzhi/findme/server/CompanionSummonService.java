package com.kuzhi.findme.server;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.network.RescueMagicPacket;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

final class CompanionSummonService {
   static final double DIRECT_FLYING_RESCUE_DISTANCE = 18.0;

   private CompanionSummonService() {
   }

   static void summonActive(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
      boolean arrivalStarted = false;
      Optional<CompanionSummonPreparationService.Preparation> maybePreparation = CompanionSummonPreparationService.prepare(player, data, kind);
      if (!maybePreparation.isEmpty()) {
         CompanionSummonPreparationService.Preparation preparation = maybePreparation.get();
         long now = preparation.now();
         boolean inCombat = preparation.inCombat();
         boolean fallingRescue = preparation.fallingRescue();
         LivingEntity companionRescueTarget = preparation.companionRescueTarget();
         boolean companionRescue = companionRescueTarget != null;
         UUID uuid = preparation.uuid();
         Entity entity = CompanionManager.locateEntity(player.level().getServer(), data, uuid).orElse(null);
         boolean freshHomeResidentRestore = false;
         if (entity instanceof LivingEntity resident
            && CompanionManager.isHomeResident(uuid)
            && data.homeNestBlock(uuid).isPresent()
            && CompanionStorageService.snapshotAndDiscardHomeResidentForSummon(player, data, resident)) {
            entity = null;
            freshHomeResidentRestore = true;
         }

         Optional<CompoundTag> storedTag = entity == null ? data.storedEntity(preparation.uuid()) : Optional.empty();
         CompanionMoveType plannedMoveType = plannedMoveType(entity, storedTag, kind);
         if (isVoidSummon(player)) {
            if (kind == CompanionKind.MOUNT && plannedMoveType == CompanionMoveType.FLY) {
               if (!completeDirectVoidFlyingMount(player, data, preparation.uuid(), entity, inCombat, now, freshHomeResidentRestore)) {
                  CompanionSummonFailureService.unavailable(player, data, kind, false);
               }
            } else {
               CompanionSummonLineService.showVoidUnsafe(player, data, kind);
            }
         } else {
            boolean restoredFromStorage = false;
            if (entity == null) {
               CompanionSummonPlacementService.StoredRestore restoreSpawn = CompanionSummonPlacementService.findStoredRestoreSpawn(
                  player, data, kind, storedTag, fallingRescue, companionRescueTarget
               );
               if (restoreSpawn.preloadChunkArea()) {
                  CompanionCinematicLandingService.preloadChunkArea(player.level(), restoreSpawn.pos(), 1);
               }

               Vec3 focus = arrivalFocus(player, kind, companionRescueTarget);
               if (restoreSpawn.directFlyingRescue()) {
                  entity = freshHomeResidentRestore
                     ? CompanionEntityTransferService.restoreStoredEntityFresh(
                           player.level(), player, data, preparation.uuid(), restoreSpawn.pos(), player.getYRot(), player.getXRot()
                        )
                        .orElse(null)
                     : CompanionEntityTransferService.restoreStoredEntity(
                           player, data, preparation.uuid(), restoreSpawn.pos(), player.getYRot(), player.getXRot()
                        )
                        .orElse(null);
                  restoredFromStorage = entity != null;
                  arrivalStarted = false;
               } else {
                  RescueMagicPacket.Purpose purpose = arrivalPurpose(restoreSpawn.preloadChunkArea(), companionRescue);
                  restoredFromStorage = (
                        entity = (freshHomeResidentRestore
                              ? CompanionEntityTransferService.restoreStoredEntityFreshForArrival(
                                 player.level(),
                                 player,
                                 data,
                                 preparation.uuid(),
                                 restoreSpawn.pos(),
                                 player.getYRot(),
                                 player.getXRot(),
                                 focus,
                                 arrivalDuration(kind, restoreSpawn.preloadChunkArea(), companionRescue),
                                 arrivalStyle(kind),
                                 purpose
                              )
                              : CompanionEntityTransferService.restoreStoredEntityForArrival(
                                 player,
                                 data,
                                 preparation.uuid(),
                                 restoreSpawn.pos(),
                                 player.getYRot(),
                                 player.getXRot(),
                                 focus,
                                 arrivalDuration(kind, restoreSpawn.preloadChunkArea(), companionRescue),
                                 arrivalStyle(kind),
                                 purpose
                              ))
                           .orElse(null)
                     )
                     != null;
                  arrivalStarted = restoredFromStorage;
               }
            }

            if (!(entity instanceof LivingEntity living && !entity.isRemoved() && entity.isAlive())) {
               CompanionSummonFailureService.unavailable(player, data, kind, false);
            } else if (kind == CompanionKind.MOUNT && player.getVehicle() != null && player.getVehicle().getUUID().equals(entity.getUUID())) {
               CompanionSummonLineService.showAlreadyRiding(player, living);
            } else {
               if (entity.isPassenger()) {
                  entity.stopRiding();
               }

               if (entity.getFirstPassenger() != null && entity.getFirstPassenger() != player) {
                  CompanionSummonLineService.showOccupied(player, living, kind);
               } else {
                  data.setOrigin(
                     entity.getUUID(), SavedPosition.of(entity.level(), entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot())
                  );
                  CompanionMoveType moveType = CompanionManager.moveType(entity, kind);
                  CompanionMoveType summonMoveType = CompanionEntityClassifier.summonMoveType(living, kind);
                  boolean vehicleRescue = CompanionSummonModeService.isAirborneGroundVehicleRescue(player, data, kind, moveType);
                  boolean rescueSummon = fallingRescue || CompanionSummonModeService.isAirborneGroundRescue(player, kind, moveType) || vehicleRescue;
                  boolean currentRideIsBiologicalMount = CompanionSummonModeService.isRegisteredBiologicalMount(data, player.getVehicle());
                  boolean switchingMount = kind == CompanionKind.MOUNT && player.getVehicle() != null && player.getVehicle() != entity && !vehicleRescue;
                  boolean transitionFromFlyingMount = currentRideIsBiologicalMount
                     && CompanionCinematicLandingService.distanceToGround(player) > 6.0
                     && CompanionManager.moveType(player.getVehicle(), kind) == CompanionMoveType.FLY
                     && moveType != CompanionMoveType.FLY;
                  MountCinematicMode mode = CompanionSummonModeService.modeFor(kind, moveType, rescueSummon, switchingMount, transitionFromFlyingMount);
                  if (kind == CompanionKind.MOUNT && mode.isRescue() && !restoredFromStorage && !isDirectFlyingRescue(player, moveType, mode)) {
                     BlockPos restorePos = CompanionSummonPlacementService.findMountRescueRestoreSpawn(player, moveType);
                     CompanionCinematicLandingService.preloadChunkArea(player.level(), restorePos, 1);
                     LivingEntity restoredLiving = CompanionEntityTransferService.moveEntityForArrival(
                        player,
                        living,
                        player.level(),
                        restorePos,
                        player.getYRot(),
                        player.getXRot(),
                        player.position(),
                        arrivalDuration(kind, true, false),
                        arrivalStyle(kind),
                        RescueMagicPacket.Purpose.RESCUE
                     );
                     Entity restored = restoredLiving;
                     arrivalStarted = true;
                     if (restoredLiving.isRemoved() || !restoredLiving.isAlive()) {
                        CompanionSummonFailureService.unavailable(player, data, kind, true);
                        return;
                     }

                     entity = restored;
                     living = restoredLiving;
                     restoredFromStorage = true;
                     moveType = CompanionManager.moveType(living, kind);
                     summonMoveType = CompanionEntityClassifier.summonMoveType(living, kind);
                     mode = CompanionSummonModeService.modeFor(kind, moveType, rescueSummon, switchingMount, transitionFromFlyingMount);
                  }

                  boolean airborneGroundCompanion = CompanionSummonModeService.isAirborneGroundCompanionSummon(player, kind, summonMoveType);
                  boolean companionRescuex = companionRescueTarget != null;
                  rescueSummon = false;
                  if (kind == CompanionKind.COMPANION && !companionRescuex && !airborneGroundCompanion && isUnsafeHighCompanionSummon(player, summonMoveType)) {
                     if (restoredFromStorage) {
                        CompanionSummonFailureService.restoredEntityFailed(player, data, kind, living, CompanionSummonFailureService.Reason.TOO_HIGH);
                     } else {
                        CompanionSummonLineService.showTooHigh(player, data, kind, living);
                     }
                  } else {
                     if (transitionFromFlyingMount) {
                        Entity oldVehicle = player.getVehicle();
                        player.stopRiding();
                        if (oldVehicle != null && oldVehicle != entity && data.contains(CompanionKind.MOUNT, oldVehicle.getUUID())) {
                           data.rememberPrevious(CompanionKind.MOUNT, oldVehicle.getUUID());
                           CompanionManager.sendAway(oldVehicle, player, data);
                        }

                        CompanionMountCinematicFlowService.stabilizeFallingPlayer(player);
                     }

                     fallingRescue = kind == CompanionKind.MOUNT
                        && !mode.isRescue()
                        && !switchingMount
                        && !restoredFromStorage
                        && living.level() == player.level()
                        && living.distanceTo(player) <= 16.0;
                     boolean alreadyAtRescueSpawn = kind == CompanionKind.MOUNT && mode.isRescue() && restoredFromStorage;
                     if (!fallingRescue && !alreadyAtRescueSpawn) {
                        BlockPos target = CompanionSummonPlacementService.findLiveMoveTarget(
                           player, kind, living, moveType, summonMoveType, mode, airborneGroundCompanion, companionRescueTarget
                        );
                        Optional<BlockPos> openTarget = CompanionSummonPlacementService.adjustForOpenLiveSpace(player, living, target, mode);
                        if (!openTarget.isPresent()) {
                           if (restoredFromStorage) {
                              CompanionSummonFailureService.restoredEntityFailed(
                                 player, data, kind, living, CompanionSummonFailureService.Reason.NEED_OPEN_SPACE
                              );
                           } else {
                              CompanionSummonLineService.showNeedOpenSpace(player, data, kind, living);
                           }

                           return;
                        }

                        BlockPos var40 = openTarget.get();
                        if (mode.isRescue()) {
                           CompanionCinematicLandingService.preloadChunkArea(player.level(), var40, 1);
                        } else if (companionRescuex) {
                           CompanionCinematicLandingService.preloadChunkArea(player.level(), var40, 1);
                        }

                        CompanionArrivalSoundService.playCommandVoice(player, living);
                        rescueSummon = true;
                        Vec3 focus = arrivalFocus(player, kind, companionRescueTarget);
                        if (isDirectFlyingRescue(player, moveType, mode)) {
                           living = CompanionEntityTransferService.moveEntityTo(living, player.level(), var40, player.getYRot(), player.getXRot());
                           arrivalStarted = false;
                        } else {
                           living = CompanionEntityTransferService.moveEntityForArrival(
                              player,
                              living,
                              player.level(),
                              var40,
                              player.getYRot(),
                              player.getXRot(),
                              focus,
                              arrivalDuration(kind, mode.isRescue(), companionRescuex),
                              arrivalStyle(kind),
                              arrivalPurpose(mode.isRescue(), companionRescuex)
                           );
                           arrivalStarted = true;
                        }
                     }

                     if (!rescueSummon) {
                        CompanionArrivalSoundService.playCommandVoice(player, living);
                     }

                     CompanionSummonCompletionService.complete(
                        player, data, kind, living, moveType, mode, restoredFromStorage, inCombat, companionRescuex, companionRescueTarget, arrivalStarted, now
                     );
                  }
               }
            }
         }
      }
   }

   private static CompanionMoveType plannedMoveType(Entity entity, Optional<CompoundTag> storedTag, CompanionKind kind) {
      if (entity instanceof LivingEntity living) {
         return kind == CompanionKind.MOUNT ? CompanionManager.moveType(living, kind) : CompanionEntityClassifier.summonMoveType(living, kind);
      } else if (storedTag.isPresent()) {
         String entityType = CompanionManager.storedEntityType(storedTag.get());
         return kind == CompanionKind.MOUNT ? CompanionEntityClassifier.moveType(entityType, kind) : CompanionEntityClassifier.summonMoveType(entityType, kind);
      } else {
         return CompanionMoveType.WALK;
      }
   }

   private static boolean isVoidSummon(ServerPlayer player) {
      return !CompanionCinematicLandingService.hasReliableLandingBelow(player.level(), player);
   }

   private static boolean completeDirectVoidFlyingMount(
      ServerPlayer player, PlayerCompanionData data, UUID uuid, Entity entity, boolean inCombat, long now, boolean freshRestore
   ) {
      if (entity != null && entity.getUUID().equals(player.getVehicle() == null ? null : player.getVehicle().getUUID())) {
         if (entity instanceof LivingEntity riding) {
            CompanionSummonLineService.showAlreadyRiding(player, riding);
         }

         return true;
      } else if (entity != null && entity.getFirstPassenger() != null && entity.getFirstPassenger() != player) {
         if (entity instanceof LivingEntity occupied) {
            CompanionSummonLineService.showOccupied(player, occupied, CompanionKind.MOUNT);
         }

         return true;
      } else {
         if (entity != null && entity.isPassenger()) {
            entity.stopRiding();
         }

         BlockPos target = BlockPos.containing(player.getX(), CompanionCinematicPositionService.directFlyingCatchY(player), player.getZ());
         LivingEntity living = null;
         if (entity instanceof LivingEntity liveEntity) {
            data.setOrigin(
               uuid, SavedPosition.of(liveEntity.level(), liveEntity.getX(), liveEntity.getY(), liveEntity.getZ(), liveEntity.getYRot(), liveEntity.getXRot())
            );
            living = CompanionEntityTransferService.moveEntityTo(liveEntity, player.level(), target, player.getYRot(), player.getXRot());
         } else if ((
            freshRestore
               ? CompanionEntityTransferService.restoreStoredEntityFresh(player.level(), player, data, uuid, target, player.getYRot(), player.getXRot())
                  .orElse(null)
               : CompanionEntityTransferService.restoreStoredEntity(player, data, uuid, target, player.getYRot(), player.getXRot()).orElse(null)
         ) instanceof LivingEntity restoredLiving) {
            living = restoredLiving;
         }

         if (living != null && !living.isRemoved() && living.isAlive()) {
            CompanionArrivalSoundService.playCommandVoice(player, living);
            CompanionSummonCompletionService.completeVoidFlyingMount(player, data, living, inCombat, now);
            return true;
         } else {
            return false;
         }
      }
   }

   private static boolean isDirectFlyingRescue(ServerPlayer player, CompanionMoveType moveType, MountCinematicMode mode) {
      return moveType == CompanionMoveType.FLY && mode.isFlyingRescue() && CompanionCinematicLandingService.shouldDirectFlyingRescue(player);
   }

   private static boolean isUnsafeHighCompanionSummon(ServerPlayer player, CompanionMoveType summonMoveType) {
      if (summonMoveType == CompanionMoveType.FLY) {
         return false;
      } else {
         return player.onGround() ? false : CompanionCinematicLandingService.distanceToGround(player) > 4.0;
      }
   }

   private static Vec3 arrivalFocus(ServerPlayer player, CompanionKind kind, LivingEntity companionRescueTarget) {
      return kind == CompanionKind.COMPANION && companionRescueTarget != null ? companionRescueTarget.position() : player.position();
   }

   private static int arrivalDuration(CompanionKind kind, boolean rescue, boolean companionRescue) {
      return !rescue && !companionRescue ? 42 : 48;
   }

   private static RescueMagicPacket.Style arrivalStyle(CompanionKind kind) {
      return kind == CompanionKind.MOUNT ? RescueMagicPacket.Style.VERTICAL_PORTAL : RescueMagicPacket.Style.GROUND_CIRCLE;
   }

   private static RescueMagicPacket.Purpose arrivalPurpose(boolean rescue, boolean companionRescue) {
      return !rescue && !companionRescue ? RescueMagicPacket.Purpose.SUMMON : RescueMagicPacket.Purpose.RESCUE;
   }
}
