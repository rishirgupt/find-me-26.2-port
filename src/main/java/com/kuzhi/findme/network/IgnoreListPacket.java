package com.kuzhi.findme.network;

import com.kuzhi.findme.client.ClientIgnoreState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import com.kuzhi.findme.compat.NetworkEvent;

public record IgnoreListPacket(List<IgnoreListPacket.Entry> entries) implements CustomPacketPayload {
   public static final Type<IgnoreListPacket> TYPE = new Type(Identifier.fromNamespaceAndPath("find_me", "ignore_list"));
   public static final StreamCodec<RegistryFriendlyByteBuf, IgnoreListPacket> STREAM_CODEC = NetworkCodecs.of(
      IgnoreListPacket::encode, IgnoreListPacket::decode
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void encode(IgnoreListPacket packet, FriendlyByteBuf buffer) {
      buffer.writeVarInt(packet.entries.size());

      for (IgnoreListPacket.Entry entry : packet.entries) {
         buffer.writeUUID(entry.uuid);
         buffer.writeUtf(entry.name, 128);
      }
   }

   public static IgnoreListPacket decode(FriendlyByteBuf buffer) {
      int size = buffer.readVarInt();
      ArrayList<IgnoreListPacket.Entry> entries = new ArrayList<>(size);

      for (int i = 0; i < size; i++) {
         entries.add(new IgnoreListPacket.Entry(buffer.readUUID(), buffer.readUtf(128)));
      }

      return new IgnoreListPacket(entries);
   }

   public static void handle(IgnoreListPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> ClientIgnoreState.update(packet.entries));
      context.setPacketHandled(true);
   }

   public record Entry(UUID uuid, String name) {
   }
}
