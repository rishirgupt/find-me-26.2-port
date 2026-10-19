package com.kuzhi.findme.server;

import com.kuzhi.findme.network.RescueMagicPacket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

final class CompanionArrivalSequenceService {
   private static final int DEFAULT_SUMMON_REVEAL_DELAY_TICKS = 16;
   private static final int DEFAULT_RESCUE_REVEAL_DELAY_TICKS = 11;
   private static final Map<UUID, CompanionArrivalSequenceService.PendingArrival> PENDING = new HashMap<>();

   private CompanionArrivalSequenceService() {
   }

   static void start(ServerPlayer player, LivingEntity living, Vec3 focus) {
      start(player, living, focus, RescueMagicPacket.Purpose.SUMMON);
   }

   static void start(ServerPlayer player, LivingEntity living, Vec3 focus, RescueMagicPacket.Purpose purpose) {
      if (living != null && !living.level().isClientSide()) {
         boolean noAi = living instanceof Mob mob && mob.isNoAi();
         startWithCapturedState(player, living, focus, purpose, noAi);
      }
   }

   static void startWithCapturedState(ServerPlayer player, LivingEntity living, Vec3 focus, RescueMagicPacket.Purpose purpose, boolean noAi) {
      if (living != null && !living.level().isClientSide()) {
         UUID uuid = living.getUUID();
         CompanionArrivalSequenceService.PendingArrival previous = PENDING.remove(uuid);
         if (previous != null) {
            previous.restore(living);
         }

         CompanionArrivalSequenceService.PendingArrival pending = CompanionArrivalSequenceService.PendingArrival.capture(player, focus, purpose, noAi);
         pending.prepare(living);
         PENDING.put(uuid, pending);
      }
   }

   static void startWithDelay(ServerPlayer player, LivingEntity living, Vec3 focus, RescueMagicPacket.Purpose purpose, int revealDelayTicks) {
      if (living != null && !living.level().isClientSide()) {
         boolean noAi = living instanceof Mob mob && mob.isNoAi();
         startWithCapturedStateAndDelay(player, living, focus, purpose, noAi, revealDelayTicks);
      }
   }

   static void startWithCapturedStateAndDelay(
      ServerPlayer player, LivingEntity living, Vec3 focus, RescueMagicPacket.Purpose purpose, boolean noAi, int revealDelayTicks
   ) {
      if (living != null && !living.level().isClientSide()) {
         UUID uuid = living.getUUID();
         CompanionArrivalSequenceService.PendingArrival previous = PENDING.remove(uuid);
         if (previous != null) {
            previous.restore(living);
         }

         CompanionArrivalSequenceService.PendingArrival pending = CompanionArrivalSequenceService.PendingArrival.capture(
            player, focus, purpose, noAi, revealDelayTicks
         );
         pending.prepare(living);
         PENDING.put(uuid, pending);
      }
   }

   static int delayFor(RescueMagicPacket.Purpose purpose) {
      return purpose == RescueMagicPacket.Purpose.RESCUE ? 11 : 16;
   }

   static boolean isPending(LivingEntity living) {
      return living != null && PENDING.containsKey(living.getUUID());
   }

   static Optional<Boolean> originalNoAi(LivingEntity living) {
      if (living == null) {
         return Optional.empty();
      }

      CompanionArrivalSequenceService.PendingArrival pending = PENDING.get(living.getUUID());
      return pending == null ? Optional.empty() : Optional.of(pending.noAi());
   }

