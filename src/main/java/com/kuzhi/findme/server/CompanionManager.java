package com.kuzhi.findme.server;

import com.kuzhi.findme.common.CompanionAction;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.ModBlocks;
import com.kuzhi.findme.common.SavedPosition;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public final class CompanionManager {
   private CompanionManager() {
   }

   public static void bootstrap() {
   }

   public static void handle(ServerPlayer player, CompanionKind kind, CompanionAction action, int targetEntityId) {
      CompanionCommandHandler.handle(player, kind, action, targetEntityId);
   }

   public static void syncToClient(ServerPlayer player, CompanionKind kind) {
      CompanionSyncService.syncToClient(player, kind);
   }

   public static void syncDeadToClient(ServerPlayer player) {
      CompanionSyncService.syncDeadToClient(player);
   }

   public static void forgetClientSyncCache(ServerPlayer player) {
      CompanionSyncService.forgetPlayer(player);
   }

   public static int list(ServerPlayer player, CompanionKind kind) {
      return CompanionListService.list(player, kind);
   }

   static CompoundTag previewEntityTag(Entity entity, String entityType) {
      return CompanionEntitySnapshots.previewEntityTag(entity, entityType);
   }

   static void writePreviewBounds(Entity entity, CompoundTag tag) {
      CompanionEntitySnapshots.writePreviewBounds(entity, tag);
   }

   public static boolean markRegisteredDead(MinecraftServer server, LivingEntity living) {
      return CompanionDeathService.markRegisteredDead(server, living);
   }

   public static boolean markRegisteredDead(MinecraftServer server, LivingEntity living, boolean confirmedDeath) {
      return CompanionDeathService.markRegisteredDead(server, living, confirmedDeath);
   }

   public static boolean consumeIntentionalStorageRemoval(MinecraftServer server, UUID uuid) {
      return CompanionStorageService.consumeIntentionalStorageRemoval(server, uuid);
   }

   public static boolean consumeIntentionalTransferRemoval(MinecraftServer server, UUID uuid) {
      return CompanionEntityTransferService.consumeIntentionalTransferRemoval(server, uuid);
   }

   public static int select(ServerPlayer player, CompanionKind kind, int index) {
      return CompanionListService.select(player, kind, index);
   }

   public static int remove(ServerPlayer player, CompanionKind kind, int index) {
      return CompanionListService.remove(player, kind, index);
   }

   public static int assignWheelSlot(ServerPlayer player, CompanionKind kind, int index, int slot) {
      return CompanionListService.assignWheelSlot(player, kind, index, slot);
   }

   public static void rename(ServerPlayer player, CompanionKind kind, int index, String name) {
      CompanionListService.rename(player, kind, index, name);
   }

   public static int summon(ServerPlayer player, CompanionKind kind) {
      CompanionSummonService.summonActive(player, data(player), kind);
      return 1;
   }

   public static int returnHome(ServerPlayer player, CompanionKind kind) {
      CompanionEntityTransferService.returnActive(player, data(player), kind);
      return 1;
   }

   public static int setHome(ServerPlayer player, CompanionKind kind) {
      return CompanionHomeService.setActiveHome(player, data(player), kind);
   }

   public static int goHome(ServerPlayer player, CompanionKind kind) {
      return CompanionHomeService.sendActiveHome(player, data(player), kind);
   }

   public static int clearHome(ServerPlayer player, CompanionKind kind) {
      return CompanionHomeService.clearActiveHome(player, data(player), kind);
   }

   public static void handleHouseHome(
      ServerPlayer player, CompanionKind kind, CompanionAction action, int index, BlockPos housePos, Identifier dimensionId
   ) {
      if (player != null && housePos != null && dimensionId != null) {
         if (action == CompanionAction.SET_HOME || action == CompanionAction.GO_HOME || action == CompanionAction.CLEAR_HOME) {
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
            ServerLevel houseLevel = player.level().getServer().getLevel(dimension);
            if (houseLevel != null && houseLevel.isLoaded(housePos) && houseLevel.getBlockState(housePos).is((Block)ModBlocks.SMALL_HOUSE.get())) {
               PlayerCompanionData data = data(player);
               switch (action) {
                  case SET_HOME:
                     CompanionHomeService.setIndexHomeAtHouse(player, data, kind, index, houseLevel, housePos);
                     break;
                  case GO_HOME:
                     CompanionHomeService.sendIndexHome(player, data, kind, index);
                     break;
                  case CLEAR_HOME:
                     CompanionHomeService.clearIndexHome(player, data, kind, index);
               }
            } else {
               tell(player, "message.find_me.house_missing", ChatFormatting.YELLOW);
            }
         }
      }
   }

   public static void clearHomesForHouse(ServerLevel level, BlockPos housePos) {
      if (level != null && housePos != null && level.getServer() != null) {
         SavedPosition source = SavedPosition.of(level, housePos.getX(), housePos.getY(), housePos.getZ(), 0.0F, 0.0F);

         for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            PlayerCompanionData data = data(player);
            CompanionHomeResidentService.collectResidentsForBrokenHouse(player, data, source);
            CompanionHomeService.clearHomesForHouse(player, data, source);
         }
      }
   }

   public static int listIgnored(ServerPlayer player) {
      return CompanionIgnoreService.listIgnored(player);
   }

   public static void syncIgnoredToClient(ServerPlayer player) {
      CompanionIgnoreService.syncIgnoredToClient(player);
   }

   public static void syncBlacklistToClient(ServerPlayer player) {
      CompanionIgnoreService.syncBlacklistToClient(player);
   }

   public static int blacklistEntryType(ServerPlayer player, CompanionKind kind, int index) {
      return CompanionIgnoreService.blacklistEntryType(player, kind, index);
   }

   public static int removeIgnored(ServerPlayer player, int index) {
      return CompanionIgnoreService.removeIgnored(player, index);
   }

   public static int removeBlacklist(ServerPlayer player, int index) {
      return CompanionIgnoreService.removeBlacklist(player, index);
   }

   public static int clearIgnored(ServerPlayer player) {
      return CompanionIgnoreService.clearIgnored(player);
   }

   public static int listVault(ServerPlayer player) {
      return CompanionSafetyService.listVault(player);
   }

   public static int restoreVault(ServerPlayer player, int index) {
      return CompanionSafetyService.restoreVault(player, index);
   }

   public static int clearVault(ServerPlayer player) {
      return CompanionSafetyService.clearVault(player);
   }

   public static int listBackups(ServerPlayer player) {
      return CompanionSafetyService.listBackups(player);
   }

   public static int createBackup(ServerPlayer player, String reason) {
      return CompanionSafetyService.createBackup(player, reason);
   }

   public static int restoreBackup(ServerPlayer player, int index) {
      return CompanionSafetyService.restoreBackup(player, index);
   }

   public static void tickPlayerSafety(ServerPlayer player) {
      CompanionSafetyService.tickPlayerSafety(player);
   }

   public static void tickHomeResidents(ServerPlayer player) {
      CompanionHomeResidentService.tickPlayer(player);
   }

   private static void collectActive(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
      CompanionCollectionService.collectActive(player, data, kind);
   }

   static boolean collectActiveShoulderCompanion(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
      return CompanionCollectionService.collectActiveShoulderCompanion(player, data, uuid);
   }

   public static void collectDeployedOnDeath(ServerPlayer player) {
      CompanionCollectionService.collectDeployedOnDeath(player);
   }

   public static void collectDeployedForTravel(ServerPlayer player) {
      CompanionCollectionService.collectDeployedForTravel(player);
   }

   public static void collectDeployedForLogout(ServerPlayer player) {
      CompanionCollectionService.collectDeployedForLogout(player);
   }

   public static void snapshotRegisteredBeforeUnload(MinecraftServer server, Entity entity) {
      CompanionCollectionService.snapshotRegisteredBeforeUnload(server, entity);
   }

   public static void autoRegisterRiddenMount(ServerPlayer player) {
      CompanionRegistrationService.autoRegisterRiddenMount(player);
   }

   public static void promoteRiddenRegisteredCompanionToMount(ServerPlayer player) {
      CompanionRegistrationService.promoteRiddenRegisteredCompanionToMount(player);
   }

   public static void autoRegisterNearbyOwnedEntities(ServerPlayer player) {
      CompanionRegistrationService.autoRegisterNearbyOwnedEntities(player);
   }

   public static void autoRegisterTamedCompanion(ServerPlayer player, LivingEntity living) {
      CompanionRegistrationService.autoRegisterTamedCompanion(player, living);
   }

   public static int tameTarget(ServerPlayer player, CompanionKind kind) {
      return CompanionRegistrationService.tameTarget(player, kind);
   }

   static Optional<CompoundTag> shoulderEntityTag(ServerPlayer player, UUID uuid) {
      return CompanionShoulderService.shoulderEntityTag(player, uuid);
   }

   static boolean clearPlayerShoulderEntity(ServerPlayer player, UUID uuid) {
      return CompanionShoulderService.clearPlayerShoulderEntity(player, uuid);
   }

   static Optional<LivingEntity> releaseShoulderCompanion(ServerPlayer player, UUID uuid, CompoundTag source) {
      return CompanionShoulderService.releaseShoulderCompanion(player, uuid, source);
   }

   static void enforceCompanionDeploymentLimit(ServerPlayer player, PlayerCompanionData data, UUID keepUuid) {
      ArrayList<UUID> deployed = new ArrayList<>(data.deployedList(CompanionKind.COMPANION));

      while (deployed.size() > 2) {
         UUID oldest = deployed.remove(0);
         if (oldest.equals(keepUuid) && !deployed.isEmpty()) {
            deployed.add(oldest);
            oldest = deployed.remove(0);
         }

         LivingEntity living;
         Entity entity;
         if ((entity = locateEntity(player.level().getServer(), data, oldest).orElse(null)) instanceof LivingEntity && (living = (LivingEntity)entity).isAlive()) {
            CompanionDeploymentService.collectLiving(player, data, CompanionKind.COMPANION, living);
         } else {
            Optional<CompoundTag> shoulderTag = shoulderEntityTag(player, oldest);
            if (shoulderTag.isPresent()) {
               data.storeEntity(oldest, storedShoulderTag(player, oldest, shoulderTag.get()));
               clearPlayerShoulderEntity(player, oldest);
            }
         }

         data.clearDeployed(CompanionKind.COMPANION, oldest);
      }
   }

   public static void tickPendingMounts(MinecraftServer server) {
      CompanionMountCinematicFlowService.tickMountCinematics(server);
      CompanionRetreatService.tickRetreats(server);
      CompanionMountSettleProtectionService.tickSettledMountProtections(server);
   }

   static boolean isMountSettleProtected(ServerPlayer player) {
      return CompanionMountSettleProtectionService.isMountSettleProtected(player);
   }

   static CompanionMoveType moveType(Entity entity, CompanionKind kind) {
      return CompanionEntityClassifier.moveType(entity, kind);
   }

   static CompanionMoveType moveType(String entityType, CompanionKind kind) {
      return CompanionEntityClassifier.moveType(entityType, kind);
   }

   static String storedEntityName(CompoundTag tag, UUID uuid) {
      return CompanionEntitySnapshots.storedEntityName(tag, uuid);
   }

   static String storedEntityType(CompoundTag tag) {
      return CompanionEntitySnapshots.storedEntityType(tag);
   }

   static boolean isOwnedBy(ServerPlayer player, Entity entity) {
      return CompanionEntityClassifier.isOwnedBy(player, entity);
   }

   static Optional<Entity> findEntity(MinecraftServer server, UUID uuid) {
      return CompanionEntityLookup.findEntity(server, uuid);
   }

   static Optional<Entity> locateEntity(MinecraftServer server, PlayerCompanionData data, UUID uuid) {
      return CompanionEntityLookup.locateEntity(server, data, uuid);
   }

   static Optional<Entity> findLoadedEntity(MinecraftServer server, PlayerCompanionData data, UUID uuid) {
      return CompanionEntityLookup.findLoadedEntity(server, data, uuid);
   }

   static void sendAway(Entity entity, ServerPlayer player, PlayerCompanionData data) {
      CompanionRetreatService.sendAway(entity, player, data);
   }

   static void storeAndDiscard(ServerPlayer player, PlayerCompanionData data, LivingEntity living) {
      CompanionStorageService.storeAndDiscard(player, data, living);
   }

   static void tickStorageEffects(MinecraftServer server) {
      CompanionStorageService.tickStorageEffects(server);
   }

   static boolean isStoragePending(LivingEntity living) {
      return CompanionStorageService.isStoragePending(living);
   }

   static boolean isStoragePending(UUID uuid) {
      return CompanionStorageService.isStoragePending(uuid);
   }

   static boolean isHomeResident(UUID uuid) {
      return CompanionHomeResidentService.isResident(uuid);
   }

   static void clearHomeResident(UUID uuid) {
      CompanionHomeResidentService.clearResident(uuid);
   }

   static void clearHomeResident(LivingEntity living) {
      CompanionHomeResidentService.clearResident(living);
   }

   static boolean sendHomeIfPossible(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, LivingEntity living) {
      return CompanionHomeResidentService.sendHomeIfPossible(player, data, kind, living);
   }

   static void storeEntity(ServerPlayer player, PlayerCompanionData data, LivingEntity living) {
      CompanionStorageService.storeEntity(player, data, living);
   }

   static void snapshotEntity(ServerPlayer player, PlayerCompanionData data, LivingEntity living) {
      CompanionStorageService.snapshotEntity(player, data, living);
   }

   static CompoundTag storedShoulderTag(ServerPlayer player, UUID uuid, CompoundTag source) {
      return CompanionStorageService.storedShoulderTag(player, uuid, source);
   }

   static void spawnBlinkParticles(LivingEntity living) {
      if (living.level() instanceof ServerLevel level2) {
         double width = Math.max(0.8, living.getBbWidth());
         double height = Math.max(1.0, living.getBbHeight());
         double size = Math.max(width, height * 0.55);
         int count = Mth.clamp((int)Math.round(42.0 + size * size * 16.0), 48, 320);
         level2.sendParticles(
            ParticleTypes.PORTAL, living.getX(), living.getY() + height * 0.5, living.getZ(), count, width * 0.65, height * 0.45, width * 0.65, 0.16
         );
      }
   }

   static void tickArrivalSequences(MinecraftServer server) {
      CompanionArrivalSequenceService.tick(server);
   }

   static void tickCombatRescues(MinecraftServer server) {
      CompanionCombatRescueService.tick(server);
   }

   static boolean isArrivalPending(LivingEntity living) {
      return CompanionArrivalSequenceService.isPending(living);
   }

   static Optional<Boolean> arrivalOriginalNoAi(LivingEntity living) {
      return CompanionArrivalSequenceService.originalNoAi(living);
   }

   static void cancelArrivalSequence(LivingEntity living) {
      CompanionArrivalSequenceService.cancel(living);
   }

   static void restoreAnimationControl(LivingEntity living) {
      CompanionAnimationHelper.restoreAnimationControl(living);
   }

   static void forceFlyingAnimationPose(LivingEntity living) {
      CompanionAnimationHelper.forceFlyingAnimationPose(living);
   }

   static boolean keepsFlyingAnimationWithActiveAi(LivingEntity living) {
      return CompanionAnimationHelper.keepsFlyingAnimationWithActiveAi(living);
   }

   static CompoundTag healedStoredEntity(ServerPlayer player, PlayerCompanionData data, UUID uuid, CompoundTag storedTag) {
      return CompanionStorageService.healedStoredEntity(player, data, uuid, storedTag);
   }

   static PlayerCompanionData data(ServerPlayer player) {
      PlayerCompanionDataMirror.restoreIfMissing(player);
      return PlayerCompanionData.load(com.kuzhi.findme.compat.PersistentData.of(player));
   }

   static void save(ServerPlayer player, PlayerCompanionData data) {
      data.save(com.kuzhi.findme.compat.PersistentData.of(player));
      PlayerCompanionDataMirror.remember(player);
   }

   static void tell(ServerPlayer player, String key, ChatFormatting color, Object... args) {
      player.sendOverlayMessage(Component.translatable(key, args).withStyle(color));
   }

   static Component label(CompanionKind kind) {
      return Component.translatable(kind == CompanionKind.MOUNT ? "message.find_me.kind_mount" : "message.find_me.kind_companion");
   }

   static CompanionKind other(CompanionKind kind) {
      return kind == CompanionKind.MOUNT ? CompanionKind.COMPANION : CompanionKind.MOUNT;
   }
}
