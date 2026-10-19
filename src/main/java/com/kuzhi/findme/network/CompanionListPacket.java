package com.kuzhi.findme.network;

import com.kuzhi.findme.client.ClientCompanionState;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import com.kuzhi.findme.compat.NetworkEvent;

public record CompanionListPacket(CompanionKind kind, int activeIndex, List<CompanionListPacket.Entry> entries) implements CustomPacketPayload {
   public static final Type<CompanionListPacket> TYPE = new Type(Identifier.fromNamespaceAndPath("find_me", "companion_list"));
   public static final StreamCodec<RegistryFriendlyByteBuf, CompanionListPacket> STREAM_CODEC = NetworkCodecs.of(
      CompanionListPacket::encode, CompanionListPacket::decode
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void encode(CompanionListPacket packet, FriendlyByteBuf buffer) {
      buffer.writeEnum(packet.kind);
      buffer.writeInt(packet.activeIndex);
      buffer.writeVarInt(packet.entries.size());

      for (CompanionListPacket.Entry entry : packet.entries) {
         buffer.writeUUID(entry.uuid);
         buffer.writeInt(entry.entityId);
         buffer.writeUtf(entry.entityType, 128);
         buffer.writeUtf(entry.name, 128);
         buffer.writeBoolean(entry.loaded);
         buffer.writeBoolean(entry.alive);
         buffer.writeBoolean(entry.deployed);
         buffer.writeBoolean(entry.ridden);
         buffer.writeBoolean(entry.hasHome);
         buffer.writeBoolean(entry.homeResident);
         buffer.writeFloat(entry.health);
         buffer.writeFloat(entry.maxHealth);
         buffer.writeFloat(entry.armor);
         buffer.writeEnum(entry.moveType);
         buffer.writeNbt(entry.previewTag);
      }
   }

   public static CompanionListPacket decode(FriendlyByteBuf buffer) {
      CompanionKind kind = (CompanionKind)buffer.readEnum(CompanionKind.class);
      int activeIndex = buffer.readInt();
      int size = buffer.readVarInt();
      ArrayList<CompanionListPacket.Entry> entries = new ArrayList<>(size);

      for (int i = 0; i < size; i++) {
         entries.add(
            new CompanionListPacket.Entry(
               buffer.readUUID(),
               buffer.readInt(),
               buffer.readUtf(128),
               buffer.readUtf(128),
               buffer.readBoolean(),
               buffer.readBoolean(),
               buffer.readBoolean(),
               buffer.readBoolean(),
               buffer.readBoolean(),
               buffer.readBoolean(),
               buffer.readFloat(),
               buffer.readFloat(),
               buffer.readFloat(),
               (CompanionMoveType)buffer.readEnum(CompanionMoveType.class),
               buffer.readNbt()
            )
         );
      }

      return new CompanionListPacket(kind, activeIndex, entries);
   }

   public static void handle(CompanionListPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> ClientCompanionState.update(packet.kind, packet.activeIndex, packet.entries));
      context.setPacketHandled(true);
   }

   public record Entry(
      UUID uuid,
      int entityId,
      String entityType,
      String name,
      boolean loaded,
      boolean alive,
      boolean deployed,
      boolean ridden,
      boolean hasHome,
      boolean homeResident,
      float health,
      float maxHealth,
      float armor,
      CompanionMoveType moveType,
      CompoundTag previewTag
   ) {
   }
}
