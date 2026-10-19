package com.kuzhi.findme.server;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.StorageEffectPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class CompanionStorageService {
   private static final Map<UUID, Long> INTENTIONAL_STORAGE_REMOVALS = new HashMap<>();
   private static final List<CompanionStorageService.PendingStorageEffect> PENDING_STORAGE_EFFECTS = new ArrayList<>();
   private static final String SHOULDER_COMPANION_UUID = "FindMeCompanionUUID";
   private static final int STORAGE_EFFECT_TICKS = 18;

   private CompanionStorageService() {
   }

   static boolean consumeIntentionalStorageRemoval(MinecraftServer server, UUID uuid) {
      Long expiresAt = INTENTIONAL_STORAGE_REMOVALS.remove(uuid);
      if (expiresAt == null) {
         return false;
      }

      long now = server == null ? 0L : server.overworld().getGameTime();
      return now <= expiresAt;
   }

   static void storeAndDiscard(ServerPlayer player, PlayerCompanionData data, LivingEntity living) {
      if (data.contains(living.getUUID())) {
         if (!isStoragePending(living)) {
            CompanionManager.clearHomeResident(living);
            CompanionManager.cancelArrivalSequence(living);
            beginStorageEffect(player, living);
            storeEntity(player, data, living);
            living.stopRiding();
            markIntentionalStorageRemoval(player.level().getServer(), living.getUUID());
            PENDING_STORAGE_EFFECTS.removeIf(pending -> pending.entityUuid.equals(living.getUUID()));
            PENDING_STORAGE_EFFECTS.add(CompanionStorageService.PendingStorageEffect.normal(living.getUUID(), player.getUUID(), 18));
         }
      }
   }

   static boolean sendHomeAfterStorage(
      ServerPlayer player, PlayerCompanionData data, CompanionKind kind, LivingEntity living, ServerLevel destination, BlockPos target, float yRot, float xRot
   ) {
      if (player == null || data == null || kind == null || living == null || destination == null || target == null || !data.contains(kind, living.getUUID())) {
         return false;
      }

      if (isStoragePending(living)) {
         return false;
      }

      CompanionManager.clearHomeResident(living);
      CompanionManager.cancelArrivalSequence(living);
      beginStorageEffect(player, living);
      storeEntity(player, data, living);
      living.stopRiding();
      PENDING_STORAGE_EFFECTS.removeIf(pending -> pending.entityUuid.equals(living.getUUID()));
      SavedPosition homeTarget = SavedPosition.of(destination, target.getX() + 0.5, target.getY(), target.getZ() + 0.5, yRot, xRot);
      PENDING_STORAGE_EFFECTS.add(CompanionStorageService.PendingStorageEffect.homeReturn(living.getUUID(), player.getUUID(), 18, kind, homeTarget));
      return true;
   }

   static void tickStorageEffects(MinecraftServer server) {
      if (server != null && !PENDING_STORAGE_EFFECTS.isEmpty()) {
         Iterator<CompanionStorageService.PendingStorageEffect> iterator = PENDING_STORAGE_EFFECTS.iterator();

         while (iterator.hasNext()) {
            CompanionStorageService.PendingStorageEffect pending = iterator.next();
            Entity entity = CompanionManager.findEntity(server, pending.entityUuid).orElse(null);
            if (entity instanceof LivingEntity living && living.isAlive()) {
               ServerPlayer player = server.getPlayerList().getPlayer(pending.playerUuid);
               if (player == null) {
                  if (pending.homeTarget != null) {
                     finishHomeReturn(server, living, pending, null);
                  } else {
                     finishStorageEffect(server, living, null);
                  }

                  iterator.remove();
               } else {
                  stabilizePendingStorage(living);
                  if (pending.age == 4 || pending.age == 9 || pending.age == 13) {
                     CompanionMagicAudioService.playStorageWhoosh(player, living.position(), pending.age == 4 ? 0 : (pending.age == 9 ? 1 : 2));
                  }

                  pending.age++;
                  if (pending.age >= pending.durationTicks) {
                     if (pending.homeTarget != null) {
                        finishHomeReturn(server, living, pending, player);
                     } else {
                        finishStorageEffect(server, living, player);
                     }

                     iterator.remove();
                  }
               }
            } else {
               iterator.remove();
            }
         }
      }
   }

   static boolean isStoragePending(LivingEntity living) {
      return living != null && PENDING_STORAGE_EFFECTS.stream().anyMatch(pending -> pending.entityUuid.equals(living.getUUID()));
   }

   static boolean isStoragePending(UUID uuid) {
      return uuid != null && PENDING_STORAGE_EFFECTS.stream().anyMatch(pending -> pending.entityUuid.equals(uuid));
   }

   static void storeEntity(ServerPlayer player, PlayerCompanionData data, LivingEntity living) {
      saveEntitySnapshot(player, data, living, true);
   }

   static void snapshotEntity(ServerPlayer player, PlayerCompanionData data, LivingEntity living) {
      saveEntitySnapshot(player, data, living, false);
   }

   static boolean snapshotAndDiscardHomeResidentForSummon(ServerPlayer player, PlayerCompanionData data, LivingEntity living) {
      if (player != null && data != null && living != null && data.contains(living.getUUID()) && !isStoragePending(living)) {
         CompanionManager.clearHomeResident(living);
         CompanionManager.cancelArrivalSequence(living);
         storeEntity(player, data, living);
         living.stopRiding();
         markIntentionalStorageRemoval(player.level().getServer(), living.getUUID());
         living.discard();
         CompanionManager.save(player, data);
         return true;
      } else {
         return false;
      }
   }

   static CompoundTag storedShoulderTag(ServerPlayer player, UUID uuid, CompoundTag source) {
      CompoundTag tag = source.copy();
      resetStoredStateTags(tag);
      if (!tag.read("UUID", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
         tag.store("UUID", net.minecraft.core.UUIDUtil.CODEC, uuid);
      }

      tag.store("FindMeCompanionUUID", net.minecraft.core.UUIDUtil.CODEC, uuid);
      if (!tag.contains("CompanionRescueType") && tag.contains("id")) {
         tag.putString("CompanionRescueType", tag.getStringOr("id", ""));
      }

      if (!tag.contains("CompanionRescueName")) {
         tag.putString("CompanionRescueName", CompanionManager.storedEntityName(tag, uuid));
      }

      float health = tag.contains("Health") ? tag.getFloatOr("Health", 0.0F) : tag.getFloatOr("CompanionRescueHealth", 0.0F);
      if (health <= 0.0F) {
         health = 1.0F;
      }

      float maxHealth = tag.getFloatOr("CompanionRescueMaxHealth", 0.0F);
      if (maxHealth <= 0.0F) {
         maxHealth = Math.max(health, 1.0F);
      }

      tag.putFloat("CompanionRescueHealth", Math.min(health, maxHealth));
      tag.putFloat("CompanionRescueMaxHealth", maxHealth);
      tag.putFloat("Health", Math.min(health, maxHealth));
      if (!tag.contains("CompanionRescueArmor")) {
         tag.putInt("CompanionRescueArmor", 0);
      }

      tag.putLong("CompanionRescueStoredAt", player.level().getGameTime());
      return tag;
   }

   static CompoundTag healedStoredEntity(ServerPlayer player, PlayerCompanionData data, UUID uuid, CompoundTag storedTag) {
      CompoundTag tag = sanitizedStoredTag(storedTag);
      float health = tag.getFloatOr("CompanionRescueHealth", 0.0F);
      float maxHealth = tag.getFloatOr("CompanionRescueMaxHealth", 0.0F);
      long storedAt = tag.getLongOr("CompanionRescueStoredAt", 0L);
      if (!(maxHealth <= 0.0F) && !(health <= 0.0F) && !(health >= maxHealth)) {
         long now = player.level().getGameTime();
         if (storedAt <= 0L) {
            tag.putLong("CompanionRescueStoredAt", now);
            data.storeEntity(uuid, tag);
            CompanionManager.save(player, data);
            return tag;
         }

         float healed = Math.min(maxHealth, health + (float)Math.max(0L, now - storedAt) / 20.0F);
         if (healed > health) {
            tag.putFloat("CompanionRescueHealth", healed);
            tag.putLong("CompanionRescueStoredAt", now);
            if (tag.contains("Health")) {
               tag.putFloat("Health", healed);
            }

            data.storeEntity(uuid, tag);
            CompanionManager.save(player, data);
         }

         return tag;
      } else {
         return tag;
      }
   }

   static CompoundTag sanitizedStoredTag(CompoundTag source) {
      CompoundTag tag = source == null ? new CompoundTag() : source.copy();
      resetStoredStateTags(tag);
      return tag;
   }

   static void markIntentionalStorageRemoval(MinecraftServer server, UUID uuid) {
      if (server != null) {
         INTENTIONAL_STORAGE_REMOVALS.put(uuid, server.overworld().getGameTime() + 40L);
      }
   }

   private static void beginStorageEffect(ServerPlayer player, LivingEntity living) {
      AABB box = CompanionEntityVisualBoundsService.visualBounds(living);
      Vec3 anchor = new Vec3(box.getCenter().x, box.minY, box.getCenter().z);
      float radius = CompanionEntityVisualBoundsService.contractCircleRadius(living);
      float height = CompanionEntityVisualBoundsService.portalHeight(living);
      CompanionSummonLineService.showStorage(player, living);
      if (living.level() instanceof ServerLevel level) {
         ModNetwork.sendToPlayersNear(level, anchor, 96.0, new StorageEffectPacket(living.getId(), anchor.x, anchor.y, anchor.z, radius, height, 26));
      } else {
         ModNetwork.sendToPlayer(player, new StorageEffectPacket(living.getId(), anchor.x, anchor.y, anchor.z, radius, height, 26));
      }

      CompanionMagicAudioService.playStorageOpen(player, anchor, radius);
   }

   private static void stabilizePendingStorage(LivingEntity living) {
      living.stopRiding();
      living.setDeltaMovement(Vec3.ZERO);
      living.fallDistance = 0.0F;
      living.invulnerableTime = Math.max(living.invulnerableTime, 12);
      living.hurtMarked = true;
      if (living instanceof Mob mob) {
         mob.getNavigation().stop();
         mob.setTarget(null);
      }
   }

   private static void finishStorageEffect(MinecraftServer server, LivingEntity living, ServerPlayer player) {
      stabilizePendingStorage(living);
      if (player != null) {
         CompanionMagicAudioService.playStorageComplete(player, living.position());
      }

      markIntentionalStorageRemoval(server, living.getUUID());
      living.discard();
   }

   private static void finishHomeReturn(MinecraftServer server, LivingEntity living, CompanionStorageService.PendingStorageEffect pending, ServerPlayer player) {
      stabilizePendingStorage(living);
      if (player != null) {
         CompanionMagicAudioService.playStorageComplete(player, living.position());
      }

      ServerLevel destination = server.getLevel(pending.homeTarget.dimension());
      if (destination == null) {
         if (player != null) {
            if (player != null) {
               PlayerCompanionData data = CompanionManager.data(player);
               storeEntity(player, data, living);
               data.clearDeployed(pending.kind, living.getUUID());
               CompanionManager.save(player, data);
               CompanionManager.syncToClient(player, pending.kind);
            }

            markIntentionalStorageRemoval(server, living.getUUID());
            living.discard();
         }
      } else {
         BlockPos target = pending.homeTarget.blockPos();
         if (player == null) {
            LivingEntity moved = CompanionEntityTransferService.moveEntityTo(
               living, destination, target, pending.homeTarget.yRot(), pending.homeTarget.xRot(), false
            );
            CompanionHomeResidentService.markResidentEntity(moved);
         } else {
            PlayerCompanionData data = CompanionManager.data(player);
            UUID uuid = living.getUUID();
            markIntentionalStorageRemoval(server, uuid);
            living.discard();
            Entity restored = CompanionEntityTransferService.restoreStoredEntityFresh(
                  destination, player, data, uuid, target, pending.homeTarget.yRot(), pending.homeTarget.xRot()
               )
               .orElse(null);
            if (restored instanceof LivingEntity moved && moved.isAlive()) {
               CompanionHomeResidentService.markResidentEntity(moved);
               data.clearDeployed(pending.kind, moved.getUUID());
               data.setLastKnownPosition(
                  moved.getUUID(), SavedPosition.of(moved.level(), moved.getX(), moved.getY(), moved.getZ(), moved.getYRot(), moved.getXRot())
               );
            } else {
               data.clearDeployed(pending.kind, uuid);
            }

            CompanionManager.save(player, data);
            CompanionManager.syncToClient(player, pending.kind);
         }
      }
   }

   private static void saveEntitySnapshot(ServerPlayer player, PlayerCompanionData data, LivingEntity living, boolean resetLiveState) {
      if (resetLiveState) {
         resetLiveStateForStorage(living);
      }

      CompoundTag tag;
      if (com.kuzhi.findme.compat.EntityNbt.save(living, tag = new CompoundTag())) {
         writeStoredEntitySnapshot(player, data, living, tag);
      }
   }

   private static void writeStoredEntitySnapshot(ServerPlayer player, PlayerCompanionData data, LivingEntity living, CompoundTag tag) {
      resetStoredStateTags(tag);
      tag.putString("CompanionRescueName", living.getDisplayName().getString());
      tag.putString("CompanionRescueType", EntityType.getKey(living.getType()).toString());
      CompanionManager.writePreviewBounds(living, tag);
      tag.putFloat("CompanionRescueHealth", living.getHealth());
      tag.putFloat("CompanionRescueMaxHealth", living.getMaxHealth());
      tag.putInt("CompanionRescueArmor", living.getArmorValue());
      tag.putLong("CompanionRescueStoredAt", player.level().getGameTime());
      tag.putBoolean("NoAI", false);
      tag.putBoolean("NoGravity", false);
      tag.putBoolean("Invisible", false);
      data.storeEntity(living.getUUID(), tag);
      data.setLastKnownPosition(
         living.getUUID(), SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(), living.getYRot(), living.getXRot())
      );
   }

   private static void resetLiveStateForStorage(LivingEntity living) {
      living.stopRiding();
      living.clearFire();
      living.setTicksFrozen(0);
      living.setDeltaMovement(Vec3.ZERO);
      living.setInvisible(false);
      living.setNoGravity(false);
      living.fallDistance = 0.0F;
      living.hurtMarked = true;
      if (living instanceof TamableAnimal tamable) {
         tamable.setOrderedToSit(false);
         tamable.setInSittingPose(false);
      }

      if (living instanceof Mob mob) {
         mob.getNavigation().stop();
         mob.setTarget(null);
         mob.setNoAi(false);
      }
   }

   private static void resetStoredStateTags(CompoundTag tag) {
      tag.putShort("Fire", (short)0);
      tag.putFloat("FallDistance", 0.0F);
      tag.putBoolean("NoAI", false);
      tag.putBoolean("NoGravity", false);
      tag.putBoolean("Invisible", false);
      tag.putBoolean("Sitting", false);
      tag.putBoolean("OrderedToSit", false);
      tag.putBoolean("InSittingPose", false);
      tag.putBoolean("IsSitting", false);
      tag.putBoolean("SittingPose", false);
      tag.remove("Motion");
      tag.remove("Passengers");
      tag.remove("Leash");
   }

   private static final class PendingStorageEffect {
      final UUID entityUuid;
      final UUID playerUuid;
      final int durationTicks;
      final CompanionKind kind;
      final SavedPosition homeTarget;
      int age;

      private PendingStorageEffect(UUID entityUuid, UUID playerUuid, int durationTicks, CompanionKind kind, SavedPosition homeTarget) {
         this.entityUuid = entityUuid;
         this.playerUuid = playerUuid;
         this.durationTicks = durationTicks;
         this.kind = kind;
         this.homeTarget = homeTarget;
      }

      static CompanionStorageService.PendingStorageEffect normal(UUID entityUuid, UUID playerUuid, int durationTicks) {
         return new CompanionStorageService.PendingStorageEffect(entityUuid, playerUuid, durationTicks, null, null);
      }

      static CompanionStorageService.PendingStorageEffect homeReturn(
         UUID entityUuid, UUID playerUuid, int durationTicks, CompanionKind kind, SavedPosition homeTarget
      ) {
         return new CompanionStorageService.PendingStorageEffect(entityUuid, playerUuid, durationTicks, kind, homeTarget);
      }
   }
}
