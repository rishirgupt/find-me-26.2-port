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

public record DeadCompanionListPacket(List<DeadCompanionListPacket.Entry> entries) implements CustomPacketPayload {
   public static final Type<DeadCompanionListPacket> TYPE = new Type(Identifier.fromNamespaceAndPath("find_me", "dead_companion_list"));
   public static final StreamCodec<RegistryFriendlyByteBuf, DeadCompanionListPacket> STREAM_CODEC = NetworkCodecs.of(
      DeadCompanionListPacket::encode, DeadCompanionListPacket::decode
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void encode(DeadCompanionListPacket packet, FriendlyByteBuf buffer) {
      buffer.writeVarInt(packet.entries.size());

      for (DeadCompanionListPacket.Entry entry : packet.entries) {
         buffer.writeUUID(entry.uuid);
         buffer.writeEnum(entry.kind);
         buffer.writeUtf(entry.entityType, 128);
         buffer.writeUtf(entry.name, 128);
         buffer.writeFloat(entry.health);
         buffer.writeFloat(entry.maxHealth);
         buffer.writeFloat(entry.armor);
         buffer.writeEnum(entry.moveType);
         buffer.writeNbt(entry.previewTag);
      }
   }

   public static DeadCompanionListPacket decode(FriendlyByteBuf buffer) {
      int size = buffer.readVarInt();
      ArrayList<DeadCompanionListPacket.Entry> entries = new ArrayList<>(size);

      for (int i = 0; i < size; i++) {
         entries.add(
            new DeadCompanionListPacket.Entry(
               buffer.readUUID(),
               (CompanionKind)buffer.readEnum(CompanionKind.class),
               buffer.readUtf(128),
               buffer.readUtf(128),
               buffer.readFloat(),
               buffer.readFloat(),
               buffer.readFloat(),
               (CompanionMoveType)buffer.readEnum(CompanionMoveType.class),
               buffer.readNbt()
            )
         );
      }

      return new DeadCompanionListPacket(entries);
   }

   public static void handle(DeadCompanionListPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> ClientCompanionState.updateDead(packet.entries));
      context.setPacketHandled(true);
   }

   public record Entry(
      UUID uuid,
      CompanionKind kind,
      String entityType,
      String name,
      float health,
      float maxHealth,
      float armor,
      CompanionMoveType moveType,
      CompoundTag previewTag
   ) {
   }
}
