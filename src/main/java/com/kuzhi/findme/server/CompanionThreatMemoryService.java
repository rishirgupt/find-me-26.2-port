package com.kuzhi.findme.server;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

final class CompanionThreatMemoryService {
   private static final int MEMORY_TICKS = 120;
   private static final Map<UUID, CompanionThreatMemoryService.ThreatMemory> MEMORIES = new HashMap<>();

   private CompanionThreatMemoryService() {
   }

   static void remember(LivingEntity victim, net.minecraft.world.damagesource.DamageSource source) {
      if (victim instanceof ServerPlayer player) {
         LivingEntity attacker = sourceAttacker(source);
         if (attacker != null && attacker != player && !(attacker instanceof Player) && !CompanionManager.isOwnedBy(player, attacker)) {
            MEMORIES.put(
               player.getUUID(),
               new CompanionThreatMemoryService.ThreatMemory(attacker.getUUID(), attacker.level().dimension(), player.level().getGameTime() + 120L)
            );
         }
      }
   }

   static Optional<LivingEntity> findRecentThreat(ServerPlayer player, double range) {
      CompanionThreatMemoryService.ThreatMemory memory = MEMORIES.get(player.getUUID());
      if (memory == null) {
         return Optional.empty();
      } else {
         long now = player.level().getGameTime();
         if (now <= memory.expiresAt() && memory.dimension().equals(player.level().dimension())) {
            Entity entity = CompanionManager.findEntity(player.level().getServer(), memory.threatUuid()).orElse(null);
            return entity instanceof LivingEntity living
                  && living.level() == player.level()
                  && living.isAlive()
                  && !(living.distanceToSqr(player) > range * range)
               ? Optional.of(living)
               : Optional.empty();
         } else {
            MEMORIES.remove(player.getUUID());
            return Optional.empty();
         }
      }
   }

   private static LivingEntity sourceAttacker(DamageSource source) {
      if (source == null) {
         return null;
      } else if (source.getEntity() instanceof LivingEntity living) {
         return living;
      } else {
         return source.getDirectEntity() instanceof LivingEntity living ? living : null;
      }
   }

   private record ThreatMemory(UUID threatUuid, ResourceKey<Level> dimension, long expiresAt) {
   }
}
