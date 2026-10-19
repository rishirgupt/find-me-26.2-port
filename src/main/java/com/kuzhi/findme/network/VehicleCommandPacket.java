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

public record VehicleCommandPacket(VehicleCommandPacket.Action action, int value) implements CustomPacketPayload {
   public static final Type<VehicleCommandPacket> TYPE = new Type(Identifier.fromNamespaceAndPath("find_me", "vehicle_command"));
   public static final StreamCodec<RegistryFriendlyByteBuf, VehicleCommandPacket> STREAM_CODEC = NetworkCodecs.of(
      VehicleCommandPacket::encode, VehicleCommandPacket::decode
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void encode(VehicleCommandPacket packet, FriendlyByteBuf buffer) {
      buffer.writeEnum(packet.action);
      buffer.writeInt(packet.value);
   }

   public static VehicleCommandPacket decode(FriendlyByteBuf buffer) {
      return new VehicleCommandPacket((VehicleCommandPacket.Action)buffer.readEnum(VehicleCommandPacket.Action.class), buffer.readInt());
   }

   public static void handle(VehicleCommandPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> {
         if (context.getSender() != null) {
            VehicleManager.handle(context.getSender(), packet.action, packet.value);
         }
      });
      context.setPacketHandled(true);
   }

   public enum Action {
      SYNC,
      SELECT,
      SELECT_SUMMON,
      RECALL,
      REORDER_WHEEL,
      ADD_TO_WHEEL,
      REMOVE_FROM_WHEEL,
      REMOVE;
   }
}
