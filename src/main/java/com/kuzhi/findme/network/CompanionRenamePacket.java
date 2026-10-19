package com.kuzhi.findme.network;

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

public record CompanionRenamePacket(CompanionKind kind, int index, String name) implements CustomPacketPayload {
   public static final Type<CompanionRenamePacket> TYPE = new Type(Identifier.fromNamespaceAndPath("find_me", "companion_rename"));
   public static final StreamCodec<RegistryFriendlyByteBuf, CompanionRenamePacket> STREAM_CODEC = NetworkCodecs.of(
      CompanionRenamePacket::encode, CompanionRenamePacket::decode
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void encode(CompanionRenamePacket packet, FriendlyByteBuf buffer) {
      buffer.writeEnum(packet.kind);
      buffer.writeInt(packet.index);
      buffer.writeUtf(packet.name, 64);
   }

   public static CompanionRenamePacket decode(FriendlyByteBuf buffer) {
      return new CompanionRenamePacket((CompanionKind)buffer.readEnum(CompanionKind.class), buffer.readInt(), buffer.readUtf(64));
   }

   public static void handle(CompanionRenamePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> {
         if (context.getSender() != null) {
            CompanionManager.rename(context.getSender(), packet.kind, packet.index, packet.name);
         }
      });
      context.setPacketHandled(true);
   }
}
