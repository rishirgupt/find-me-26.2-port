package com.kuzhi.findme.server;

import com.kuzhi.findme.FindMe;
import com.kuzhi.findme.common.SavedPosition;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

final class SableVehicleCompatibility {
   static final String MARKER = "FindMeSableSubLevel";
   static final String TYPE = "simulated:sable_sublevel";
   private static final String BLUEPRINT = "FindMeSableBlueprint";
   private static final String FORMAT = "FindMeSableFormat";
   private static final String FORMAT_BLUEPRINT_V1 = "sable_blueprint_v1";
   private static final String STORED_DIMENSION = "FindMeSableDimension";
   private static final String STORED_X = "FindMeSableX";
   private static final String STORED_Y = "FindMeSableY";
   private static final String STORED_Z = "FindMeSableZ";
   private static final String CENTER_Y_OFFSET = "FindMeSableCenterYOffset";
   private static final SableVehicleCompatibility.Reflection REFLECTION = SableVehicleCompatibility.Reflection.load();

   private SableVehicleCompatibility() {
   }

   static void bootstrap() {
      FindMe.LOGGER.info("FindMe Sable compatibility: sable={}, sable_schematic_api={}", REFLECTION.available(), REFLECTION.snapshotAvailable());
   }

   static boolean available() {
      return REFLECTION.available();
   }

   static boolean snapshotAvailable() {
      return REFLECTION.snapshotAvailable();
   }

   static boolean isStoredSable(CompoundTag tag) {
      return tag != null && tag.getBooleanOr("FindMeSableSubLevel", false);
   }

   static boolean isStored(PlayerCompanionData data, UUID uuid) {
      return data != null && uuid != null && isStoredSable(data.storedEntities.get(uuid));
   }

   static boolean canRestore(PlayerCompanionData data, UUID uuid) {
      CompoundTag tag = data != null && uuid != null ? data.storedEntities.get(uuid) : null;
      return isStoredSable(tag) && REFLECTION.snapshotAvailable() && tag.contains("FindMeSableBlueprint");
   }

   static Optional<SableVehicleCompatibility.Handle> findAt(ServerLevel level, BlockPos pos) {
      return available() && level != null && pos != null ? wrap(level, REFLECTION.getContaining(level, pos)) : Optional.empty();
   }

   static Optional<SableVehicleCompatibility.Handle> findForEntity(Entity entity) {
      return available() && entity != null && entity.level() instanceof ServerLevel level
         ? wrap(level, REFLECTION.getTrackingOrVehicleSubLevel(entity))
         : Optional.empty();
   }

   static Optional<SableVehicleCompatibility.Handle> find(ServerPlayer player, UUID uuid) {
      return player == null ? Optional.empty() : find(player.level().getServer(), uuid);
   }

   static Optional<SableVehicleCompatibility.Handle> find(MinecraftServer server, UUID uuid) {
      if (available() && server != null && uuid != null) {
         for (ServerLevel level : server.getAllLevels()) {
            Optional<SableVehicleCompatibility.Handle> handle = find(level, uuid);
            if (handle.isPresent()) {
               return handle;
            }
         }

         return Optional.empty();
      } else {
         return Optional.empty();
      }
   }

   static Optional<SableVehicleCompatibility.Handle> find(ServerLevel level, UUID uuid) {
      if (available() && level != null && uuid != null) {
         Object container = REFLECTION.container(level);
         return wrap(level, REFLECTION.getSubLevel(container, uuid));
      } else {
         return Optional.empty();
      }
   }

   static boolean store(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
      if (isStored(data, uuid)) {
         return true;
      }

      Optional<SableVehicleCompatibility.Handle> maybeHandle = find(player, uuid);
      return maybeHandle.isPresent() && store(player, data, maybeHandle.get());
   }

