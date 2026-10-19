package com.kuzhi.findme.server;

import com.kuzhi.findme.Config;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

final class CompanionRescueProtectionService {
   private static final int COMPANION_PROTECTION_TICKS = 120;
   private static final int PLAYER_PROTECTION_TICKS = 45;

   private CompanionRescueProtectionService() {
   }

   static void protectPlayer(Player player) {
      if (player != null) {
         player.fallDistance = 0.0F;
         player.clearFire();
         player.invulnerableTime = Math.max(player.invulnerableTime, Config.postTeleportInvulnerabilityTicks);
         player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 45, 0, false, false, true));
      }
   }

   static void protectCompanion(LivingEntity companion) {
      if (companion != null) {
         companion.fallDistance = 0.0F;
         companion.clearFire();
         companion.setAirSupply(companion.getMaxAirSupply());
         companion.invulnerableTime = Math.max(companion.invulnerableTime, Config.postTeleportInvulnerabilityTicks);
         companion.removeEffect(MobEffects.POISON);
         companion.removeEffect(MobEffects.WITHER);
         addIfUseful(companion, MobEffects.RESISTANCE, 120, 1);
         addIfUseful(companion, MobEffects.FIRE_RESISTANCE, 120, 0);
         addIfUseful(companion, MobEffects.SPEED, 120, 1);
         addIfUseful(companion, MobEffects.STRENGTH, 120, 0);
      }
   }

   private static void addIfUseful(LivingEntity living, Holder<MobEffect> effect, int duration, int amplifier) {
      MobEffectInstance current = living.getEffect(effect);
      if (current == null || current.getDuration() < duration || current.getAmplifier() < amplifier) {
         living.addEffect(new MobEffectInstance(effect, duration, amplifier, false, false, true));
      }
   }
}
