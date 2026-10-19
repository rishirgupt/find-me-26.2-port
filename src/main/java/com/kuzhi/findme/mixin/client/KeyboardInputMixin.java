package com.kuzhi.findme.mixin.client;

import com.kuzhi.findme.client.CompanionWheelScreen;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets the player keep walking while a summon wheel is open: the wheel is a screen, which normally releases the keys. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
   @Inject(method = "tick", at = @At("TAIL"))
   private void findme$moveBehindWheel(CallbackInfo ci) {
      Input held = CompanionWheelScreen.heldMovement();
      if (held != null) {
         this.keyPresses = held;
         float forward = held.forward() == held.backward() ? 0.0F : (held.forward() ? 1.0F : -1.0F);
         float left = held.left() == held.right() ? 0.0F : (held.left() ? 1.0F : -1.0F);
         this.moveVector = new Vec2(left, forward).normalized();
      }
   }
}
