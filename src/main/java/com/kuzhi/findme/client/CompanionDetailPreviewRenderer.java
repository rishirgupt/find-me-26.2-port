package com.kuzhi.findme.client;

import com.kuzhi.findme.network.CompanionListPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;

final class CompanionDetailPreviewRenderer {
   private static final Map<UUID, Entity> PREVIEW_ENTITIES = new HashMap<>();
   private static final Map<UUID, String> PREVIEW_KEYS = new HashMap<>();
   private static final ArrayDeque<CompanionDetailPreviewRenderer.WarmEntry> WARM_QUEUE = new ArrayDeque<>();
   private static final Map<UUID, String> PENDING_KEYS = new HashMap<>();

   void render(
      GuiGraphicsExtractor graphics,
      CompanionListPacket.Entry entry,
      int x,
      int y,
      int w,
      int h,
      float yaw,
      float pitch,
      float zoom,
      float cameraOffsetX,
      float cameraOffsetY,
      String fallbackType
   ) {
      this.render(graphics, entry, x, y, w, h, yaw, pitch, zoom, cameraOffsetX, cameraOffsetY, fallbackType, 78.0F, 28.0F);
   }

   void render(
      GuiGraphicsExtractor graphics,
      CompanionListPacket.Entry entry,
      int x,
      int y,
      int w,
      int h,
      float yaw,
      float pitch,
      float zoom,
      float cameraOffsetX,
      float cameraOffsetY,
      String fallbackType,
      float baseSize,
      float padding
   ) {
      if (!SableVehicleScreenshotPreviewCache.render(graphics, entry.uuid(), x + 2, y + 2, w - 4, h - 4)) {
         Entity entity = this.previewEntity(entry, fallbackType);
         if (entity != null) {
            stabilizePreviewPose(entity, yaw, pitch);
            float scale = CompanionPreviewScaler.detailScale(entry, entity, baseSize, padding) * zoom;
            graphics.enableScissor(x + 2, y + 2, x + w - 2, y + h - 2);
            renderPreviewEntity(graphics, entity, x + w / 2 + Math.round(cameraOffsetX), y + h - 12 + Math.round(cameraOffsetY), scale, yaw, pitch);
            graphics.disableScissor();
         }
      }
   }

   void renderWheel(GuiGraphicsExtractor graphics, CompanionListPacket.Entry entry, int x, int y, String fallbackType) {
      if (!SableVehicleScreenshotPreviewCache.render(graphics, entry.uuid(), x - 20, y - 13, 40, 26)) {
         Entity entity = this.previewEntity(entry, fallbackType, false);
         if (entity == null) {
            enqueueWarm(entry, fallbackType);
         } else {
            stabilizePreviewPose(entity, 180.0F, 0.0F);
            float scale = CompanionPreviewScaler.wheelScale(entry, entity);
            graphics.enableScissor(x - 23, y - 14, x + 23, y + 13);
            renderPreviewEntity(graphics, entity, x, y + 14, scale, 180.0F, 0.0F);
            graphics.disableScissor();
         }
      }
   }

   private Entity previewEntity(CompanionListPacket.Entry entry, String fallbackType) {
      return this.previewEntity(entry, fallbackType, true);
   }

