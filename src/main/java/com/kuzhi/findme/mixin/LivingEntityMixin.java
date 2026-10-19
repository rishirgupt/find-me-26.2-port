package com.kuzhi.findme.mixin;

import com.kuzhi.findme.server.CompanionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
   /** Bound creatures keep their items for revival, so nothing is dropped; experience still is. */
   @Inject(method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V", at = @At("HEAD"), cancellable = true)
   private void findme$noLoot(ServerLevel level, DamageSource source, boolean playerKilled, CallbackInfo ci) {
      if (CompanionEvents.suppressDeathDrops((LivingEntity)(Object)this)) {
         ci.cancel();
      }
   }

   @Inject(method = "dropCustomDeathLoot", at = @At("HEAD"), cancellable = true)
   private void findme$noCustomLoot(ServerLevel level, DamageSource source, boolean killedByPlayer, CallbackInfo ci) {
      if (CompanionEvents.suppressDeathDrops((LivingEntity)(Object)this)) {
         ci.cancel();
      }
   }

   @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
   private void findme$noEquipment(ServerLevel level, CallbackInfo ci) {
      if (CompanionEvents.suppressDeathDrops((LivingEntity)(Object)this)) {
         ci.cancel();
      }
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
