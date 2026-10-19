package com.kuzhi.findme.server;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.kuzhi.findme.compat.Parts;

final class CompanionMountContactService {
   private static final Map<Class<?>, Optional<Method>> CUSTOM_CONTACT_SEGMENT_METHODS = new HashMap<>();
   private static final Map<Class<?>, Optional<Field>> CUSTOM_CONTACT_SEGMENT_FIELDS = new HashMap<>();
   private static final Map<Class<?>, List<Method>> CUSTOM_PART_METHODS = new HashMap<>();
   private static final Map<Class<?>, List<Field>> CUSTOM_PART_FIELDS = new HashMap<>();
   private static final Map<CompanionMountContactService.ContactBoxCacheKey, List<AABB>> CONTACT_BOX_CACHE = new HashMap<>();
   private static long contactBoxCacheTick = Long.MIN_VALUE;

   private CompanionMountContactService() {
   }

   static boolean canMountByNormalUse(ServerPlayer player, LivingEntity mount) {
      if (mount instanceof AbstractHorse horse) {
         return horse.isTamed() && horse.isSaddled() && CompanionManager.isOwnedBy(player, horse);
      } else {
         Boolean saddled = booleanMethodValue(mount, "isSaddled");
         if (saddled != null && !saddled) {
            return false;
         }

         Boolean tame = booleanMethodValue(mount, "isTame");
         if (tame == null) {
            tame = booleanMethodValue(mount, "isTamed");
         }

         return tame == null || !tame || CompanionManager.isOwnedBy(player, mount);
      }
   }

   static boolean intersectsMountContact(LivingEntity mount, AABB otherBox, double inflate) {
      for (AABB box : contactBoxes(mount, inflate)) {
         if (otherBox.intersects(box)) {
            FindMeDebugLogger.info(
               "contact-result",
               "intersectsMountContact mount={} other={} inflate={} result=true hit={}",
               FindMeDebugLogger.entity(mount),
               FindMeDebugLogger.box(otherBox),
               inflate,
               FindMeDebugLogger.box(box)
            );
            return true;
         }
      }

      FindMeDebugLogger.info(
         "contact-result",
         "intersectsMountContact mount={} other={} inflate={} result=false",
         FindMeDebugLogger.entity(mount),
         FindMeDebugLogger.box(otherBox),
         inflate
      );
      return false;
   }

   static boolean contactsIntersect(Entity first, Entity second, double inflate) {
      List<AABB> firstBoxes = contactBoxes(first, inflate);
      List<AABB> secondBoxes = contactBoxes(second, inflate);

      for (AABB firstBox : firstBoxes) {
         for (AABB secondBox : secondBoxes) {
            if (firstBox.intersects(secondBox)) {
               FindMeDebugLogger.info(
                  "contact-result",
                  "contactsIntersect first={} second={} inflate={} result=true method=pairwise firstBox={} secondBox={}",
                  FindMeDebugLogger.entity(first),
                  FindMeDebugLogger.entity(second),
                  inflate,
                  FindMeDebugLogger.box(firstBox),
                  FindMeDebugLogger.box(secondBox)
               );
               return true;
            }
         }
      }

      if (sweptContact(first, firstBoxes, second, secondBoxes)) {
         FindMeDebugLogger.info(
            "contact-result",
            "contactsIntersect first={} second={} inflate={} result=true method=swept firstMotion={} secondMotion={}",
            FindMeDebugLogger.entity(first),
            FindMeDebugLogger.entity(second),
            inflate,
            first.getDeltaMovement(),
            second.getDeltaMovement()
         );
         return true;
      } else {
         boolean broad = broadMultipartContact(first, firstBoxes, second, secondBoxes, inflate);
         FindMeDebugLogger.info(
            "contact-result",
            "contactsIntersect first={} second={} inflate={} result={} method=broad-or-none",
            FindMeDebugLogger.entity(first),
            FindMeDebugLogger.entity(second),
            inflate,
            broad
         );
         return broad;
      }
   }

   static AABB mountContactBox(LivingEntity mount) {
      AABB box = union(contactBoxes(mount, 0.0));
      double boxWidth = Math.max(box.getXsize(), box.getZsize());
      double boxHeight = box.getYsize();
      double horizontal = Math.max(0.35, Math.min(3.25, boxWidth * 0.22));
      double vertical = Math.max(0.35, Math.min(2.25, boxHeight * 0.18));
      return box.inflate(horizontal, vertical, horizontal);
   }

   static AABB entityContactBox(Entity entity) {
      return entity instanceof LivingEntity living ? mountContactBox(living) : union(contactBoxes(entity, 0.0));
   }

