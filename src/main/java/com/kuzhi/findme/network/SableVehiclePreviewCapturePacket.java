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

public record SableVehiclePreviewCapturePacket(UUID uuid, double minX, double minY, double minZ, double maxX, double maxY, double maxZ)
   implements CustomPacketPayload {
   public static final Type<SableVehiclePreviewCapturePacket> TYPE = new Type(Identifier.fromNamespaceAndPath("find_me", "sable_vehicle_preview_capture"));
   public static final StreamCodec<RegistryFriendlyByteBuf, SableVehiclePreviewCapturePacket> STREAM_CODEC = NetworkCodecs.of(
      SableVehiclePreviewCapturePacket::encode, SableVehiclePreviewCapturePacket::decode
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void encode(SableVehiclePreviewCapturePacket packet, FriendlyByteBuf buffer) {
      buffer.writeUUID(packet.uuid);
      buffer.writeDouble(packet.minX);
      buffer.writeDouble(packet.minY);
      buffer.writeDouble(packet.minZ);
      buffer.writeDouble(packet.maxX);
      buffer.writeDouble(packet.maxY);
      buffer.writeDouble(packet.maxZ);
   }

   public static SableVehiclePreviewCapturePacket decode(FriendlyByteBuf buffer) {
      return new SableVehiclePreviewCapturePacket(
         buffer.readUUID(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble()
      );
   }

   public static void handle(SableVehiclePreviewCapturePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> SableVehicleScreenshotPreviewCache.capture(packet));
      context.setPacketHandled(true);
   }
}
