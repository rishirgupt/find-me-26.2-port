package com.kuzhi.findme.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class PlayerCompanionAutoBindIgnoreService {
   private PlayerCompanionAutoBindIgnoreService() {
   }

   static void ignore(PlayerCompanionData data, UUID uuid) {
      ignore(data, uuid, "");
   }

   static void ignore(PlayerCompanionData data, UUID uuid, String name) {
      data.autoBindIgnored.add(uuid);
      if (name != null && !name.isBlank()) {
         data.autoBindIgnoredNames.put(uuid, name);
      }
   }

   static boolean isIgnored(PlayerCompanionData data, UUID uuid) {
      return data.autoBindIgnored.contains(uuid);
   }

   static List<UUID> ignoredList(PlayerCompanionData data) {
      return List.copyOf(data.autoBindIgnored);
   }

   static boolean removeIgnored(PlayerCompanionData data, int index) {
      ArrayList<UUID> list = new ArrayList<>(data.autoBindIgnored);
      if (index >= 0 && index < list.size()) {
         UUID uuid = list.get(index);
         data.autoBindIgnoredNames.remove(uuid);
         return data.autoBindIgnored.remove(uuid);
      } else {
         return false;
      }
   }

   static void clearIgnored(PlayerCompanionData data) {
      data.autoBindIgnored.clear();
      data.autoBindIgnoredNames.clear();
   }

   static String ignoredName(PlayerCompanionData data, UUID uuid) {
      return data.autoBindIgnoredNames.getOrDefault(uuid, "");
   }

   static void blacklistType(PlayerCompanionData data, String entityType) {
      String normalized = normalizeType(entityType);
      if (!normalized.isBlank()) {
         data.autoBindTypeBlacklist.add(normalized);
      }
   }

   static boolean isTypeBlacklisted(PlayerCompanionData data, String entityType) {
      String normalized = normalizeType(entityType);
      return !normalized.isBlank() && data.autoBindTypeBlacklist.contains(normalized);
   }

   static List<String> blacklistedTypes(PlayerCompanionData data) {
      ArrayList<String> list = new ArrayList<>(data.autoBindTypeBlacklist);
      list.sort(Comparator.naturalOrder());
      return List.copyOf(list);
   }

   static boolean removeBlacklistedType(PlayerCompanionData data, int index) {
      List<String> list = blacklistedTypes(data);
      return index >= 0 && index < list.size() ? data.autoBindTypeBlacklist.remove(list.get(index)) : false;
   }

   private static String normalizeType(String entityType) {
      return entityType == null ? "" : entityType.trim().toLowerCase();
   }
}
