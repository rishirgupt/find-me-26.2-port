package com.kuzhi.findme.network;

import com.kuzhi.findme.client.ClientVehicleSealEffectState;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import com.kuzhi.findme.compat.NetworkEvent;

public record VehicleSealEffectPacket(double x, double y, double z, float radius, float height, int durationTicks) implements CustomPacketPayload {
   public static final Type<VehicleSealEffectPacket> TYPE = new Type(Identifier.fromNamespaceAndPath("find_me", "vehicle_seal_effect"));
   public static final StreamCodec<RegistryFriendlyByteBuf, VehicleSealEffectPacket> STREAM_CODEC = NetworkCodecs.of(
      VehicleSealEffectPacket::encode, VehicleSealEffectPacket::decode
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void encode(VehicleSealEffectPacket packet, FriendlyByteBuf buffer) {
      buffer.writeDouble(packet.x);
      buffer.writeDouble(packet.y);
      buffer.writeDouble(packet.z);
      buffer.writeFloat(packet.radius);
      buffer.writeFloat(packet.height);
      buffer.writeVarInt(packet.durationTicks);
   }

   public static VehicleSealEffectPacket decode(FriendlyByteBuf buffer) {
      return new VehicleSealEffectPacket(
         buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat(), buffer.readFloat(), buffer.readVarInt()
      );
   }

   public static void handle(VehicleSealEffectPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> ClientVehicleSealEffectState.start(packet));
      context.setPacketHandled(true);
   }
}
