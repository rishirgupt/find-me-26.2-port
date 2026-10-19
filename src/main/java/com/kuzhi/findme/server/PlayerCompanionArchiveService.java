package com.kuzhi.findme.server;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

final class PlayerCompanionArchiveService {
   private PlayerCompanionArchiveService() {
   }

   static void addVaultSnapshot(
      PlayerCompanionData data, UUID uuid, CompanionKind kind, String name, CompoundTag entityTag, long savedAt, String reason, int limit
   ) {
      if (Config.enableVault && uuid != null && entityTag != null) {
         CompoundTag tag = entityTag.copy();
         String entityType = tag.getStringOr("CompanionRescueType", "");
         if (entityType == null || entityType.isBlank()) {
            entityType = tag.getStringOr("id", "");
         }

         String displayName = name != null && !name.isBlank() ? name : tag.getStringOr("CompanionRescueName", "");
         if (displayName == null || displayName.isBlank()) {
            displayName = uuid.toString().substring(0, 8);
         }

         long snapshotTime = savedAt > 0L ? savedAt : tag.getLongOr("CompanionRescueStoredAt", 0L);
         data.vault.removeIf(entry -> entry.uuid().equals(uuid));
         data.vault
            .add(
               0,
               new PlayerCompanionData.VaultEntry(
                  uuid, kind, entityType == null ? "" : entityType, displayName, tag, snapshotTime, reason == null ? "snapshot" : reason
               )
            );
         trimVault(data, limit);
      }
   }

   static void vaultSnapshot(PlayerCompanionData data, UUID uuid, CompanionKind kind, String reason, int limit) {
      if (Config.enableVault) {
         CompoundTag tag = data.storedEntities.get(uuid);
         if (tag != null) {
            addVaultSnapshot(data, uuid, kind, data.displayNames.get(uuid), tag, tag.getLongOr("CompanionRescueStoredAt", 0L), reason, limit);
         }
      }
   }

   static List<PlayerCompanionData.VaultEntry> vaultList(PlayerCompanionData data) {
      return List.copyOf(data.vault);
   }

   static Optional<PlayerCompanionData.VaultEntry> vaultAt(PlayerCompanionData data, int index) {
      return index >= 0 && index < data.vault.size() ? Optional.of(data.vault.get(index)) : Optional.empty();
   }

   static boolean clearVault(PlayerCompanionData data) {
      if (data.vault.isEmpty()) {
         return false;
      }

      data.vault.clear();
      return true;
   }

   static boolean restoreVaultAt(PlayerCompanionData data, int index) {
      Optional<PlayerCompanionData.VaultEntry> maybeEntry = vaultAt(data, index);
      if (maybeEntry.isEmpty()) {
         return false;
      }

      PlayerCompanionData.VaultEntry entry = maybeEntry.get();
      UUID uuid = entry.uuid();
      data.storeEntity(uuid, entry.entityTag());
      data.setDisplayName(uuid, entry.name());
      data.deadCompanions.remove(uuid);
      data.deadKinds.remove(uuid);
      data.add(entry.kind(), uuid);
      data.setActiveUuid(entry.kind(), uuid);
      if (entry.kind() == CompanionKind.MOUNT) {
         data.markMountEligible(uuid);
      }

      return true;
   }

   static void createBackup(PlayerCompanionData data, long savedAt, String reason, int limit) {
      CompoundTag state = PlayerCompanionDataCodec.createBackupState(data);
      data.backups.add(0, new PlayerCompanionData.BackupEntry(savedAt, reason == null ? "backup" : reason, state));
      trimBackups(data, limit);
   }

   static List<PlayerCompanionData.BackupEntry> backupList(PlayerCompanionData data) {
      return List.copyOf(data.backups);
   }

   static Optional<PlayerCompanionData.BackupEntry> backupAt(PlayerCompanionData data, int index) {
      return index >= 0 && index < data.backups.size() ? Optional.of(data.backups.get(index)) : Optional.empty();
   }

   static boolean restoreBackupAt(PlayerCompanionData data, int index) {
      Optional<PlayerCompanionData.BackupEntry> backup = backupAt(data, index);
      if (backup.isEmpty()) {
         return false;
      }

      restoreBackup(data, backup.get());
      return true;
   }

   static void restoreBackup(PlayerCompanionData data, PlayerCompanionData.BackupEntry backup) {
      if (backup != null) {
         PlayerCompanionDataCodec.restoreBackupState(data, backup.state());
      }
   }

   static long lastBackupAt(PlayerCompanionData data) {
      return data.backups.isEmpty() ? 0L : data.backups.get(0).savedAt();
   }

   static void trimVault(PlayerCompanionData data, int limit) {
      int max = Math.max(1, limit);

      while (data.vault.size() > max) {
         data.vault.remove(data.vault.size() - 1);
      }
   }

   static void trimBackups(PlayerCompanionData data, int limit) {
      int max = Math.max(1, limit);

      while (data.backups.size() > max) {
         data.backups.remove(data.backups.size() - 1);
      }
   }
}