   private Entity previewEntity(CompanionListPacket.Entry entry, String fallbackType, boolean createIfMissing) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.level == null) {
         return null;
      }

      if (hasNoPreview(entry)) {
         PREVIEW_ENTITIES.remove(entry.uuid());
         PREVIEW_KEYS.remove(entry.uuid());
         PENDING_KEYS.remove(entry.uuid());
         return null;
      }

      String key = previewKey(entry);
      Entity cached = PREVIEW_ENTITIES.get(entry.uuid());
      if (cached != null && key.equals(PREVIEW_KEYS.get(entry.uuid()))) {
         return cached;
      }

      if (!createIfMissing) {
         return null;
      }

      Entity created = null;
      CompoundTag renderTag = entry.previewTag();
      if (renderTag != null && !renderTag.isEmpty()) {
         CompoundTag tag = CompanionPreviewTags.sanitized(renderTag, entry.entityType());
         Entity loaded = EntityType.loadEntityRecursive(tag, minecraft.level, new net.minecraft.world.entity.EntitySpawnRequest(net.minecraft.world.entity.EntitySpawnReason.LOAD, false), entity -> {
            entity.snapTo(0.0, 0.0, 0.0, 0.0F, 0.0F);
            return entity;
         });
         created = loaded;
      }

      if (created == null) {
         String type = !entry.entityType().isBlank() ? entry.entityType() : fallbackType;
         created = java.util.Optional.ofNullable(net.minecraft.resources.Identifier.tryParse(type)).flatMap(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE::getOptional).map(entityType -> (Entity)entityType.create(minecraft.level, net.minecraft.world.entity.EntitySpawnReason.LOAD)).orElse(null);
      }

      if (created != null) {
         // Entities that were never added to a level have no id yet, and renderers need one.
         created.setId(nextPreviewId--);
         PREVIEW_ENTITIES.put(entry.uuid(), created);
         PREVIEW_KEYS.put(entry.uuid(), key);
         PENDING_KEYS.remove(entry.uuid());
      } else {
         PREVIEW_ENTITIES.remove(entry.uuid());
         PREVIEW_KEYS.remove(entry.uuid());
         PENDING_KEYS.remove(entry.uuid());
      }

      return created;
   }

   static void enqueueWarm(List<CompanionListPacket.Entry> entries, String fallbackType) {
      for (CompanionListPacket.Entry entry : entries) {
         enqueueWarm(entry, fallbackType);
      }
   }

   static void enqueueWarm(CompanionListPacket.Entry entry, String fallbackType) {
      if (hasNoPreview(entry)) {
         PREVIEW_ENTITIES.remove(entry.uuid());
         PREVIEW_KEYS.remove(entry.uuid());
         PENDING_KEYS.remove(entry.uuid());
      } else {
         String key = previewKey(entry);
         if (!key.equals(PREVIEW_KEYS.get(entry.uuid())) && !key.equals(PENDING_KEYS.get(entry.uuid()))) {
            PENDING_KEYS.put(entry.uuid(), key);
            WARM_QUEUE.addLast(new CompanionDetailPreviewRenderer.WarmEntry(entry, fallbackType));
         }
      }
   }

   static void tickWarmCache() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.level == null) {
         WARM_QUEUE.clear();
         PENDING_KEYS.clear();
      } else if (!WARM_QUEUE.isEmpty()) {
         CompanionDetailPreviewRenderer warmer = new CompanionDetailPreviewRenderer();
         int budget = minecraft.gui.screen() instanceof CompanionWheelScreen ? 3 : 1;

         while (budget-- > 0 && !WARM_QUEUE.isEmpty()) {
            CompanionDetailPreviewRenderer.WarmEntry warmEntry = WARM_QUEUE.removeFirst();
            String key = previewKey(warmEntry.entry);
            if (key.equals(PREVIEW_KEYS.get(warmEntry.entry.uuid()))) {
               PENDING_KEYS.remove(warmEntry.entry.uuid());
            } else {
               warmer.previewEntity(warmEntry.entry, warmEntry.fallbackType, true);
            }
         }
      }
   }

   private static int nextPreviewId = -1000;
   private static final java.util.Set<String> PREVIEW_FAILURES = new java.util.HashSet<>();

   static void renderPreviewEntity(GuiGraphicsExtractor graphics, Entity entity, int x, int bottomY, float scale, float yaw, float pitch) {
      Minecraft minecraft = Minecraft.getInstance();
      EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
      net.minecraft.client.renderer.entity.state.EntityRenderState renderState;
      try {
         renderState = dispatcher.getRenderer(entity).createRenderState(entity, 1.0F);
      } catch (RuntimeException exception) {
         if (PREVIEW_FAILURES.add(entity.getType().toString())) {
            com.kuzhi.findme.FindMe.LOGGER.warn("FindMe could not prepare a preview of {}", entity.getType(), exception);
         }

         return;
      }

      renderState.shadowPieces.clear();
      renderState.outlineColor = 0;
      Quaternionf pitchRotation = Axis.XP.rotationDegrees(pitch);
      Quaternionf rotation = Axis.ZP.rotationDegrees(180.0F).mul(pitchRotation);
      // The entity is drawn into a square whose bottom edge is the line it stands on.
      float extent = Math.max(entity.getBbWidth(), entity.getBbHeight()) * scale;
      int half = Math.max(24, Math.round(extent * 0.75F) + 8);
      org.joml.Vector3f translation = new org.joml.Vector3f(0.0F, half / Math.max(scale, 0.001F), 0.0F);
      graphics.entity(renderState, scale, translation, rotation, pitchRotation, x - half, bottomY - half * 2, x + half, bottomY);
   }

   static void stabilizePreviewPose(Entity entity, float yaw, float pitch) {
      entity.tickCount = 1;
      if (entity instanceof LivingEntity living) {
         living.walkAnimation.setSpeed(0.0F);
         living.walkAnimation.update(0.0F, 1.0F, 1.0F);
         living.hurtTime = 0;
         living.deathTime = 0;
         living.swingTime = 0;
         living.attackAnim = 0.0F;
         living.oAttackAnim = 0.0F;
         living.yBodyRot = yaw;
         living.yBodyRotO = yaw;
         living.yHeadRot = yaw;
         living.yHeadRotO = yaw;
      }

      entity.setYRot(yaw);
      entity.setXRot(pitch);
      entity.xRotO = pitch;
      entity.yRotO = yaw;
   }

   private static String previewKey(CompanionListPacket.Entry entry) {
      CompoundTag tag = entry.previewTag();
      if (hasNoPreview(entry)) {
         return "no_preview";
      } else {
         return tag == null ? entry.entityType() + ":0" : entry.entityType() + ":" + tag.hashCode();
      }
   }

   private static boolean hasNoPreview(CompanionListPacket.Entry entry) {
      return entry != null && entry.previewTag() == null && entry.entityType().isBlank();
   }

   private record WarmEntry(CompanionListPacket.Entry entry, String fallbackType) {
   }
}
