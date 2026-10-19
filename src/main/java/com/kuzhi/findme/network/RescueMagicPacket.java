package com.kuzhi.findme.network;

import com.kuzhi.findme.client.ClientRescueMagicRenderState;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import com.kuzhi.findme.compat.NetworkEvent;

public record RescueMagicPacket(
   double x, double y, double z, float radius, float height, float yaw, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose
) implements CustomPacketPayload {
   public static final Type<RescueMagicPacket> TYPE = new Type(Identifier.fromNamespaceAndPath("find_me", "rescue_magic"));
   public static final StreamCodec<RegistryFriendlyByteBuf, RescueMagicPacket> STREAM_CODEC = NetworkCodecs.of(
      RescueMagicPacket::encode, RescueMagicPacket::decode
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void encode(RescueMagicPacket packet, FriendlyByteBuf buffer) {
      buffer.writeDouble(packet.x);
      buffer.writeDouble(packet.y);
      buffer.writeDouble(packet.z);
      buffer.writeFloat(packet.radius);
      buffer.writeFloat(packet.height);
      buffer.writeFloat(packet.yaw);
      buffer.writeVarInt(packet.durationTicks);
      buffer.writeEnum(packet.style);
      buffer.writeEnum(packet.purpose);
   }

   public static RescueMagicPacket decode(FriendlyByteBuf buffer) {
      return new RescueMagicPacket(
         buffer.readDouble(),
         buffer.readDouble(),
         buffer.readDouble(),
         buffer.readFloat(),
         buffer.readFloat(),
         buffer.readFloat(),
         buffer.readVarInt(),
         (RescueMagicPacket.Style)buffer.readEnum(RescueMagicPacket.Style.class),
         (RescueMagicPacket.Purpose)buffer.readEnum(RescueMagicPacket.Purpose.class)
      );
   }

   public static void handle(RescueMagicPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> ClientRescueMagicRenderState.start(packet));
      context.setPacketHandled(true);
   }

   public enum Purpose {
      SUMMON,
      RESCUE;
   }

   public enum Style {
      VERTICAL_PORTAL,
      GROUND_CIRCLE;
   }
}
