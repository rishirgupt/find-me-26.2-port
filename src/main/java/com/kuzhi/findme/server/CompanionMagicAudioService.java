package com.kuzhi.findme.server;

import com.kuzhi.findme.Config;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

final class CompanionMagicAudioService {
   private CompanionMagicAudioService() {
   }

   static void playCircleOpen(ServerPlayer player, Vec3 position, float radius, boolean urgent) {
      float size = Math.max(1.0F, Math.min(radius, 10.0F));
      playNearOwner(player, position, SoundEvents.ENCHANTMENT_TABLE_USE, 0.56F + size * 0.026F, urgent ? 0.84F : 1.04F);
      playNearOwner(player, position, SoundEvents.AMETHYST_BLOCK_CHIME, urgent ? 0.46F : 0.32F, urgent ? 1.34F : 1.18F);
      if (urgent) {
         playNearOwner(player, position, SoundEvents.BEACON_POWER_SELECT, 0.24F + size * 0.01F, 1.28F);
      }
   }

   static void playStorageOpen(ServerPlayer player, Vec3 position, float radius) {
      float size = Math.max(1.0F, Math.min(radius, 12.0F));
      playNearOwner(player, position, SoundEvents.ENCHANTMENT_TABLE_USE, 0.48F + size * 0.022F, 0.92F);
      playNearOwner(player, position, SoundEvents.AMETHYST_BLOCK_CHIME, 0.2F + size * 0.012F, 1.42F);
   }

   static void playStorageWhoosh(ServerPlayer player, Vec3 position, int stage) {
      float pitch = switch (stage) {
         case 0 -> 1.24F;
         case 1 -> 1.06F;
         default -> 0.88F;
      };

      float volume = switch (stage) {
         case 0 -> 0.2F;
         case 1 -> 0.26F;
         default -> 0.34F;
      };
      SoundEvent sound = stage == 1 ? SoundEvents.EVOKER_CAST_SPELL : SoundEvents.ILLUSIONER_CAST_SPELL;
      playNearOwner(player, position, sound, volume, pitch);
   }

   static void playStorageComplete(ServerPlayer player, Vec3 position) {
      playNearOwner(player, position, SoundEvents.ENDERMAN_TELEPORT, 0.3F, 1.42F);
      playNearOwner(player, position, SoundEvents.AMETHYST_BLOCK_CHIME, 0.3F, 1.55F);
   }

   private static void playNearOwner(ServerPlayer player, Vec3 position, SoundEvent sound, float volume, float pitch) {
      if (player != null && position != null) {
         volume = scaleVolume(volume);
         if (!(volume <= 0.0F)) {
            Vec3 soundPos = position;
            Vec3 delta = position.subtract(player.position());
            if (delta.lengthSqr() > 576.0) {
               soundPos = player.position().add(delta.normalize().scale(8.0));
            }

            player.level().playSound(null, soundPos.x, soundPos.y, soundPos.z, sound, SoundSource.NEUTRAL, volume, pitch);
         }
      }
   }

   private static float scaleVolume(float volume) {
      double scale = Math.max(0.0, Math.min(2.0, Config.magicEffectVolume));
      return (float)(volume * scale);
   }
}
