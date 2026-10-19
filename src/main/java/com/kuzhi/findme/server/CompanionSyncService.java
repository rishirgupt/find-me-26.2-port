package com.kuzhi.findme.server;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.network.CompanionListPacket;
import com.kuzhi.findme.network.DeadCompanionListPacket;
import com.kuzhi.findme.network.ModNetwork;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

final class CompanionSyncService {
   private static final Map<UUID, EnumMap<CompanionKind, Map<UUID, String>>> SENT_PREVIEW_SIGNATURES = new HashMap<>();

   private CompanionSyncService() {
   }

   static void syncToClient(ServerPlayer player, CompanionKind kind) {
      PlayerCompanionData data = CompanionManager.data(player);
      ArrayList<CompanionListPacket.Entry> entries = new ArrayList<>();

      for (UUID uuid : data.wheelOrder(kind)) {
         Entity entity = CompanionManager.findLoadedEntity(player.level().getServer(), data, uuid).orElse(null);
         Optional<CompoundTag> shoulderTag = kind == CompanionKind.COMPANION ? CompanionManager.shoulderEntityTag(player, uuid) : Optional.empty();
         Optional<CompoundTag> storedTag = shoulderTag.<CompoundTag>map(tag -> CompanionManager.storedShoulderTag(player, uuid, tag))
            .or(() -> data.storedEntity(uuid).map(tag -> CompanionManager.healedStoredEntity(player, data, uuid, tag)));
         String entityType = entity == null ? storedTag.map(CompanionManager::storedEntityType).orElse("") : EntityType.getKey(entity.getType()).toString();
         String name = data.displayName(uuid)
            .orElseGet(
               () -> entity == null
                  ? storedTag.<String>map(tag -> CompanionManager.storedEntityName(tag, uuid)).orElse(uuid.toString().substring(0, 8))
                  : entity.getDisplayName().getString()
            );
         boolean loaded = entity != null;
         boolean alive = entity == null ? storedTag.isPresent() : entity.isAlive();
         boolean ridden = player.getVehicle() != null && player.getVehicle().getUUID().equals(uuid);
         boolean liveInWorld = entity instanceof LivingEntity living && living.isAlive() && !CompanionManager.isStoragePending(living);
         boolean homeResident = CompanionManager.isHomeResident(uuid);
         boolean deployed = ridden || shoulderTag.isPresent() || data.isDeployed(kind, uuid) || liveInWorld && !homeResident;
         boolean hasHome = data.homePosition(uuid).isPresent();
         float health = 0.0F;
         float maxHealth = 0.0F;
         float armor = 0.0F;
         if (entity instanceof LivingEntity living) {
            health = living.getHealth();
            maxHealth = living.getMaxHealth();
            armor = living.getArmorValue();
         } else if (storedTag.isPresent()) {
            CompoundTag tag = storedTag.get();
            health = tag.getFloatOr("CompanionRescueHealth", 0.0F);
            maxHealth = tag.getFloatOr("CompanionRescueMaxHealth", 0.0F);
            armor = tag.getIntOr("CompanionRescueArmor", 0);
         }

         CompanionMoveType entryMoveType = entity == null ? CompanionManager.moveType(entityType, kind) : CompanionManager.moveType(entity, kind);
         CompoundTag fullPreviewTag = entity == null
            ? storedTag.<CompoundTag>map(CompoundTag::copy).orElse(null)
            : CompanionManager.previewEntityTag(entity, entityType);
         CompoundTag previewTag = previewTagForPacket(player, kind, uuid, entityType, fullPreviewTag);
         entries.add(
            new CompanionListPacket.Entry(
               uuid,
               entity == null ? -1 : entity.getId(),
               entityType,
               name,
               loaded,
               alive,
               deployed,
               ridden,
               hasHome,
               homeResident,
               health,
               maxHealth,
               armor,
               entryMoveType,
               previewTag
            )
         );
      }

      ModNetwork.sendToPlayer(player, new CompanionListPacket(kind, data.activeWheelIndex(kind), entries));
   }

   static void forgetPlayer(ServerPlayer player) {
      if (player != null) {
         SENT_PREVIEW_SIGNATURES.remove(player.getUUID());
      }
   }

   static void syncDeadToClient(ServerPlayer player) {
      PlayerCompanionData data = CompanionManager.data(player);
      ArrayList<DeadCompanionListPacket.Entry> entries = new ArrayList<>();

      for (UUID uuid : data.deadList()) {
         Optional<CompoundTag> storedTag = data.storedEntity(uuid);
         CompanionKind kind = data.kindOf(uuid).orElse(CompanionKind.COMPANION);
         String entityType = storedTag.map(CompanionManager::storedEntityType).orElse("");
         String name = data.displayName(uuid)
            .orElseGet(() -> storedTag.<String>map(tag -> CompanionManager.storedEntityName(tag, uuid)).orElse(uuid.toString().substring(0, 8)));
         if (!name.endsWith(" (Dead)")) {
            name = name + " (Dead)";
         }

         float health = storedTag.<Float>map(tag -> tag.getFloatOr("CompanionRescueHealth", 0.0F)).orElse(0.0F);
         float maxHealth = storedTag.<Float>map(tag -> tag.getFloatOr("CompanionRescueMaxHealth", 0.0F)).orElse(0.0F);
         float armor = storedTag.<Float>map(tag -> (float)tag.getIntOr("CompanionRescueArmor", 0)).orElse(0.0F);
         CompanionMoveType moveType = CompanionManager.moveType(entityType, kind);
         CompoundTag previewTag = storedTag.<CompoundTag>map(CompoundTag::copy).orElse(null);
         entries.add(new DeadCompanionListPacket.Entry(uuid, kind, entityType, name, health, maxHealth, armor, moveType, previewTag));
      }

      ModNetwork.sendToPlayer(player, new DeadCompanionListPacket(entries));
   }

   private static CompoundTag previewTagForPacket(ServerPlayer player, CompanionKind kind, UUID uuid, String entityType, CompoundTag previewTag) {
      if (previewTag == null) {
         return null;
      }

      String signature = entityType + ":" + previewTag.hashCode();
      Map<UUID, String> kindCache = SENT_PREVIEW_SIGNATURES.computeIfAbsent(player.getUUID(), ignored -> new EnumMap<>(CompanionKind.class))
         .computeIfAbsent(kind, ignored -> new HashMap<>());
      String previous = kindCache.put(uuid, signature);
      return signature.equals(previous) ? null : previewTag;
   }
}
