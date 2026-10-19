package com.kuzhi.findme.server;

import com.kuzhi.findme.common.CompanionKind;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

final class PendingRetreat {
   private final UUID entityUuid;
   private final Vec3 origin;
   private final Vec3 direction;
   private final UUID playerUuid;
   private final CompanionKind kind;
   private int age;

   PendingRetreat(UUID entityUuid, Vec3 origin, Vec3 direction, UUID playerUuid, CompanionKind kind) {
      this.entityUuid = entityUuid;
      this.origin = origin;
      this.direction = direction;
      this.playerUuid = playerUuid;
      this.kind = kind;
   }

   UUID entityUuid() {
      return this.entityUuid;
   }

   Vec3 origin() {
      return this.origin;
   }

   Vec3 direction() {
      return this.direction;
   }

   UUID playerUuid() {
      return this.playerUuid;
   }

   CompanionKind kind() {
      return this.kind;
   }

   int age() {
      return this.age;
   }

   void incrementAge() {
      this.age++;
   }
}
