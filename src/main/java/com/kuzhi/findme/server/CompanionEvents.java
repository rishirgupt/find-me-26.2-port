package com.kuzhi.findme.server;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.ModItems;
import com.kuzhi.findme.common.NamePaperItem;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Game event hooks. Events Fabric has no callback for (taming, drops, falls, teleports) arrive through mixins. */
public final class CompanionEvents {
   private CompanionEvents() {
   }

   public static void register() {
      CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> CompanionCommands.register(dispatcher));
      ServerTickEvents.END_SERVER_TICK.register(CompanionEvents::onServerTick);
      EntitySleepEvents.STOP_SLEEPING.register((entity, sleepingPos) -> {
         if (entity instanceof ServerPlayer serverPlayer) {
            CompanionSafetyService.trySleepRevive(serverPlayer);
         }
      });
      UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> onEntityInteract(player, hand, entity));
      ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
         if (entity instanceof LivingEntity living) {
            CompanionExternalEntityReconciliationService.reconcileJoinedEntity(living);
         }
      });
      ServerEntityEvents.ENTITY_UNLOAD.register(CompanionEvents::onEntityLeaveLevel);
      ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> onPlayerTravel(player));
      ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
         ServerPlayer player = handler.getPlayer();
         CompanionManager.collectDeployedForLogout(player);
         VehicleManager.collectDeployed(player);
         CompanionManager.forgetClientSyncCache(player);
      });
      ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
         ServerPlayer player = handler.getPlayer();
         PlayerCompanionDataMirror.restoreIfMissing(player);
         syncAll(player);
      });
      ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
         if (PlayerCompanionDataMirror.copyCloneData(oldPlayer, newPlayer)) {
            syncAll(newPlayer);
         }
      });
      ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> onLivingDeath(entity));
      ServerLivingEntityEvents.ALLOW_DAMAGE.register(CompanionEvents::onLivingHurt);
   }

   private static void syncAll(ServerPlayer player) {
      CompanionManager.syncToClient(player, CompanionKind.MOUNT);
      CompanionManager.syncToClient(player, CompanionKind.COMPANION);
      CompanionManager.syncDeadToClient(player);
      VehicleManager.syncToClient(player);
   }

   private static void onServerTick(MinecraftServer server) {
      CompanionManager.tickPendingMounts(server);
      CompanionContractService.tick(server);
      CompanionManager.tickArrivalSequences(server);
      CompanionManager.tickCombatRescues(server);
      CompanionManager.tickStorageEffects(server);
      VehicleManager.tickPendingSummons(server);

      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         onPlayerTick(player);
      }
   }

   private static void onPlayerTick(ServerPlayer player) {
      CompanionManager.promoteRiddenRegisteredCompanionToMount(player);
      if (Config.enableAutoBinding) {
         CompanionManager.autoRegisterRiddenMount(player);
         CompanionManager.autoRegisterNearbyOwnedEntities(player);
      }

      CompanionManager.tickPlayerSafety(player);
      CompanionManager.tickHomeResidents(player);
   }

   private static InteractionResult onEntityInteract(Player player, InteractionHand hand, Entity target) {
      ItemStack stack = player.getItemInHand(hand);
      if (stack.is(ModItems.VEHICLE_BINDER.get())) {
         if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
         } else if (player instanceof ServerPlayer serverPlayer) {
            return VehicleManager.bindVehicle(serverPlayer, target, hand) ? InteractionResult.CONSUME : InteractionResult.FAIL;
         } else {
            return InteractionResult.FAIL;
         }
      } else if (stack.is(ModItems.NAME_PAPER.get()) && target instanceof LivingEntity living) {
         if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
         } else if (player instanceof ServerPlayer serverPlayer) {
            boolean started = CompanionContractService.start(serverPlayer, living);
            if (started) {
               player.getCooldowns().addCooldown(stack, 80);
               NamePaperItem.consumeOne(stack, player);
            }

            return started ? InteractionResult.CONSUME : InteractionResult.FAIL;
         } else {
            return InteractionResult.FAIL;
         }
      } else {
         return InteractionResult.PASS;
      }
   }

   /** A player tamed {@code animal} (called from a mixin). */
   public static void onAnimalTame(Player tamer, LivingEntity animal) {
      if (tamer instanceof ServerPlayer player && Config.enableAutoBinding) {
         CompanionManager.autoRegisterTamedCompanion(player, animal);
      }
   }

   /** A player is about to teleport or change dimension (called from a mixin), or has just changed level. */
   public static void onPlayerTravel(ServerPlayer player) {
      CompanionManager.collectDeployedForTravel(player);
      VehicleManager.collectDeployed(player);
   }

   private static void onEntityLeaveLevel(Entity entity, ServerLevel level) {
      MinecraftServer server = level.getServer();
      if (entity instanceof LivingEntity living) {
         if (CompanionManager.consumeIntentionalTransferRemoval(server, living.getUUID())) {
            return;
         }

         if (CompanionManager.consumeIntentionalStorageRemoval(server, living.getUUID())) {
            return;
         }

         if (CompanionManager.markRegisteredDead(server, living)) {
            return;
         }
      }

      CompanionManager.snapshotRegisteredBeforeUnload(server, entity);
      if (entity instanceof LivingEntity living) {
         CompanionExternalEntityReconciliationService.reconcileAfterRegisteredEntityLeave(server, living);
      }
   }

   /** @return false to keep the entity alive */
   private static boolean onLivingDeath(LivingEntity livingEntity) {
      if (livingEntity instanceof ServerPlayer player) {
         CompanionManager.collectDeployedOnDeath(player);
         VehicleManager.collectDeployed(player);
         return true;
      } else if (CompanionManager.isStoragePending(livingEntity)) {
         return false;
      } else {
         CompanionManager.markRegisteredDead(livingEntity.level().getServer(), livingEntity, true);
         return true;
      }
   }

   /** Whether the death drops of {@code livingEntity} should be withheld (asked from a mixin). */
   public static boolean suppressDeathDrops(LivingEntity livingEntity) {
      return Config.preventBoundCreatureDeathDrops && CompanionDeathService.shouldSuppressDeathDrops(livingEntity);
   }

   /** @return false to cancel the damage */
   private static boolean onLivingHurt(LivingEntity entity, DamageSource source, float amount) {
      if (VehicleManager.shouldCancelSwitchVehicleDamage(entity, source)) {
         return false;
      } else {
         CompanionThreatMemoryService.remember(entity, source);
         return true;
      }
   }

   /** Whether fall damage should be skipped: riders, and mounts carrying a player, never take it (asked from a mixin). */
   public static boolean cancelFall(LivingEntity entity) {
      if (entity instanceof ServerPlayer player && player.getVehicle() != null) {
         return true;
      } else {
         return entity.getPassengers().stream().anyMatch(ServerPlayer.class::isInstance);
      }
   }
}
