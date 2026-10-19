package com.kuzhi.findme.server;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.FindMe;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

final class FindMeDebugLogger {
   private static final Map<String, Long> BOX_LOG_TICKS = new HashMap<>();
   private static long lastBoxLogTick = Long.MIN_VALUE;

   private FindMeDebugLogger() {
   }

   static boolean enabled() {
      return Config.debugDiagnostics;
   }

   static void info(String area, String message, Object... args) {
      if (enabled()) {
         FindMe.LOGGER.info("[FindMe debug/{}] " + message, prepend(area, args));
      }
   }

   static void contactBoxes(
      Entity entity,
      double inflate,
      List<AABB> boxes,
      int officialParts,
      int officialPartsFound,
      int ignoredOfficialParts,
      boolean reflectedUsed,
      int reflectedParts,
      boolean segmentsUsed
   ) {
      if (enabled() && entity != null) {
         long tick = entity.level() == null ? -1L : entity.level().getGameTime();
         if (tick != lastBoxLogTick) {
            BOX_LOG_TICKS.clear();
            lastBoxLogTick = tick;
         }

         String key = entity.getId() + ":" + inflate;
         Long previousTick = BOX_LOG_TICKS.put(key, tick);
         if (previousTick == null || previousTick != tick) {
            info(
               "contact-boxes",
               "{} inflate={} main={} boxes={} officialParts={}/{} ignoredOfficialParts={} reflectedUsed={} reflectedParts={} customSegments={}",
               entity(entity),
               round(inflate),
               box(entity.getBoundingBox()),
               boxesSummary(boxes),
               officialParts,
               officialPartsFound,
               ignoredOfficialParts,
               reflectedUsed,
               reflectedParts,
               segmentsUsed
            );
         }
      }
   }

   static String entity(Entity entity) {
      if (!enabled()) {
         return "";
      }

      if (entity == null) {
         return "null";
      }

      Identifier key = EntityType.getKey(entity.getType());
      return key + "#" + entity.getId() + "/" + entity.getUUID();
   }

   static String box(AABB box) {
      if (!enabled()) {
         return "";
      } else {
         return box == null
            ? "null"
            : "["
               + round(box.minX)
               + ","
               + round(box.minY)
               + ","
               + round(box.minZ)
               + " -> "
               + round(box.maxX)
               + ","
               + round(box.maxY)
               + ","
               + round(box.maxZ)
               + " size="
               + round(box.getXsize())
               + "x"
               + round(box.getYsize())
               + "x"
               + round(box.getZsize())
               + "]";
      }
   }

   private static String boxesSummary(List<AABB> boxes) {
      if (!enabled()) {
         return "";
      }

      if (boxes != null && !boxes.isEmpty()) {
         StringBuilder builder = new StringBuilder("[");
         int limit = Math.min(boxes.size(), 6);

         for (int i = 0; i < limit; i++) {
            if (i > 0) {
               builder.append("; ");
            }

            builder.append(i).append('=').append(box(boxes.get(i)));
         }

         if (boxes.size() > limit) {
            builder.append("; +").append(boxes.size() - limit).append(" more");
         }

         return builder.append(']').toString();
      } else {
         return "[]";
      }
   }

   private static Object[] prepend(String first, Object[] rest) {
      Object[] combined = new Object[rest.length + 1];
      combined[0] = first;
      System.arraycopy(rest, 0, combined, 1, rest.length);
      return combined;
   }

   private static String round(double value) {
      return String.format(Locale.ROOT, "%.3f", value);
   }
}
