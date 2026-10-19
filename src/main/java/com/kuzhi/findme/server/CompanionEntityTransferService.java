package com.kuzhi.findme.server;

import com.kuzhi.findme.FindMe;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.network.RescueMagicPacket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

final class CompanionEntityTransferService {
   private static final Map<UUID, Long> INTENTIONAL_TRANSFERS = new HashMap<>();

   private CompanionEntityTransferService() {
   }

   static boolean consumeIntentionalTransferRemoval(MinecraftServer server, UUID uuid) {
      if (server != null && uuid != null) {
         long now = server.overworld().getGameTime();
         Iterator<Entry<UUID, Long>> iterator = INTENTIONAL_TRANSFERS.entrySet().iterator();

         while (iterator.hasNext()) {
            Entry<UUID, Long> entry = iterator.next();
            if (entry.getValue() < now) {
               iterator.remove();
            }
         }

         Long expiresAt = INTENTIONAL_TRANSFERS.remove(uuid);
         return expiresAt != null && expiresAt >= now;
      } else {
         return false;
      }
   }

   static void returnActive(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
      CompanionHomeService.returnActive(player, data, kind);
   }

   static Optional<Entity> restoreStoredEntity(ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot) {
      return restoreStoredEntity(player.level(), player, data, uuid, pos, yRot, xRot, false);
   }

   static Optional<Entity> restoreStoredEntity(
      ServerLevel level, ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot
   ) {
      return restoreStoredEntity(level, player, data, uuid, pos, yRot, xRot, false);
   }

   static Optional<Entity> restoreStoredEntityFresh(
      ServerLevel level, ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot
   ) {
      return restoreStoredEntity(
         level,
         player,
         data,
         uuid,
         pos,
         yRot,
         xRot,
         false,
         player.position(),
         42,
         RescueMagicPacket.Style.VERTICAL_PORTAL,
         RescueMagicPacket.Purpose.SUMMON,
         false,
         true
      );
   }

   static Optional<Entity> restoreStoredEntityFreshForArrival(
      ServerLevel level,
      ServerPlayer player,
      PlayerCompanionData data,
      UUID uuid,
      BlockPos pos,
      float yRot,
      float xRot,
      Vec3 focus,
      int durationTicks,
      RescueMagicPacket.Style style,
      RescueMagicPacket.Purpose purpose
   ) {
      return restoreStoredEntity(level, player, data, uuid, pos, yRot, xRot, true, focus, durationTicks, style, purpose, false, true);
   }

   static Optional<Entity> restoreStoredEntityForArrival(ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot) {
      return restoreStoredEntity(player.level(), player, data, uuid, pos, yRot, xRot, true);
   }

   static Optional<Entity> restoreStoredEntityForArrival(
      ServerLevel level,
      ServerPlayer player,
      PlayerCompanionData data,
      UUID uuid,
      BlockPos pos,
      float yRot,
      float xRot,
      Vec3 focus,
      int durationTicks,
      RescueMagicPacket.Style style,
      RescueMagicPacket.Purpose purpose
   ) {
      return restoreStoredEntity(level, player, data, uuid, pos, yRot, xRot, true, focus, durationTicks, style, purpose);
   }

   static Optional<Entity> restoreStoredEntityForArrival(
      ServerPlayer player,
      PlayerCompanionData data,
      UUID uuid,
      BlockPos pos,
      float yRot,
      float xRot,
      Vec3 focus,
      int durationTicks,
      RescueMagicPacket.Style style
   ) {
      return restoreStoredEntityForArrival(player, data, uuid, pos, yRot, xRot, focus, durationTicks, style, RescueMagicPacket.Purpose.SUMMON);
   }

   static Optional<Entity> restoreStoredEntityForArrival(
      ServerPlayer player,
      PlayerCompanionData data,
      UUID uuid,
      BlockPos pos,
      float yRot,
      float xRot,
      Vec3 focus,
      int durationTicks,
      RescueMagicPacket.Style style,
      RescueMagicPacket.Purpose purpose
   ) {
      return restoreStoredEntity(player, data, uuid, pos, yRot, xRot, true, focus, durationTicks, style, purpose);
   }

   private static Optional<Entity> restoreStoredEntity(
      ServerLevel level, ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot, boolean prepareArrival
   ) {
      return restoreStoredEntity(
         level,
         player,
         data,
         uuid,
         pos,
         yRot,
         xRot,
         prepareArrival,
         player.position(),
         42,
         RescueMagicPacket.Style.VERTICAL_PORTAL,
         RescueMagicPacket.Purpose.SUMMON
      );
   }

