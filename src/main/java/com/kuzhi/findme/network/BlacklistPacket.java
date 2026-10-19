package com.kuzhi.findme.network;

import com.kuzhi.findme.client.ClientBlacklistState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import com.kuzhi.findme.compat.NetworkEvent;

public record BlacklistPacket(List<BlacklistPacket.Entry> entries) implements CustomPacketPayload {
   public static final Type<BlacklistPacket> TYPE = new Type(Identifier.fromNamespaceAndPath("find_me", "blacklist"));
   public static final StreamCodec<RegistryFriendlyByteBuf, BlacklistPacket> STREAM_CODEC = NetworkCodecs.of(BlacklistPacket::encode, BlacklistPacket::decode);

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void encode(BlacklistPacket packet, FriendlyByteBuf buffer) {
      buffer.writeVarInt(packet.entries.size());

      for (BlacklistPacket.Entry entry : packet.entries) {
         buffer.writeUtf(entry.entityType, 128);
      }
   }

   public static BlacklistPacket decode(FriendlyByteBuf buffer) {
      int size = buffer.readVarInt();
      ArrayList<BlacklistPacket.Entry> entries = new ArrayList<>(size);

      for (int i = 0; i < size; i++) {
         entries.add(new BlacklistPacket.Entry(buffer.readUtf(128)));
      }

      return new BlacklistPacket(entries);
   }

   public static void handle(BlacklistPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> ClientBlacklistState.update(packet.entries));
      context.setPacketHandled(true);
   }

   public record Entry(String entityType) {
   }
}