   static boolean store(ServerPlayer player, PlayerCompanionData data, SableVehicleCompatibility.Handle handle) {
      if (snapshotAvailable() && player != null && data != null && handle != null && handle.subLevel != null) {
         UUID uuid = handle.uuid();
         AABB box = handle.box();
         if (uuid != null && isReasonableVehicleBox(box)) {
            Vec3 center = box.getCenter();
            CompoundTag blueprint = REFLECTION.exportBlueprint(handle.level, center, box.inflate(0.25));
            if (blueprint != null && !blueprint.isEmpty()) {
               List<SableVehicleCompatibility.Handle> exportedHandles = exportedHandles(handle.level, blueprint, handle);
               if (exportedHandles.isEmpty()) {
                  FindMe.LOGGER.warn("FindMe Sable storage refused: blueprint for {} did not map back to live sub-levels", uuid);
                  return false;
               }

               String name = handle.name();
               REFLECTION.detachPlayer(player);
               movePlayerOutOfSubLevel(player, box);

               for (SableVehicleCompatibility.Handle exported : exportedHandles) {
                  REFLECTION.stopMotion(exported.subLevel);
               }

               for (SableVehicleCompatibility.Handle exported : exportedHandles) {
                  if (!REFLECTION.removeSubLevel(exported.level, exported.subLevel)) {
                     FindMe.LOGGER
                        .warn("FindMe Sable storage warning: exported blueprint for {} but could not remove live sub-level {}", uuid, exported.uuid());
                  }
               }

               CompoundTag tag = storedTag(player, handle, box, center);
               tag.putString("FindMeSableFormat", "sable_blueprint_v1");
               tag.put("FindMeSableBlueprint", blueprint);
               data.storeEntity(uuid, tag);
               data.setDisplayName(uuid, name);
               data.setLastKnownPosition(uuid, SavedPosition.of(handle.level, center.x, center.y, center.z, 0.0F, 0.0F));
               FindMe.LOGGER.info("FindMe stored Sable vehicle {} as blueprint snapshot with {} sub-level(s)", uuid, exportedHandles.size());
               return true;
            } else {
               FindMe.LOGGER.warn("FindMe Sable storage refused: blueprint export returned empty for sub-level {}", uuid);
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   static Optional<SableVehicleCompatibility.Handle> restore(ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos) {
      if (snapshotAvailable() && player != null && data != null && uuid != null && pos != null) {
         CompoundTag tag = data.storedEntities.get(uuid);
         if (isStoredSable(tag) && tag.contains("FindMeSableBlueprint")) {
            Vec3 anchor = restoreOrigin(tag, pos);
            REFLECTION.detachPlayer(player);
            SableVehicleCompatibility.Placement placement = REFLECTION.placeBlueprint(player.level(), tag.getCompoundOrEmpty("FindMeSableBlueprint"), anchor);
            if (placement != null && placement.placedCount > 0) {
               UUID placedUuid = placement.mappedUuid(uuid);
               if (placedUuid == null) {
                  placedUuid = placement.firstPlacedUuid();
               }

               if (placedUuid == null) {
                  FindMe.LOGGER.warn("FindMe Sable restore failed: blueprint placement produced no UUID mapping for {}", uuid);
                  return Optional.empty();
               }

               Optional<SableVehicleCompatibility.Handle> handle = find(player.level(), placedUuid);
               if (handle.isEmpty()) {
                  FindMe.LOGGER.warn("FindMe Sable restore failed: placed sub-level {} was not found after blueprint placement", placedUuid);
                  return Optional.empty();
               }

               data.removeStoredEntity(uuid);
               if (!uuid.equals(placedUuid)) {
                  data.replaceUuid(uuid, placedUuid);
               }

               data.setDisplayName(placedUuid, storedName(tag));
               Vec3 center = handle.get().box().getCenter();
               data.setLastKnownPosition(placedUuid, SavedPosition.of(player.level(), center.x, center.y, center.z, player.getYRot(), player.getXRot()));
               pushPlayerOut(player, handle.get());
               FindMe.LOGGER.info("FindMe restored Sable vehicle {} from blueprint snapshot as {}", uuid, placedUuid);
               return handle;
            } else {
               FindMe.LOGGER.warn("FindMe Sable restore failed: blueprint placement returned no sub-levels for {}", uuid);
               return Optional.empty();
            }
         } else {
            return Optional.empty();
         }
      } else {
         return Optional.empty();
      }
   }

   static boolean move(SableVehicleCompatibility.Handle handle, ServerLevel level, BlockPos pos) {
      if (handle != null && level != null && pos != null && handle.level == level) {
         REFLECTION.stopMotion(handle.subLevel);
         REFLECTION.teleport(handle.subLevel, moveOrigin(handle, pos));
         return true;
      } else {
         return false;
      }
   }

   static void pushPlayerOut(ServerPlayer player, SableVehicleCompatibility.Handle handle) {
      if (player != null && handle != null) {
         REFLECTION.detachPlayer(player);
         movePlayerOutOfSubLevel(player, handle.box());
         REFLECTION.detachPlayer(player);
      }
   }

   static void release(SableVehicleCompatibility.Handle handle) {
   }

   private static CompoundTag storedTag(ServerPlayer player, SableVehicleCompatibility.Handle handle, AABB box, Vec3 center) {
      UUID uuid = handle.uuid();
      CompoundTag tag = new CompoundTag();
      tag.putBoolean("FindMeSableSubLevel", true);
      tag.putString("id", "simulated:sable_sublevel");
      tag.putString("CompanionRescueType", "simulated:sable_sublevel");
      tag.putString("CompanionRescueName", handle.name());
      tag.store("UUID", net.minecraft.core.UUIDUtil.CODEC, uuid);
      tag.putFloat("CompanionPreviewWidth", (float)Math.max(1.0, box.getXsize()));
      tag.putFloat("CompanionPreviewHeight", (float)Math.max(1.0, box.getYsize()));
      tag.putFloat("CompanionPreviewDepth", (float)Math.max(1.0, box.getZsize()));
      tag.putDouble("FindMeSableCenterYOffset", Math.max(0.0, center.y - box.minY));
      tag.putLong("CompanionRescueStoredAt", player.level().getGameTime());
      tag.putString("FindMeSableDimension", handle.level.dimension().identifier().toString());
      tag.putDouble("FindMeSableX", center.x);
      tag.putDouble("FindMeSableY", center.y);
      tag.putDouble("FindMeSableZ", center.z);
      return tag;
   }

   private static Vec3 restoreOrigin(CompoundTag tag, BlockPos pos) {
      double offset = tag.contains("FindMeSableCenterYOffset")
         ? tag.getDoubleOr("FindMeSableCenterYOffset", 0.0)
         : Math.max(0.5, tag.getFloatOr("CompanionPreviewHeight", 0.0F) * 0.5);
      return new Vec3(pos.getX() + 0.5, pos.getY() + offset, pos.getZ() + 0.5);
   }

   private static Vec3 moveOrigin(SableVehicleCompatibility.Handle handle, BlockPos pos) {
      AABB box = handle.box();
      double offset = isReasonableVehicleBox(box) ? Math.max(0.0, box.getCenter().y - box.minY) : 1.0;
      return new Vec3(pos.getX() + 0.5, pos.getY() + offset, pos.getZ() + 0.5);
   }

   private static String storedName(CompoundTag tag) {
      String name = tag.getStringOr("CompanionRescueName", "");
      return name != null && !name.isBlank() ? name : "Sable Vehicle";
   }

   private static List<SableVehicleCompatibility.Handle> exportedHandles(ServerLevel level, CompoundTag blueprint, SableVehicleCompatibility.Handle fallback) {
      ArrayList<SableVehicleCompatibility.Handle> result = new ArrayList<>();
      if (blueprint != null && blueprint.contains("sub_levels")) {
         ListTag subLevels = blueprint.getListOrEmpty("sub_levels");

         for (int i = 0; i < subLevels.size(); i++) {
            CompoundTag subLevel = subLevels.getCompoundOrEmpty(i);
            if (subLevel.read("source_uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
               UUID sourceUuid = subLevel.read("source_uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow();
               Optional<SableVehicleCompatibility.Handle> handle = find(level, sourceUuid);
               if (handle.isPresent() && result.stream().noneMatch(existing -> sourceUuid.equals(existing.uuid()))) {
                  result.add(handle.get());
               }
            }
         }
      }

      if (result.isEmpty() && fallback != null) {
         result.add(fallback);
      }

      return result;
   }

   private static void movePlayerOutOfSubLevel(ServerPlayer player, AABB box) {
      if (isReasonableVehicleBox(box) && player.getBoundingBox().intersects(box.inflate(0.75))) {
         Vec3 pos = player.position();
         double east = Math.abs(box.maxX - pos.x);
         double west = Math.abs(pos.x - box.minX);
         double south = Math.abs(box.maxZ - pos.z);
         double north = Math.abs(pos.z - box.minZ);
         double x = pos.x;
         double z = pos.z;
         double min = Math.min(Math.min(east, west), Math.min(south, north));
         if (min == east) {
            x = box.maxX + 2.0;
         } else if (min == west) {
            x = box.minX - 2.0;
         } else if (min == south) {
            z = box.maxZ + 2.0;
         } else {
            z = box.minZ - 2.0;
         }

         player.stopRiding();
         player.setDeltaMovement(Vec3.ZERO);
         player.fallDistance = 0.0F;
         teleportPlayerKeepingView(player, new Vec3(x, pos.y, z));
      }
   }

   private static void teleportPlayerKeepingView(ServerPlayer player, Vec3 pos) {
      if (player != null && pos != null) {
         float yRot = player.getYRot();
         float xRot = player.getXRot();
         player.teleportTo(pos.x, pos.y, pos.z);
         player.snapTo(pos.x, pos.y, pos.z, yRot, xRot);
         player.setYRot(yRot);
         player.setXRot(xRot);
         player.yRotO = yRot;
         player.xRotO = xRot;
         player.xOld = pos.x;
         player.xo = pos.x;
         player.yOld = pos.y;
         player.yo = pos.y;
         player.zOld = pos.z;
         player.zo = pos.z;
         player.setDeltaMovement(Vec3.ZERO);
         player.fallDistance = 0.0F;
         REFLECTION.clearTracking(player);
      }
   }

   private static boolean isReasonableVehicleBox(AABB box) {
      return box != null
         && Double.isFinite(box.minX)
         && Double.isFinite(box.minY)
         && Double.isFinite(box.minZ)
         && Double.isFinite(box.maxX)
         && Double.isFinite(box.maxY)
         && Double.isFinite(box.maxZ)
         && box.getXsize() > 0.0
         && box.getYsize() > 0.0
         && box.getZsize() > 0.0
         && box.getXsize() <= 512.0
         && box.getYsize() <= 256.0
         && box.getZsize() <= 512.0;
   }

   private static Optional<SableVehicleCompatibility.Handle> wrap(ServerLevel level, Object subLevel) {
      if (available() && level != null && subLevel != null && !REFLECTION.isRemoved(subLevel)) {
         UUID uuid = REFLECTION.uuid(subLevel);
         return uuid == null ? Optional.empty() : Optional.of(new SableVehicleCompatibility.Handle(level, subLevel));
      } else {
         return Optional.empty();
      }
   }

   static final class Handle {
      private final ServerLevel level;
      private final Object subLevel;

      private Handle(ServerLevel level, Object subLevel) {
         this.level = level;
         this.subLevel = subLevel;
      }

      UUID uuid() {
         return SableVehicleCompatibility.REFLECTION.uuid(this.subLevel);
      }

      String name() {
         String name = SableVehicleCompatibility.REFLECTION.name(this.subLevel);
         return name != null && !name.isBlank() ? name : "Sable Vehicle";
      }

      AABB box() {
         return SableVehicleCompatibility.REFLECTION.box(this.subLevel);
      }

      float radius() {
         AABB box = this.box();
         return (float)Math.max(1.4, Math.min(28.0, Math.max(box.getXsize(), box.getZsize()) * 0.55 + 1.35));
      }

      float height() {
         return (float)Math.max(1.0, Math.min(32.0, this.box().getYsize()));
      }
   }

   private record Placement(int placedCount, Map<UUID, UUID> uuidMap) {
      private UUID mappedUuid(UUID source) {
         return this.uuidMap == null ? null : this.uuidMap.get(source);
      }

      private UUID firstPlacedUuid() {
         return this.uuidMap != null && !this.uuidMap.isEmpty() ? this.uuidMap.values().iterator().next() : null;
      }
   }

   private static final class Reflection {
      private final boolean available;
      private final boolean snapshotAvailable;
      private final Field helperField;
      private final Method getContaining;
      private final Method getTrackingOrVehicleSubLevel;
      private final Method getContainer;
      private final Method getSubLevel;
      private final Method removeSubLevel;
      private final Object removalReasonRemoved;
      private final Method rigidBodyOf;
      private final Method teleport;
      private final Method getLinearVelocity;
      private final Method getAngularVelocity;
      private final Method addLinearAndAngularVelocity;
      private final Method logicalPose;
      private final Method orientation;
      private final Method boundingBox;
      private final Method toMojang;
      private final Method uuid;
      private final Method name;
      private final Method isRemoved;
      private final Method kickEntity;
      private final Class<?> entityMovementExtension;
      private final Method setTrackingSubLevel;
      private final Method setLastTrackingSubLevelID;
      private final Method setPosField;
      private final Method projectOutOfSubLevel;
      private final Constructor<?> boundingBoxConstructor;
      private final Method exportBlueprint;
      private final Method blueprintSave;
      private final Method blueprintLoad;
      private final Method blueprintIsEmpty;
      private final Method placeBlueprint;
      private final Method placedSubLevels;
      private final Method subLevelUuidMap;

      private Reflection() {
         this.available = false;
         this.snapshotAvailable = false;
         this.helperField = null;
         this.getContaining = null;
         this.getTrackingOrVehicleSubLevel = null;
         this.getContainer = null;
         this.getSubLevel = null;
         this.removeSubLevel = null;
         this.removalReasonRemoved = null;
         this.rigidBodyOf = null;
         this.teleport = null;
         this.getLinearVelocity = null;
         this.getAngularVelocity = null;
         this.addLinearAndAngularVelocity = null;
         this.logicalPose = null;
         this.orientation = null;
         this.boundingBox = null;
         this.toMojang = null;
         this.uuid = null;
         this.name = null;
         this.isRemoved = null;
         this.kickEntity = null;
         this.entityMovementExtension = null;
         this.setTrackingSubLevel = null;
         this.setLastTrackingSubLevelID = null;
         this.setPosField = null;
         this.projectOutOfSubLevel = null;
         this.boundingBoxConstructor = null;
         this.exportBlueprint = null;
         this.blueprintSave = null;
         this.blueprintLoad = null;
         this.blueprintIsEmpty = null;
         this.placeBlueprint = null;
         this.placedSubLevels = null;
         this.subLevelUuidMap = null;
      }

      private Reflection(
         boolean available,
         boolean snapshotAvailable,
         Field helperField,
         Method getContaining,
         Method getTrackingOrVehicleSubLevel,
         Method getContainer,
         Method getSubLevel,
         Method removeSubLevel,
         Object removalReasonRemoved,
         Method rigidBodyOf,
         Method teleport,
         Method getLinearVelocity,
         Method getAngularVelocity,
         Method addLinearAndAngularVelocity,
         Method logicalPose,
         Method orientation,
         Method boundingBox,
         Method toMojang,
         Method uuid,
         Method name,
         Method isRemoved,
         Method kickEntity,
         Class<?> entityMovementExtension,
         Method setTrackingSubLevel,
         Method setLastTrackingSubLevelID,
         Method setPosField,
         Method projectOutOfSubLevel,
         Constructor<?> boundingBoxConstructor,
         Method exportBlueprint,
         Method blueprintSave,
         Method blueprintLoad,
         Method blueprintIsEmpty,
         Method placeBlueprint,
         Method placedSubLevels,
         Method subLevelUuidMap
      ) {
         this.available = available;
         this.snapshotAvailable = snapshotAvailable;
         this.helperField = helperField;
         this.getContaining = getContaining;
         this.getTrackingOrVehicleSubLevel = getTrackingOrVehicleSubLevel;
         this.getContainer = getContainer;
         this.getSubLevel = getSubLevel;
         this.removeSubLevel = removeSubLevel;
         this.removalReasonRemoved = removalReasonRemoved;
         this.rigidBodyOf = rigidBodyOf;
         this.teleport = teleport;
         this.getLinearVelocity = getLinearVelocity;
         this.getAngularVelocity = getAngularVelocity;
         this.addLinearAndAngularVelocity = addLinearAndAngularVelocity;
         this.logicalPose = logicalPose;
         this.orientation = orientation;
         this.boundingBox = boundingBox;
         this.toMojang = toMojang;
         this.uuid = uuid;
         this.name = name;
         this.isRemoved = isRemoved;
         this.kickEntity = kickEntity;
         this.entityMovementExtension = entityMovementExtension;
         this.setTrackingSubLevel = setTrackingSubLevel;
         this.setLastTrackingSubLevelID = setLastTrackingSubLevelID;
         this.setPosField = setPosField;
         this.projectOutOfSubLevel = projectOutOfSubLevel;
         this.boundingBoxConstructor = boundingBoxConstructor;
         this.exportBlueprint = exportBlueprint;
         this.blueprintSave = blueprintSave;
         this.blueprintLoad = blueprintLoad;
         this.blueprintIsEmpty = blueprintIsEmpty;
         this.placeBlueprint = placeBlueprint;
         this.placedSubLevels = placedSubLevels;
         this.subLevelUuidMap = subLevelUuidMap;
      }

      static SableVehicleCompatibility.Reflection load() {
         try {
            Class<?> sable = Class.forName("dev.ryanhcode.sable.Sable");
            Class<?> helperClass = Class.forName("dev.ryanhcode.sable.ActiveSableCompanion");
            Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
            Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
            Class<?> serverSubLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.ServerSubLevel");
            Class<?> removalReasonClass = Class.forName("dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason");
            Class<?> rigidBodyHandleClass = Class.forName("dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle");
            Class<?> poseClass = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc");
            Class<?> boxInterfaceClass = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3dc");
            Class<?> boxClass = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3d");
            Class<?> entitySubLevelUtil = Class.forName("dev.ryanhcode.sable.api.entity.EntitySubLevelUtil");
            Class<?> entityMovementExtension = Class.forName("dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension");
            Field helperField = sable.getField("HELPER");
            Method getContaining = helperClass.getMethod("getContaining", Level.class, Vec3i.class);
            Method getTracking = helperClass.getMethod("getTrackingOrVehicleSubLevel", Entity.class);
            Method getContainer = containerClass.getMethod("getContainer", ServerLevel.class);
            Method getSubLevel = containerClass.getMethod("getSubLevel", UUID.class);
            Method removeSubLevel = containerClass.getMethod("removeSubLevel", subLevelClass, removalReasonClass);
            Object removalReasonRemoved = Enum.valueOf(removalReasonClass.asSubclass(Enum.class), "REMOVED");
            Method rigidBodyOf = rigidBodyHandleClass.getMethod("of", serverSubLevelClass);
            Method teleport = rigidBodyHandleClass.getMethod("teleport", Vector3dc.class, Quaterniondc.class);
            Method getLinearVelocity = rigidBodyHandleClass.getMethod("getLinearVelocity", Vector3d.class);
            Method getAngularVelocity = rigidBodyHandleClass.getMethod("getAngularVelocity", Vector3d.class);
            Method addLinearAndAngularVelocity = rigidBodyHandleClass.getMethod("addLinearAndAngularVelocity", Vector3dc.class, Vector3dc.class);
            Method logicalPose = subLevelClass.getMethod("logicalPose");
            Method orientation = poseClass.getMethod("orientation");
            Method boundingBox = subLevelClass.getMethod("boundingBox");
            Method toMojang = boxInterfaceClass.getMethod("toMojang");
            Method uuid = subLevelClass.getMethod("getUniqueId");
            Method name = subLevelClass.getMethod("getName");
            Method isRemoved = subLevelClass.getMethod("isRemoved");
            Method kickEntity = entitySubLevelUtil.getMethod("kickEntity", subLevelClass, Entity.class);
            Method setTrackingSubLevel = entityMovementExtension.getMethod("sable$setTrackingSubLevel", subLevelClass);
            Method setLastTrackingSubLevelID = entityMovementExtension.getMethod("sable$setLastTrackingSubLevelID", UUID.class);
            Method setPosField = entityMovementExtension.getMethod("sable$setPosField", Vec3.class);
            Method projectOutOfSubLevel = helperClass.getMethod("projectOutOfSubLevel", Level.class, Vec3.class);
            Constructor<?> boundingBoxConstructor = boxClass.getConstructor(double.class, double.class, double.class, double.class, double.class, double.class);
            Method exportBlueprint = null;
            Method blueprintSave = null;
            Method blueprintLoad = null;
            Method blueprintIsEmpty = null;
            Method placeBlueprint = null;
            Method placedSubLevels = null;
            Method subLevelUuidMap = null;
            boolean snapshotAvailable = false;

            try {
               Class<?> exporterClass = Class.forName("dev.rew1nd.sableschematicapi.blueprint.SableBlueprintExporter");
               Class<?> blueprintClass = Class.forName("dev.rew1nd.sableschematicapi.blueprint.SableBlueprint");
               Class<?> placerClass = Class.forName("dev.rew1nd.sableschematicapi.blueprint.SableBlueprintPlacer");
               Class<?> resultClass = Class.forName("dev.rew1nd.sableschematicapi.blueprint.SableBlueprintPlacer$Result");
               exportBlueprint = exporterClass.getMethod("export", ServerLevel.class, Vec3.class, boxClass);
               blueprintSave = blueprintClass.getMethod("save");
               blueprintLoad = blueprintClass.getMethod("load", CompoundTag.class);
               blueprintIsEmpty = blueprintClass.getMethod("isEmpty");
               placeBlueprint = placerClass.getMethod("place", ServerLevel.class, blueprintClass, Vec3.class);
               placedSubLevels = resultClass.getMethod("placedSubLevels");
               subLevelUuidMap = resultClass.getMethod("subLevelUuidMap");
               snapshotAvailable = true;
            } catch (ReflectiveOperationException | LinkageError ignored) {
               snapshotAvailable = false;
            }

            return new SableVehicleCompatibility.Reflection(
               true,
               snapshotAvailable,
               helperField,
               getContaining,
               getTracking,
               getContainer,
               getSubLevel,
               removeSubLevel,
               removalReasonRemoved,
               rigidBodyOf,
               teleport,
               getLinearVelocity,
               getAngularVelocity,
               addLinearAndAngularVelocity,
               logicalPose,
               orientation,
               boundingBox,
               toMojang,
               uuid,
               name,
               isRemoved,
               kickEntity,
               entityMovementExtension,
               setTrackingSubLevel,
               setLastTrackingSubLevelID,
               setPosField,
               projectOutOfSubLevel,
               boundingBoxConstructor,
               exportBlueprint,
               blueprintSave,
               blueprintLoad,
               blueprintIsEmpty,
               placeBlueprint,
               placedSubLevels,
               subLevelUuidMap
            );
         } catch (ReflectiveOperationException | LinkageError ignored) {
            return new SableVehicleCompatibility.Reflection();
         }
      }

      boolean available() {
         return this.available;
      }

      boolean snapshotAvailable() {
         return this.available && this.snapshotAvailable;
      }

      Object helper() {
         try {
            return this.helperField.get(null);
         } catch (IllegalAccessException e) {
            return null;
         }
      }

      Object getContaining(ServerLevel level, BlockPos pos) {
         try {
            return this.getContaining.invoke(this.helper(), level, pos);
         } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
         }
      }

      Object getTrackingOrVehicleSubLevel(Entity entity) {
         try {
            return this.getTrackingOrVehicleSubLevel.invoke(this.helper(), entity);
         } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
         }
      }

      Object container(ServerLevel level) {
         try {
            return this.getContainer.invoke(null, level);
         } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
         }
      }

      Object getSubLevel(Object container, UUID uuid) {
         try {
            return container == null ? null : this.getSubLevel.invoke(container, uuid);
         } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
         }
      }

      CompoundTag exportBlueprint(ServerLevel level, Vec3 origin, AABB box) {
         if (this.snapshotAvailable() && level != null && origin != null && box != null) {
            try {
               Object bounds = this.boundingBoxConstructor.newInstance(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
               Object blueprint = this.exportBlueprint.invoke(null, level, origin, bounds);
               Object empty = this.blueprintIsEmpty.invoke(blueprint);
               if (Boolean.TRUE.equals(empty)) {
                  return null;
               } else {
                  return this.blueprintSave.invoke(blueprint) instanceof CompoundTag tag ? tag.copy() : null;
               }
            } catch (ReflectiveOperationException | RuntimeException e) {
               return null;
            }
         } else {
            return null;
         }
      }

      SableVehicleCompatibility.Placement placeBlueprint(ServerLevel level, CompoundTag tag, Vec3 origin) {
         if (this.snapshotAvailable() && level != null && tag != null && origin != null) {
            try {
               Object blueprint = this.blueprintLoad.invoke(null, tag.copy());
               Object result = this.placeBlueprint.invoke(null, level, blueprint, origin);
               Object placed = this.placedSubLevels.invoke(result);
               Object map = this.subLevelUuidMap.invoke(result);
               int placedCount = placed instanceof Number number ? number.intValue() : 0;
               Map<UUID, UUID> uuidMap = (Map<UUID, UUID>)(map instanceof Map<?, ?> raw ? raw : Map.of());
               return new SableVehicleCompatibility.Placement(placedCount, uuidMap);
            } catch (ReflectiveOperationException | RuntimeException e) {
               return null;
            }
         } else {
            return null;
         }
      }

      boolean removeSubLevel(ServerLevel level, Object subLevel) {
         try {
            Object container = this.container(level);
            if (container != null && subLevel != null && this.removalReasonRemoved != null) {
               this.removeSubLevel.invoke(container, subLevel, this.removalReasonRemoved);
               return true;
            } else {
               return false;
            }
         } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
         }
      }

      void stopMotion(Object subLevel) {
         try {
            Object handle = this.rigidBodyOf.invoke(null, subLevel);
            if (handle == null) {
               return;
            }

            Vector3d linear = (Vector3d)this.getLinearVelocity.invoke(handle, new Vector3d());
            Vector3d angular = (Vector3d)this.getAngularVelocity.invoke(handle, new Vector3d());
            linear.negate();
            angular.negate();
            this.addLinearAndAngularVelocity.invoke(handle, linear, angular);
         } catch (ReflectiveOperationException | RuntimeException var5) {
         }
      }

      void teleport(Object subLevel, Vec3 pos) {
         try {
            Object handle = this.rigidBodyOf.invoke(null, subLevel);
            Object pose = this.logicalPose.invoke(subLevel);
            Quaterniondc orientation = (Quaterniondc)(this.orientation.invoke(pose) instanceof Quaterniondc quaternion ? quaternion : new Quaterniond());
            this.teleport.invoke(handle, new Vector3d(pos.x, pos.y, pos.z), orientation);
         } catch (ReflectiveOperationException | RuntimeException var8) {
         }
      }

      AABB box(Object subLevel) {
         try {
            Object box = this.boundingBox.invoke(subLevel);
            return this.toMojang.invoke(box) instanceof AABB aabb ? aabb : new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
         } catch (ReflectiveOperationException | RuntimeException e) {
            return new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
         }
      }

      UUID uuid(Object subLevel) {
         try {
            return (subLevel == null ? null : this.uuid.invoke(subLevel)) instanceof UUID value ? value : null;
         } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
         }
      }

      String name(Object subLevel) {
         try {
            return (subLevel == null ? null : this.name.invoke(subLevel)) instanceof String value ? value : "";
         } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
         }
      }

      boolean isRemoved(Object subLevel) {
         try {
            Object removed = subLevel == null ? Boolean.TRUE : this.isRemoved.invoke(subLevel);
            return Boolean.TRUE.equals(removed);
         } catch (ReflectiveOperationException | RuntimeException e) {
            return true;
         }
      }

      void kickEntity(Object subLevel, Entity entity) {
         try {
            if (subLevel != null && entity != null) {
               this.kickEntity.invoke(null, subLevel, entity);
            }
         } catch (ReflectiveOperationException | RuntimeException var4) {
         }
      }

      void detachPlayer(ServerPlayer player) {
         if (player != null) {
            float yRot = player.getYRot();
            float xRot = player.getXRot();
            Vec3 pos = this.projectOutOfSubLevel(player.level(), player.position());
            this.clearTracking(player);
            player.stopRiding();
            player.teleportTo(pos.x, pos.y, pos.z);
            player.snapTo(pos.x, pos.y, pos.z, yRot, xRot);
            player.setYRot(yRot);
            player.setXRot(xRot);
            player.yRotO = yRot;
            player.xRotO = xRot;
            player.xOld = pos.x;
            player.xo = pos.x;
            player.yOld = pos.y;
            player.yo = pos.y;
            player.zOld = pos.z;
            player.zo = pos.z;
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0F;
            this.setPosField(player, pos);
         }
      }

      Vec3 projectOutOfSubLevel(ServerLevel level, Vec3 pos) {
         try {
            return this.projectOutOfSubLevel.invoke(this.helper(), level, pos) instanceof Vec3 vec ? vec : pos;
         } catch (ReflectiveOperationException | RuntimeException e) {
            return pos;
         }
      }

      void clearTracking(Entity entity) {
         try {
            if (entity != null && this.entityMovementExtension != null && this.entityMovementExtension.isInstance(entity)) {
               this.setTrackingSubLevel.invoke(entity, null);
               this.setLastTrackingSubLevelID.invoke(entity, null);
               this.setPosField(entity, entity.position());
            }
         } catch (ReflectiveOperationException | RuntimeException var3) {
         }
      }

      private void setPosField(Entity entity, Vec3 pos) {
         try {
            if (entity != null && pos != null && this.entityMovementExtension != null && this.entityMovementExtension.isInstance(entity)) {
               this.setPosField.invoke(entity, pos);
            }
         } catch (ReflectiveOperationException | RuntimeException var4) {
         }
      }
   }
}
