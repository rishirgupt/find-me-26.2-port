package com.kuzhi.findme.client;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.network.CompanionListPacket;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

final class CompanionPreviewScaler {
   private CompanionPreviewScaler() {
   }

   static float wheelScale(CompanionListPacket.Entry entry, Entity entity) {
      Double override = override(entry);
      if (override != null) {
         return override.floatValue();
      }

      double width = Math.max(0.75, previewWidth(entry, entity));
      double height = Math.max(0.75, previewHeight(entry, entity));
      double depth = Math.max(0.75, previewDepth(entry, entity));
      width = Math.max(width, depth);
      double maxSize = Math.max(width * 1.45, height);
      double scale = 30.0 / Math.max(0.75, maxSize);
      return (float)Math.max(0.18, Math.min(12.0, scale));
   }

   static float detailScale(CompanionListPacket.Entry entry, Entity entity, float boxSize, float maxScale) {
      Double override = override(entry);
      if (override != null) {
         return override.floatValue();
      }

      double width = Math.max(0.75, previewWidth(entry, entity));
      double height = Math.max(0.75, previewHeight(entry, entity));
      double depth = Math.max(0.75, previewDepth(entry, entity));
      double maxSize = Math.max(Math.max(width, depth) * 1.15, height);
      double scale = boxSize / Math.max(0.75, maxSize);
      return (float)Math.max(0.12, Math.min(maxScale, scale));
   }

   private static double previewWidth(CompanionListPacket.Entry entry, Entity entity) {
      return previewDimension(entry, "CompanionPreviewWidth", entity.getBbWidth(), iceAndFireDragonMinimum(entry, 7.5));
   }

   private static double previewHeight(CompanionListPacket.Entry entry, Entity entity) {
      return previewDimension(entry, "CompanionPreviewHeight", entity.getBbHeight(), iceAndFireDragonMinimum(entry, 5.5));
   }

   private static double previewDepth(CompanionListPacket.Entry entry, Entity entity) {
      return previewDimension(entry, "CompanionPreviewDepth", entity.getBbWidth(), iceAndFireDragonMinimum(entry, 7.5));
   }

   private static double previewDimension(CompanionListPacket.Entry entry, String key, double fallback, double minimum) {
      CompoundTag tag = entry.previewTag();
      if (tag != null && tag.contains(key)) {
         float value = tag.getFloatOr(key, 0.0F);
         if (Float.isFinite(value) && value > 0.0F) {
            return Math.max(value, minimum);
         }
      }

      return Math.max(fallback, minimum);
   }

   private static double iceAndFireDragonMinimum(CompanionListPacket.Entry entry, double minimum) {
      String key = entry.entityType() == null ? "" : entry.entityType().toLowerCase(Locale.ROOT);
      return !key.equals("iceandfire:fire_dragon") && !key.equals("iceandfire:ice_dragon") && !key.equals("iceandfire:lightning_dragon") ? 0.0 : minimum;
   }

   private static Double override(CompanionListPacket.Entry entry) {
      String key = entry.entityType() == null ? "" : entry.entityType().toLowerCase(Locale.ROOT);
      return Config.previewScaleOverrides.get(key);
   }
}
