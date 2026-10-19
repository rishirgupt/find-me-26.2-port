package com.kuzhi.findme.client;

import com.kuzhi.findme.network.RescueMagicPacket;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

final class ClientRescueMagicRenderer {
   private static final ClientMagicCircleRenderer.Color BLUE = new ClientMagicCircleRenderer.Color(0.58F, 0.92F, 1.0F);
   private static final ClientMagicCircleRenderer.Color WHITE = new ClientMagicCircleRenderer.Color(0.96F, 1.0F, 1.0F);

   private ClientRescueMagicRenderer() {
   }

   static void renderWorld(WorldRenderFrame event) {
      if (!ClientRescueMagicRenderState.effects().isEmpty()) {
         event.drawQuads((buffer, matrix) -> {

         for (ClientRescueMagicRenderState.Effect effect : ClientRescueMagicRenderState.effects()) {
            renderEffect(buffer, matrix, effect, event.partialTick());
         }
         });
      }
   }

   private static void renderEffect(VertexConsumer buffer, Matrix4f matrix, ClientRescueMagicRenderState.Effect effect, float partialTick) {
      float age = effect.age + partialTick;
      float progress = Mth.clamp(age / effect.durationTicks, 0.0F, 1.0F);
      boolean rescue = effect.purpose == RescueMagicPacket.Purpose.RESCUE;
      float open = smooth(Mth.clamp(progress / (rescue ? 0.22F : 0.42F), 0.0F, 1.0F));
      float fade = 1.0F - smooth(Mth.clamp((progress - (rescue ? 0.72F : 0.82F)) / (rescue ? 0.18F : 0.18F), 0.0F, 1.0F));
      float alpha = open * fade * (rescue ? 0.92F : 0.66F);
      if (!(alpha <= 0.01F)) {
         if (effect.style == RescueMagicPacket.Style.GROUND_CIRCLE) {
            renderGroundEffect(buffer, matrix, effect, progress, open, alpha, age);
         } else {
            renderVerticalEffect(buffer, matrix, effect, progress, open, alpha, age);
         }
      }
   }

   private static void renderVerticalEffect(
      VertexConsumer buffer, Matrix4f matrix, ClientRescueMagicRenderState.Effect effect, float progress, float open, float alpha, float age
   ) {
      boolean rescue = effect.purpose == RescueMagicPacket.Purpose.RESCUE;
      double baseRadius = Math.max(1.5, Math.max(effect.radius, effect.height * 0.52F)) * (rescue ? 1.12 : 1.0);
      double growth = (rescue ? 0.04 : 0.1) + (rescue ? 0.96 : 0.9) * open;
      double radius = baseRadius * growth;
      Vec3 center = effect.center.add(0.0, Math.max(effect.height * 0.52F, Math.min(radius * 0.62, effect.radius * 0.8)) + Math.sin(age * 0.11) * 0.025, 0.0);
      float renderAge = rescue ? age * 1.38F + (float)Math.sin(age * 0.17F) * 2.0F : age * 0.82F + (float)Math.sin(age * 0.07F) * 0.7F;
      ClientMagicCircleRenderer.renderVerticalPortal(
         buffer, matrix, center, radius, effect.yaw, BLUE, WHITE, (rescue ? 0.82F : 0.56F) * alpha, progress, renderAge
      );
      float flash = smooth(Mth.clamp(1.0F - Math.abs(progress - (rescue ? 0.24F : 0.38F)) / (rescue ? 0.11F : 0.14F), 0.0F, 1.0F));
      if (flash > 0.02F) {
         ClientMagicCircleRenderer.renderVerticalPortal(
            buffer,
            matrix,
            center,
            radius * (1.0 + flash * (rescue ? 0.11 : 0.04)),
            effect.yaw,
            WHITE,
            BLUE,
            flash * (rescue ? 0.42F : 0.18F),
            progress,
            renderAge + 9.0F
         );
      }
   }

   private static void renderGroundEffect(
      VertexConsumer buffer, Matrix4f matrix, ClientRescueMagicRenderState.Effect effect, float progress, float open, float alpha, float age
   ) {
      boolean rescue = effect.purpose == RescueMagicPacket.Purpose.RESCUE;
      double baseRadius = Math.max(1.35, effect.radius * (rescue ? 0.98 : 0.9));
      double radius = baseRadius * ((rescue ? 0.06 : 0.12) + (rescue ? 0.94 : 0.88) * open);
      float renderAge = rescue ? age * 1.45F + (float)Math.sin(age * 0.18F) * 2.0F : age * 0.78F + (float)Math.sin(age * 0.06F) * 0.6F;
      ClientMagicCircleRenderer.renderGroundCircle(
         buffer, matrix, effect.center, radius, BLUE, (rescue ? 0.7F : 0.44F) * alpha, progress, renderAge, rescue ? 0.44 : 0.22
      );
      if (!rescue) {
         float detailOpen = smooth(Mth.clamp((progress - 0.32F) / 0.16F, 0.0F, 1.0F));
         if (detailOpen > 0.01F) {
            ClientMagicCircleRenderer.renderGroundCircle(
               buffer, matrix, effect.center, radius * 0.985, WHITE, 0.15F * alpha * detailOpen, progress, renderAge + 4.0F, 0.58
            );
         }
      } else {
         float flash = smooth(Mth.clamp(1.0F - Math.abs(progress - (rescue ? 0.22F : 0.4F)) / (rescue ? 0.11F : 0.16F), 0.0F, 1.0F));
         if (flash > 0.02F) {
            ClientMagicCircleRenderer.renderGroundCircle(
               buffer,
               matrix,
               effect.center,
               radius * (1.0 + flash * (rescue ? 0.22 : 0.1)),
               WHITE,
               flash * (rescue ? 0.4F : 0.16F),
               progress,
               renderAge + 7.0F,
               rescue ? 0.72 : 0.58
            );
         }
      }
   }

   private static float smooth(float t) {
      float clamped = Mth.clamp(t, 0.0F, 1.0F);
      return clamped * clamped * (3.0F - 2.0F * clamped);
   }
}
