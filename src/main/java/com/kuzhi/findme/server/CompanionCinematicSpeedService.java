package com.kuzhi.findme.server;

import com.kuzhi.findme.common.CompanionMoveType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

final class CompanionCinematicSpeedService {
   private static final Map<UUID, PlayerMotionSample> PLAYER_MOTION_SAMPLES = new HashMap<>();
   private static final int MOUNT_SWITCH_SPEED_BOOST_TICKS = 100;
   private static final double MOVING_PLAYER_SPEED_THRESHOLD = 0.25;
   private static final double AIR_SWITCH_PLAYER_SPEED_GAIN = 1.65;
   private static final double AIR_SWITCH_PLAYER_SPEED_CAP_BONUS = 4.5;
   private static final double MOUNT_SWITCH_STUCK_SPEED_MULTIPLIER = 3.0;
   private static final double DEFAULT_FLY_CINEMATIC_SPEED = 1.75;

   private CompanionCinematicSpeedService() {
   }

   static void forgetPlayer(UUID playerUuid) {
      PLAYER_MOTION_SAMPLES.remove(playerUuid);
   }

   static double cinematicSpeed(PendingMountCinematic cinematic, LivingEntity living, ServerPlayer player, double distance) {
      if (cinematic.mode().isRescue()) {
         double groundDistance = CompanionCinematicLandingService.distanceToGround(player);
         double fallSpeed = Math.max(0.35, -player.getDeltaMovement().y);
         return cinematic.mode().isFlyingRescue() && !Double.isNaN(cinematic.catchY())
            ? flyingRescueSpeed(cinematic, player, distance, groundDistance, fallSpeed)
            : groundRescueSpeed(cinematic, player, distance, groundDistance, fallSpeed);
      }

      if (cinematic.mode().isMountSwitch() && cinematic.stage() == MountCinematicStage.SWITCH) {
         double playerSpeed = playerHorizontalSpeed(player);
         if (playerSpeed > 0.25) {
            double bonus = Math.min(4.5, playerSpeed * 1.65);
            return Math.min(distance, applyStuckSpeedBoost(cinematic, baseSpeed(cinematic.moveType()) + bonus));
         }
      }

      return applyStuckSpeedBoost(cinematic, baseSpeed(cinematic.moveType()));
   }

   static double samplePlayerHorizontalSpeed(ServerPlayer player) {
      Vec3 current = player.position();
      int tick = player.tickCount;
      PlayerMotionSample previous = PLAYER_MOTION_SAMPLES.get(player.getUUID());
      double speed = 0.0;
      if (previous != null) {
         if (previous.tick() == tick) {
            return previous.speed();
         }

         Vec3 position = previous.position();
         speed = Math.sqrt(Mth.square(current.x - position.x) + Mth.square(current.z - position.z));
      }

      PLAYER_MOTION_SAMPLES.put(player.getUUID(), new PlayerMotionSample(current, tick, speed));
      return speed;
   }

   private static double flyingRescueSpeed(PendingMountCinematic cinematic, ServerPlayer player, double distance, double groundDistance, double fallSpeed) {
      double blocksUntilCatch = Math.max(1.0, player.getY() - cinematic.catchY());
      double ticksUntilCatch = Math.max(2.0, blocksUntilCatch / fallSpeed);
      double targetTicks = cinematic.flyingRescueStaged() ? Math.max(2.0, ticksUntilCatch - 30.0) : ticksUntilCatch;
      double needed = distance / targetTicks * (cinematic.flyingRescueStaged() ? 1.0 : 1.03);
      double progress = 1.0 - Mth.clamp(blocksUntilCatch / Math.max(1.0, groundDistance), 0.0, 1.0);
      if (!cinematic.flyingRescueStaged()) {
         double base = 2.2 + progress * 3.2;
         double cap = blocksUntilCatch > 50.0
            ? 4.0
            : (blocksUntilCatch > 30.0 ? 5.2 : (blocksUntilCatch > 20.0 ? 6.8 : (blocksUntilCatch > 10.0 ? 9.0 : 12.0)));
         CompanionCinematicSpeedService.AirSwitchBonus bonus = airToFlySwitchBonus(cinematic, player);
         return applyStuckSpeedBoost(cinematic, Math.min(cap + bonus.cap(), Math.max(base + bonus.base(), needed)));
      } else {
         double base = 1.6 + progress * 2.4;
         double cap = groundDistance > 70.0 ? 4.5 : (blocksUntilCatch > 30.0 ? 4.8 : (blocksUntilCatch > 18.0 ? 5.5 : (blocksUntilCatch > 10.0 ? 6.8 : 9.0)));
         CompanionCinematicSpeedService.AirSwitchBonus bonus = airToFlySwitchBonus(cinematic, player);
         return applyStuckSpeedBoost(cinematic, Math.min(cap + bonus.cap(), Math.max(base + bonus.base(), needed)));
      }
   }

