package com.kuzhi.findme.server;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;

final class CompanionEntityClassifier {
   private static final Map<Class<?>, List<CompanionEntityClassifier.OwnershipMethod>> OWNERSHIP_METHODS = new HashMap<>();

   private CompanionEntityClassifier() {
   }

   static Optional<CompanionKind> classify(ServerPlayer player, Entity entity) {
      Identifier id = EntityType.getKey(entity.getType());
      String key = id.toString();
      if (Config.customMounts.contains(key) || isKnownRideableMountKey(key)) {
         return Optional.of(CompanionKind.MOUNT);
      } else if (Config.customCompanions.contains(key)) {
         return Optional.of(CompanionKind.COMPANION);
      } else if (!isOwnedBy(player, entity)) {
         return Optional.empty();
      } else {
         return !(entity instanceof AbstractHorse) && !isKnownRideableMountKey(key) ? Optional.of(CompanionKind.COMPANION) : Optional.of(CompanionKind.MOUNT);
      }
   }

   static boolean isManualBindingMountKey(String key) {
      return key != null && (Config.customMounts.contains(key.toLowerCase(Locale.ROOT)) || isExplicitCompatibleMountKey(key));
   }

   static boolean isIceAndFireDragonKey(String key) {
      String normalized = key.toLowerCase(Locale.ROOT);
      return normalized.equals("iceandfire:fire_dragon") || normalized.equals("iceandfire:ice_dragon") || normalized.equals("iceandfire:lightning_dragon");
   }

   static CompanionMoveType moveType(Entity entity, CompanionKind kind) {
      if (kind == CompanionKind.COMPANION) {
         return CompanionMoveType.COMPANION;
      } else if (entity == null) {
         return CompanionMoveType.WALK;
      } else {
         String key = EntityType.getKey(entity.getType()).toString();
         Optional<CompanionMoveType> configured = configuredMoveType(key);
         if (configured.isPresent()) {
            return configured.get();
         } else if (Config.customWalking.contains(key)) {
            return CompanionMoveType.WALK;
         } else if (Config.customSwimming.contains(key)
            || isKnownSwimmingMountKey(key)
            || entity instanceof WaterAnimal
            || entity.getType().getCategory() == MobCategory.WATER_CREATURE) {
            return CompanionMoveType.SWIM;
         } else {
            return !Config.customFlying.contains(key) && !(com.kuzhi.findme.compat.EntityCompat.isFlyingMob(entity)) && !isKnownFlyingMountKey(key)
               ? CompanionMoveType.WALK
               : CompanionMoveType.FLY;
         }
      }
   }

   static CompanionMoveType summonMoveType(Entity entity, CompanionKind kind) {
      if (kind == CompanionKind.MOUNT) {
         return moveType(entity, kind);
      } else {
         String key = EntityType.getKey(entity.getType()).toString();
         Optional<CompanionMoveType> configured = configuredMoveType(key);
         if (configured.isPresent()) {
            return configured.get();
         } else if (Config.customWalking.contains(key)) {
            return CompanionMoveType.WALK;
         } else if (Config.customSwimming.contains(key)
            || isKnownSwimmingMountKey(key)
            || entity instanceof WaterAnimal
            || entity.getType().getCategory() == MobCategory.WATER_CREATURE) {
            return CompanionMoveType.SWIM;
         } else {
            return !Config.customFlying.contains(key) && !(com.kuzhi.findme.compat.EntityCompat.isFlyingMob(entity)) && !isKnownFlyingMountKey(key) && !isKnownFlyingCompanionKey(key)
               ? CompanionMoveType.WALK
               : CompanionMoveType.FLY;
         }
      }
   }

