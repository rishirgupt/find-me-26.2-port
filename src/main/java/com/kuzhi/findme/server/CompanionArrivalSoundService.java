package com.kuzhi.findme.server;

import com.kuzhi.findme.Config;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class CompanionArrivalSoundService {
   private CompanionArrivalSoundService() {
   }

   static void playCommandVoice(ServerPlayer player, LivingEntity living) {
      if (Config.enableCreatureArrivalVoice && living != null && !living.isSilent()) {
         SoundEvent voice = resolveVoice(living);
         if (voice != null) {
            float volume = (float)Math.max(0.0, Math.min(2.0, Config.creatureArrivalVoiceVolume));
            if (volume > 0.0F) {
               playNearPlayer(player, living, voice, volume, voicePitch(living));
            }
         }
      }
   }

   private static SoundEvent resolveVoice(LivingEntity living) {
      SoundEvent voice = reflectedEntitySound(living);
      if (voice == null) {
         voice = specificAmbientSound(living);
      }

      if (voice == null) {
         voice = registryAmbientSound(living);
      }

      return voice;
   }

   private static void playNearPlayer(ServerPlayer player, LivingEntity living, SoundEvent sound, float volume, float pitch) {
      Vec3 soundPos = living.position();
      Level soundLevel = living.level();
      if (player != null && player.level() == living.level()) {
         Vec3 direction = living.position().subtract(player.position());
         soundPos = player.position().add(direction.lengthSqr() > 0.001 ? direction.normalize().scale(2.5) : player.getLookAngle().scale(2.5));
      } else if (player != null) {
         soundLevel = player.level();
         soundPos = player.position().add(player.getLookAngle().scale(2.5));
      }

      soundLevel.playSound(null, soundPos.x, soundPos.y, soundPos.z, sound, SoundSource.NEUTRAL, volume, pitch);
   }

   private static SoundEvent reflectedEntitySound(LivingEntity living) {
      for (Class<?> type = living.getClass(); type != null && LivingEntity.class.isAssignableFrom(type); type = type.getSuperclass()) {
         SoundEvent sound = reflectedNoArgSound(living, type, "getAmbientSound");
         if (sound != null) {
            return sound;
         }

         sound = reflectedNoArgSound(living, type, "getRoarSound");
         if (sound != null) {
            return sound;
         }
      }

      return null;
   }

   private static SoundEvent reflectedNoArgSound(LivingEntity living, Class<?> type, String methodName) {
      try {
         Method method = type.getDeclaredMethod(methodName);
         method.setAccessible(true);
         return method.invoke(living) instanceof SoundEvent sound ? sound : null;
      } catch (ReflectiveOperationException | RuntimeException ignored) {
         return null;
      }
   }

   private static SoundEvent registryAmbientSound(LivingEntity living) {
      Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType());
      if (typeId == null) {
         return null;
      }

      String namespace = typeId.getNamespace();
      String path = typeId.getPath();
      String compactPath = path.replace("_", "");
      String ageHint = ageHint(living);

      for (Identifier candidate : List.of(
         id(namespace, "entity." + path + ".ambient"),
         id(namespace, "entity." + path + ".idle"),
         id(namespace, "entity." + path + ".say"),
         id(namespace, "entity." + path + ".roar"),
         id(namespace, path + ".ambient"),
         id(namespace, path + ".idle"),
         id(namespace, path + "_idle"),
         id(namespace, path + "_ambient"),
         id(namespace, path + "_roar"),
         id(namespace, path + "_adult_idle"),
         id(namespace, path + "_teen_idle"),
         id(namespace, path + "_child_idle"),
         id(namespace, compactPath + "_idle"),
         id(namespace, compactPath + "_ambient"),
         id(namespace, compactPath + "_roar"),
         id(namespace, compactPath + "_adult_idle"),
         id(namespace, compactPath + "_teen_idle"),
         id(namespace, compactPath + "_child_idle"),
         id(namespace, "mob." + path + ".say")
      )) {
         SoundEvent sound = (SoundEvent)BuiltInRegistries.SOUND_EVENT.getValue(candidate);
         if (sound != null) {
            return sound;
         }
      }

      return scanRelatedSound(typeId, ageHint);
   }

   private static Identifier id(String namespace, String path) {
      Identifier location = Identifier.tryParse(namespace + ":" + path);
      return location == null ? Identifier.tryParse("minecraft:empty") : location;
   }

   private static SoundEvent scanRelatedSound(Identifier entityTypeId, String ageHint) {
      String namespace = entityTypeId.getNamespace();
      String entityPath = entityTypeId.getPath();
      String compactEntityPath = compact(entityPath);
      int bestScore = 0;
      SoundEvent best = null;

      for (Identifier soundId : BuiltInRegistries.SOUND_EVENT.keySet()) {
         if (namespace.equals(soundId.getNamespace())) {
            int score = soundScore(soundId.getPath(), entityPath, compactEntityPath, ageHint);
            if (score > bestScore) {
               bestScore = score;
               best = (SoundEvent)BuiltInRegistries.SOUND_EVENT.getValue(soundId);
            }
         }
      }

      return bestScore >= 40 ? best : null;
   }

   private static int soundScore(String soundPath, String entityPath, String compactEntityPath, String ageHint) {
      String normalized = soundPath.replace('.', '_').replace('-', '_').toLowerCase();
      String compactSoundPath = compact(normalized);
      int score = 0;
      if (normalized.contains(entityPath)) {
         score += 40;
      }

      if (compactSoundPath.contains(compactEntityPath)) {
         score += 42;
      }

      for (String token : entityPath.split("_")) {
         if (token.length() >= 4 && normalized.contains(token)) {
            score += 8;
         }
      }

      if (normalized.contains("idle")) {
         score += 34;
      }

      if (normalized.contains("ambient")) {
         score += 30;
      }

      if (normalized.contains("say")) {
         score += 22;
      }

      if (normalized.contains("roar")) {
         score += 18;
      }

      if (normalized.contains("growl")) {
         score += 12;
      }

      if (normalized.contains("hurt")) {
         score -= 18;
      }

      if (normalized.contains("death")) {
         score -= 28;
      }

      if (normalized.contains("step")) {
         score -= 20;
      }

      if (ageHint != null && normalized.contains(ageHint)) {
         score += 16;
      } else if (ageHint == null && normalized.contains("adult")) {
         score += 4;
      }

      return score;
   }

   private static String compact(String value) {
      return value.replace("_", "").replace(".", "").replace("-", "").toLowerCase();
   }

   private static String ageHint(LivingEntity living) {
      if (reflectedBoolean(living, "isBaby")) {
         return "child";
      } else if (reflectedBoolean(living, "isTeen")) {
         return "teen";
      } else {
         return reflectedBoolean(living, "isMature") ? "adult" : null;
      }
   }

   private static boolean reflectedBoolean(LivingEntity living, String methodName) {
      for (Class<?> type = living.getClass(); type != null && LivingEntity.class.isAssignableFrom(type); type = type.getSuperclass()) {
         try {
            Method method = type.getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method.invoke(living) instanceof Boolean result && result;
         } catch (ReflectiveOperationException | RuntimeException ignored) {
         }
      }

      return false;
   }

   private static float voicePitch(LivingEntity living) {
      return living instanceof TamableAnimal ? 1.0F + (living.getRandom().nextFloat() - living.getRandom().nextFloat()) * 0.2F : 1.0F;
   }

   private static SoundEvent specificAmbientSound(LivingEntity living) {
      EntityType<?> type = living.getType();
      if (type == net.minecraft.world.entity.EntityTypes.WOLF) {
         return com.kuzhi.findme.compat.EntityCompat.vanillaSound("entity.wolf.ambient");
      } else if (type == net.minecraft.world.entity.EntityTypes.CAT) {
         return com.kuzhi.findme.compat.EntityCompat.vanillaSound("entity.cat.ambient");
      } else if (type == net.minecraft.world.entity.EntityTypes.HORSE) {
         return SoundEvents.HORSE_AMBIENT;
      } else if (type == net.minecraft.world.entity.EntityTypes.DONKEY) {
         return SoundEvents.DONKEY_AMBIENT;
      } else if (type == net.minecraft.world.entity.EntityTypes.MULE) {
         return SoundEvents.MULE_AMBIENT;
      } else if (type == net.minecraft.world.entity.EntityTypes.LLAMA || type == net.minecraft.world.entity.EntityTypes.TRADER_LLAMA) {
         return SoundEvents.LLAMA_AMBIENT;
      } else if (type == net.minecraft.world.entity.EntityTypes.CAMEL) {
         return SoundEvents.CAMEL_AMBIENT;
      } else if (type == net.minecraft.world.entity.EntityTypes.PIG) {
         return com.kuzhi.findme.compat.EntityCompat.vanillaSound("entity.pig.ambient");
      } else if (type == net.minecraft.world.entity.EntityTypes.STRIDER) {
         return SoundEvents.STRIDER_AMBIENT;
      } else if (type == net.minecraft.world.entity.EntityTypes.FOX) {
         return SoundEvents.FOX_AMBIENT;
      } else if (type == net.minecraft.world.entity.EntityTypes.PARROT) {
         return SoundEvents.PARROT_AMBIENT;
      } else if (type == net.minecraft.world.entity.EntityTypes.FROG) {
         return SoundEvents.FROG_AMBIENT;
      } else if (type == net.minecraft.world.entity.EntityTypes.ALLAY) {
         return SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
      } else if (type == net.minecraft.world.entity.EntityTypes.AXOLOTL) {
         return SoundEvents.AXOLOTL_IDLE_WATER;
      } else if (type == net.minecraft.world.entity.EntityTypes.TURTLE) {
         return SoundEvents.TURTLE_AMBIENT_LAND;
      } else {
         return type == net.minecraft.world.entity.EntityTypes.RAVAGER ? SoundEvents.RAVAGER_AMBIENT : null;
      }
   }
}
