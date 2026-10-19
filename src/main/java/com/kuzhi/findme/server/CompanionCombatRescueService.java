package com.kuzhi.findme.server;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.network.RescueMagicPacket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

final class CompanionCombatRescueService {
   private static final int COMBAT_RESCUE_CONTROL_TICKS = 80;
   private static final int RESCUE_INTENT_NUDGE_INTERVAL_TICKS = 10;
   private static final double RESCUE_NAVIGATION_SPEED = 1.35;
   private static final double RECENT_THREAT_RANGE = 40.0;
   private static final double SCAN_THREAT_RANGE = 32.0;
   private static final double OWNER_PULL_DISTANCE_SQR = 16.0;
   private static final double THREAT_FAR_FROM_OWNER_SQR = 36.0;
   private static final double LOST_TARGET_DISTANCE_SQR = 64.0;
   private static final Map<UUID, CompanionCombatRescueService.PendingCombatRescue> PENDING = new HashMap<>();

   private CompanionCombatRescueService() {
   }

   static Optional<LivingEntity> findThreat(ServerPlayer player) {
      Optional<LivingEntity> remembered = CompanionThreatMemoryService.findRecentThreat(player, 40.0);
      if (remembered.filter(threat -> isValidThreat(player, threat, 40.0)).isPresent()) {
         return remembered;
      }

      LivingEntity recent = player.getLastHurtByMob();
      if (isValidThreat(player, recent, 40.0)) {
         return Optional.of(recent);
      }

      ServerLevel level = player.level();
      LivingEntity best = null;
      double bestScore = Double.NEGATIVE_INFINITY;

      for (LivingEntity candidate : level.getEntitiesOfClass(
         LivingEntity.class, player.getBoundingBox().inflate(32.0), entity -> isValidThreat(player, entity, 32.0)
      )) {
         double score = threatScore(player, candidate);
         if (score > bestScore) {
            bestScore = score;
            best = candidate;
         }
      }

      return Optional.ofNullable(best);
   }

   static BlockPos findSpawn(ServerPlayer player, LivingEntity companion, CompanionMoveType moveType, LivingEntity threat) {
      if (threat == null || !threat.isAlive() || threat.level() != player.level()) {
         return CompanionSpawnPlacementService.findSummonSpot(player, CompanionKind.COMPANION, moveType);
      }

      if (moveType == CompanionMoveType.SWIM) {
         BlockPos origin = BlockPos.containing(threat.position());
         return CompanionPlacementFinder.findWaterWithOpenSurface(player.level(), origin)
            .orElseGet(() -> CompanionSpawnPlacementService.findSummonSpot(player, CompanionKind.COMPANION, moveType));
      }

      Vec3 awayFromPlayer = threat.position().subtract(player.position());
      awayFromPlayer = new Vec3(awayFromPlayer.x, 0.0, awayFromPlayer.z);
      if (awayFromPlayer.lengthSqr() < 0.01) {
         awayFromPlayer = player.getLookAngle();
         awayFromPlayer = new Vec3(awayFromPlayer.x, 0.0, awayFromPlayer.z);
      }

      if (awayFromPlayer.lengthSqr() < 0.01) {
         awayFromPlayer = new Vec3(1.0, 0.0, 0.0);
      }

      awayFromPlayer = awayFromPlayer.normalize();
      BlockPos base = BlockPos.containing(threat.position().add(awayFromPlayer.scale(1.8)));
      BlockPos surface = CompanionSpawnPlacementService.walkSurfacePos(player.level(), base, threat.getY());
      if (companion != null) {
         Optional<BlockPos> open = CompanionSpawnPlacementService.findOpenEntitySpace(player.level(), companion, surface);
         if (open.isPresent()) {
            return open.get();
         }
      }

      return CompanionPlacementFinder.findSafe(player.level(), surface).orElse(surface);
   }

