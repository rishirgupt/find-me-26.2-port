package com.kuzhi.findme.server;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

final class CompanionCinematicOrientationHelper {
   private CompanionCinematicOrientationHelper() {
   }

   static float yawToward(Vec3 direction) {
      return (float)(Mth.atan2(direction.z, direction.x) * 180.0 / Math.PI) - 90.0F;
   }

   static float yawTowardStable(LivingEntity living, Vec3 direction) {
      Vec3 horizontal = new Vec3(direction.x, 0.0, direction.z);
      return horizontal.lengthSqr() < 0.0025 ? living.getYRot() : yawToward(horizontal.normalize());
   }

   static float pitchToward(Vec3 direction) {
      double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
      return Mth.clamp((float)(-(Mth.atan2(direction.y, horizontal) * 180.0 / Math.PI)), -35.0F, 35.0F);
   }

   static void faceYaw(LivingEntity living, float yaw) {
      living.setYRot(yaw);
      living.setYHeadRot(yaw);
      living.yRotO = yaw;
      living.yBodyRot = yaw;
      living.yBodyRotO = yaw;
      living.yHeadRot = yaw;
      living.yHeadRotO = yaw;
   }
}
