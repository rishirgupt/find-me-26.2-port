package com.kuzhi.findme.server;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.SavedPosition;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.kuzhi.findme.compat.Parts;

final class CompanionRegistrationService {
   private static final Map<UUID, UUID> LAST_RIDDEN_MOUNTS = new HashMap<>();

   private CompanionRegistrationService() {
   }

   static void autoRegisterRiddenMount(ServerPlayer player) {
      if (Config.enableAutoBinding) {
         Entity vehicle = player.getVehicle();
         LivingEntity livingVehicle;
         UUID currentRidden = vehicle instanceof LivingEntity && (livingVehicle = (LivingEntity)vehicle) != player ? livingVehicle.getUUID() : null;
         UUID previousRidden = LAST_RIDDEN_MOUNTS.put(player.getUUID(), currentRidden);
         if (previousRidden == null && currentRidden != null || previousRidden != null && !previousRidden.equals(currentRidden)) {
            CompanionManager.syncToClient(player, CompanionKind.MOUNT);
         }

         if (vehicle instanceof LivingEntity living && living != player) {
            PlayerCompanionData data = CompanionManager.data(player);
            if (data.contains(CompanionKind.COMPANION, living.getUUID())) {
               promoteRiddenCompanionToMount(player, data, living);
            } else if (!data.contains(CompanionKind.MOUNT, living.getUUID())
               && !data.isAutoBindIgnored(living.getUUID())
               && !isTypeBlacklisted(data, living)
               && canAutoRegisterRiddenMount(player, living)) {
               data.add(CompanionKind.MOUNT, living.getUUID());
               SavedPosition position = SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(), living.getYRot(), living.getXRot());
               data.setOrigin(living.getUUID(), position);
               data.setLastKnownPosition(living.getUUID(), position);
               CompanionDeploymentService.rememberDeployedWithinLimit(player, data, CompanionKind.MOUNT, living.getUUID());
               data.markMountEligible(living.getUUID());
               CompanionManager.save(player, data);
               CompanionManager.syncToClient(player, CompanionKind.MOUNT);
               CompanionSummonLineService.showRegistered(player, living);
            }
         }
      }
   }

   static void promoteRiddenRegisteredCompanionToMount(ServerPlayer player) {
      if (player.getVehicle() instanceof LivingEntity living && living != player) {
         PlayerCompanionData data = CompanionManager.data(player);
         if (data.contains(CompanionKind.COMPANION, living.getUUID()) && !data.contains(CompanionKind.MOUNT, living.getUUID())) {
            promoteRiddenCompanionToMount(player, data, living);
         }
      }
   }

   static void autoRegisterNearbyOwnedEntities(ServerPlayer player) {
      if (Config.enableAutoBinding) {
         if (player.tickCount % 40 == 0) {
            PlayerCompanionData data = CompanionManager.data(player);
            AABB area = player.getBoundingBox().inflate(48.0, 24.0, 48.0);
            boolean changed = false;

            for (LivingEntity living : player.level().getEntitiesOfClass(LivingEntity.class, area, entity -> entity != player && entity.isAlive())) {
               if (!data.contains(living.getUUID())
                  && CompanionManager.isOwnedBy(player, living)
                  && !isSittingHomePet(living)
                  && !data.isAutoBindIgnored(living.getUUID())
                  && !isTypeBlacklisted(data, living)
                  && data.add(CompanionKind.COMPANION, living.getUUID())) {
                  SavedPosition position = SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(), living.getYRot(), living.getXRot());
                  data.setOrigin(living.getUUID(), position);
                  data.setLastKnownPosition(living.getUUID(), position);
                  CompanionDeploymentService.rememberDeployedWithinLimit(player, data, CompanionKind.COMPANION, living.getUUID());
                  changed = true;
               }
            }

            if (changed) {
               CompanionManager.save(player, data);
               CompanionManager.syncToClient(player, CompanionKind.MOUNT);
               CompanionManager.syncToClient(player, CompanionKind.COMPANION);
            }
         }
      }
   }

   static void autoRegisterTamedCompanion(ServerPlayer player, LivingEntity living) {
      if (Config.enableAutoBinding) {
         if (living != player && living.isAlive()) {
            PlayerCompanionData data = CompanionManager.data(player);
            UUID uuid = living.getUUID();
            if (!data.isAutoBindIgnored(uuid) && !isTypeBlacklisted(data, living)) {
               if (!data.contains(CompanionKind.COMPANION, uuid)) {
                  data.add(CompanionKind.COMPANION, uuid);
                  SavedPosition position = SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(), living.getYRot(), living.getXRot());
                  data.setOrigin(uuid, position);
                  data.setLastKnownPosition(uuid, position);
                  CompanionSummonLineService.showRegistered(player, living);
               }

               CompanionDeploymentService.rememberDeployedWithinLimit(player, data, CompanionKind.COMPANION, uuid);
               CompanionManager.save(player, data);
               CompanionManager.syncToClient(player, CompanionKind.COMPANION);
               CompanionManager.syncToClient(player, CompanionKind.MOUNT);
            }
         }
      }
   }

   static void sweepDeadEntriesAndSync(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
      if (moveDeadEntriesOutOfLiveList(player, data, kind)) {
         CompanionManager.save(player, data);
         CompanionManager.syncToClient(player, kind);
         CompanionManager.syncDeadToClient(player);
      }
   }

   static int tameTarget(ServerPlayer player, CompanionKind kind) {
      Entity looked = lookedAtEntity(player, 32.0);
      if (looked instanceof LivingEntity living && looked != player) {
         forceTame(player, living);
         if (kind == CompanionKind.MOUNT) {
            PlayerCompanionData data = CompanionManager.data(player);
            data.add(CompanionKind.MOUNT, living.getUUID());
            SavedPosition position = SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(), living.getYRot(), living.getXRot());
            data.setOrigin(living.getUUID(), position);
            data.setLastKnownPosition(living.getUUID(), position);
            data.markMountEligible(living.getUUID());
            CompanionDeploymentService.rememberDeployedWithinLimit(player, data, CompanionKind.MOUNT, living.getUUID());
            CompanionManager.save(player, data);
            CompanionManager.syncToClient(player, CompanionKind.MOUNT);
            CompanionManager.syncToClient(player, CompanionKind.COMPANION);
            CompanionSummonLineService.showRegistered(player, living);
            return 1;
         } else {
            registerContracted(player, living, CompanionKind.COMPANION);
            return 1;
         }
      } else {
         CompanionManager.tell(player, "message.find_me.no_target", ChatFormatting.RED);
         return 0;
      }
   }

   static Optional<CompanionKind> manualBindingKind(ServerPlayer player, LivingEntity living) {
      if (living != player && living.isAlive()) {
         PlayerCompanionData data = CompanionManager.data(player);
         if (isTypeBlacklisted(data, living)) {
            return Optional.empty();
         } else {
            String key = EntityType.getKey(living.getType()).toString();
            boolean configuredOrCompatibleMount = CompanionEntityClassifier.isManualBindingMountKey(key);
            if (!CompanionManager.isOwnedBy(player, living)) {
               return player.getVehicle() == living && configuredOrCompatibleMount ? Optional.of(CompanionKind.MOUNT) : Optional.empty();
            } else {
               return Optional.of(configuredOrCompatibleMount ? CompanionKind.MOUNT : CompanionKind.COMPANION);
            }
         }
      } else {
         return Optional.empty();
      }
   }

   static boolean registerContracted(ServerPlayer player, LivingEntity living, CompanionKind kind) {
      return registerContracted(player, living, kind, true);
   }

   static boolean registerContractedForStorage(ServerPlayer player, LivingEntity living, CompanionKind kind) {
      return registerContracted(player, living, kind, false);
   }

   private static boolean registerContracted(ServerPlayer player, LivingEntity living, CompanionKind kind, boolean rememberDeployed) {
      if (living != player && living.isAlive()) {
         PlayerCompanionData data = CompanionManager.data(player);
         if (kind == CompanionKind.MOUNT && data.contains(CompanionKind.COMPANION, living.getUUID()) && !data.contains(CompanionKind.MOUNT, living.getUUID())) {
            promoteRiddenCompanionToMount(player, data, living);
            return true;
         }

         if (data.contains(kind, living.getUUID())) {
            if (rememberDeployed) {
               CompanionDeploymentService.rememberDeployedWithinLimit(player, data, kind, living.getUUID());
            } else {
               data.clearDeployed(kind, living.getUUID());
            }

            CompanionManager.save(player, data);
            CompanionManager.syncToClient(player, CompanionKind.MOUNT);
            CompanionManager.syncToClient(player, CompanionKind.COMPANION);
            return true;
         } else {
            if (!data.add(kind, living.getUUID())) {
               return false;
            }

            SavedPosition position = SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(), living.getYRot(), living.getXRot());
            data.setOrigin(living.getUUID(), position);
            data.setLastKnownPosition(living.getUUID(), position);
            if (rememberDeployed) {
               CompanionDeploymentService.rememberDeployedWithinLimit(player, data, kind, living.getUUID());
            } else {
               data.clearDeployed(kind, living.getUUID());
            }

            if (kind == CompanionKind.MOUNT) {
               data.markMountEligible(living.getUUID());
            }

            CompanionManager.save(player, data);
            CompanionManager.syncToClient(player, CompanionKind.MOUNT);
            CompanionManager.syncToClient(player, CompanionKind.COMPANION);
            CompanionSummonLineService.showRegistered(player, living);
            return true;
         }
      } else {
         return false;
      }
   }

   private static void promoteRiddenCompanionToMount(ServerPlayer player, PlayerCompanionData data, LivingEntity living) {
      UUID uuid = living.getUUID();
      data.add(CompanionKind.MOUNT, uuid);
      SavedPosition position = SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(), living.getYRot(), living.getXRot());
      data.setOrigin(uuid, position);
      data.setLastKnownPosition(uuid, position);
      CompanionDeploymentService.rememberDeployedWithinLimit(player, data, CompanionKind.MOUNT, uuid);
      data.markMountEligible(uuid);
      CompanionManager.save(player, data);
      CompanionManager.syncToClient(player, CompanionKind.COMPANION);
      CompanionManager.syncToClient(player, CompanionKind.MOUNT);
   }

   private static boolean moveDeadEntriesOutOfLiveList(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
      boolean changed = false;

      for (UUID uuid : List.copyOf(data.list(kind))) {
         Entity entity = CompanionManager.findLoadedEntity(player.level().getServer(), data, uuid).orElse(null);
         if ((entity == null || !entity.isAlive()) && (kind != CompanionKind.COMPANION || !CompanionManager.shoulderEntityTag(player, uuid).isPresent())) {
            Optional<CompoundTag> stored = data.storedEntity(uuid);
            boolean deadStored = stored.isPresent() && stored.get().contains("CompanionRescueDead") && stored.get().getBooleanOr("CompanionRescueDead", false);
            if (entity != null || deadStored) {
               if (entity instanceof LivingEntity living) {
                  CompanionManager.markRegisteredDead(player.level().getServer(), living);
                  changed = true;
               } else if (stored.isPresent()) {
                  CompoundTag tag = stored.get();
                  if (!tag.getStringOr("CompanionRescueName", "").endsWith(" (Dead)")) {
                     tag.putString("CompanionRescueName", tag.getStringOr("CompanionRescueName", ""));
                  }

                  data.storeEntity(uuid, tag);
                  changed |= data.markDead(uuid, kind);
               }
            }
         }
      }

      return changed;
   }

   private static Entity lookedAtEntity(ServerPlayer player, double range) {
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      Vec3 end = eye.add(look.scale(range));
      AABB area = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
      Entity best = null;
      double bestDistance = range * range;

      for (Entity entity : player.level().getEntities(player, area, Entity::isAlive)) {
         Entity target = entity;
         if (Parts.isPart(entity)) {
            Entity parent = Parts.parentOf(entity);
            if (parent instanceof LivingEntity && parent.isAlive()) {
               target = parent;
            }
         }

         if (target instanceof LivingEntity && target != player) {
            AABB box = entity.getBoundingBox().inflate(entity.getPickRadius() + 0.35);
            Optional<Vec3> hit = box.clip(eye, end);
            if (!hit.isEmpty()) {
               double distance = eye.distanceToSqr(hit.get());
               if (!(distance >= bestDistance)) {
                  best = target;
                  bestDistance = distance;
               }
            }
         }
      }

      return best;
   }

   private static void forceTame(ServerPlayer player, LivingEntity living) {
      if (living instanceof TamableAnimal tamable) {
         tamable.tame(player);
         tamable.setOrderedToSit(false);
      }

      if (living instanceof AbstractHorse horse) {
         horse.tameWithName(player);
      }

      callMethod(living, "setOwnerUUID", player.getUUID());
      callMethod(living, "setOwnerId", player.getUUID());
      callMethod(living, "setTameOwnerUUID", player.getUUID());
      callMethod(living, "setMasterUUID", player.getUUID());
      callMethod(living, "setOwner", player);
      callMethod(living, "setTame", Boolean.TRUE);
      callMethod(living, "setTamed", Boolean.TRUE);
      callMethod(living, "setIsTamed", Boolean.TRUE);
   }

   private static void callMethod(Object target, String name, Object value) {
      for (Method method : target.getClass().getMethods()) {
         if (method.getName().equals(name) && method.getParameterCount() == 1) {
            Class<?> parameter = method.getParameterTypes()[0];
            if (value instanceof Boolean && (parameter == boolean.class || parameter == Boolean.class)
               || value != null && parameter.isAssignableFrom(value.getClass())) {
               try {
                  method.invoke(target, value);
                  return;
               } catch (ReflectiveOperationException | RuntimeException var9) {
               }
            }
         }
      }
   }

   private static boolean canAutoRegisterRiddenMount(ServerPlayer player, LivingEntity entity) {
      String key = EntityType.getKey(entity.getType()).toString();
      return Config.customMounts.contains(key) ? true : CompanionManager.isOwnedBy(player, entity);
   }

   private static boolean isSittingHomePet(LivingEntity living) {
      return !(living instanceof TamableAnimal tamable && (tamable.isOrderedToSit() || tamable.isInSittingPose()))
         ? callBooleanGetter(living, "isOrderedToSit")
            || callBooleanGetter(living, "isInSittingPose")
            || callBooleanGetter(living, "isSitting")
            || callBooleanGetter(living, "isSittingPose")
            || callBooleanGetter(living, "getSitting")
            || callBooleanGetter(living, "getIsSitting")
         : true;
   }

   private static boolean callBooleanGetter(Object target, String name) {
      for (Method method : target.getClass().getMethods()) {
         if (method.getName().equals(name)
            && method.getParameterCount() == 0
            && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
            try {
               Object value = method.invoke(target);
               return Boolean.TRUE.equals(value);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
               return false;
            }
         }
      }

      return false;
   }

   private static boolean isTypeBlacklisted(PlayerCompanionData data, LivingEntity living) {
      return data.isAutoBindTypeBlacklisted(EntityType.getKey(living.getType()).toString());
   }
}
