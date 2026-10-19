package com.kuzhi.findme.server;

import com.kuzhi.findme.common.CompanionKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class PlayerCompanionWheelService {
   private PlayerCompanionWheelService() {
   }

   static List<UUID> wheelOrder(PlayerCompanionData data, CompanionKind kind) {
      ArrayList<UUID> order = new ArrayList<>();
      List<UUID> list = data.companions.get(kind);

      for (UUID uuid : data.wheelSlots.get(kind)) {
         if (list.contains(uuid) && !order.contains(uuid)) {
            order.add(uuid);
         }
      }

      for (UUID uuid : list) {
         if (!order.contains(uuid)) {
            order.add(uuid);
         }
      }

      return List.copyOf(order);
   }

   static int activeWheelIndex(PlayerCompanionData data, CompanionKind kind) {
      Optional<UUID> active = data.active(kind);
      return active.isEmpty() ? -1 : wheelOrder(data, kind).indexOf(active.get());
   }

   static Optional<UUID> wheelUuidAt(PlayerCompanionData data, CompanionKind kind, int index) {
      List<UUID> order = wheelOrder(data, kind);
      return index >= 0 && index < order.size() ? Optional.of(order.get(index)) : Optional.empty();
   }

   static boolean setWheelSlot(PlayerCompanionData data, CompanionKind kind, int sourceIndex, int slotIndex) {
      Optional<UUID> maybeUuid = wheelUuidAt(data, kind, sourceIndex);
      if (!maybeUuid.isEmpty() && slotIndex >= 0) {
         UUID uuid = maybeUuid.get();
         List<UUID> slots = data.wheelSlots.get(kind);
         slots.remove(uuid);
         int insertAt = Math.min(slotIndex, slots.size());
         slots.add(insertAt, uuid);
         return true;
      } else {
         return false;
      }
   }

   static boolean reorder(PlayerCompanionData data, CompanionKind kind, int from, int to) {
      List<UUID> list = data.companions.get(kind);
      if (from >= 0 && from < list.size() && to >= 0 && to < list.size()) {
         UUID uuid = list.remove(from);
         list.add(to, uuid);
         data.activeIndexes.put(kind, to);
         return true;
      } else {
         return false;
      }
   }
}
