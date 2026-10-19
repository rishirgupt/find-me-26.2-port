package com.kuzhi.findme.server;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.ModBlocks;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.network.RescueMagicPacket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

final class CompanionHomeResidentService {
   private static final int CHECK_INTERVAL_TICKS = 40;
   private static final double ACTIVATION_DISTANCE_SQR = 6400.0;
   private static final Set<UUID> RESIDENTS = new HashSet<>();
   private static final Map<UUID, Long> NEXT_PLAYER_CHECK = new HashMap<>();

   private CompanionHomeResidentService() {
   }

   static void tickPlayer(ServerPlayer player) {
      long now = player.level().getGameTime();
      long next = NEXT_PLAYER_CHECK.getOrDefault(player.getUUID(), 0L);
      boolean restoreDue = now >= next;
      if (restoreDue) {
         NEXT_PLAYER_CHECK.put(player.getUUID(), now + 40L);
      }

      if (restoreDue) {
         PlayerCompanionData data = CompanionManager.data(player);
         boolean mountsChanged = false;
         boolean companionsChanged = false;

         for (CompanionKind kind : CompanionKind.values()) {
            boolean changed = false;

            for (UUID uuid : data.wheelOrder(kind)) {
               if (restoreDue && shouldRestoreResident(player, data, kind, uuid)) {
                  changed |= restoreResidentNearHouse(player, data, kind, uuid);
               }
            }

            if (kind == CompanionKind.MOUNT) {
               mountsChanged |= changed;
            } else {
               companionsChanged |= changed;
            }
         }

         if (mountsChanged || companionsChanged) {
            CompanionManager.save(player, data);
            if (mountsChanged) {
               CompanionManager.syncToClient(player, CompanionKind.MOUNT);
            }

            if (companionsChanged) {
               CompanionManager.syncToClient(player, CompanionKind.COMPANION);
            }
         }
      }
   }

   static boolean isResident(UUID uuid) {
      return uuid != null && RESIDENTS.contains(uuid);
   }

   static void markResident(UUID uuid) {
      if (uuid != null) {
         RESIDENTS.add(uuid);
      }
   }

   static void clearResident(UUID uuid) {
      if (uuid != null) {
         RESIDENTS.remove(uuid);
      }
   }

   static void clearResident(LivingEntity living) {
      if (living != null) {
         clearResident(living.getUUID());
         CompanionHomeBehaviorService.clearResidentBehavior(living);
      }
   }

   static void markResidentEntity(LivingEntity living) {
      if (living != null) {
         keepResidentPersistent(living);
         markResident(living.getUUID());
         CompanionHomeBehaviorService.applyResidentBehavior(living);
      }
   }

