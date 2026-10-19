package com.kuzhi.findme.server;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

final class CompanionSummonPreparationService {
   private CompanionSummonPreparationService() {
   }

   static Optional<CompanionSummonPreparationService.Preparation> prepare(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
      long now = player.level().getGameTime();
      long readyAt = data.readyAt(kind);
      if (now < readyAt) {
         CompanionSummonLineService.showCooldown(player, data, kind, (readyAt - now + 19L) / 20L);
         return Optional.empty();
      }

      boolean inCombat = isInCombat(player);
      if (!Config.enablePvpSummon && player.getLastHurtByMob() instanceof Player) {
         CompanionSummonLineService.showPvpDisabled(player, data, kind);
         return Optional.empty();
      }

      Optional<UUID> maybeUuid = data.active(kind);
      if (maybeUuid.isEmpty()) {
         CompanionSummonLineService.showNoRegistered(player, kind);
         return Optional.empty();
      }

      UUID uuid = maybeUuid.get();
      if (CompanionManager.isStoragePending(uuid)) {
         CompanionSummonLineService.showBusy(player, data, kind, uuid);
         return Optional.empty();
      }

      if (kind == CompanionKind.COMPANION && CompanionManager.collectActiveShoulderCompanion(player, data, uuid)) {
         return Optional.empty();
      }

      boolean fallingRescue = kind == CompanionKind.MOUNT
         && player.getVehicle() == null
         && (player.fallDistance >= Config.rescueMinFallDistance || !player.onGround() && player.getDeltaMovement().y < -0.2);
      LivingEntity companionRescueTarget = kind == CompanionKind.COMPANION ? CompanionCombatRescueService.findThreat(player).orElse(null) : null;
      if (fallingRescue) {
         CompanionMountCinematicFlowService.stabilizeFallingPlayer(player);
      }

      return Optional.of(new CompanionSummonPreparationService.Preparation(now, uuid, inCombat, fallingRescue, companionRescueTarget));
   }

   private static boolean isInCombat(ServerPlayer player) {
      return player.getLastHurtByMob() != null || player.getLastHurtMob() != null;
   }

   record Preparation(long now, UUID uuid, boolean inCombat, boolean fallingRescue, LivingEntity companionRescueTarget) {
   }
}