   private static Optional<Entity> restoreStoredEntity(
      ServerPlayer player,
      PlayerCompanionData data,
      UUID uuid,
      BlockPos pos,
      float yRot,
      float xRot,
      boolean prepareArrival,
      Vec3 focus,
      int durationTicks,
      RescueMagicPacket.Style style,
      RescueMagicPacket.Purpose purpose
   ) {
      return restoreStoredEntity(player.level(), player, data, uuid, pos, yRot, xRot, prepareArrival, focus, durationTicks, style, purpose);
   }

   private static Optional<Entity> restoreStoredEntity(
      ServerLevel level,
      ServerPlayer player,
      PlayerCompanionData data,
      UUID uuid,
      BlockPos pos,
      float yRot,
      float xRot,
      boolean prepareArrival,
      Vec3 focus,
      int durationTicks,
      RescueMagicPacket.Style style,
      RescueMagicPacket.Purpose purpose
   ) {
      return restoreStoredEntity(level, player, data, uuid, pos, yRot, xRot, prepareArrival, focus, durationTicks, style, purpose, true, false);
   }

   private static Optional<Entity> restoreStoredEntity(
      ServerLevel level,
      ServerPlayer player,
      PlayerCompanionData data,
      UUID uuid,
      BlockPos pos,
      float yRot,
      float xRot,
      boolean prepareArrival,
      Vec3 focus,
      int durationTicks,
      RescueMagicPacket.Style style,
      RescueMagicPacket.Purpose purpose,
      boolean reuseLoadedEntity,
      boolean allowWhileStoragePending
   ) {
      Optional<CompoundTag> maybeTag = data.storedEntity(uuid);
      if (maybeTag.isEmpty()) {
         return Optional.empty();
      }

      if (!allowWhileStoragePending && CompanionManager.isStoragePending(uuid)) {
         FindMe.LOGGER.warn("FindMe blocked restore for companion {} while its storage animation is still pending. Stored data was kept.", uuid);
         return Optional.empty();
      }

      if (reuseLoadedEntity) {
         Optional<Entity> alreadyLoaded = CompanionManager.findEntity(player.level().getServer(), uuid);
         if (alreadyLoaded.isPresent()) {
            Entity existing = alreadyLoaded.get();
            Entity moved = moveLoadedDuplicateInsteadOfRestoring(player, existing, level, pos, yRot, xRot, prepareArrival, focus, durationTicks, style, purpose);
            data.removeStoredEntity(uuid);
            data.setLastKnownPosition(uuid, SavedPosition.of(moved.level(), moved.getX(), moved.getY(), moved.getZ(), moved.getYRot(), moved.getXRot()));
            CompanionManager.save(player, data);
            FindMe.LOGGER
               .warn("FindMe found a live entity while restoring stored companion {}; reused the live entity and removed the stale stored copy.", uuid);
            return Optional.of(moved);
         }
      }

      CompoundTag tag = CompanionStorageService.healedStoredEntity(player, data, uuid, maybeTag.get());
      tag.store("UUID", net.minecraft.core.UUIDUtil.CODEC, uuid);
      Entity restored = EntityType.loadEntityRecursive(tag, level, new net.minecraft.world.entity.EntitySpawnRequest(net.minecraft.world.entity.EntitySpawnReason.LOAD, false), entity -> {
         entity.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yRot, xRot);
         return entity;
      });
      if (restored == null) {
         return Optional.empty();
      }

      boolean originalNoAi = false;
      if (restored instanceof Mob mob) {
         mob.setNoAi(false);
      }

      boolean added = level.addFreshEntity(restored);
      if (!added) {
         FindMe.LOGGER
            .error("FindMe failed to restore stored companion {} because Minecraft rejected the entity add. Stored data was kept for recovery.", uuid);
         return Optional.empty();
      }

      if (restored instanceof LivingEntity living) {
         if (prepareArrival) {
            CompanionArrivalMagicService.openAt(player, living, restored.position(), focus, durationTicks, style, purpose);
            CompanionArrivalSequenceService.startWithCapturedStateAndDelay(
               player,
               living,
               focus == null ? player.position() : focus,
               purpose,
               originalNoAi,
               CompanionArrivalMagicService.revealDelayTicks(durationTicks, style, purpose)
            );
         }

         if (!prepareArrival) {
            CompanionManager.restoreAnimationControl(living);
         }

         if (!prepareArrival && !CompanionManager.isArrivalPending(living)) {
            CompanionManager.spawnBlinkParticles(living);
         }
      }