   static CompanionMoveType summonMoveType(String entityType, CompanionKind kind) {
      if (kind == CompanionKind.MOUNT) {
         return moveType(entityType, kind);
      }

      if (entityType != null && !entityType.isBlank()) {
         Optional<CompanionMoveType> configured = configuredMoveType(entityType);
         if (configured.isPresent()) {
            return configured.get();
         } else if (Config.customWalking.contains(entityType)) {
            return CompanionMoveType.WALK;
         } else if (Config.customSwimming.contains(entityType) || isKnownSwimmingMountKey(entityType)) {
            return CompanionMoveType.SWIM;
         } else {
            return !Config.customFlying.contains(entityType) && !isKnownFlyingMountKey(entityType) && !isKnownFlyingCompanionKey(entityType)
               ? CompanionMoveType.WALK
               : CompanionMoveType.FLY;
         }
      } else {
         return CompanionMoveType.WALK;
      }
   }

   static CompanionMoveType moveType(String entityType, CompanionKind kind) {
      if (kind == CompanionKind.COMPANION) {
         return CompanionMoveType.COMPANION;
      }

      if (entityType != null && !entityType.isBlank()) {
         Optional<CompanionMoveType> configured = configuredMoveType(entityType);
         if (configured.isPresent()) {
            return configured.get();
         } else if (Config.customWalking.contains(entityType)) {
            return CompanionMoveType.WALK;
         } else if (Config.customSwimming.contains(entityType) || isKnownSwimmingMountKey(entityType)) {
            return CompanionMoveType.SWIM;
         } else {
            return !Config.customFlying.contains(entityType) && !isKnownFlyingMountKey(entityType) ? CompanionMoveType.WALK : CompanionMoveType.FLY;
         }
      } else {
         return CompanionMoveType.WALK;
      }
   }

   private static boolean isKnownRideableMountKey(String key) {
      if (isExplicitCompatibleMountKey(key)) {
         return true;
      }

      String normalized = key.toLowerCase(Locale.ROOT);
      return normalized.contains("dragon")
         || normalized.contains("horse")
         || normalized.contains("donkey")
         || normalized.contains("mule")
         || normalized.contains("llama")
         || normalized.contains("camel")
         || normalized.contains("pig")
         || normalized.contains("strider")
         || normalized.contains("ravager")
         || normalized.contains("wyrm")
         || normalized.contains("wyvern")
         || normalized.contains("drake")
         || normalized.contains("griffin")
         || normalized.contains("gryphon")
         || normalized.contains("hippogryph")
         || normalized.contains("hippocampus")
         || normalized.contains("pegasus")
         || normalized.contains("manticore")
         || normalized.contains("cockatrice")
         || normalized.contains("maid");
   }

   private static boolean isIceAndFireMountKey(String key) {
      String normalized = key.toLowerCase(Locale.ROOT);
      if (!normalized.startsWith("iceandfire:")) {
         return false;
      }

      String path = normalized.substring("iceandfire:".length());
      return path.equals("fire_dragon")
         || path.equals("ice_dragon")
         || path.equals("lightning_dragon")
         || path.equals("hippogryph")
         || path.equals("hippocampus")
         || path.equals("amphithere")
         || path.equals("deathworm")
         || path.equals("cockatrice")
         || path.equals("dread_horse");
   }

   static boolean isDragonMountsLegacyKey(String key) {
      return key.toLowerCase(Locale.ROOT).equals("dragonmounts:dragon");
   }

   private static boolean isErsMountKey(String key) {
      String normalized = key.toLowerCase(Locale.ROOT);
      if (!normalized.startsWith("ers:")) {
         return false;
      }

      String path = normalized.substring("ers:".length());
      return path.equals("dentisaurus_longirostris") || path.equals("terridensaurus_saevus");
   }

   private static boolean isSaintsDragonsMountKey(String key) {
      String normalized = key.toLowerCase(Locale.ROOT);
      if (!normalized.startsWith("saintsdragons:")) {
         return false;
      }

      String path = normalized.substring("saintsdragons:".length());
      return path.equals("raevyx")
         || path.equals("stegonaut")
         || path.equals("cindervane")
         || path.equals("varasuchus")
         || path.equals("ignivorus")
         || path.equals("volitans")
         || path.equals("nulljaw");
   }

   private static boolean isExplicitCompatibleMountKey(String key) {
      return isIceAndFireMountKey(key) || isErsMountKey(key) || isSaintsDragonsMountKey(key);
   }

