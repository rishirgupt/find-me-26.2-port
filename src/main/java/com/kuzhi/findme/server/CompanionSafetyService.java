package com.kuzhi.findme.server;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.SavedPosition;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

final class CompanionSafetyService {
   private static final Map<UUID, Long> LAST_SLEEP_REVIVE = new HashMap<>();

   private CompanionSafetyService() {
   }

   static int listVault(ServerPlayer player) {
      PlayerCompanionData data = CompanionManager.data(player);
      List<PlayerCompanionData.VaultEntry> entries = data.vaultList();
      if (entries.isEmpty()) {
         CompanionManager.tell(player, "message.find_me.vault_empty", ChatFormatting.YELLOW);
         return 0;
      }

      CompanionManager.tell(player, "message.find_me.vault_header", ChatFormatting.AQUA);

      for (int i = 0; i < entries.size(); i++) {
         PlayerCompanionData.VaultEntry entry = entries.get(i);
         player.sendSystemMessage(
            Component.translatable("message.find_me.vault_entry", new Object[]{i, entry.name(), entry.kind().name().toLowerCase(Locale.ROOT), entry.reason()})
               .withStyle(ChatFormatting.GRAY)
         );
      }

      return entries.size();
   }

   static int restoreVault(ServerPlayer player, int index) {
      PlayerCompanionData data = CompanionManager.data(player);
      Optional<PlayerCompanionData.VaultEntry> entry = data.vaultAt(index);
      if (!entry.isEmpty() && data.restoreVaultAt(index)) {
         CompanionManager.save(player, data);
         CompanionManager.syncToClient(player, CompanionKind.MOUNT);
         CompanionManager.syncToClient(player, CompanionKind.COMPANION);
         CompanionManager.syncDeadToClient(player);
         CompanionManager.tell(player, "message.find_me.vault_restored", ChatFormatting.GREEN, entry.get().name());
         return 1;
      } else {
         CompanionManager.tell(player, "message.find_me.invalid_vault_index", ChatFormatting.RED);
         return 0;
      }
   }

   static int clearVault(ServerPlayer player) {
      PlayerCompanionData data = CompanionManager.data(player);
      if (!data.clearVault()) {
         CompanionManager.tell(player, "message.find_me.vault_empty", ChatFormatting.YELLOW);
         return 0;
      } else {
         CompanionManager.save(player, data);
         CompanionManager.tell(player, "message.find_me.vault_cleared", ChatFormatting.GREEN);
         return 1;
      }
   }

   static int listBackups(ServerPlayer player) {
      PlayerCompanionData data = CompanionManager.data(player);
      List<PlayerCompanionData.BackupEntry> backups = data.backupList();
      if (backups.isEmpty()) {
         CompanionManager.tell(player, "message.find_me.backup_empty", ChatFormatting.YELLOW);
         return 0;
      }

      CompanionManager.tell(player, "message.find_me.backup_header", ChatFormatting.AQUA);

      for (int i = 0; i < backups.size(); i++) {
         PlayerCompanionData.BackupEntry backup = backups.get(i);
         player.sendSystemMessage(
            Component.translatable("message.find_me.backup_entry", new Object[]{i, backup.savedAt(), backup.reason()}).withStyle(ChatFormatting.GRAY)
         );
      }

      return backups.size();
   }

   static int createBackup(ServerPlayer player, String reason) {
      if (!Config.enableAutoBackups) {
         CompanionManager.tell(player, "message.find_me.backup_disabled", ChatFormatting.YELLOW);
         return 0;
      } else {
         PlayerCompanionData data = CompanionManager.data(player);
         if (!hasBackupWorthyState(data)) {
            CompanionManager.tell(player, "message.find_me.no_registered", ChatFormatting.YELLOW, CompanionManager.label(CompanionKind.COMPANION));
            return 0;
         } else {
            long now = player.level().getGameTime();
            data.createBackup(now, reason, Config.backupCapacity);
            CompanionManager.save(player, data);
            PlayerCompanionDataMirror.rememberBackup(player, now, reason, Config.backupCapacity);
            CompanionManager.tell(player, "message.find_me.backup_created", ChatFormatting.GREEN);
            return 1;
         }
      }
   }

   static int restoreBackup(ServerPlayer player, int index) {
      PlayerCompanionData data = CompanionManager.data(player);
      Optional<PlayerCompanionData.BackupEntry> targetBackup = data.backupAt(index);
      if (targetBackup.isEmpty()) {
         CompanionManager.tell(player, "message.find_me.invalid_backup_index", ChatFormatting.RED);
         return 0;
      } else {
         long now = player.level().getGameTime();
         data.createBackup(now, "before_restore", Config.backupCapacity);
         CompanionManager.save(player, data);
         PlayerCompanionDataMirror.rememberBackup(player, now, "before_restore", Config.backupCapacity);
         data.restoreBackup(targetBackup.get());
         CompanionManager.save(player, data);
         CompanionManager.syncToClient(player, CompanionKind.MOUNT);
         CompanionManager.syncToClient(player, CompanionKind.COMPANION);
         CompanionManager.syncDeadToClient(player);
         CompanionManager.tell(player, "message.find_me.backup_restored", ChatFormatting.GREEN, index);
         return 1;
      }
   }

   static void tickPlayerSafety(ServerPlayer player) {
      tickAutomaticBackup(player);
   }

