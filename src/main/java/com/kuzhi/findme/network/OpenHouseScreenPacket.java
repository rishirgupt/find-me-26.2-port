package com.kuzhi.findme.network;

import com.kuzhi.findme.client.ClientHouseScreenOpener;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import com.kuzhi.findme.compat.NetworkEvent;

public record OpenHouseScreenPacket(BlockPos housePos, Identifier dimensionId) implements CustomPacketPayload {
   public static final Type<OpenHouseScreenPacket> TYPE = new Type(Identifier.fromNamespaceAndPath("find_me", "open_house_screen"));
   public static final StreamCodec<RegistryFriendlyByteBuf, OpenHouseScreenPacket> STREAM_CODEC = NetworkCodecs.of(
      OpenHouseScreenPacket::encode, OpenHouseScreenPacket::decode
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void encode(OpenHouseScreenPacket packet, FriendlyByteBuf buffer) {
      buffer.writeBlockPos(packet.housePos);
      buffer.writeIdentifier(packet.dimensionId);
   }

   public static OpenHouseScreenPacket decode(FriendlyByteBuf buffer) {
      return new OpenHouseScreenPacket(buffer.readBlockPos(), buffer.readIdentifier());
   }

   public static void handle(OpenHouseScreenPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> ClientHouseScreenOpener.open(packet.housePos, packet.dimensionId));
      context.setPacketHandled(true);
   }
}