   private static Boolean booleanMethodValue(Object target, String name) {
      try {
         Method method = target.getClass().getMethod(name);
         if (method.getReturnType() != boolean.class && method.getReturnType() != Boolean.class) {
            return null;
         } else {
            return method.invoke(target) instanceof Boolean result ? result : null;
         }
      } catch (ReflectiveOperationException | RuntimeException ignored) {
         return null;
      }
   }

   private static List<AABB> contactBoxes(Entity entity, double inflate) {
      Level level = entity.level();
      long tick = level == null ? Long.MIN_VALUE : level.getGameTime();
      if (tick != contactBoxCacheTick) {
         CONTACT_BOX_CACHE.clear();
         contactBoxCacheTick = tick;
      }

      CompanionMountContactService.ContactBoxCacheKey key = new CompanionMountContactService.ContactBoxCacheKey(
         level == null ? "" : level.dimension().identifier().toString(), entity.getId(), Double.doubleToLongBits(inflate)
      );
      List<AABB> cached = CONTACT_BOX_CACHE.get(key);
      if (cached != null) {
         return cached;
      }

      List<AABB> boxes = new ArrayList<>();
      int reflectedParts = 0;
      int customSegments = 0;
      boxes.add(entity.getBoundingBox().inflate(inflate));
      CompanionMountContactService.RelatedParts officialParts = relatedParts(entity);

      for (Entity part : officialParts.parts()) {
         if (part != null) {
            boxes.add(part.getBoundingBox().inflate(inflate));
         }
      }

      if (officialParts.parts().isEmpty()) {
         List<Entity> reflected = reflectedPartEntities(entity);
         reflectedParts = reflected.size();

         for (Entity part : reflected) {
            if (part != null && part != entity) {
               boxes.add(part.getBoundingBox().inflate(inflate));
            }
         }

         customSegments = addCustomContactSegmentBoxes(entity, boxes, inflate);
      }

      FindMeDebugLogger.contactBoxes(
         entity,
         inflate,
         boxes,
         officialParts.parts().size(),
         officialParts.found(),
         officialParts.ignored(),
         officialParts.parts().isEmpty() && reflectedParts > 0,
         reflectedParts,
         customSegments > 0
      );
      List<AABB> cachedBoxes = List.copyOf(boxes);
      CONTACT_BOX_CACHE.put(key, cachedBoxes);
      return cachedBoxes;
   }

   private static AABB union(List<AABB> boxes) {
      AABB result = boxes.isEmpty() ? new AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0) : boxes.get(0);

      for (int i = 1; i < boxes.size(); i++) {
         result = result.minmax(boxes.get(i));
      }