   static void applyArrival(ServerPlayer player, LivingEntity companion, LivingEntity threat, boolean sendArrivalMagic) {
      CompanionRescueProtectionService.protectPlayer(player);
      CompanionRescueProtectionService.protectCompanion(companion);
      if (threat != null && threat.isAlive()) {
         PENDING.put(
            companion.getUUID(),
            new CompanionCombatRescueService.PendingCombatRescue(player.getUUID(), companion.getUUID(), threat.getUUID(), companion.position(), 80)
         );
         reinforceRescueIntent(player, companion, threat, companion.position(), true);
         staggerThreat(threat);
         Vec3 away = threat.position().subtract(player.position());
         if (away.lengthSqr() > 0.01) {
            Vec3 push = away.normalize().scale(0.28).add(0.0, 0.05, 0.0);
            threat.setDeltaMovement(threat.getDeltaMovement().add(push));
            threat.hurtMarked = true;
         }

         CompanionArrivalMagicService.openPulse(player, player.position().add(0.0, 0.02, 0.0), 1.85F, 22, RescueMagicPacket.Purpose.RESCUE);
      }

      if (sendArrivalMagic) {
         CompanionArrivalMagicService.sendGround(
            player, companion, threat == null ? player.position() : threat.position(), 48, RescueMagicPacket.Purpose.RESCUE
         );
      }
   }

   static void tick(MinecraftServer server) {
      if (server != null && !PENDING.isEmpty()) {
         Iterator<Entry<UUID, CompanionCombatRescueService.PendingCombatRescue>> iterator = PENDING.entrySet().iterator();

         while (iterator.hasNext()) {
            Entry<UUID, CompanionCombatRescueService.PendingCombatRescue> entry = iterator.next();
            CompanionCombatRescueService.PendingCombatRescue pending = entry.getValue();
            Entity companionEntity = CompanionManager.findEntity(server, pending.companionUuid()).orElse(null);
            Entity threatEntity = CompanionManager.findEntity(server, pending.threatUuid()).orElse(null);
            ServerPlayer player = server.getPlayerList().getPlayer(pending.playerUuid());
            if (!(
               companionEntity instanceof LivingEntity companion
                  && threatEntity instanceof LivingEntity threat
                  && player != null
                  && companion.isAlive()
                  && threat.isAlive()
            )) {
               iterator.remove();
            } else if (CompanionManager.isStoragePending(companion) || CompanionManager.isHomeResident(companion.getUUID())) {
               cancelRescueControl(companion);
               iterator.remove();
            } else if (CompanionManager.isArrivalPending(companion)) {
               entry.setValue(pending.tickDown());
            } else {
               boolean shouldNudge = pending.remainingTicks() % 10 == 0;
               reinforceRescueIntent(player, companion, threat, pending.anchor(), shouldNudge);
               CompanionCombatRescueService.PendingCombatRescue next = pending.tickDown();
               if (next.remainingTicks() > 0 && (!(companion.distanceToSqr(threat) > 64.0) || !(threat.distanceToSqr(player) > 64.0))) {
                  entry.setValue(next);
               } else {
                  iterator.remove();
               }
            }
         }
      }
   }

   private static void cancelRescueControl(LivingEntity companion) {
      if (companion instanceof Mob mob) {
         mob.getNavigation().stop();
         mob.setTarget(null);
         mob.setAggressive(false);
      }
   }

