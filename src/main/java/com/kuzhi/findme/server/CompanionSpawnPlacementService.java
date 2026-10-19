package com.kuzhi.findme.server;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

final class CompanionSpawnPlacementService {
   private CompanionSpawnPlacementService() {
   }

   static BlockPos findSummonSpot(ServerPlayer player, CompanionKind kind, CompanionMoveType moveType) {
      if (moveType == CompanionMoveType.SWIM) {
         Optional<BlockPos> water = CompanionPlacementFinder.findWaterWithOpenSurface(player.level(), player.blockPosition());
         if (water.isPresent()) {
            return water.get();
         }

         CompanionSummonLineService.showNoWater(player, kind);
      }

      Vec3 base = kind == CompanionKind.MOUNT ? player.position().add(player.getLookAngle().normalize().scale(2.0)) : player.position().add(0.0, 0.0, 1.0);
      return CompanionPlacementFinder.findSafe(player.level(), BlockPos.containing(base)).orElse(player.blockPosition());
   }

   static BlockPos findArrivalSpawn(ServerPlayer player, CompanionMoveType moveType, boolean rescue) {
      Vec3 look = player.getLookAngle().normalize();
      Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
      if (horizontal.lengthSqr() < 0.001) {
         horizontal = new Vec3(0.0, 0.0, 1.0);
      }

      horizontal = horizontal.normalize();
      if (moveType == CompanionMoveType.FLY) {
         if (rescue) {
            BlockPos landing = CompanionCinematicLandingService.rescueAnchor(player.level(), player);
            if (CompanionCinematicLandingService.shouldDirectFlyingRescue(player)) {
               return BlockPos.containing(landing.getX() + 0.5, CompanionCinematicPositionService.directFlyingCatchY(player), landing.getZ() + 0.5);
            }

            double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
            Vec3 direction = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
            double horizontalDistance = CompanionCinematicLandingService.rescueApproachDistance(CompanionCinematicLandingService.distanceToGround(player), true);
            Vec3 start = new Vec3(landing.getX() + 0.5, CompanionCinematicPositionService.directFlyingCatchY(player), landing.getZ() + 0.5)
               .add(direction.scale(horizontalDistance));
            return BlockPos.containing(start);
         } else {
            Vec3 behind = player.position().subtract(horizontal.scale(34.0)).add(0.0, 7.0, 0.0);
            return BlockPos.containing(behind);
         }
      } else if (moveType == CompanionMoveType.SWIM) {
         BlockPos origin = rescue ? CompanionCinematicLandingService.rescueAnchor(player.level(), player) : player.blockPosition();
         Optional<BlockPos> water = CompanionPlacementFinder.findWaterWithOpenSurface(player.level(), origin);
         if (water.isPresent()) {
            return water.get();
         } else if (rescue && !CompanionCinematicLandingService.hasReliableLandingBelow(player.level(), player)) {
            CompanionSummonLineService.showNoWater(player, CompanionKind.MOUNT);
            return origin;
         } else {
            return findSummonSpot(player, CompanionKind.MOUNT, moveType);
         }
      } else {
         Vec3 ahead;
         if (rescue) {
            BlockPos landing = CompanionCinematicLandingService.rescueAnchor(player.level(), player);
            if (CompanionCinematicLandingService.distanceToGround(player) <= 18.0) {
               return walkSurfacePos(player.level(), landing, landing.getY());
            }

            double groundDistance = CompanionCinematicLandingService.distanceToGround(player);
            double runDistance = CompanionCinematicLandingService.rescueApproachDistance(groundDistance, false);
            ahead = Vec3.atBottomCenterOf(landing).subtract(horizontal.scale(runDistance));
         } else {
            ahead = player.position().subtract(horizontal.scale(22.0));
         }

         BlockPos base = BlockPos.containing(ahead);
         return CompanionPlacementFinder.findSafe(player.level(), base).orElseGet(() -> walkSurfacePos(player.level(), base, base.getY()));
      }
   }

   static BlockPos walkSurfacePos(ServerLevel level, BlockPos base, double fallbackY) {
      double y = CompanionCinematicLandingService.walkGroundY(level, base.getX() + 0.5, fallbackY, base.getZ() + 0.5, fallbackY);
      return BlockPos.containing(base.getX(), y, base.getZ());
   }

   static boolean hasOpenBox(ServerLevel level, BlockPos pos, double width, double height) {
      return CompanionPlacementFinder.hasOpenBox(level, pos, width, height);
   }

   static boolean hasOpenEntitySpace(ServerLevel level, LivingEntity entity, double x, double y, double z) {
      return CompanionPlacementFinder.hasOpenEntitySpace(level, entity, x, y, z);
   }

   static Optional<BlockPos> findOpenEntitySpace(ServerLevel level, LivingEntity entity, BlockPos origin) {
      return CompanionPlacementFinder.findOpenEntitySpace(level, entity, origin);
   }
}