   static void trySleepRevive(ServerPlayer player) {
      if (Config.enableSleepRevive && !(player.getRandom().nextDouble() > Config.sleepReviveChance)) {
         long now = player.level().getGameTime();
         long cooldown = Math.max(0, Config.sleepReviveCooldownMinutes) * 1200L;
         Long last = LAST_SLEEP_REVIVE.get(player.getUUID());
         if (last == null || cooldown <= 0L || now - last >= cooldown) {
            PlayerCompanionData data = CompanionManager.data(player);
            List<UUID> dead = data.deadList();
            if (!dead.isEmpty()) {
               UUID uuid = dead.get(dead.size() - 1);
               Optional<CompoundTag> maybeTag = data.storedEntity(uuid);
               if (!maybeTag.isEmpty()) {
                  CompanionKind kind = data.kindOf(uuid).orElse(CompanionKind.COMPANION);
                  CompoundTag tag = maybeTag.get();
                  String name = data.displayName(uuid).orElseGet(() -> CompanionManager.storedEntityName(tag, uuid).replace(" (Dead)", ""));
                  tag.remove("CompanionRescueDead");
                  tag.putString("CompanionRescueName", name.replace(" (Dead)", ""));
                  float maxHealth = Math.max(1.0F, tag.getFloatOr("CompanionRescueMaxHealth", 0.0F));
                  tag.putFloat("CompanionRescueHealth", maxHealth);
                  tag.putFloat("Health", maxHealth);
                  data.addVaultSnapshot(uuid, kind, name, maybeTag.get(), now, "revive", Config.vaultCapacity);
                  data.storeEntity(uuid, tag);
                  data.setDisplayName(uuid, name.replace(" (Dead)", ""));
                  data.add(kind, uuid);
                  data.setActiveUuid(kind, uuid);
                  LAST_SLEEP_REVIVE.put(player.getUUID(), now);
                  boolean spawned = false;
                  if (Config.sleepReviveSpawnEntity) {
                     CompanionMoveType moveType = CompanionManager.moveType(CompanionManager.storedEntityType(tag), kind);
                     Optional<BlockPos> reviveSpot = findReviveSpot(player, tag, kind, moveType);
                     if (reviveSpot.isPresent()) {
                        Optional<Entity> restored = CompanionEntityTransferService.restoreStoredEntity(
                           player, data, uuid, reviveSpot.get(), player.getYRot(), player.getXRot()
                        );
                        if (restored.isPresent() && restored.get() instanceof LivingEntity living) {
                           data.setDeployed(kind, uuid);
                           data.setLastKnownPosition(
                              uuid, SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(), living.getYRot(), living.getXRot())
                           );
                           spawned = true;
                        }
                     }
                  }

                  CompanionManager.save(player, data);
                  CompanionManager.syncToClient(player, CompanionKind.MOUNT);
                  CompanionManager.syncToClient(player, CompanionKind.COMPANION);
                  CompanionManager.syncDeadToClient(player);
                  CompanionSummonLineService.showSleepRevived(player, name.replace(" (Dead)", ""), spawned);
               }
            }
         }
      }
   }

   private static void tickAutomaticBackup(ServerPlayer player) {
      if (Config.enableAutoBackups && player.tickCount % 200 == 0) {
         PlayerCompanionData data = CompanionManager.data(player);
         if (hasBackupWorthyState(data)) {
            long now = player.level().getGameTime();
            long intervalTicks = Math.max(1, Config.backupIntervalMinutes) * 1200L;
            if (data.lastBackupAt() <= 0L || now - data.lastBackupAt() >= intervalTicks) {
               data.createBackup(now, "auto", Config.backupCapacity);
               CompanionManager.save(player, data);
               PlayerCompanionDataMirror.rememberBackup(player, now, "auto", Config.backupCapacity);
            }
         }
      }
   }

   private static Optional<BlockPos> findReviveSpot(ServerPlayer player, CompoundTag tag, CompanionKind kind, CompanionMoveType moveType) {
      double width = Math.max(tag.getFloatOr("CompanionPreviewWidth", 0.0F), 1.0);
      double height = Math.max(tag.getFloatOr("CompanionPreviewHeight", 0.0F), 1.0);
      boolean large = width >= 3.0 || height >= 3.0;
      if (!large) {
         return Optional.of(CompanionSpawnPlacementService.findSummonSpot(player, kind, moveType));
      }

      ServerLevel level = player.level();
      BlockPos origin = player.blockPosition();

      for (int radius = 16; radius <= 32; radius += 4) {
         for (int dx = -radius; dx <= radius; dx += 4) {
            for (int dz = -radius; dz <= radius; dz += 4) {
               if (Math.abs(dx) == radius || Math.abs(dz) == radius) {
                  BlockPos candidate = CompanionSpawnPlacementService.walkSurfacePos(level, origin.offset(dx, 0, dz), player.getY());
                  if (CompanionSpawnPlacementService.hasOpenBox(level, candidate, width, height)) {
                     return Optional.of(candidate);
                  }
               }
            }
         }
      }

      return Optional.empty();
   }

   private static boolean hasBackupWorthyState(PlayerCompanionData data) {
      return PlayerCompanionDataCodec.isMeaningfulRoot(PlayerCompanionDataCodec.createBackupState(data));
   }
}
