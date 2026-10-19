package com.kuzhi.findme.server;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.SavedPosition;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

final class CompanionSummonCompletionService {
   private CompanionSummonCompletionService() {
   }

   static void complete(
      ServerPlayer player,
      PlayerCompanionData data,
      CompanionKind kind,
      LivingEntity living,
      CompanionMoveType moveType,
      MountCinematicMode mode,
      boolean restoredFromStorage,
      boolean inCombat,
      boolean companionRescue,
      LivingEntity companionRescueTarget,
      boolean arrivalStarted,
      long now
   ) {
      CompanionManager.clearHomeResident(living);
      collectOtherVisibleEntitiesBeforeSummon(player, data, kind, living.getUUID());
      data.setLastKnownPosition(
         living.getUUID(), SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(), living.getYRot(), living.getXRot())
      );
      living.invulnerableTime = Math.max(living.invulnerableTime, Config.postTeleportInvulnerabilityTicks);
      if (kind == CompanionKind.MOUNT) {
         completeMountSummon(player, living, moveType, mode, restoredFromStorage, inCombat, !arrivalStarted);
      } else {
         completeCompanionSummon(player, data, kind, living, companionRescue, companionRescueTarget, !arrivalStarted);
      }

      int cooldown = kind == CompanionKind.MOUNT && inCombat
         ? Config.combatMountCooldownTicks
         : (kind == CompanionKind.MOUNT ? Config.mountCooldownTicks : Config.companionCooldownTicks);
      data.setReadyAt(kind, now + cooldown);
      CompanionManager.save(player, data);
      CompanionManager.syncToClient(player, kind);
      CompanionSummonLineService.showOrSchedule(player, living, kind, mode.isRescue() || companionRescue, companionRescue, arrivalStarted);
   }

   static void completeVoidFlyingMount(ServerPlayer player, PlayerCompanionData data, LivingEntity living, boolean inCombat, long now) {
      CompanionManager.clearHomeResident(living);
      collectOtherVisibleEntitiesBeforeSummon(player, data, CompanionKind.MOUNT, living.getUUID());
      CompanionManager.cancelArrivalSequence(living);
      CompanionManager.restoreAnimationControl(living);
      CompanionManager.forceFlyingAnimationPose(living);
      Entity previousRide = player.getVehicle();
      boolean riding = player.startRiding(living, true, true) || player.getVehicle() == living;
      if (riding) {
         CompanionMountCinematicFlowService.completeMountSwitch(player.level().getServer(), player, living, previousRide);
      }

      player.fallDistance = 0.0F;
      player.invulnerableTime = Math.max(player.invulnerableTime, Config.postTeleportInvulnerabilityTicks);
      living.fallDistance = 0.0F;
      living.invulnerableTime = Math.max(living.invulnerableTime, Config.postTeleportInvulnerabilityTicks);
      data.setDeployed(CompanionKind.MOUNT, living.getUUID());
      data.setLastKnownPosition(
         living.getUUID(), SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(), living.getYRot(), living.getXRot())
      );
      VehicleManager.enforceSingleRideSlotForMount(player, data, living.getUUID(), previousRide);
      int cooldown = inCombat ? Config.combatMountCooldownTicks : Config.mountCooldownTicks;
      data.setReadyAt(CompanionKind.MOUNT, now + cooldown);
      applyCombatSpeedBoost(player, inCombat);
      CompanionManager.save(player, data);
      CompanionManager.syncToClient(player, CompanionKind.MOUNT);
      CompanionSummonLineService.showVoidFlyingMount(player, living);
   }

   private static void completeMountSummon(
      ServerPlayer player,
      LivingEntity living,
      CompanionMoveType moveType,
      MountCinematicMode mode,
      boolean restoredFromStorage,
      boolean inCombat,
      boolean sendArrivalMagic
   ) {
      CompanionMoveType cinematicMoveType = CompanionSummonModeService.landPresentationMoveType(moveType);
      player.fallDistance = 0.0F;
      player.invulnerableTime = Math.max(player.invulnerableTime, Config.postTeleportInvulnerabilityTicks);
      boolean mountedImmediately = false;
      if (mode.isFlyingRescue() && CompanionCinematicLandingService.shouldDirectFlyingRescue(player)) {
         CompanionManager.cancelArrivalSequence(living);
         CompanionMountCinematicFlowService.completeMountSwitch(player.level().getServer(), player, living);
         CompanionMountCinematicFlowService.scheduleDirectFlyingCatchCinematic(player, living, cinematicMoveType, mode, restoredFromStorage, true);
         mountedImmediately = true;
      }

      if (!mountedImmediately) {
         CompanionMountCinematicFlowService.scheduleMountCinematic(player, living, cinematicMoveType, mode, restoredFromStorage, sendArrivalMagic);
      }

      if (inCombat && Config.enableCombatSpeedBoost && Config.combatSpeedBoostTicks > 0 && Config.combatSpeedBoostPercent > 0.0) {
         applyCombatSpeedBoost(player, true);
      }
   }

   private static void applyCombatSpeedBoost(ServerPlayer player, boolean inCombat) {
      if (inCombat && Config.enableCombatSpeedBoost && Config.combatSpeedBoostTicks > 0 && !(Config.combatSpeedBoostPercent <= 0.0)) {
         int amplifier = Math.max(0, (int)Math.ceil(Config.combatSpeedBoostPercent / 0.2) - 1);
         player.addEffect(new MobEffectInstance(MobEffects.SPEED, Config.combatSpeedBoostTicks, amplifier, false, false, true));
      }
   }

   private static void completeCompanionSummon(
      ServerPlayer player,
      PlayerCompanionData data,
      CompanionKind kind,
      LivingEntity living,
      boolean companionRescue,
      LivingEntity companionRescueTarget,
      boolean sendArrivalMagic
   ) {
      CompanionDeploymentService.rememberDeployedWithinLimit(player, data, kind, living.getUUID());
      if (companionRescue) {
         CompanionCombatRescueService.applyArrival(player, living, companionRescueTarget, sendArrivalMagic);
      } else if (sendArrivalMagic) {
         CompanionArrivalMagicService.sendGround(player, living, player.position(), 42);
      }
   }

   private static void collectOtherVisibleEntitiesBeforeSummon(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID keepUuid) {
      Entity currentVehicle = player.getVehicle();
      UUID currentVehicleUuid = currentVehicle == null ? null : currentVehicle.getUUID();
      boolean changed = CompanionDeploymentService.collectOtherDeployed(player, data, kind, keepUuid, currentVehicleUuid, true);
      if (changed) {
         CompanionManager.save(player, data);
         CompanionManager.syncToClient(player, kind);
      }
   }
}
