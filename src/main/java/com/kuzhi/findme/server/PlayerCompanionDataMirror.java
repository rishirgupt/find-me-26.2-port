package com.kuzhi.findme.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

final class PlayerCompanionDataMirror extends SavedData {
   private static final String DATA_NAME = "find_me_player_data_mirror";
   private final Map<UUID, CompoundTag> playerData = new HashMap<>();
   private final Map<UUID, List<PlayerCompanionDataMirror.MirrorBackup>> playerBackups = new HashMap<>();
   private final Set<UUID> clearedPlayers = new HashSet<>();

   private static final com.mojang.serialization.Codec<PlayerCompanionDataMirror> CODEC = CompoundTag.CODEC
      .xmap(tag -> load(tag, null), mirror -> mirror.save(new CompoundTag(), null));
   private static final net.minecraft.world.level.saveddata.SavedDataType<PlayerCompanionDataMirror> TYPE = new net.minecraft.world.level.saveddata.SavedDataType<>(
      net.minecraft.resources.Identifier.fromNamespaceAndPath("find_me", "player_data_mirror"), PlayerCompanionDataMirror::new, CODEC, null
   );

   private PlayerCompanionDataMirror() {
   }

   static PlayerCompanionDataMirror get(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(TYPE);
   }

   static void remember(ServerPlayer player) {
      if (player != null && player.level().getServer() != null) {
         if (PlayerCompanionDataCodec.hasRoot(com.kuzhi.findme.compat.PersistentData.of(player))) {
            CompoundTag root = PlayerCompanionDataCodec.rootCopy(com.kuzhi.findme.compat.PersistentData.of(player));
            PlayerCompanionDataMirror mirror = get(player.level().getServer());
            if (!PlayerCompanionDataCodec.isMeaningfulRoot(root)) {
               mirror.rememberCleared(player.getUUID());
            } else {
               mirror.put(player.getUUID(), root);
            }
         }
      }
   }

   static void rememberBackup(ServerPlayer player, long savedAt, String reason, int limit) {
      if (player != null && player.level().getServer() != null) {
         if (PlayerCompanionDataCodec.hasRoot(com.kuzhi.findme.compat.PersistentData.of(player))) {
            CompoundTag root = PlayerCompanionDataCodec.rootCopy(com.kuzhi.findme.compat.PersistentData.of(player));
            if (PlayerCompanionDataCodec.isMeaningfulRoot(root)) {
               get(player.level().getServer()).putBackup(player.getUUID(), savedAt, reason, root, limit);
            }
         }
      }
   }

   static boolean restoreIfMissing(ServerPlayer player) {
      if (player == null || player.level().getServer() == null) {
         return false;
      }

      if (PlayerCompanionDataCodec.hasRoot(com.kuzhi.findme.compat.PersistentData.of(player))) {
         remember(player);
         return false;
      }

      PlayerCompanionDataMirror mirror = get(player.level().getServer());
      Optional<CompoundTag> mirrored = mirror.copy(player.getUUID());
      if (mirrored.isEmpty() && !mirror.isCleared(player.getUUID())) {
         mirrored = mirror.latestBackup(player.getUUID());
      }

      if (mirrored.isEmpty()) {
         return false;
      }

      PlayerCompanionDataCodec.putRoot(com.kuzhi.findme.compat.PersistentData.of(player), mirrored.get());
      return true;
   }

   static boolean copyCloneData(Player original, ServerPlayer clone) {
      if (original == null || clone == null || clone.level().getServer() == null) {
         return false;
      }

      if (PlayerCompanionDataCodec.hasRoot(com.kuzhi.findme.compat.PersistentData.of(original))) {
         CompoundTag originalRoot = PlayerCompanionDataCodec.rootCopy(com.kuzhi.findme.compat.PersistentData.of(original));
         PlayerCompanionDataCodec.putRoot(com.kuzhi.findme.compat.PersistentData.of(clone), originalRoot);
         PlayerCompanionDataMirror mirror = get(clone.level().getServer());
         if (PlayerCompanionDataCodec.isMeaningfulRoot(originalRoot)) {
            mirror.put(clone.getUUID(), originalRoot);
            return true;
         } else {
            mirror.rememberCleared(clone.getUUID());
            return false;
         }
      } else {
         return restoreIfMissing(clone);
      }
   }

   private static PlayerCompanionDataMirror load(CompoundTag tag, Provider provider) {
      PlayerCompanionDataMirror mirror = new PlayerCompanionDataMirror();
      ListTag players = tag.getListOrEmpty("players");

      for (int i = 0; i < players.size(); i++) {
         CompoundTag entry = players.getCompoundOrEmpty(i);
         if (entry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent() && entry.contains("data")) {
            CompoundTag root = entry.getCompoundOrEmpty("data");
            if (PlayerCompanionDataCodec.isMeaningfulRoot(root)) {
               mirror.playerData.put(entry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(), root.copy());
            }
         }
      }

      ListTag backups = tag.getListOrEmpty("backups");

      for (int i = 0; i < backups.size(); i++) {
         CompoundTag playerEntry = backups.getCompoundOrEmpty(i);
         if (playerEntry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
            UUID uuid = playerEntry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow();
            ListTag entries = playerEntry.getListOrEmpty("entries");
            List<PlayerCompanionDataMirror.MirrorBackup> playerBackupList = new ArrayList<>();

            for (int j = 0; j < entries.size(); j++) {
               CompoundTag backupTag = entries.getCompoundOrEmpty(j);
               if (backupTag.contains("data")) {
                  CompoundTag root = backupTag.getCompoundOrEmpty("data");
                  if (PlayerCompanionDataCodec.isMeaningfulRoot(root)) {
                     playerBackupList.add(new PlayerCompanionDataMirror.MirrorBackup(backupTag.getLongOr("savedAt", 0L), backupTag.getStringOr("reason", ""), root));
                  }
               }
            }

            if (!playerBackupList.isEmpty()) {
               mirror.playerBackups.put(uuid, playerBackupList);
            }
         }
      }

      ListTag cleared = tag.getListOrEmpty("clearedPlayers");

      for (int i = 0; i < cleared.size(); i++) {
         CompoundTag entry = cleared.getCompoundOrEmpty(i);
         if (entry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
            mirror.clearedPlayers.add(entry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow());
         }
      }

      return mirror;
   }

