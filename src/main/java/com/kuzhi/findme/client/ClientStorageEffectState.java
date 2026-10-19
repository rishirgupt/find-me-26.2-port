package com.kuzhi.findme.client;

import com.kuzhi.findme.network.StorageEffectPacket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public final class ClientStorageEffectState {
   private static final List<ClientStorageEffectState.Effect> EFFECTS = new ArrayList<>();

   private ClientStorageEffectState() {
   }

   public static void start(StorageEffectPacket packet) {
      EFFECTS.removeIf(effect -> effect.entityId == packet.entityId());
      EFFECTS.add(
         new ClientStorageEffectState.Effect(
            packet.entityId(),
            new Vec3(packet.x(), packet.y(), packet.z()),
            Math.max(0.8F, packet.radius()),
            Math.max(1.0F, packet.height()),
            Math.max(1, packet.durationTicks())
         )
      );
      if (EFFECTS.size() > 12) {
         EFFECTS.remove(0);
      }
   }

   static void tick() {
      Iterator<ClientStorageEffectState.Effect> iterator = EFFECTS.iterator();

      while (iterator.hasNext()) {
         ClientStorageEffectState.Effect effect = iterator.next();
         effect.age++;
         if (effect.age >= effect.durationTicks) {
            iterator.remove();
         }
      }
   }

   static List<ClientStorageEffectState.Effect> effects() {
      return EFFECTS;
   }

   static final class Effect {
      final int entityId;
      final Vec3 fallbackBase;
      final float radius;
      final float height;
      final int durationTicks;
      int age;

      Effect(int entityId, Vec3 fallbackBase, float radius, float height, int durationTicks) {
         this.entityId = entityId;
         this.fallbackBase = fallbackBase;
         this.radius = radius;
         this.height = height;
         this.durationTicks = durationTicks;
      }
   }
}
