package com.kuzhi.findme.network;

import com.kuzhi.findme.compat.NetworkEvent;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class ModNetwork {
   public static final Object channel = new Object();

   private ModNetwork() {
   }

   /** Registers every payload type and the handlers for packets the server receives. */
   public static void register() {
      PayloadTypeRegistry.serverboundPlay().register(CompanionCommandPacket.TYPE, CompanionCommandPacket.STREAM_CODEC);
      PayloadTypeRegistry.serverboundPlay().register(CompanionRenamePacket.TYPE, CompanionRenamePacket.STREAM_CODEC);
      PayloadTypeRegistry.serverboundPlay().register(CompanionHouseHomePacket.TYPE, CompanionHouseHomePacket.STREAM_CODEC);
      PayloadTypeRegistry.serverboundPlay().register(VehicleCommandPacket.TYPE, VehicleCommandPacket.STREAM_CODEC);
      PayloadTypeRegistry.serverboundPlay().register(VehicleRenamePacket.TYPE, VehicleRenamePacket.STREAM_CODEC);
      PayloadTypeRegistry.clientboundPlay().register(CompanionListPacket.TYPE, CompanionListPacket.STREAM_CODEC);
      PayloadTypeRegistry.clientboundPlay().register(IgnoreListPacket.TYPE, IgnoreListPacket.STREAM_CODEC);
      PayloadTypeRegistry.clientboundPlay().register(DeadCompanionListPacket.TYPE, DeadCompanionListPacket.STREAM_CODEC);
      PayloadTypeRegistry.clientboundPlay().register(ContractCameraPacket.TYPE, ContractCameraPacket.STREAM_CODEC);
      PayloadTypeRegistry.clientboundPlay().register(RescueMagicPacket.TYPE, RescueMagicPacket.STREAM_CODEC);
      PayloadTypeRegistry.clientboundPlay().register(BlacklistPacket.TYPE, BlacklistPacket.STREAM_CODEC);
      PayloadTypeRegistry.clientboundPlay().register(StorageEffectPacket.TYPE, StorageEffectPacket.STREAM_CODEC);
      PayloadTypeRegistry.clientboundPlay().register(CompanionDialoguePacket.TYPE, CompanionDialoguePacket.STREAM_CODEC);
      PayloadTypeRegistry.clientboundPlay().register(OpenHouseScreenPacket.TYPE, OpenHouseScreenPacket.STREAM_CODEC);
      PayloadTypeRegistry.clientboundPlay().register(VehicleListPacket.TYPE, VehicleListPacket.STREAM_CODEC);
      PayloadTypeRegistry.clientboundPlay().register(VehicleSealEffectPacket.TYPE, VehicleSealEffectPacket.STREAM_CODEC);
      PayloadTypeRegistry.clientboundPlay().register(SableVehiclePreviewCapturePacket.TYPE, SableVehiclePreviewCapturePacket.STREAM_CODEC);
      PayloadTypeRegistry.clientboundPlay().register(SableVehiclePreviewDeletePacket.TYPE, SableVehiclePreviewDeletePacket.STREAM_CODEC);
      PayloadTypeRegistry.clientboundPlay().register(SableVehiclePreviewTransferPacket.TYPE, SableVehiclePreviewTransferPacket.STREAM_CODEC);
      ServerPlayNetworking.registerGlobalReceiver(CompanionCommandPacket.TYPE, (packet, context) -> {
         NetworkEvent.Context legacy = NetworkEvent.Context.ofServer(context.player(), context.server());
         CompanionCommandPacket.handle(packet, () -> legacy);
      });
      ServerPlayNetworking.registerGlobalReceiver(CompanionRenamePacket.TYPE, (packet, context) -> {
         NetworkEvent.Context legacy = NetworkEvent.Context.ofServer(context.player(), context.server());
         CompanionRenamePacket.handle(packet, () -> legacy);
      });
      ServerPlayNetworking.registerGlobalReceiver(CompanionHouseHomePacket.TYPE, (packet, context) -> {
         NetworkEvent.Context legacy = NetworkEvent.Context.ofServer(context.player(), context.server());
         CompanionHouseHomePacket.handle(packet, () -> legacy);
      });
      ServerPlayNetworking.registerGlobalReceiver(VehicleCommandPacket.TYPE, (packet, context) -> {
         NetworkEvent.Context legacy = NetworkEvent.Context.ofServer(context.player(), context.server());
         VehicleCommandPacket.handle(packet, () -> legacy);
      });
      ServerPlayNetworking.registerGlobalReceiver(VehicleRenamePacket.TYPE, (packet, context) -> {
         NetworkEvent.Context legacy = NetworkEvent.Context.ofServer(context.player(), context.server());
         VehicleRenamePacket.handle(packet, () -> legacy);
      });
   }

   public static void sendToServer(CustomPacketPayload packet) {
      ClientSender.send(packet);
   }

   public static void sendToPlayer(ServerPlayer player, CustomPacketPayload packet) {
      if (player != null && ServerPlayNetworking.canSend(player, packet.type())) {
         ServerPlayNetworking.send(player, packet);
      }
   }

   public static void sendToPlayersNear(ServerLevel level, Vec3 center, double radius, CustomPacketPayload packet) {
      if (level != null && center != null) {
         double radiusSqr = radius * radius;
         for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) <= radiusSqr) {
               sendToPlayer(player, packet);
            }
         }
      }
   }

   /** Kept apart so the dedicated server never loads client networking classes. */
   private static final class ClientSender {
      static void send(CustomPacketPayload packet) {
         net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(packet);
      }
   }
}
