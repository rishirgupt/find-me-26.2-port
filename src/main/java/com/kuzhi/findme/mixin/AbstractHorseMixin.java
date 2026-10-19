package com.kuzhi.findme.mixin;

import com.kuzhi.findme.server.CompanionEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractHorse.class)
public abstract class AbstractHorseMixin {
   @Inject(method = "tameWithName", at = @At("TAIL"))
   private void findme$afterTame(Player player, CallbackInfoReturnable<Boolean> cir) {
      CompanionEvents.onAnimalTame(player, (LivingEntity)(Object)this);
   }

   @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
   private void findme$cancelFall(double fallDistance, float damageModifier, DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
      LivingEntity self = (LivingEntity)(Object)this;
      if (CompanionEvents.cancelFall(self)) {
         self.fallDistance = 0.0;
         cir.setReturnValue(false);
      }
   }
}