   static void tick(MinecraftServer server) {
      if (server != null && !PENDING.isEmpty()) {
         Iterator<Entry<UUID, CompanionArrivalSequenceService.PendingArrival>> iterator = PENDING.entrySet().iterator();

         while (iterator.hasNext()) {
            Entry<UUID, CompanionArrivalSequenceService.PendingArrival> entry = iterator.next();
            CompanionArrivalSequenceService.PendingArrival pending = entry.getValue();
            Entity entity = CompanionManager.findEntity(server, entry.getKey()).orElse(null);
            if (entity instanceof LivingEntity living && living.isAlive()) {
               CompanionArrivalSequenceService.PendingArrival next = pending.tick(living);
               if (next == null) {
                  iterator.remove();
               } else {
                  entry.setValue(next);
               }
            } else {
               CompanionSummonLineService.cancelScheduled(entry.getKey());
               iterator.remove();
            }
         }
      }
   }

   static void cancel(LivingEntity living) {
      if (living != null) {
         CompanionArrivalSequenceService.PendingArrival pending = PENDING.remove(living.getUUID());
         if (pending != null) {
            CompanionSummonLineService.cancelScheduled(living);
            pending.restore(living);
         }
      }
   }

   private record PendingArrival(int remainingTicks, boolean noAi, UUID playerUuid, Vec3 focus, RescueMagicPacket.Purpose purpose) {
      static CompanionArrivalSequenceService.PendingArrival capture(ServerPlayer player, Vec3 focus, RescueMagicPacket.Purpose purpose, boolean noAi) {
         return capture(player, focus, purpose, noAi, CompanionArrivalSequenceService.delayFor(purpose));
      }

      static CompanionArrivalSequenceService.PendingArrival capture(
         ServerPlayer player, Vec3 focus, RescueMagicPacket.Purpose purpose, boolean noAi, int revealDelayTicks
      ) {
         UUID playerUuid = player == null ? null : player.getUUID();
         RescueMagicPacket.Purpose safePurpose = purpose == null ? RescueMagicPacket.Purpose.SUMMON : purpose;
         return new CompanionArrivalSequenceService.PendingArrival(Math.max(1, revealDelayTicks), noAi, playerUuid, focus, safePurpose);
      }

      void prepare(LivingEntity living) {
         living.setDeltaMovement(Vec3.ZERO);
         living.fallDistance = 0.0F;
      }

      CompanionArrivalSequenceService.PendingArrival tick(LivingEntity living) {
         if (this.remainingTicks <= 1) {
            this.reveal(living);
            return null;
         } else {
            return new CompanionArrivalSequenceService.PendingArrival(this.remainingTicks - 1, this.noAi, this.playerUuid, this.focus, this.purpose);
         }
      }

      void restore(LivingEntity living) {
         if (living instanceof Mob mob) {
            mob.setNoAi(this.noAi);
         }
      }

      void reveal(LivingEntity living) {
         this.restore(living);
         Vec3 launch = launchVelocity(living, this.focus, this.purpose);
         living.setDeltaMovement(launch);
         living.hurtMarked = true;
         living.fallDistance = 0.0F;
         CompanionSummonLineService.showScheduled(living);
      }

      private static Vec3 launchVelocity(LivingEntity living, Vec3 focus, RescueMagicPacket.Purpose purpose) {
         Vec3 direction = living.position().subtract(focus == null ? living.position().add(0.0, 0.0, 1.0) : focus);
         direction = new Vec3(direction.x, 0.0, direction.z);
         if (direction.lengthSqr() < 0.01) {
            direction = living.getLookAngle();
            direction = new Vec3(direction.x, 0.0, direction.z);
         }

         if (direction.lengthSqr() < 0.01) {
            direction = new Vec3(0.0, 0.0, 1.0);
         }

         double sizeBoost = Math.min(0.22, Math.max(living.getBbWidth(), living.getBbHeight()) * 0.025);
         return purpose == RescueMagicPacket.Purpose.RESCUE
            ? direction.normalize().scale(0.46 + sizeBoost * 1.2).add(0.0, 0.34 + sizeBoost, 0.0)
            : direction.normalize().scale(0.2 + sizeBoost * 0.55).add(0.0, 0.18 + sizeBoost * 0.5, 0.0);
      }
   }
}
