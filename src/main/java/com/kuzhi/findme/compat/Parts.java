package com.kuzhi.findme.compat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;

/**
 * Multi-part entities without a loader-provided part type: the vanilla ender dragon, plus any modded part
 * entity that exposes its owner through a {@code getParent()} or {@code getDragonParent()} method (Saint's
 * Dragons hitbox parts do).
 */
public final class Parts {
   private static final String[] PARENT_METHODS = new String[]{"getDragonParent", "getParent", "getParentMob"};
   private static final Map<Class<?>, Optional<Method>> PARENT_LOOKUP = new ConcurrentHashMap<>();

   private Parts() {
   }

   public static boolean isPart(Entity entity) {
      return entity != null && (entity instanceof EnderDragonPart || parentMethod(entity.getClass()).isPresent());
   }

   /** The entity a part belongs to, or null when {@code entity} is not a part. */
   public static Entity parentOf(Entity entity) {
      if (entity == null) {
         return null;
      } else if (entity instanceof EnderDragonPart dragonPart) {
         return dragonPart.parentMob;
      } else {
         Optional<Method> method = parentMethod(entity.getClass());
         if (method.isEmpty()) {
            return null;
         }

         try {
            return method.get().invoke(entity) instanceof Entity parent && parent != entity ? parent : null;
         } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
         }
      }
   }

   public static boolean isMultipart(Entity entity) {
      return entity instanceof EnderDragon;
   }

   public static Entity[] of(Entity entity) {
      return entity instanceof EnderDragon dragon ? dragon.getSubEntities() : new Entity[0];
   }

   /** Every part entity currently loaded in the level. */
   public static List<Entity> inLevel(ServerLevel level) {
      List<Entity> parts = new ArrayList<>();
      for (Entity entity : level.getAllEntities()) {
         if (entity instanceof EnderDragon dragon) {
            parts.addAll(List.of(dragon.getSubEntities()));
         } else if (entity != null && !(entity instanceof EnderDragonPart) && parentMethod(entity.getClass()).isPresent()) {
            parts.add(entity);
         }
      }

      return parts;
   }

   private static Optional<Method> parentMethod(Class<?> type) {
      return PARENT_LOOKUP.computeIfAbsent(type, Parts::findParentMethod);
   }

   private static Optional<Method> findParentMethod(Class<?> type) {
      // Only dedicated part classes: an ordinary mob with an unrelated getParent() must not count as a part.
      if (!type.getSimpleName().toLowerCase(java.util.Locale.ROOT).contains("part")) {
         return Optional.empty();
      }

      for (String name : PARENT_METHODS) {
         try {
            Method method = type.getMethod(name);
            if (Entity.class.isAssignableFrom(method.getReturnType())) {
               method.setAccessible(true);
               return Optional.of(method);
            }
         } catch (ReflectiveOperationException | RuntimeException exception) {
         }
      }

      return Optional.empty();
   }
}