   private static boolean isKnownFlyingCompanionKey(String key) {
      String normalized = key.toLowerCase(Locale.ROOT);
      return normalized.equals("minecraft:parrot")
         || normalized.equals("minecraft:allay")
         || normalized.equals("minecraft:bat")
         || normalized.contains("bird")
         || normalized.contains("parrot")
         || normalized.contains("raven")
         || normalized.contains("crow")
         || normalized.contains("owl")
         || normalized.contains("eagle")
         || normalized.contains("falcon");
   }

   private static boolean isKnownFlyingMountKey(String key) {
      String normalized = key.toLowerCase(Locale.ROOT);
      if (normalized.startsWith("ers:")) {
         return false;
      } else if (normalized.startsWith("iceandfire:")) {
         String path = normalized.substring("iceandfire:".length());
         return path.equals("fire_dragon")
            || path.equals("ice_dragon")
            || path.equals("lightning_dragon")
            || path.equals("hippogryph")
            || path.equals("amphithere");
      } else if (normalized.startsWith("saintsdragons:")) {
         String path = normalized.substring("saintsdragons:".length());
         return path.equals("raevyx") || path.equals("cindervane") || path.equals("ignivorus") || path.equals("volitans") || path.equals("nulljaw");
      } else {
         return normalized.contains("dragon")
            || normalized.contains("wyvern")
            || normalized.contains("griffin")
            || normalized.contains("gryphon")
            || normalized.contains("hippogryph")
            || normalized.contains("pegasus");
      }
   }

   private static boolean isKnownSwimmingMountKey(String key) {
      String normalized = key.toLowerCase(Locale.ROOT);
      return normalized.equals("iceandfire:hippocampus") || normalized.equals("ers:dentisaurus_longirostris") || normalized.equals("saintsdragons:varasuchus");
   }

   private static Optional<CompanionMoveType> configuredMoveType(String entityType) {
      return Config.customMoveType(entityType).flatMap(value -> {
         return switch (value) {
            case "fly", "flying" -> Optional.of(CompanionMoveType.FLY);
            case "swim", "swimming", "water" -> Optional.of(CompanionMoveType.SWIM);
            case "walk", "walking", "ground", "land" -> Optional.of(CompanionMoveType.WALK);
            case "companion" -> Optional.of(CompanionMoveType.COMPANION);
            default -> Optional.empty();
         };
      });
   }

   static boolean isOwnedBy(ServerPlayer player, Entity entity) {
      if (entity instanceof OwnableEntity ownable) {
         return player.getUUID().equals(com.kuzhi.findme.compat.EntityCompat.ownerUuid(ownable));
      } else if (entity instanceof TamableAnimal tamable) {
         return tamable.isTame() && player.getUUID().equals(com.kuzhi.findme.compat.EntityCompat.ownerUuid(tamable));
      } else {
         return entity instanceof AbstractHorse horse
            ? horse.isTamed() && player.getUUID().equals(com.kuzhi.findme.compat.EntityCompat.ownerUuid(horse))
            : isOwnedByReflectedMethod(player, entity) || isOwnedBySavedTag(player, entity);
      }
   }

   static boolean hasOwnershipSignal(Entity entity) {
      if (entity instanceof OwnableEntity ownable) {
         return com.kuzhi.findme.compat.EntityCompat.ownerUuid(ownable) != null;
      } else if (entity instanceof TamableAnimal tamable) {
         return tamable.isTame() && com.kuzhi.findme.compat.EntityCompat.ownerUuid(tamable) != null;
      } else if (entity instanceof AbstractHorse horse) {
         return horse.isTamed() && com.kuzhi.findme.compat.EntityCompat.ownerUuid(horse) != null;
      } else {
         if (!OWNERSHIP_METHODS.computeIfAbsent(entity.getClass(), CompanionEntityClassifier::findOwnershipMethods).isEmpty()) {
            return true;
         }

         Identifier entityKey = EntityType.getKey(entity.getType());
         if ("minecraft".equals(entityKey.getNamespace())) {
            return false;
         }

         CompoundTag tag = new CompoundTag();

         try {
            com.kuzhi.findme.compat.EntityNbt.saveWithoutId(entity, tag);
         } catch (RuntimeException ignored) {
            return false;
         }

         String[] uuidKeys = new String[]{
            "Owner", "OwnerUUID", "owner", "ownerUUID", "TameOwner", "TameOwnerUUID", "MasterUUID", "masterUUID", "MaidMasterUUID"
         };

         for (String key : uuidKeys) {
            if (tag.read(key, net.minecraft.core.UUIDUtil.CODEC).isPresent() || !tag.getStringOr(key, "").isBlank()) {
               return true;
            }
         }

         return false;
      }
   }

