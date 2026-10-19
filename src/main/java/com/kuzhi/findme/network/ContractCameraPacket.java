package com.kuzhi.findme.network;

import com.kuzhi.findme.client.ClientContractCamera;
import com.kuzhi.findme.client.ClientContractRenderState;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import com.kuzhi.findme.compat.NetworkEvent;

public record ContractCameraPacket(
   boolean active,
   int targetEntityId,
   int durationTicks,
   float yaw,
   float pitch,
   double playerX,
   double playerY,
   double playerZ,
   double targetX,
   double targetY,
   double targetZ,
   float playerRadius,
   float targetRadius,
   String targetName,
   ContractCameraPacket.Mode mode,
   boolean cameraEnabled
) implements CustomPacketPayload {
   public static final Type<ContractCameraPacket> TYPE = new Type(Identifier.fromNamespaceAndPath("find_me", "contract_camera"));
   public static final StreamCodec<RegistryFriendlyByteBuf, ContractCameraPacket> STREAM_CODEC = NetworkCodecs.of(
      ContractCameraPacket::encode, ContractCameraPacket::decode
   );

   public ContractCameraPacket(boolean active, int targetEntityId, int durationTicks) {
      this(
         active,
         targetEntityId,
         durationTicks,
         0.0F,
         0.0F,
         0.0,
         0.0,
         0.0,
         0.0,
         0.0,
         0.0,
         1.4F,
         2.2F,
         "",
         active ? ContractCameraPacket.Mode.START : ContractCameraPacket.Mode.STOP,
         true
      );
   }

   public ContractCameraPacket(
      boolean active,
      int targetEntityId,
      int durationTicks,
      float yaw,
      float pitch,
      double playerX,
      double playerY,
      double playerZ,
      double targetX,
      double targetY,
      double targetZ,
      float playerRadius,
      float targetRadius,
      String targetName
   ) {
      this(
         active,
         targetEntityId,
         durationTicks,
         yaw,
         pitch,
         playerX,
         playerY,
         playerZ,
         targetX,
         targetY,
         targetZ,
         playerRadius,
         targetRadius,
         targetName,
         active ? ContractCameraPacket.Mode.START : ContractCameraPacket.Mode.STOP,
         true
      );
   }

   public ContractCameraPacket(
      boolean active,
      int targetEntityId,
      int durationTicks,
      float yaw,
      float pitch,
      double playerX,
      double playerY,
      double playerZ,
      double targetX,
      double targetY,
      double targetZ,
      float playerRadius,
      float targetRadius,
      String targetName,
      ContractCameraPacket.Mode mode
   ) {
      this(
         active,
         targetEntityId,
         durationTicks,
         yaw,
         pitch,
         playerX,
         playerY,
         playerZ,
         targetX,
         targetY,
         targetZ,
         playerRadius,
         targetRadius,
         targetName,
         mode,
         true
      );
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void encode(ContractCameraPacket packet, FriendlyByteBuf buffer) {
      buffer.writeBoolean(packet.active);
      buffer.writeInt(packet.targetEntityId);
      buffer.writeVarInt(packet.durationTicks);
      buffer.writeFloat(packet.yaw);
      buffer.writeFloat(packet.pitch);
      buffer.writeDouble(packet.playerX);
      buffer.writeDouble(packet.playerY);
      buffer.writeDouble(packet.playerZ);
      buffer.writeDouble(packet.targetX);
      buffer.writeDouble(packet.targetY);
      buffer.writeDouble(packet.targetZ);
      buffer.writeFloat(packet.playerRadius);
      buffer.writeFloat(packet.targetRadius);
      buffer.writeUtf(packet.targetName, 128);
      buffer.writeEnum(packet.mode);
      buffer.writeBoolean(packet.cameraEnabled);
   }

   public static ContractCameraPacket decode(FriendlyByteBuf buffer) {
      return new ContractCameraPacket(
         buffer.readBoolean(),
         buffer.readInt(),
         buffer.readVarInt(),
         buffer.readFloat(),
         buffer.readFloat(),
         buffer.readDouble(),
         buffer.readDouble(),
         buffer.readDouble(),
         buffer.readDouble(),
         buffer.readDouble(),
         buffer.readDouble(),
         buffer.readFloat(),
         buffer.readFloat(),
         buffer.readUtf(128),
         (ContractCameraPacket.Mode)buffer.readEnum(ContractCameraPacket.Mode.class),
         buffer.readBoolean()
      );
   }

   public static void handle(ContractCameraPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> {
         if (packet.mode != ContractCameraPacket.Mode.START && !packet.active) {
            if (packet.mode == ContractCameraPacket.Mode.COMPLETE) {
               ClientContractCamera.finish(packet.durationTicks);
               ClientContractRenderState.finish(packet, ClientContractRenderState.Outcome.COMPLETE);
            } else if (packet.mode == ContractCameraPacket.Mode.CANCEL) {
               ClientContractCamera.finish(packet.durationTicks);
               ClientContractRenderState.finish(packet, ClientContractRenderState.Outcome.CANCEL);
            } else {
               ClientContractCamera.stop();
               ClientContractRenderState.stop();
            }
         } else {
            if (packet.cameraEnabled) {
               ClientContractCamera.start(packet);
            } else {
               ClientContractCamera.stop();
            }

            ClientContractRenderState.start(packet);
         }
      });
      context.setPacketHandled(true);
   }

   public enum Mode {
      START,
      STOP,
      COMPLETE,
      CANCEL;
   }
}
