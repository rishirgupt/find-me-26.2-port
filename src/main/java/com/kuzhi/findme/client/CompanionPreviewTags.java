package com.kuzhi.findme.client;

import java.util.Locale;
import net.minecraft.nbt.CompoundTag;

final class CompanionPreviewTags {
   private CompanionPreviewTags() {
   }

   static CompoundTag sanitized(CompoundTag source, String entityType) {
      if (entityType != null && entityType.toLowerCase(Locale.ROOT).startsWith("saintsdragons:")) {
         CompoundTag clean = new CompoundTag();
         clean.putString("id", entityType);
         copyPreviewMetadata(source, clean);
         return clean;
      }

      CompoundTag tag = source.copy();
      if (!tag.contains("id") && entityType != null && !entityType.isBlank()) {
         tag.putString("id", entityType);
      }

      tag.remove("UUID");
      tag.remove("Pos");
      tag.remove("Motion");
      tag.remove("Rotation");
      tag.remove("Passengers");
      tag.remove("Leash");
      tag.remove("Vehicle");
      return tag;
   }

   private static void copyPreviewMetadata(CompoundTag source, CompoundTag target) {
      for (String key : new String[]{
         "CompanionPreviewWidth",
         "CompanionPreviewHeight",
         "CompanionPreviewDepth",
         "CompanionRescueHealth",
         "CompanionRescueMaxHealth",
         "CompanionRescueArmor"
      }) {
         if (source.contains(key)) {
            target.put(key, source.get(key).copy());
         }
      }
   }
}