   private static boolean isOwnedByReflectedMethod(ServerPlayer player, Entity entity) {
      for (CompanionEntityClassifier.OwnershipMethod ownershipMethod : OWNERSHIP_METHODS.computeIfAbsent(
         entity.getClass(), CompanionEntityClassifier::findOwnershipMethods
      )) {
         try {
            Object value = ownershipMethod.method().invoke(entity);
            if (ownerValueMatches(player, value)) {
               return true;
            }
         } catch (ReflectiveOperationException | RuntimeException var5) {
         }
      }

      return false;
   }

   private static List<CompanionEntityClassifier.OwnershipMethod> findOwnershipMethods(Class<?> type) {
      ArrayList<CompanionEntityClassifier.OwnershipMethod> methods = new ArrayList<>();

      for (Method method : type.getMethods()) {
         if (method.getParameterCount() == 0) {
            String name = method.getName().toLowerCase(Locale.ROOT);
            if (name.equals("getowneruuid")
               || name.equals("getownerid")
               || name.equals("getowner")
               || name.equals("gettameowneruuid")
               || name.equals("getmasteruuid")
               || name.equals("getmaster")
               || name.equals("getmaidmasteruuid")) {
               Class<?> returnType = method.getReturnType();
               if (UUID.class.isAssignableFrom(returnType)
                  || Entity.class.isAssignableFrom(returnType)
                  || Player.class.isAssignableFrom(returnType)
                  || String.class.isAssignableFrom(returnType)) {
                  methods.add(new CompanionEntityClassifier.OwnershipMethod(method));
               }
            }
         }
      }

      return List.copyOf(methods);
   }

   private static boolean ownerValueMatches(ServerPlayer player, Object value) {
      if (value instanceof UUID uuid) {
         return player.getUUID().equals(uuid);
      } else if (value instanceof Entity entity) {
         return player.getUUID().equals(entity.getUUID());
      } else if (value instanceof String text) {
         try {
            return player.getUUID().equals(UUID.fromString(text));
         } catch (IllegalArgumentException ignored) {
            return player.getScoreboardName().equals(text) || player.getGameProfile().name().equals(text);
         }
      } else {
         return false;
      }
   }

   private static boolean isOwnedBySavedTag(ServerPlayer player, Entity entity) {
      Identifier entityKey = EntityType.getKey(entity.getType());
      if ("minecraft".equals(entityKey.getNamespace())) {
         return false;
      }

      CompoundTag tag = new CompoundTag();

      try {
         com.kuzhi.findme.compat.EntityNbt.saveWithoutId(entity, tag);
      } catch (RuntimeException ignored) {
         return false;
      }

      String[] uuidKeys = new String[]{"Owner", "OwnerUUID", "owner", "ownerUUID", "TameOwner", "TameOwnerUUID", "MasterUUID", "masterUUID", "MaidMasterUUID"};

      for (String key : uuidKeys) {
         if (tag.read(key, net.minecraft.core.UUIDUtil.CODEC).isPresent() && player.getUUID().equals(tag.read(key, net.minecraft.core.UUIDUtil.CODEC).orElseThrow())) {
            return true;
         }

         String value = tag.getStringOr(key, "");
         if (!value.isBlank() && ownerValueMatches(player, value)) {
            return true;
         }
      }

      return false;
   }

   private record OwnershipMethod(Method method) {
   }
}
