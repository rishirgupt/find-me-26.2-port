package com.kuzhi.findme.client;

import com.kuzhi.findme.network.ContractCameraPacket;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

public final class ClientContractCamera {
   private static CameraType previousCameraType;
   private static Entity previousCameraEntity;
   private static ArmorStand cameraEntity;
   private static int targetEntityId = -1;
   private static int ticksRemaining;
   private static int totalTicks;
   private static Vec3 playerAnchor = Vec3.ZERO;
   private static Vec3 targetAnchor = Vec3.ZERO;
   private static float targetRadius = 2.2F;
   private static double sideSign = 1.0;

   private ClientContractCamera() {
   }

   public static void start(ContractCameraPacket packet) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.player != null && minecraft.level != null) {
         previousCameraType = minecraft.options.getCameraType();
         previousCameraEntity = minecraft.getCameraEntity();
         targetEntityId = packet.targetEntityId();
         ticksRemaining = Math.max(packet.durationTicks(), 20);
         totalTicks = ticksRemaining;
         playerAnchor = new Vec3(packet.playerX(), packet.playerY(), packet.playerZ());
         targetAnchor = new Vec3(packet.targetX(), packet.targetY(), packet.targetZ());
         targetRadius = packet.targetRadius();
         sideSign = chooseSideSign(minecraft);
         cameraEntity = new ArmorStand(minecraft.level, minecraft.player.getX(), minecraft.player.getY() + 2.0, minecraft.player.getZ());
         cameraEntity.setInvisible(true);
         cameraEntity.setNoGravity(true);
         cameraEntity.noPhysics = true;
         updateCamera(minecraft, 0.0F);
         minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
         HandVisibility.set(false);
      }
   }

   /**
    * Where the ceremony wants the view to be, or null when no ceremony is running. The camera stays attached
    * to the player (so the player is still drawn) and a mixin moves the viewpoint here.
    */
   public static Entity viewpoint() {
      return ticksRemaining > 0 ? cameraEntity : null;
   }

   public static void finish(int durationTicks) {
      stop();
   }

   public static void stop() {
      Minecraft minecraft = Minecraft.getInstance();
      if (previousCameraType != null) {
         minecraft.options.setCameraType(previousCameraType);
      }

      HandVisibility.set(true);
      previousCameraType = null;
      previousCameraEntity = null;
      cameraEntity = null;
      targetEntityId = -1;
      ticksRemaining = 0;
      totalTicks = 0;
      playerAnchor = Vec3.ZERO;
      targetAnchor = Vec3.ZERO;
      targetRadius = 2.2F;
      sideSign = 1.0;
   }

   public static void tick() {
      if (ticksRemaining <= 0) {
         if (cameraEntity != null) {
            stop();
         }
      } else {
         Minecraft minecraft = Minecraft.getInstance();
         if (minecraft.player != null && minecraft.level != null && cameraEntity != null) {
            updateCamera(minecraft, 1.0F - (float)ticksRemaining / Math.max(totalTicks, 1));
            if (minecraft.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
               minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }

            HandVisibility.set(false);
            ticksRemaining--;
         } else {
            stop();
         }
      }
   }

   private static void updateCamera(Minecraft minecraft, float progress) {
      ClientLevel level = minecraft.level;
      if (minecraft.player != null && level != null && cameraEntity != null) {
         Entity target = level.getEntity(targetEntityId);
         if (target == null) {
            stop();
         } else {
            Vec3 playerBase = playerAnchor;
            Vec3 targetBase = targetAnchor;
            double targetHeight = target.getBbHeight();
            double targetWidth = target.getBbWidth();
            Vec3 playerCenter = playerBase.add(0.0, Math.max(1.2, minecraft.player.getBbHeight() * 0.72), 0.0);
            Vec3 targetCenter = targetBase.add(0.0, Math.max(1.0, targetHeight * 0.48), 0.0);
            Vec3 midpoint = playerCenter.add(targetCenter).scale(0.5);
            Vec3 axis = horizontal(targetCenter.subtract(playerCenter));
            if (axis.lengthSqr() < 0.01) {
               axis = horizontal(minecraft.player.getLookAngle());
            }

            if (axis.lengthSqr() < 0.01) {
               axis = new Vec3(1.0, 0.0, 0.0);
            }

            axis = axis.normalize();
            Vec3 side = new Vec3(-axis.z, 0.0, axis.x);
            side = side.scale(sideSign);
            double separation = Math.sqrt(horizontal(targetCenter.subtract(playerCenter)).lengthSqr());
            double distance = Mth.clamp(separation * 0.58 + targetWidth * 1.05 + 4.4, 6.5, 19.0);
            double height = Mth.clamp(Math.max(minecraft.player.getBbHeight(), targetHeight) * 0.3 + 2.25, 2.8, 8.2);
            Vec3 cameraPos = midpoint.add(side.scale(distance)).add(0.0, height, 0.0);
            Vec3 lookAt = midpoint.add(0.0, Math.max(0.8, targetHeight * 0.12), 0.0);
            float yaw = yawToward(cameraPos, lookAt);
            float pitch = pitchToward(cameraPos, lookAt);
            cameraEntity.snapTo(cameraPos.x, cameraPos.y, cameraPos.z, yaw, pitch);
            cameraEntity.setYHeadRot(yaw);
            cameraEntity.yRotO = yaw;
            cameraEntity.xRotO = pitch;
         }
      }
   }

   private static double chooseSideSign(Minecraft minecraft) {
      Vec3 playerCenter = playerAnchor.add(0.0, Math.max(1.2, minecraft.player.getBbHeight() * 0.72), 0.0);
      Vec3 targetCenter = targetAnchor.add(0.0, Math.max(1.0, targetRadius * 0.8), 0.0);
      Vec3 axis = horizontal(targetCenter.subtract(playerCenter));
      if (axis.lengthSqr() < 0.01) {
         axis = horizontal(minecraft.player.getLookAngle());
      }

      if (axis.lengthSqr() < 0.01) {
         axis = new Vec3(1.0, 0.0, 0.0);
      }

      Vec3 side = new Vec3(-axis.normalize().z, 0.0, axis.normalize().x);
      return horizontal(minecraft.player.getLookAngle()).dot(side) < 0.0 ? -1.0 : 1.0;
   }

   private static Vec3 horizontal(Vec3 vector) {
      return new Vec3(vector.x, 0.0, vector.z);
   }

   private static float yawToward(Vec3 from, Vec3 to) {
      double dx = to.x - from.x;
      double dz = to.z - from.z;
      return Mth.wrapDegrees((float)(Mth.atan2(dz, dx) * 180.0F / (float)Math.PI) - 90.0F);
   }

   private static float pitchToward(Vec3 from, Vec3 to) {
      double dx = to.x - from.x;
      double dy = to.y - from.y;
      double dz = to.z - from.z;
      double horizontal = Math.sqrt(dx * dx + dz * dz);
      return Mth.clamp((float)(-(Mth.atan2(dy, horizontal) * 180.0F / (float)Math.PI)), -35.0F, 42.0F);
   }
}
