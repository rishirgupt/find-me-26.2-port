package com.kuzhi.findme.server;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

final class CompanionShoulderService {
   private static final String SHOULDER_COMPANION_UUID = "FindMeCompanionUUID";

   private CompanionShoulderService() {
   }

   static Optional<CompoundTag> shoulderEntityTag(ServerPlayer player, UUID uuid) {
      CompoundTag left = player.getShoulderEntityLeft();
      if (matchesShoulderEntity(left, uuid)) {
         return Optional.of(left.copy());
      }

      CompoundTag right = player.getShoulderEntityRight();
      return matchesShoulderEntity(right, uuid) ? Optional.of(right.copy()) : Optional.empty();
   }

   static boolean clearPlayerShoulderEntity(ServerPlayer player, UUID uuid) {
      if (matchesShoulderEntity(player.getShoulderEntityLeft(), uuid)) {
         return setPlayerShoulderEntity(player, true, new CompoundTag());
      } else {
         return matchesShoulderEntity(player.getShoulderEntityRight(), uuid) ? setPlayerShoulderEntity(player, false, new CompoundTag()) : false;
      }
   }

   static Optional<LivingEntity> releaseShoulderCompanion(ServerPlayer player, UUID uuid, CompoundTag source) {
      CompoundTag tag = source.copy();
      if (!tag.contains("id")) {
         return Optional.empty();
      }

      tag.store("UUID", net.minecraft.core.UUIDUtil.CODEC, uuid);
      Entity released = EntityType.loadEntityRecursive(tag, player.level(), new net.minecraft.world.entity.EntitySpawnRequest(net.minecraft.world.entity.EntitySpawnReason.LOAD, false), entity -> {
         entity.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
         return entity;
      });
      if (released instanceof LivingEntity living) {
         if (!clearPlayerShoulderEntity(player, uuid)) {
            return Optional.empty();
         }

         player.level().addFreshEntity(released);
         CompanionManager.restoreAnimationControl(living);
         return Optional.of(living);
      } else {
         return Optional.empty();
      }
   }

   private static boolean matchesShoulderEntity(CompoundTag tag, UUID uuid) {
      if (tag == null || tag.isEmpty()) {
         return false;
      } else {
         return tag.read("FindMeCompanionUUID", net.minecraft.core.UUIDUtil.CODEC).isPresent() && uuid.equals(tag.read("FindMeCompanionUUID", net.minecraft.core.UUIDUtil.CODEC).orElseThrow())
            ? true
            : tag.read("UUID", net.minecraft.core.UUIDUtil.CODEC).isPresent() && uuid.equals(tag.read("UUID", net.minecraft.core.UUIDUtil.CODEC).orElseThrow());
      }
   }

   private static boolean setPlayerShoulderEntity(ServerPlayer player, boolean left, CompoundTag tag) {
      String[] methodNames = left ? new String[]{"setShoulderEntityLeft", "m_36362_"} : new String[]{"setShoulderEntityRight", "m_36364_"};

      for (String name : methodNames) {
         try {
            Method method = Player.class.getDeclaredMethod(name, CompoundTag.class);
            method.setAccessible(true);
            method.invoke(player, tag);
            return true;
         } catch (ReflectiveOperationException | RuntimeException var9) {
         }
      }

      return false;
   }
}
