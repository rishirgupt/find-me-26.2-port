package com.kuzhi.findme.client;

import com.kuzhi.findme.network.RescueMagicPacket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public final class ClientRescueMagicRenderState {
   private static final List<ClientRescueMagicRenderState.Effect> EFFECTS = new ArrayList<>();

   private ClientRescueMagicRenderState() {
   }

   public static void start(RescueMagicPacket packet) {
      EFFECTS.add(
         new ClientRescueMagicRenderState.Effect(
            new Vec3(packet.x(), packet.y(), packet.z()),
            packet.radius(),
            packet.height(),
            packet.yaw(),
            Math.max(1, packet.durationTicks()),
            packet.style(),
            packet.purpose()
         )
      );
      if (EFFECTS.size() > 8) {
         EFFECTS.remove(0);
      }
   }

   static void tick() {
      Iterator<ClientRescueMagicRenderState.Effect> iterator = EFFECTS.iterator();

      while (iterator.hasNext()) {
         ClientRescueMagicRenderState.Effect effect = iterator.next();
         effect.age++;
         if (effect.age >= effect.durationTicks) {
            iterator.remove();
         }
      }
   }

   static List<ClientRescueMagicRenderState.Effect> effects() {
      return EFFECTS;
   }

   static final class Effect {
      final Vec3 center;
      final float radius;
      final float height;
      final float yaw;
      final int durationTicks;
      final RescueMagicPacket.Style style;
      final RescueMagicPacket.Purpose purpose;
      int age;

      Effect(Vec3 center, float radius, float height, float yaw, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose) {
         this.center = center;
         this.radius = radius;
         this.height = height;
         this.yaw = yaw;
         this.durationTicks = durationTicks;
         this.style = style;
         this.purpose = purpose;
      }
   }
}
