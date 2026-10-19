package com.kuzhi.findme.mixin;

import com.kuzhi.findme.server.CompanionEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TamableAnimal.class)
public abstract class TamableAnimalMixin {
   @Inject(method = "tame", at = @At("TAIL"))
   private void findme$afterTame(Player player, CallbackInfo ci) {
      CompanionEvents.onAnimalTame(player, (LivingEntity)(Object)this);
   }
}