   private static void staggerThreat(LivingEntity threat) {
      threat.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 34, 1, false, false, true));
      threat.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 42, 0, false, false, true));
   }

   private static void reinforceRescueIntent(ServerPlayer player, LivingEntity companion, LivingEntity threat, Vec3 anchor, boolean hardNudge) {
      if (CompanionOwnershipGuardService.isRegisteredFor(player, companion)) {
         CompanionAnimationHelper.forceStandingPose(companion);
         if (companion instanceof TamableAnimal tamable) {
            tamable.setOrderedToSit(false);
            tamable.setInSittingPose(false);
         }

         boolean correctedPull = correctOwnerFollowPull(player, companion, threat, anchor);
         faceToward(companion, threat.position());
         if (companion instanceof Mob mob) {
            if (mob.isNoAi()) {
               mob.setNoAi(false);
            }

            if (mob.getTarget() != threat) {
               mob.setTarget(threat);
            }

            if (!mob.isAggressive()) {
               mob.setAggressive(true);
            }

            if (hardNudge || correctedPull || mob.getNavigation().isDone() || companion.distanceToSqr(threat) > 9.0) {
               mob.getNavigation().moveTo(threat, 1.35);
            }
         }
      }
   }

   private static boolean correctOwnerFollowPull(ServerPlayer player, LivingEntity companion, LivingEntity threat, Vec3 anchor) {
      if (anchor != null && player != null && companion.level() == player.level() && threat.level() == companion.level()) {
         boolean pulledToOwner = companion.distanceToSqr(player) <= 16.0;
         boolean threatStillAway = threat.distanceToSqr(player) >= 36.0;
         boolean anchorNearThreat = anchor.distanceToSqr(threat.position()) < threat.distanceToSqr(player);
         if (pulledToOwner && threatStillAway && anchorNearThreat) {
            companion.teleportTo(anchor.x, anchor.y, anchor.z);
            companion.snapTo(anchor.x, anchor.y, anchor.z, companion.getYRot(), companion.getXRot());
            companion.setDeltaMovement(Vec3.ZERO);
            companion.fallDistance = 0.0F;
            companion.hurtMarked = true;
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean isValidThreat(ServerPlayer player, LivingEntity entity, double range) {
      if (entity != null && entity != player && entity.isAlive() && !entity.isRemoved() && !(entity instanceof Player)) {
         if (!(entity.distanceToSqr(player) > range * range) && !CompanionManager.isOwnedBy(player, entity)) {
            if (entity instanceof Mob mob && mob.getTarget() == player) {
               return true;
            } else {
               return player.getLastHurtByMob() == entity ? true : entity instanceof Enemy && isLookingAtPlayer(entity, player);
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static double threatScore(ServerPlayer player, LivingEntity entity) {
      double distance = Math.sqrt(entity.distanceToSqr(player));
      double score = 16.0 - distance;
      if (entity instanceof Mob mob && mob.getTarget() == player) {
         score += 40.0;
      }

      if (player.getLastHurtByMob() == entity) {
         score += 30.0;
      }

      if (isLookingAtPlayer(entity, player)) {
         score += 12.0;
      }

      return score;
   }

   private static boolean isLookingAtPlayer(LivingEntity entity, ServerPlayer player) {
      Vec3 look = entity.getLookAngle();
      Vec3 toPlayer = player.position().add(0.0, player.getBbHeight() * 0.55, 0.0).subtract(entity.position().add(0.0, entity.getBbHeight() * 0.55, 0.0));
      return !(look.lengthSqr() < 0.001) && !(toPlayer.lengthSqr() < 0.001)
         ? look.normalize().dot(toPlayer.normalize()) > 0.74 && entity.hasLineOfSight(player)
         : false;
   }

   private static void faceToward(LivingEntity living, Vec3 target) {
      double dx = target.x - living.getX();
      double dz = target.z - living.getZ();
      float yaw = Mth.wrapDegrees((float)(Mth.atan2(dz, dx) * 180.0F / (float)Math.PI) - 90.0F);
      living.setYRot(yaw);
      living.setYHeadRot(yaw);
      living.yRotO = yaw;
      living.yHeadRot = yaw;
      living.yHeadRotO = yaw;
   }

   private record PendingCombatRescue(UUID playerUuid, UUID companionUuid, UUID threatUuid, Vec3 anchor, int remainingTicks) {
      CompanionCombatRescueService.PendingCombatRescue tickDown() {
         return new CompanionCombatRescueService.PendingCombatRescue(this.playerUuid, this.companionUuid, this.threatUuid, this.anchor, this.remainingTicks - 1);
      }
   }
}
