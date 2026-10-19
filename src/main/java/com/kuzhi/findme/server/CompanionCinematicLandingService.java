package com.kuzhi.findme.server;

import com.kuzhi.findme.common.CompanionMoveType;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

final class CompanionCinematicLandingService {
   private static final double DIRECT_FLYING_RESCUE_SAFETY_TICKS = 15.0;

   private CompanionCinematicLandingService() {
   }

   static double distanceToGround(ServerPlayer player) {
      if (player.level() instanceof ServerLevel serverLevel) {
         return !hasReliableLandingBelow(serverLevel, player)
            ? Double.POSITIVE_INFINITY
            : Math.max(0.0, player.getY() - predictedLanding(serverLevel, player).getY());
      } else {
         return Math.max(0.0, player.fallDistance);
      }
   }

   static double flyingCatchHeight(ServerPlayer player) {
      double groundDistance = distanceToGround(player);
      return groundDistance <= 18.0
         ? Math.max(4.0, groundDistance - 2.0)
         : Math.min(Math.max(groundDistance * 0.45, 18.0), Math.min(42.0, groundDistance - 6.0));
   }

   static boolean shouldDirectFlyingRescue(ServerPlayer player) {
      if (player.level() instanceof ServerLevel serverLevel) {
         if (!hasReliableLandingBelow(serverLevel, player)) {
            return true;
         }

         double groundDistance = distanceToGround(player);
         if (groundDistance <= 18.0) {
            return true;
         }

         double fallSpeed = -player.getDeltaMovement().y;
         if (fallSpeed <= 0.05) {
            return false;
         }

         double catchY = predictedLanding(serverLevel, player).getY() + flyingCatchHeight(player);
         double blocksUntilCatch = Math.max(0.0, player.getY() - catchY);
         double ticksUntilCatch = blocksUntilCatch / Math.max(0.35, fallSpeed);
         return ticksUntilCatch <= 15.0;
      } else {
         return false;
      }
   }

   static void preloadChunkArea(ServerLevel level, BlockPos center, int radius) {
      int chunkX = center.getX() >> 4;
      int chunkZ = center.getZ() >> 4;

      for (int x = -radius; x <= radius; x++) {
         for (int z = -radius; z <= radius; z++) {
            level.getChunk(chunkX + x, chunkZ + z);
         }
      }
   }

   static Vec3 cinematicTarget(PendingMountCinematic cinematic, Level level, ServerPlayer player) {
      if (cinematic.mode().isRescue()) {
         if (cinematic.moveType() == CompanionMoveType.WALK && level instanceof ServerLevel serverLevel) {
            BlockPos landing = rescueAnchor(serverLevel, player);
            return Vec3.atBottomCenterOf(landing).add(0.0, rescueGroundYOffset(cinematic), 0.0);
         } else if (cinematic.moveType() == CompanionMoveType.SWIM && level instanceof ServerLevel serverLevel) {
            BlockPos landing = rescueAnchor(serverLevel, player);
            return Vec3.atBottomCenterOf(landing);
         } else if (!(cinematic.moveType() == CompanionMoveType.FLY && level instanceof ServerLevel serverLevel) || Double.isNaN(cinematic.catchY())) {
            Vec3 predicted = player.position().add(player.getDeltaMovement().scale(8.0));
            double yOffset = cinematic.moveType() == CompanionMoveType.FLY && distanceToGround(player) <= 20.0 ? -1.4 : -5.0;
            if (cinematic.moveType() == CompanionMoveType.FLY && level instanceof ServerLevel serverLevel) {
               double minY = hasReliableLandingBelow(serverLevel, player) ? predictedLanding(serverLevel, player).getY() + 1.2 : player.getY() - 1.2;
               return new Vec3(predicted.x, Math.max(predicted.y + yOffset, minY), predicted.z);
            } else {
               return predicted.add(0.0, yOffset, 0.0);
            }
         } else {
            if (!cinematic.flyingRescueStaged() && cinematic.flyingStageTarget() != null) {
               return cinematic.flyingStageTarget();
            }

            BlockPos landing = rescueAnchor(serverLevel, player);
            return new Vec3(landing.getX() + 0.5, cinematic.catchY(), landing.getZ() + 0.5);
         }
      } else {
         return player.position();
      }
   }

   static BlockPos predictedLanding(ServerLevel level, ServerPlayer player) {
      return findPredictedLanding(level, player).orElse(player.blockPosition());
   }

   static BlockPos rescueAnchor(ServerLevel level, ServerPlayer player) {
      return findPredictedLanding(level, player).orElse(BlockPos.containing(player.getX(), player.getY() - 1.0, player.getZ()));
   }

   static boolean hasReliableLandingBelow(ServerLevel level, ServerPlayer player) {
      return findPredictedLanding(level, player).isPresent();
   }

   private static Optional<BlockPos> findPredictedLanding(ServerLevel level, ServerPlayer player) {
      MutableBlockPos pos = player.blockPosition().mutable();
      int minY = level.getMinY();

      while (pos.getY() > minY) {
         if (!level.getFluidState(pos).isEmpty()) {
            return Optional.of(pos.above().immutable());
         }

         BlockPos below = pos.below();
         if (!level.getFluidState(below).isEmpty()) {
            return Optional.of(pos.immutable());
         }

         if (CompanionPlacementFinder.isLandingSurface(level, below) && CompanionPlacementFinder.isOpenForLanding(level, pos)) {
            return Optional.of(pos.immutable());
         }

         pos.move(0, -1, 0);
      }

      return Optional.empty();
   }

   static double walkGroundY(Level level, double x, double currentY, double z, double fallbackY) {
      int startY = Mth.floor(Math.max(currentY, fallbackY) + 6.0);
      int minY = Math.max(level.getMinY(), Mth.floor(Math.min(currentY, fallbackY) - 16.0));
      MutableBlockPos pos = BlockPos.containing(x, startY, z).mutable();

      while (pos.getY() >= minY) {
         BlockPos below = pos.below();
         if (CompanionPlacementFinder.isLandingSurface(level, below)
            && CompanionPlacementFinder.isOpenForLanding(level, pos)
            && level.getBlockState(pos.above()).isAir()) {
            return landingSurfaceY(level, below);
         }

         pos.move(0, -1, 0);
      }

      return fallbackY;
   }

   static double rescueApproachDistance(double fallHeight, boolean flying) {
      double height = Math.max(0.0, fallHeight);
      if (flying) {
         return height <= 80.0 ? Mth.clamp(12.0 + height * 0.45, 16.0, 48.0) : Math.min(192.0, 48.0 + Math.sqrt(height - 80.0) * 3.7);
      } else {
         return height <= 80.0 ? Mth.clamp(10.0 + height * 0.5, 14.0, 52.0) : Math.min(144.0, 52.0 + Math.sqrt(height - 80.0) * 2.3);
      }
   }

   static double rescueGroundYOffset(PendingMountCinematic cinematic) {
      return cinematic.mode().isGroundOrWaterRescue() && cinematic.moveType() == CompanionMoveType.WALK ? 0.18 : 0.0;
   }

   static double landingSurfaceY(Level level, BlockPos surfacePos) {
      VoxelShape shape = level.getBlockState(surfacePos).getCollisionShape(level, surfacePos);
      return surfacePos.getY() + (shape.isEmpty() ? 0.0 : shape.max(Axis.Y));
   }
}
