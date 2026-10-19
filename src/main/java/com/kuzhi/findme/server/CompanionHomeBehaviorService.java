package com.kuzhi.findme.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;

final class CompanionHomeBehaviorService {
   private static final Map<UUID, Boolean> PREVIOUS_ORDERED_SIT = new HashMap<>();

   private CompanionHomeBehaviorService() {
   }

   static void applyResidentBehavior(LivingEntity living) {
      setTemporarySit(living, true);
      living.fallDistance = 0.0F;
      if (living instanceof Mob mob) {
         mob.getNavigation().stop();
         mob.setTarget(null);
      }
   }

   static void clearResidentBehavior(LivingEntity living) {
      restoreTemporarySit(living);
   }

   private static void setTemporarySit(LivingEntity living, boolean sitting) {
      if (living instanceof TamableAnimal tamable) {
         UUID uuid = tamable.getUUID();
         PREVIOUS_ORDERED_SIT.putIfAbsent(uuid, tamable.isOrderedToSit());
         tamable.setOrderedToSit(sitting);
         tamable.setInSittingPose(sitting);
      }
   }

   private static void restoreTemporarySit(LivingEntity living) {
      if (living instanceof TamableAnimal tamable) {
         Boolean previous = PREVIOUS_ORDERED_SIT.remove(tamable.getUUID());
         if (previous != null) {
            tamable.setOrderedToSit(previous);
            tamable.setInSittingPose(previous);
         }
      }
   }
}
