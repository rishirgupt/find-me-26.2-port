package com.kuzhi.findme.network;

import com.kuzhi.findme.server.VehicleManager;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import com.kuzhi.findme.compat.NetworkEvent;

public record VehicleRenamePacket(boolean wheel, int index, String name) implements CustomPacketPayload {
   public static final Type<VehicleRenamePacket> TYPE = new Type(Identifier.fromNamespaceAndPath("find_me", "vehicle_rename"));
   public static final StreamCodec<RegistryFriendlyByteBuf, VehicleRenamePacket> STREAM_CODEC = NetworkCodecs.of(
      VehicleRenamePacket::encode, VehicleRenamePacket::decode
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void encode(VehicleRenamePacket packet, FriendlyByteBuf buffer) {
      buffer.writeBoolean(packet.wheel);
      buffer.writeInt(packet.index);
      buffer.writeUtf(packet.name == null ? "" : packet.name, 64);
   }

   public static VehicleRenamePacket decode(FriendlyByteBuf buffer) {
      return new VehicleRenamePacket(buffer.readBoolean(), buffer.readInt(), buffer.readUtf(64));
   }

   public static void handle(VehicleRenamePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> {
         if (context.getSender() != null) {
            VehicleManager.rename(context.getSender(), packet.wheel, packet.index, packet.name);
         }
      });
      context.setPacketHandled(true);
   }
}