   private static double groundRescueSpeed(PendingMountCinematic cinematic, ServerPlayer player, double distance, double groundDistance, double fallSpeed) {
      double ticksUntilTenBlocks = Math.max(2.0, Math.max(0.0, groundDistance - 10.0) / fallSpeed);
      double needed = distance / ticksUntilTenBlocks * 0.92;
      double urgency = 1.0 - Mth.clamp((groundDistance - 10.0) / 70.0, 0.0, 1.0);
      double base = cinematic.moveType() == CompanionMoveType.FLY ? 2.0 + urgency * 3.2 : 0.8 + urgency * 2.2;
      double cap = groundDistance > 80.0
         ? 2.4
         : (groundDistance > 50.0 ? 3.2 : (groundDistance > 30.0 ? 4.2 : (groundDistance > 20.0 ? 5.5 : (groundDistance > 12.0 ? 7.0 : 9.0))));
      if (groundDistance <= 12.0) {
         cap = Math.max(cap, 9.0);
         base = Math.max(base, 5.5);
      }

      if (cinematic.mode().isGroundOrWaterRescue()) {
         base *= 0.78;
         cap *= 0.78;
      }

      if (cinematic.mode().isAirToGroundSwitch()) {
         double playerSpeed = playerHorizontalSpeed(player);
         if (playerSpeed > 0.25) {
            double bonus = Math.min(4.5, playerSpeed * 1.65);
            base += bonus * 0.55;
            cap += bonus;
         }
      }

      return applyStuckSpeedBoost(cinematic, Math.min(cap, Math.max(base, needed)));
   }

   private static CompanionCinematicSpeedService.AirSwitchBonus airToFlySwitchBonus(PendingMountCinematic cinematic, ServerPlayer player) {
      if (cinematic.mode() != MountCinematicMode.AIR_TO_FLY_SWITCH) {
         return CompanionCinematicSpeedService.AirSwitchBonus.NONE;
      }

      double playerSpeed = playerHorizontalSpeed(player);
      if (playerSpeed <= 0.25) {
         return CompanionCinematicSpeedService.AirSwitchBonus.NONE;
      }

      double bonus = Math.min(4.5, playerSpeed * 1.65);
      return new CompanionCinematicSpeedService.AirSwitchBonus(bonus * 0.65, bonus);
   }

   private static double applyStuckSpeedBoost(PendingMountCinematic cinematic, double speed) {
      if (!cinematic.mode().isMountSwitch()) {
         return speed;
      }

      int age = cinematic.stage() == MountCinematicStage.SWITCH ? cinematic.switchAge() : cinematic.age();
      return age >= 100 ? speed * 3.0 : speed;
   }

   private static double playerHorizontalSpeed(ServerPlayer player) {
      double speed = samplePlayerHorizontalSpeed(player);
      Vec3 movement = player.getDeltaMovement();
      speed = Math.max(speed, movement.horizontalDistance());
      Entity vehicle = player.getVehicle();
      if (vehicle != null) {
         Vec3 vehicleMovement = vehicle.getDeltaMovement();
         speed = Math.max(speed, vehicleMovement.horizontalDistance());
      }

      return speed;
   }

   private static double baseSpeed(CompanionMoveType moveType) {
      return switch (moveType) {
         case FLY -> 1.75;
         case SWIM -> 0.55;
         case WALK, COMPANION -> 0.42;
      };
   }

   private record AirSwitchBonus(double base, double cap) {
      private static final CompanionCinematicSpeedService.AirSwitchBonus NONE = new CompanionCinematicSpeedService.AirSwitchBonus(0.0, 0.0);
   }
}
