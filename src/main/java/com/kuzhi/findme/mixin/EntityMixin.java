package com.kuzhi.findme.mixin;

import com.kuzhi.findme.compat.PersistentData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives every entity a data tag that is saved with it; Find me keeps the lists of each player there. */
@Mixin(Entity.class)
public abstract class EntityMixin implements PersistentData.Holder {
   @Unique
   private static final String FINDME_KEY = "FindMeData";
   @Unique
   private static final String FINDME_LEGACY_KEY = "NeoForgeData";
   @Unique
   private CompoundTag findme$data = new CompoundTag();

   @Override
   public CompoundTag findme$persistentData() {
      return this.findme$data;
   }

   @Inject(method = "saveWithoutId", at = @At("RETURN"))
   private void findme$save(ValueOutput output, CallbackInfo ci) {
      if (!this.findme$data.isEmpty()) {
         output.store(FINDME_KEY, CompoundTag.CODEC, this.findme$data.copy());
      }
   }

   @Inject(method = "load", at = @At("RETURN"))
   private void findme$load(ValueInput input, CallbackInfo ci) {
      this.findme$data = input.read(FINDME_KEY, CompoundTag.CODEC)
         .or(() -> input.read(FINDME_LEGACY_KEY, CompoundTag.CODEC))
         .map(CompoundTag::copy)
         .orElseGet(CompoundTag::new);
   }
}
