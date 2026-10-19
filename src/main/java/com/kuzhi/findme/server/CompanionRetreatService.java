package com.kuzhi.findme.server;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

final class CompanionRetreatService {
   private static final List<PendingRetreat> PENDING_RETREATS = new ArrayList<>();

   private CompanionRetreatService() {
   }

   static void tickRetreats(MinecraftServer server) {
      Iterator<PendingRetreat> iterator = PENDING_RETREATS.iterator();

      while (iterator.hasNext()) {
         PendingRetreat retreat = iterator.next();
         Entity entity = CompanionManager.findEntity(server, retreat.entityUuid()).orElse(null);
         if (entity instanceof LivingEntity living && living.isAlive()) {
            CompanionManager.restoreAnimationControl(living);
            Vec3 direction = retreat.direction();
            if (living instanceof Mob mob) {
               mob.getNavigation().stop();
               mob.setTarget(null);
            }

            Vec3 next = living.position().add(direction.scale(0.55));
            float lookYaw = CompanionCinematicOrientationHelper.yawToward(direction);
            living.snapTo(next.x, next.y, next.z, lookYaw, living.getXRot());
            living.setPos(next.x, next.y, next.z);
            CompanionCinematicOrientationHelper.faceYaw(living, lookYaw);
            living.setDeltaMovement(direction.scale(0.55));
            living.fallDistance = 0.0F;
            living.invulnerableTime = Math.max(living.invulnerableTime, Config.postTeleportInvulnerabilityTicks);
            living.hurtMarked = true;
            retreat.incrementAge();
            ServerPlayer player = server.getPlayerList().getPlayer(retreat.playerUuid());
            boolean pulledBackToPlayer = player != null && retreat.age() > 8 && living.distanceTo(player) < 8.0F;
            boolean pulledBackToOrigin = retreat.age() > 8 && living.position().distanceTo(retreat.origin()) < 8.0;
            if (!(living.position().distanceTo(retreat.origin()) < 32.0) || retreat.age() > 55 || pulledBackToPlayer || pulledBackToOrigin) {
               if (player != null) {
                  PlayerCompanionData data = CompanionManager.data(player);
                  if (retreat.kind() != null
                     && data.homeNestBlock(living.getUUID()).isPresent()
                     && CompanionManager.sendHomeIfPossible(player, data, retreat.kind(), living)) {
                     data.clearDeployed(retreat.kind(), living.getUUID());
                     CompanionManager.save(player, data);
                     CompanionManager.syncToClient(player, retreat.kind());
                     iterator.remove();
                     continue;
                  }

                  CompanionManager.storeEntity(player, data, living);
                  if (retreat.kind() != null) {
                     data.clearDeployed(retreat.kind(), living.getUUID());
                  }

                  CompanionManager.save(player, data);
                  if (retreat.kind() != null) {
                     CompanionManager.syncToClient(player, retreat.kind());
                  }
               }

               CompanionManager.spawnBlinkParticles(living);
               CompanionStorageService.markIntentionalStorageRemoval(server, living.getUUID());
               living.discard();
               iterator.remove();
            }
         } else {
            iterator.remove();
         }
      }
   }

   static void cancelRetreat(LivingEntity living) {
      PENDING_RETREATS.removeIf(retreat -> retreat.entityUuid().equals(living.getUUID()));
   }

   static void sendAway(Entity entity, ServerPlayer player, PlayerCompanionData data) {
      if (entity instanceof LivingEntity living) {
         living.stopRiding();
         if (living instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
         }

         cancelRetreat(living);
         CompanionKind kind = kindOf(data, living);
         PENDING_RETREATS.add(new PendingRetreat(living.getUUID(), player.position(), randomRetreatDirection(player), player.getUUID(), kind));
         if (kind != null) {
            CompanionManager.syncToClient(player, kind);
         }
      }
   }

   private static CompanionKind kindOf(PlayerCompanionData data, LivingEntity living) {
      if (data != null && living != null) {
         if (data.contains(CompanionKind.MOUNT, living.getUUID())) {
            return CompanionKind.MOUNT;
         } else {
            return data.contains(CompanionKind.COMPANION, living.getUUID()) ? CompanionKind.COMPANION : null;
         }
      } else {
         return null;
      }
   }

   private static Vec3 randomRetreatDirection(ServerPlayer player) {
      Vec3 look = player.getLookAngle();
      Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
      if (horizontal.lengthSqr() < 0.001) {
         horizontal = new Vec3(0.0, 0.0, 1.0);
      }

      horizontal = horizontal.normalize();
      int choice = player.getRandom().nextInt(3);
      if (choice == 1) {
         return new Vec3(-horizontal.z, 0.0, horizontal.x).normalize();
      } else {
         return choice == 2 ? new Vec3(horizontal.z, 0.0, -horizontal.x).normalize() : horizontal.reverse();
      }
   }
}
