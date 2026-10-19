package com.kuzhi.findme.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

final class ClientStorageEffectRenderer {
   private static final ClientMagicCircleRenderer.Color BLUE = new ClientMagicCircleRenderer.Color(0.54F, 0.88F, 1.0F);
   private static final ClientMagicCircleRenderer.Color WHITE = new ClientMagicCircleRenderer.Color(0.96F, 1.0F, 1.0F);

   private ClientStorageEffectRenderer() {
   }

   static void renderWorld(WorldRenderFrame event) {
      if (!ClientStorageEffectState.effects().isEmpty()) {
         event.drawQuads((buffer, matrix) -> {

         for (ClientStorageEffectState.Effect effect : ClientStorageEffectState.effects()) {
            renderEffect(buffer, matrix, effect, event.partialTick());
         }
         });
      }
   }

   private static void renderEffect(VertexConsumer buffer, Matrix4f matrix, ClientStorageEffectState.Effect effect, float partialTick) {
      float age = effect.age + partialTick;
      float progress = Mth.clamp(age / effect.durationTicks, 0.0F, 1.0F);
      float open = smooth(Mth.clamp(progress / 0.24F, 0.0F, 1.0F));
      float wrap = wrapProgress(progress);
      float fade = 1.0F - smooth(Mth.clamp((progress - 0.78F) / 0.22F, 0.0F, 1.0F));
      if (!(fade <= 0.01F)) {
         ClientStorageEffectRenderer.Bounds bounds = bounds(effect);
         double radius = Math.max(0.9, bounds.radius) * (0.18 + 0.82 * open);
         float alpha = 0.62F * open * fade;
         ClientMagicCircleRenderer.renderGroundCircle(buffer, matrix, bounds.base, radius, BLUE, alpha, progress, age * 0.86F + wobble(age) * 2.5F, 0.36);
         renderWrap(buffer, matrix, bounds, radius, wrap, fade, age);
         renderClosingRings(buffer, matrix, bounds, radius, wrap, fade, age);
      }
   }

   private static void renderWrap(
      VertexConsumer buffer, Matrix4f matrix, ClientStorageEffectRenderer.Bounds bounds, double radius, float wrap, float fade, float age
   ) {
      if (!(wrap <= 0.01F)) {
         int strands = 5;
         int segments = 72;
         double height = bounds.height;
         double maxT = Mth.clamp(wrap, 0.0F, 1.0F);

         for (int strand = 0; strand < strands; strand++) {
            double phase = strand * Math.PI * 2.0 / strands;
            double speed = 0.052 + strand * 0.007;
            double spin = age * speed + Math.sin(age * 0.055 + strand) * 0.42;
            Vec3 previous = null;

            for (int i = 0; i <= segments; i++) {
               double t = maxT * i / segments;
               double easedT = smooth((float)t);
               double localRadius = radius * (0.98 - 0.34 * easedT + Math.sin(age * 0.1 + i * 0.35 + strand) * 0.018);
               double angle = phase + spin + easedT * Math.PI * 5.1 + Math.sin(easedT * Math.PI * 2.0 + age * 0.045) * 0.24;
               double y = bounds.base.y + 0.18 + height * easedT;
               Vec3 point = new Vec3(bounds.base.x + Math.cos(angle) * localRadius, y, bounds.base.z + Math.sin(angle) * localRadius);
               if (previous != null) {
                  float segmentAlpha = fade * (0.18F + 0.54F * (float)easedT) * (1.0F - Math.max(0.0F, (float)t - 0.86F) * 2.2F);
                  ClientMagicCircleRenderer.line(buffer, matrix, previous, point, 0.035 + radius * 0.01, strand % 2 == 0 ? WHITE : BLUE, segmentAlpha);
               }

               previous = point;
            }
         }
      }
   }

   private static void renderClosingRings(
      VertexConsumer buffer, Matrix4f matrix, ClientStorageEffectRenderer.Bounds bounds, double radius, float wrap, float fade, float age
   ) {
      if (!(wrap <= 0.04F)) {
         double top = bounds.base.y + 0.18 + bounds.height * smooth(wrap);
         double pulse = Math.sin(age * 0.23) * 0.04;
         double bandRadius = radius * (0.94 - 0.46 * smooth(wrap) + pulse);
         float alpha = fade * (0.22F + 0.45F * wrap);
         ClientMagicCircleRenderer.ring(buffer, matrix, bounds.base, top, Math.max(0.16, bandRadius), 72, age * -0.035, 0.04 + radius * 0.006, WHITE, alpha);
         if (wrap > 0.68F) {
            double lowRadius = radius * (1.02 - 0.72 * smooth((wrap - 0.68F) / 0.32F));
            ClientMagicCircleRenderer.ring(
               buffer, matrix, bounds.base, bounds.base.y + 0.24, Math.max(0.12, lowRadius), 72, age * 0.055, 0.032 + radius * 0.005, BLUE, alpha * 0.72F
            );
         }
      }
   }

   private static ClientStorageEffectRenderer.Bounds bounds(ClientStorageEffectState.Effect effect) {
      Entity entity = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(effect.entityId);
      if (entity != null) {
         AABB box = entity.getBoundingBox();
         double radius = Math.max(effect.radius, Math.max(box.getXsize(), box.getZsize()) * 0.62 + 0.45);
         double height = Math.max(effect.height, box.getYsize());
         return new ClientStorageEffectRenderer.Bounds(new Vec3(box.getCenter().x, box.minY, box.getCenter().z), radius, height);
      } else {
         return new ClientStorageEffectRenderer.Bounds(effect.fallbackBase, effect.radius, effect.height);
      }
   }

   private static float wrapProgress(float progress) {
      float staged = Mth.clamp((progress - 0.1F) / 0.7F, 0.0F, 1.0F);
      float accelerated = staged < 0.55F ? 0.55F * smooth(staged / 0.55F) * 0.62F : 0.34F + 0.66F * smooth((staged - 0.55F) / 0.45F);
      return Mth.clamp(accelerated, 0.0F, 1.0F);
   }

   private static float smooth(float t) {
      float clamped = Mth.clamp(t, 0.0F, 1.0F);
      return clamped * clamped * (3.0F - 2.0F * clamped);
   }

   private static float wobble(float age) {
      return (float)Math.sin(age * 0.11F) + (float)Math.sin(age * 0.041F) * 0.55F;
   }

   private record Bounds(Vec3 base, double radius, double height) {
   }
}
