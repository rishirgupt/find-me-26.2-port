package com.kuzhi.findme.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

final class ClientVehicleSealEffectRenderer {
   private static final ClientMagicCircleRenderer.Color BLUE = new ClientMagicCircleRenderer.Color(0.56F, 0.88F, 1.0F);
   private static final ClientMagicCircleRenderer.Color WHITE = new ClientMagicCircleRenderer.Color(0.98F, 1.0F, 1.0F);

   private ClientVehicleSealEffectRenderer() {
   }

   static void renderWorld(WorldRenderFrame event) {
      if (!ClientVehicleSealEffectState.effects().isEmpty()) {
         event.drawQuads((buffer, matrix) -> {

         for (ClientVehicleSealEffectState.Effect effect : ClientVehicleSealEffectState.effects()) {
            renderEffect(buffer, matrix, effect, event.partialTick());
         }
         });
      }
   }

   private static void renderEffect(VertexConsumer buffer, Matrix4f matrix, ClientVehicleSealEffectState.Effect effect, float partialTick) {
      float age = effect.age + partialTick;
      float progress = Mth.clamp(age / effect.durationTicks, 0.0F, 1.0F);
      float open = smooth(Mth.clamp(progress / 0.2F, 0.0F, 1.0F));
      float close = smooth(Mth.clamp((progress - 0.24F) / 0.58F, 0.0F, 1.0F));
      float fade = 1.0F - smooth(Mth.clamp((progress - 0.82F) / 0.18F, 0.0F, 1.0F));
      if (!(open * fade <= 0.01F)) {
         double baseY = effect.base.y + 0.18;
         double topY = effect.base.y + Math.max(1.0, effect.height) + 0.32;
         double centerY = (baseY + topY) * 0.5;
         double lowerY = Mth.lerp(close, baseY, centerY - 0.05);
         double upperY = Mth.lerp(close, topY, centerY + 0.05);
         double radius = effect.radius * (0.35 + 0.65 * open) * (1.0 - 0.28 * close);
         float alpha = 0.66F * open * fade;
         float spin = age * (0.055F + 0.035F * close) + (float)Math.sin(age * 0.08F) * 0.7F;
         Vec3 center = new Vec3(effect.base.x, effect.base.y, effect.base.z);
         ClientMagicCircleRenderer.renderSealDisk(buffer, matrix, center, lowerY, radius, spin, BLUE, alpha, false);
         ClientMagicCircleRenderer.renderSealDisk(buffer, matrix, center, upperY, radius * 0.98, -spin * 1.08, WHITE, alpha * 0.82F, true);
         renderSealLines(buffer, matrix, center, lowerY, upperY, radius, close, fade, age);
         float flash = smooth(Mth.clamp(1.0F - Math.abs(progress - 0.8F) / 0.12F, 0.0F, 1.0F));
         if (flash > 0.02F) {
            ClientMagicCircleRenderer.ring(
               buffer, matrix, center, centerY, Math.max(0.16, radius * (0.42 - 0.22 * close)), 72, spin * 1.6, 0.05 + radius * 0.006, WHITE, flash * 0.48F
            );
         }
      }
   }

   private static void renderSealLines(
      VertexConsumer buffer, Matrix4f matrix, Vec3 center, double lowerY, double upperY, double radius, float close, float fade, float age
   ) {
      if (!(close <= 0.02F)) {
         int strands = 8;
         float alpha = fade * close * 0.36F;

         for (int i = 0; i < strands; i++) {
            double angle = (Math.PI * 2) * i / strands + age * 0.028 + Math.sin(age * 0.05 + i) * 0.08;
            double strandRadius = radius * (0.76 + 0.1 * Math.sin(age * 0.06 + i));
            Vec3 bottom = new Vec3(center.x + Math.cos(angle) * strandRadius, lowerY, center.z + Math.sin(angle) * strandRadius);
            Vec3 top = new Vec3(
               center.x + Math.cos(angle + close * 0.55) * strandRadius * 0.92, upperY, center.z + Math.sin(angle + close * 0.55) * strandRadius * 0.92
            );
            ClientMagicCircleRenderer.line(buffer, matrix, bottom, top, 0.03 + radius * 0.004, i % 2 == 0 ? BLUE : WHITE, alpha);
         }
      }
   }

   private static float smooth(float t) {
      float clamped = Mth.clamp(t, 0.0F, 1.0F);
      return clamped * clamped * (3.0F - 2.0F * clamped);
   }
}
