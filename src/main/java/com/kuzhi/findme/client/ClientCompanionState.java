package com.kuzhi.findme.client;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.network.CompanionListPacket;
import com.kuzhi.findme.network.DeadCompanionListPacket;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.nbt.CompoundTag;

public final class ClientCompanionState {
   private static final Map<CompanionKind, List<CompanionListPacket.Entry>> ENTRIES = new EnumMap<>(CompanionKind.class);
   private static final Map<CompanionKind, Integer> ACTIVE = new EnumMap<>(CompanionKind.class);
   private static List<DeadCompanionListPacket.Entry> DEAD_ENTRIES = List.of();

   private ClientCompanionState() {
   }

   public static void update(CompanionKind kind, int activeIndex, List<CompanionListPacket.Entry> entries) {
      List<CompanionListPacket.Entry> merged = mergePreviewTags(ENTRIES.getOrDefault(kind, List.of()), entries);
      ENTRIES.put(kind, List.copyOf(merged));
      ACTIVE.put(kind, activeIndex);
      CompanionDetailPreviewRenderer.enqueueWarm(merged, fallbackPreviewType(kind));
   }

   public static List<CompanionListPacket.Entry> entries(CompanionKind kind) {
      return ENTRIES.getOrDefault(kind, List.of());
   }

   public static int activeIndex(CompanionKind kind) {
      return ACTIVE.getOrDefault(kind, -1);
   }

   public static void updateDead(List<DeadCompanionListPacket.Entry> entries) {
      DEAD_ENTRIES = List.copyOf(entries);
   }

   public static List<DeadCompanionListPacket.Entry> deadEntries() {
      return DEAD_ENTRIES;
   }

   private static String fallbackPreviewType(CompanionKind kind) {
      return kind == CompanionKind.MOUNT ? "minecraft:horse" : "minecraft:wolf";
   }

   private static List<CompanionListPacket.Entry> mergePreviewTags(List<CompanionListPacket.Entry> previous, List<CompanionListPacket.Entry> incoming) {
      Map<UUID, CompanionListPacket.Entry> previousByUuid = previous.stream()
         .collect(Collectors.toMap(CompanionListPacket.Entry::uuid, Function.identity(), (left, right) -> left));
      ArrayList<CompanionListPacket.Entry> merged = new ArrayList<>(incoming.size());

      for (CompanionListPacket.Entry entry : incoming) {
         CompoundTag previewTag = entry.previewTag();
         CompanionListPacket.Entry old = previousByUuid.get(entry.uuid());
         if (previewTag == null && old != null && old.previewTag() != null && !entry.entityType().isBlank() && entry.entityType().equals(old.entityType())) {
            previewTag = old.previewTag().copy();
         }

         merged.add(copyWithPreview(entry, previewTag));
      }

      return merged;
   }

   private static CompanionListPacket.Entry copyWithPreview(CompanionListPacket.Entry entry, CompoundTag previewTag) {
      CompanionMoveType moveType = entry.moveType();
      return new CompanionListPacket.Entry(
         entry.uuid(),
         entry.entityId(),
         entry.entityType(),
         entry.name(),
         entry.loaded(),
         entry.alive(),
         entry.deployed(),
         entry.ridden(),
         entry.hasHome(),
         entry.homeResident(),
         entry.health(),
         entry.maxHealth(),
         entry.armor(),
         moveType,
         previewTag
      );
   }
}
