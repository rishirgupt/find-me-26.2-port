package com.kuzhi.findme.client;

import com.kuzhi.findme.network.ContractCameraPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class ClientContractRenderState {
   private static int targetEntityId = -1;
   private static int durationTicks;
   private static int ticksRemaining;
   private static int endingDurationTicks;
   private static int endingTicksRemaining;
   private static Vec3 playerCircle = Vec3.ZERO;
   private static Vec3 targetCircle = Vec3.ZERO;
   private static float playerRadius = 1.5F;
   private static float targetRadius = 2.2F;
   private static String targetName = "";
   private static ClientContractRenderState.Outcome outcome = ClientContractRenderState.Outcome.NONE;

   private ClientContractRenderState() {
   }

   public static void start(ContractCameraPacket packet) {
      targetEntityId = packet.targetEntityId();
      durationTicks = Math.max(packet.durationTicks(), 20);
      ticksRemaining = durationTicks;
      playerCircle = new Vec3(packet.playerX(), packet.playerY(), packet.playerZ());
      targetCircle = new Vec3(packet.targetX(), packet.targetY(), packet.targetZ());
      playerRadius = packet.playerRadius();
      targetRadius = packet.targetRadius();
      targetName = packet.targetName();
      endingDurationTicks = 0;
      endingTicksRemaining = 0;
      outcome = ClientContractRenderState.Outcome.NONE;
   }

   public static void finish(ContractCameraPacket packet, ClientContractRenderState.Outcome finishOutcome) {
      targetEntityId = packet.targetEntityId();
      if (durationTicks <= 0) {
         durationTicks = 120;
      }

      ticksRemaining = 0;
      endingDurationTicks = Math.max(packet.durationTicks(), 12);
      endingTicksRemaining = endingDurationTicks;
      if (!packet.targetName().isBlank()) {
         targetName = packet.targetName();
      }

      playerCircle = new Vec3(packet.playerX(), packet.playerY(), packet.playerZ());
      targetCircle = new Vec3(packet.targetX(), packet.targetY(), packet.targetZ());
      playerRadius = packet.playerRadius();
      targetRadius = packet.targetRadius();
      outcome = finishOutcome == null ? ClientContractRenderState.Outcome.NONE : finishOutcome;
   }

   public static void stop() {
      targetEntityId = -1;
      durationTicks = 0;
      ticksRemaining = 0;
      endingDurationTicks = 0;
      endingTicksRemaining = 0;
      targetName = "";
      outcome = ClientContractRenderState.Outcome.NONE;
   }

   public static void tick() {
      if (endingTicksRemaining > 0) {
         endingTicksRemaining--;
      } else if (ticksRemaining > 0) {
         ticksRemaining--;
      }
   }

   public static boolean active() {
      return ticksRemaining > 0 || endingTicksRemaining > 0;
   }

   public static int targetEntityId() {
      return targetEntityId;
   }

   public static float progress(float partialTick) {
      if (outcome != ClientContractRenderState.Outcome.NONE) {
         return 1.0F;
      } else {
         return durationTicks <= 0 ? 1.0F : Math.min(1.0F, Math.max(0.0F, (durationTicks - ticksRemaining + partialTick) / durationTicks));
      }
   }

   public static int age(float partialTick) {
      return outcome != ClientContractRenderState.Outcome.NONE
         ? durationTicks + Math.max(0, Math.round(endingDurationTicks - endingTicksRemaining + partialTick))
         : Math.max(0, Math.round(durationTicks - ticksRemaining + partialTick));
   }

   public static float endingProgress(float partialTick) {
      return outcome != ClientContractRenderState.Outcome.NONE && endingDurationTicks > 0
         ? Math.min(1.0F, Math.max(0.0F, (endingDurationTicks - endingTicksRemaining + partialTick) / endingDurationTicks))
         : 0.0F;
   }

   public static Vec3 playerCircle() {
      return playerCircle;
   }

   public static Vec3 targetCircle() {
      return targetCircle;
   }

   public static float playerRadius() {
      return playerRadius;
   }

   public static float targetRadius() {
      return targetRadius;
   }

   public static String targetName() {
      return targetName;
   }

   public static ClientContractRenderState.Outcome outcome() {
      return outcome;
   }

   public static Component subtitle() {
      int age = age(0.0F);
      if (outcome == ClientContractRenderState.Outcome.COMPLETE) {
         return Component.translatable("message.find_me.contract_sealed", new Object[]{targetName});
      } else if (outcome == ClientContractRenderState.Outcome.CANCEL) {
         return Component.translatable("message.find_me.contract_broken");
      } else if (age >= 92) {
         return Component.translatable("message.find_me.contract_vow", new Object[]{targetName});
      } else if (age >= 54) {
         return Component.translatable("message.find_me.contract_answer", new Object[]{targetName});
      } else {
         return age >= 18 ? Component.translatable("message.find_me.contract_name", new Object[]{targetName}) : Component.empty();
      }
   }

   public static ClientContractRenderState.Phase phase() {
      int age = age(0.0F);
      if (age >= 92) {
         return ClientContractRenderState.Phase.VOW;
      } else {
         return age >= 54 ? ClientContractRenderState.Phase.RESPONSE : ClientContractRenderState.Phase.NAME;
      }
   }

   public enum Outcome {
      NONE,
      COMPLETE,
      CANCEL;
   }

   public enum Phase {
      NAME,
      RESPONSE,
      VOW;
   }
}
