package com.kuzhi.findme.server;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.ModItems;
import com.kuzhi.findme.common.NamePaperItem;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.RescueMagicPacket;
import com.kuzhi.findme.network.SableVehiclePreviewCapturePacket;
import com.kuzhi.findme.network.SableVehiclePreviewDeletePacket;
import com.kuzhi.findme.network.SableVehiclePreviewTransferPacket;
import com.kuzhi.findme.network.VehicleCommandPacket;
import com.kuzhi.findme.network.VehicleListPacket;
import com.kuzhi.findme.network.VehicleSealEffectPacket;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.kuzhi.findme.compat.Parts;

public final class VehicleManager {
   private static final String VEHICLE_MARKER = "FindMeVehicle";
   private static final int VEHICLE_SUMMON_MAGIC_TICKS = 36;
   private static final int VEHICLE_SEAL_TICKS = 34;
   private static final double VEHICLE_EFFECT_PACKET_RADIUS = 96.0;
   private static final int SWITCH_DAMAGE_PROTECTION_TICKS = 80;
   private static final int SABLE_ACTION_COOLDOWN_TICKS = 8;
   private static final List<VehicleManager.PendingVehicleSummon> PENDING_SUMMONS = new ArrayList<>();
   private static final List<VehicleManager.VehicleSwitchDamageProtection> SWITCH_DAMAGE_PROTECTIONS = new ArrayList<>();
   private static final Map<UUID, Long> SABLE_ACTION_COOLDOWNS = new HashMap<>();

   private VehicleManager() {
   }

   public static void bootstrap() {
      SableVehicleCompatibility.bootstrap();
   }

   public static boolean isBinder(ItemStack stack) {
      return stack.is((Item)ModItems.VEHICLE_BINDER.get());
   }

   public static void handle(ServerPlayer player, VehicleCommandPacket.Action action, int value) {
      PlayerCompanionData data = data(player);
      switch (action) {
         case SYNC:
            syncToClient(player);
            break;
         case SELECT:
            cancelPendingSummons(player.getUUID());
            data.setActiveVehicleIndex(value);
            save(player, data);
            syncToClient(player);
            break;
         case SELECT_SUMMON:
            if (data.setActiveVehicleIndex(value)) {
               save(player, data);
               summonOrStoreActive(player, data);
            } else {
               tell(player, "message.find_me.invalid_vehicle_index", ChatFormatting.RED);
            }

            syncToClient(player);
            break;
         case RECALL:
            recallIndex(player, data, value);
            syncToClient(player);
            break;
         case REORDER_WHEEL:
            int from = value % 1000;
            int to = value / 1000;
            if (data.reorderVehicleWheel(from, to)) {
               save(player, data);
            }

            syncToClient(player);
            break;
         case ADD_TO_WHEEL:
            List<UUID> warehouse = data.vehicleWarehouseOrder();
            if (value >= 0 && value < warehouse.size()) {
               data.addVehicleWheelSlot(warehouse.get(value));
               save(player, data);
            }

            syncToClient(player);
            break;
         case REMOVE_FROM_WHEEL:
            data.removeVehicleWheelSlot(value);
            save(player, data);
            syncToClient(player);
            break;
         case REMOVE:
            removeAt(player, data, value);
            syncToClient(player);
      }
   }

   public static boolean bindVehicle(ServerPlayer player, Entity target, InteractionHand hand) {
      Optional<SableVehicleCompatibility.Handle> maybeSable = SableVehicleCompatibility.findForEntity(target);
      if (maybeSable.isPresent()) {
         return bindSableVehicle(player, maybeSable.get(), hand);
      } else {
         Entity vehicle = unwrapPart(target);
         if (!isBindableVehicle(vehicle)) {
            tell(player, "message.find_me.vehicle_not_supported", ChatFormatting.YELLOW);
            return false;
         } else {
            PlayerCompanionData data = data(player);
            UUID uuid = vehicle.getUUID();
            if (data.containsVehicle(uuid)) {
               data.setActiveVehicleUuid(uuid);
               save(player, data);
               syncToClient(player);
               tell(player, "message.find_me.vehicle_already_bound", ChatFormatting.YELLOW, Component.literal(storedName(data, uuid)));
               return true;
            } else if (!storeVehicle(player, data, vehicle)) {
               tell(player, "message.find_me.vehicle_store_failed", ChatFormatting.RED);
               return false;
            } else {
               data.addVehicle(uuid);
               data.clearDeployedVehicle(uuid);
               NamePaperItem.consumeOne(player.getItemInHand(hand), player);
               save(player, data);
               syncToClient(player);
               tell(player, "message.find_me.vehicle_bound", ChatFormatting.GREEN, Component.literal(storedName(data, uuid)));
               return true;
            }
         }
      }
   }

   public static boolean bindVehicleAt(ServerPlayer player, BlockPos pos, InteractionHand hand) {
      Optional<SableVehicleCompatibility.Handle> maybeSable = SableVehicleCompatibility.findAt(player.level(), pos);
      if (maybeSable.isEmpty()) {
         tell(
            player, SableVehicleCompatibility.available() ? "message.find_me.vehicle_not_supported" : "message.find_me.sable_not_loaded", ChatFormatting.YELLOW
         );
         return false;
      } else {
         return bindSableVehicle(player, maybeSable.get(), hand);
      }
   }

   private static boolean bindSableVehicle(ServerPlayer player, SableVehicleCompatibility.Handle handle, InteractionHand hand) {
      if (isSableActionCoolingDown(player)) {
         return false;
      } else if (!SableVehicleCompatibility.snapshotAvailable()) {
         tell(player, "message.find_me.sable_blueprint_not_loaded", ChatFormatting.YELLOW);
         return false;
      } else {
         PlayerCompanionData data = data(player);
         UUID uuid = handle.uuid();
         if (data.containsVehicle(uuid)) {
            data.setActiveVehicleUuid(uuid);
            save(player, data);
            syncToClient(player);
            tell(player, "message.find_me.vehicle_already_bound", ChatFormatting.YELLOW, Component.literal(storedName(data, uuid)));
            return true;
         } else {
            AABB sealBox = handle.box();
            sendSablePreviewCapture(player, uuid, sealBox);
            if (!SableVehicleCompatibility.store(player, data, handle)) {
               tell(player, "message.find_me.vehicle_store_failed", ChatFormatting.RED);
               return false;
            } else {
               sendSableVehicleSealEffect(player, sealBox);
               data.addVehicle(uuid);
               data.clearDeployedVehicle(uuid);
               markSableActionCooldown(player);
               NamePaperItem.consumeOne(player.getItemInHand(hand), player);
               save(player, data);
               syncToClient(player);
               tell(player, "message.find_me.vehicle_bound", ChatFormatting.GREEN, Component.literal(storedName(data, uuid)));
               return true;
            }
         }
      }
   }

