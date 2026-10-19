package com.kuzhi.findme.compat;

import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;

/** Small entity queries whose game API changed between the version the mod was written for and this one. */
public final class EntityCompat {
   private EntityCompat() {
   }

   /** The owner's UUID, or null when the entity has no owner. */
   public static UUID ownerUuid(OwnableEntity ownable) {
      EntityReference<LivingEntity> owner = ownable == null ? null : ownable.getOwnerReference();
      return owner == null ? null : owner.getUUID();
   }

   /** Mobs that fly as their normal way of moving (what used to be the FlyingMob base class, and its relatives). */
   public static boolean isFlyingMob(Entity entity) {
      if (!(entity instanceof Mob mob)) {
         return false;
      } else if (mob.getMoveControl() instanceof FlyingMoveControl) {
         return true;
      } else {
         String name = mob.getClass().getSimpleName();
         return name.equals("Ghast") || name.equals("Phantom") || name.equals("HappyGhast");
      }
   }

   /** A vanilla sound by its id path, or null when this version has no such sound. */
   public static SoundEvent vanillaSound(String path) {
      return BuiltInRegistries.SOUND_EVENT.getValue(Identifier.withDefaultNamespace(path));
   }
}
