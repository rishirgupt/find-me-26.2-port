package com.kuzhi.findme.mixin.client;

import com.kuzhi.findme.client.HandVisibility;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
   @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
   private void findme$hideHand(CallbackInfo ci) {
      if (!HandVisibility.visible()) {
         ci.cancel();
      }
   }
}