   public static boolean summonOrStoreActive(ServerPlayer player, PlayerCompanionData data) {
      Optional<UUID> active = data.activeVehicle();
      if (active.isEmpty()) {
         tell(player, "message.find_me.no_vehicles", ChatFormatting.YELLOW);
         return false;
      }

      UUID uuid = active.get();
      if (isSableVehicle(player, data, uuid)) {
         return summonOrStoreSable(player, data, uuid);
      }

      Entity live = findEntity(player.level().getServer(), uuid).orElse(null);
      Entity currentRide = player.getVehicle();
      if (currentRide != null && currentRide.getUUID().equals(uuid)) {
         tell(player, "message.find_me.vehicle_already_summoned", ChatFormatting.YELLOW, Component.literal(storedName(data, uuid)));
         return false;
      }

      if (currentRide != null && live != currentRide) {
         return switchToVehicle(player, data, uuid, currentRide, live);
      }

      if (live == null || live.isRemoved() || !data.isVehicleDeployed(uuid) && live.level() != player.level()) {
         data.deployedVehicle().ifPresent(previous -> {
            if (!previous.equals(uuid)) {
               findEntity(player.level().getServer(), previous).ifPresent(entityx -> storeSelected(player, data, previous, entityx));
               data.clearDeployedVehicle(previous);
            }
         });
         Entity entity = live;
         if (entity != null && !entity.isRemoved()) {
            entity = moveVehicleTo(
               entity, player.level(), findVehicleSpotForEntity(player, entity, vehiclePlacementOrigin(player)), player.getYRot(), player.getXRot()
            );
            sendVehicleSummonMagic(player, entity.position(), vehicleRadius(entity), vehicleHeight(entity));
         } else {
            if (scheduleStoredVehicleSummon(player, data, uuid)) {
               return true;
            }

            entity = null;
         }

         if (entity != null && !entity.isRemoved()) {
            enforceSingleRideSlotForVehicle(player, data, uuid, currentRide);
            data.setDeployedVehicle(uuid);
            data.setLastKnownPosition(uuid, SavedPosition.of(entity.level(), entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot()));
            save(player, data);
            tell(player, "message.find_me.vehicle_summoned", ChatFormatting.AQUA, entity.getDisplayName());
            return true;
         }

         if (rawStoredTag(data, uuid).isEmpty()) {
            data.remove(uuid);
         }

         save(player, data);
         tell(player, "message.find_me.unavailable_vehicle", ChatFormatting.RED);
         return false;
      } else {
         tell(player, "message.find_me.vehicle_already_summoned", ChatFormatting.YELLOW, live.getDisplayName());
         return false;
      }
   }

   private static boolean summonOrStoreSable(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
      if (isSableActionCoolingDown(player)) {
         return false;
      }

      if (SableVehicleCompatibility.isStored(data, uuid)) {
         if (!SableVehicleCompatibility.canRestore(data, uuid)) {
            tell(player, "message.find_me.sable_blueprint_not_loaded", ChatFormatting.YELLOW);
            return false;
         } else if (!scheduleStoredVehicleSummon(player, data, uuid)) {
            tell(player, "message.find_me.unavailable_vehicle", ChatFormatting.RED);
            return false;
         } else {
            markSableActionCooldown(
               player, CompanionArrivalMagicService.revealDelayTicks(36, RescueMagicPacket.Style.GROUND_CIRCLE, RescueMagicPacket.Purpose.SUMMON) + 8
            );
            return true;
         }
      } else {
         Optional<SableVehicleCompatibility.Handle> live = SableVehicleCompatibility.find(player, uuid);
         if (live.isPresent()) {
            SableVehicleCompatibility.Handle handle = live.get();
            Entity previousRide = player.getVehicle();
            if (data.isVehicleDeployed(uuid) && previousRide == null) {
               tell(player, "message.find_me.vehicle_already_summoned", ChatFormatting.YELLOW, Component.literal(storedName(data, uuid)));
               return false;
            } else {
               BlockPos pos = findSableVehicleSpot(player, data, uuid, handle);
               if (!SableVehicleCompatibility.move(handle, player.level(), pos)) {
                  tell(player, "message.find_me.vehicle_switch_failed", ChatFormatting.YELLOW);
                  return false;
               } else {
                  SableVehicleCompatibility.pushPlayerOut(player, handle);
                  Vec3 anchor = Vec3.atBottomCenterOf(pos);
                  sendVehicleSummonMagic(player, anchor, handle.radius(), handle.height());
                  enforceSingleRideSlotForVehicle(player, data, uuid, previousRide);
                  data.setDeployedVehicle(uuid);
                  data.setLastKnownPosition(uuid, SavedPosition.of(player.level(), anchor.x, anchor.y, anchor.z, player.getYRot(), player.getXRot()));
                  markSableActionCooldown(player);
                  save(player, data);
                  tell(player, "message.find_me.vehicle_summoned", ChatFormatting.AQUA, Component.literal(handle.name()));
                  return true;
               }
            }
         } else if (!scheduleStoredVehicleSummon(player, data, uuid)) {
            tell(player, "message.find_me.unavailable_vehicle", ChatFormatting.RED);
            return false;
         } else {
            markSableActionCooldown(
               player, CompanionArrivalMagicService.revealDelayTicks(36, RescueMagicPacket.Style.GROUND_CIRCLE, RescueMagicPacket.Purpose.SUMMON) + 8
            );
            return true;
         }
      }
   }

