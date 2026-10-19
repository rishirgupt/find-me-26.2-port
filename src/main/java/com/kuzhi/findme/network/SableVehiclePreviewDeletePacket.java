package com.kuzhi.findme.network;

import com.kuzhi.findme.client.SableVehicleScreenshotPreviewCache;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import com.kuzhi.findme.compat.NetworkEvent;

public record SableVehiclePreviewDeletePacket(UUID uuid) implements CustomPacketPayload {
   public static final Type<SableVehiclePreviewDeletePacket> TYPE = new Type(Identifier.fromNamespaceAndPath("find_me", "sable_vehicle_preview_delete"));
   public static final StreamCodec<RegistryFriendlyByteBuf, SableVehiclePreviewDeletePacket> STREAM_CODEC = NetworkCodecs.of(
      SableVehiclePreviewDeletePacket::encode, SableVehiclePreviewDeletePacket::decode
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void encode(SableVehiclePreviewDeletePacket packet, FriendlyByteBuf buffer) {
      buffer.writeUUID(packet.uuid);
   }

   public static SableVehiclePreviewDeletePacket decode(FriendlyByteBuf buffer) {
      return new SableVehiclePreviewDeletePacket(buffer.readUUID());
   }

   public static void handle(SableVehiclePreviewDeletePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> SableVehicleScreenshotPreviewCache.delete(packet.uuid()));
      context.setPacketHandled(true);
   }
}
