package com.kuzhi.findme.server;

import com.kuzhi.findme.Config;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

final class CompanionPlacementFinder {
   private CompanionPlacementFinder() {
   }

   static Optional<BlockPos> findWater(ServerLevel level, BlockPos origin) {
      int radius = Config.safeSearchRadius;

      for (int y = -4; y <= 4; y++) {
         for (int r = 0; r <= radius; r++) {
            for (int x = -r; x <= r; x++) {
               for (int z = -r; z <= r; z++) {
                  BlockPos candidate = origin.offset(x, y, z);
                  if ((r <= 0 || Math.abs(x) == r || Math.abs(z) == r)
                     && level.getFluidState(candidate).is(FluidTags.WATER)
                     && level.getFluidState(candidate.above()).is(FluidTags.WATER)) {
                     return Optional.of(candidate);
                  }
               }
            }
         }
      }

      return Optional.empty();
   }

   static Optional<BlockPos> findWaterWithOpenSurface(ServerLevel level, BlockPos origin) {
      Optional<BlockPos> direct = findWater(level, origin);
      if (direct.isPresent() && isWaterArrivalOpen(level, direct.get())) {
         return direct;
      }

      int radius = Config.safeSearchRadius;

      for (int y = -6; y <= 6; y++) {
         for (int r = 0; r <= radius; r++) {
            for (int x = -r; x <= r; x++) {
               for (int z = -r; z <= r; z++) {
                  BlockPos candidate = origin.offset(x, y, z);
                  if ((r <= 0 || Math.abs(x) == r || Math.abs(z) == r)
                     && level.getFluidState(candidate).is(FluidTags.WATER)
                     && isWaterArrivalOpen(level, candidate)) {
                     return Optional.of(candidate);
                  }
               }
            }
         }
      }

      return Optional.empty();
   }

   private static boolean isWaterArrivalOpen(ServerLevel level, BlockPos waterPos) {
      return !level.getFluidState(waterPos).isEmpty()
         && hasOpenBox(level, new AABB(waterPos.getX(), waterPos.getY(), waterPos.getZ(), waterPos.getX() + 1.0, waterPos.getY() + 2.2, waterPos.getZ() + 1.0));
   }

   static Optional<BlockPos> findSafe(ServerLevel level, BlockPos origin) {
      if (isSafe(level, origin)) {
         return Optional.of(origin);
      }

      int radius = Config.safeSearchRadius;

      for (int y = -3; y <= 3; y++) {
         for (int r = 1; r <= radius; r++) {
            for (int x = -r; x <= r; x++) {
               for (int z = -r; z <= r; z++) {
                  BlockPos candidate = origin.offset(x, y, z);
                  if ((Math.abs(x) == r || Math.abs(z) == r) && isSafe(level, candidate)) {
                     return Optional.of(candidate);
                  }
               }
            }
         }
      }

      return Optional.empty();
   }

   static boolean isSafe(ServerLevel level, BlockPos pos) {
      return isLandingSurface(level, pos.below())
         && isOpenForLanding(level, pos)
         && level.getBlockState(pos.above()).isAir()
         && !isDangerous(level, pos)
         && !isDangerous(level, pos.above())
         && !isDangerous(level, pos.below());
   }

   static boolean hasOpenEntitySpace(ServerLevel level, LivingEntity entity, double x, double y, double z) {
      double width = Math.max(0.9, entity.getBbWidth() + 0.55);
      double height = Math.max(1.8, entity.getBbHeight() + 0.25);
      AABB box = new AABB(x - width * 0.5, y, z - width * 0.5, x + width * 0.5, y + height, z + width * 0.5);
      return hasOpenBox(level, box);
   }

   static boolean hasOpenBox(ServerLevel level, BlockPos pos, double width, double height) {
      double boxWidth = Math.max(0.9, width + 0.75);
      double boxHeight = Math.max(1.8, height + 0.35);
      AABB box = new AABB(
         pos.getX() + 0.5 - boxWidth * 0.5,
         pos.getY(),
         pos.getZ() + 0.5 - boxWidth * 0.5,
         pos.getX() + 0.5 + boxWidth * 0.5,
         pos.getY() + boxHeight,
         pos.getZ() + 0.5 + boxWidth * 0.5
      );
      return hasOpenBox(level, box);
   }

   static boolean hasOpenBox(ServerLevel level, AABB box) {
      BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
      BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);

      for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
         VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
         if (!shape.isEmpty()) {
            for (AABB blockBox : shape.toAabbs()) {
               if (blockBox.move(pos).intersects(box)) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   static Optional<BlockPos> findOpenEntitySpace(ServerLevel level, LivingEntity entity, BlockPos origin) {
      if (isSafe(level, origin) && hasOpenEntitySpace(level, entity, origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5)) {
         return Optional.of(origin);
      }

      int radius = Math.max(2, Config.safeSearchRadius);

      for (int y = -3; y <= 3; y++) {
         for (int r = 1; r <= radius; r++) {
            for (int x = -r; x <= r; x++) {
               for (int z = -r; z <= r; z++) {
                  BlockPos candidate = origin.offset(x, y, z);
                  if ((Math.abs(x) == r || Math.abs(z) == r)
                     && isSafe(level, candidate)
                     && hasOpenEntitySpace(level, entity, candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5)) {
                     return Optional.of(candidate);
                  }
               }
            }
         }
      }

      return Optional.empty();
   }

   static boolean isLandingSurface(Level level, BlockPos pos) {
      return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
   }

   static boolean isOpenForLanding(Level level, BlockPos pos) {
      VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
      return shape.isEmpty() || shape.max(Axis.Y) <= 0.25;
   }

   private static boolean isDangerous(ServerLevel level, BlockPos pos) {
      return level.getBlockState(pos).is(Blocks.LAVA)
         || level.getBlockState(pos).is(Blocks.FIRE)
         || level.getBlockState(pos).is(Blocks.SOUL_FIRE)
         || level.getFluidState(pos).is(FluidTags.LAVA);
   }
}
