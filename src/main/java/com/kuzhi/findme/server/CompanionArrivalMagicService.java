package com.kuzhi.findme.server;

import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.RescueMagicPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

final class CompanionArrivalMagicService {
   private static final double MAGIC_PACKET_RADIUS = 96.0;

   private CompanionArrivalMagicService() {
   }

   static void send(ServerPlayer player, LivingEntity living, Vec3 focus, int durationTicks) {
      send(player, living, focus, durationTicks, RescueMagicPacket.Style.VERTICAL_PORTAL, RescueMagicPacket.Purpose.SUMMON);
   }

   static void sendGround(ServerPlayer player, LivingEntity living, Vec3 focus, int durationTicks) {
      send(player, living, focus, durationTicks, RescueMagicPacket.Style.GROUND_CIRCLE, RescueMagicPacket.Purpose.SUMMON);
   }

   static void sendGround(ServerPlayer player, LivingEntity living, Vec3 focus, int durationTicks, RescueMagicPacket.Purpose purpose) {
      send(player, living, focus, durationTicks, RescueMagicPacket.Style.GROUND_CIRCLE, purpose);
   }

   static void beginAt(ServerPlayer player, LivingEntity living, BlockPos anchor, Vec3 focus, int durationTicks, RescueMagicPacket.Style style) {
      beginAt(player, living, Vec3.atBottomCenterOf(anchor), focus, durationTicks, style, RescueMagicPacket.Purpose.SUMMON);
   }

   static void beginAt(
      ServerPlayer player,
      LivingEntity living,
      BlockPos anchor,
      Vec3 focus,
      int durationTicks,
      RescueMagicPacket.Style style,
      RescueMagicPacket.Purpose purpose
   ) {
      beginAt(player, living, Vec3.atBottomCenterOf(anchor), focus, durationTicks, style, purpose);
   }

   static void beginAt(ServerPlayer player, LivingEntity living, Vec3 anchor, Vec3 focus, int durationTicks, RescueMagicPacket.Style style) {
      beginAt(player, living, anchor, focus, durationTicks, style, RescueMagicPacket.Purpose.SUMMON);
   }

   static void beginAt(
      ServerPlayer player, LivingEntity living, Vec3 anchor, Vec3 focus, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose
   ) {
      sendAt(player, effectLevel(player, living), living, anchor, focus, durationTicks, style, purpose);
      CompanionArrivalSequenceService.startWithDelay(
         player, living, focus == null ? player.position() : focus, purpose, revealDelayTicks(durationTicks, style, purpose)
      );
   }

   static void openAt(
      ServerPlayer player,
      LivingEntity living,
      BlockPos anchor,
      Vec3 focus,
      int durationTicks,
      RescueMagicPacket.Style style,
      RescueMagicPacket.Purpose purpose
   ) {
      openAt(player, living, Vec3.atBottomCenterOf(anchor), focus, durationTicks, style, purpose);
   }

   static void openAt(
      ServerPlayer player, LivingEntity living, Vec3 anchor, Vec3 focus, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose
   ) {
      sendAt(player, effectLevel(player, living), living, anchor, focus, durationTicks, style, purpose);
   }

   static void openAt(
      ServerLevel level,
      ServerPlayer player,
      LivingEntity living,
      Vec3 anchor,
      Vec3 focus,
      int durationTicks,
      RescueMagicPacket.Style style,
      RescueMagicPacket.Purpose purpose
   ) {
      sendAt(player, level, living, anchor, focus, durationTicks, style, purpose);
   }

   static void openPulse(ServerPlayer player, Vec3 anchor, float radius, int durationTicks, RescueMagicPacket.Purpose purpose) {
      float yaw = yawToward(anchor, player.position());
      RescueMagicPacket packet = new RescueMagicPacket(
         anchor.x, anchor.y, anchor.z, radius, 1.6F, yaw, durationTicks, RescueMagicPacket.Style.GROUND_CIRCLE, purpose
      );
      ModNetwork.sendToPlayersNear(player.level(), anchor, 96.0, packet);
      CompanionMagicAudioService.playCircleOpen(player, anchor, radius, purpose == RescueMagicPacket.Purpose.RESCUE);
   }

   static void send(ServerPlayer player, LivingEntity living, Vec3 focus, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose) {
      Vec3 anchor = living.position();
      sendAt(player, effectLevel(player, living), living, anchor, focus, durationTicks, style, purpose);
      CompanionArrivalSequenceService.startWithDelay(
         player, living, focus == null ? player.position() : focus, purpose, revealDelayTicks(durationTicks, style, purpose)
      );
   }

   static int revealDelayTicks(int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose) {
      boolean rescue = purpose == RescueMagicPacket.Purpose.RESCUE;
      float progress;
      if (rescue) {
         progress = style == RescueMagicPacket.Style.VERTICAL_PORTAL ? 0.24F : 0.22F;
      } else {
         progress = style == RescueMagicPacket.Style.VERTICAL_PORTAL ? 0.38F : 0.34F;
      }

      return Mth.clamp(Math.round(durationTicks * progress), 1, 24);
   }

   private static void sendAt(
      ServerPlayer player,
      ServerLevel level,
      LivingEntity living,
      Vec3 anchor,
      Vec3 focus,
      int durationTicks,
      RescueMagicPacket.Style style,
      RescueMagicPacket.Purpose purpose
   ) {
      float radius = style == RescueMagicPacket.Style.GROUND_CIRCLE
         ? CompanionEntityVisualBoundsService.summonCircleRadius(living)
         : CompanionEntityVisualBoundsService.portalRadius(living);
      float height = CompanionEntityVisualBoundsService.portalHeight(living);
      float yaw = yawToward(anchor, focus == null ? player.position() : focus);
      RescueMagicPacket packet = new RescueMagicPacket(anchor.x, anchor.y, anchor.z, radius, height, yaw, durationTicks, style, purpose);
      ModNetwork.sendToPlayersNear(level == null ? player.level() : level, anchor, 96.0, packet);
      CompanionMagicAudioService.playCircleOpen(player, anchor, radius, purpose == RescueMagicPacket.Purpose.RESCUE);
   }

   private static ServerLevel effectLevel(ServerPlayer player, LivingEntity living) {
      return living != null && living.level() instanceof ServerLevel level ? level : player.level();
   }

   private static float yawToward(Vec3 from, Vec3 to) {
      double dx = to.x - from.x;
      double dz = to.z - from.z;
      return dx * dx + dz * dz < 1.0E-4 ? 0.0F : Mth.wrapDegrees((float)(Mth.atan2(dz, dx) * 180.0F / (float)Math.PI) - 90.0F);
   }
}