   private void put(UUID uuid, CompoundTag root) {
      if (uuid != null && PlayerCompanionDataCodec.isMeaningfulRoot(root)) {
         this.playerData.put(uuid, root.copy());
         this.clearedPlayers.remove(uuid);
         this.setDirty();
      }
   }

   private void rememberCleared(UUID uuid) {
      if (uuid != null) {
         boolean changed = this.playerData.remove(uuid) != null;
         changed |= this.clearedPlayers.add(uuid);
         if (changed) {
            this.setDirty();
         }
      }
   }

   private Optional<CompoundTag> copy(UUID uuid) {
      CompoundTag root = this.playerData.get(uuid);
      return !PlayerCompanionDataCodec.isMeaningfulRoot(root) ? Optional.empty() : Optional.of(root.copy());
   }

   private void putBackup(UUID uuid, long savedAt, String reason, CompoundTag root, int limit) {
      if (uuid != null && PlayerCompanionDataCodec.isMeaningfulRoot(root)) {
         List<PlayerCompanionDataMirror.MirrorBackup> backups = this.playerBackups.computeIfAbsent(uuid, ignored -> new ArrayList<>());
         backups.add(0, new PlayerCompanionDataMirror.MirrorBackup(savedAt, reason, root));
         int max = Math.max(1, limit);

         while (backups.size() > max) {
            backups.remove(backups.size() - 1);
         }

         this.clearedPlayers.remove(uuid);
         this.setDirty();
      }
   }

   private Optional<CompoundTag> latestBackup(UUID uuid) {
      List<PlayerCompanionDataMirror.MirrorBackup> backups = this.playerBackups.get(uuid);
      if (backups != null && !backups.isEmpty()) {
         for (PlayerCompanionDataMirror.MirrorBackup backup : backups) {
            if (PlayerCompanionDataCodec.isMeaningfulRoot(backup.data())) {
               return Optional.of(backup.data());
            }
         }

         return Optional.empty();
      } else {
         return Optional.empty();
      }
   }

   private boolean isCleared(UUID uuid) {
      return uuid != null && this.clearedPlayers.contains(uuid);
   }

   public CompoundTag save(CompoundTag tag, Provider provider) {
      ListTag players = new ListTag();

      for (Entry<UUID, CompoundTag> mirrored : this.playerData.entrySet()) {
         CompoundTag root = mirrored.getValue();
         if (PlayerCompanionDataCodec.isMeaningfulRoot(root)) {
            CompoundTag entry = new CompoundTag();
            entry.store("uuid", net.minecraft.core.UUIDUtil.CODEC, mirrored.getKey());
            entry.put("data", root.copy());
            players.add(entry);
         }
      }

      tag.put("players", players);
      ListTag backups = new ListTag();

      for (Entry<UUID, List<PlayerCompanionDataMirror.MirrorBackup>> mirroredBackups : this.playerBackups.entrySet()) {
         ListTag entries = new ListTag();

         for (PlayerCompanionDataMirror.MirrorBackup backup : mirroredBackups.getValue()) {
            if (PlayerCompanionDataCodec.isMeaningfulRoot(backup.data())) {
               CompoundTag backupTag = new CompoundTag();
               backupTag.putLong("savedAt", backup.savedAt());
               backupTag.putString("reason", backup.reason());
               backupTag.put("data", backup.data());
               entries.add(backupTag);
            }
         }

         if (!entries.isEmpty()) {
            CompoundTag playerEntry = new CompoundTag();
            playerEntry.store("uuid", net.minecraft.core.UUIDUtil.CODEC, mirroredBackups.getKey());
            playerEntry.put("entries", entries);
            backups.add(playerEntry);
         }
      }

      tag.put("backups", backups);
      ListTag cleared = new ListTag();

      for (UUID uuid : this.clearedPlayers) {
         CompoundTag entry = new CompoundTag();
         entry.store("uuid", net.minecraft.core.UUIDUtil.CODEC, uuid);
         cleared.add(entry);
      }

      tag.put("clearedPlayers", cleared);
      return tag;
   }

   private record MirrorBackup(long savedAt, String reason, CompoundTag data) {
      private MirrorBackup {
         reason = reason == null ? "backup" : reason;
         data = data == null ? new CompoundTag() : data.copy();
      }

      public CompoundTag data() {
         return this.data.copy();
      }
   }
}