   static boolean sendHomeIfPossible(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, LivingEntity living) {
      if (player != null && data != null && kind != null && living != null && data.contains(kind, living.getUUID())) {
         Optional<ServerLevel> maybeLevel = houseLevel(player, data, living.getUUID());
         Optional<BlockPos> maybeHouse = houseBlock(data, living.getUUID());
         if (!maybeLevel.isEmpty() && !maybeHouse.isEmpty()) {
            ServerLevel level = maybeLevel.get();
            BlockPos housePos = maybeHouse.get();
            if (!validLoadedHouse(level, housePos)) {
               return false;
            }

            BlockPos target = findResidentSpot(level, living, housePos).or(() -> findResidentFootPos(level, housePos)).orElse(housePos.above());
            return CompanionStorageService.sendHomeAfterStorage(player, data, kind, living, level, target, living.getYRot(), living.getXRot());
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   static void collectResidentsForBrokenHouse(ServerPlayer player, PlayerCompanionData data, SavedPosition source) {
      if (player != null && data != null && source != null) {
         boolean mountsChanged = false;
         boolean companionsChanged = false;

         for (CompanionKind kind : CompanionKind.values()) {
            boolean changed = false;

            for (UUID uuid : data.wheelOrder(kind)) {
               if (isResident(uuid) && !data.homeNestBlock(uuid).filter(saved -> sameBlock(saved, source)).isEmpty()) {
                  data.clearHomePosition(uuid);
                  Entity entity = CompanionManager.locateEntity(player.level().getServer(), data, uuid).orElse(null);
                  if (entity instanceof LivingEntity living && living.isAlive()) {
                     changed |= CompanionDeploymentService.collectLiving(player, data, kind, living);
                  } else {
                     clearResident(uuid);
                     changed = true;
                  }
               }
            }

            if (kind == CompanionKind.MOUNT) {
               mountsChanged |= changed;
            } else {
               companionsChanged |= changed;
            }
         }

         if (mountsChanged || companionsChanged) {
            CompanionManager.save(player, data);
            if (mountsChanged) {
               CompanionManager.syncToClient(player, CompanionKind.MOUNT);
            }

            if (companionsChanged) {
               CompanionManager.syncToClient(player, CompanionKind.COMPANION);
            }
         }
      }
   }

   private static boolean shouldRestoreResident(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid) {
      if (uuid == null || kind == null || isResident(uuid) || data.isDeployed(kind, uuid) || !data.storedEntity(uuid).isPresent()) {
         return false;
      }

      if (!CompanionManager.isStoragePending(uuid) && !data.homeNestBlock(uuid).isEmpty() && !data.homePosition(uuid).isEmpty()) {
         Optional<ServerLevel> maybeLevel = houseLevel(player, data, uuid);
         Optional<BlockPos> maybeHouse = houseBlock(data, uuid);
         if (!maybeLevel.isEmpty() && !maybeHouse.isEmpty()) {
            ServerLevel level = maybeLevel.get();
            BlockPos housePos = maybeHouse.get();
            return validLoadedHouse(level, housePos) && player.level().dimension().equals(level.dimension())
               ? player.distanceToSqr(housePos.getX() + 0.5, housePos.getY() + 0.5, housePos.getZ() + 0.5) <= 6400.0
               : false;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean restoreResidentNearHouse(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid) {
      ServerLevel level = houseLevel(player, data, uuid).orElse(null);
      BlockPos housePos = houseBlock(data, uuid).orElse(null);
      if (level != null && housePos != null) {
         BlockPos target = findResidentFootPos(level, housePos).orElse(housePos.above());
         Entity restored = CompanionEntityTransferService.restoreStoredEntityForArrival(
               level,
               player,
               data,
               uuid,
               target,
               player.getYRot(),
               0.0F,
               Vec3.atCenterOf(housePos),
               36,
               RescueMagicPacket.Style.GROUND_CIRCLE,
               RescueMagicPacket.Purpose.SUMMON
            )
            .orElse(null);
         if (restored instanceof LivingEntity living && living.isAlive()) {
            BlockPos openTarget = findResidentSpot(level, living, housePos).orElse(target);
            if (!openTarget.equals(target)) {
               living = CompanionEntityTransferService.moveEntityTo(living, level, openTarget, living.getYRot(), living.getXRot());
            }

            markResidentEntity(living);
            data.clearDeployed(kind, uuid);
            data.setLastKnownPosition(uuid, SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(), living.getYRot(), living.getXRot()));
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static Optional<ServerLevel> houseLevel(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
      return data.homeNestBlock(uuid).map(SavedPosition::dimension).map(player.level().getServer()::getLevel);
   }

   private static Optional<BlockPos> houseBlock(PlayerCompanionData data, UUID uuid) {
      return data.homeNestBlock(uuid).map(SavedPosition::blockPos);
   }

   private static boolean validLoadedHouse(ServerLevel level, BlockPos housePos) {
      return level.hasChunkAt(housePos) && level.isLoaded(housePos) && level.getBlockState(housePos).is((Block)ModBlocks.SMALL_HOUSE.get());
   }

   static Optional<BlockPos> findResidentSpot(ServerLevel level, LivingEntity living, BlockPos housePos) {
      if (level != null && living != null && housePos != null) {
         for (int y = -1; y <= 2; y++) {
            for (int r = 2; r <= 5; r++) {
               for (int x = -r; x <= r; x++) {
                  for (int z = -r; z <= r; z++) {
                     if (Math.abs(x) == r || Math.abs(z) == r) {
                        BlockPos candidate = housePos.offset(x, y, z);
                        if (CompanionPlacementFinder.isSafe(level, candidate)
                           && CompanionPlacementFinder.hasOpenEntitySpace(level, living, candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5)) {
                           return Optional.of(candidate);
                        }
                     }
                  }
               }
            }
         }

         return CompanionPlacementFinder.findOpenEntitySpace(level, living, housePos.offset(2, 0, 0));
      } else {
         return Optional.empty();
      }
   }

   private static Optional<BlockPos> findResidentFootPos(ServerLevel level, BlockPos housePos) {
      if (level != null && housePos != null) {
         for (int y = -1; y <= 2; y++) {
            for (int r = 2; r <= 5; r++) {
               for (int x = -r; x <= r; x++) {
                  for (int z = -r; z <= r; z++) {
                     if (Math.abs(x) == r || Math.abs(z) == r) {
                        BlockPos candidate = housePos.offset(x, y, z);
                        if (CompanionPlacementFinder.isSafe(level, candidate)) {
                           return Optional.of(candidate);
                        }
                     }
                  }
               }
            }
         }

         return CompanionPlacementFinder.findSafe(level, housePos.offset(2, 0, 0));
      } else {
         return Optional.empty();
      }
   }

   private static void keepResidentPersistent(LivingEntity living) {
      if (living instanceof Mob mob) {
         mob.setPersistenceRequired();
      }
   }

   private static boolean sameBlock(SavedPosition first, SavedPosition second) {
      return first != null && second != null && first.dimension().equals(second.dimension()) && first.blockPos().equals(second.blockPos());
   }
}