   private static boolean switchToVehicle(ServerPlayer player, PlayerCompanionData data, UUID targetUuid, Entity currentRide, Entity liveTarget) {
      if (currentRide != null && !currentRide.isRemoved()) {
         cancelPendingSummons(player.getUUID());
         boolean protectingOldMount = data.contains(CompanionKind.MOUNT, currentRide.getUUID());
         if (protectingOldMount) {
            protectOldMountFromVehicleSwitch(player, currentRide.getUUID(), targetUuid);
         }

         if (liveTarget != null && liveTarget.getFirstPassenger() != null && liveTarget.getFirstPassenger() != player) {
            clearSwitchDamageProtection(player.getUUID(), currentRide.getUUID(), targetUuid);
            tell(player, "message.find_me.occupied", ChatFormatting.RED, Component.literal(storedName(data, targetUuid)));
            return false;
         }

         Entity target = liveTarget;
         BlockPos switchPos = BlockPos.containing(currentRide.getX(), currentRide.getY(), currentRide.getZ());
         boolean restored = false;
         if (target != null && !target.isRemoved()) {
            if (target.level() != player.level() || target.distanceToSqr(currentRide) > 1.0) {
               target = moveVehicleTo(
                  target, player.level(), findVehicleSpotForEntityNear(player, target, switchPos), currentRide.getYRot(), currentRide.getXRot()
               );
            }
         } else {
            target = restoreVehicle(player, data, targetUuid, findVehicleSpotForStored(player, data, targetUuid, switchPos)).orElse(null);
            restored = target != null;
         }

         if (target != null && !target.isRemoved()) {
            target.setYRot(currentRide.getYRot());
            target.setXRot(currentRide.getXRot());
            target.setDeltaMovement(currentRide.getDeltaMovement());
            target.fallDistance = 0.0F;
            boolean riding = VehicleCompatibilityService.tryBoardVehicle(player, target, currentRide);
            if (!riding) {
               if (restored || data.containsVehicle(targetUuid)) {
                  storeVehicle(player, data, target);
                  data.clearDeployedVehicle(targetUuid);
                  save(player, data);
               }

               clearSwitchDamageProtection(player.getUUID(), currentRide.getUUID(), targetUuid);
               tell(player, "message.find_me.vehicle_switch_failed", ChatFormatting.YELLOW);
               return false;
            } else {
               UUID oldUuid = currentRide.getUUID();
               if (!oldUuid.equals(targetUuid) && isVehicleEntry(data, oldUuid)) {
                  storeVehicle(player, data, currentRide);
                  data.clearDeployedVehicle(oldUuid);
               } else if (!oldUuid.equals(targetUuid)) {
                  protectOldMountFromVehicleSwitch(player, oldUuid, targetUuid);
                  CompanionDeploymentService.retireMountAfterSharedRideSwitch(player, data, currentRide);
               }

               data.deployedVehicle().ifPresent(previous -> {
                  if (!previous.equals(targetUuid) && !previous.equals(oldUuid)) {
                     locateEntity(player.level().getServer(), data, previous).ifPresent(entity -> {
                        storeVehicle(player, data, entity);
                        data.clearDeployedVehicle(previous);
                     });
                  }
               });
               data.setDeployedVehicle(targetUuid);
               data.setLastKnownPosition(
                  targetUuid, SavedPosition.of(target.level(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot())
               );
               enforceSingleRideSlotForVehicle(player, data, targetUuid, currentRide);
               save(player, data);
               spawnBlinkParticles(target);
               tell(player, "message.find_me.vehicle_switched", ChatFormatting.AQUA, target.getDisplayName());
               return true;
            }
         } else {
            clearSwitchDamageProtection(player.getUUID(), currentRide.getUUID(), targetUuid);
            tell(player, "message.find_me.unavailable_vehicle", ChatFormatting.RED);
            return false;
         }
      } else {
         return summonOrStoreActive(player, data);
      }
   }

   public static void syncToClient(ServerPlayer player) {
      PlayerCompanionData data = data(player);
      List<VehicleListPacket.Entry> wheelEntries = entries(player, data, data.vehicleWheelOrder());
      List<VehicleListPacket.Entry> allEntries = entries(player, data, data.vehicleWarehouseOrder());
      ModNetwork.sendToPlayer(player, new VehicleListPacket(data.activeVehicleIndex(), wheelEntries, allEntries));
   }

   public static void collectDeployed(ServerPlayer player) {
      PlayerCompanionData data = data(player);
      cancelPendingSummons(player.getUUID());
      data.deployedVehicle().ifPresent(uuid -> {
         Entity entity = locateEntity(player.level().getServer(), data, uuid).orElse(null);
         boolean collected = false;
         if (isSableVehicle(player, data, uuid)) {
            collected = SableVehicleCompatibility.store(player, data, uuid);
         } else if (entity != null && !entity.isRemoved()) {
            collected = storeVehicle(player, data, entity);
         } else {
            collected = true;
         }

         if (collected) {
            data.clearDeployedVehicle(uuid);
         }
      });
      save(player, data);
   }

   public static void rename(ServerPlayer player, boolean wheel, int index, String name) {
      PlayerCompanionData data = data(player);
      List<UUID> warehouse = data.vehicleWarehouseOrder();
      Optional<UUID> uuid = wheel
         ? data.vehicleWheelUuidAt(index)
         : (index >= 0 && index < warehouse.size() ? Optional.of(warehouse.get(index)) : Optional.empty());
      if (uuid.isEmpty()) {
         tell(player, "message.find_me.invalid_vehicle_index", ChatFormatting.RED);
      } else {
         String trimmed = name == null ? "" : name.trim();
         if (trimmed.length() > 48) {
            trimmed = trimmed.substring(0, 48);
         }

         data.setDisplayName(uuid.get(), trimmed);
         save(player, data);
         syncToClient(player);
      }
   }

   public static boolean isVehicleEntry(PlayerCompanionData data, UUID uuid) {
      return data.containsVehicle(uuid)
         || data.isVehicleMount(uuid)
         || rawStoredTag(data, uuid)
            .map(tag -> tag.getBooleanOr("FindMeVehicle", false) || tag.getBooleanOr("FindMeVehicleMount", false) || SableVehicleCompatibility.isStoredSable(tag))
            .orElse(false);
   }

   public static boolean collectVehicle(ServerPlayer player, PlayerCompanionData data, UUID uuid, Entity entity) {
      if (!isVehicleEntry(data, uuid)) {
         return false;
      }

      storeSelected(player, data, uuid, entity);
      syncToClient(player);
      return true;
   }

   public static boolean summonVehicle(ServerPlayer player, PlayerCompanionData data, UUID uuid, long now, boolean inCombat) {
      if (!data.containsVehicle(uuid)) {
         data.addVehicle(uuid);
      }

      data.setActiveVehicleUuid(uuid);
      return summonOrStoreActive(player, data);
   }

   public static void tickVehicleCinematics(MinecraftServer server) {
   }

   public static void tickPendingSummons(MinecraftServer server) {
      tickSwitchDamageProtections();
      tickSableActionCooldowns(server);
      Iterator<VehicleManager.PendingVehicleSummon> iterator = PENDING_SUMMONS.iterator();

      while (iterator.hasNext()) {
         VehicleManager.PendingVehicleSummon pending = iterator.next();
         pending.age++;
         if (pending.age >= pending.delayTicks) {
            iterator.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(pending.playerUuid);
            if (player != null) {
               PlayerCompanionData data = data(player);
               if (data.containsVehicle(pending.vehicleUuid) && !data.activeVehicle().filter(pending.vehicleUuid::equals).isEmpty()) {
                  Entity currentRide = player.getVehicle();
                  if (isSableVehicle(player, data, pending.vehicleUuid)) {
                     Optional<SableVehicleCompatibility.Handle> restored = SableVehicleCompatibility.restore(
                        player, data, pending.vehicleUuid, pending.position
                     );
                     if (restored.isEmpty()) {
                        if (rawStoredTag(data, pending.vehicleUuid).isEmpty()) {
                           data.remove(pending.vehicleUuid);
                        }

                        save(player, data);
                        syncToClient(player);
                        tell(player, "message.find_me.unavailable_vehicle", ChatFormatting.RED);
                     } else {
                        UUID restoredUuid = restored.get().uuid();
                        if (!pending.vehicleUuid.equals(restoredUuid)) {
                           sendSablePreviewTransfer(player, pending.vehicleUuid, restoredUuid);
                        }

                        enforceSingleRideSlotForVehicle(player, data, restoredUuid, currentRide);
                        Vec3 anchor = Vec3.atBottomCenterOf(pending.position);
                        data.setDeployedVehicle(restoredUuid);
                        data.setLastKnownPosition(
                           restoredUuid, SavedPosition.of(player.level(), anchor.x, anchor.y, anchor.z, player.getYRot(), player.getXRot())
                        );
                        markSableActionCooldown(player);
                        save(player, data);
                        syncToClient(player);
                        tell(player, "message.find_me.vehicle_summoned", ChatFormatting.AQUA, Component.literal(restored.get().name()));
                     }
                  } else if (currentRide == null || currentRide.getUUID().equals(pending.vehicleUuid)) {
                     Entity entity = restoreVehicle(player, data, pending.vehicleUuid, pending.position).orElse(null);
                     if (entity != null && !entity.isRemoved()) {
                        entity.setYRot(player.getYRot());
                        entity.setXRot(player.getXRot());
                        entity.fallDistance = 0.0F;
                        enforceSingleRideSlotForVehicle(player, data, pending.vehicleUuid, currentRide);
                        data.setDeployedVehicle(pending.vehicleUuid);
                        data.setLastKnownPosition(
                           pending.vehicleUuid,
                           SavedPosition.of(entity.level(), entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot())
                        );
                        save(player, data);
                        syncToClient(player);
                        tell(player, "message.find_me.vehicle_summoned", ChatFormatting.AQUA, entity.getDisplayName());
                     } else {
                        if (rawStoredTag(data, pending.vehicleUuid).isEmpty()) {
                           data.remove(pending.vehicleUuid);
                        }

                        save(player, data);
                        syncToClient(player);
                        tell(player, "message.find_me.unavailable_vehicle", ChatFormatting.RED);
                     }
                  }
               }
            }
         }
      }
   }

   public static boolean shouldCancelSwitchVehicleDamage(LivingEntity victim, DamageSource source) {
      if (victim != null && source != null && !SWITCH_DAMAGE_PROTECTIONS.isEmpty()) {
         UUID victimUuid = victim.getUUID();

         for (VehicleManager.VehicleSwitchDamageProtection protection : SWITCH_DAMAGE_PROTECTIONS) {
            if (protection.mountUuid.equals(victimUuid) && isProtectedVehicleDamage(victim, source, protection)) {
               victim.invulnerableTime = Math.max(victim.invulnerableTime, 20);
               victim.fallDistance = 0.0F;
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static boolean releaseVehicle(ServerPlayer player, PlayerCompanionData data, UUID uuid, Entity entity) {
      if (!isVehicleEntry(data, uuid)) {
         return false;
      }

      removeUuid(player, data, uuid, entity);
      return true;
   }

   public static boolean snapshotBeforeUnload(MinecraftServer server, Entity entity) {
      UUID uuid = entity.getUUID();

      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         PlayerCompanionData data = data(player);
         if (data.containsVehicle(uuid)) {
            storeVehicle(player, data, entity);
            data.clearDeployedVehicle(uuid);
            save(player, data);
            syncToClient(player);
            return true;
         }
      }

      return false;
   }

   static boolean storeMountedEntity(ServerPlayer player, PlayerCompanionData data, Entity entity) {
      if (entity == null) {
         return false;
      } else {
         Optional<SableVehicleCompatibility.Handle> sable = SableVehicleCompatibility.findForEntity(entity);
         if (sable.isPresent() && isVehicleEntry(data, sable.get().uuid())) {
            return SableVehicleCompatibility.store(player, data, sable.get());
         } else {
            return !isVehicleEntry(data, entity.getUUID()) ? false : storeSelected(player, data, entity.getUUID(), entity);
         }
      }
   }

   static boolean collectIfFindMeVehicle(ServerPlayer player, PlayerCompanionData data, Entity entity) {
      if (entity != null && data != null && !entity.isRemoved()) {
         Optional<SableVehicleCompatibility.Handle> sable = SableVehicleCompatibility.findForEntity(entity);
         if (sable.isPresent() && isVehicleEntry(data, sable.get().uuid())) {
            UUID uuid = sable.get().uuid();
            if (!SableVehicleCompatibility.store(player, data, sable.get())) {
               return false;
            }

            data.clearDeployedVehicle(uuid);
            save(player, data);
            syncToClient(player);
            return true;
         } else {
            if (!isVehicleEntry(data, entity.getUUID())) {
               return false;
            }

            UUID uuid = entity.getUUID();
            if (!storeVehicle(player, data, entity)) {
               return false;
            }

            data.clearDeployedVehicle(uuid);
            save(player, data);
            syncToClient(player);
            return true;
         }
      } else {
         return false;
      }
   }

   static Optional<Entity> locateEntity(MinecraftServer server, PlayerCompanionData data, UUID uuid) {
      Optional<Entity> loaded = findEntity(server, uuid);
      if (loaded.isPresent()) {
         return loaded;
      }

      Optional<SavedPosition> maybePosition = data.lastKnownPosition(uuid);
      if (maybePosition.isEmpty()) {
         return Optional.empty();
      }

      SavedPosition position = maybePosition.get();
      ServerLevel level = server.getLevel(position.dimension());
      if (level == null) {
         return Optional.empty();
      }

      level.getChunk(position.blockPos());
      return Optional.ofNullable(level.getEntity(uuid));
   }

   static Optional<Entity> findEntity(MinecraftServer server, UUID uuid) {
      for (ServerLevel level : server.getAllLevels()) {
         Entity entity = level.getEntity(uuid);
         if (entity != null && !entity.isRemoved()) {
            return Optional.of(entity);
         }
      }

      return Optional.empty();
   }

   static PlayerCompanionData data(ServerPlayer player) {
      PlayerCompanionDataMirror.restoreIfMissing(player);
      return PlayerCompanionData.load(com.kuzhi.findme.compat.PersistentData.of(player));
   }

   static void save(ServerPlayer player, PlayerCompanionData data) {
      data.save(com.kuzhi.findme.compat.PersistentData.of(player));
      PlayerCompanionDataMirror.remember(player);
   }

   private static boolean recallIndex(ServerPlayer player, PlayerCompanionData data, int index) {
      Optional<UUID> uuid = data.vehicleWheelUuidAt(index);
      if (uuid.isEmpty()) {
         tell(player, "message.find_me.invalid_vehicle_index", ChatFormatting.RED);
         return false;
      } else {
         cancelPendingSummon(player.getUUID(), uuid.get());
         Entity entity = locateEntity(player.level().getServer(), data, uuid.get()).orElse(null);
         return storeSelected(player, data, uuid.get(), entity);
      }
   }

   private static boolean storeSelected(ServerPlayer player, PlayerCompanionData data, UUID uuid, Entity entity) {
      cancelPendingSummon(player.getUUID(), uuid);
      Entity vehicle = entity == null ? locateEntity(player.level().getServer(), data, uuid).orElse(null) : entity;
      if (isSableVehicle(player, data, uuid)) {
         if (isSableActionCoolingDown(player)) {
            return false;
         }

         if (!SableVehicleCompatibility.snapshotAvailable()) {
            tell(player, "message.find_me.sable_blueprint_not_loaded", ChatFormatting.YELLOW);
            return false;
         }

         AABB sealBox = SableVehicleCompatibility.find(player, uuid).map(SableVehicleCompatibility.Handle::box).orElse(null);
         sendSablePreviewCapture(player, uuid, sealBox);
         if (!SableVehicleCompatibility.store(player, data, uuid)) {
            tell(player, "message.find_me.vehicle_store_failed", ChatFormatting.RED);
            return false;
         }

         sendSableVehicleSealEffect(player, sealBox);
         markSableActionCooldown(player);
      } else if (vehicle != null && !vehicle.isRemoved() && !storeVehicle(player, data, vehicle)) {
         tell(player, "message.find_me.vehicle_store_failed", ChatFormatting.RED);
         return false;
      }

      data.clearDeployedVehicle(uuid);
      save(player, data);
      tell(player, "message.find_me.vehicle_stored", ChatFormatting.GRAY, Component.literal(storedName(data, uuid)));
      return true;
   }

   private static boolean isSableVehicle(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
      return !SableVehicleCompatibility.available()
         ? false
         : SableVehicleCompatibility.find(player, uuid).isPresent() || SableVehicleCompatibility.isStored(data, uuid);
   }

   private static void removeAt(ServerPlayer player, PlayerCompanionData data, int index) {
      Optional<UUID> uuid;
      if (index >= 0) {
         uuid = data.vehicleWheelUuidAt(index);
      } else {
         int warehouseIndex = -index - 1;
         List<UUID> warehouse = data.vehicleWarehouseOrder();
         uuid = warehouseIndex >= 0 && warehouseIndex < warehouse.size() ? Optional.of(warehouse.get(warehouseIndex)) : Optional.empty();
      }

      if (uuid.isEmpty()) {
         tell(player, "message.find_me.invalid_vehicle_index", ChatFormatting.RED);
      } else {
         cancelPendingSummon(player.getUUID(), uuid.get());
         Entity entity = findEntity(player.level().getServer(), uuid.get()).orElse(null);
         removeUuid(player, data, uuid.get(), entity);
      }
   }

   private static void removeUuid(ServerPlayer player, PlayerCompanionData data, UUID uuid, Entity entity) {
      if (isSableVehicle(player, data, uuid)) {
         SableVehicleCompatibility.Handle handle = SableVehicleCompatibility.find(player, uuid)
            .orElseGet(() -> SableVehicleCompatibility.restore(player, data, uuid, findSableVehicleSpot(player, data, uuid, null)).orElse(null));
         if (handle == null) {
            tell(player, "message.find_me.unavailable_vehicle", ChatFormatting.RED);
         } else {
            UUID releaseUuid = handle.uuid();
            SableVehicleCompatibility.move(handle, player.level(), findSableVehicleSpot(player, data, uuid, handle));
            SableVehicleCompatibility.pushPlayerOut(player, handle);
            SableVehicleCompatibility.release(handle);
            sendSablePreviewDelete(player, uuid);
            if (releaseUuid != null && !releaseUuid.equals(uuid)) {
               sendSablePreviewDelete(player, releaseUuid);
            }

            data.remove(releaseUuid == null ? uuid : releaseUuid);
            save(player, data);
            tell(player, "message.find_me.vehicle_released", ChatFormatting.GRAY);
         }
      } else {
         Entity release = entity;
         if (release == null) {
            release = restoreVehicle(player, data, uuid, findVehicleSpotForStored(player, data, uuid, player.blockPosition())).orElse(null);
         }

         if (release != null && !release.isRemoved()) {
            moveVehicleTo(release, player.level(), findVehicleSpotForEntity(player, release, player.blockPosition()), player.getYRot(), player.getXRot());
         }

         data.remove(uuid);
         sendSablePreviewDelete(player, uuid);
         save(player, data);
         tell(player, "message.find_me.vehicle_released", ChatFormatting.GRAY);
      }
   }

   private static List<VehicleListPacket.Entry> entries(ServerPlayer player, PlayerCompanionData data, List<UUID> uuids) {
      ArrayList<VehicleListPacket.Entry> entries = new ArrayList<>();

      for (UUID uuid : uuids) {
         Entity entity = findEntity(player.level().getServer(), uuid).orElse(null);
         Optional<CompoundTag> storedTag = rawStoredTag(data, uuid);
         Optional<SableVehicleCompatibility.Handle> sable = SableVehicleCompatibility.find(player, uuid);
         boolean storedSable = storedTag.filter(SableVehicleCompatibility::isStoredSable).isPresent();
         String entityType = sable.isPresent() || storedSable
            ? "simulated:sable_sublevel"
            : (entity == null ? storedTag.<String>map(VehicleManager::storedType).orElse("") : entityType(entity));
         String name = data.displayName(uuid)
            .orElseGet(
               () -> sable.map(SableVehicleCompatibility.Handle::name)
                  .orElseGet(
                     () -> entity == null
                        ? storedTag.<String>map(VehicleManager::storedName).orElse(uuid.toString().substring(0, 8))
                        : entity.getDisplayName().getString()
                  )
            );
         boolean loaded = entity != null || sable.isPresent();
         boolean alive = sable.isPresent() || (entity == null ? storedTag.isPresent() : entity.isAlive());
         boolean ridden = player.getVehicle() != null && player.getVehicle().getUUID().equals(uuid);
         boolean deployed = ridden || data.isVehicleDeployed(uuid);
         CompoundTag previewTag = entity == null
            ? storedTag.<CompoundTag>map(tag -> storedSable ? null : tag.copy()).orElse(null)
            : CompanionManager.previewEntityTag(entity, entityType);
         entries.add(new VehicleListPacket.Entry(uuid, entity == null ? -1 : entity.getId(), entityType, name, loaded, alive, deployed, ridden, previewTag));
      }

      return entries;
   }

   private static boolean storeVehicle(ServerPlayer player, PlayerCompanionData data, Entity entity) {
      CompoundTag tag = new CompoundTag();
      if (!com.kuzhi.findme.compat.EntityNbt.save(entity, tag)) {
         return false;
      }

      resetStoredVehicleTags(tag);
      tag.putBoolean("FindMeVehicle", true);
      tag.putString("CompanionRescueName", entity.getDisplayName().getString());
      tag.putString("CompanionRescueType", entityType(entity));
      CompanionEntitySnapshots.writePreviewBounds(entity, tag);
      tag.putLong("CompanionRescueStoredAt", player.level().getGameTime());
      UUID uuid = entity.getUUID();
      data.storeEntity(uuid, tag);
      data.setDisplayName(uuid, entity.getDisplayName().getString());
      data.setLastKnownPosition(uuid, SavedPosition.of(entity.level(), entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot()));
      if (player.getVehicle() == entity) {
         player.stopRiding();
      }

      for (Entity passenger : new ArrayList<Entity>(entity.getPassengers())) {
         passenger.stopRiding();
      }

      sendVehicleSealEffect(player, entity);
      entity.discard();
      return true;
   }

   private static boolean scheduleStoredVehicleSummon(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
      Optional<CompoundTag> maybeTag = rawStoredTag(data, uuid);
      if (maybeTag.isEmpty()) {
         return false;
      }

      BlockPos pos = findVehicleSpotForStored(player, data, uuid, vehiclePlacementOrigin(player));
      if (maybeTag.filter(SableVehicleCompatibility::isStoredSable).isPresent()) {
         if (!SableVehicleCompatibility.canRestore(data, uuid)) {
            return false;
         }

         pos = findSableVehicleSpot(player, data, uuid, null);
      }

      Vec3 anchor = Vec3.atBottomCenterOf(pos);
      float radius = vehicleRadius(player, data, uuid, pos);
      float height = vehicleHeight(player, data, uuid, pos);
      sendVehicleSummonMagic(player, anchor, radius, height);
      int delay = CompanionArrivalMagicService.revealDelayTicks(36, RescueMagicPacket.Style.GROUND_CIRCLE, RescueMagicPacket.Purpose.SUMMON);
      cancelPendingSummons(player.getUUID());
      PENDING_SUMMONS.add(new VehicleManager.PendingVehicleSummon(player.getUUID(), uuid, pos, delay));
      return true;
   }

   private static Optional<Entity> restoreVehicle(ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos) {
      Optional<CompoundTag> maybeTag = rawStoredTag(data, uuid);
      if (maybeTag.isEmpty()) {
         return Optional.empty();
      }

      if (SableVehicleCompatibility.isStoredSable(maybeTag.get())) {
         return Optional.empty();
      }

      CompoundTag tag = maybeTag.get().copy();
      tag.store("UUID", net.minecraft.core.UUIDUtil.CODEC, uuid);
      Entity restored = EntityType.loadEntityRecursive(tag, player.level(), new net.minecraft.world.entity.EntitySpawnRequest(net.minecraft.world.entity.EntitySpawnReason.LOAD, false), entity -> {
         entity.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), player.getXRot());
         return entity;
      });
      if (restored == null) {
         return Optional.empty();
      }

      if (!player.level().addFreshEntity(restored)) {
         return Optional.empty();
      }

      data.removeStoredEntity(uuid);
      data.setLastKnownPosition(
         uuid, SavedPosition.of(restored.level(), restored.getX(), restored.getY(), restored.getZ(), restored.getYRot(), restored.getXRot())
      );
      save(player, data);
      return Optional.of(restored);
   }

   private static Entity moveVehicleTo(Entity entity, ServerLevel level, BlockPos pos, float yRot, float xRot) {
      Entity moved = entity;
      double x = pos.getX() + 0.5;
      double y = pos.getY();
      double z = pos.getZ() + 0.5;
      if (moved.level() != level) {
         Entity changed = moved.teleport(new TeleportTransition(level, new Vec3(x, y, z), Vec3.ZERO, yRot, xRot, TeleportTransition.DO_NOTHING));
         if (changed != null) {
            moved = changed;
         }
      }

      moved.teleportTo(x, y, z);
      moved.snapTo(x, y, z, yRot, xRot);
      moved.setPos(x, y, z);
      moved.setYRot(yRot);
      moved.setXRot(xRot);
      moved.setDeltaMovement(Vec3.ZERO);
      moved.fallDistance = 0.0F;
      return moved;
   }

   static void enforceSingleRideSlotForMount(ServerPlayer player, PlayerCompanionData data, UUID keepMountUuid, Entity previousRide) {
      collectPreviousRideForSharedSlot(player, data, keepMountUuid, previousRide);
      collectOtherVehiclesForSharedSlot(player, data, null, previousRide == null ? null : previousRide.getUUID());
   }

   private static void enforceSingleRideSlotForVehicle(ServerPlayer player, PlayerCompanionData data, UUID keepVehicleUuid, Entity previousRide) {
      collectPreviousRideForSharedSlot(player, data, keepVehicleUuid, previousRide);
      collectOtherVehiclesForSharedSlot(player, data, keepVehicleUuid, previousRide == null ? null : previousRide.getUUID());
      if (CompanionDeploymentService.collectOtherDeployed(player, data, CompanionKind.MOUNT, null)) {
         CompanionManager.syncToClient(player, CompanionKind.MOUNT);
      }
   }

   private static void collectPreviousRideForSharedSlot(ServerPlayer player, PlayerCompanionData data, UUID keepUuid, Entity previousRide) {
      UUID previousRideUuid = null;
      if (previousRide != null && !previousRide.isRemoved()) {
         Optional<SableVehicleCompatibility.Handle> sable = SableVehicleCompatibility.findForEntity(previousRide);
         if (sable.isPresent() && !sable.get().uuid().equals(keepUuid) && isVehicleEntry(data, sable.get().uuid())) {
            if (SableVehicleCompatibility.store(player, data, sable.get())) {
               data.clearDeployedVehicle(sable.get().uuid());
            }

            return;
         }

         previousRideUuid = previousRide.getUUID();
         if (!previousRideUuid.equals(keepUuid)) {
            if (isVehicleEntry(data, previousRideUuid)) {
               if (storeVehicle(player, data, previousRide)) {
                  data.clearDeployedVehicle(previousRideUuid);
               }
            } else if (!CompanionDeploymentService.retireMountAfterSharedRideSwitch(player, data, previousRide) && player.getVehicle() == previousRide) {
               player.stopRiding();
            }
         }
      }
   }

   private static void collectOtherVehiclesForSharedSlot(ServerPlayer player, PlayerCompanionData data, UUID keepVehicleUuid, UUID excludedUuid) {
      for (UUID otherVehicle : data.vehicleList()) {
         if (!otherVehicle.equals(keepVehicleUuid) && !otherVehicle.equals(excludedUuid)) {
            Optional<SableVehicleCompatibility.Handle> liveSable = SableVehicleCompatibility.find(player, otherVehicle);
            if (liveSable.isPresent()) {
               if (SableVehicleCompatibility.store(player, data, liveSable.get())) {
                  data.clearDeployedVehicle(otherVehicle);
               }
            } else {
               findEntity(player.level().getServer(), otherVehicle).ifPresent(entity -> {
                  if (storeVehicle(player, data, entity)) {
                     data.clearDeployedVehicle(otherVehicle);
                  }
               });
            }
         }
      }

      data.deployedVehicle().ifPresent(previous -> {
         if (!previous.equals(keepVehicleUuid) && !previous.equals(excludedUuid)) {
            if (isSableVehicle(player, data, previous)) {
               if (SableVehicleCompatibility.store(player, data, previous)) {
                  data.clearDeployedVehicle(previous);
               }
            } else {
               locateEntity(player.level().getServer(), data, previous).ifPresent(entity -> {
                  if (storeVehicle(player, data, entity)) {
                     data.clearDeployedVehicle(previous);
                  }
               });
            }
         }
      });
   }

   private static boolean isBindableVehicle(Entity entity) {
      return entity != null
         && !(entity instanceof Player)
         && !(entity instanceof LivingEntity)
         && !(entity instanceof ItemEntity)
         && !(entity instanceof ExperienceOrb)
         && !(entity instanceof Projectile);
   }

   private static Entity unwrapPart(Entity entity) {
      entity = unwrapKnownVehicleProxy(entity);
      if (Parts.isPart(entity)) {
         Entity parent = Parts.parentOf(entity);
         return parent == null ? entity : unwrapKnownVehicleProxy(parent);
      } else {
         return entity;
      }
   }

   private static Entity unwrapKnownVehicleProxy(Entity entity) {
      if (entity != null && isEntityType(entity, "automobility", "hitbox")) {
         try {
            Method method = entity.getClass().getMethod("automobile");
            if (method.invoke(entity) instanceof Entity vehicle && vehicle != entity && !vehicle.isRemoved()) {
               return vehicle;
            }
         } catch (ReflectiveOperationException | RuntimeException var4) {
         }

         return entity;
      } else {
         return entity;
      }
   }

   private static boolean isEntityType(Entity entity, String namespace, String path) {
      Identifier key = EntityType.getKey(entity.getType());
      return key != null && namespace.equals(key.getNamespace()) && path.equals(key.getPath());
   }

   private static void resetStoredVehicleTags(CompoundTag tag) {
      tag.putShort("Fire", (short)0);
      tag.putFloat("FallDistance", 0.0F);
      tag.remove("Motion");
      tag.remove("Passengers");
   }

   private static String storedName(PlayerCompanionData data, UUID uuid) {
      return data.displayName(uuid).orElseGet(() -> rawStoredTag(data, uuid).map(VehicleManager::storedName).orElse(uuid.toString().substring(0, 8)));
   }

   private static String storedName(CompoundTag tag) {
      String name = tag.getStringOr("CompanionRescueName", "");
      if (!name.isBlank()) {
         return name;
      }

      String type = storedType(tag);
      int separator = type.indexOf(58);
      return separator >= 0 && separator + 1 < type.length() ? type.substring(separator + 1) : type;
   }

   private static String storedType(PlayerCompanionData data, UUID uuid) {
      return rawStoredTag(data, uuid).map(VehicleManager::storedType).orElse("");
   }

   private static String storedType(CompoundTag tag) {
      String type = tag.getStringOr("CompanionRescueType", "");
      return type.isBlank() ? tag.getStringOr("id", "") : type;
   }

   private static Optional<CompoundTag> rawStoredTag(PlayerCompanionData data, UUID uuid) {
      return data != null && uuid != null ? Optional.ofNullable(data.storedEntities.get(uuid)) : Optional.empty();
   }

   private static String entityType(Entity entity) {
      Identifier key = EntityType.getKey(entity.getType());
      return key == null ? "" : key.toString();
   }

   private static void sendVehicleSealEffect(ServerPlayer player, Entity entity) {
      if (entity.level() instanceof ServerLevel level) {
         AABB var7 = entity.getBoundingBox();
         Vec3 base = new Vec3(var7.getCenter().x, var7.minY, var7.getCenter().z);
         float radius = vehicleRadius(entity);
         float height = vehicleHeight(entity);
         ModNetwork.sendToPlayersNear(level, base, 96.0, new VehicleSealEffectPacket(base.x, base.y, base.z, radius, height, 34));
         CompanionMagicAudioService.playStorageOpen(player, base, radius);
      }
   }

   private static void sendSableVehicleSealEffect(ServerPlayer player, AABB box) {
      if (player != null && box != null) {
         Vec3 base = new Vec3(box.getCenter().x, box.minY, box.getCenter().z);
         float radius = (float)Mth.clamp(Math.max(box.getXsize(), box.getZsize()) * 0.55 + 1.35, 1.4, 28.0);
         float height = (float)Mth.clamp(box.getYsize(), 1.0, 32.0);
         ModNetwork.sendToPlayersNear(player.level(), base, 96.0, new VehicleSealEffectPacket(base.x, base.y, base.z, radius, height, 34));
         CompanionMagicAudioService.playStorageOpen(player, base, radius);
      }
   }

   private static void sendSablePreviewCapture(ServerPlayer player, UUID uuid, AABB box) {
      if (player != null && uuid != null && box != null) {
         ModNetwork.sendToPlayer(player, new SableVehiclePreviewCapturePacket(uuid, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
      }
   }

   private static void sendSablePreviewDelete(ServerPlayer player, UUID uuid) {
      if (player != null && uuid != null) {
         ModNetwork.sendToPlayer(player, new SableVehiclePreviewDeletePacket(uuid));
      }
   }

   private static void sendSablePreviewTransfer(ServerPlayer player, UUID fromUuid, UUID toUuid) {
      if (player != null && fromUuid != null && toUuid != null && !fromUuid.equals(toUuid)) {
         ModNetwork.sendToPlayer(player, new SableVehiclePreviewTransferPacket(fromUuid, toUuid));
      }
   }

   private static void sendVehicleSummonMagic(ServerPlayer player, Vec3 anchor, float radius, float height) {
      float safeRadius = Math.max(1.2F, radius);
      float safeHeight = Math.max(1.0F, height);
      RescueMagicPacket packet = new RescueMagicPacket(
         anchor.x, anchor.y, anchor.z, safeRadius, safeHeight, player.getYRot(), 36, RescueMagicPacket.Style.GROUND_CIRCLE, RescueMagicPacket.Purpose.SUMMON
      );
      ModNetwork.sendToPlayersNear(player.level(), anchor, 96.0, packet);
      CompanionMagicAudioService.playCircleOpen(player, anchor, safeRadius, false);
   }

   private static float vehicleRadius(Entity entity) {
      return entity == null ? 1.4F : Mth.clamp(CompanionEntityVisualBoundsService.summonCircleRadius(entity), 1.4F, 22.0F);
   }

   private static float vehicleHeight(Entity entity) {
      return entity == null ? 1.2F : (float)Mth.clamp(CompanionEntityVisualBoundsService.visualBounds(entity).getYsize(), 1.0, 24.0);
   }

   private static float vehicleRadius(ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos) {
      Optional<CompoundTag> maybeTag = rawStoredTag(data, uuid);
      if (maybeTag.filter(SableVehicleCompatibility::isStoredSable).isPresent()) {
         CompoundTag tag = maybeTag.get();
         return Mth.clamp(Math.max(storedPreviewWidth(tag), storedPreviewDepth(tag)) * 0.55F + 1.35F, 1.4F, 28.0F);
      } else {
         Entity preview = previewStoredVehicle(player, data, uuid, pos).orElse(null);
         return preview != null
            ? vehicleRadius(preview)
            : rawStoredTag(data, uuid)
               .map(tag -> Mth.clamp(Math.max(storedPreviewWidth(tag), storedPreviewDepth(tag)) * 0.78F + 1.35F, 1.4F, 22.0F))
               .orElse(1.4F);
      }
   }

   private static float vehicleHeight(ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos) {
      Optional<CompoundTag> maybeTag = rawStoredTag(data, uuid);
      if (maybeTag.filter(SableVehicleCompatibility::isStoredSable).isPresent()) {
         return Mth.clamp(storedPreviewHeight(maybeTag.get()), 1.0F, 32.0F);
      }

      Entity preview = previewStoredVehicle(player, data, uuid, pos).orElse(null);
      return preview != null ? vehicleHeight(preview) : rawStoredTag(data, uuid).map(tag -> Mth.clamp(storedPreviewHeight(tag), 1.0F, 24.0F)).orElse(1.2F);
   }

   private static Optional<Entity> previewStoredVehicle(ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos) {
      Optional<CompoundTag> maybeTag = rawStoredTag(data, uuid);
      if (maybeTag.isEmpty()) {
         return Optional.empty();
      }

      if (SableVehicleCompatibility.isStoredSable(maybeTag.get())) {
         return Optional.empty();
      }

      CompoundTag tag = maybeTag.get().copy();
      tag.store("UUID", net.minecraft.core.UUIDUtil.CODEC, uuid);
      Entity preview = EntityType.loadEntityRecursive(tag, player.level(), new net.minecraft.world.entity.EntitySpawnRequest(net.minecraft.world.entity.EntitySpawnReason.LOAD, false), entity -> {
         entity.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), player.getXRot());
         return entity;
      });
      return Optional.ofNullable(preview);
   }

   private static float storedPreviewWidth(CompoundTag tag) {
      return tag.contains("CompanionPreviewWidth") ? tag.getFloatOr("CompanionPreviewWidth", 0.0F) : 1.0F;
   }

   private static float storedPreviewHeight(CompoundTag tag) {
      return tag.contains("CompanionPreviewHeight") ? tag.getFloatOr("CompanionPreviewHeight", 0.0F) : 1.0F;
   }

   private static float storedPreviewDepth(CompoundTag tag) {
      return tag.contains("CompanionPreviewDepth") ? tag.getFloatOr("CompanionPreviewDepth", 0.0F) : storedPreviewWidth(tag);
   }

   private static BlockPos findVehicleSpotForEntity(ServerPlayer player, Entity entity, BlockPos origin) {
      return findVehicleSpotForEntityNear(player, entity, origin);
   }

   private static BlockPos findVehicleSpotForEntityNear(ServerPlayer player, Entity entity, BlockPos origin) {
      if (entity == null) {
         return VehiclePlacementService.findVehicleSpotNear(player, origin, "");
      }

      AABB box = entity.getBoundingBox();
      double width = Math.max(1.0, box.getXsize());
      double height = Math.max(1.0, box.getYsize());
      double depth = Math.max(1.0, box.getZsize());
      return VehiclePlacementService.findVehicleSpotNear(player, origin, entityType(entity), width, height, depth);
   }

   private static BlockPos findVehicleSpotForStored(ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos origin) {
      Optional<CompoundTag> maybeTag = rawStoredTag(data, uuid);
      if (maybeTag.isEmpty()) {
         return VehiclePlacementService.findVehicleSpotNear(player, origin, storedType(data, uuid));
      }

      CompoundTag tag = maybeTag.get();
      return VehiclePlacementService.findVehicleSpotNear(
         player, origin, storedType(tag), storedPreviewWidth(tag), storedPreviewHeight(tag), storedPreviewDepth(tag)
      );
   }

   private static BlockPos findSableVehicleSpot(ServerPlayer player, PlayerCompanionData data, UUID uuid, SableVehicleCompatibility.Handle handle) {
      double width = 6.0;
      double height = 4.0;
      double depth = 6.0;
      if (handle != null) {
         AABB box = handle.box();
         width = Math.max(width, box.getXsize());
         height = Math.max(height, box.getYsize());
         depth = Math.max(depth, box.getZsize());
      } else {
         Optional<CompoundTag> tag = rawStoredTag(data, uuid);
         if (tag.isPresent()) {
            width = Math.max(width, storedPreviewWidth(tag.get()));
            height = Math.max(height, storedPreviewHeight(tag.get()));
            depth = Math.max(depth, storedPreviewDepth(tag.get()));
         }
      }

      BlockPos origin = vehiclePlacementOrigin(player);
      return VehiclePlacementService.findVehicleSpotNear(player, origin, "simulated:sable_sublevel", width, height, depth);
   }

   private static BlockPos vehiclePlacementOrigin(ServerPlayer player) {
      Entity ride = player.getVehicle();
      return ride != null && !ride.isRemoved() && ride.level() == player.level()
         ? BlockPos.containing(ride.getX(), ride.getY(), ride.getZ())
         : player.blockPosition();
   }

   private static void protectOldMountFromVehicleSwitch(ServerPlayer player, UUID mountUuid, UUID vehicleUuid) {
      if (player != null && mountUuid != null && vehicleUuid != null) {
         clearSwitchDamageProtection(player.getUUID(), mountUuid, vehicleUuid);
         SWITCH_DAMAGE_PROTECTIONS.add(new VehicleManager.VehicleSwitchDamageProtection(player.getUUID(), mountUuid, vehicleUuid, 80));
      }
   }

   private static void clearSwitchDamageProtection(UUID playerUuid, UUID mountUuid, UUID vehicleUuid) {
      SWITCH_DAMAGE_PROTECTIONS.removeIf(
         protection -> protection.playerUuid.equals(playerUuid) && protection.mountUuid.equals(mountUuid) && protection.vehicleUuid.equals(vehicleUuid)
      );
   }

   private static void cancelPendingSummon(UUID playerUuid, UUID vehicleUuid) {
      if (playerUuid != null && vehicleUuid != null) {
         PENDING_SUMMONS.removeIf(pending -> pending.playerUuid.equals(playerUuid) && pending.vehicleUuid.equals(vehicleUuid));
      }
   }

   private static void cancelPendingSummons(UUID playerUuid) {
      if (playerUuid != null) {
         PENDING_SUMMONS.removeIf(pending -> pending.playerUuid.equals(playerUuid));
      }
   }

   private static void tickSwitchDamageProtections() {
      Iterator<VehicleManager.VehicleSwitchDamageProtection> iterator = SWITCH_DAMAGE_PROTECTIONS.iterator();

      while (iterator.hasNext()) {
         VehicleManager.VehicleSwitchDamageProtection protection = iterator.next();
         protection.ticksLeft--;
         if (protection.ticksLeft <= 0) {
            iterator.remove();
         }
      }
   }

   private static boolean isSableActionCoolingDown(ServerPlayer player) {
      if (player == null) {
         return true;
      } else {
         long now = player.level().getGameTime();
         Long until = SABLE_ACTION_COOLDOWNS.get(player.getUUID());
         if (until != null && until > now) {
            tell(player, "message.find_me.vehicle_action_pending", ChatFormatting.YELLOW);
            return true;
         } else {
            SABLE_ACTION_COOLDOWNS.remove(player.getUUID());
            return false;
         }
      }
   }

   private static void markSableActionCooldown(ServerPlayer player) {
      markSableActionCooldown(player, 8);
   }

   private static void markSableActionCooldown(ServerPlayer player, int ticks) {
      if (player != null) {
         SABLE_ACTION_COOLDOWNS.put(player.getUUID(), player.level().getGameTime() + Math.max(1, ticks));
      }
   }

   private static void tickSableActionCooldowns(MinecraftServer server) {
      if (!SABLE_ACTION_COOLDOWNS.isEmpty() && server != null) {
         SABLE_ACTION_COOLDOWNS.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            return player == null || entry.getValue() <= player.level().getGameTime();
         });
      }
   }

