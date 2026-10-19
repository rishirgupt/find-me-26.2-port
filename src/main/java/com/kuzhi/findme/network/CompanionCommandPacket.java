package com.kuzhi.findme.network;

import com.kuzhi.findme.common.CompanionAction;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.server.CompanionManager;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import com.kuzhi.findme.compat.NetworkEvent;

public record CompanionCommandPacket(CompanionKind kind, CompanionAction action, int targetEntityId) implements CustomPacketPayload {
   public static final Type<CompanionCommandPacket> TYPE = new Type(Identifier.fromNamespaceAndPath("find_me", "companion_command"));
   public static final StreamCodec<RegistryFriendlyByteBuf, CompanionCommandPacket> STREAM_CODEC = NetworkCodecs.of(
      CompanionCommandPacket::encode, CompanionCommandPacket::decode
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void encode(CompanionCommandPacket packet, FriendlyByteBuf buffer) {
      buffer.writeEnum(packet.kind);
      buffer.writeEnum(packet.action);
      buffer.writeInt(packet.targetEntityId);
   }

   public static CompanionCommandPacket decode(FriendlyByteBuf buffer) {
      return new CompanionCommandPacket(
         (CompanionKind)buffer.readEnum(CompanionKind.class), (CompanionAction)buffer.readEnum(CompanionAction.class), buffer.readInt()
      );
   }

   public static void handle(CompanionCommandPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> {
         if (context.getSender() != null) {
            CompanionManager.handle(context.getSender(), packet.kind, packet.action, packet.targetEntityId);
         }
      });
      context.setPacketHandled(true);
   }
}
