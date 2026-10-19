package com.kuzhi.findme.network;

import com.kuzhi.findme.client.ClientStorageEffectState;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import com.kuzhi.findme.compat.NetworkEvent;

public record StorageEffectPacket(int entityId, double x, double y, double z, float radius, float height, int durationTicks) implements CustomPacketPayload {
   public static final Type<StorageEffectPacket> TYPE = new Type(Identifier.fromNamespaceAndPath("find_me", "storage_effect"));
   public static final StreamCodec<RegistryFriendlyByteBuf, StorageEffectPacket> STREAM_CODEC = NetworkCodecs.of(
      StorageEffectPacket::encode, StorageEffectPacket::decode
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void encode(StorageEffectPacket packet, FriendlyByteBuf buffer) {
      buffer.writeVarInt(packet.entityId);
      buffer.writeDouble(packet.x);
      buffer.writeDouble(packet.y);
      buffer.writeDouble(packet.z);
      buffer.writeFloat(packet.radius);
      buffer.writeFloat(packet.height);
      buffer.writeVarInt(packet.durationTicks);
   }

   public static StorageEffectPacket decode(FriendlyByteBuf buffer) {
      return new StorageEffectPacket(
         buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat(), buffer.readFloat(), buffer.readVarInt()
      );
   }

   public static void handle(StorageEffectPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> ClientStorageEffectState.start(packet));
      context.setPacketHandled(true);
   }
}