   private static boolean isProtectedVehicleDamage(LivingEntity victim, DamageSource source, VehicleManager.VehicleSwitchDamageProtection protection) {
      Entity direct = source.getDirectEntity();
      Entity causing = source.getEntity();
      if (!isProtectedVehicleEntity(direct, protection.vehicleUuid) && !isProtectedVehicleEntity(causing, protection.vehicleUuid)) {
         if (causing instanceof ServerPlayer player && player.getUUID().equals(protection.playerUuid)) {
            Entity vehicle = player.getVehicle();
            return isProtectedVehicleEntity(vehicle, protection.vehicleUuid);
         } else if (direct instanceof ServerPlayer player && player.getUUID().equals(protection.playerUuid)) {
            Entity vehicle = player.getVehicle();
            return isProtectedVehicleEntity(vehicle, protection.vehicleUuid);
         } else if (direct == null && causing == null && victim.level().getServer() != null) {
            Entity vehicle = CompanionManager.findEntity(victim.level().getServer(), protection.vehicleUuid).orElse(null);
            return vehicle != null && vehicle.level() == victim.level() && vehicle.distanceToSqr(victim) <= 16.0;
         } else {
            return false;
         }
      } else {
         return true;
      }
   }

   private static boolean isProtectedVehicleEntity(Entity entity, UUID vehicleUuid) {
      if (entity != null && vehicleUuid != null) {
         if (entity.getUUID().equals(vehicleUuid)) {
            return true;
         }

         Entity root = entity.getRootVehicle();
         return root != null && root.getUUID().equals(vehicleUuid);
      } else {
         return false;
      }
   }

