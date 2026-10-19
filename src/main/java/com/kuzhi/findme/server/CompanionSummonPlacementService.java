package com.kuzhi.findme.server;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

final class CompanionSummonPlacementService {
   private CompanionSummonPlacementService() {
   }

   static CompanionSummonPlacementService.StoredRestore findStoredRestoreSpawn(
      ServerPlayer player,
      PlayerCompanionData data,
      CompanionKind kind,
      Optional<CompoundTag> storedTag,
      boolean fallingRescue,
      LivingEntity companionRescueTarget
   ) {
      CompanionMoveType storedMoveType = storedTag.map(CompanionManager::storedEntityType)
         .map(type -> CompanionEntityClassifier.summonMoveType(type, kind))
         .orElse(CompanionMoveType.WALK);
      CompanionMoveType storedMountMoveType = storedTag.map(CompanionManager::storedEntityType)
         .map(type -> CompanionManager.moveType(type, kind))
         .orElse(storedMoveType);
      boolean airborneGroundCompanion = CompanionSummonModeService.isAirborneGroundCompanionSummon(player, kind, storedMoveType);
      boolean airborneGroundMountSwitch = CompanionSummonModeService.isAirborneGroundMountSwitch(player, kind, storedMountMoveType);
      boolean airborneGroundVehicleRescue = CompanionSummonModeService.isAirborneGroundVehicleRescue(player, data, kind, storedMountMoveType);
      boolean companionRescue = companionRescueTarget != null;
      boolean restoreRescue = fallingRescue
         || CompanionSummonModeService.isAirborneGroundRescue(player, kind, storedMoveType)
         || airborneGroundVehicleRescue
         || airborneGroundCompanion
         || airborneGroundMountSwitch
         || companionRescue;
      boolean directFlyingRescue = kind == CompanionKind.MOUNT
         && fallingRescue
         && storedMountMoveType == CompanionMoveType.FLY
         && CompanionCinematicLandingService.shouldDirectFlyingRescue(player);
      BlockPos pos = storedTag.map(CompanionManager::storedEntityType)
         .map(
            type -> {
               CompanionMoveType summonMoveType = CompanionEntityClassifier.summonMoveType(type, kind);
               if (companionRescue) {
                  return CompanionCombatRescueService.findSpawn(player, null, summonMoveType, companionRescueTarget);
               } else {
                  return kind != CompanionKind.MOUNT && !airborneGroundCompanion
                     ? CompanionSpawnPlacementService.findSummonSpot(player, kind, summonMoveType)
                     : CompanionSpawnPlacementService.findArrivalSpawn(player, summonMoveType, restoreRescue);
               }
            }
         )
         .orElseGet(
            () -> {
               if (companionRescue) {
                  return CompanionCombatRescueService.findSpawn(player, null, storedMoveType, companionRescueTarget);
               } else {
                  return !airborneGroundCompanion && !airborneGroundMountSwitch && !airborneGroundVehicleRescue
                     ? CompanionSpawnPlacementService.findSummonSpot(player, kind, CompanionMoveType.WALK)
                     : CompanionSpawnPlacementService.findArrivalSpawn(player, storedMountMoveType, true);
               }
            }
         );
      return new CompanionSummonPlacementService.StoredRestore(pos, restoreRescue, directFlyingRescue);
   }

   static BlockPos findLiveMoveTarget(
      ServerPlayer player,
      CompanionKind kind,
      LivingEntity living,
      CompanionMoveType moveType,
      CompanionMoveType summonMoveType,
      MountCinematicMode mode,
      boolean airborneGroundCompanion,
      LivingEntity companionRescueTarget
   ) {
      if (kind == CompanionKind.MOUNT) {
         return CompanionSpawnPlacementService.findArrivalSpawn(player, moveType, mode.isRescue());
      } else if (companionRescueTarget != null) {
         return CompanionCombatRescueService.findSpawn(player, living, summonMoveType, companionRescueTarget);
      } else {
         return airborneGroundCompanion
            ? CompanionSpawnPlacementService.findArrivalSpawn(player, summonMoveType, true)
            : CompanionSpawnPlacementService.findSummonSpot(player, kind, summonMoveType);
      }
   }

   static Optional<BlockPos> adjustForOpenLiveSpace(ServerPlayer player, LivingEntity living, BlockPos target, MountCinematicMode mode) {
      return !mode.isRescue()
            && !CompanionSpawnPlacementService.hasOpenEntitySpace(player.level(), living, target.getX() + 0.5, target.getY(), target.getZ() + 0.5)
         ? CompanionSpawnPlacementService.findOpenEntitySpace(player.level(), living, target)
         : Optional.of(target);
   }

   static BlockPos findMountRescueRestoreSpawn(ServerPlayer player, CompanionMoveType moveType) {
      return CompanionSpawnPlacementService.findArrivalSpawn(player, moveType, true);
   }

   record StoredRestore(BlockPos pos, boolean preloadChunkArea, boolean directFlyingRescue) {
   }
}
