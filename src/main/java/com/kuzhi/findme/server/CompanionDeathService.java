package com.kuzhi.findme.server;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.SavedPosition;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

final class CompanionDeathService {
   private CompanionDeathService() {
   }

   static boolean markRegisteredDead(MinecraftServer server, LivingEntity living) {
      return markRegisteredDead(server, living, false);
   }

   static boolean markRegisteredDead(MinecraftServer server, LivingEntity living, boolean confirmedDeath) {
      if (server != null && !living.level().isClientSide()) {
         if (!confirmedDeath && living.getHealth() > 0.0F) {
            return false;
         }

         UUID uuid = living.getUUID();
         boolean changed = false;

         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerCompanionData data = CompanionManager.data(player);
            Optional<CompanionKind> maybeKind = data.kindOf(uuid);
            if (!maybeKind.isEmpty() && !data.deadList().contains(uuid)) {
               CompanionKind kind = maybeKind.get();
               if (kind != CompanionKind.COMPANION || !CompanionManager.shoulderEntityTag(player, uuid).isPresent()) {
                  CompanionManager.clearHomeResident(living);
                  String entityType = EntityType.getKey(living.getType()).toString();
                  CompoundTag tag = CompanionManager.previewEntityTag(living, entityType);
                  float maxHealth = living.getMaxHealth();
                  float previewHealth = Math.max(
                     1.0F, Math.min(maxHealth <= 0.0F ? 1.0F : maxHealth, living.getHealth() <= 0.0F ? maxHealth : living.getHealth())
                  );
                  tag.putString("id", entityType);
                  tag.putString("CompanionRescueName", living.getDisplayName().getString());
                  tag.putString("CompanionRescueType", entityType);
                  tag.putFloat("CompanionRescueHealth", previewHealth);
                  tag.putFloat("CompanionRescueMaxHealth", Math.max(1.0F, maxHealth));
                  tag.putInt("CompanionRescueArmor", living.getArmorValue());
                  tag.putBoolean("CompanionRescueDead", true);
                  tag.putFloat("Health", previewHealth);
                  data.storeEntity(uuid, tag);
                  data.setLastKnownPosition(
                     uuid, SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(), living.getYRot(), living.getXRot())
                  );
                  data.markDead(uuid, kind);
                  CompanionManager.save(player, data);
                  CompanionManager.syncToClient(player, CompanionKind.MOUNT);
                  CompanionManager.syncToClient(player, CompanionKind.COMPANION);
                  CompanionManager.syncDeadToClient(player);
                  changed = true;
               }
            }
         }

         return changed;
      } else {
         return false;
      }
   }

   static boolean shouldSuppressDeathDrops(LivingEntity living) {
      if (living != null && !living.level().isClientSide()) {
         MinecraftServer server = living.level().getServer();
         if (server == null) {
            return false;
         }

         UUID uuid = living.getUUID();

         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerCompanionData data = CompanionManager.data(player);
            if (data.kindOf(uuid).isPresent()) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }
}