      data.removeStoredEntity(uuid);
      data.setLastKnownPosition(
         uuid, SavedPosition.of(restored.level(), restored.getX(), restored.getY(), restored.getZ(), restored.getYRot(), restored.getXRot())
      );
      CompanionManager.save(player, data);
      return Optional.of(restored);
   }

   private static Entity moveLoadedDuplicateInsteadOfRestoring(
      ServerPlayer player,
      Entity existing,
      ServerLevel level,
      BlockPos pos,
      float yRot,
      float xRot,
      boolean prepareArrival,
      Vec3 focus,
      int durationTicks,
      RescueMagicPacket.Style style,
      RescueMagicPacket.Purpose purpose
   ) {
      if (existing instanceof LivingEntity living) {
         return prepareArrival
            ? moveEntityForArrival(player, living, level, pos, yRot, xRot, focus, durationTicks, style, purpose)
            : moveEntityTo(living, level, pos, yRot, xRot);
      } else {
         if (existing.level() != level) {
            double x = pos.getX() + 0.5;
            double y = pos.getY();
            double z = pos.getZ() + 0.5;
            Entity changed = existing.teleport(transition(level, x, y, z, yRot, xRot));
            if (changed != null) {
               existing = changed;
            }
         }

         double x = pos.getX() + 0.5;
         double y = pos.getY();
         double z = pos.getZ() + 0.5;
         existing.teleportTo(x, y, z);
         existing.snapTo(x, y, z, yRot, xRot);
         return existing;
      }
   }

   static LivingEntity moveEntityForArrival(
      ServerPlayer player,
      LivingEntity entity,
      ServerLevel level,
      BlockPos pos,
      float yRot,
      float xRot,
      Vec3 focus,
      int durationTicks,
      RescueMagicPacket.Style style
   ) {
      return moveEntityForArrival(player, entity, level, pos, yRot, xRot, focus, durationTicks, style, RescueMagicPacket.Purpose.SUMMON);
   }

   static LivingEntity moveEntityForArrival(
      ServerPlayer player,
      LivingEntity entity,
      ServerLevel level,
      BlockPos pos,
      float yRot,
      float xRot,
      Vec3 focus,
      int durationTicks,
      RescueMagicPacket.Style style,
      RescueMagicPacket.Purpose purpose
   ) {
      CompanionArrivalMagicService.openAt(level, player, entity, Vec3.atBottomCenterOf(pos), focus, durationTicks, style, purpose);
      CompanionArrivalSequenceService.startWithDelay(
         player, entity, focus == null ? player.position() : focus, purpose, CompanionArrivalMagicService.revealDelayTicks(durationTicks, style, purpose)
      );
      return moveEntityTo(entity, level, pos, yRot, xRot);
   }

   static LivingEntity moveEntityTo(LivingEntity entity, ServerLevel level, BlockPos pos, float yRot, float xRot) {
      return moveEntityTo(entity, level, pos, yRot, xRot, true);
   }

   static LivingEntity moveEntityTo(LivingEntity entity, ServerLevel level, BlockPos pos, float yRot, float xRot, boolean spawnBlinkParticles) {
      if (spawnBlinkParticles && !CompanionManager.isArrivalPending(entity)) {
         CompanionManager.spawnBlinkParticles(entity);
      }

      Entity changed = null;
      if (entity.level() != level) {
         markIntentionalTransfer(entity);
         changed = entity.teleport(transition(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yRot, xRot));
      }

      if (changed instanceof LivingEntity changedLiving) {
         entity = changedLiving;
      }

      double x = pos.getX() + 0.5;
      double y = pos.getY();
      double z = pos.getZ() + 0.5;
      entity.teleportTo(x, y, z);
      entity.snapTo(x, y, z, yRot, xRot);
      entity.setPos(x, y, z);
      entity.setYRot(yRot);
      entity.setXRot(xRot);
      entity.setDeltaMovement(Vec3.ZERO);
      entity.fallDistance = 0.0F;
      if (entity instanceof Mob mob) {
         mob.getNavigation().stop();
         mob.setTarget(null);
      }

      if (!CompanionManager.isArrivalPending(entity)) {
         CompanionManager.restoreAnimationControl(entity);
      }

      if (spawnBlinkParticles && !CompanionManager.isArrivalPending(entity)) {
         CompanionManager.spawnBlinkParticles(entity);
      }

      return entity;
   }

   private static void markIntentionalTransfer(Entity entity) {
      MinecraftServer server = entity.level().getServer();
      if (server != null) {
         INTENTIONAL_TRANSFERS.put(entity.getUUID(), server.overworld().getGameTime() + 40L);
      }
   }

   private static TeleportTransition transition(ServerLevel level, double x, double y, double z, float yRot, float xRot) {
      return new TeleportTransition(level, new Vec3(x, y, z), Vec3.ZERO, yRot, xRot, TeleportTransition.DO_NOTHING);
   }
}
