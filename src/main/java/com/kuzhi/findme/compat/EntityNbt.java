package com.kuzhi.findme.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.TagValueOutput;

/** Entity snapshots as plain NBT, the form the mod stores them in. */
public final class EntityNbt {
   private EntityNbt() {
   }

   /** Writes the entity with its type id, as {@code Entity.save(CompoundTag)} did; false when it cannot be saved. */
   public static boolean save(Entity entity, CompoundTag tag) {
      TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
      if (!entity.save(output)) {
         return false;
      }

      tag.merge(output.buildResult());
      return true;
   }

   /** Writes the entity (without its type id) into {@code tag}, as {@code Entity.saveWithoutId(CompoundTag)} did. */
   public static CompoundTag saveWithoutId(Entity entity, CompoundTag tag) {
      TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
      entity.saveWithoutId(output);
      tag.merge(output.buildResult());
      return tag;
   }
}
