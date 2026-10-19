package com.kuzhi.findme.client;

import com.kuzhi.findme.network.VehicleSealEffectPacket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public final class ClientVehicleSealEffectState {
   private static final List<ClientVehicleSealEffectState.Effect> EFFECTS = new ArrayList<>();

   private ClientVehicleSealEffectState() {
   }

   public static void start(VehicleSealEffectPacket packet) {
      EFFECTS.add(
         new ClientVehicleSealEffectState.Effect(
            new Vec3(packet.x(), packet.y(), packet.z()), Math.max(0.9F, packet.radius()), Math.max(1.0F, packet.height()), Math.max(1, packet.durationTicks())
         )
      );
      if (EFFECTS.size() > 10) {
         EFFECTS.remove(0);
      }
   }

   static void tick() {
      Iterator<ClientVehicleSealEffectState.Effect> iterator = EFFECTS.iterator();

      while (iterator.hasNext()) {
         ClientVehicleSealEffectState.Effect effect = iterator.next();
         effect.age++;
         if (effect.age >= effect.durationTicks) {
            iterator.remove();
         }
      }
   }

   static List<ClientVehicleSealEffectState.Effect> effects() {
      return EFFECTS;
   }

   static final class Effect {
      final Vec3 base;
      final float radius;
      final float height;
      final int durationTicks;
      int age;

      Effect(Vec3 base, float radius, float height, int durationTicks) {
         this.base = base;
         this.radius = radius;
         this.height = height;
         this.durationTicks = durationTicks;
      }
   }
}
