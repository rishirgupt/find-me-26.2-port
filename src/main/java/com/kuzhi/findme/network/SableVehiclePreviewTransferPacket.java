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

public record SableVehiclePreviewTransferPacket(UUID fromUuid, UUID toUuid) implements CustomPacketPayload {
   public static final Type<SableVehiclePreviewTransferPacket> TYPE = new Type(
      Identifier.fromNamespaceAndPath("find_me", "sable_vehicle_preview_transfer")
   );
   public static final StreamCodec<RegistryFriendlyByteBuf, SableVehiclePreviewTransferPacket> STREAM_CODEC = NetworkCodecs.of(
      SableVehiclePreviewTransferPacket::encode, SableVehiclePreviewTransferPacket::decode
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void encode(SableVehiclePreviewTransferPacket packet, FriendlyByteBuf buffer) {
      buffer.writeUUID(packet.fromUuid);
      buffer.writeUUID(packet.toUuid);
   }

   public static SableVehiclePreviewTransferPacket decode(FriendlyByteBuf buffer) {
      return new SableVehiclePreviewTransferPacket(buffer.readUUID(), buffer.readUUID());
   }

   public static void handle(SableVehiclePreviewTransferPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> SableVehicleScreenshotPreviewCache.transfer(packet.fromUuid(), packet.toUuid()));
      context.setPacketHandled(true);
   }
}
