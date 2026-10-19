package com.kuzhi.findme.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * Extra data kept on an entity and saved with it (what NeoForge calls persistent data). The storage itself is
 * added to {@link Entity} by a mixin.
 */
public final class PersistentData {
   private PersistentData() {
   }

   public static CompoundTag of(Entity entity) {
      return ((Holder)entity).findme$persistentData();
   }

   public interface Holder {
      CompoundTag findme$persistentData();
   }
}
