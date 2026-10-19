package com.kuzhi.findme.mixin.client;

import com.kuzhi.findme.client.ClientContractCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Places the view at the contract ceremony's side-on viewpoint. The camera itself stays on the player, because
 * the game only draws the local player while the camera belongs to them.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
   @Shadow
   protected abstract void setPosition(Vec3 position);

   @Shadow
   protected abstract void setRotation(float yRot, float xRot);

   @Inject(method = "alignWithEntity", at = @At("RETURN"))
   private void findme$ceremonyView(float partialTick, CallbackInfo ci) {
      Entity viewpoint = ClientContractCamera.viewpoint();
      if (viewpoint != null) {
         this.setRotation(viewpoint.getYRot(), viewpoint.getXRot());
         this.setPosition(viewpoint.position());
      }
   }
}