   private static void spawnBlinkParticles(Entity entity) {
      if (entity.level() instanceof ServerLevel level) {
         AABB var8 = entity.getBoundingBox();
         double width = Math.max(0.8, Math.max(var8.getXsize(), var8.getZsize()));
         double height = Math.max(0.6, var8.getYsize());
         int count = Mth.clamp((int)Math.round(24.0 + width * height * 10.0), 24, 160);
         level.sendParticles(
            ParticleTypes.PORTAL, entity.getX(), entity.getY() + height * 0.5, entity.getZ(), count, width * 0.55, height * 0.45, width * 0.55, 0.1
         );
      }
   }

   private static void tell(ServerPlayer player, String key, ChatFormatting color, Object... args) {
      player.sendOverlayMessage(Component.translatable(key, args).withStyle(color));
   }

   private static final class PendingVehicleSummon {
      private final UUID playerUuid;
      private final UUID vehicleUuid;
      private final BlockPos position;
      private final int delayTicks;
      private int age;

      private PendingVehicleSummon(UUID playerUuid, UUID vehicleUuid, BlockPos position, int delayTicks) {
         this.playerUuid = playerUuid;
         this.vehicleUuid = vehicleUuid;
         this.position = position;
         this.delayTicks = Math.max(1, delayTicks);
      }
   }

   private static final class VehicleSwitchDamageProtection {
      private final UUID playerUuid;
      private final UUID mountUuid;
      private final UUID vehicleUuid;
      private int ticksLeft;

      private VehicleSwitchDamageProtection(UUID playerUuid, UUID mountUuid, UUID vehicleUuid, int ticksLeft) {
         this.playerUuid = playerUuid;
         this.mountUuid = mountUuid;
         this.vehicleUuid = vehicleUuid;
         this.ticksLeft = ticksLeft;
      }
   }
}
