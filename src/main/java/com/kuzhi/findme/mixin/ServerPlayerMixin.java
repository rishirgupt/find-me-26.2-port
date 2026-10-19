package com.kuzhi.findme.mixin;

import com.kuzhi.findme.server.CompanionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
   /** Before a player leaves for another dimension, deployed creatures and vehicles are collected. */
   @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At("HEAD"))
   private void findme$beforeDimensionChange(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir) {
      ServerPlayer self = (ServerPlayer)(Object)this;
      if (transition.newLevel() != self.level() && !self.isRemoved()) {
         CompanionEvents.onPlayerTravel(self);
      }
   }
}
