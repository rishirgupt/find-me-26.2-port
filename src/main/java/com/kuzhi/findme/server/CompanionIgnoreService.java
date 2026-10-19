package com.kuzhi.findme.server;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.network.BlacklistPacket;
import com.kuzhi.findme.network.IgnoreListPacket;
import com.kuzhi.findme.network.ModNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

final class CompanionIgnoreService {
   private CompanionIgnoreService() {
   }

   static int listIgnored(ServerPlayer player) {
      PlayerCompanionData data = CompanionManager.data(player);
      List<UUID> ignored = data.autoBindIgnoredList();
      if (ignored.isEmpty()) {
         CompanionManager.tell(player, "message.find_me.ignore_empty", ChatFormatting.YELLOW);
         return 0;
      }

      CompanionManager.tell(player, "message.find_me.ignore_header", ChatFormatting.AQUA);

      for (int i = 0; i < ignored.size(); i++) {
         player.sendSystemMessage(Component.translatable("message.find_me.ignore_entry", new Object[]{i, ignored.get(i)}).withStyle(ChatFormatting.GRAY));
      }

      return ignored.size();
   }

   static void syncIgnoredToClient(ServerPlayer player) {
      PlayerCompanionData data = CompanionManager.data(player);
      ArrayList<IgnoreListPacket.Entry> entries = new ArrayList<>();

      for (UUID uuid : data.autoBindIgnoredList()) {
         String name = data.autoBindIgnoredName(uuid);
         if (name.isBlank()) {
            name = uuid.toString().substring(0, 8);
         }

         entries.add(new IgnoreListPacket.Entry(uuid, name));
      }

      ModNetwork.sendToPlayer(player, new IgnoreListPacket(entries));
   }

   static void syncBlacklistToClient(ServerPlayer player) {
      PlayerCompanionData data = CompanionManager.data(player);
      ArrayList<BlacklistPacket.Entry> entries = new ArrayList<>();

      for (String entityType : data.autoBindTypeBlacklist()) {
         entries.add(new BlacklistPacket.Entry(entityType));
      }

      ModNetwork.sendToPlayer(player, new BlacklistPacket(entries));
   }

   static int blacklistEntryType(ServerPlayer player, CompanionKind kind, int index) {
      PlayerCompanionData data = CompanionManager.data(player);
      Optional<UUID> maybeUuid = data.wheelUuidAt(kind, index);
      if (maybeUuid.isEmpty()) {
         CompanionManager.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionManager.label(kind));
         return 0;
      } else {
         String entityType = entityType(player, data, maybeUuid.get());
         if (entityType.isBlank()) {
            CompanionManager.tell(player, "message.find_me.blacklist_missing_type", ChatFormatting.RED);
            return 0;
         } else {
            data.blacklistAutoBindType(entityType);
            CompanionManager.save(player, data);
            CompanionManager.tell(player, "message.find_me.blacklist_added", ChatFormatting.GREEN, entityType);
            return 1;
         }
      }
   }

   static int removeIgnored(ServerPlayer player, int index) {
      PlayerCompanionData data = CompanionManager.data(player);
      if (!data.removeAutoBindIgnored(index)) {
         CompanionManager.tell(player, "message.find_me.invalid_ignore_index", ChatFormatting.RED);
         return 0;
      } else {
         CompanionManager.save(player, data);
         CompanionManager.tell(player, "message.find_me.ignore_removed", ChatFormatting.GREEN);
         return 1;
      }
   }

   static int removeBlacklist(ServerPlayer player, int index) {
      PlayerCompanionData data = CompanionManager.data(player);
      if (!data.removeAutoBindTypeBlacklist(index)) {
         CompanionManager.tell(player, "message.find_me.invalid_blacklist_index", ChatFormatting.RED);
         return 0;
      } else {
         CompanionManager.save(player, data);
         CompanionManager.tell(player, "message.find_me.blacklist_removed", ChatFormatting.GREEN);
         return 1;
      }
   }

   static int clearIgnored(ServerPlayer player) {
      PlayerCompanionData data = CompanionManager.data(player);
      data.clearAutoBindIgnored();
      CompanionManager.save(player, data);
      CompanionManager.tell(player, "message.find_me.ignore_cleared", ChatFormatting.GREEN);
      return 1;
   }

   private static String entityType(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
      Entity entity = CompanionManager.findLoadedEntity(player.level().getServer(), data, uuid).orElse(null);
      if (entity != null) {
         return EntityType.getKey(entity.getType()).toString();
      }

      Optional<CompoundTag> stored = data.storedEntity(uuid);
      return stored.map(CompanionManager::storedEntityType).orElse("");
   }
}
