package com.kuzhi.findme.client;

import com.kuzhi.findme.compat.NetworkEvent;
import com.kuzhi.findme.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Handlers for the packets the client receives. */
public final class ClientNetwork {
   private ClientNetwork() {
   }

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(CompanionListPacket.TYPE, (packet, context) -> {
         NetworkEvent.Context legacy = NetworkEvent.Context.ofClient(context.client());
         CompanionListPacket.handle(packet, () -> legacy);
      });
      ClientPlayNetworking.registerGlobalReceiver(IgnoreListPacket.TYPE, (packet, context) -> {
         NetworkEvent.Context legacy = NetworkEvent.Context.ofClient(context.client());
         IgnoreListPacket.handle(packet, () -> legacy);
      });
      ClientPlayNetworking.registerGlobalReceiver(DeadCompanionListPacket.TYPE, (packet, context) -> {
         NetworkEvent.Context legacy = NetworkEvent.Context.ofClient(context.client());
         DeadCompanionListPacket.handle(packet, () -> legacy);
      });
      ClientPlayNetworking.registerGlobalReceiver(ContractCameraPacket.TYPE, (packet, context) -> {
         NetworkEvent.Context legacy = NetworkEvent.Context.ofClient(context.client());
         ContractCameraPacket.handle(packet, () -> legacy);
      });
      ClientPlayNetworking.registerGlobalReceiver(RescueMagicPacket.TYPE, (packet, context) -> {
         NetworkEvent.Context legacy = NetworkEvent.Context.ofClient(context.client());
         RescueMagicPacket.handle(packet, () -> legacy);
      });
      ClientPlayNetworking.registerGlobalReceiver(BlacklistPacket.TYPE, (packet, context) -> {
         NetworkEvent.Context legacy = NetworkEvent.Context.ofClient(context.client());
         BlacklistPacket.handle(packet, () -> legacy);
      });
      ClientPlayNetworking.registerGlobalReceiver(StorageEffectPacket.TYPE, (packet, context) -> {
         NetworkEvent.Context legacy = NetworkEvent.Context.ofClient(context.client());
         StorageEffectPacket.handle(packet, () -> legacy);
      });
      ClientPlayNetworking.registerGlobalReceiver(CompanionDialoguePacket.TYPE, (packet, context) -> {
         NetworkEvent.Context legacy = NetworkEvent.Context.ofClient(context.client());
         CompanionDialoguePacket.handle(packet, () -> legacy);
      });
      ClientPlayNetworking.registerGlobalReceiver(OpenHouseScreenPacket.TYPE, (packet, context) -> {
         NetworkEvent.Context legacy = NetworkEvent.Context.ofClient(context.client());
         OpenHouseScreenPacket.handle(packet, () -> legacy);
      });
      ClientPlayNetworking.registerGlobalReceiver(VehicleListPacket.TYPE, (packet, context) -> {
         NetworkEvent.Context legacy = NetworkEvent.Context.ofClient(context.client());
         VehicleListPacket.handle(packet, () -> legacy);
      });
      ClientPlayNetworking.registerGlobalReceiver(VehicleSealEffectPacket.TYPE, (packet, context) -> {
         NetworkEvent.Context legacy = NetworkEvent.Context.ofClient(context.client());
         VehicleSealEffectPacket.handle(packet, () -> legacy);
      });
      ClientPlayNetworking.registerGlobalReceiver(SableVehiclePreviewCapturePacket.TYPE, (packet, context) -> {
         NetworkEvent.Context legacy = NetworkEvent.Context.ofClient(context.client());
         SableVehiclePreviewCapturePacket.handle(packet, () -> legacy);
      });
      ClientPlayNetworking.registerGlobalReceiver(SableVehiclePreviewDeletePacket.TYPE, (packet, context) -> {
         NetworkEvent.Context legacy = NetworkEvent.Context.ofClient(context.client());
         SableVehiclePreviewDeletePacket.handle(packet, () -> legacy);
      });
      ClientPlayNetworking.registerGlobalReceiver(SableVehiclePreviewTransferPacket.TYPE, (packet, context) -> {
         NetworkEvent.Context legacy = NetworkEvent.Context.ofClient(context.client());
         SableVehiclePreviewTransferPacket.handle(packet, () -> legacy);
      });
   }
}
