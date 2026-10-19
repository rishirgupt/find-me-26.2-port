package com.kuzhi.findme.server;

import com.kuzhi.findme.common.CompanionMoveType;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

final class PendingMountCinematic {
   private final UUID playerUuid;
   private final UUID mountUuid;
   private final CompanionMoveType moveType;
   private final MountCinematicMode mode;
   private final double catchY;
   private final Vec3 flyingStageTarget;
   private final Vec3 introAnchor;
   private final boolean physicalCatchOnly;
   private final boolean originalNoGravity;
   private final boolean originalNoAi;
   private Vec3 waitPosition;
   private Vec3 lastCinematicPosition;
   private MountCinematicStage stage = MountCinematicStage.APPROACH;
   private int age;
   private int switchAge;
   private int warmupTicks;
   private boolean switched;
   private boolean flyingRescueStaged;

   PendingMountCinematic(
      UUID playerUuid,
      UUID mountUuid,
      CompanionMoveType moveType,
      MountCinematicMode mode,
      double catchY,
      Vec3 flyingStageTarget,
      boolean flyingRescueStaged,
      Vec3 introAnchor,
      boolean physicalCatchOnly,
      int warmupTicks,
      Vec3 lastCinematicPosition,
      boolean originalNoGravity,
      boolean originalNoAi
   ) {
      this.playerUuid = playerUuid;
      this.mountUuid = mountUuid;
      this.moveType = moveType;
      this.mode = mode;
      this.catchY = catchY;
      this.flyingStageTarget = flyingStageTarget;
      this.flyingRescueStaged = flyingRescueStaged;
      this.introAnchor = introAnchor;
      this.physicalCatchOnly = physicalCatchOnly;
      this.warmupTicks = warmupTicks;
      this.lastCinematicPosition = lastCinematicPosition;
      this.originalNoGravity = originalNoGravity;
      this.originalNoAi = originalNoAi;
   }

   UUID playerUuid() {
      return this.playerUuid;
   }

   UUID mountUuid() {
      return this.mountUuid;
   }

   CompanionMoveType moveType() {
      return this.moveType;
   }

   MountCinematicMode mode() {
      return this.mode;
   }

   double catchY() {
      return this.catchY;
   }

   Vec3 flyingStageTarget() {
      return this.flyingStageTarget;
   }

   Vec3 introAnchor() {
      return this.introAnchor;
   }

   boolean physicalCatchOnly() {
      return this.physicalCatchOnly;
   }

   boolean originalNoGravity() {
      return this.originalNoGravity;
   }

   boolean originalNoAi() {
      return this.originalNoAi;
   }

   boolean waitLocked() {
      return this.waitPosition != null;
   }

   Vec3 waitPosition() {
      return this.waitPosition;
   }

   void lockWait(Vec3 waitPosition) {
      this.waitPosition = waitPosition;
   }

   Vec3 lastCinematicPosition() {
      return this.lastCinematicPosition;
   }

   void rememberPosition(Vec3 position) {
      this.lastCinematicPosition = position;
   }

   int warmupTicks() {
      return this.warmupTicks;
   }

   void decrementWarmup() {
      this.warmupTicks = Math.max(0, this.warmupTicks - 1);
   }

   boolean requiresFlyingIntroLock() {
      return this.moveType == CompanionMoveType.FLY && !this.mode.isRescue();
   }

   boolean flyingRescueStaged() {
      return this.flyingRescueStaged;
   }

   void setFlyingRescueStaged() {
      this.flyingRescueStaged = true;
   }

   MountCinematicStage stage() {
      return this.stage;
   }

   void beginSwitch() {
      this.stage = MountCinematicStage.SWITCH;
      this.switchAge = 0;
   }

   int age() {
      return this.age;
   }

   void incrementAge() {
      this.age++;
   }

   int switchAge() {
      return this.switchAge;
   }

   void incrementSwitchAge() {
      this.switchAge++;
   }

   boolean switched() {
      return this.switched;
   }

   void setSwitched() {
      this.switched = true;
   }
}
