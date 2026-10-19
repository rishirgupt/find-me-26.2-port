package com.kuzhi.findme.network;

import com.kuzhi.findme.common.CompanionAction;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.server.CompanionManager;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import com.kuzhi.findme.compat.NetworkEvent;

public record CompanionHouseHomePacket(CompanionKind kind, CompanionAction action, int index, BlockPos housePos, Identifier dimensionId)
   implements CustomPacketPayload {
   public static final Type<CompanionHouseHomePacket> TYPE = new Type(Identifier.fromNamespaceAndPath("find_me", "companion_house_home"));
   public static final StreamCodec<RegistryFriendlyByteBuf, CompanionHouseHomePacket> STREAM_CODEC = NetworkCodecs.of(
      CompanionHouseHomePacket::encode, CompanionHouseHomePacket::decode
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void encode(CompanionHouseHomePacket packet, FriendlyByteBuf buffer) {
      buffer.writeEnum(packet.kind);
      buffer.writeEnum(packet.action);
      buffer.writeInt(packet.index);
      buffer.writeBlockPos(packet.housePos);
      buffer.writeIdentifier(packet.dimensionId);
   }

   public static CompanionHouseHomePacket decode(FriendlyByteBuf buffer) {
      return new CompanionHouseHomePacket(
         (CompanionKind)buffer.readEnum(CompanionKind.class),
         (CompanionAction)buffer.readEnum(CompanionAction.class),
         buffer.readInt(),
         buffer.readBlockPos(),
         buffer.readIdentifier()
      );
   }

   public static void handle(CompanionHouseHomePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> {
         if (context.getSender() != null) {
            CompanionManager.handleHouseHome(context.getSender(), packet.kind, packet.action, packet.index, packet.housePos, packet.dimensionId);
         }
      });
      context.setPacketHandled(true);
   }
}
