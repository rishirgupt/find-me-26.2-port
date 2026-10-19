package com.kuzhi.findme.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

public record SavedPosition(ResourceKey<Level> dimension, double x, double y, double z, float yRot, float xRot) {
   public static SavedPosition of(Level level, double x, double y, double z, float yRot, float xRot) {
      return new SavedPosition(level.dimension(), x, y, z, yRot, xRot);
   }

   public BlockPos blockPos() {
      return BlockPos.containing(this.x, this.y, this.z);
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putString("dimension", this.dimension.identifier().toString());
      tag.putDouble("x", this.x);
      tag.putDouble("y", this.y);
      tag.putDouble("z", this.z);
      tag.putFloat("yRot", this.yRot);
      tag.putFloat("xRot", this.xRot);
      return tag;
   }

   public static SavedPosition load(CompoundTag tag) {
      ResourceKey dimension = ResourceKey.create(Registries.DIMENSION, Identifier.parse(tag.getStringOr("dimension", "")));
      return new SavedPosition(dimension, tag.getDoubleOr("x", 0.0), tag.getDoubleOr("y", 0.0), tag.getDoubleOr("z", 0.0), tag.getFloatOr("yRot", 0.0F), tag.getFloatOr("xRot", 0.0F));
   }
}
