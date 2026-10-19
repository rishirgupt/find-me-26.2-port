package com.kuzhi.findme.network;

import com.kuzhi.findme.client.ClientVehicleState;
import com.kuzhi.findme.common.CompanionMoveType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import com.kuzhi.findme.compat.NetworkEvent;

public record VehicleListPacket(int activeIndex, List<VehicleListPacket.Entry> wheelEntries, List<VehicleListPacket.Entry> allEntries)
   implements CustomPacketPayload {
   public static final Type<VehicleListPacket> TYPE = new Type(Identifier.fromNamespaceAndPath("find_me", "vehicle_list"));
   public static final StreamCodec<RegistryFriendlyByteBuf, VehicleListPacket> STREAM_CODEC = NetworkCodecs.of(
      VehicleListPacket::encode, VehicleListPacket::decode
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void encode(VehicleListPacket packet, FriendlyByteBuf buffer) {
      buffer.writeInt(packet.activeIndex);
      writeEntries(buffer, packet.wheelEntries);
      writeEntries(buffer, packet.allEntries);
   }

   public static VehicleListPacket decode(FriendlyByteBuf buffer) {
      int activeIndex = buffer.readInt();
      return new VehicleListPacket(activeIndex, readEntries(buffer), readEntries(buffer));
   }

   public static void handle(VehicleListPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> ClientVehicleState.update(packet.activeIndex, packet.wheelEntries, packet.allEntries));
      context.setPacketHandled(true);
   }

   private static void writeEntries(FriendlyByteBuf buffer, List<VehicleListPacket.Entry> entries) {
      buffer.writeVarInt(entries.size());

      for (VehicleListPacket.Entry entry : entries) {
         buffer.writeUUID(entry.uuid);
         buffer.writeInt(entry.entityId);
         buffer.writeUtf(entry.entityType, 128);
         buffer.writeUtf(entry.name, 128);
         buffer.writeBoolean(entry.loaded);
         buffer.writeBoolean(entry.alive);
         buffer.writeBoolean(entry.deployed);
         buffer.writeBoolean(entry.ridden);
         buffer.writeNbt(entry.previewTag);
      }
   }

   private static List<VehicleListPacket.Entry> readEntries(FriendlyByteBuf buffer) {
      int size = buffer.readVarInt();
      ArrayList<VehicleListPacket.Entry> entries = new ArrayList<>(size);

      for (int i = 0; i < size; i++) {
         entries.add(
            new VehicleListPacket.Entry(
               buffer.readUUID(),
               buffer.readInt(),
               buffer.readUtf(128),
               buffer.readUtf(128),
               buffer.readBoolean(),
               buffer.readBoolean(),
               buffer.readBoolean(),
               buffer.readBoolean(),
               buffer.readNbt()
            )
         );
      }

      return entries;
   }

   public record Entry(
      UUID uuid, int entityId, String entityType, String name, boolean loaded, boolean alive, boolean deployed, boolean ridden, CompoundTag previewTag
   ) {
      public CompanionListPacket.Entry asPreviewEntry() {
         boolean sable = "simulated:sable_sublevel".equals(this.entityType);
         return new CompanionListPacket.Entry(
            this.uuid,
            this.entityId,
            sable ? "" : this.entityType,
            this.name,
            this.loaded,
            this.alive,
            this.deployed,
            this.ridden,
            false,
            false,
            0.0F,
            0.0F,
            0.0F,
            CompanionMoveType.WALK,
            sable ? null : this.previewTag
         );
      }
   }
}