      return result;
   }

   private static CompanionMountContactService.RelatedParts relatedParts(Entity entity) {
      List<Entity> result = new ArrayList<>();
      Set<Entity> seen = new HashSet<>();
      CompanionMountContactService.PartCounter counter = new CompanionMountContactService.PartCounter();
      if (Parts.isMultipart(entity)) {
         addFilteredParts(entity, Arrays.asList(Parts.of(entity)), result, seen, counter);
      }

      if (entity.level() instanceof ServerLevel serverLevel) {
         addFilteredParts(entity, Parts.inLevel(serverLevel), result, seen, counter);
      }

      return new CompanionMountContactService.RelatedParts(result, counter.found, counter.ignored);
   }

   private static void addFilteredParts(
      Entity entity, Iterable<Entity> parts, List<Entity> result, Set<Entity> seen, CompanionMountContactService.PartCounter counter
   ) {
      for (Entity part : parts) {
         if (part != null && isPartOf(entity, part) && seen.add(part)) {
            counter.found++;
            if (isUsableRelatedPart(entity, part)) {
               result.add(part);
            } else {
               counter.ignored++;
            }
         }
      }
   }

   private static boolean broadMultipartContact(Entity first, List<AABB> firstBoxes, Entity second, List<AABB> secondBoxes, double inflate) {
      double firstPadding = broadContactPadding(first, firstBoxes, inflate);
      double secondPadding = broadContactPadding(second, secondBoxes, inflate);
      if (firstPadding <= 0.0 && secondPadding <= 0.0) {
         return false;
      }

      AABB firstUnion = union(firstBoxes).inflate(firstPadding);
      AABB secondUnion = union(secondBoxes).inflate(secondPadding);
      return firstUnion.intersects(secondUnion);
   }

   private static boolean sweptContact(Entity first, List<AABB> firstBoxes, Entity second, List<AABB> secondBoxes) {
      Vec3 firstSweep = boundedSweep(first.getDeltaMovement());
      Vec3 secondSweep = boundedSweep(second.getDeltaMovement());
      if (firstSweep.lengthSqr() < 0.0025 && secondSweep.lengthSqr() < 0.0025) {
         return false;
      }

      for (AABB firstBox : firstBoxes) {
         AABB sweptFirst = sweepBox(firstBox, firstSweep);

         for (AABB secondBox : secondBoxes) {
            AABB sweptSecond = sweepBox(secondBox, secondSweep);
            if (sweptFirst.intersects(sweptSecond)) {
               return true;
            }
         }
      }

      return false;
   }

   private static AABB sweepBox(AABB box, Vec3 motion) {
      AABB swept = box.expandTowards(motion).expandTowards(motion.scale(-1.0));
      return swept.inflate(0.18);
   }

   private static Vec3 boundedSweep(Vec3 motion) {
      if (motion != null && Double.isFinite(motion.x) && Double.isFinite(motion.y) && Double.isFinite(motion.z)) {
         double length = motion.length();
         return length <= 3.0 ? motion : motion.scale(3.0 / length);
      } else {
         return Vec3.ZERO;
      }
   }

   private static double broadContactPadding(Entity entity, List<AABB> boxes, double inflate) {
      if (boxes.isEmpty()) {
         return 0.0;
      }

      AABB box = union(boxes);
      double width = Math.max(box.getXsize(), box.getZsize());
      double size = Math.max(width, box.getYsize());
      boolean multipartOrLarge = boxes.size() > 1 || Parts.isMultipart(entity) || Math.max(entity.getBbWidth(), entity.getBbHeight()) >= 3.0F || size >= 4.0;
      return !multipartOrLarge ? 0.0 : Math.max(0.45, Math.min(2.25, size * 0.1 + inflate * 0.5));
   }

   private static int addCustomContactSegmentBoxes(Entity entity, List<AABB> boxes, double inflate) {
      Vec3[] positions = customContactSegmentPositions(entity);
      if (positions != null && positions.length != 0) {
         double radius = Math.max(1.0, Math.min(4.0, Math.max(entity.getBbWidth(), entity.getBbHeight()) * 0.25)) + inflate;
         double vertical = Math.max(0.75, Math.min(3.0, entity.getBbHeight() * 0.18)) + inflate;
         int added = 0;

         for (Vec3 position : positions) {
            if (position != null && Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z)) {
               boxes.add(
                  new AABB(position.x - radius, position.y - vertical, position.z - radius, position.x + radius, position.y + vertical, position.z + radius)
               );
               added++;
            }
         }

         return added;
      } else {
         return 0;
      }
   }

   private static Vec3[] customContactSegmentPositions(Entity entity) {
      Optional<Method> method = CUSTOM_CONTACT_SEGMENT_METHODS.computeIfAbsent(entity.getClass(), CompanionMountContactService::findCustomContactSegmentMethod);
      if (method.isPresent()) {
         try {
            if (method.get().invoke(entity) instanceof Vec3[] positions) {
               return positions;
            }
         } catch (ReflectiveOperationException | RuntimeException var6) {
         }
      }

      Optional<Field> field = CUSTOM_CONTACT_SEGMENT_FIELDS.computeIfAbsent(entity.getClass(), CompanionMountContactService::findCustomContactSegmentField);
      if (field.isPresent()) {
         try {
            if (field.get().get(entity) instanceof Vec3[] positions) {
               return positions;
            }
         } catch (ReflectiveOperationException | RuntimeException var5) {
         }
      }

      return null;
   }

   private static Optional<Method> findCustomContactSegmentMethod(Class<?> type) {
      try {
         Method method = type.getMethod("getPosArray");
         if (method.getReturnType().isArray() && method.getReturnType().getComponentType() == Vec3.class && method.getParameterCount() == 0) {
            method.setAccessible(true);
            return Optional.of(method);
         }
      } catch (NoSuchMethodException | SecurityException var2) {
      }

      return Optional.empty();
   }

   private static Optional<Field> findCustomContactSegmentField(Class<?> type) {
      try {
         Field field = type.getField("posArray");
         if (field.getType().isArray() && field.getType().getComponentType() == Vec3.class) {
            field.setAccessible(true);
            return Optional.of(field);
         }
      } catch (NoSuchFieldException | SecurityException var2) {
      }

      return Optional.empty();
   }

   private static List<Entity> reflectedPartEntities(Entity entity) {
      List<Entity> parts = new ArrayList<>();
      Set<Entity> seen = new HashSet<>();

      for (Method method : CUSTOM_PART_METHODS.computeIfAbsent(entity.getClass(), CompanionMountContactService::findCustomPartMethods)) {
         try {
            collectEntities(method.invoke(entity), entity, parts, seen);
         } catch (ReflectiveOperationException | RuntimeException var7) {
         }
      }

      for (Field field : CUSTOM_PART_FIELDS.computeIfAbsent(entity.getClass(), CompanionMountContactService::findCustomPartFields)) {
         try {
            collectEntities(field.get(entity), entity, parts, seen);
         } catch (ReflectiveOperationException | RuntimeException var6) {
         }
      }

      return parts;
   }

   private static void collectEntities(Object value, Entity owner, List<Entity> parts, Set<Entity> seen) {
      if (value != null) {
         if (value instanceof Entity entity) {
            addReflectedPart(owner, entity, parts, seen);
         } else if (value instanceof Entity[] entities) {
            for (Entity entity : entities) {
               addReflectedPart(owner, entity, parts, seen);
            }
         } else {
            if (value instanceof Iterable) {
               for (Object entry : (Iterable)value) {
                  if (entry instanceof Entity entity) {
                     addReflectedPart(owner, entity, parts, seen);
                  }
               }
            }
         }
      }
   }

   private static void addReflectedPart(Entity owner, Entity part, List<Entity> parts, Set<Entity> seen) {
      if (part != null && part != owner && seen.add(part)) {
         if (!(Parts.isPart(part) && !isPartOf(owner, part))) {
            if (isUsableRelatedPart(owner, part)) {
               parts.add(part);
            }
         }
      }
   }

   private static boolean isUsableRelatedPart(Entity owner, Entity part) {
      if (part.level() != owner.level()) {
         return false;
      } else {
         AABB ownerBox = owner.getBoundingBox();
         AABB partBox = part.getBoundingBox();
         if (isFiniteBox(partBox) && (!(partBox.getXsize() < 0.001) || !(partBox.getYsize() < 0.001) || !(partBox.getZsize() < 0.001))) {
            double ownerWidth = Math.max(ownerBox.getXsize(), ownerBox.getZsize());
            double partWidth = Math.max(partBox.getXsize(), partBox.getZsize());
            double horizontal = Math.max(4.0, Math.min(28.0, ownerWidth * 2.4 + partWidth));
            double vertical = Math.max(3.0, Math.min(18.0, ownerBox.getYsize() * 0.85 + partBox.getYsize()));
            return ownerBox.inflate(horizontal, vertical, horizontal).intersects(partBox);
         } else {
            return false;
         }
      }
   }

   private static boolean isFiniteBox(AABB box) {
      return Double.isFinite(box.minX)
         && Double.isFinite(box.minY)
         && Double.isFinite(box.minZ)
         && Double.isFinite(box.maxX)
         && Double.isFinite(box.maxY)
         && Double.isFinite(box.maxZ);
   }

   private static boolean isPartOf(Entity owner, Entity part) {
      if (Parts.parentOf(part) == owner) {
         return true;
      }

      try {
         return part.is(owner);
      } catch (RuntimeException ignored) {
         return false;
      }
   }

   private static List<Method> findCustomPartMethods(Class<?> type) {
      List<Method> result = new ArrayList<>();

      for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
         for (Method method : current.getDeclaredMethods()) {
            if (method.getParameterCount() == 0 && looksLikePartMember(method.getName())) {
               Class<?> returnType = method.getReturnType();
               if (Entity.class.isAssignableFrom(returnType)
                  || returnType.isArray() && Entity.class.isAssignableFrom(returnType.getComponentType())
                  || Iterable.class.isAssignableFrom(returnType)) {
                  try {
                     method.setAccessible(true);
                     result.add(method);
                  } catch (RuntimeException var9) {
                  }
               }
            }
         }
      }

      return result;
   }

   private static List<Field> findCustomPartFields(Class<?> type) {
      List<Field> result = new ArrayList<>();

      for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
         for (Field field : current.getDeclaredFields()) {
            if (looksLikePartMember(field.getName())) {
               Class<?> fieldType = field.getType();
               if (Entity.class.isAssignableFrom(fieldType)
                  || fieldType.isArray() && Entity.class.isAssignableFrom(fieldType.getComponentType())
                  || Iterable.class.isAssignableFrom(fieldType)) {
                  try {
                     field.setAccessible(true);
                     result.add(field);
                  } catch (RuntimeException var9) {
                  }
               }
            }
         }
      }

      return result;
   }

   private static boolean looksLikePartMember(String name) {
      String lower = name.toLowerCase(Locale.ROOT);
      return lower.contains("part") || lower.contains("segment") || lower.contains("hitbox") || lower.contains("subentity") || lower.contains("sub_entity");
   }

   private record ContactBoxCacheKey(String dimension, int entityId, long inflateBits) {
   }

   private static final class PartCounter {
      private int found;
      private int ignored;
   }

   private record RelatedParts(List<Entity> parts, int found, int ignored) {
   }
}
