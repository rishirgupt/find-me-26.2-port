package com.kuzhi.findme.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * One frame of world rendering, as the effect renderers need it: untextured, translucent, double-sided
 * coloured quads given in world coordinates.
 */
final class WorldRenderFrame {
   private final LevelRenderContext context;
   private final float partialTick;

   WorldRenderFrame(LevelRenderContext context) {
      this.context = context;
      this.partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
   }

   float partialTick() {
      return this.partialTick;
   }

   /**
    * Queues quads for this frame. {@code painter} receives the vertex buffer and a matrix that takes world
    * coordinates to camera space.
    */
   void drawQuads(BiConsumer<VertexConsumer, Matrix4f> painter) {
      Vec3 cameraPos = this.context.levelState().cameraRenderState.pos;
      PoseStack pose = this.context.poseStack();
      pose.pushPose();
      pose.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
      this.context
         .submitNodeCollector()
         .submitCustomGeometry(pose, RenderTypes.debugQuads(), (last, buffer) -> painter.accept(buffer, new Matrix4f(last.pose())));
      pose.popPose();
   }
}
