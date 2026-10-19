package com.kuzhi.findme.server;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

final class CompanionEntitySnapshots {
   private CompanionEntitySnapshots() {
   }

   static CompoundTag previewEntityTag(Entity entity, String entityType) {
      CompoundTag tag = new CompoundTag();
      com.kuzhi.findme.compat.EntityNbt.saveWithoutId(entity, tag);
      tag.putString("id", entityType);
      writePreviewBounds(entity, tag);
      tag.remove("UUID");
      tag.remove("Pos");
      tag.remove("Motion");
      tag.remove("Rotation");
      tag.remove("Passengers");
      tag.remove("Leash");
      tag.remove("NoAI");
      tag.remove("NoGravity");
      tag.remove("Invisible");
      return tag;
   }

   static void writePreviewBounds(Entity entity, CompoundTag tag) {
      CompanionEntityVisualBoundsService.VisualDimensions dimensions = CompanionEntityVisualBoundsService.previewDimensions(entity);
      tag.putFloat("CompanionPreviewWidth", (float)dimensions.width());
      tag.putFloat("CompanionPreviewHeight", (float)dimensions.height());
      tag.putFloat("CompanionPreviewDepth", (float)dimensions.depth());
   }

   static String storedEntityName(CompoundTag tag, UUID uuid) {
      String name;
      if (tag.contains("CompanionRescueName") && !(name = tag.getStringOr("CompanionRescueName", "")).isBlank()) {
         return name;
      }

      if (tag.contains("CustomName")) {
         String customName = tag.getStringOr("CustomName", "");
         if (!customName.isBlank()) {
            return customName;
         }
      }

      String id;
      if ((id = tag.getStringOr("id", "")).isBlank()) {
         return uuid.toString().substring(0, 8);
      }

      int separator = id.indexOf(58);
      return separator >= 0 && separator + 1 < id.length() ? id.substring(separator + 1) : id;
   }

   static String storedEntityType(CompoundTag tag) {
      String type;
      return tag.contains("CompanionRescueType") && !(type = tag.getStringOr("CompanionRescueType", "")).isBlank() ? type : tag.getStringOr("id", "");
   }
}
