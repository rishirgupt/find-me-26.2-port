package com.kuzhi.findme.server;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

final class CompanionMountSettleProtectionService {
   private static final Map<UUID, SettledMountProtection> RECENT_SETTLED_MOUNTS = new HashMap<>();
   private static final int MOUNT_SETTLE_PROTECTION_TICKS = 10;

   private CompanionMountSettleProtectionService() {
   }

   static void rememberSettledMount(ServerPlayer player, LivingEntity mount) {
      long until = player.level().getGameTime() + 10L;
      RECENT_SETTLED_MOUNTS.put(player.getUUID(), new SettledMountProtection(mount.getUUID(), until));
   }

   static boolean isMountSettleProtected(ServerPlayer player) {
      SettledMountProtection protection = RECENT_SETTLED_MOUNTS.get(player.getUUID());
      if (protection == null) {
         return false;
      } else {
         long now = player.level().getGameTime();
         if (now > protection.untilTick()) {
            RECENT_SETTLED_MOUNTS.remove(player.getUUID());
            return false;
         } else {
            Entity vehicle = player.getVehicle();
            if (vehicle != null && vehicle.getUUID().equals(protection.mountUuid())) {
               PlayerCompanionData data = CompanionManager.data(player);
               return data.deployed(CompanionKind.MOUNT).map(protection.mountUuid()::equals).orElse(false);
            } else {
               return false;
            }
         }
      }
   }

   static void tickSettledMountProtections(MinecraftServer server) {
      Iterator<Entry<UUID, SettledMountProtection>> iterator = RECENT_SETTLED_MOUNTS.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<UUID, SettledMountProtection> entry = iterator.next();
         ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
         if (player == null) {
            iterator.remove();
         } else {
            SettledMountProtection protection = entry.getValue();
            long now = player.level().getGameTime();
            if (now > protection.untilTick()) {
               iterator.remove();
            } else {
               Entity vehicle = player.getVehicle();
               if (vehicle != null) {
                  if (vehicle.getUUID().equals(protection.mountUuid())) {
                     player.fallDistance = 0.0F;
                     vehicle.fallDistance = 0.0F;
                  } else {
                     iterator.remove();
                  }
               } else if (!player.onGround() && !(player.getDeltaMovement().y >= -0.08)) {
                  Entity mount = CompanionManager.findEntity(server, protection.mountUuid()).orElse(null);
                  if (!(mount instanceof LivingEntity living && living.isAlive() && living.level() == player.level())) {
                     iterator.remove();
                  } else if ((
                        CompanionMountCinematicFlowService.isTouchingMountCollision(player, living, null)
                           || !(player.distanceTo(living) > Math.max(4.0F, living.getBbWidth() + 2.0F))
                     )
                     && CompanionMountCinematicFlowService.startRidingAfterContact(player, living, MountCinematicMode.NORMAL_SUMMON)) {
                     player.fallDistance = 0.0F;
                     living.fallDistance = 0.0F;
                     player.invulnerableTime = Math.max(player.invulnerableTime, Config.postTeleportInvulnerabilityTicks);
                     living.invulnerableTime = Math.max(living.invulnerableTime, Config.postTeleportInvulnerabilityTicks);
                  }
               }
            }
         }
      }
   }
}
