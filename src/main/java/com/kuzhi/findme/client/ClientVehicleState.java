package com.kuzhi.findme.client;

import com.kuzhi.findme.network.VehicleListPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.nbt.CompoundTag;

public final class ClientVehicleState {
   private static int activeIndex = -1;
   private static List<VehicleListPacket.Entry> wheelEntries = List.of();
   private static List<VehicleListPacket.Entry> allEntries = List.of();

   private ClientVehicleState() {
   }

   public static void update(int active, List<VehicleListPacket.Entry> wheel, List<VehicleListPacket.Entry> all) {
      activeIndex = active;
      List<VehicleListPacket.Entry> previous = new ArrayList<>(wheelEntries.size() + allEntries.size());
      previous.addAll(wheelEntries);
      previous.addAll(allEntries);
      wheelEntries = List.copyOf(mergePreviewTags(previous, wheel));
      allEntries = List.copyOf(mergePreviewTags(previous, all));
      CompanionDetailPreviewRenderer.enqueueWarm(wheelEntries.stream().map(VehicleListPacket.Entry::asPreviewEntry).toList(), "minecraft:boat");
   }

   public static int activeIndex() {
      return activeIndex;
   }

   public static List<VehicleListPacket.Entry> wheelEntries() {
      return wheelEntries;
   }

   public static List<VehicleListPacket.Entry> allEntries() {
      return allEntries;
   }

   private static List<VehicleListPacket.Entry> mergePreviewTags(List<VehicleListPacket.Entry> previous, List<VehicleListPacket.Entry> incoming) {
      Map<UUID, VehicleListPacket.Entry> previousByUuid = previous.stream()
         .collect(Collectors.toMap(VehicleListPacket.Entry::uuid, Function.identity(), (left, right) -> left));
      ArrayList<VehicleListPacket.Entry> merged = new ArrayList<>(incoming.size());

      for (VehicleListPacket.Entry entry : incoming) {
         CompoundTag previewTag = entry.previewTag();
         VehicleListPacket.Entry old = previousByUuid.get(entry.uuid());
         if (previewTag == null && old != null && old.previewTag() != null && !entry.entityType().isBlank() && entry.entityType().equals(old.entityType())) {
            previewTag = old.previewTag().copy();
         }

         merged.add(
            new VehicleListPacket.Entry(
               entry.uuid(), entry.entityId(), entry.entityType(), entry.name(), entry.loaded(), entry.alive(), entry.deployed(), entry.ridden(), previewTag
            )
         );
      }

      return merged;
   }
}
